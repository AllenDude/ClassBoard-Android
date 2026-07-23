package com.classboard.app

import org.json.JSONObject
import java.util.UUID

/**
 * A single to-do item. Its deadline isn't a plain date — it's tied to
 * your class calendar: either "whatever class you have next" (dueMode
 * = ANY), or "the next meeting of this specific subject" (dueMode =
 * SUBJECT, with subjectName telling us which one).
 */
data class TodoItem(
    val id: String,
    val text: String,
    val dueMode: String, // "ANY" or "SUBJECT"
    val subjectName: String?,
    val completed: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("dueMode", dueMode)
        put("subjectName", subjectName ?: JSONObject.NULL)
        put("completed", completed)
    }

    companion object {
        fun fromJson(o: JSONObject): TodoItem = TodoItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            text = o.optString("text", ""),
            dueMode = o.optString("dueMode", "ANY"),
            subjectName = if (o.isNull("subjectName")) null else o.optString("subjectName", null),
            completed = o.optBoolean("completed", false)
        )
    }
}
