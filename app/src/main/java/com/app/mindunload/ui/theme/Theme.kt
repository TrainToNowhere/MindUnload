package com.app.mindunload.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.app.mindunload.data.ColorPalette
import com.app.mindunload.data.DarkModePreference

/**
 * Live theme selection (Settings → Design). A plain mutable-state singleton rather than a
 * CompositionLocal or ViewModel-scoped flow: [PlannerColors] itself is a singleton read
 * directly from ~20 screens (see its own doc comment), so the selection driving it needs
 * the same global reach. [com.app.mindunload.ui.MainActivity] seeds this from
 * [com.app.mindunload.ai.SettingsStore] before the first composition; [SettingsViewModel]
 * (in ViewModels.kt) updates both the store and this object when the user changes it.
 */
object AppTheme {
    var palette by mutableStateOf(ColorPalette.DEFAULT)
    var darkMode by mutableStateOf(DarkModePreference.DEFAULT)
}

/**
 * Per-palette accent tones. Deliberately narrow: only the colors that make a palette read
 * as "its own theme" (primary, chip fills, the dark briefing-card panel) vary; the
 * neutral background/surface/text tones are the same warm/dark editorial pair for every
 * palette (see [PlannerColors]'s own comment on why those are fixed rather than dynamic).
 */
private data class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryDark: Color,
    val chipBg: Color,
    val chipText: Color,
    val statusBgActive: Color,
    val darkCard: Color,
    val onDarkCard: Color,
    val darkCardMuted: Color,
    val darkCardDarkMode: Color,
    val onDarkCardDarkMode: Color,
    val darkCardMutedDarkMode: Color,
)

private val paletteAccents: Map<ColorPalette, AccentTones> = mapOf(
    // The original "Alltagsplaner Prototyp v2" green — unchanged from before palettes existed.
    ColorPalette.WARM to AccentTones(
        primary = Color(0xFF3E624F),
        onPrimary = Color(0xFFF6F4EF),
        primaryDark = Color(0xFF7FA98D),
        chipBg = Color(0xFFEDF0EA),
        chipText = Color(0xFF4A5A50),
        statusBgActive = Color(0xFFE0E8E1),
        darkCard = Color(0xFF2E3B33),
        onDarkCard = Color(0xFFEDEBE4),
        darkCardMuted = Color(0xFF9DB2A4),
        darkCardDarkMode = Color(0xFF354236),
        onDarkCardDarkMode = Color(0xFFEDEBE4),
        darkCardMutedDarkMode = Color(0xFFB1C5BA),
    ),
    ColorPalette.OCEAN to AccentTones(
        primary = Color(0xFF2F6690),
        onPrimary = Color(0xFFF3F7FA),
        primaryDark = Color(0xFF7FAFD1),
        chipBg = Color(0xFFE7EFF4),
        chipText = Color(0xFF335A72),
        statusBgActive = Color(0xFFDCE8EF),
        darkCard = Color(0xFF223B4B),
        onDarkCard = Color(0xFFE7EFF4),
        darkCardMuted = Color(0xFF9AB7C9),
        darkCardDarkMode = Color(0xFF2B4759),
        onDarkCardDarkMode = Color(0xFFE7EFF4),
        darkCardMutedDarkMode = Color(0xFFAAC9DB),
    ),
    ColorPalette.VIOLET to AccentTones(
        primary = Color(0xFF6A4E7C),
        onPrimary = Color(0xFFF6F3F8),
        primaryDark = Color(0xFFB79ACB),
        chipBg = Color(0xFFF0E9F3),
        chipText = Color(0xFF5A3F6C),
        statusBgActive = Color(0xFFEBE0F0),
        darkCard = Color(0xFF3C2E48),
        onDarkCard = Color(0xFFF0E9F3),
        darkCardMuted = Color(0xFFBBA2C8),
        darkCardDarkMode = Color(0xFF493A57),
        onDarkCardDarkMode = Color(0xFFF0E9F3),
        darkCardMutedDarkMode = Color(0xFFCBB4D6),
    ),
    ColorPalette.SLATE to AccentTones(
        primary = Color(0xFF4A5568),
        onPrimary = Color(0xFFF5F5F3),
        primaryDark = Color(0xFFA6AEBD),
        chipBg = Color(0xFFE9EBEF),
        chipText = Color(0xFF3D4658),
        statusBgActive = Color(0xFFE3E5EA),
        darkCard = Color(0xFF2E333F),
        onDarkCard = Color(0xFFE9EBEF),
        darkCardMuted = Color(0xFFA3A9B8),
        darkCardDarkMode = Color(0xFF394050),
        onDarkCardDarkMode = Color(0xFFE9EBEF),
        darkCardMutedDarkMode = Color(0xFFB4BAC8),
    ),
)

/**
 * The palette's own accent color, independent of whichever palette is currently applied —
 * used for the swatches in the Settings → Design palette picker (unlike [PlannerColors],
 * which only ever holds the *currently selected* palette's colors).
 */
fun ColorPalette.previewColor(): Color = paletteAccents.getValue(this).primary

/**
 * Color tokens for MindUnload. Neutral tones (background/surface/text/…) are Compose-state
 * backed and swap between the warm-light and dark set as [applyTheme] is called; accent
 * tones additionally vary by [ColorPalette]. Every field stays a plain `PlannerColors.xxx`
 * read at every call site — none of the ~20 screens that already read this object needed
 * to change when palettes and live dark mode were added.
 */
object PlannerColors {

    // --- Neutral tones: same across every palette, switched between light/dark by
    // [applyTheme]. The dark variants for mutedLight/faint/divider (mutedLightDark,
    // faintDark, dividerDark below) follow the same relative-brightness step as their
    // light counterparts — they were not part of the original dark-mode work, which only
    // covered background/surface/outline/text/muted.
    var background by mutableStateOf(Neutrals.LIGHT_BACKGROUND)
        private set
    var surface by mutableStateOf(Neutrals.LIGHT_SURFACE)
        private set
    var outline by mutableStateOf(Neutrals.LIGHT_OUTLINE)
        private set
    var text by mutableStateOf(Neutrals.LIGHT_TEXT)
        private set
    var muted by mutableStateOf(Neutrals.LIGHT_MUTED)
        private set
    var mutedLight by mutableStateOf(Neutrals.LIGHT_MUTED_LIGHT)
        private set
    var faint by mutableStateOf(Neutrals.LIGHT_FAINT)
        private set
    var divider by mutableStateOf(Neutrals.LIGHT_DIVIDER)
        private set

    // Status colors are semantic, not brand — fixed regardless of palette or dark mode.
    val overdue = Color(0xFFA9502B)
    val soon = Color(0xFFD99070)
    val statusBgOverdue = Color(0xFFF4E4DA)
    val statusBgSoon = Color(0xFFF6E9DD)
    val statusBgDone = Color(0xFFF1EEE7)

    // --- Accent tones: vary with the selected [ColorPalette]; see [paletteAccents].
    var primary by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).primary)
        private set
    var onPrimary by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).onPrimary)
        private set
    var primaryDark by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).primaryDark)
        private set
    var chipBg by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).chipBg)
        private set
    var chipText by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).chipText)
        private set
    var statusBgActive by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).statusBgActive)
        private set
    var darkCard by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).darkCard)
        private set
    var onDarkCard by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).onDarkCard)
        private set
    var darkCardMuted by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).darkCardMuted)
        private set
    var darkCardDarkMode by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).darkCardDarkMode)
        private set
    var onDarkCardDarkMode by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).onDarkCardDarkMode)
        private set
    var darkCardMutedDarkMode by mutableStateOf(paletteAccents.getValue(ColorPalette.DEFAULT).darkCardMutedDarkMode)
        private set

    /**
     * Status colours, named semantically rather than by appearance — see the original
     * rationale below. [statusActive] is now a computed alias (not a snapshot at init)
     * since [primary] itself can change at runtime.
     */
    val statusActive: Color get() = primary
    val statusOverdue = overdue
    val statusSoon = soon
    val statusDone = faint

    /** Applies a palette + dark/light mode combination to every token above. */
    fun applyTheme(palette: ColorPalette, dark: Boolean) {
        background = if (dark) Neutrals.DARK_BACKGROUND else Neutrals.LIGHT_BACKGROUND
        surface = if (dark) Neutrals.DARK_SURFACE else Neutrals.LIGHT_SURFACE
        outline = if (dark) Neutrals.DARK_OUTLINE else Neutrals.LIGHT_OUTLINE
        text = if (dark) Neutrals.DARK_TEXT else Neutrals.LIGHT_TEXT
        muted = if (dark) Neutrals.DARK_MUTED else Neutrals.LIGHT_MUTED
        mutedLight = if (dark) Neutrals.DARK_MUTED_LIGHT else Neutrals.LIGHT_MUTED_LIGHT
        faint = if (dark) Neutrals.DARK_FAINT else Neutrals.LIGHT_FAINT
        divider = if (dark) Neutrals.DARK_DIVIDER else Neutrals.LIGHT_DIVIDER

        val a = paletteAccents.getValue(palette)
        primary = if (dark) a.primaryDark else a.primary
        onPrimary = if (dark) Neutrals.DARK_BACKGROUND else a.onPrimary
        primaryDark = a.primaryDark
        chipBg = a.chipBg
        chipText = a.chipText
        statusBgActive = a.statusBgActive
        darkCard = a.darkCard
        onDarkCard = a.onDarkCard
        darkCardMuted = a.darkCardMuted
        darkCardDarkMode = a.darkCardDarkMode
        onDarkCardDarkMode = a.onDarkCardDarkMode
        darkCardMutedDarkMode = a.darkCardMutedDarkMode
    }

}

/** Neutral tone constants for [PlannerColors] — a top-level object, not a companion:
 *  Kotlin does not allow a companion object inside a standalone `object`. */
private object Neutrals {
    val LIGHT_BACKGROUND = Color(0xFFF6F4EF)
    val LIGHT_SURFACE = Color(0xFFFFFFFF)
    val LIGHT_OUTLINE = Color(0xFFE9E4DC)
    val LIGHT_TEXT = Color(0xFF23211D)
    val LIGHT_MUTED = Color(0xFF98928A)
    val LIGHT_MUTED_LIGHT = Color(0xFFA29C93)
    val LIGHT_FAINT = Color(0xFFB4AEA4)
    val LIGHT_DIVIDER = Color(0xFFF1EEE7)

    val DARK_BACKGROUND = Color(0xFF1C1B18)
    val DARK_SURFACE = Color(0xFF262523)
    val DARK_OUTLINE = Color(0xFF3A3835)
    val DARK_TEXT = Color(0xFFEDEBE4)
    val DARK_MUTED = Color(0xFFA9A399)
    // Not part of the original dark-mode pass — derived to sit on the same
    // brightness step between DARK_MUTED and DARK_OUTLINE as their light equivalents.
    val DARK_MUTED_LIGHT = Color(0xFF8D877D)
    val DARK_FAINT = Color(0xFF57534C)
    val DARK_DIVIDER = Color(0xFF322F2B)
}

@Composable
private fun plannerLightColorScheme() = lightColorScheme(
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

@Composable
private fun plannerDarkColorScheme() = darkColorScheme(
    primary = PlannerColors.primary,
    onPrimary = PlannerColors.background,
    // primaryContainer had been the *dark* accent — readable on a light surface but
    // invisible against a dark background. The light variant ([PlannerColors.primary],
    // already resolved to the palette's light-on-dark tone here) gives proper contrast
    // for FilterChip/AssistChip on dark backgrounds.
    primaryContainer = PlannerColors.primary,
    onPrimaryContainer = PlannerColors.background,
    // secondaryContainer: dark mode uses the lighter panel tone so AssistChips and any
    // other secondary fills stay visibly distinct from the background.
    secondaryContainer = PlannerColors.darkCardDarkMode,
    onSecondaryContainer = PlannerColors.onDarkCardDarkMode,
    background = PlannerColors.background,
    onBackground = PlannerColors.text,
    surface = PlannerColors.surface,
    onSurface = PlannerColors.text,
    surfaceVariant = PlannerColors.surface,
    onSurfaceVariant = PlannerColors.muted,
    outline = PlannerColors.outline,
    secondary = PlannerColors.muted,
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
    darkTheme: Boolean = AppTheme.darkMode == DarkModePreference.DARK,
    content: @Composable () -> Unit,
) {
    // Synchronous, not a SideEffect: PlannerColors must already hold the right values
    // before `content()` below composes, or the first frame of every screen would render
    // with stale tokens and immediately recompose.
    PlannerColors.applyTheme(AppTheme.palette, darkTheme)
    val colorScheme = if (darkTheme) plannerDarkColorScheme() else plannerLightColorScheme()
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
