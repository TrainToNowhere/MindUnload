package com.app.ai.planner.ui

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
import com.app.ai.planner.R
import com.app.ai.planner.data.PlannerItem
import com.app.ai.planner.data.Priority
import com.app.ai.planner.ui.theme.PlannerColors
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
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(
            onClick = onOpenDrawer,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, PlannerColors.outline),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = PlannerColors.surface),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) {
            MenuIcon(tint = PlannerColors.muted)
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
                color = PlannerColors.text,
            )
            if (subtitle != null) {
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

/** Header row with a back chevron, for detail-like screens without bottom nav. */
@Composable
fun BackHeader(label: String = stringResource(R.string.action_back), onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(onClick = onBack)
            .padding(vertical = 6.dp),
    ) {
        BackChevronIcon(tint = PlannerColors.muted)
        Text(label, style = MaterialTheme.typography.labelLarge, color = PlannerColors.muted)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PlannerColors.muted,
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
 */
@Composable
fun CardMetaRow(
    item: PlannerItem,
    modifier: Modifier = Modifier,
    showListName: Boolean = false,
    /** Off on the category screen — there every card carries the same category. */
    showCategory: Boolean = true,
) {
    val meta = buildList {
        if (showListName) item.listName?.let { add(it) }
        if (showCategory) item.category?.let { add(it) }
        if (item.priority != Priority.NONE) add(priorityLabel(item.priority))
        item.dueAt?.let { add(formatRelativeDue(it)) }
        item.recurrence?.let { add("↻ " + recurrenceLabel(it)) }
        // The AI's auto-research suggestion: a subtle hint in the list.
        if (item.researchSuggested && !item.done) add(stringResource(R.string.research_badge))
    }.joinToString(" · ")
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        // fill = false: without metadata the row collapses and the date still sits right.
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.mutedLight,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            formatDateTime(item.createdAt).substringBefore(" "),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.faint,
            modifier = Modifier.padding(start = 8.dp),
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
                .padding(14.dp),
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
                    color = if (item.done) PlannerColors.faint else PlannerColors.text,
                )
                CardMetaRow(item, showListName = showListName)
            }
        }
    }
}
