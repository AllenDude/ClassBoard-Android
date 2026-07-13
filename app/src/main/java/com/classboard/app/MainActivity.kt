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
import android.widget.ArrayAdapter
import android.widget.EditText
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
        if (granted) {
            proceedAfterNotifPermission()
        } else {
            Toast.makeText(this, "Notifications permission was denied.", Toast.LENGTH_LONG).show()
            updateStatusLine()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentThemeKey = Themes.getSaved(this)

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
                ScheduleStore.getProgramLabel(this).let {
                    if (it.startsWith("Add your program")) "" else it
                }
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

        val recyclerView = findViewById<RecyclerView>(R.id.entryList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EntryAdapter(
            ScheduleStore.getSchedule(this).sortedWith(compareBy({ it.day }, { it.start })),
            Themes.all.getValue(currentThemeKey),
            onEdit = { showEntryDialog(it) },
            onDelete = { deleteEntry(it) }
        )
        recyclerView.adapter = adapter

        setupLeadSpinner()
        setupThemeSpinner()

        findViewById<android.widget.Button>(R.id.enableBtn).setOnClickListener {
            requestNotificationsAndAlarms()
        }

        findViewById<android.widget.Button>(R.id.addBtn).setOnClickListener {
            showEntryDialog(null)
        }

        applyTheme(currentThemeKey)
        updateStatusLine()
        updateNextUp()
    }

    override fun onResume() {
        super.onResume()
        updateStatusLine()
        updateNextUp()
    }

    // ---------------- Theming ----------------

    private fun setupThemeSpinner() {
        val spinner = findViewById<Spinner>(R.id.themeSpinner)
        val keys = Themes.all.keys.toList()
        val labels = keys.map { Themes.labels[it] ?: it }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val index = keys.indexOf(currentThemeKey).takeIf { it >= 0 } ?: 0
        spinner.setSelection(index)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
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

        findViewById<android.view.View>(R.id.rootLayout).setBackgroundColor(t.bg)
        findViewById<android.view.View>(R.id.boardCard).background = rounded(t.panel2, 10f, t.accent)
        findViewById<TextView>(R.id.titleText).setTextColor(t.accent)
        findViewById<TextView>(R.id.semesterLabel).setTextColor(t.slate)
        findViewById<TextView>(R.id.nextUpText).setTextColor(t.ink)
        findViewById<TextView>(R.id.statusLine).setTextColor(t.slate)

        val enableBtn = findViewById<android.widget.Button>(R.id.enableBtn)
        enableBtn.background = rounded(t.accent, 8f)
        enableBtn.setTextColor(t.accentText)

        val addBtn = findViewById<android.widget.Button>(R.id.addBtn)
        addBtn.background = rounded(t.panel, 8f)
        addBtn.setTextColor(t.ink)

        adapter.updateTheme(t)
    }

    // ---------------- Next up ----------------

    private fun updateNextUp() {
        val schedule = ScheduleStore.getSchedule(this)
        if (schedule.isEmpty()) {
            findViewById<TextView>(R.id.nextUpText).text = "Next up: add a class below \u2193"
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
        val dayLabel = dayNames[entry.day]
        findViewById<TextView>(R.id.nextUpText).text =
            "Next up: ${entry.subject} \u00b7 $dayLabel ${fmt12(entry.start)}"
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
        spinner.setSelection(index)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                ScheduleStore.setLeadMinutes(this@MainActivity, leadOptions[position])
                AlarmScheduler.scheduleAll(this@MainActivity)
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
                Toast.makeText(
                    this,
                    "Please allow exact alarms on the next screen, then come back.",
                    Toast.LENGTH_LONG
                ).show()
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
            notifsEnabled && exactOk -> "Notifications: enabled \u2014 reminders are scheduled with the OS directly"
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
            startBtn.text = "Start: ${startTime}"
            endBtn.text = "End: ${endTime}"
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
        refreshList()
        updateNextUp()
    }

    private fun deleteEntry(entry: ScheduleEntry) {
        AlarmScheduler.cancelOne(this, entry.id)
        val list = ScheduleStore.getSchedule(this).toMutableList()
        list.removeAll { it.id == entry.id }
        ScheduleStore.saveSchedule(this, list)
        refreshList()
        updateNextUp()
    }

    private fun refreshList() {
        adapter.update(ScheduleStore.getSchedule(this).sortedWith(compareBy({ it.day }, { it.start })))
    }
}
