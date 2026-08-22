package com.app.mindunload.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.ui.theme.PlannerColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Survives rotation as "2026-08" — YearMonth itself is not a Bundle type. */
private val YearMonthSaver = Saver<YearMonth, String>(
    save = { it.toString() },
    restore = YearMonth::parse,
)

@Composable
fun AppointmentsScreen(
    onOpenDrawer: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: ItemsViewModel = viewModel(),
) {
    var month by rememberSaveable(stateSaver = YearMonthSaver) {
        mutableStateOf(YearMonth.now())
    }
    val appointments by remember(month) { viewModel.appointmentsOfMonth(month) }
        .collectAsState(initial = emptyList())

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    // Done ones stay visible: RecurrenceRoller closes every occurrence once it has passed,
    // so filtering them out would leave past months empty.
    val byDay = appointments
        .filter { it.dueAt != null }
        .groupBy { Instant.ofEpochMilli(it.dueAt!!).atZone(zone).toLocalDate() }

    // Every week the month touches, including the two that spill into the neighbouring months.
    val weeks = generateSequence(month.atDay(1).with(DayOfWeek.MONDAY)) { it.plusWeeks(1) }
        .takeWhile { it <= month.atEndOfMonth().with(DayOfWeek.MONDAY) }
        .toList()

    val currentWeekStart = today.with(DayOfWeek.MONDAY)
    // Collapsed by default, and a month switch starts over — except the current week,
    // which opens by itself so the tab is immediately useful without tapping anything.
    val expanded = remember(month) {
        mutableStateMapOf<LocalDate, Boolean>().apply {
            if (month == YearMonth.now()) put(currentWeekStart, true)
        }
    }
    var pendingDelete by remember { mutableStateOf<PlannerItem?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 8.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.tab_appointments),
                subtitle = stringResource(R.string.appointments_subtitle),
                onOpenDrawer = onOpenDrawer,
            )
        }
        item(key = "month-bar") {
            MonthBar(
                month = month,
                isCurrentMonth = month == YearMonth.now(),
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onToday = { month = YearMonth.now() },
            )
        }
        if (byDay.isEmpty()) {
            item {
                EmptyState(
                    message = stringResource(R.string.appointments_month_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }
        weeks.forEach { weekStart ->
            item(key = "week-$weekStart") {
                WeekCard(
                    weekStart = weekStart,
                    month = month,
                    today = today,
                    byDay = byDay,
                    isCurrentWeek = weekStart == currentWeekStart,
                    expanded = expanded[weekStart] == true,
                    onToggle = { expanded[weekStart] = expanded[weekStart] != true },
                    onItemClick = onItemClick,
                    onDeleteClick = { pendingDelete = it },
                )
            }
        }
    }

    pendingDelete?.let { item ->
        DeleteAppointmentDialog(
            item = item,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(item)
                pendingDelete = null
            },
        )
    }
}

/** Month switcher; the "today" shortcut only appears once you have navigated away. */
@Composable
private fun MonthBar(
    month: YearMonth,
    isCurrentMonth: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val locale = Locale.getDefault()
    val prevLabel = stringResource(R.string.appointments_month_prev)
    val nextLabel = stringResource(R.string.appointments_month_next)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.semantics {
                contentDescription = prevLabel
            },
        ) {
            BackChevronIcon(tint = PlannerColors.primary)
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
            style = MaterialTheme.typography.titleMedium,
            color = PlannerColors.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        if (!isCurrentMonth) {
            TextButton(onClick = onToday) {
                Text(stringResource(R.string.appointments_month_today))
            }
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier.semantics {
                contentDescription = nextLabel
            },
        ) {
            BackChevronIcon(
                modifier = Modifier.rotate(180f),
                tint = PlannerColors.primary,
            )
        }
    }
}

/**
 * One tile per week. Collapsed it is just the header plus how many appointments are in it;
 * expanded it lists the days with appointments and the free days still ahead.
 */
@Composable
private fun WeekCard(
    weekStart: LocalDate,
    month: YearMonth,
    today: LocalDate,
    byDay: Map<LocalDate, List<PlannerItem>>,
    isCurrentWeek: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onItemClick: (Long) -> Unit,
    onDeleteClick: (PlannerItem) -> Unit,
) {
    val locale = Locale.getDefault()
    val weekEnd = weekStart.plusDays(6)
    val days = (0L..6L).map(weekStart::plusDays)
    val apptDays = days.filter { byDay.containsKey(it) }
    // Days of the neighbouring months belong to their own month view, not this one.
    val freeDays = days.filter {
        YearMonth.from(it) == month && it >= today && !byDay.containsKey(it)
    }
    val count = apptDays.sumOf { byDay.getValue(it).size }

    Card(
        Modifier.fillMaxWidth(),
        // Same white background as every other week tile — the current week is marked
        // only by the border below, dezent enough not to look like a different card type.
        colors = CardDefaults.cardColors(containerColor = PlannerColors.surface),
        border = if (isCurrentWeek) {
            BorderStroke(1.dp, PlannerColors.primary.copy(alpha = 0.3f))
        } else {
            null
        },
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(
                            if (expanded) R.string.appointments_week_collapse
                            else R.string.appointments_week_expand
                        ),
                        onClick = onToggle,
                    )
                    .padding(14.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        R.string.appointments_week_label,
                        weekStart.get(WeekFields.ISO.weekOfWeekBasedYear()),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = PlannerColors.primary,
                )
                Text(
                    weekRangeLabel(weekStart, weekEnd, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.muted,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (!expanded) {
                    Text(
                        pluralStringResource(R.plurals.appointments_week_count, count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (count == 0) PlannerColors.faint else PlannerColors.primary,
                    )
                }
                // Chevron points down when collapsed, up when open.
                BackChevronIcon(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .rotate(if (expanded) 90f else 270f),
                    tint = PlannerColors.mutedLight,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = PlannerColors.divider)
                    apptDays.forEachIndexed { index, date ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = PlannerColors.divider,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                        DayBlock(
                            date = date,
                            today = today,
                            items = byDay.getValue(date),
                            onItemClick = onItemClick,
                            onDeleteClick = onDeleteClick,
                        )
                    }
                    if (apptDays.isEmpty()) {
                        Text(
                            stringResource(R.string.appointments_week_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlannerColors.faint,
                            modifier = Modifier.padding(14.dp, 9.dp, 14.dp, 11.dp),
                        )
                    }
                    // Shows at a glance which upcoming days of the week are still free.
                    if (freeDays.isNotEmpty()) {
                        if (apptDays.isNotEmpty()) {
                            HorizontalDivider(
                                color = PlannerColors.divider,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                        Text(
                            stringResource(
                                R.string.appointments_free_days,
                                freeDays.joinToString(", ") {
                                    it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) +
                                            " ${it.dayOfMonth}."
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlannerColors.faint,
                            modifier = Modifier.padding(14.dp, 9.dp, 14.dp, 11.dp),
                        )
                    } else if (apptDays.isNotEmpty()) {
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}

/** One day inside the week tile: day label plus its appointment rows. */
@Composable
private fun DayBlock(
    date: LocalDate,
    today: LocalDate,
    items: List<PlannerItem>,
    onItemClick: (Long) -> Unit,
    onDeleteClick: (PlannerItem) -> Unit,
) {
    val locale = Locale.getDefault()
    val deleteLabel = stringResource(R.string.appointments_delete)
    val label = if (date == today) {
        stringResource(
            R.string.appointments_today_label,
            date.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", locale)),
        )
    } else {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) +
                ", " + date.format(DateTimeFormatter.ofPattern("d. MMMM", locale))
    }
    Column(Modifier.padding(top = 9.dp, bottom = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (date == today) PlannerColors.primary else PlannerColors.mutedLight,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        items.forEach { appt ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(appt.id) }
                    // 12 dp / 12 dp vertical gives the row a comfortable 48 dp hit target
                    // when paired with bodyLarge text plus optional subtitle. The previous
                    // 7/7 left the row at ~36 dp — visually fine but a11y-short for talkback
                    // and small-touch users. End padding stays tight so the trailing Trash
                    // IconButton keeps a visible gap to the row edge.
                    .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    appt.dueAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = animateDoneColor(isDone = appt.done, base = PlannerColors.primary).value,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        appt.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (appt.done) PlannerColors.mutedLight else Color.Unspecified,
                        textDecoration = if (appt.done) TextDecoration.LineThrough else null,
                    )
                    val subtitle = buildList {
                        appt.category?.let { add(it) }
                        appt.recurrence?.let { add("↻ " + recurrenceLabel(it)) }
                    }.joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = PlannerColors.mutedLight
                        )
                    }
                }
                IconButton(
                    onClick = { onDeleteClick(appt) },
                    modifier = Modifier.semantics { contentDescription = deleteLabel },
                ) {
                    TrashIcon(tint = PlannerColors.mutedLight)
                }
            }
        }
    }
}

/** "3.–9. Aug" within one month, "30. Jul – 5. Aug" across a month boundary. */
private fun weekRangeLabel(start: LocalDate, end: LocalDate, locale: Locale): String {
    val day = DateTimeFormatter.ofPattern("d.", locale)
    val dayMonth = DateTimeFormatter.ofPattern("d. MMM", locale)
    return if (start.month == end.month) {
        "${start.format(day)}–${end.format(dayMonth)}"
    } else {
        "${start.format(dayMonth)} – ${end.format(dayMonth)}"
    }
}

/** Deleting cancels the reminder as well, so it asks first. */
@Composable
private fun DeleteAppointmentDialog(
    item: PlannerItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appointments_delete)) },
        text = { Text(stringResource(R.string.appointments_delete_confirm, item.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = PlannerColors.overdue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        containerColor = PlannerColors.surface,
    )
}
