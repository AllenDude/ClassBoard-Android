package com.classboard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when the OS alarm we scheduled goes off — whether the app has
 * been open in the last five minutes or the last five weeks.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val subject = intent.getStringExtra("subject") ?: "Class"
        val room = intent.getStringExtra("room") ?: ""
        val teacher = intent.getStringExtra("teacher") ?: ""
        val day = intent.getIntExtra("day", 1)
        val start = intent.getStringExtra("start") ?: "00:00"
        val end = intent.getStringExtra("end") ?: "00:00"
        val leadMinutes = intent.getIntExtra("leadMinutes", 15)

        val title = "$subject in $leadMinutes min"
        val body = buildString {
            append(fmt12(start)).append("–").append(fmt12(end))
            if (room.isNotBlank()) append(" · Room $room")
        }

        NotificationHelper.show(context, id.hashCode(), title, body)

        // Re-arm this same class for its NEXT weekly occurrence, so
        // reminders keep going indefinitely without needing the app
        // reopened.
        val entry = ScheduleEntry(id, subject, room, teacher, day, start, end)
        AlarmScheduler.scheduleOne(context, entry)
    }

    private fun fmt12(hhmm: String): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return String.format("%d:%02d %s", hour12, m, period)
    }
}
