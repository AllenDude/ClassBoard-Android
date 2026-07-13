package com.classboard.app

import android.content.Context

data class ThemeColors(
    val bg: Int,
    val panel: Int,
    val panel2: Int,
    val accent: Int,
    val accentText: Int,
    val ink: Int,
    val slate: Int
)

object Themes {
    private const val KEY = "class_board_prefs"
    private const val THEME_KEY = "theme_key"

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
        "default" to "Classic (Amber)",
        "blue" to "Blue",
        "blue_pastel" to "Blue Pastel",
        "pink" to "Pink",
        "pink_pastel" to "Pink Pastel",
        "neon" to "Neon",
        "neon_pastel" to "Neon Pastel"
    )

    fun getSaved(context: Context): String =
        context.getSharedPreferences(KEY, Context.MODE_PRIVATE).getString(THEME_KEY, "default") ?: "default"

    fun save(context: Context, key: String) {
        context.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit().putString(THEME_KEY, key).apply()
    }
}
