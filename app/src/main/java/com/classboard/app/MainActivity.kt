package com.classboard.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import java.util.Calendar

/**
 * The app's single screen. It shows one of two pages:
 *
 *   Home      a tab per schedule, the next class, and that schedule's
 *             week as a timetable (Table) or a day-by-day list (List)
 *   Settings  alert timing, colour theme, backup
 *
 * Nothing is kept in memory between changes: after anything is edited
 * the screen is simply redrawn from what's stored (see [refresh]).
 */
class MainActivity : AppCompatActivity() {

    private var themeKey = "default"
    private var theme: ThemeColors = Themes.get(themeKey)
    private var schedules: List<Schedule> = emptyList()
    private var activeId: String? = null
    private var settingsOpen = false
    private var bannerMode = BANNER_NONE
    private lateinit var dialogs: Dialogs

    private val active: Schedule? get() = schedules.firstOrNull { it.id == activeId }

    /** Keeps the countdown and the timetable's "now" line current
     *  while the app is on screen. */
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            renderNextUp()
            v<TimetableView>(R.id.timetable).invalidate()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showSettings(false)
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            proceedAfterNotifPermission()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
            // Android won't show its own prompt any more; the switch
            // now lives in the system settings.
            openNotificationSettings()
        } else {
            toast("Notifications were not allowed, so alerts stay off.")
            refresh()
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ScheduleStore.exportBackupJson(this).toByteArray())
                }
                toast("Backup saved.")
            } catch (e: Exception) {
                toast("Couldn't save the backup.")
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val added = if (text == null) emptyList() else ScheduleStore.importBackupJson(this, text)
                if (added.isEmpty()) {
                    toast("That file didn't look like a Class Board backup.")
                } else {
                    ScheduleStore.setActiveId(this, added.first().id)
                    AlarmScheduler.scheduleAll(this)
                    showSettings(false)
                    toast(if (added.size == 1) "1 schedule imported." else "${added.size} schedules imported.")
                }
            } catch (e: Exception) {
                toast("Couldn't read that file.")
            }
        }
    }

    // ---------------- Lifecycle ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        themeKey = Themes.getSaved(this)
        theme = Themes.get(themeKey)
        dialogs = Dialogs(this) { theme }
        NotificationHelper.ensureChannel(this)
        onBackPressedDispatcher.addCallback(this, backToHome)

        wireHome()
        wireSettings()
        refresh()

        // Puts back any alarm the OS has dropped since the last visit.
        AlarmScheduler.scheduleAll(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------------- Small helpers ----------------

    private fun <T : View> v(id: Int): T = findViewById(id)

    private fun dp(value: Float): Int = Ui.dp(this, value)

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun show(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private val medium: Typeface get() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // ---------------- Wiring ----------------

    private fun wireHome() {
        v<View>(R.id.settingsBtn).setOnClickListener { showSettings(true) }
        v<View>(R.id.scheduleMenuBtn).setOnClickListener { showScheduleMenu() }
        v<View>(R.id.createScheduleBtn).setOnClickListener { createSchedule() }
        v<View>(R.id.addClassFab).setOnClickListener { addClass() }
        v<View>(R.id.bannerAction).setOnClickListener { onBannerAction() }
        v<View>(R.id.tableToggle).setOnClickListener { setViewMode(ScheduleStore.VIEW_TABLE) }
        v<View>(R.id.listToggle).setOnClickListener { setViewMode(ScheduleStore.VIEW_LIST) }
        v<TimetableView>(R.id.timetable).onEntryClick = { editClass(it) }
    }

    private fun wireSettings() {
        v<View>(R.id.settingsBackBtn).setOnClickListener { showSettings(false) }
        v<View>(R.id.enableBtn).setOnClickListener { requestNotificationsAndAlarms() }

        v<View>(R.id.leadMinus).setOnClickListener { stepLead(-1) }
        v<View>(R.id.leadPlus).setOnClickListener { stepLead(1) }
        v<View>(R.id.leadValue).setOnClickListener {
            dialogs.minutes(
                "Start alerting",
                "Minutes before a class, from 1 to ${ScheduleStore.MAX_LEAD_MINUTES}.",
                ScheduleStore.getLeadMinutes(this),
                1,
                ScheduleStore.MAX_LEAD_MINUTES
            ) { setAlertTiming(it, ScheduleStore.getRepeatMinutes(this)) }
        }

        v<View>(R.id.repeatMinus).setOnClickListener { stepRepeat(-1) }
        v<View>(R.id.repeatPlus).setOnClickListener { stepRepeat(1) }
        v<View>(R.id.repeatValue).setOnClickListener {
            val lead = ScheduleStore.getLeadMinutes(this)
            if (lead <= 1) {
                toast("Alerts start 1 minute ahead, so there is no room to repeat.")
            } else {
                dialogs.minutes(
                    "Repeat every",
                    "Minutes between alerts, from 1 to ${lead - 1}. Enter 0 to turn repeating off.",
                    ScheduleStore.getRepeatMinutes(this),
                    0,
                    lead - 1
                ) { setAlertTiming(lead, it) }
            }
        }

        v<View>(R.id.exportBtn).setOnClickListener { exportLauncher.launch("class-board-backup.json") }
        v<View>(R.id.importBtn).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
    }

    // ---------------- Drawing the screen ----------------

    /** Reloads what's stored and redraws everything from it. */
    private fun refresh() {
        schedules = ScheduleStore.getSchedules(this)
        val saved = ScheduleStore.getActiveId(this)
        activeId = (schedules.firstOrNull { it.id == saved } ?: schedules.firstOrNull())?.id

        applyChrome()
        Ui.paint(v<View>(R.id.root), theme)

        show(v(R.id.homePage), !settingsOpen)
        show(v(R.id.settingsPage), settingsOpen)
        renderHeader()
        renderTabs()
        renderAlertBanner()
        renderNextUp()
        renderWeek()
        renderFab()
        renderSettings()
    }

    /** Status and navigation bars follow the theme too. */
    private fun applyChrome() {
        window.statusBarColor = theme.bg
        window.navigationBarColor = theme.bg
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.isAppearanceLightStatusBars = theme.isLight
        bars.isAppearanceLightNavigationBars = theme.isLight
    }

    private fun showSettings(open: Boolean) {
        settingsOpen = open
        backToHome.isEnabled = open
        refresh()
    }

    private fun renderHeader() {
        val s = active
        show(v(R.id.appLabel), s != null)
        show(v(R.id.scheduleMenuBtn), s != null)
        v<TextView>(R.id.scheduleTitle).text = s?.name?.ifBlank { "Untitled schedule" } ?: "Class Board"
        v<TextView>(R.id.scheduleNote).text = if (s == null) {
            "Timetables and class alerts"
        } else {
            val parts = mutableListOf<String>()
            if (s.note.isNotBlank()) parts.add(s.note)
            parts.add(if (s.entries.size == 1) "1 class" else "${s.entries.size} classes")
            if (!s.alertsEnabled) parts.add("alerts off")
            parts.joinToString("  ·  ")
        }
    }

    /** One tab per schedule, then a tab that creates a new one. */
    private fun renderTabs() {
        val row = v<LinearLayout>(R.id.tabRow)
        val scroll = v<HorizontalScrollView>(R.id.tabScroll)
        row.removeAllViews()
        show(scroll, schedules.isNotEmpty())

        var selectedTab: View? = null
        schedules.forEach { schedule ->
            val selected = schedule.id == activeId
            val name = schedule.name.ifBlank { "Untitled schedule" }
            val tab = makeTab(name, selected, outlined = false)
            tab.contentDescription = if (selected) "$name, selected" else name
            tab.setOnClickListener { selectSchedule(schedule.id) }
            tab.setOnLongClickListener {
                editSchedule(schedule)
                true
            }
            row.addView(tab)
            if (selected) selectedTab = tab
        }

        val add = makeTab("+ New", selected = false, outlined = true)
        add.contentDescription = "New schedule"
        add.setOnClickListener { createSchedule() }
        row.addView(add)

        val target = selectedTab
        if (target != null) {
            scroll.post { scroll.smoothScrollTo((target.left - dp(16f)).coerceAtLeast(0), 0) }
        }
    }

    private fun makeTab(label: String, selected: Boolean, outlined: Boolean): TextView {
        val t = theme
        val tab = TextView(this)
        tab.text = label
        tab.textSize = 14f
        tab.typeface = medium
        tab.gravity = Gravity.CENTER
        tab.maxLines = 1
        tab.ellipsize = TextUtils.TruncateAt.END
        tab.maxWidth = dp(220f)
        tab.minWidth = dp(64f)
        tab.setPadding(dp(18f), 0, dp(18f), 0)
        when {
            selected -> {
                tab.background = Ui.pressable(this, t.accent, 24f, t.accentText)
                tab.setTextColor(t.accentText)
            }
            outlined -> {
                tab.background = Ui.pressable(this, Color.TRANSPARENT, 24f, t.ink, t.line)
                tab.setTextColor(t.slate)
            }
            else -> {
                tab.background = Ui.pressable(this, t.panel, 24f, t.ink)
                tab.setTextColor(t.ink)
            }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48f))
        params.marginEnd = dp(8f)
        tab.layoutParams = params
        return tab
    }

    /** A strip that only appears when alerts can't currently reach
     *  the user, with a one-tap fix. */
    private fun renderAlertBanner() {
        val banner = v<View>(R.id.alertBanner)
        val s = active
        bannerMode = when {
            s == null || s.entries.isEmpty() -> BANNER_NONE
            !s.alertsEnabled -> BANNER_MUTED
            !alertsReady() -> BANNER_PERMISSION
            else -> BANNER_NONE
        }
        show(banner, bannerMode != BANNER_NONE)
        if (bannerMode == BANNER_NONE) return

        banner.background = Ui.rounded(this, Themes.blend(theme.bg, theme.accent, 0.14f), 16f)
        v<TextView>(R.id.bannerText).text = if (bannerMode == BANNER_MUTED) {
            "Alerts are switched off for this schedule."
        } else {
            "Alerts can't reach you until notifications are allowed."
        }
    }

    private fun onBannerAction() {
        val s = active
        if (bannerMode == BANNER_MUTED && s != null) {
            ScheduleStore.updateSchedule(this, s.copy(alertsEnabled = true))
            AlarmScheduler.scheduleAll(this)
            refresh()
        } else if (bannerMode == BANNER_PERMISSION) {
            requestNotificationsAndAlarms()
        }
    }

    /** The class happening now, or else the soonest one coming up. */
    private fun renderNextUp() {
        val card = v<View>(R.id.nextCard)
        val s = active
        if (s == null || s.entries.isEmpty()) {
            show(card, false)
            return
        }
        show(card, true)

        val now = System.currentTimeMillis()
        val soonest = s.entries
            .map { it to ScheduleMath.occurrence(it, now) }
            .minByOrNull { it.second.startAt } ?: return
        val entry = soonest.first
        val occurrence = soonest.second
        val live = occurrence.startAt <= now

        val t = theme
        val soft = Themes.blend(t.accentText, t.accent, 0.28f)
        card.background = Ui.rounded(this, t.accent, 22f)

        val label = v<TextView>(R.id.nextLabel)
        label.text = if (live) "HAPPENING NOW" else "NEXT UP"
        label.setTextColor(soft)

        val subject = v<TextView>(R.id.nextSubject)
        subject.text = entry.subject.ifBlank { "Class" }
        subject.setTextColor(t.accentText)

        val meta = v<TextView>(R.id.nextMeta)
        meta.text = listOf(
            ScheduleMath.DAY_LONG[entry.day],
            ScheduleMath.timeRange(entry),
            entry.room,
            entry.teacher
        ).filter { it.isNotBlank() }.joinToString("  ·  ")
        meta.setTextColor(soft)

        val countdown = v<TextView>(R.id.nextCountdown)
        countdown.text = if (live) {
            "Ends in ${ScheduleMath.countdown(occurrence.endAt - now)}"
        } else {
            "Starts in ${ScheduleMath.countdown(occurrence.startAt - now)}"
        }
        countdown.setTextColor(t.accent)
        countdown.background = Ui.rounded(this, t.accentText, 20f)
    }

    /** The open schedule's week, as a table or a list. */
    private fun renderWeek() {
        val s = active
        show(v(R.id.noScheduleState), s == null)
        show(v(R.id.scheduleContent), s != null)
        if (s == null) return

        val entries = s.entries
        val empty = entries.isEmpty()
        val asTable = ScheduleStore.getViewMode(this) == ScheduleStore.VIEW_TABLE

        show(v(R.id.emptyClasses), empty)
        show(v(R.id.weekHeader), !empty)
        show(v(R.id.tableCard), !empty && asTable)
        show(v(R.id.listContainer), !empty && !asTable)

        styleToggle(v(R.id.tableToggle), asTable)
        styleToggle(v(R.id.listToggle), !asTable)

        v<TimetableView>(R.id.timetable).setData(entries, theme)
        renderList(entries)
    }

    private fun styleToggle(toggle: TextView, on: Boolean) {
        toggle.background =
            if (on) Ui.rounded(this, theme.accent, 11f)
            else Ui.pressable(this, Color.TRANSPARENT, 11f, theme.ink)
        toggle.setTextColor(if (on) theme.accentText else theme.slate)
        toggle.isSelected = on
    }

    private fun renderList(entries: List<ScheduleEntry>) {
        val t = theme
        val list = v<LinearLayout>(R.id.listContainer)
        list.removeAllViews()

        val order = ScheduleMath.subjectOrder(entries)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1

        ScheduleMath.WEEK_ORDER.forEach { day ->
            val dayEntries = entries.filter { it.day == day }.sortedBy { ScheduleMath.startMinutes(it) }
            if (dayEntries.isEmpty()) return@forEach

            val header = TextView(this)
            header.text = if (day == today) "${ScheduleMath.DAY_LONG[day]}  ·  Today" else ScheduleMath.DAY_LONG[day]
            header.textSize = 14f
            header.typeface = medium
            header.setTextColor(if (day == today) t.accent else t.slate)
            header.setPadding(dp(4f), dp(if (list.childCount == 0) 2f else 14f), 0, dp(8f))
            list.addView(header)

            dayEntries.forEach { entry ->
                val row = layoutInflater.inflate(R.layout.item_class_row, list, false)
                row.background = Ui.pressable(this, t.panel, 16f, t.ink)
                row.clipToOutline = true

                val index = order[ScheduleMath.subjectKey(entry.subject)] ?: 0
                row.findViewById<View>(R.id.classRowStripe).setBackgroundColor(Themes.subjectColor(index))

                val time = row.findViewById<TextView>(R.id.classRowTime)
                time.text = ScheduleMath.fmt12(entry.start) + "\n" + ScheduleMath.fmt12(entry.end)
                time.setTextColor(t.slate)

                val subject = row.findViewById<TextView>(R.id.classRowSubject)
                subject.text = entry.subject.ifBlank { "Class" }
                subject.setTextColor(t.ink)

                val meta = row.findViewById<TextView>(R.id.classRowMeta)
                val details = listOf(entry.room, entry.teacher).filter { it.isNotBlank() }.joinToString("  ·  ")
                meta.text = details
                meta.setTextColor(t.slate)
                show(meta, details.isNotEmpty())

                row.setOnClickListener { editClass(entry) }
                list.addView(row)
            }
        }
    }

    private fun renderFab() {
        val fab = v<TextView>(R.id.addClassFab)
        show(fab, !settingsOpen && active != null)
        fab.background = Ui.pressable(this, theme.accent, 28f, theme.accentText)
        fab.setTextColor(theme.accentText)
        fab.compoundDrawableTintList = ColorStateList.valueOf(theme.accentText)
    }

    private fun renderSettings() {
        val t = theme
        val notifsOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val ready = alertsReady()

        v<TextView>(R.id.alertStatus).text = when {
            ready -> "Alerts are on. They are set with the phone's own alarm system, so they arrive even when this app is closed."
            notifsOn -> "Notifications are allowed, but exact alarms are not, so alerts may arrive late."
            else -> "Notifications are not allowed yet, so alerts can't be shown."
        }
        show(v(R.id.enableBtn), !ready)

        val lead = ScheduleStore.getLeadMinutes(this)
        val repeat = ScheduleStore.getRepeatMinutes(this)
        v<TextView>(R.id.leadValue).text = "$lead min before"
        v<TextView>(R.id.repeatValue).text = if (repeat <= 0) "Off" else "$repeat min"

        val summary = v<TextView>(R.id.alertSummary)
        summary.text = ScheduleMath.alertSummary(lead, repeat)
        summary.setTextColor(t.ink)
        summary.background = Ui.rounded(this, Themes.blend(t.panel, t.accent, 0.14f), 14f)

        renderThemeRow()
    }

    private fun renderThemeRow() {
        val row = v<LinearLayout>(R.id.themeRow)
        row.removeAllViews()
        Themes.all.forEach { (key, colors) ->
            val selected = key == themeKey
            val label = Themes.labels[key] ?: key
            // Each swatch previews a theme: its background with a dot
            // of its accent colour.
            val ring = Ui.oval(colors.bg, if (selected) theme.accent else theme.line, dp(if (selected) 3f else 1.5f))
            val dot = InsetDrawable(Ui.oval(colors.accent), dp(13f))
            val swatch = View(this)
            swatch.background = LayerDrawable(arrayOf(ring, dot))
            swatch.contentDescription = if (selected) "$label, selected" else label
            swatch.setOnClickListener {
                themeKey = key
                theme = Themes.get(key)
                Themes.save(this, key)
                refresh()
            }
            val params = LinearLayout.LayoutParams(dp(48f), dp(48f))
            params.marginEnd = dp(12f)
            row.addView(swatch, params)
        }
        v<TextView>(R.id.themeName).text = Themes.labels[themeKey] ?: themeKey
    }

    // ---------------- Schedules ----------------

    private fun selectSchedule(id: String) {
        ScheduleStore.setActiveId(this, id)
        refresh()
    }

    private fun setViewMode(mode: String) {
        ScheduleStore.setViewMode(this, mode)
        refresh()
    }

    private fun createSchedule() {
        dialogs.scheduleForm(null) { name, note, alertsEnabled ->
            val created = ScheduleStore.addSchedule(this, name, note, alertsEnabled)
            ScheduleStore.setActiveId(this, created.id)
            refresh()
        }
    }

    private fun editSchedule(schedule: Schedule) {
        dialogs.scheduleForm(schedule) { name, note, alertsEnabled ->
            // Re-read it so classes added since the dialog opened
            // aren't overwritten.
            val current = ScheduleStore.getSchedules(this).firstOrNull { it.id == schedule.id } ?: schedule
            ScheduleStore.updateSchedule(this, current.copy(name = name, note = note, alertsEnabled = alertsEnabled))
            AlarmScheduler.scheduleAll(this)
            refresh()
        }
    }

    private fun showScheduleMenu() {
        val s = active ?: return
        dialogs.actions(
            s.name.ifBlank { "Untitled schedule" },
            listOf(
                Dialogs.Action("Edit name and alerts") { editSchedule(s) },
                Dialogs.Action("Duplicate") { duplicateSchedule(s) },
                Dialogs.Action("Delete schedule", danger = true) { confirmDeleteSchedule(s) }
            )
        )
    }

    private fun duplicateSchedule(schedule: Schedule) {
        val copy = ScheduleStore.duplicateSchedule(this, schedule.id) ?: return
        ScheduleStore.setActiveId(this, copy.id)
        AlarmScheduler.scheduleAll(this)
        refresh()
    }

    private fun confirmDeleteSchedule(schedule: Schedule) {
        val count = schedule.entries.size
        val name = schedule.name.ifBlank { "this schedule" }
        val what = when (count) {
            0 -> "It has no classes."
            1 -> "Its 1 class and that class's alerts go with it."
            else -> "Its $count classes and their alerts go with it."
        }
        dialogs.confirm("Delete $name?", "$what This can't be undone.", "Delete") {
            schedule.entries.forEach { AlarmScheduler.cancelOne(this, it.id) }
            ScheduleStore.deleteSchedule(this, schedule.id)
            refresh()
        }
    }

    // ---------------- Classes ----------------

    private fun addClass() {
        val s = active
        if (s == null) {
            createSchedule()
            return
        }
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        dialogs.classForm(null, today, null) { entries ->
            ScheduleStore.upsertEntries(this, s.id, entries)
            AlarmScheduler.scheduleAll(this)
            refresh()
        }
    }

    private fun editClass(entry: ScheduleEntry) {
        val s = active ?: return
        dialogs.classForm(
            entry,
            entry.day,
            onDelete = {
                AlarmScheduler.cancelOne(this, entry.id)
                ScheduleStore.deleteEntry(this, s.id, entry.id)
                refresh()
            }
        ) { entries ->
            // Clear the old timing first so an alert for the previous
            // time can't linger.
            AlarmScheduler.cancelOne(this, entry.id)
            ScheduleStore.upsertEntries(this, s.id, entries)
            AlarmScheduler.scheduleAll(this)
            refresh()
        }
    }

    // ---------------- Alert timing ----------------

    private fun stepLead(direction: Int) {
        val next = ScheduleMath.stepLead(ScheduleStore.getLeadMinutes(this), direction, ScheduleStore.MAX_LEAD_MINUTES)
        setAlertTiming(next, ScheduleStore.getRepeatMinutes(this))
    }

    private fun stepRepeat(direction: Int) {
        val lead = ScheduleStore.getLeadMinutes(this)
        setAlertTiming(lead, ScheduleMath.stepRepeat(ScheduleStore.getRepeatMinutes(this), direction, lead))
    }

    /** Saves both alert settings together and re-arms every alarm.
     *  The repeat interval is kept shorter than the lead time. */
    private fun setAlertTiming(leadMinutes: Int, repeatMinutes: Int) {
        val lead = leadMinutes.coerceIn(1, ScheduleStore.MAX_LEAD_MINUTES)
        ScheduleStore.setLeadMinutes(this, lead)
        ScheduleStore.setRepeatMinutes(this, ScheduleMath.clampRepeat(repeatMinutes, lead))
        AlarmScheduler.scheduleAll(this)
        renderSettings()
    }

    // ---------------- Notification & exact-alarm permission ----------------

    private fun exactAlarmsAllowed(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    private fun alertsReady(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled() && exactAlarmsAllowed()

    private fun requestNotificationsAndAlarms() {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        when {
            enabled -> proceedAfterNotifPermission()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
            toast("Allow notifications for Class Board, then come back.")
        } catch (e: Exception) {
            toast("Open your phone's settings and allow notifications for Class Board.")
        }
    }

    private fun proceedAfterNotifPermission() {
        NotificationHelper.ensureChannel(this)
        if (!exactAlarmsAllowed()) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
                toast("Allow exact alarms on the next screen, then come back.")
            } catch (e: Exception) {
                toast("Alerts are on, but may arrive a little late on this phone.")
            }
        } else {
            toast("Alerts are on.")
        }
        AlarmScheduler.scheduleAll(this)
        refresh()
    }

    companion object {
        private const val TICK_MS = 30_000L
        private const val BANNER_NONE = 0
        private const val BANNER_PERMISSION = 1
        private const val BANNER_MUTED = 2
    }
}
