package org.krand.habitbuilder

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object HabitColors {
    val canvas = Color(0xFFF6F6F7)
    val ink = Color(0xFF25262A)
    val muted = Color(0xFF60646C)
    val line = Color(0xFFDFE1E5)
    val soft = Color(0xFFE9EAED)
    val charcoal = Color(0xFF3C414B)

    val light = lightColorScheme(
        primary = charcoal,
        onPrimary = Color.White,
        primaryContainer = soft,
        onPrimaryContainer = ink,
        secondary = Color(0xFF596474),
        onSecondary = Color.White,
        background = canvas,
        onBackground = ink,
        surface = Color.White,
        onSurface = ink,
        surfaceVariant = soft,
        onSurfaceVariant = muted,
        outline = line,
    )

    val dark = darkColorScheme(
        primary = Color(0xFFD0D5DE),
        onPrimary = Color(0xFF252930),
        primaryContainer = Color(0xFF3A3E46),
        onPrimaryContainer = Color(0xFFF0F1F3),
        secondary = Color(0xFFBFC3CB),
        onSecondary = Color(0xFF1C1D20),
        background = Color(0xFF1C1D20),
        onBackground = Color(0xFFF0F1F3),
        surface = Color(0xFF292B30),
        onSurface = Color(0xFFF0F1F3),
        surfaceVariant = Color(0xFF3A3E46),
        onSurfaceVariant = Color(0xFFBFC3CB),
        outline = Color(0xFF454951),
    )

    fun dayTint(color: DayColor, dark: Boolean): Color = when (color) {
        DayColor.NONE -> if (dark) Color(0xFF292B30) else Color.White
        DayColor.MINT -> if (dark) Color(0xFF304A2A) else Color(0xFFE4F1DE)
        DayColor.AMBER -> if (dark) Color(0xFF54441E) else Color(0xFFFFF0C9)
        DayColor.ROSE -> if (dark) Color(0xFF532F37) else Color(0xFFFBE1E3)
        DayColor.BLUE -> if (dark) Color(0xFF2E405C) else Color(0xFFE0EDFF)
    }
}
