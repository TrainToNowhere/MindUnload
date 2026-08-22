package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.mindunload.ui.theme.PlannerColors
import com.app.mindunload.ui.theme.Radius

/**
 * Categorical status of an item, mapped to a colour pair via [PlannerColors.statusBg*] and
 * [PlannerColors.status*]. Names describe the *situation*, not the colour — when the
 * palette moves overdue from terracotta to amber, callers don't need to rename.
 *
 * The mapping today is intentionally identical to the existing [PlannerColors.primary] /
 * [PlannerColors.overdue] / [PlannerColors.soon] / [PlannerColors.faint] palette; the
 * separate types mean existing screens can migrate at their own pace.
 */
enum class StatusKind { ACTIVE, OVERDUE, SOON, DONE }

/**
 * Small uppercase pill that announces a row's status (e.g. "Überfällig", "Heute", "Erledigt").
 * Use in [CardMetaRow] adjacent positions or next to a card title, never in body text — its
 * compact label-style means it scans quickly but ends abruptly mid-sentence.
 *
 * The pill hugs its label (`wrapContentSize`-style by virtue of [Text]'s natural sizing)
 * and never stretches the parent row. Keep the [text] under roughly 16 characters or it
 * starts to dominate the row.
 */
@Composable
fun StatusBadge(
    text: String,
    kind: StatusKind,
    modifier: Modifier = Modifier,
) {
    StatusBadgeInternal(
        text = text,
        bg = backgroundFor(kind),
        fg = foregroundFor(kind),
        modifier = modifier,
    )
}

/**
 * Variant for callers that already determined their colour pair (e.g. via a repository
 * call) — keeps the visual contract the same while skipping the enum mapping. Use sparingly;
 * the [StatusKind] overload covers 95 % of callsites.
 */
@Composable
fun StatusBadge(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
) {
    StatusBadgeInternal(text = text, bg = bg, fg = fg, modifier = modifier)
}

@Composable
private fun StatusBadgeInternal(text: String, bg: Color, fg: Color, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(bg)
            // 8/4 dp pill is deliberately tighter than the existing 11/5 dp chip in
            // [Tag] (which appears in DetailScreen) — a status badge competes with the
            // card title for attention, so it needs less ink.
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun backgroundFor(kind: StatusKind): Color = when (kind) {
    StatusKind.ACTIVE -> PlannerColors.statusBgActive
    StatusKind.OVERDUE -> PlannerColors.statusBgOverdue
    StatusKind.SOON -> PlannerColors.statusBgSoon
    StatusKind.DONE -> PlannerColors.statusBgDone
}

private fun foregroundFor(kind: StatusKind): Color = when (kind) {
    StatusKind.ACTIVE -> PlannerColors.statusActive
    StatusKind.OVERDUE -> PlannerColors.statusOverdue
    StatusKind.SOON -> PlannerColors.statusSoon
    StatusKind.DONE -> PlannerColors.statusDone
}
