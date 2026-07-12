package com.classboard.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Hands each class's reminder time directly to the Android OS's own
 * alarm system. This is the piece that makes reminders survive the
 * app being closed for weeks — the OS itself wakes the phone up at
 * the right moment, the same mechanism a real alarm clock app uses.
 */
object AlarmScheduler {

    fun scheduleAll(context: Context) {
        val entries = ScheduleStore.getSchedule(context)
        entries.forEach { scheduleOne(context, it) }
    }

    fun scheduleOne(context: Context, entry: ScheduleEntry) {
        val leadMinutes = ScheduleStore.getLeadMinutes(context)
        val occurrence = ScheduleStore.nextOccurrence(entry)
        val fireTime = occurrence.clone() as Calendar
        fireTime.add(Calendar.MINUTE, -leadMinutes)

        // If subtracting the lead time pushes it into the past (e.g. a
        // class starting in 5 minutes with a 15-minute lead), just fire
        // right away instead of skipping this occurrence entirely.
        val now = Calendar.getInstance()
        if (fireTime.before(now)) {
            fireTime.timeInMillis = now.timeInMillis + 5_000
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", entry.id)
            putExtra("subject", entry.subject)
            putExtra("room", entry.room)
            putExtra("teacher", entry.teacher)
            putExtra("day", entry.day)
            putExtra("start", entry.start)
            putExtra("end", entry.end)
            putExtra("leadMinutes", leadMinutes)
        }

        val requestCode = entry.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // No exact-alarm permission yet — fall back to an
                // approximate alarm rather than silently doing nothing.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, fireTime.timeInMillis, pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, fireTime.timeInMillis, pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, fireTime.timeInMillis, pendingIntent
            )
        }
    }

    fun cancelOne(context: Context, entryId: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = entryId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}
