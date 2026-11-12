package com.classboard.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/**
 * The visual building blocks shared by every screen and dialog:
 * rounded surfaces, pressable backgrounds, and [paint], which colours
 * a whole layout from the tags on its views.
 */
object Ui {

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(
        context: Context,
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeDp: Float = 1f
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        setColor(color)
        if (strokeColor != null) setStroke(dp(context, strokeDp), strokeColor)
    }

    fun oval(color: Int, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokeColor != null && strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** A rounded surface that shows a ripple when touched. `on` is the
     *  colour of whatever sits on top of it (text or icon). */
    fun pressable(
        context: Context,
        color: Int,
        radiusDp: Float,
        on: Int,
        strokeColor: Int? = null
    ): Drawable = RippleDrawable(
        ColorStateList.valueOf(Themes.withAlpha(on, 0.18f)),
        rounded(context, color, radiusDp, strokeColor),
        rounded(context, Color.WHITE, radiusDp)
    )

    /** Background for an icon-only button: nothing at rest, a round
     *  ripple when touched. */
    fun iconRipple(on: Int): Drawable = RippleDrawable(
        ColorStateList.valueOf(Themes.withAlpha(on, 0.18f)),
        null,
        oval(Color.WHITE)
    )

    /**
     * Colours a view and everything inside it. Layouts don't hard-code
     * any colours; each view that needs one carries a tag instead:
     *
     *   page / card / inset / divider   surfaces
     *   ink / slate / accent / danger   text colours
     *   primary / secondary / textbtn   buttons
     *   step                            stepper and picker buttons
     *   icon                            icon buttons
     *   field                           text inputs
     *   switch                          on/off switches
     */
    fun paint(view: View, t: ThemeColors) {
        val c = view.context
        when (view.tag as? String) {
            "page" -> view.setBackgroundColor(t.bg)
            "card" -> view.background = rounded(c, t.panel, 20f)
            "inset" -> view.background = rounded(c, t.panel2, 14f)
            "divider" -> view.setBackgroundColor(t.line)

            "ink" -> (view as? TextView)?.setTextColor(t.ink)
            "slate" -> (view as? TextView)?.setTextColor(t.slate)
            "accent" -> (view as? TextView)?.setTextColor(t.accent)
            "danger" -> (view as? TextView)?.setTextColor(t.danger)

            "primary" -> {
                view.background = pressable(c, t.accent, 16f, t.accentText)
                (view as? TextView)?.setTextColor(t.accentText)
            }
            "secondary" -> {
                view.background = pressable(c, t.panel2, 16f, t.ink)
                (view as? TextView)?.setTextColor(t.ink)
            }
            "textbtn" -> {
                view.background = pressable(c, Color.TRANSPARENT, 12f, t.accent)
                (view as? TextView)?.setTextColor(t.accent)
            }
            "step" -> {
                view.background = pressable(c, t.panel2, 14f, t.ink)
                (view as? TextView)?.setTextColor(t.ink)
            }
            "icon" -> {
                view.background = iconRipple(t.ink)
                (view as? ImageView)?.setColorFilter(t.ink)
            }
            "field" -> (view as? EditText)?.let {
                it.setTextColor(t.ink)
                it.setHintTextColor(t.slate)
                it.background = rounded(c, t.panel2, 14f)
            }
            "switch" -> (view as? SwitchCompat)?.let {
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                it.thumbTintList = ColorStateList(states, intArrayOf(t.accent, t.slate))
                it.trackTintList = ColorStateList(
                    states,
                    intArrayOf(Themes.blend(t.panel2, t.accent, 0.5f), t.line)
                )
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) paint(view.getChildAt(i), t)
        }
    }
}
