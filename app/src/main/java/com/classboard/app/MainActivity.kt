package com.classboard.app

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: EntryAdapter
    private val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val leadOptions = listOf(5, 10, 15, 30, 60)
    private var currentThemeKey: String = "default"

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) proceedAfterNotifPermission()
        else {
            Toast.makeText(this, "Notifications permission was denied.", Toast.LENGTH_LONG).show()
            updateStatusLine()
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { out ->
                    out.write(ScheduleStore.exportBackupJson(this).toByteArray())
                }
                Toast.makeText(this, "Backup saved.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't save the backup.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val text = contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                val ok = text != null && ScheduleStore.importBackupJson(this, text)
                if (ok) {
                    Toast.makeText(this, "Schedule imported.", Toast.LENGTH_SHORT).show()
                    refreshEverything()
                } else {
                    Toast.makeText(this, "That file didn't look like a Class Board backup.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't read that file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentThemeKey = Themes.getSaved(this)

        setupHeader()

        val recyclerView = findViewById<RecyclerView>(R.id.entryList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.isNestedScrollingEnabled = false
        adapter = EntryAdapter(
            ScheduleStore.getSchedule(this).sortedWith(compareBy({ it.day }, { it.start })),
            Themes.all.getValue(currentThemeKey),
            onEdit = { showEntryDialog(it) },
            onDelete = { deleteEntry(it) }
        )
        recyclerView.adapter = adapter

        setupLeadSpinner()
        setupThemeSpinner()
        setupManageToggle()

        findViewById<android.widget.Button>(R.id.enableBtn).setOnClickListener {
            requestNotificationsAndAlarms()
        }
        findViewById<android.widget.Button>(R.id.addBtn).setOnClickListener {
            showEntryDialog(null)
        }
        findViewById<android.widget.Button>(R.id.exportBtn).setOnClickListener {
            val safeSem = ScheduleStore.getSemesterLabel(this).replace(Regex("[^a-zA-Z0-9]+"), "-").lowercase()
            exportLauncher.launch("class-board-${safeSem.ifBlank { "backup" }}.json")
        }
        findViewById<android.widget.Button>(R.id.importBtn).setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
        findViewById<android.widget.Button>(R.id.resetBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset schedule?")
                .setMessage("This replaces your current classes with the original sample schedule.")
                .setPositiveButton("Reset") { _, _ ->
                    ScheduleStore.resetToDefault(this)
                    refreshEverything()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        applyTheme(currentThemeKey)
        updateStatusLine()
        refreshEverything()
    }

    override fun onResume() {
        super.onResume()
        updateStatusLine()
        refreshEverything()
    }

    private fun refreshEverything() {
        refreshList()
        updateNextUp()
        updateWeekList()
    }

    // ---------------- Header (semester / program) ----------------

    private fun setupHeader() {
        val semesterLabel = findViewById<TextView>(R.id.semesterLabel)
        fun refreshHeader() {
            semesterLabel.text = "${ScheduleStore.getSemesterLabel(this)} · ${ScheduleStore.getProgramLabel(this)}"
        }
        refreshHeader()
        semesterLabel.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_header_edit, null)
            val semInput = dialogView.findViewById<EditText>(R.id.semInput)
            val progInput = dialogView.findViewById<EditText>(R.id.progInput)
            semInput.setText(ScheduleStore.getSemesterLabel(this))
            progInput.setText(
                ScheduleStore.getProgramLabel(this).let { if (it.startsWith("Add your program")) "" else it }
            )
            AlertDialog.Builder(this)
                .setTitle("Your details")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val sem = semInput.text.toString().trim().ifEmpty { "1st Sem" }
                    val prog = progInput.text.toString().trim().ifEmpty { "Add your program in Settings ›" }
                    ScheduleStore.setSemesterLabel(this, sem)
                    ScheduleStore.setProgramLabel(this, prog)
                    refreshHeader()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ---------------- Manage panel toggle ----------------

    private fun setupManageToggle() {
        val toggleBtn = findViewById<android.widget.Button>(R.id.manageToggleBtn)
        val body = findViewById<LinearLayout>(R.id.manageBody)
        toggleBtn.setOnClickListener {
            val nowVisible = body.visibility != View.VISIBLE
            body.visibility = if (nowVisible) View.VISIBLE else View.GONE
            toggleBtn.text = if (nowVisible) "Manage schedule ▴" else "Manage schedule ▾"
        }
    }

    // ---------------- Theming ----------------

    private fun setupThemeSpinner() {
        val spinner = findViewById<Spinner>(R.id.themeSpinner)
        val keys = Themes.all.keys.toList()
        val labels = keys.map { Themes.labels[it] ?: it }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val index = keys.indexOf(currentThemeKey).takeIf { it >= 0 } ?: 0
        spinner.setSelection(index, false)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = keys[position]
                if (key != currentThemeKey) {
                    currentThemeKey = key
                    Themes.save(this@MainActivity, key)
                    applyTheme(key)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun rounded(colorInt: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(colorInt)
            strokeColor?.let { setStroke((1.5f * resources.displayMetrics.density).toInt(), it) }
        }
    }

    private fun applyTheme(key: String) {
        val t = Themes.all.getValue(key)

        findViewById<View>(R.id.rootLayout).setBackgroundColor(t.bg)
        findViewById<View>(R.id.scrollRoot).setBackgroundColor(t.bg)
        findViewById<View>(R.id.boardCard).background = rounded(t.panel2, 10f, t.accent)
        findViewById<TextView>(R.id.titleText).setTextColor(t.accent)
        findViewById<TextView>(R.id.semesterLabel).setTextColor(t.slate)
        findViewById<TextView>(R.id.statusLine).setTextColor(t.slate)

        val enableBtn = findViewById<android.widget.Button>(R.id.enableBtn)
        enableBtn.background = rounded(t.accent, 8f)
        enableBtn.setTextColor(t.accentText)

        val manageToggleBtn = findViewById<android.widget.Button>(R.id.manageToggleBtn)
        manageToggleBtn.background = rounded(t.panel, 8f)
        manageToggleBtn.setTextColor(t.ink)

        val addBtn = findViewById<android.widget.Button>(R.id.addBtn)
        addBtn.background = rounded(t.panel, 8f)
        addBtn.setTextColor(t.ink)

        val exportBtn = findViewById<android.widget.Button>(R.id.exportBtn)
        exportBtn.background = rounded(t.panel, 8f)
        exportBtn.setTextColor(t.ink)

        val importBtn = findViewById<android.widget.Button>(R.id.importBtn)
        importBtn.background = rounded(t.panel, 8f)
        importBtn.setTextColor(t.ink)

        val resetBtn = findViewById<android.widget.Button>(R.id.resetBtn)
        resetBtn.background = rounded(t.panel, 8f)

        adapter.updateTheme(t)
        updateNextUp()
        updateWeekList()
    }

    // ---------------- Next up (flap row) ----------------

    private fun updateNextUp() {
        val t = Themes.all.getValue(currentThemeKey)
        val row = findViewById<LinearLayout>(R.id.nextUpFlapRow)
        row.removeAllViews()

        val schedule = ScheduleStore.getSchedule(this)
        if (schedule.isEmpty()) {
            addFlap(row, t, "STATUS", "Add a class below")
            return
        }

        var soonestEntry: ScheduleEntry? = null
        var soonestTime: Calendar? = null
        schedule.forEach { entry ->
            val at = ScheduleStore.nextOccurrence(entry)
            if (soonestTime == null || at.before(soonestTime)) {
                soonestTime = at
                soonestEntry = entry
            }
        }
        val entry = soonestEntry ?: return
        val at = soonestTime ?: return

        val diffMin = ((at.timeInMillis - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
        val h = diffMin / 60
        val m = diffMin % 60
        val countdown = if (h > 0) "${h}h ${m}m" else "${m}m"

        addFlap(row, t, "SUBJECT", entry.subject, wide = true)
        addFlap(row, t, "DAY", dayNames[entry.day])
        addFlap(row, t, "TIME", fmt12(entry.start))
        if (entry.room.isNotBlank()) addFlap(row, t, "ROOM", entry.room)
        addFlap(row, t, "STARTS IN", countdown)
    }

    private fun addFlap(row: LinearLayout, t: ThemeColors, key: String, value: String, wide: Boolean = false) {
        val view = layoutInflater.inflate(R.layout.item_flap, row, false)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6f * resources.displayMetrics.density
            setColor(t.panel)
        }
        view.background = bg
        if (wide) view.minimumWidth = (150 * resources.displayMetrics.density).toInt()
        view.findViewById<TextView>(R.id.flapKey).apply {
            text = key
            setTextColor(t.slate)
        }
        view.findViewById<TextView>(R.id.flapValue).apply {
            text = value
            setTextColor(t.accent)
        }
        row.addView(view)
    }

    // ---------------- Week list (read-only) ----------------

    private fun updateWeekList() {
        val t = Themes.all.getValue(currentThemeKey)
        val container = findViewById<LinearLayout>(R.id.weekListContainer)
        container.removeAllViews()

        val schedule = ScheduleStore.getSchedule(this)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1

        for (offset in 0..6) {
            val dayIdx = (today + offset) % 7
            val entries = schedule.filter { it.day == dayIdx }.sortedBy { it.start }
            if (entries.isEmpty()) continue

            val header = TextView(this).apply {
                text = if (offset == 0) "${dayNames[dayIdx]} · Today" else dayNames[dayIdx]
                setTextColor(if (offset == 0) t.accent else t.slate)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                letterSpacing = 0.1f
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
            }
            container.addView(header)

            entries.forEach { entry ->
                val row = layoutInflater.inflate(R.layout.item_week_row, container, false)
                row.findViewById<TextView>(R.id.weekRowTime).apply {
                    text = "${fmt12(entry.start)}\n${fmt12(entry.end)}"
                    setTextColor(t.slate)
                }
                row.findViewById<TextView>(R.id.weekRowSubject).apply {
                    text = entry.subject
                    setTextColor(t.ink)
                }
                row.findViewById<TextView>(R.id.weekRowMeta).apply {
                    text = listOf(entry.room, entry.teacher).filter { it.isNotBlank() }.joinToString(" · ")
                    setTextColor(t.slate)
                }
                container.addView(row)
            }
        }
    }

    private fun fmt12(hhmm: String): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return String.format("%d:%02d %s", hour12, m, period)
    }

    // ---------------- Lead time ----------------

    private fun setupLeadSpinner() {
        val spinner = findViewById<Spinner>(R.id.leadSpinner)
        val labels = leadOptions.map { "$it min before" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val saved = ScheduleStore.getLeadMinutes(this)
        val index = leadOptions.indexOf(saved).takeIf { it >= 0 } ?: 2
        spinner.setSelection(index, false)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val minutes = leadOptions[position]
                if (minutes != ScheduleStore.getLeadMinutes(this@MainActivity)) {
                    ScheduleStore.setLeadMinutes(this@MainActivity, minutes)
                    AlarmScheduler.scheduleAll(this@MainActivity)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    // ---------------- Notifications & exact alarm permission ----------------

    private fun requestNotificationsAndAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            proceedAfterNotifPermission()
        }
    }

    private fun proceedAfterNotifPermission() {
        NotificationHelper.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                Toast.makeText(this, "Please allow exact alarms on the next screen, then come back.", Toast.LENGTH_LONG).show()
            }
        }
        AlarmScheduler.scheduleAll(this)
        updateStatusLine()
        Toast.makeText(this, "Reminders are on.", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusLine() {
        val statusLine = findViewById<TextView>(R.id.statusLine)
        val notifsEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        val exactOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else true

        statusLine.text = when {
            notifsEnabled && exactOk -> "Notifications: enabled — reminders are scheduled with the OS directly"
            notifsEnabled && !exactOk -> "Notifications: on, but exact-alarm permission is still needed"
            else -> "Notifications: not enabled yet"
        }
    }

    // ---------------- Add / edit / delete ----------------

    private fun showEntryDialog(existing: ScheduleEntry?) {
        val view = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        val subjectInput = view.findViewById<EditText>(R.id.subjectInput)
        val roomInput = view.findViewById<EditText>(R.id.roomInput)
        val teacherInput = view.findViewById<EditText>(R.id.teacherInput)
        val daySpinner = view.findViewById<Spinner>(R.id.daySpinner)
        val startBtn = view.findViewById<android.widget.Button>(R.id.startTimeBtn)
        val endBtn = view.findViewById<android.widget.Button>(R.id.endTimeBtn)

        daySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dayNames)

        var startTime = existing?.start ?: "08:00"
        var endTime = existing?.end ?: "09:00"

        fun refreshTimeButtons() {
            startBtn.text = "Start: $startTime"
            endBtn.text = "End: $endTime"
        }
        refreshTimeButtons()

        startBtn.setOnClickListener {
            val (h, m) = startTime.split(":").map { it.toInt() }
            TimePickerDialog(this, { _, hh, mm ->
                startTime = String.format("%02d:%02d", hh, mm)
                refreshTimeButtons()
            }, h, m, true).show()
        }
        endBtn.setOnClickListener {
            val (h, m) = endTime.split(":").map { it.toInt() }
            TimePickerDialog(this, { _, hh, mm ->
                endTime = String.format("%02d:%02d", hh, mm)
                refreshTimeButtons()
            }, h, m, true).show()
        }

        existing?.let {
            subjectInput.setText(it.subject)
            roomInput.setText(it.room)
            teacherInput.setText(it.teacher)
            daySpinner.setSelection(it.day)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add a class" else "Edit class")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val subject = subjectInput.text.toString().trim()
                if (subject.isEmpty()) {
                    Toast.makeText(this, "Subject can't be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val entry = ScheduleEntry(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    subject = subject,
                    room = roomInput.text.toString().trim(),
                    teacher = teacherInput.text.toString().trim(),
                    day = daySpinner.selectedItemPosition,
                    start = startTime,
                    end = endTime
                )
                saveEntry(entry)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveEntry(entry: ScheduleEntry) {
        val list = ScheduleStore.getSchedule(this).toMutableList()
        val index = list.indexOfFirst { it.id == entry.id }
        if (index >= 0) list[index] = entry else list.add(entry)
        ScheduleStore.saveSchedule(this, list)
        AlarmScheduler.scheduleOne(this, entry)
        refreshEverything()
    }

    private fun deleteEntry(entry: ScheduleEntry) {
        AlarmScheduler.cancelOne(this, entry.id)
        val list = ScheduleStore.getSchedule(this).toMutableList()
        list.removeAll { it.id == entry.id }
        ScheduleStore.saveSchedule(this, list)
        refreshEverything()
    }

    private fun refreshList() {
        adapter.update(ScheduleStore.getSchedule(this).sortedWith(compareBy({ it.day }, { it.start })))
    }
}
