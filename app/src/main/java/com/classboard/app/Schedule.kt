package com.classboard.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One named timetable — a semester, a summer term, a work rota. The
 * app can hold any number of these; each shows up as its own tab.
 *
 * alertsEnabled lets a schedule be kept around without it sending
 * reminders (last semester's classes, for example).
 */
data class Schedule(
    val id: String,
    val name: String,
    val note: String = "",
    val alertsEnabled: Boolean = true,
    val entries: List<ScheduleEntry> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("note", note)
        put("alertsEnabled", alertsEnabled)
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        put("entries", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): Schedule {
            val arr = o.optJSONArray("entries") ?: JSONArray()
            val list = mutableListOf<ScheduleEntry>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                list.add(ScheduleEntry.fromJson(item))
            }
            return Schedule(
                id = text(o, "id").ifBlank { UUID.randomUUID().toString() },
                name = text(o, "name"),
                note = text(o, "note"),
                alertsEnabled = o.optBoolean("alertsEnabled", true),
                entries = list
            )
        }

        private fun text(o: JSONObject, key: String): String =
            if (o.isNull(key)) "" else o.optString(key, "").trim()
    }
}
