package com.classboard.app

import android.content.Context
import org.json.JSONArray
import java.util.Calendar

object TodoStore {
    private const val PREFS = "class_board_prefs"
    private const val KEY_TODOS = "todos_json"

    fun getTodos(context: Context): MutableList<TodoItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TODOS, null)
            ?: return mutableListOf()
        val arr = JSONArray(raw)
        val list = mutableListOf<TodoItem>()
        for (i in 0 until arr.length()) list.add(TodoItem.fromJson(arr.getJSONObject(i)))
        return list
    }

    fun saveTodos(context: Context, items: List<TodoItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TODOS, arr.toString()).apply()
    }

    fun addTodo(context: Context, item: TodoItem) {
        val list = getTodos(context)
        list.add(item)
        saveTodos(context, list)
    }

    fun deleteTodo(context: Context, id: String) {
        val list = getTodos(context)
        list.removeAll { it.id == id }
        saveTodos(context, list)
    }

    fun setCompleted(context: Context, id: String, completed: Boolean) {
        val list = getTodos(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(completed = completed)
            saveTodos(context, list)
        }
    }

    /** The moment this to-do is due: either the soonest upcoming class
     *  of one specific subject, or the soonest upcoming class at all.
     *  Returns null if that can't be resolved anymore (e.g. its
     *  subject was deleted from the schedule entirely). */
    fun computeDeadline(context: Context, item: TodoItem): Calendar? {
        val schedule = ScheduleStore.getSchedule(context)
        val candidates = if (item.dueMode == "SUBJECT") {
            schedule.filter { it.subject == item.subjectName }
        } else {
            schedule
        }
        if (candidates.isEmpty()) return null

        var soonest: Calendar? = null
        candidates.forEach { entry ->
            val at = ScheduleStore.nextOccurrence(entry)
            if (soonest == null || at.before(soonest)) soonest = at
        }
        return soonest
    }

    /** Drops any to-do whose deadline has already passed (this is the
     *  "auto-deletes once the time is past" behavior — independent of
     *  whether the person ever checked it off), and returns what's
     *  left sorted soonest-due-first. */
    fun pruneAndSort(context: Context): List<Pair<TodoItem, Calendar?>> {
        val now = Calendar.getInstance()
        val list = getTodos(context)
        val kept = mutableListOf<TodoItem>()
        val withDeadlines = mutableListOf<Pair<TodoItem, Calendar?>>()

        list.forEach { item ->
            val deadline = computeDeadline(context, item)
            if (deadline != null && deadline.after(now)) {
                kept.add(item)
                withDeadlines.add(item to deadline)
            }
            // else: deadline already passed, or its subject no longer
            // exists — either way, quietly drop it.
        }

        if (kept.size != list.size) saveTodos(context, kept)
        return withDeadlines.sortedBy { it.second?.timeInMillis ?: Long.MAX_VALUE }
    }
}
