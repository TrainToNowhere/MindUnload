package com.app.mindunload.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design tokens for MindUnload.
 *
 * Centralised so the entire UI pulls from the same scale — individual screens no longer
 * pick their own pixel values, and a global re-tune is a one-file change. Tokens cover
 * the four things that visibly drift first when a designer works alone: spacing, corner
 * radii, interactive hit targets, and elevation.
 *
 * Rounding policy: where a legacy literal sat ±2 dp from the closest token (e.g. 14 dp
 * → [Spacing.l] = 16 dp, or 11 dp → [Radius.md] = 12 dp) the token wins. A 1–2 dp drift
 * is invisible in the running app and the consistency gain outweighs the pixel-perfect
 * preservation. Where a value carries *semantic* meaning that the token scale does not
 * capture (border stroke widths, fixed icon canvas, drawer width) the literal stays
 * inline with a comment explaining why.
 */

/** Stepped spacing scale — the rhythm of every gap and inset. */
object Spacing {
    /** No gap; use when you deliberately want to zero out a Builder default. */
    val none = 0.dp

    /** Tightest gap (close-set metadata, vertical rhythm inside a row). */
    val xs = 4.dp

    /** Default gap between row items and between a title and its subtitle. */
    val s = 8.dp

    /**
     * Long-form alias of [Spacing.s]. Useful where the short form looks like a typo or
     * collides visually with another identifier.
     */
    val sm = s

    /** Group separator; between sub-sections inside a card or panel. */
    val m = 12.dp

    /** Outer card padding; primary screen-inset for body content. */
    val l = 16.dp

    /** Header/section inset; the fixed screen edge padding on phone layouts. */
    val xl = 20.dp

    /** Generous bottom inset so the last list item clears a gesture bar. */
    val xxl = 24.dp
}

/** Corner-radius scale, paired with [Spacing] in the same s/m/l/xl feel. */
object Radius {
    /** Caps, very small chips. */
    val xs = 4.dp

    /** Avatars, inline chips with text. */
    val sm = 8.dp

    /** Buttons, text-input shells, nav items. */
    val md = 12.dp

    /** Default card radius — matches the original [PlannerColors.surface] cards. */
    val lg = 16.dp

    /** Hero cards (briefing panel, usage dashboard). */
    val xl = 20.dp

    /** Reserved for fully-rounded pill shapes (chips, tags). Use sparingly. */
    val pill = 999.dp
}

/**
 * Interactive minimum sizes. M3 recommends 48 dp on every touch target; certain header
 * controls in this app deliberately drop to 34 dp (see [ScreenHeader]) because the inner
 * form would otherwise shove the title off-screen on narrow phones. The deviation is a
 * known compromise, not an oversight.
 */
object HitTarget {
    /** Standard M3 minimum — pick this for every IconButton unless a tighter variant
     *  is explicitly justified. */
    val min = 48.dp

    /**
     * Compact variant for the screen header menu button. Pairs a 34 dp visual button
     * with a 48 dp touch slop to keep a11y coverage without bloating the header height.
     * Only use where the surrounding column already provides the extra padding.
     */
    val compactHeader = 34.dp
}

/** Elevation scale; placeholder for future shadow work (currently the cards are flat). */
object Elevation {
    val none = 0.dp
    /** Reserved — M3 CardDefaults uses 1 dp on filled cards by default. */
    val card = 1.dp
}

/**
 * Line-icon defaults. Centralised so every [androidx.compose.foundation.Canvas]-based
 * icon in [com.app.mindunload.ui.PlannerIcons] pulls the same stroke weight and
 * rendered size. Without this constant the icon helper had hard-coded 21.dp size + 1.9.dp
 * stroke inline, and individual callsites could — and did — pass their own numbers.
 *
 * M3 default icons (Material Icons) are 24.dp; we sit one step down (21.dp) so they sit
 * visually lighter than the auto-flow M3 components would produce. Stroke is thicker
 * than M3's 1.5 dp by intent: the editorial palette is warm and quiet, so a slightly
 * heavier line keeps the icons legible at small sizes.
 */
object IconSize {
    /** Rendered icon box. M3 is 24.dp; we use 21.dp for a lighter, editorial feel. */
    val default = 21.dp

    /** Stroke width in the icon canvas. Slightly heavier than M3's 1.5 dp default. */
    val stroke = 1.9f

    /** Reserved: 28.dp icon box for empty-state and onboarding. */
    val large = 28.dp
}
