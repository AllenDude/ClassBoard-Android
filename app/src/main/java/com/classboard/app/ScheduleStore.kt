package com.classboard.app

import android.content.Context
import org.json.JSONArray
import java.util.Calendar

/**
 * Everything about saving/loading the schedule and settings on this
 * phone. No server, no internet — just this phone's own storage.
 */
object ScheduleStore {
    private const val PREFS = "class_board_prefs"
    private const val KEY_SCHEDULE = "schedule_json"
    private const val KEY_LEAD_MINUTES = "lead_minutes"
    private const val KEY_SEMESTER = "semester_label"

    fun getSchedule(context: Context): MutableList<ScheduleEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SCHEDULE, null)
        if (raw == null) {
            val seed = defaultSchedule()
            saveSchedule(context, seed)
            return seed.toMutableList()
        }
        val arr = JSONArray(raw)
        val list = mutableListOf<ScheduleEntry>()
        for (i in 0 until arr.length()) {
            list.add(ScheduleEntry.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    fun saveSchedule(context: Context, entries: List<ScheduleEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCHEDULE, arr.toString())
            .apply()
    }

    fun getLeadMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LEAD_MINUTES, 15)

    fun setLeadMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LEAD_MINUTES, minutes).apply()
    }

    fun getSemesterLabel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SEMESTER, "1st Sem") ?: "1st Sem"

    fun setSemesterLabel(context: Context, label: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEMESTER, label).apply()
    }

    /** The next real moment (as a Calendar) this class meeting happens,
     *  measured entirely in this phone's own local time — no timezone
     *  math needed at all, since it never leaves the phone. */
    fun nextOccurrence(entry: ScheduleEntry, from: Calendar = Calendar.getInstance()): Calendar {
        val (h, m) = entry.start.split(":").map { it.toInt() }
        for (addDays in 0..7) {
            val candidate = from.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, addDays)
            candidate.set(Calendar.HOUR_OF_DAY, h)
            candidate.set(Calendar.MINUTE, m)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)
            if (candidate.get(Calendar.DAY_OF_WEEK) - 1 == entry.day &&
                candidate.timeInMillis > from.timeInMillis
            ) {
                return candidate
            }
        }
        // Fallback: shouldn't happen, but return a week from now.
        val fallback = from.clone() as Calendar
        fallback.add(Calendar.DAY_OF_YEAR, 7)
        return fallback
    }

    private fun defaultSchedule(): List<ScheduleEntry> = listOf(
        ScheduleEntry("d1", "Purposive Communication", "R3", "Lopez, Rizza Mae Carlos", 1, "10:00", "11:30"),
        ScheduleEntry("d2", "Purposive Communication", "R3", "Lopez, Rizza Mae Carlos", 5, "10:00", "11:30"),
        ScheduleEntry("d3", "Intro to Computing", "R1", "Ancheta, Verson Labuguen", 2, "12:00", "13:00"),
        ScheduleEntry("d4", "Intro to Computing", "R1", "Ancheta, Verson Labuguen", 3, "12:00", "13:00"),
        ScheduleEntry("d5", "Intro to Computing (Lab)", "CSS Lab", "Ancheta, Verson Labuguen", 4, "07:00", "10:00"),
        ScheduleEntry("d6", "Climate Change & DRRM", "R3", "Montemayor, Joannalyn Sapon", 1, "15:00", "17:00"),
        ScheduleEntry("d7", "Gender and Society", "R4", "Mateo, Jhamie Tetz Infante", 3, "07:00", "08:30"),
        ScheduleEntry("d8", "Gender and Society", "R4", "Mateo, Jhamie Tetz Infante", 5, "07:00", "08:30"),
        ScheduleEntry("d9", "Fundamentals of Programming", "R6", "Feliciano, Catleen Glo Madayag", 5, "14:00", "16:00"),
        ScheduleEntry("d10", "Fundamentals of Programming (Lab)", "CL1", "Feliciano, Catleen Glo Madayag", 1, "07:00", "10:00"),
        ScheduleEntry("d11", "Mathematics in the Modern World", "R3", "Pua, Marianne Jane Antoinette D", 2, "13:00", "14:30"),
        ScheduleEntry("d12", "Mathematics in the Modern World", "R3", "Pua, Marianne Jane Antoinette D", 4, "13:00", "14:30"),
        ScheduleEntry("d13", "Health and Wellness Science", "Gym", "", 2, "14:30", "16:00"),
        ScheduleEntry("d14", "Health and Wellness Science", "Gym", "", 4, "14:30", "16:00"),
        ScheduleEntry("d15", "NSTP 1", "", "Valdez, Jean Manganip", 6, "08:00", "11:00"),
        ScheduleEntry("d16", "PE 1 — Movement Patterns", "Gym", "Hiloma, Jan Michael Vincent Franco", 3, "13:00", "15:00")
    )
}
