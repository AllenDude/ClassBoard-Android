package com.classboard.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * A real Android foreground service — the mechanism the OS guarantees
 * keeps a notification in place, in the shade and on the lock screen,
 * while a class is coming up or under way.
 *
 * Timing is handled by AlarmManager in [AlarmScheduler]; this service
 * only owns the notification. It always enters the foreground as soon
 * as it is started, which is what Android requires of a service
 * started this way.
 */
class ClassForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID).orEmpty()
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Class reminder"
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        val notificationId = notificationIdFor(entryId)
        val previous = activeEntryId

        try {
            val notification = NotificationHelper.build(this, title, body)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
            activeEntryId = entryId
            // A different class took over the slot (back-to-back
            // classes): clear the one that was showing before.
            if (previous != null && previous != entryId) {
                NotificationHelper.cancel(this, notificationIdFor(previous))
            }
            if (entryId.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } catch (e: Exception) {
            // The OS refused foreground status; a normal notification
            // is still better than silence.
            NotificationHelper.show(this, notificationId, title, body)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeEntryId = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "entryId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"

        /** The class whose notification the service is holding. */
        @Volatile
        private var activeEntryId: String? = null

        fun notificationIdFor(entryId: String): Int = entryId.hashCode()

        /** Shows (or updates) the alert for a class. */
        fun show(context: Context, entryId: String, title: String, body: String) {
            val intent = Intent(context, ClassForegroundService::class.java).apply {
                putExtra(EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                NotificationHelper.show(context, notificationIdFor(entryId), title, body)
            }
        }

        /** Removes a class's alert. The service is only stopped when
         *  it is this class it's holding — when one class ends as the
         *  next begins, the next one's alert must stay up. */
        fun dismiss(context: Context, entryId: String) {
            NotificationHelper.cancel(context, notificationIdFor(entryId))
            if (activeEntryId == entryId) {
                context.stopService(Intent(context, ClassForegroundService::class.java))
            }
        }
    }
}
