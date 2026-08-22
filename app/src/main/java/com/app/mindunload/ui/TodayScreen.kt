package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.ui.theme.PlannerColors
import androidx.compose.foundation.isSystemInDarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(
    onOpenDrawer: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val briefing by viewModel.briefingText.collectAsState()
    val generating by viewModel.generating.collectAsState()
    val error by viewModel.error.collectAsState()
    val appointments by viewModel.todaysAppointments.collectAsState(initial = emptyList())
    val resurface by viewModel.resurfaceItems.collectAsState(initial = emptyList())
    val cleanup by viewModel.cleanupEntries.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.reloadCleanup() }
    val activity by viewModel.recentActivity.collectAsState()

    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    val dateLabel = "$weekday, ${today.format(DateTimeFormatter.ofPattern("d. MMMM", locale))}"

    // Hero-panel colors. The briefing sits on the "dark card" surface in both modes —
    // but that color is fixed (#2E3B33). Under dark theme it sat too close to the page
    // background, so the panel vanished. These locals switch once per recomposition;
    // they don't read state, so this is a single condition per draw.
    val darkMode = isSystemInDarkTheme()
    val heroBg = if (darkMode) PlannerColors.darkCardDarkMode else PlannerColors.darkCard
    val heroOn = if (darkMode) PlannerColors.onDarkCardDarkMode else PlannerColors.onDarkCard
    val heroMuted = if (darkMode) PlannerColors.darkCardMutedDarkMode else PlannerColors.darkCardMuted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 8.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.today_greeting),
            subtitle = dateLabel,
            onOpenDrawer = onOpenDrawer
        )

        // Briefing
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(heroBg)
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.briefing_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = heroMuted,
            )
            if (briefing != null) {
                Text(
                    briefing!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = heroOn,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else if (!generating) {
                Text(
                    stringResource(R.string.briefing_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroOn,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (generating) {
                Row(modifier = Modifier.padding(top = 10.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = heroOn,
                    )
                    Text(
                        stringResource(R.string.briefing_generating),
                        color = heroOn
                    )
                }
            } else if (briefing == null) {
                OutlinedButton(
                    onClick = { viewModel.generateNow() },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.briefing_generate_now))
                }
            }
            error?.let {
                Text(
                    stringResource(R.string.error_with_message, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.soon,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Today's appointments
        Column {
            SectionLabel(stringResource(R.string.today_section))
            if (appointments.isEmpty()) {
                SmallEmptyHint(stringResource(R.string.today_no_appointments))
            } else {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    appointments.forEach { appt ->
                        androidx.compose.material3.Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(appt.id) },
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = PlannerColors.surface
                            ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                appt.dueAt?.let {
                                    Text(
                                        java.time.Instant.ofEpochMilli(it)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("HH:mm")),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PlannerColors.primary,
                                    )
                                }
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(appt.title, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Resurfacing: bring old, undated entries back up
        if (resurface.isNotEmpty()) {
            Column {
                SectionLabel(stringResource(R.string.resurface_section))
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    resurface.forEach { item ->
                        ResurfaceCard(
                            item = item,
                            onClick = { onItemClick(item.id) },
                            onDone = { viewModel.resurfaceDone(item.id) },
                            onLater = { viewModel.resurfaceLater(item.id) },
                            onDiscard = { viewModel.resurfaceDiscard(item) },
                        )
                    }
                }
            }
        }

        // Weekly cleanup: pending suggestions to confirm
        if (cleanup.isNotEmpty()) {
            Column {
                SectionLabel(stringResource(R.string.cleanup_section))
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    cleanup.forEach { entry ->
                        CleanupCard(
                            entry = entry,
                            onApply = { viewModel.applyCleanup(entry) },
                            onIgnore = { viewModel.dismissCleanup(entry) },
                            onItemClick = onItemClick,
                        )
                    }
                    val cleanupRunning by viewModel.cleanupRunning.collectAsState()
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { viewModel.runCleanupNow() },
                            enabled = !cleanupRunning,
                        ) {
                            Text(stringResource(R.string.cleanup_run_now))
                        }
                        if (cleanupRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // Verlauf
        if (activity.isNotEmpty()) {
            Column {
                SectionLabel(stringResource(R.string.history_section))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PlannerColors.surface)
                        .padding(vertical = 6.dp)
                        .padding(horizontal = 15.dp),
                ) {
                    activity.forEach { entry -> ActivityEntryRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun ResurfaceCard(
    item: com.app.mindunload.data.PlannerItem,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onDiscard: () -> Unit,
) {
    val weeks = ((System.currentTimeMillis() - item.createdAt) / (7L * 24 * 60 * 60 * 1000))
        .toInt().coerceAtLeast(1)
    androidx.compose.material3.Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = PlannerColors.surface),
    ) {
        Column(Modifier
            .fillMaxWidth()
            .padding(14.dp, 12.dp, 14.dp, 4.dp)) {
            Text(
                "${typeLabel(item.type)} · " +
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.resurface_age,
                            weeks,
                            weeks
                        ),
                style = MaterialTheme.typography.labelSmall,
                color = PlannerColors.faint,
            )
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row {
                androidx.compose.material3.TextButton(onClick = onDone) {
                    Text(stringResource(R.string.resurface_done), color = PlannerColors.primary)
                }
                androidx.compose.material3.TextButton(onClick = onLater) {
                    Text(stringResource(R.string.resurface_later), color = PlannerColors.muted)
                }
                androidx.compose.material3.TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.resurface_discard), color = PlannerColors.muted)
                }
            }
        }
    }
}

@Composable
private fun CleanupCard(
    entry: CleanupEntry,
    onApply: () -> Unit,
    onIgnore: () -> Unit,
    onItemClick: (Long) -> Unit,
) {
    // Tapping expands the affected entries; each entry leads to the detail view.
    var expanded by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            false
        )
    }
    androidx.compose.material3.Card(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = PlannerColors.surface),
    ) {
        Column(Modifier
            .fillMaxWidth()
            .padding(14.dp, 12.dp, 14.dp, 4.dp)) {
            Text(entry.description, style = MaterialTheme.typography.bodyMedium)
            if (entry.items.isNotEmpty()) {
                Text(
                    stringResource(
                        if (expanded) R.string.cleanup_hide_items else R.string.cleanup_show_items,
                        entry.items.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = PlannerColors.faint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    entry.items.forEach { (id, title) ->
                        Text(
                            "• $title",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlannerColors.chipText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(id) }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
            Row {
                if (entry.action == "delete" || entry.action == "complete") {
                    androidx.compose.material3.TextButton(onClick = onApply) {
                        Text(stringResource(R.string.cleanup_apply), color = PlannerColors.primary)
                    }
                }
                androidx.compose.material3.TextButton(onClick = onIgnore) {
                    Text(stringResource(R.string.cleanup_ignore), color = PlannerColors.muted)
                }
            }
        }
    }
}

@Composable
private fun ActivityEntryRow(entry: ActivityEntry) {
    Column(Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entry.lines.forEach { line ->
                when (line) {
                    is ActivityLine.SavedItem -> {
                        val label = line.type?.let { typeLabel(it) }
                        Text(
                            if (label != null) "$label · ${line.title}" else line.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is ActivityLine.Link -> Text(
                        stringResource(R.string.chat_link_created, line.aTitle, line.bTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlannerColors.chipText,
                    )

                    is ActivityLine.Command -> Text(
                        line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlannerColors.chipText,
                    )
                }
            }
        }
        // Timestamp on its own line at the bottom right — consistent with all cards.
        Text(
            activityTimeLabel(entry.at),
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.faint,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.End)
                .padding(top = 2.dp),
        )
    }
}

/** Time of day for today's entries, otherwise date with time. */
private fun activityTimeLabel(epochMillis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val dateTime = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
    val pattern = if (dateTime.toLocalDate() == LocalDate.now()) "HH:mm" else "d. MMM HH:mm"
    return dateTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
