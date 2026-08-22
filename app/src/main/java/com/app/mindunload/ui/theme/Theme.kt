package com.app.mindunload.ui.theme

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

    /**
     * Status colours, named semantically rather than by appearance. Today they map 1:1
     * to [primary]/[overdue]/[soon]/[faint], but having their own names means callers say
     * "this row is overdue" instead of "this row is overdue-coloured". When the system
     * later wants to move overdue from terracotta to amber — or take "done-as-faint" off
     * the table — only the constant values change, not every screen that uses them.
     */
    val statusActive = primary
    val statusOverdue = overdue
    val statusSoon = soon
    val statusDone = faint

    /** Background tint for status pills. Mirrors [chipBg] today, kept separate so a
     *  later re-tint doesn't yank the chips along with it. */
    val statusBgActive = Color(0xFFE0E8E1)
    val statusBgOverdue = Color(0xFFF4E4DA)
    val statusBgSoon = Color(0xFFF6E9DD)
    val statusBgDone = Color(0xFFF1EEE7)

    // Dark-theme variant: same green accents, dark neutral surfaces (not specified
    // 1:1 in the prototype, but derived analogously).
    val backgroundDark = Color(0xFF1C1B18)
    val surfaceDark = Color(0xFF262523)
    val outlineDark = Color(0xFF3A3835)
    val textDark = Color(0xFFEDEBE4)
    val mutedDark = Color(0xFFA9A399)
    val primaryDark = Color(0xFF7FA98D)

    /**
     * Briefing-card background in dark mode. The light-mode "dark green" panel
     * ([darkCard]) would land at roughly the same luminance as [backgroundDark] and
     * dissolve into the page; the lighter surface tone keeps the panel clearly framed
     * while still reading as "this is the dark variant of the dark card".
     */
    val darkCardDarkMode = Color(0xFF354236)
    val onDarkCardDarkMode = Color(0xFFEDEBE4)
    val darkCardMutedDarkMode = Color(0xFFB1C5BA)
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
    // primaryContainer had been the *dark* green — readable on a light surface but
    // invisible against backgroundDark. The light variant ([primaryDark] = #7FA98D)
    // gives proper contrast for FilterChip/AssistChip on dark backgrounds.
    primaryContainer = PlannerColors.primaryDark,
    onPrimaryContainer = PlannerColors.backgroundDark,
    // secondaryContainer: dark mode now uses the lighter panel tone so AssistChips and
    // any other secondary fills stay visibly distinct from [backgroundDark].
    secondaryContainer = PlannerColors.darkCardDarkMode,
    onSecondaryContainer = PlannerColors.onDarkCardDarkMode,
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
fun MindUnloadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography.copy(
            // headlineSmall: the editorial signature — italic serif only here, every
            // screen reads its title through this style.
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = PlannerHeadlineFont,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
            ),
            // headlineMedium was italic serif; the italic is dropped because the only
            // callsite (Usage cost number) needs precise digit-shapes for legibility.
            // Serif stays so the hero number reads in the same family as headlineSmall.
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = PlannerHeadlineFont,
                fontStyle = FontStyle.Normal,
                fontWeight = FontWeight.Medium,
            ),
            // titleLarge was italic serif; italic is dropped because the only callsite
            // (drawer masthead) sits in a tall, dense panel and the italic swept the text
            // off-balance. Serif stays — the masthead should still feel like the same
            // publication as the headline.
            titleLarge = MaterialTheme.typography.titleLarge.copy(
                fontFamily = PlannerHeadlineFont,
                fontStyle = FontStyle.Normal,
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
