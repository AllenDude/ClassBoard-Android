package com.classboard.app

import org.json.JSONObject

/**
 * One class meeting: a single subject on a single day of the week.
 * A subject that meets Mon & Wed is stored as two separate entries.
 *
 * day: 0=Sunday, 1=Monday, ... 6=Saturday (matches java.util.Calendar)
 * start / end: 24-hour "HH:MM", e.g. "09:30"
 */
data class ScheduleEntry(
    val id: String,
    val subject: String,
    val room: String,
    val teacher: String,
    val day: Int,
    val start: String,
    val end: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("subject", subject)
        put("room", room)
        put("teacher", teacher)
        put("day", day)
        put("start", start)
        put("end", end)
    }

    companion object {
        fun fromJson(o: JSONObject): ScheduleEntry = ScheduleEntry(
            id = o.optString("id", java.util.UUID.randomUUID().toString()),
            subject = o.optString("subject", ""),
            room = o.optString("room", ""),
            teacher = o.optString("teacher", ""),
            day = o.optInt("day", 1),
            start = o.optString("start", "08:00"),
            end = o.optString("end", "09:00")
        )
    }
}
