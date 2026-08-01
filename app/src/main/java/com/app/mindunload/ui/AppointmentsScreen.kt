package com.app.mindunload.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.data.ItemType
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

@Composable
fun AppointmentsScreen(
    onOpenDrawer: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: ItemsViewModel = viewModel(),
) {
    val appointments by viewModel.items(ItemType.APPOINTMENT).collectAsState(initial = emptyList())
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val byDay = appointments
        .filter { !it.done && it.dueAt != null }
        .sortedBy { it.dueAt }
        .groupBy { Instant.ofEpochMilli(it.dueAt!!).atZone(zone).toLocalDate() }

    // Walk week by week from today (or an overdue leftover) to the last
    // appointment, so weeks and days WITHOUT appointments stay visible as gaps.
    val firstDay = minOf(byDay.keys.minOrNull() ?: today, today)
    val lastDay = maxOf(byDay.keys.maxOrNull() ?: today, today)
    val weeks = generateSequence(firstDay.with(DayOfWeek.MONDAY)) { it.plusWeeks(1) }
        .takeWhile { it <= lastDay.with(DayOfWeek.MONDAY) }
        .toList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 18.dp,
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
        if (byDay.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.appointments_empty),
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            return@LazyColumn
        }
        var lastMonth: YearMonth? = null
        weeks.forEach { weekStart ->
            val month = YearMonth.from(maxOf(weekStart, firstDay))
            if (month != lastMonth) {
                lastMonth = month
                item(key = "month-$month") {
                    Text(
                        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                        style = MaterialTheme.typography.titleMedium,
                        color = PlannerColors.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            item(key = "week-$weekStart") {
                WeekCard(
                    weekStart = weekStart,
                    today = today,
                    byDay = byDay,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}

/** One tile per week: week header, day blocks with appointments, free days at the bottom. */
@Composable
private fun WeekCard(
    weekStart: LocalDate,
    today: LocalDate,
    byDay: Map<LocalDate, List<PlannerItem>>,
    onItemClick: (Long) -> Unit,
) {
    val locale = Locale.getDefault()
    val rangeFmt = DateTimeFormatter.ofPattern("d. MMM", locale)
    val days = (0L..6L).map(weekStart::plusDays)
    val apptDays = days.filter { byDay.containsKey(it) }
    val freeDays = days.filter { it >= today && !byDay.containsKey(it) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PlannerColors.surface),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
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
                Spacer(Modifier.weight(1f))
                Text(
                    "${weekStart.format(rangeFmt)} – ${weekStart.plusDays(6).format(rangeFmt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.muted,
                )
            }
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
                    if (apptDays.isNotEmpty()) {
                        stringResource(
                            R.string.appointments_free_days,
                            freeDays.joinToString(", ") {
                                it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) +
                                        " ${it.dayOfMonth}."
                            },
                        )
                    } else {
                        stringResource(R.string.appointments_week_empty)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.faint,
                    modifier = Modifier.padding(14.dp, 9.dp, 14.dp, 11.dp),
                )
            } else {
                Spacer(Modifier.size(8.dp))
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
) {
    val locale = Locale.getDefault()
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
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    appt.dueAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlannerColors.primary,
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(appt.title, style = MaterialTheme.typography.bodyLarge)
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
            }
        }
    }
}
