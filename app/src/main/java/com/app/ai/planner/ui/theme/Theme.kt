package com.app.ai.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Fixed warm color palette from the Claude design prototype ("Alltagsplaner Prototyp v2") —
 * deliberately no dynamic Material You color scheme, that would clash with the custom look.
 */
object PlannerColors {
    val background = Color(0xFFF6F4EF)
    val surface = Color(0xFFFFFFFF)
    val outline = Color(0xFFE9E4DC)
    val primary = Color(0xFF3E624F)
    val onPrimary = Color(0xFFF6F4EF)
    val darkCard = Color(0xFF2E3B33)
    val onDarkCard = Color(0xFFEDEBE4)
    val darkCardMuted = Color(0xFF9DB2A4)
    val text = Color(0xFF23211D)
    val muted = Color(0xFF98928A)
    val mutedLight = Color(0xFFA29C93)
    val faint = Color(0xFFB4AEA4)
    val overdue = Color(0xFFA9502B)
    val soon = Color(0xFFD99070)
    val chipBg = Color(0xFFEDF0EA)
    val chipText = Color(0xFF4A5A50)
    val divider = Color(0xFFF1EEE7)

    // Dark-theme variant: same green accents, dark neutral surfaces (not specified
    // 1:1 in the prototype, but derived analogously).
    val backgroundDark = Color(0xFF1C1B18)
    val surfaceDark = Color(0xFF262523)
    val outlineDark = Color(0xFF3A3835)
    val textDark = Color(0xFFEDEBE4)
    val mutedDark = Color(0xFFA9A399)
    val primaryDark = Color(0xFF7FA98D)
}

private val LightColors = lightColorScheme(
    primary = PlannerColors.primary,
    onPrimary = PlannerColors.onPrimary,
    // Set container colors explicitly, otherwise M3 components (e.g. the
    // TimePicker) fall back to the purple baseline scheme.
    primaryContainer = PlannerColors.chipBg,
    onPrimaryContainer = PlannerColors.primary,
    secondaryContainer = PlannerColors.chipBg,
    onSecondaryContainer = PlannerColors.chipText,
    background = PlannerColors.background,
    onBackground = PlannerColors.text,
    surface = PlannerColors.surface,
    onSurface = PlannerColors.text,
    surfaceVariant = PlannerColors.chipBg,
    onSurfaceVariant = PlannerColors.chipText,
    outline = PlannerColors.outline,
    secondary = PlannerColors.muted,
    error = PlannerColors.overdue,
)

private val DarkColors = darkColorScheme(
    primary = PlannerColors.primaryDark,
    onPrimary = PlannerColors.backgroundDark,
    primaryContainer = PlannerColors.primary,
    onPrimaryContainer = PlannerColors.onPrimary,
    secondaryContainer = PlannerColors.darkCard,
    onSecondaryContainer = PlannerColors.onDarkCard,
    background = PlannerColors.backgroundDark,
    onBackground = PlannerColors.textDark,
    surface = PlannerColors.surfaceDark,
    onSurface = PlannerColors.textDark,
    surfaceVariant = PlannerColors.surfaceDark,
    onSurfaceVariant = PlannerColors.mutedDark,
    outline = PlannerColors.outlineDark,
    secondary = PlannerColors.mutedDark,
    error = PlannerColors.overdue,
)

/**
 * Headline font in the style of "Newsreader" (italic serif) — deliberately a bundled font
 * instead of a Google Fonts download (no network dependency, no certificate risk).
 */
val PlannerHeadlineFont = FontFamily.Serif
val PlannerBodyFont = FontFamily.SansSerif

@Composable
fun AIPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography.copy(
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = PlannerHeadlineFont,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
            ),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = PlannerHeadlineFont,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
            ),
            titleLarge = MaterialTheme.typography.titleLarge.copy(
                fontFamily = PlannerHeadlineFont,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
            ),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = PlannerBodyFont),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = PlannerBodyFont),
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = PlannerBodyFont),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = PlannerBodyFont),
            titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = PlannerBodyFont),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = PlannerBodyFont),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = PlannerBodyFont),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = PlannerBodyFont),
        ),
        content = content,
    )
}
