package com.app.mindunload.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.ai.Whisper
import com.app.mindunload.ai.WhisperModel
import com.app.mindunload.reminders.ReminderScheduler
import com.app.mindunload.reminders.reminderOffsetLabel
import com.app.mindunload.ui.theme.PlannerColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    var keyInput by remember { mutableStateOf("") }
    val hasKey by viewModel.hasKey.collectAsState()
    val briefingTime by viewModel.briefingTime.collectAsState()
    val reminderOffsets by viewModel.reminderOffsets.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val feedback = LocalFeedback.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            feedback.show(context.getString(R.string.export_cancelled))
        } else {
            scope.launch {
                busy = true
                try {
                    val json = viewModel.exportJson()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                    feedback.show(context.getString(R.string.export_done))
                } catch (e: Exception) {
                    feedback.show(
                        context.getString(R.string.export_failed) + ": " + (e.message ?: ""),
                        long = true,
                    )
                } finally {
                    busy = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            feedback.show(context.getString(R.string.import_cancelled))
        } else {
            scope.launch {
                busy = true
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("empty file")
                    val result = viewModel.importJson(json)
                    feedback.show(
                        context.getString(R.string.import_done, result.items, result.links),
                    )
                } catch (e: Exception) {
                    feedback.show(
                        context.getString(R.string.import_failed) + ": " + (e.message ?: ""),
                        long = true,
                    )
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
    ) {
        BackHeader(onBack = onBack)
        Text(
            stringResource(R.string.settings_headline),
            style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(top = 6.dp),
        )

        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.settings_api_key_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (hasKey) {
                        stringResource(R.string.settings_key_present)
                    } else {
                        stringResource(R.string.settings_key_missing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasKey) PlannerColors.primary else PlannerColors.overdue,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_key_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                val keySaved = stringResource(R.string.settings_key_saved)
                Button(
                    onClick = {
                        viewModel.saveKey(keyInput)
                        keyInput = ""
                        feedback.show(keySaved)
                    },
                    enabled = keyInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
                HorizontalDivider(color = PlannerColors.divider)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.settings_model_parsing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Tag("Haiku")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.settings_model_research),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Tag("Sonnet")
                }
            }
        }

        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.briefing_label))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val (hour, minute) = briefingTime
                Text(
                    stringResource(
                        R.string.settings_briefing_time,
                        "%02d:%02d".format(hour, minute)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text(stringResource(R.string.settings_briefing_change))
                }
            }
        }

        // Lead times for appointment reminders — several fire one after another.
        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.settings_reminders_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.settings_reminders_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.muted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderScheduler.PRESET_OFFSETS.forEach { minutes ->
                        FilterChip(
                            selected = minutes in reminderOffsets,
                            onClick = { viewModel.toggleReminderOffset(minutes) },
                            label = { Text(reminderOffsetLabel(context, minutes)) },
                        )
                    }
                }
                if (reminderOffsets.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_reminders_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlannerColors.overdue,
                    )
                }
            }
        }

        // Speech model for voice messages — runs entirely on the device.
        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.settings_speech_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.settings_speech_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.muted,
                )
                val selectedModel by viewModel.whisperModel.collectAsState()
                val installed by viewModel.installedWhisperModels.collectAsState()
                val progress by viewModel.whisperDownload.collectAsState()
                val error by viewModel.whisperError.collectAsState()

                WhisperModel.entries.forEach { model ->
                    WhisperModelRow(
                        model = model,
                        selected = model == selectedModel,
                        installed = model in installed,
                        // Only one download at a time; the row of the running one shows it.
                        downloadProgress = progress.takeIf { model == selectedModel },
                        busy = progress != null,
                        onSelect = { viewModel.selectWhisperModel(model) },
                        onDownload = { viewModel.downloadWhisperModel(model) },
                        onDelete = { viewModel.deleteWhisperModel(model) },
                    )
                }
                error?.let {
                    Text(
                        stringResource(R.string.error_with_message, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlannerColors.overdue,
                    )
                }
                // Whether the native library loaded at all. Without this line a device
                // where it does not is indistinguishable from a bad recording.
                val nativeError = remember { Whisper.nativeError() }
                Text(
                    if (nativeError == null) {
                        stringResource(R.string.settings_speech_ready)
                    } else {
                        stringResource(R.string.settings_speech_unavailable, nativeError)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (nativeError == null) PlannerColors.muted else PlannerColors.overdue,
                )
            }
        }

        // Weather location for the briefing (Open-Meteo, no API key needed)
        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.settings_weather_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val weatherLocation by viewModel.weatherLocation.collectAsState()
                var latInput by remember { mutableStateOf(weatherLocation.first?.toString() ?: "") }
                var lonInput by remember {
                    mutableStateOf(
                        weatherLocation.second?.toString() ?: ""
                    )
                }
                Text(
                    stringResource(R.string.settings_weather_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlannerColors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it },
                        label = { Text(stringResource(R.string.settings_weather_lat)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lonInput,
                        onValueChange = { lonInput = it },
                        label = { Text(stringResource(R.string.settings_weather_lon)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                val weatherSaved = stringResource(R.string.settings_weather_saved)
                val weatherCleared = stringResource(R.string.settings_weather_cleared)
                val weatherInvalid = stringResource(R.string.settings_weather_invalid)
                Button(onClick = {
                    val lat = latInput.replace(',', '.').toDoubleOrNull()
                    val lon = lonInput.replace(',', '.').toDoubleOrNull()
                    val blank = latInput.isBlank() && lonInput.isBlank()
                    when {
                        blank -> {
                            viewModel.setWeatherLocation(null, null)
                            feedback.show(weatherCleared)
                        }
                        // Half a coordinate or unparseable input would silently disable the
                        // weather — say so instead of storing null behind the user's back.
                        lat == null || lon == null -> feedback.show(weatherInvalid, long = true)
                        else -> {
                            viewModel.setWeatherLocation(lat, lon)
                            feedback.show(weatherSaved)
                        }
                    }
                }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }

        if (showTimePicker) {
            BriefingTimeDialog(
                hour = briefingTime.first,
                minute = briefingTime.second,
                onDismiss = { showTimePicker = false },
                onConfirm = { h, m ->
                    viewModel.setBriefingTime(h, m)
                    showTimePicker = false
                    feedback.show(
                        context.getString(
                            R.string.settings_briefing_saved,
                            "%02d:%02d".format(h, m),
                        ),
                    )
                },
            )
        }

        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.cleanup_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
            ) {
                val cleanupRunning by viewModel.cleanupRunning.collectAsState()
                val cleanupStarted = stringResource(R.string.cleanup_started)
                val cleanupDone = stringResource(R.string.cleanup_done)
                val cleanupFailed = stringResource(R.string.cleanup_failed)
                OutlinedButton(
                    onClick = {
                        feedback.show(cleanupStarted)
                        viewModel.runCleanupNow { ok ->
                            feedback.show(if (ok) cleanupDone else cleanupFailed, long = !ok)
                        }
                    },
                    enabled = !cleanupRunning,
                ) {
                    Text(
                        stringResource(
                            if (cleanupRunning) R.string.cleanup_running else R.string.cleanup_run_now,
                        ),
                    )
                }
            }
        }

        Column(Modifier.padding(top = 20.dp)) {
            SectionLabel(stringResource(R.string.settings_export_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlannerColors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("mindunload-export-${LocalDate.now()}.json") },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.action_export))
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.action_import))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BriefingTimeDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onConfirm(
                    state.hour,
                    state.minute
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
        text = { androidx.compose.material3.TimePicker(state = state) },
    )
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = PlannerColors.chipText,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PlannerColors.chipBg)
            .padding(10.dp, 4.dp),
    )
}

/**
 * One selectable speech model with its state: pick it, download it, delete it. A model
 * only becomes usable once its file is actually on the device, so the row shows the
 * download separately from the selection.
 */
@Composable
private fun WhisperModelRow(
    model: WhisperModel,
    selected: Boolean,
    installed: Boolean,
    downloadProgress: Float?,
    busy: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val labelRes = when (model) {
        WhisperModel.TINY -> R.string.speech_model_tiny
        WhisperModel.BASE -> R.string.speech_model_base
        WhisperModel.SMALL -> R.string.speech_model_small
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect, enabled = !busy)
            Column(Modifier.weight(1f)) {
                Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "%d MB".format(model.sizeBytes / 1_000_000),
                    style = MaterialTheme.typography.labelSmall,
                    color = PlannerColors.muted,
                )
            }
            when {
                downloadProgress != null -> Text(
                    "%d %%".format((downloadProgress * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlannerColors.primary,
                )

                installed -> TextButton(onClick = onDelete, enabled = !busy) {
                    Text(stringResource(R.string.action_delete), color = PlannerColors.overdue)
                }

                else -> TextButton(onClick = onDownload, enabled = !busy) {
                    Text(stringResource(R.string.settings_speech_download))
                }
            }
        }
        downloadProgress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = PlannerColors.primary,
            )
        }
    }
}
