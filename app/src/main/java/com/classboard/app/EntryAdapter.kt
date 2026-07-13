package com.classboard.app

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EntryAdapter(
    private var entries: List<ScheduleEntry>,
    private var theme: ThemeColors,
    private val onEdit: (ScheduleEntry) -> Unit,
    private val onDelete: (ScheduleEntry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.ViewHolder>() {

    private val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val subjectText: TextView = view.findViewById(R.id.subjectText)
        val metaText: TextView = view.findViewById(R.id.metaText)
        val editBtn: View = view.findViewById(R.id.editBtn)
        val deleteBtn: View = view.findViewById(R.id.deleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.subjectText.text = entry.subject
        holder.subjectText.setTextColor(theme.ink)
        holder.metaText.text = "${dayNames[entry.day]} · ${fmt12(entry.start)}–${fmt12(entry.end)}" +
            if (entry.room.isNotBlank()) " · ${entry.room}" else ""
        holder.metaText.setTextColor(theme.slate)

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * holder.root.resources.displayMetrics.density
            setColor(theme.panel)
        }
        holder.root.background = bg

        holder.editBtn.setOnClickListener { onEdit(entry) }
        holder.deleteBtn.setOnClickListener { onDelete(entry) }
    }

    override fun getItemCount(): Int = entries.size

    fun update(newEntries: List<ScheduleEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    fun updateTheme(newTheme: ThemeColors) {
        theme = newTheme
        notifyDataSetChanged()
    }

    private fun fmt12(hhmm: String): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return String.format("%d:%02d %s", hour12, m, period)
    }
}
