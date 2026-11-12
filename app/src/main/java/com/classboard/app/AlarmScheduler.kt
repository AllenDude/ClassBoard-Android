package com.classboard.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Hands each class's alert times directly to the Android OS's own
 * alarm system — the same mechanism a real alarm clock uses, so
 * alerts survive the app being closed for weeks.
 *
 * Each class occurrence uses three alarm slots:
 *   REMIND  — the next alert before the class. The first one fires
 *             "lead minutes" ahead; each time it fires it lines up the
 *             following one "repeat minutes" later, until the class
 *             starts. Only one is ever pending at a time.
 *   ONGOING — fires exactly when the class starts.
 *   END     — fires when the class ends, removes the notification, and
 *             is the only phase that arms next week's occurrence.
 */
object AlarmScheduler {

    const val PHASE_REMIND = "REMIND"
    const val PHASE_ONGOING = "ONGOING"
    const val PHASE_END = "END"

    const val EXTRA_PHASE = "phase"
    const val EXTRA_ENTRY_ID = "entryId"
    const val EXTRA_START_AT = "startAt"
    const val EXTRA_END_AT = "endAt"
    const val EXTRA_ALERT_AT = "alertAt"

    /** Brings the OS alarms in line with what's stored: arms every
     *  class in a schedule that has alerts on, and clears the rest.
     *  Safe to call any time. */
    fun scheduleAll(context: Context) {
        ScheduleStore.getSchedules(context).forEach { schedule ->
            schedule.entries.forEach { entry ->
                if (schedule.alertsEnabled) scheduleOne(context, entry) else cancelOne(context, entry.id)
            }
        }
    }

    /** Arms the alarms for this class's current or next meeting. */
    fun scheduleOne(context: Context, entry: ScheduleEntry) {
        val now = System.currentTimeMillis()
        val occurrence = ScheduleMath.occurrence(entry, now)

        arm(context, entry.id, PHASE_END, occurrence.endAt, occurrence.startAt, occurrence.endAt)

        if (occurrence.startAt > now) {
            arm(context, entry.id, PHASE_ONGOING, occurrence.startAt, occurrence.startAt, occurrence.endAt)
            armNextReminder(context, entry.id, occurrence.startAt, occurrence.endAt, now)
        } else {
            // Already under way: nothing left to announce, just wait
            // for it to end.
            cancelPhase(context, entry.id, PHASE_REMIND)
            cancelPhase(context, entry.id, PHASE_ONGOING)
        }
    }

    /** Lines up the first alert that falls after `after`, or clears
     *  the slot once there are none left before the class starts. */
    fun armNextReminder(context: Context, entryId: String, startAt: Long, endAt: Long, after: Long) {
        val next = ScheduleMath.nextAlertAt(
            startAt,
            ScheduleStore.getLeadMinutes(context),
            ScheduleStore.getRepeatMinutes(context),
            after
        )
        if (next != null) {
            arm(context, entryId, PHASE_REMIND, next, startAt, endAt)
        } else {
            cancelPhase(context, entryId, PHASE_REMIND)
        }
    }

    /** Clears every alarm for a class and takes down its notification
     *  if one is showing. */
    fun cancelOne(context: Context, entryId: String) {
        cancelPhase(context, entryId, PHASE_REMIND)
        cancelPhase(context, entryId, PHASE_ONGOING)
        cancelPhase(context, entryId, PHASE_END)
        ClassForegroundService.dismiss(context, entryId)
    }

    private fun requestCode(entryId: String, phase: String): Int = (entryId + "_" + phase).hashCode()

    private fun pendingIntent(context: Context, entryId: String, phase: String, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(entryId, phase),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun arm(context: Context, entryId: String, phase: String, at: Long, startAt: Long, endAt: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_PHASE, phase)
            putExtra(EXTRA_ENTRY_ID, entryId)
            putExtra(EXTRA_START_AT, startAt)
            putExtra(EXTRA_END_AT, endAt)
            putExtra(EXTRA_ALERT_AT, at)
        }
        val pending = pendingIntent(context, entryId, phase, intent)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAt = maxOf(at, System.currentTimeMillis() + 2_000)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
    }

    private fun cancelPhase(context: Context, entryId: String, phase: String) {
        val pending = pendingIntent(context, entryId, phase, Intent(context, ReminderReceiver::class.java))
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pending)
    }
}
