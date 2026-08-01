package com.app.mindunload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.Priority
import com.app.mindunload.ui.theme.PlannerColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onNavigateToItem: (Long) -> Unit,
    onOpenResearch: (Long) -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    val item by viewModel.item(itemId).collectAsState(initial = null)
    val linked by viewModel.linkedItems(itemId).collectAsState(initial = emptyList())
    val notes by viewModel.notes(itemId).collectAsState(initial = emptyList())
    val researching by viewModel.researching.collectAsState()
    val researchError by viewModel.researchError.collectAsState()

    val current = item ?: return
    val overdue = current.dueAt != null && !current.done &&
            Instant.ofEpochMilli(current.dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
                .isBefore(LocalDate.now())

    var editDue by remember { mutableStateOf(false) }
    if (editDue && current.dueAt != null) {
        DueDateTimeDialog(
            initialMillis = current.dueAt,
            onDismiss = { editDue = false },
            onConfirm = { millis ->
                editDue = false
                viewModel.setDueAt(itemId, millis)
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(onBack = onBack)

        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(typeLabel(current.type), PlannerColors.chipBg, PlannerColors.chipText)
            if (overdue) Tag(
                stringResource(R.string.detail_overdue),
                Color(0xFFF4E4DA),
                PlannerColors.overdue
            )
        }
        Text(
            current.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(top = 10.dp),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PlannerColors.surface)
                .padding(horizontal = 16.dp),
        ) {
            current.category?.let { MetaRow(stringResource(R.string.detail_category), it) }
            if (current.priority != Priority.NONE) MetaRow(
                stringResource(R.string.detail_priority),
                priorityLabel(current.priority)
            )
            current.dueAt?.let { due ->
                // Tapping opens the date+time picker — manual rescheduling without the chat.
                Column(Modifier.clickable { editDue = true }) {
                    MetaRow(
                        stringResource(R.string.detail_due),
                        formatDateTime(due) + "  ✎",
                        valueColor = if (overdue) PlannerColors.overdue else PlannerColors.text,
                    )
                }
            }
            current.recurrence?.let {
                MetaRow(
                    stringResource(R.string.detail_recurrence),
                    recurrenceLabel(it)
                )
            }
        }

        // The saved text itself. For knowledge entries this IS the entry, so the section
        // also appears (with a hint) when it is empty; other types show it only when filled.
        if (current.notes != null || current.type == ItemType.NOTE) {
            Column(Modifier.padding(top = 22.dp)) {
                SectionLabel(stringResource(R.string.detail_notes_header))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PlannerColors.surface)
                        .padding(16.dp),
                ) {
                    current.notes?.takeIf { it.isNotBlank() }?.let { MarkdownText(it) } ?: Text(
                        stringResource(R.string.knowledge_no_content),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlannerColors.faint,
                    )
                }
            }
        }

        if (current.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                current.tags.forEach { tag ->
                    Text(
                        "#$tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = PlannerColors.muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PlannerColors.chipBg)
                            .padding(11.dp, 5.dp),
                    )
                }
            }
        }

        if (linked.isNotEmpty()) {
            Column(Modifier.padding(top = 22.dp)) {
                SectionLabel(stringResource(R.string.linked_items_header))
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    linked.forEach { entry ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PlannerColors.surface)
                                .clickable { onNavigateToItem(entry.other.id) }
                                .padding(15.dp, 12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    entry.other.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = PlannerColors.primary)
                                )
                                Text(
                                    entry.link.relation.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PlannerColors.muted
                                )
                            }
                            entry.link.reason?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PlannerColors.mutedLight,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(top = 22.dp)) {
            SectionLabel(stringResource(R.string.research_header))
            if (current.researchSuggested && notes.isEmpty()) {
                Text(
                    stringResource(R.string.research_suggested_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (notes.isEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.startResearch(itemId) },
                    enabled = !researching,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    SearchIcon(tint = PlannerColors.primary)
                    Text(" " + stringResource(R.string.action_research))
                }
                Text(
                    stringResource(R.string.research_cost_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = PlannerColors.faint,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    notes.forEach { note ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PlannerColors.surface)
                                .clickable { onOpenResearch(note.id) }
                                .padding(15.dp, 13.dp),
                        ) {
                            Text(
                                note.summary.lineSequence().firstOrNull()?.trimStart('#', ' ')
                                    ?.take(60)
                                    ?: stringResource(R.string.research_header),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                stringResource(
                                    R.string.research_note_subtitle,
                                    formatDateTime(note.createdAt)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = PlannerColors.mutedLight,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
            if (researching) {
                Row(Modifier.padding(top = 10.dp)) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.research_running))
                }
            }
            researchError?.let {
                Text(
                    stringResource(R.string.research_failed) + ": " + it,
                    color = PlannerColors.overdue,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(11.dp, 5.dp),
    )
}

@Composable
private fun MetaRow(label: String, value: String, valueColor: Color = PlannerColors.text) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PlannerColors.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** Two-step dialog: first date, then time — for manually rescheduling an appointment. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DueDateTimeDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = Instant.ofEpochMilli(initialMillis).atZone(zone)
    var step by remember { mutableStateOf(0) }
    val dateState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initial.toLocalDate()
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = androidx.compose.material3.rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    if (step == 0) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { step = 1 }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            androidx.compose.material3.DatePicker(state = dateState)
        }
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val dateUtc = dateState.selectedDateMillis ?: return@TextButton
                    val date =
                        Instant.ofEpochMilli(dateUtc).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onConfirm(
                        date.atTime(timeState.hour, timeState.minute)
                            .atZone(zone).toInstant().toEpochMilli(),
                    )
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = { androidx.compose.material3.TimePicker(state = timeState) },
        )
    }
}

@Composable
fun typeLabel(type: ItemType): String = when (type) {
    ItemType.TASK -> stringResource(R.string.type_task)
    ItemType.IDEA -> stringResource(R.string.type_idea)
    ItemType.GOAL -> stringResource(R.string.type_goal)
    ItemType.APPOINTMENT -> stringResource(R.string.type_appointment)
    ItemType.SHOPPING_ITEM -> stringResource(R.string.type_shopping_item)
    ItemType.NOTE -> stringResource(R.string.type_note)
}
