package com.ayushpant.habitquest.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Habit Quest's own look: the same colours on every phone (not the wallpaper colours), so the app is
 * recognisable and every colour keeps its meaning. White cards on a soft background, round corners.
 */

/** Brand colours, also used directly where a fixed meaning is needed. */
object Brand {
    val Teal = Color(0xFF0F8B6F)       // primary: progress, "done"
    val TealDark = Color(0xFF5FD6B5)
    val Amber = Color(0xFFF2A922)      // streaks, celebrations
    val Coral = Color(0xFFE5624F)      // late / missed (used sparingly, never shaming)
    val Ink = Color(0xFF1B1F1E)
}

private val Light = lightColorScheme(
    primary = Brand.Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F3EA),
    onPrimaryContainer = Color(0xFF00382B),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9C2),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF5B5BD6),
    tertiaryContainer = Color(0xFFE3E2FF),
    error = Color(0xFFC6402E),
    errorContainer = Color(0xFFFFDAD4),
    background = Color(0xFFF5F7F6),
    onBackground = Brand.Ink,
    surface = Color(0xFFF5F7F6),
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFE3E8E6),
    onSurfaceVariant = Color(0xFF59615E),
    outline = Color(0xFFB9C2BE),
    outlineVariant = Color(0xFFDDE3E0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFB),
    surfaceContainer = Color(0xFFF0F3F2),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF), // cards: white
)

private val Dark = darkColorScheme(
    primary = Brand.TealDark,
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF0B4F40),
    onPrimaryContainer = Color(0xFFD4F3EA),
    secondary = Color(0xFFFFC968),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF5E4100),
    onSecondaryContainer = Color(0xFFFFE9C2),
    tertiary = Color(0xFFC2C1FF),
    tertiaryContainer = Color(0xFF3A3A9E),
    error = Color(0xFFFFB4A8),
    errorContainer = Color(0xFF8C1D10),
    background = Color(0xFF0F1312),
    onBackground = Color(0xFFE1E5E3),
    surface = Color(0xFF0F1312),
    onSurface = Color(0xFFE1E5E3),
    surfaceVariant = Color(0xFF2A302E),
    onSurfaceVariant = Color(0xFFBEC6C2),
    outline = Color(0xFF5C6662),
    outlineVariant = Color(0xFF3A413F),
    surfaceContainerLowest = Color(0xFF0A0E0D),
    surfaceContainerLow = Color(0xFF161B1A),
    surfaceContainer = Color(0xFF1A201E),
    surfaceContainerHigh = Color(0xFF1E2422),
    surfaceContainerHighest = Color(0xFF222927), // cards
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), // cards
    large = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun HabitQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
