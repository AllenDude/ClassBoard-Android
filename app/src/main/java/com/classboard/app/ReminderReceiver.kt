package com.classboard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when one of the three OS alarms we scheduled goes off — works
 * identically whether the app has been open in the last five minutes
 * or the last five weeks.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phase = intent.getStringExtra("phase") ?: return
        val id = intent.getStringExtra("id") ?: return
        val subject = intent.getStringExtra("subject") ?: "Class"
        val room = intent.getStringExtra("room") ?: ""
        val teacher = intent.getStringExtra("teacher") ?: ""
        val day = intent.getIntExtra("day", 1)
        val start = intent.getStringExtra("start") ?: "00:00"
        val end = intent.getStringExtra("end") ?: "00:00"
        val leadMinutes = intent.getIntExtra("leadMinutes", 15)
        val notificationId = id.hashCode()

        when (phase) {
            "REMIND" -> {
                val title = "$subject in $leadMinutes min"
                val body = buildString {
                    append(fmt12(start)).append("\u2013").append(fmt12(end))
                    if (room.isNotBlank()) append(" \u00b7 Room $room")
                }
                NotificationHelper.show(context, notificationId, title, body, ongoing = true)
            }
            "ONGOING" -> {
                val title = "$subject \u2014 ongoing now"
                val body = buildString {
                    append("Ends at ").append(fmt12(end))
                    if (room.isNotBlank()) append(" \u00b7 Room $room")
                }
                NotificationHelper.show(context, notificationId, title, body, ongoing = true)
            }
            "END" -> {
                NotificationHelper.cancel(context, notificationId)
                // This is the ONLY phase that re-arms next week's cycle —
                // by now the class has genuinely finished, so searching
                // for "the next occurrence" correctly skips to next week
                // instead of finding today's class again.
                val entry = ScheduleEntry(id, subject, room, teacher, day, start, end)
                AlarmScheduler.scheduleNextCycle(context, entry)
            }
        }
    }

    private fun fmt12(hhmm: String): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return String.format("%d:%02d %s", hour12, m, period)
    }
}
