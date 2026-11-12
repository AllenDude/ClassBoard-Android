package com.classboard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when one of the OS alarms set by [AlarmScheduler] goes off —
 * works identically whether the app has been open in the last five
 * minutes or the last five weeks.
 *
 * The class is looked up fresh each time, so an alert always reflects
 * the schedule as it is now: a deleted class, or a schedule whose
 * alerts were switched off, stays quiet.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val phase = intent.getStringExtra(AlarmScheduler.EXTRA_PHASE) ?: return
        // "id" is what alarms set by the previous version carried.
        val entryId = intent.getStringExtra(AlarmScheduler.EXTRA_ENTRY_ID)
            ?: intent.getStringExtra("id")
            ?: return

        val found = ScheduleStore.findEntry(context, entryId)
        if (found == null || !found.first.alertsEnabled) {
            AlarmScheduler.cancelOne(context, entryId)
            return
        }
        val entry = found.second

        val startAt = intent.getLongExtra(AlarmScheduler.EXTRA_START_AT, 0L)
        val endAt = intent.getLongExtra(AlarmScheduler.EXTRA_END_AT, 0L)
        if (startAt <= 0L || endAt <= 0L) {
            // An alarm from before the update; start a clean cycle.
            AlarmScheduler.scheduleOne(context, entry)
            return
        }

        val now = System.currentTimeMillis()
        val where = if (entry.room.isNotBlank()) "  ·  ${entry.room}" else ""
        val name = entry.subject.ifBlank { "Class" }

        when (phase) {
            AlarmScheduler.PHASE_REMIND -> {
                if (now < startAt) {
                    ClassForegroundService.show(
                        context,
                        entryId,
                        "$name in ${ScheduleMath.countdown(startAt - now)}",
                        ScheduleMath.timeRange(entry) + where
                    )
                }
                val alertAt = intent.getLongExtra(AlarmScheduler.EXTRA_ALERT_AT, now)
                AlarmScheduler.armNextReminder(context, entryId, startAt, endAt, maxOf(now, alertAt))
            }

            AlarmScheduler.PHASE_ONGOING -> {
                if (now < endAt) {
                    ClassForegroundService.show(
                        context,
                        entryId,
                        "$name is starting now",
                        "Ends at ${ScheduleMath.fmt12(entry.end)}$where"
                    )
                }
            }

            AlarmScheduler.PHASE_END -> {
                ClassForegroundService.dismiss(context, entryId)
                // The only place next week's cycle is armed — by now
                // this meeting has genuinely finished.
                AlarmScheduler.scheduleOne(context, entry)
            }
        }
    }
}
