package com.classboard.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Hands each class's reminder times directly to the Android OS's own
 * alarm system — the same mechanism a real alarm clock uses, so
 * reminders survive the app being closed for weeks.
 *
 * Each class occurrence gets THREE alarms, not one:
 *   REMIND  — fires `leadMinutes` before class starts
 *   ONGOING — fires exactly when class starts (updates the same
 *             notification instead of creating a new one)
 *   END     — fires when class ends (removes the notification, and
 *             is the ONLY phase that re-arms next week's occurrence)
 *
 * Re-arming next week's alarms only ever happens from the END phase —
 * never from REMIND — which is what stops the bug where a reminder
 * kept re-firing every few seconds before class actually started.
 */
object AlarmScheduler {

    private const val PHASE_REMIND = "REMIND"
    private const val PHASE_ONGOING = "ONGOING"
    private const val PHASE_END = "END"

    fun scheduleAll(context: Context) {
        val entries = ScheduleStore.getSchedule(context)
        entries.forEach { scheduleOne(context, it) }
    }

    /** Arms the full REMIND/ONGOING/END cycle for this class's next
     *  upcoming occurrence. Safe to call any time — on add/edit, on
     *  app open, after boot, or after the previous cycle's END fires. */
    fun scheduleOne(context: Context, entry: ScheduleEntry) {
        val leadMinutes = ScheduleStore.getLeadMinutes(context)
        val occurrenceStart = ScheduleStore.nextOccurrence(entry)

        val occurrenceEnd = occurrenceStart.clone() as Calendar
        val (endH, endM) = entry.end.split(":").map { it.toInt() }
        occurrenceEnd.set(Calendar.HOUR_OF_DAY, endH)
        occurrenceEnd.set(Calendar.MINUTE, endM)
        occurrenceEnd.set(Calendar.SECOND, 0)
        // If the class's listed end time is numerically "earlier" than
        // its start (shouldn't normally happen, but just in case),
        // push it to the next day rather than into the past.
        if (occurrenceEnd.before(occurrenceStart)) {
            occurrenceEnd.add(Calendar.DAY_OF_YEAR, 1)
        }

        val remindTime = occurrenceStart.clone() as Calendar
        remindTime.add(Calendar.MINUTE, -leadMinutes)

        val now = Calendar.getInstance()

        // Always arm ONGOING and END for the occurrence we just found.
        arm(context, entry, PHASE_ONGOING, occurrenceStart)
        arm(context, entry, PHASE_END, occurrenceEnd)

        // Only arm REMIND if that moment hasn't already passed — e.g.
        // if someone enables notifications 5 minutes before class with
        // a 15-minute lead, there's no meaningful "before" left to warn
        // about, so we just let ONGOING handle it when class starts.
        if (remindTime.after(now)) {
            arm(context, entry, PHASE_REMIND, remindTime)
        } else {
            cancelPhase(context, entry.id, PHASE_REMIND)
        }
    }

    /** Called only from the END phase in ReminderReceiver, to line up
     *  next week's cycle once this occurrence has genuinely finished. */
    fun scheduleNextCycle(context: Context, entry: ScheduleEntry) {
        scheduleOne(context, entry)
    }

    private fun arm(context: Context, entry: ScheduleEntry, phase: String, at: Calendar) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("phase", phase)
            putExtra("id", entry.id)
            putExtra("subject", entry.subject)
            putExtra("room", entry.room)
            putExtra("teacher", entry.teacher)
            putExtra("day", entry.day)
            putExtra("start", entry.start)
            putExtra("end", entry.end)
            putExtra("leadMinutes", ScheduleStore.getLeadMinutes(context))
        }

        val requestCode = (entry.id + "_" + phase).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAt = maxOf(at.timeInMillis, System.currentTimeMillis() + 2_000)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
        }
    }

    fun cancelAllPhases(context: Context, entryId: String) {
        cancelPhase(context, entryId, PHASE_REMIND)
        cancelPhase(context, entryId, PHASE_ONGOING)
        cancelPhase(context, entryId, PHASE_END)
    }

    private fun cancelPhase(context: Context, entryId: String, phase: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = (entryId + "_" + phase).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    // Kept for compatibility with existing call sites (delete flow).
    fun cancelOne(context: Context, entryId: String) = cancelAllPhases(context, entryId)
}
