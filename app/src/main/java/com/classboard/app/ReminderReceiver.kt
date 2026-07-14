package com.classboard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Fires when one of the three OS alarms we scheduled goes off — works
 * identically whether the app has been open in the last five minutes
 * or the last five weeks.
 *
 * This receiver doesn't build notifications itself anymore — it just
 * hands the exact same phase/entry data straight to
 * ClassForegroundService, which is the only thing Android guarantees
 * can hold a truly unswipeable, always-current notification.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phase = intent.getStringExtra("phase") ?: return

        val serviceIntent = Intent(context, ClassForegroundService::class.java).apply {
            putExtra("phase", phase)
            putExtra("id", intent.getStringExtra("id"))
            putExtra("subject", intent.getStringExtra("subject"))
            putExtra("room", intent.getStringExtra("room"))
            putExtra("teacher", intent.getStringExtra("teacher"))
            putExtra("day", intent.getIntExtra("day", 1))
            putExtra("start", intent.getStringExtra("start"))
            putExtra("end", intent.getStringExtra("end"))
            putExtra("leadMinutes", intent.getIntExtra("leadMinutes", 15))
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
