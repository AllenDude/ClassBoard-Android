package com.classboard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android wipes all scheduled alarms whenever the phone restarts, and
 * alarms set for a wall-clock time go stale when the clock or time
 * zone changes. This puts them all back in each of those cases, and
 * after the app itself is updated.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> AlarmScheduler.scheduleAll(context)
        }
    }
}
