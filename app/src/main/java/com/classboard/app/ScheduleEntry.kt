package com.classboard.app

import org.json.JSONObject
import java.util.UUID

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
        /** Tolerant of missing or malformed fields, so a hand-edited
         *  or older backup file can't crash the app. */
        fun fromJson(o: JSONObject): ScheduleEntry = ScheduleEntry(
            id = text(o, "id").ifBlank { UUID.randomUUID().toString() },
            subject = text(o, "subject"),
            room = text(o, "room"),
            teacher = text(o, "teacher"),
            day = o.optInt("day", 1).coerceIn(0, 6),
            start = ScheduleMath.normalizeTime(text(o, "start"), "08:00"),
            end = ScheduleMath.normalizeTime(text(o, "end"), "09:00")
        )

        private fun text(o: JSONObject, key: String): String =
            if (o.isNull(key)) "" else o.optString(key, "").trim()
    }
}
