package com.classboard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import java.util.Calendar

/**
 * The weekly timetable: days across the top, hours down the side,
 * and each class drawn as a block that starts and ends at its real
 * times. Classes that overlap on the same day sit side by side.
 * Tapping a block reports the class through [onEntryClick].
 */
class TimetableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onEntryClick: ((ScheduleEntry) -> Unit)? = null

    private var theme: ThemeColors = Themes.get("default")
    private var days: List<Int> = emptyList()
    private var blocksByDay: Map<Int, List<ScheduleMath.Block>> = emptyMap()
    private var subjectOrder: Map<String, Int> = emptyMap()
    private var startHour = 8
    private var endHour = 12

    private val gutterW = dp(48f)
    private val headerH = dp(40f)
    private val hourH = dp(58f)
    private val minColW = dp(58f)
    private val bottomPad = dp(12f)
    private val blockRadius = dp(7f)
    private val blockGap = dp(3f)
    private val stripeW = dp(3.5f)

    private val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val condensedBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1f) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(2f) }
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(13f)
        typeface = condensedBold
        textAlign = Paint.Align.CENTER
    }
    private val hourPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        typeface = condensed
        textAlign = Paint.Align.RIGHT
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        typeface = condensedBold
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        typeface = condensed
    }

    private val rect = RectF()
    private val hitAreas = ArrayList<Pair<RectF, ScheduleEntry>>()
    private var downTarget: ScheduleEntry? = null

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    /** Gives the view a schedule's classes to draw. */
    fun setData(entries: List<ScheduleEntry>, theme: ThemeColors) {
        this.theme = theme
        days = ScheduleMath.visibleDays(entries)
        blocksByDay = days.associateWith { day -> ScheduleMath.layoutDay(entries.filter { it.day == day }) }
        subjectOrder = ScheduleMath.subjectOrder(entries)
        val range = ScheduleMath.hourRange(entries)
        startHour = range.first
        endHour = range.second
        contentDescription = "Weekly timetable, ${entries.size} " +
            (if (entries.size == 1) "class" else "classes") +
            ". Switch to List for a readable version."
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (gutterW + days.size * minColW).toInt() + paddingLeft + paddingRight
        val specSize = MeasureSpec.getSize(widthMeasureSpec)
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> specSize
            MeasureSpec.AT_MOST -> minOf(desiredW, specSize)
            else -> desiredW
        }
        val height = (headerH + (endHour - startHour) * hourH + bottomPad).toInt() +
            paddingTop + paddingBottom
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hitAreas.clear()
        val n = days.size
        if (n == 0) return

        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val gridLeft = left + gutterW
        val gridTop = top + headerH
        val gridBottom = gridTop + (endHour - startHour) * hourH
        val colW = (right - gridLeft) / n

        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_WEEK) - 1
        val todayIndex = days.indexOf(today)

        // Today's column gets a faint wash so it's easy to find.
        if (todayIndex >= 0) {
            fillPaint.color = Themes.blend(theme.panel, theme.accent, 0.09f)
            rect.set(gridLeft + todayIndex * colW, top + dp(4f), gridLeft + (todayIndex + 1) * colW, gridBottom)
            canvas.drawRoundRect(rect, dp(10f), dp(10f), fillPaint)
        }

        // Day names.
        val headerBaseline = top + headerH / 2f - (headerPaint.ascent() + headerPaint.descent()) / 2f
        days.forEachIndexed { i, day ->
            headerPaint.color = if (i == todayIndex) theme.accent else theme.slate
            canvas.drawText(ScheduleMath.DAY_SHORT[day], gridLeft + i * colW + colW / 2f, headerBaseline, headerPaint)
        }

        // Hour rules and their labels.
        linePaint.color = theme.line
        hourPaint.color = theme.slate
        val hourTextOffset = (hourPaint.ascent() + hourPaint.descent()) / 2f
        for (hour in startHour..endHour) {
            val y = gridTop + (hour - startHour) * hourH
            canvas.drawLine(gridLeft, y, right, y, linePaint)
            canvas.drawText(ScheduleMath.hourLabel(hour), gridLeft - dp(8f), y - hourTextOffset, hourPaint)
        }

        // Class blocks.
        val minuteH = hourH / 60f
        val gridStartMin = startHour * 60
        days.forEachIndexed { i, day ->
            val blocks = blocksByDay[day] ?: emptyList()
            blocks.forEach { block ->
                val laneW = (colW - blockGap) / block.lanes
                val bLeft = gridLeft + i * colW + blockGap / 2f + block.lane * laneW
                val bRight = bLeft + laneW - blockGap / 2f
                val bTop = gridTop + (block.startMin - gridStartMin) * minuteH + dp(1f)
                val bBottom = gridTop + (block.endMin - gridStartMin) * minuteH - dp(1f)
                drawBlock(canvas, block.entry, bLeft, bTop, bRight, maxOf(bBottom, bTop + dp(6f)))
            }
        }

        // A line across today's column at the current time.
        if (todayIndex >= 0) {
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            if (nowMin >= gridStartMin && nowMin <= endHour * 60) {
                val y = gridTop + (nowMin - gridStartMin) * minuteH
                val x0 = gridLeft + todayIndex * colW
                nowPaint.color = theme.accent
                canvas.drawLine(x0, y, x0 + colW, y, nowPaint)
                fillPaint.color = theme.accent
                canvas.drawCircle(x0, y, dp(3.5f), fillPaint)
            }
        }
    }

    private fun drawBlock(canvas: Canvas, entry: ScheduleEntry, l: Float, t: Float, r: Float, b: Float) {
        val index = subjectOrder[ScheduleMath.subjectKey(entry.subject)] ?: 0
        val color = Themes.subjectColor(index)

        val area = RectF(l, t, r, b)
        hitAreas.add(area to entry)

        fillPaint.color = Themes.blockFill(theme, color)
        canvas.drawRoundRect(area, blockRadius, blockRadius, fillPaint)

        // Solid stripe down the left edge in the subject's colour.
        canvas.save()
        canvas.clipRect(l, t, l + stripeW, b)
        fillPaint.color = color
        canvas.drawRoundRect(area, blockRadius, blockRadius, fillPaint)
        canvas.restore()

        val padX = dp(5f)
        val padY = dp(4f)
        val textLeft = l + stripeW + padX
        val textWidth = (r - padX - textLeft).toInt()
        val availableH = b - t - padY * 2
        val titleLineH = titlePaint.fontSpacing
        if (textWidth < dp(16f) || availableH < titleLineH) return

        titlePaint.color = theme.ink
        val maxLines = maxOf(1, (availableH / titleLineH).toInt())
        val title = entry.subject.ifBlank { "Class" }
        val layout = StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.clipRect(l, t, r, b)
        canvas.translate(textLeft, t + padY)
        layout.draw(canvas)
        canvas.restore()

        // The room goes underneath when there's a spare line for it.
        val usedH = layout.height.toFloat()
        if (entry.room.isNotBlank() && availableH - usedH >= metaPaint.fontSpacing) {
            metaPaint.color = Themes.blend(theme.ink, theme.panel, 0.30f)
            val room = TextUtils.ellipsize(entry.room, metaPaint, textWidth.toFloat(), TextUtils.TruncateAt.END)
            canvas.drawText(room, 0, room.length, textLeft, t + padY + usedH - metaPaint.ascent(), metaPaint)
        }
    }

    private fun entryAt(x: Float, y: Float): ScheduleEntry? =
        hitAreas.lastOrNull { it.first.contains(x, y) }?.second

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTarget = entryAt(event.x, event.y)
                return downTarget != null
            }
            MotionEvent.ACTION_UP -> {
                val target = entryAt(event.x, event.y)
                val pressed = downTarget
                downTarget = null
                if (target != null && target == pressed) {
                    performClick()
                    onEntryClick?.invoke(target)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downTarget = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
