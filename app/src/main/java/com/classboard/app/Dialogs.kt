package com.classboard.app

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.util.UUID

/**
 * Every dialog in the app. They all share one frame (dialog_frame.xml)
 * that is coloured from the current theme, so none of them fall back
 * to the system's default look.
 */
class Dialogs(
    private val activity: AppCompatActivity,
    private val themeOf: () -> ThemeColors
) {

    class Action(val label: String, val danger: Boolean = false, val run: () -> Unit)

    private fun dp(value: Float): Int = Ui.dp(activity, value)

    /**
     * Shows the shared dialog frame. `onPositive` returns true to
     * close the dialog, or false to keep it open (for example when a
     * form still has a problem to fix).
     */
    private fun panel(
        title: String,
        message: String? = null,
        content: View? = null,
        positive: String? = null,
        negative: String = "Cancel",
        neutral: String? = null,
        dangerPositive: Boolean = false,
        onNeutral: (() -> Unit)? = null,
        onPositive: (() -> Boolean)? = null
    ): AlertDialog {
        val t = themeOf()
        val frame = LayoutInflater.from(activity).inflate(R.layout.dialog_frame, null)
        val messageView = frame.findViewById<TextView>(R.id.dialogMessage)
        val holder = frame.findViewById<FrameLayout>(R.id.dialogContent)
        val neutralBtn = frame.findViewById<TextView>(R.id.dialogNeutral)
        val negativeBtn = frame.findViewById<TextView>(R.id.dialogNegative)
        val positiveBtn = frame.findViewById<TextView>(R.id.dialogPositive)

        frame.findViewById<TextView>(R.id.dialogTitle).text = title
        if (message != null) {
            messageView.text = message
            messageView.visibility = View.VISIBLE
        }
        if (content != null) holder.addView(content) else holder.visibility = View.GONE

        negativeBtn.text = negative
        if (positive != null) positiveBtn.text = positive else positiveBtn.visibility = View.GONE
        if (neutral != null) {
            neutralBtn.text = neutral
            neutralBtn.visibility = View.VISIBLE
        }

        Ui.paint(frame, t)
        // Destructive buttons are red rather than the accent colour.
        if (neutral != null) {
            neutralBtn.setTextColor(t.danger)
            neutralBtn.background = Ui.pressable(activity, Color.TRANSPARENT, 12f, t.danger)
        }
        if (dangerPositive) {
            positiveBtn.setTextColor(t.danger)
            positiveBtn.background = Ui.pressable(activity, Color.TRANSPARENT, 12f, t.danger)
        }

        val dialog = AlertDialog.Builder(activity).setView(frame).create()
        dialog.window?.setBackgroundDrawable(InsetDrawable(Ui.rounded(activity, t.panel, 24f), dp(16f)))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        negativeBtn.setOnClickListener { dialog.dismiss() }
        positiveBtn.setOnClickListener {
            if (onPositive == null || onPositive()) dialog.dismiss()
        }
        neutralBtn.setOnClickListener {
            dialog.dismiss()
            onNeutral?.invoke()
        }
        dialog.show()
        return dialog
    }

    /** A yes/no question before something that can't be undone. */
    fun confirm(title: String, message: String, positive: String, onConfirm: () -> Unit) {
        panel(title, message = message, positive = positive, dangerPositive = true) {
            onConfirm()
            true
        }
    }

    /** A short list of things that can be done, one per row. */
    fun actions(title: String, items: List<Action>) {
        val t = themeOf()
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(12f), 0)
        }
        var dialog: AlertDialog? = null
        items.forEach { action ->
            val color = if (action.danger) t.danger else t.ink
            val row = TextView(activity).apply {
                text = action.label
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(color)
                setPadding(dp(12f), 0, dp(12f), 0)
                background = Ui.pressable(activity, Color.TRANSPARENT, 12f, color)
                setOnClickListener {
                    dialog?.dismiss()
                    action.run()
                }
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f)))
        }
        dialog = panel(title, content = list, negative = "Close")
    }

    /** Asks for a whole number of minutes between `min` and `max`. */
    fun minutes(title: String, message: String, initial: Int, min: Int, max: Int, onPicked: (Int) -> Unit) {
        val input = EditText(activity).apply {
            tag = "field"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            maxLines = 1
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            setText(initial.toString())
            setSelection(text.length)
        }
        val holder = FrameLayout(activity).apply {
            setPadding(dp(24f), dp(8f), dp(24f), 0)
            addView(
                input,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        panel(title, message = message, content = holder, positive = "Set") {
            val typed = input.text.toString().trim().toIntOrNull()
            if (typed == null) {
                false
            } else {
                onPicked(typed.coerceIn(min, max))
                true
            }
        }
    }

    /** Create a schedule, or edit one's name, note and alert switch. */
    fun scheduleForm(existing: Schedule?, onSave: (name: String, note: String, alertsEnabled: Boolean) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_schedule, null)
        val nameInput = view.findViewById<EditText>(R.id.scheduleNameInput)
        val noteInput = view.findViewById<EditText>(R.id.scheduleNoteInput)
        val alertsSwitch = view.findViewById<SwitchCompat>(R.id.alertsSwitch)
        val error = view.findViewById<TextView>(R.id.scheduleError)

        nameInput.setText(existing?.name ?: "")
        noteInput.setText(existing?.note ?: "")
        alertsSwitch.isChecked = existing?.alertsEnabled ?: true

        panel(
            title = if (existing == null) "New schedule" else "Edit schedule",
            content = view,
            positive = if (existing == null) "Create" else "Save"
        ) {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                error.text = "Give the schedule a name."
                error.visibility = View.VISIBLE
                false
            } else {
                onSave(name, noteInput.text.toString().trim(), alertsSwitch.isChecked)
                true
            }
        }
    }

    /**
     * Add a class, or edit one. A new class can be put on several days
     * at once (it becomes one entry per day); an existing class is a
     * single meeting, so editing it picks exactly one day.
     */
    fun classForm(
        existing: ScheduleEntry?,
        defaultDay: Int,
        onDelete: (() -> Unit)?,
        onSave: (List<ScheduleEntry>) -> Unit
    ) {
        val t = themeOf()
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_class, null)
        val subjectInput = view.findViewById<EditText>(R.id.subjectInput)
        val roomInput = view.findViewById<EditText>(R.id.roomInput)
        val teacherInput = view.findViewById<EditText>(R.id.teacherInput)
        val dayRow = view.findViewById<LinearLayout>(R.id.dayRow)
        val startBtn = view.findViewById<TextView>(R.id.startTimeBtn)
        val endBtn = view.findViewById<TextView>(R.id.endTimeBtn)
        val error = view.findViewById<TextView>(R.id.classError)

        val singleDay = existing != null
        view.findViewById<TextView>(R.id.daysLabel).text =
            if (singleDay) "Day" else "Days (pick every day it meets)"

        if (existing != null) {
            subjectInput.setText(existing.subject)
            roomInput.setText(existing.room)
            teacherInput.setText(existing.teacher)
        }

        // ---- Days ----
        val selected = HashSet<Int>()
        selected.add((existing?.day ?: defaultDay).coerceIn(0, 6))
        val dayViews = LinkedHashMap<Int, TextView>()

        fun paintDays() {
            dayViews.forEach { (day, dayView) ->
                val on = selected.contains(day)
                dayView.background =
                    if (on) Ui.pressable(activity, t.accent, 12f, t.accentText)
                    else Ui.pressable(activity, t.panel2, 12f, t.ink)
                dayView.setTextColor(if (on) t.accentText else t.ink)
                dayView.isSelected = on
            }
        }

        ScheduleMath.WEEK_ORDER.forEachIndexed { index, day ->
            val dayView = TextView(activity).apply {
                text = ScheduleMath.DAY_SHORT[day].take(2)
                contentDescription = ScheduleMath.DAY_LONG[day]
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setOnClickListener {
                    if (singleDay) {
                        selected.clear()
                        selected.add(day)
                    } else if (!selected.remove(day)) {
                        selected.add(day)
                    }
                    paintDays()
                }
            }
            val params = LinearLayout.LayoutParams(0, dp(46f), 1f)
            if (index > 0) params.marginStart = dp(4f)
            dayRow.addView(dayView, params)
            dayViews[day] = dayView
        }
        paintDays()

        // ---- Times ----
        var startMin = if (existing != null) ScheduleMath.startMinutes(existing) else 8 * 60
        var endMin = if (existing != null) ScheduleMath.endMinutes(existing) else 9 * 60

        fun paintTimes() {
            startBtn.text = ScheduleMath.fmt12(startMin)
            endBtn.text = ScheduleMath.fmt12(endMin) + if (endMin < startMin) " (next day)" else ""
        }
        paintTimes()

        startBtn.setOnClickListener {
            pickTime(startMin) { picked ->
                // Moving the start keeps the class the same length.
                val length = endMin - startMin
                startMin = picked
                if (length > 0) endMin = (picked + length).coerceAtMost(24 * 60 - 1)
                paintTimes()
            }
        }
        endBtn.setOnClickListener {
            pickTime(endMin) { picked ->
                endMin = picked
                paintTimes()
            }
        }

        panel(
            title = if (existing == null) "Add class" else "Edit class",
            content = view,
            positive = "Save",
            neutral = if (existing != null && onDelete != null) "Delete" else null,
            onNeutral = onDelete
        ) {
            val subject = subjectInput.text.toString().trim()
            val problem = when {
                subject.isEmpty() -> "Give the class a name."
                selected.isEmpty() -> "Pick at least one day."
                startMin == endMin -> "The end time has to be different from the start time."
                else -> null
            }
            if (problem != null) {
                error.text = problem
                error.visibility = View.VISIBLE
                false
            } else {
                val room = roomInput.text.toString().trim()
                val teacher = teacherInput.text.toString().trim()
                val start = ScheduleMath.toHHmm(startMin)
                val end = ScheduleMath.toHHmm(endMin)
                val entries = if (existing != null) {
                    listOf(existing.copy(subject = subject, room = room, teacher = teacher, day = selected.first(), start = start, end = end))
                } else {
                    ScheduleMath.WEEK_ORDER.filter { selected.contains(it) }.map { day ->
                        ScheduleEntry(UUID.randomUUID().toString(), subject, room, teacher, day, start, end)
                    }
                }
                onSave(entries)
                true
            }
        }
    }

    private fun pickTime(initialMinutes: Int, onPicked: (Int) -> Unit) {
        val style = if (themeOf().isLight) {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        }
        TimePickerDialog(
            activity,
            style,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinutes / 60,
            initialMinutes % 60,
            DateFormat.is24HourFormat(activity)
        ).show()
    }
}
