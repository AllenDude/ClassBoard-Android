package com.classboard.app

import android.content.Context
import android.graphics.Color

data class ThemeColors(
    val bg: Int,
    val panel: Int,
    val panel2: Int,
    val accent: Int,
    val accentText: Int,
    val ink: Int,
    val slate: Int
) {
    /** True for the pastel themes, whose background is light. */
    val isLight: Boolean get() = Themes.luminance(bg) > 0.5f

    /** Hairlines: timetable grid, outlines, dividers. */
    val line: Int get() = Themes.blend(panel2, slate, if (isLight) 0.30f else 0.26f)

    /** Destructive actions and validation messages. */
    val danger: Int get() = if (isLight) 0xFFD6364B.toInt() else 0xFFFF6B6B.toInt()
}

object Themes {
    private const val PREFS = "class_board_prefs"
    private const val THEME_KEY = "theme_key"
    private const val DEFAULT_KEY = "default"

    val all: Map<String, ThemeColors> = linkedMapOf(
        "default" to ThemeColors(0xFF12151C.toInt(), 0xFF1C212B.toInt(), 0xFF242A37.toInt(), 0xFFFFB020.toInt(), 0xFF1A1400.toInt(), 0xFFEEF0F3.toInt(), 0xFF7A8699.toInt()),
        "blue" to ThemeColors(0xFF0D1420.toInt(), 0xFF151F30.toInt(), 0xFF1C2A40.toInt(), 0xFF4DA3FF.toInt(), 0xFF06182B.toInt(), 0xFFEAF2FB.toInt(), 0xFF7793B3.toInt()),
        "blue_pastel" to ThemeColors(0xFFEEF3FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFE3EDFA.toInt(), 0xFF5B8DEF.toInt(), 0xFFFFFFFF.toInt(), 0xFF2B3A52.toInt(), 0xFF7D92B3.toInt()),
        "pink" to ThemeColors(0xFF1A0E17.toInt(), 0xFF261420.toInt(), 0xFF331A2B.toInt(), 0xFFFF5FA8.toInt(), 0xFF2E0919.toInt(), 0xFFFBEAF3.toInt(), 0xFFB17D99.toInt()),
        "pink_pastel" to ThemeColors(0xFFFDF1F6.toInt(), 0xFFFFFFFF.toInt(), 0xFFFBE3EE.toInt(), 0xFFEC6FA6.toInt(), 0xFFFFFFFF.toInt(), 0xFF4A2D3C.toInt(), 0xFFB58BA0.toInt()),
        "neon" to ThemeColors(0xFF08090C.toInt(), 0xFF0F1216.toInt(), 0xFF161A20.toInt(), 0xFF39FF8F.toInt(), 0xFF06210F.toInt(), 0xFFEAFFF2.toInt(), 0xFF5EA87D.toInt()),
        "neon_pastel" to ThemeColors(0xFFF4F0FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFECE3FB.toInt(), 0xFFB06FF7.toInt(), 0xFFFFFFFF.toInt(), 0xFF3C2D52.toInt(), 0xFF9887B8.toInt())
    )

    val labels: Map<String, String> = linkedMapOf(
        "default" to "Classic Amber",
        "blue" to "Blue",
        "blue_pastel" to "Blue Pastel",
        "pink" to "Pink",
        "pink_pastel" to "Pink Pastel",
        "neon" to "Neon",
        "neon_pastel" to "Neon Pastel"
    )

    /** One colour per subject on the timetable, cycled if there are
     *  more subjects than colours. Chosen to read on dark and light. */
    private val subjectColors = intArrayOf(
        0xFF4F8EF7.toInt(),
        0xFFF2994A.toInt(),
        0xFF2EB67D.toInt(),
        0xFFE0568F.toInt(),
        0xFF9B6CF2.toInt(),
        0xFF19B5C8.toInt(),
        0xFFE2B33C.toInt(),
        0xFFE5604C.toInt(),
        0xFF6C7DE0.toInt(),
        0xFF7DB548.toInt()
    )

    fun get(key: String?): ThemeColors = all[key] ?: all.getValue(DEFAULT_KEY)

    fun getSaved(context: Context): String {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(THEME_KEY, DEFAULT_KEY)
        return if (key != null && all.containsKey(key)) key else DEFAULT_KEY
    }

    fun save(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(THEME_KEY, key).apply()
    }

    fun subjectColor(index: Int): Int {
        val n = subjectColors.size
        return subjectColors[((index % n) + n) % n]
    }

    /** A soft tint of the subject colour for a timetable block. */
    fun blockFill(theme: ThemeColors, color: Int): Int =
        blend(theme.panel, color, if (theme.isLight) 0.18f else 0.26f)

    /** Mixes `amount` (0..1) of `to` into `from`. Result is opaque. */
    fun blend(from: Int, to: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        val r = Color.red(from) + (Color.red(to) - Color.red(from)) * a
        val g = Color.green(from) + (Color.green(to) - Color.green(from)) * a
        val b = Color.blue(from) + (Color.blue(to) - Color.blue(from)) * a
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun luminance(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
}
