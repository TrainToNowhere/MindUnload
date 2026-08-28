package com.app.mindunload.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.app.mindunload.R
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.Priority
import com.app.mindunload.ui.theme.HitTarget
import com.app.mindunload.ui.theme.PlannerColors
import com.app.mindunload.ui.theme.Radius
import com.app.mindunload.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Header row with drawer button + title, as in every bottom-tab screen of the prototype. */
@Composable
fun ScreenHeader(title: String, subtitle: String?, onOpenDrawer: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        OutlinedButton(
            onClick = onOpenDrawer,
            shape = RoundedCornerShape(Radius.md),
            border = BorderStroke(1.dp, PlannerColors.outline),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = PlannerColors.surface),
            // Zero padding is required, not optional: M3's default (24×8 dp) would eat
            // a 34 dp button whole and clip the icon canvas to a single dot — that's
            // exactly what this build first showed.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            // Compact on purpose: this button sits beside the title and shares a row,
            // a full 48 dp pushes the title off-screen on narrow phones. Surrounding
            // Spacing.l provides the touch slop that the M3 minimum usually covers.
            modifier = Modifier.size(HitTarget.compactHeader),
        ) {
            MenuIcon(tint = PlannerColors.muted)
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
                color = PlannerColors.text,
            )
            // Blank counts as absent: an empty subtitle would still add its line and the
            // 2 dp gap, which pushes the title above the center of the header row.
            if (!subtitle.isNullOrBlank()) {
                // 2 dp, not [Spacing.xs] — reserved for "no perceptible gap" between a
                // heading and its helper line.
                Spacer(Modifier.size(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.muted
                )
            }
        }
    }
}

/**
 * Header row with a back chevron, for detail-like screens without bottom nav. With [title]
 * given, the chevron and the screen's headline share one row (mirrors [ScreenHeader]'s
 * menu-button-plus-title layout) instead of the headline sitting on its own line below —
 * the plain "‹ Zurück" label is only a fallback for callers that pass no title.
 */
@Composable
fun BackHeader(
    label: String = stringResource(R.string.action_back),
    title: String? = null,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(onClick = onBack)
            .padding(vertical = 6.dp),
    ) {
        BackChevronIcon(tint = PlannerColors.muted)
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
                color = PlannerColors.text,
            )
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, color = PlannerColors.muted)
        }
    }
}

/**
 * "Show completed" filter for the list screens. Completed entries leave the lists a day
 * after being checked off — this brings them back. Nothing is ever deleted, so the chip
 * always has the full history behind it; it is a view switch, not a recovery feature.
 */
@Composable
fun ShowDoneFilter(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = { Text(stringResource(R.string.filter_show_done)) },
        modifier = modifier,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PlannerColors.muted,
        // Mirrors the 2 dp "no gap" reservation in [ScreenHeader]. Labels align with
        // the column edge; a real spacing token would create visible misalignment.
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

/** Readable label for a recurrence rule, e.g. "monatlich" or "alle 2 Wochen". */
@Composable
fun recurrenceLabel(rule: String): String {
    val parts = rule.lowercase().split(":")
    val n = parts.getOrNull(1)?.toIntOrNull() ?: 1
    return when (parts[0]) {
        "daily" -> if (n > 1) stringResource(
            R.string.recur_daily_n,
            n
        ) else stringResource(R.string.recur_daily)

        "weekly" -> if (n > 1) stringResource(
            R.string.recur_weekly_n,
            n
        ) else stringResource(R.string.recur_weekly)

        "monthly" -> if (n > 1) stringResource(
            R.string.recur_monthly_n,
            n
        ) else stringResource(R.string.recur_monthly)

        "monthly_weekday" -> stringResource(R.string.recur_monthly_weekday)
        "yearly" -> if (n > 1) stringResource(
            R.string.recur_yearly_n,
            n
        ) else stringResource(R.string.recur_yearly)

        else -> rule
    }
}

@Composable
fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.HIGH -> stringResource(R.string.priority_high)
    Priority.MEDIUM -> stringResource(R.string.priority_medium)
    Priority.LOW -> stringResource(R.string.priority_low)
    Priority.NONE -> ""
}

fun formatDateTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** Renders "heute"/"morgen"/"überfällig seit …"/"Fr, 10. Juli" — as in the prototype. */
@Composable
fun formatRelativeDue(epochMillis: Long, now: LocalDate = LocalDate.now()): String {
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    val timePart = if (time.hour != 0 || time.minute != 0) {
        ", " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        ""
    }
    return when {
        date.isBefore(now) ->
            stringResource(
                R.string.due_overdue_since,
                date.format(DateTimeFormatter.ofPattern("d. MMMM", locale))
            )

        date.isEqual(now) -> stringResource(R.string.due_today) + timePart
        date.isEqual(now.plusDays(1)) -> stringResource(R.string.due_tomorrow) + timePart
        else -> {
            val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            "$weekday, ${date.format(DateTimeFormatter.ofPattern("d. MMMM", locale))}$timePart"
        }
    }
}

/**
 * The line every card closes with: category, priority, due date/time and recurrence on the
 * left, the creation date on the right. Shared by all lists so the cards read identically —
 * the title area above stays free of metadata.
 *
 * When [item] is overdue and not [PlannerItem.done], the plain "überfällig seit …" text
 * is *replaced* by a [StatusBadge]; the rest of the metadata flows in the same row, so
 * the card height does not change and no callsite needs to know.
 */
@Composable
fun CardMetaRow(
    item: PlannerItem,
    modifier: Modifier = Modifier,
    showListName: Boolean = false,
    /** Off on the category screen — there every card carries the same category. */
    showCategory: Boolean = true,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    // Pull the overdue decision up here so we don't re-compute it twice (once for the
    // plain meta list, once for the badge).
    val isOverdue = !item.done && item.dueAt != null &&
            Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalDate().isBefore(today)
    val meta = buildList {
        if (showListName) item.listName?.let { add(it) }
        if (showCategory) item.category?.let { add(it) }
        if (item.priority != Priority.NONE) add(priorityLabel(item.priority))
        // Replace the plain "überfällig seit …" with the StatusBadge *only* when overdue;
        // otherwise keep the existing relative-due text untouched.
        if (item.dueAt != null && !isOverdue) add(formatRelativeDue(item.dueAt))
        item.recurrence?.let { add("↻ " + recurrenceLabel(it)) }
        // The AI's auto-research suggestion: a subtle hint in the list.
        if (item.researchSuggested && !item.done) add(stringResource(R.string.research_badge))
    }.joinToString(" · ")
    Row(
        modifier
            .fillMaxWidth()
            // 6 dp (not [Spacing.s]) — the meta line lives inside a card already padded
            // by [Spacing.l]; a real token-width gap stacks visually.
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        // [weight(1f, fill = false)] lets the meta shrink to fit, while the right-hand
        // creation-date Text always lands at its natural width on the right. The badge
        // lives *before* the plain meta, so for a single overdue task the line reads as:
        // [ÜBERFÄLLIG] [· category · priority · research]
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isOverdue) {
                StatusBadge(
                    text = stringResource(R.string.status_overdue),
                    kind = StatusKind.OVERDUE,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.mutedLight,
                )
            }
        }
        Text(
            formatDateTime(item.createdAt).substringBefore(" "),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.faint,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

@Composable
fun ItemRow(
    item: PlannerItem,
    showListName: Boolean,
    onClick: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PlannerColors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.done, onCheckedChange = onToggleDone)
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        item.quantity?.let { append("$it ") }
                        append(item.title)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    // Animated: a slow fade into the "done" colour marks the change as
                    // intentional, which a snap-replace does not — the user just tapped
                    // a checkbox and the row went half-dead instantly otherwise.
                    color = animateDoneColor(
                        isDone = item.done,
                        base = PlannerColors.text,
                    ).value,
                )
                CardMetaRow(item, showListName = showListName)
            }
        }
    }
}

/**
 * Fades a foreground colour into [PlannerColors.faint] when [isDone] is true and back to
 * [base] otherwise. Centralised so every list uses the same 180 ms timing — a half-second
 * fade reads as sluggish on a checkbox tap.
 *
 * Returns a [State] (use `.value` at the call site) so callers stay backwards-compatible
 * with the [androidx.compose.ui.graphics.Color] parameter they previously passed to
 * `color =` on [androidx.compose.material3.Text] etc.
 */
@Composable
fun animateDoneColor(isDone: Boolean, base: androidx.compose.ui.graphics.Color): androidx.compose.runtime.State<androidx.compose.ui.graphics.Color> =
    animateColorAsState(
        targetValue = if (isDone) PlannerColors.faint else base,
        animationSpec = tween(durationMillis = 180),
        label = "done-color",
    )
