package com.classboard.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * A real Android foreground service — the only mechanism the OS
 * actually guarantees can't be swiped away, in the notification shade
 * OR on the lock screen. A plain "ongoing" notification without a
 * backing service can still get dismissed on some phones/Android
 * versions; this can't.
 *
 * Precise timing (waking the phone at exactly the right moment) is
 * still handled by AlarmManager in AlarmScheduler — this service just
 * owns the notification while REMIND → ONGOING → END plays out.
 */
class ClassForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = intent ?: return START_NOT_STICKY
        val phase = data.getStringExtra("phase") ?: return START_NOT_STICKY
        val id = data.getStringExtra("id") ?: return START_NOT_STICKY
        val subject = data.getStringExtra("subject") ?: "Class"
        val room = data.getStringExtra("room") ?: ""
        val teacher = data.getStringExtra("teacher") ?: ""
        val day = data.getIntExtra("day", 1)
        val start = data.getStringExtra("start") ?: "00:00"
        val end = data.getStringExtra("end") ?: "00:00"
        val leadMinutes = data.getIntExtra("leadMinutes", 15)
        val notificationId = id.hashCode()

        when (phase) {
            "REMIND" -> {
                val title = "$subject in $leadMinutes min"
                val body = buildString {
                    append(fmt12(start)).append("\u2013").append(fmt12(end))
                    if (room.isNotBlank()) append(" \u00b7 Room $room")
                }
                startAsForeground(notificationId, title, body)
            }
            "ONGOING" -> {
                val title = "$subject \u2014 ongoing now"
                val body = buildString {
                    append("Ends at ").append(fmt12(end))
                    if (room.isNotBlank()) append(" \u00b7 Room $room")
                }
                // Calling startForeground again with the same id simply
                // updates the existing notification's content in place —
                // whether this service was already running (normal case)
                // or just freshly started (e.g. app was reopened and
                // re-armed mid-class), this always ends up correct.
                startAsForeground(notificationId, title, body)
            }
            "END" -> {
                NotificationHelper.cancel(this, notificationId)
                stopForeground(STOP_FOREGROUND_REMOVE)
                // This is the ONLY phase that re-arms next week's cycle —
                // by now the class has genuinely finished.
                val entry = ScheduleEntry(id, subject, room, teacher, day, start, end)
                AlarmScheduler.scheduleNextCycle(applicationContext, entry)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(notificationId: Int, title: String, body: String) {
        val notification = NotificationHelper.build(this, title, body)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun fmt12(hhmm: String): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return String.format("%d:%02d %s", hour12, m, period)
    }
}
