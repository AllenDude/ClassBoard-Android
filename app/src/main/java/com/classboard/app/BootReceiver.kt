package com.classboard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android wipes all scheduled alarms whenever the phone restarts. This
 * puts them all back the moment the phone finishes booting, so a
 * restart doesn't silently break every future reminder.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}
