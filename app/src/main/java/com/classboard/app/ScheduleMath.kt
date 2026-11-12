package com.classboard.app

import java.util.Calendar
import java.util.Locale

/**
 * All the time arithmetic in one place, with no Android dependencies:
 * parsing and formatting clock times, finding when a class next
 * happens, working out when alerts fire, and arranging classes on the
 * timetable grid.
 */
object ScheduleMath {

    const val MINUTE_MS = 60_000L

    val DAY_SHORT = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val DAY_LONG = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    /** Day indexes in the order the app displays them: Monday first. */
    val WEEK_ORDER = listOf(1, 2, 3, 4, 5, 6, 0)

    // ---------------- Clock times ----------------

    /** "09:30" -> 570. Null if the text isn't a valid 24-hour time. */
    fun parseMinutes(hhmm: String): Int? {
        val parts = hhmm.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun toHHmm(minutes: Int): String {
        val safe = minutes.coerceIn(0, 24 * 60 - 1)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    fun normalizeTime(raw: String, fallback: String): String =
        parseMinutes(raw)?.let { toHHmm(it) } ?: fallback

    fun startMinutes(entry: ScheduleEntry): Int = parseMinutes(entry.start) ?: 0
    fun endMinutes(entry: ScheduleEntry): Int = parseMinutes(entry.end) ?: 0

    fun fmt12(minutes: Int): String {
        val safe = ((minutes % 1440) + 1440) % 1440
        val h = safe / 60
        val m = safe % 60
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d %s", hour12, m, period)
    }

    fun fmt12(hhmm: String): String = fmt12(parseMinutes(hhmm) ?: 0)

    /** "10:00 – 11:30 AM" when both ends share AM/PM, otherwise
     *  "10:00 AM – 1:00 PM". */
    fun timeRange(entry: ScheduleEntry): String {
        val s = startMinutes(entry)
        val e = endMinutes(entry)
        val samePeriod = (s >= 720) == (e >= 720)
        val startText = if (samePeriod) fmt12(s).substringBeforeLast(' ') else fmt12(s)
        return "$startText – ${fmt12(e)}"
    }

    /** Label for a whole hour on the timetable's time axis: "7 AM". */
    fun hourLabel(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return "$hour12 $period"
    }

    // ---------------- Occurrences ----------------

    data class Occurrence(val startAt: Long, val endAt: Long)

    /** How long one meeting lasts. A class whose end is numerically
     *  earlier than its start is treated as running past midnight. */
    fun durationMinutes(entry: ScheduleEntry): Int {
        val s = startMinutes(entry)
        val e = endMinutes(entry)
        return when {
            e > s -> e - s
            e < s -> e + 1440 - s
            else -> 1
        }
    }

    /**
     * The meeting of this class that is either happening right now or
     * coming up next — whichever ends soonest after `nowMillis`.
     * Measured entirely in the phone's own local time.
     */
    fun occurrence(entry: ScheduleEntry, nowMillis: Long): Occurrence {
        val startMin = startMinutes(entry)
        val duration = durationMinutes(entry)
        for (addDays in -1..7) {
            val c = Calendar.getInstance()
            c.timeInMillis = nowMillis
            c.add(Calendar.DAY_OF_YEAR, addDays)
            if (c.get(Calendar.DAY_OF_WEEK) - 1 != entry.day) continue
            c.set(Calendar.HOUR_OF_DAY, startMin / 60)
            c.set(Calendar.MINUTE, startMin % 60)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            val startAt = c.timeInMillis
            c.add(Calendar.MINUTE, duration)
            val endAt = c.timeInMillis
            if (endAt > nowMillis) return Occurrence(startAt, endAt)
        }
        // Not reachable for a valid day index; kept so the function
        // always returns something sensible.
        val fallback = nowMillis + 7L * 24 * 60 * MINUTE_MS
        return Occurrence(fallback, fallback + duration * MINUTE_MS)
    }

    /** "3d 4h", "2h 15m" or "12 min" — rounded up, so a class 30
     *  seconds away reads as "1 min" rather than "0 min". */
    fun countdown(millis: Long): String {
        val totalMin = if (millis <= 0) 0 else (millis + MINUTE_MS - 1) / MINUTE_MS
        val d = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "$m min"
        }
    }

    // ---------------- Alerts ----------------

    /**
     * Every alert for one class, as minutes-before-start: the first
     * one `leadMinutes` ahead, then one every `repeatMinutes` until
     * the class begins. A repeat of 0 means a single alert.
     */
    fun alertOffsets(leadMinutes: Int, repeatMinutes: Int): List<Int> {
        if (leadMinutes <= 0) return emptyList()
        if (repeatMinutes <= 0) return listOf(leadMinutes)
        val out = mutableListOf<Int>()
        var m = leadMinutes
        while (m > 0) {
            out.add(m)
            m -= repeatMinutes
        }
        return out
    }

    /** The first alert moment strictly after `after`, or null once
     *  there are none left before the class starts. */
    fun nextAlertAt(startAt: Long, leadMinutes: Int, repeatMinutes: Int, after: Long): Long? {
        if (leadMinutes <= 0) return null
        val first = startAt - leadMinutes * MINUTE_MS
        if (after < first) return first
        if (repeatMinutes <= 0) return null
        val step = repeatMinutes * MINUTE_MS
        val k = (after - first) / step + 1
        val t = first + k * step
        return if (t < startAt) t else null
    }

    /** Plain-language description of what the two alert settings add
     *  up to, shown under the controls in Settings. */
    fun alertSummary(leadMinutes: Int, repeatMinutes: Int): String {
        val offsets = alertOffsets(leadMinutes, repeatMinutes)
        val tail = "then one more when it starts."
        return when {
            offsets.size <= 1 ->
                "One alert $leadMinutes min before each class, $tail"
            offsets.size <= 6 -> {
                val head = offsets.dropLast(1).joinToString(", ")
                "Alerts $head and ${offsets.last()} min before each class, $tail"
            }
            else ->
                "${offsets.size} alerts before each class: one every $repeatMinutes min, " +
                    "starting $leadMinutes min ahead, $tail"
        }
    }

    /** The + / − buttons move the lead time in 5-minute steps, with
     *  1 minute as the floor. */
    fun stepLead(current: Int, direction: Int, max: Int): Int {
        val next = if (direction > 0) {
            (current / 5 + 1) * 5
        } else {
            val down = ((current - 1) / 5) * 5
            if (down < 1) 1 else down
        }
        return next.coerceIn(1, max)
    }

    /** The repeat intervals the + / − buttons step through. 0 = off. */
    private val REPEAT_STEPS = listOf(0, 1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)

    /** Moves the repeat interval one step up or down. It always stays
     *  shorter than the lead time — a repeat as long as the lead time
     *  would never fire a second alert. */
    fun stepRepeat(current: Int, direction: Int, leadMinutes: Int): Int {
        val allowed = REPEAT_STEPS.filter { it < leadMinutes }
        return if (direction > 0) {
            allowed.firstOrNull { it > current } ?: clampRepeat(current, leadMinutes)
        } else {
            allowed.lastOrNull { it < current } ?: 0
        }
    }

    /** Keeps a repeat interval valid for the given lead time. */
    fun clampRepeat(repeatMinutes: Int, leadMinutes: Int): Int = when {
        repeatMinutes <= 0 -> 0
        repeatMinutes < leadMinutes -> repeatMinutes
        else -> REPEAT_STEPS.lastOrNull { it < leadMinutes } ?: 0
    }

    // ---------------- Timetable layout ----------------

    /** One class placed on the grid. When classes on the same day
     *  overlap they sit side by side: `lane` of `lanes`. */
    data class Block(
        val entry: ScheduleEntry,
        val startMin: Int,
        val endMin: Int,
        val lane: Int,
        val lanes: Int
    )

    private class Pending(val entry: ScheduleEntry, val startMin: Int, val endMin: Int, var lane: Int)

    /** Places one day's classes, splitting the column wherever two
     *  or more of them overlap. */
    fun layoutDay(entries: List<ScheduleEntry>): List<Block> {
        val items = entries.map { e ->
            val s = startMinutes(e)
            val rawEnd = endMinutes(e)
            // A class running past midnight is drawn to the bottom of
            // its own day rather than wrapping.
            val end = if (rawEnd > s) rawEnd else 1440
            Pending(e, s, end, 0)
        }.sortedWith(compareBy({ it.startMin }, { it.endMin }))

        val out = mutableListOf<Block>()
        val cluster = mutableListOf<Pending>()
        val laneEnds = mutableListOf<Int>()
        var clusterEnd = -1

        fun flush() {
            val lanes = laneEnds.size
            cluster.forEach { out.add(Block(it.entry, it.startMin, it.endMin, it.lane, lanes)) }
            cluster.clear()
            laneEnds.clear()
            clusterEnd = -1
        }

        items.forEach { item ->
            if (cluster.isNotEmpty() && item.startMin >= clusterEnd) flush()
            var lane = laneEnds.indexOfFirst { it <= item.startMin }
            if (lane < 0) {
                laneEnds.add(item.endMin)
                lane = laneEnds.size - 1
            } else {
                laneEnds[lane] = item.endMin
            }
            item.lane = lane
            cluster.add(item)
            if (item.endMin > clusterEnd) clusterEnd = item.endMin
        }
        if (cluster.isNotEmpty()) flush()
        return out
    }

    /** First and last whole hour the grid needs to show. */
    fun hourRange(entries: List<ScheduleEntry>): Pair<Int, Int> {
        if (entries.isEmpty()) return 8 to 12
        var minStart = 1440
        var maxEnd = 0
        entries.forEach { e ->
            val s = startMinutes(e)
            val rawEnd = endMinutes(e)
            val end = if (rawEnd > s) rawEnd else 1440
            if (s < minStart) minStart = s
            if (end > maxEnd) maxEnd = end
        }
        val startHour = minStart / 60
        var endHour = (maxEnd + 59) / 60
        if (endHour <= startHour) endHour = startHour + 1
        return startHour to endHour.coerceAtMost(24)
    }

    /** Weekdays always, plus Saturday and Sunday only when something
     *  is scheduled on them. */
    fun visibleDays(entries: List<ScheduleEntry>): List<Int> {
        val used = entries.map { it.day }.toSet()
        return WEEK_ORDER.filter { it in 1..5 || it in used }
    }

    /** Gives each distinct subject a stable number, in the order it
     *  was first added, so its colour doesn't jump around. */
    fun subjectOrder(entries: List<ScheduleEntry>): Map<String, Int> {
        val order = LinkedHashMap<String, Int>()
        entries.forEach { e ->
            val key = subjectKey(e.subject)
            if (!order.containsKey(key)) order[key] = order.size
        }
        return order
    }

    fun subjectKey(subject: String): String = subject.trim().lowercase(Locale.ROOT)
}
