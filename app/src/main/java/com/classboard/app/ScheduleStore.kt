package com.classboard.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Everything about saving/loading schedules and settings on this
 * phone. No server, no internet — just this phone's own storage.
 *
 * The app starts with no schedules at all; nothing is pre-filled.
 */
object ScheduleStore {
    private const val PREFS = "class_board_prefs"

    private const val KEY_SCHEDULES = "schedules_json"
    private const val KEY_ACTIVE = "active_schedule_id"
    private const val KEY_LEAD_MINUTES = "lead_minutes"
    private const val KEY_REPEAT_MINUTES = "repeat_minutes"
    private const val KEY_VIEW_MODE = "view_mode"

    // Keys written by the single-schedule version of the app. Read once
    // to carry existing classes over, then removed.
    private const val LEGACY_SCHEDULE = "schedule_json"
    private const val LEGACY_SEMESTER = "semester_label"
    private const val LEGACY_PROGRAM = "program_label"
    private const val LEGACY_TODOS = "todos_json"
    private const val LEGACY_PROGRAM_PLACEHOLDER = "Add your program"

    const val MAX_LEAD_MINUTES = 180
    const val DEFAULT_LEAD_MINUTES = 15
    const val DEFAULT_REPEAT_MINUTES = 5

    const val VIEW_TABLE = "table"
    const val VIEW_LIST = "list"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun newId(): String = UUID.randomUUID().toString()

    // ---------------- Schedules ----------------

    fun getSchedules(context: Context): List<Schedule> {
        val p = prefs(context)
        val raw = p.getString(KEY_SCHEDULES, null) ?: return migrateLegacy(p)
        return parseSchedules(raw)
    }

    fun saveSchedules(context: Context, schedules: List<Schedule>) {
        prefs(context).edit().putString(KEY_SCHEDULES, toJsonArray(schedules).toString()).apply()
    }

    fun addSchedule(context: Context, name: String, note: String, alertsEnabled: Boolean): Schedule {
        val schedule = Schedule(newId(), name, note, alertsEnabled, emptyList())
        saveSchedules(context, getSchedules(context) + schedule)
        return schedule
    }

    /** Replaces the stored schedule that has the same id. */
    fun updateSchedule(context: Context, schedule: Schedule) {
        saveSchedules(context, getSchedules(context).map { if (it.id == schedule.id) schedule else it })
    }

    fun deleteSchedule(context: Context, scheduleId: String) {
        saveSchedules(context, getSchedules(context).filter { it.id != scheduleId })
    }

    /** Copies a schedule and all its classes under new ids. */
    fun duplicateSchedule(context: Context, scheduleId: String): Schedule? {
        val all = getSchedules(context)
        val source = all.firstOrNull { it.id == scheduleId } ?: return null
        val copy = source.copy(
            id = newId(),
            name = (source.name.ifBlank { "Untitled schedule" } + " copy"),
            entries = source.entries.map { it.copy(id = newId()) }
        )
        saveSchedules(context, all + copy)
        return copy
    }

    // ---------------- Classes ----------------

    /** Adds each class to the schedule, or replaces the one that
     *  already has its id. */
    fun upsertEntries(context: Context, scheduleId: String, entries: List<ScheduleEntry>) {
        saveSchedules(context, getSchedules(context).map { schedule ->
            if (schedule.id != scheduleId) {
                schedule
            } else {
                val merged = schedule.entries.toMutableList()
                entries.forEach { entry ->
                    val index = merged.indexOfFirst { it.id == entry.id }
                    if (index >= 0) merged[index] = entry else merged.add(entry)
                }
                schedule.copy(entries = merged)
            }
        })
    }

    fun deleteEntry(context: Context, scheduleId: String, entryId: String) {
        saveSchedules(context, getSchedules(context).map { schedule ->
            if (schedule.id != scheduleId) schedule
            else schedule.copy(entries = schedule.entries.filter { it.id != entryId })
        })
    }

    /** The class with this id and the schedule it belongs to. */
    fun findEntry(context: Context, entryId: String): Pair<Schedule, ScheduleEntry>? {
        getSchedules(context).forEach { schedule ->
            val entry = schedule.entries.firstOrNull { it.id == entryId }
            if (entry != null) return schedule to entry
        }
        return null
    }

    // ---------------- Settings ----------------

    fun getActiveId(context: Context): String? = prefs(context).getString(KEY_ACTIVE, null)

    fun setActiveId(context: Context, scheduleId: String?) {
        prefs(context).edit().putString(KEY_ACTIVE, scheduleId).apply()
    }

    /** How many minutes before a class its alerts begin. */
    fun getLeadMinutes(context: Context): Int =
        prefs(context).getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES).coerceIn(1, MAX_LEAD_MINUTES)

    fun setLeadMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_LEAD_MINUTES, minutes.coerceIn(1, MAX_LEAD_MINUTES)).apply()
    }

    /** How often alerts repeat until the class starts. 0 = no repeat. */
    fun getRepeatMinutes(context: Context): Int =
        prefs(context).getInt(KEY_REPEAT_MINUTES, DEFAULT_REPEAT_MINUTES).coerceIn(0, MAX_LEAD_MINUTES)

    fun setRepeatMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_REPEAT_MINUTES, minutes.coerceIn(0, MAX_LEAD_MINUTES)).apply()
    }

    fun getViewMode(context: Context): String =
        if (prefs(context).getString(KEY_VIEW_MODE, VIEW_TABLE) == VIEW_LIST) VIEW_LIST else VIEW_TABLE

    fun setViewMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_VIEW_MODE, mode).apply()
    }

    // ---------------- Backup ----------------

    /** Every schedule on this phone, as one backup file. */
    fun exportBackupJson(context: Context): String {
        val obj = JSONObject()
        obj.put("app", "class-board")
        obj.put("version", 2)
        obj.put("exportedAt", java.time.Instant.now().toString())
        obj.put("schedules", toJsonArray(getSchedules(context)))
        return obj.toString(2)
    }

    /**
     * Adds the schedules found in a backup file next to the ones
     * already here — nothing existing is replaced. Understands both
     * the current multi-schedule format and the older single-schedule
     * one. Returns what was added; empty if the file wasn't a backup.
     */
    fun importBackupJson(context: Context, json: String): List<Schedule> {
        val incoming: List<Schedule> = try {
            val obj = JSONObject(json)
            val many = obj.optJSONArray("schedules")
            val single = obj.optJSONArray("schedule")
            when {
                many != null -> parseSchedules(many.toString())
                single != null -> listOf(
                    Schedule(
                        id = "",
                        name = text(obj, "semester"),
                        note = text(obj, "program").let {
                            if (it.startsWith(LEGACY_PROGRAM_PLACEHOLDER)) "" else it
                        },
                        alertsEnabled = true,
                        entries = parseEntries(single.toString())
                    )
                )
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (incoming.isEmpty()) return emptyList()

        // Fresh ids throughout, so importing the same file twice (or a
        // backup of schedules that are still here) can't collide.
        val fresh = incoming.map { schedule ->
            schedule.copy(
                id = newId(),
                name = schedule.name.ifBlank { "Imported schedule" },
                entries = schedule.entries.map { it.copy(id = newId()) }
            )
        }
        saveSchedules(context, getSchedules(context) + fresh)
        return fresh
    }

    // ---------------- Internals ----------------

    private fun toJsonArray(schedules: List<Schedule>): JSONArray {
        val arr = JSONArray()
        schedules.forEach { arr.put(it.toJson()) }
        return arr
    }

    private fun parseSchedules(raw: String): List<Schedule> = try {
        val arr = JSONArray(raw)
        val out = mutableListOf<Schedule>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Schedule.fromJson(o))
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseEntries(raw: String): List<ScheduleEntry> = try {
        val arr = JSONArray(raw)
        val out = mutableListOf<ScheduleEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(ScheduleEntry.fromJson(o))
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    private fun text(o: JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "").trim()

    /**
     * First run after updating from the single-schedule version: the
     * classes saved there become one schedule so nothing is lost, and
     * the old keys (including the removed to-do list) are cleared.
     * On a fresh install there is nothing to carry over and the app
     * simply starts empty.
     */
    private fun migrateLegacy(p: SharedPreferences): List<Schedule> {
        val result = mutableListOf<Schedule>()
        val legacy = p.getString(LEGACY_SCHEDULE, null)
        if (legacy != null) {
            val entries = parseEntries(legacy)
            if (entries.isNotEmpty()) {
                val name = (p.getString(LEGACY_SEMESTER, null) ?: "").trim().ifBlank { "My schedule" }
                val program = (p.getString(LEGACY_PROGRAM, null) ?: "").trim()
                val note = if (program.startsWith(LEGACY_PROGRAM_PLACEHOLDER)) "" else program
                result.add(Schedule(newId(), name, note, true, entries))
            }
        }
        p.edit()
            .putString(KEY_SCHEDULES, toJsonArray(result).toString())
            .remove(LEGACY_SCHEDULE)
            .remove(LEGACY_SEMESTER)
            .remove(LEGACY_PROGRAM)
            .remove(LEGACY_TODOS)
            .apply()
        return result
    }
}
