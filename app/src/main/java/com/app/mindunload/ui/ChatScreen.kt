package com.app.mindunload.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.mindunload.R
import com.app.mindunload.data.AttachmentKind
import com.app.mindunload.data.Attachments
import com.app.mindunload.data.CaptureRequest
import com.app.mindunload.data.CaptureStatus
import com.app.mindunload.data.ChatMode
import com.app.mindunload.data.ItemType
import com.app.mindunload.ui.theme.HitTarget
import com.app.mindunload.ui.theme.PlannerColors
import com.app.mindunload.ui.theme.Radius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun ChatScreen(onOpenDrawer: () -> Unit, viewModel: ChatViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    // Attachment picked but not sent yet — shown as a preview above the input field.
    var pending by remember { mutableStateOf<PendingAttachment?>(null) }
    var importing by remember { mutableStateOf(false) }
    // Open attachment menu (camera / gallery) anchored at the image button.
    var attachMenu by remember { mutableStateOf(false) }
    // File the camera app is writing into — kept across the launch so the result
    // callback knows what to import.
    var cameraTarget by remember { mutableStateOf<File?>(null) }
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current

    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recordedMs by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(0f) }
    // One player for the whole chat: starting a second voice message stops the first.
    val player = remember { VoicePlayer() }
    var playingPath by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.stop()
        }
    }

    // Live level and elapsed time while recording — polled, because AudioRecord itself
    // reports neither.
    LaunchedEffect(recording) {
        while (recording) {
            recordedMs = recorder.durationMs
            level = recorder.amplitude
            delay(80)
        }
        level = 0f
    }

    val feedback = LocalFeedback.current
    val recordingFailed = stringResource(R.string.voice_record_failed)
    val cameraFailed = stringResource(R.string.chat_camera_failed)
    val fileFailed = stringResource(R.string.chat_file_failed)

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        // Import happens right away: the picker's read permission on the URI expires,
        // the copy in our own storage does not.
        viewModel.importImage(context, uri) { path ->
            pending = path?.let { PendingAttachment(it, AttachmentKind.IMAGE) }
            importing = false
        }
    }

    val cameraCapture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { taken ->
        val file = cameraTarget
        cameraTarget = null
        if (!taken || file == null) {
            // Cancelled in the camera app — the empty placeholder file must not stay.
            file?.delete()
            return@rememberLauncherForActivityResult
        }
        importing = true
        // Same path as the picker: the raw shot is downscaled into a fresh file, the
        // full-resolution original is dropped right after.
        viewModel.importImage(context, Uri.fromFile(file)) { path ->
            file.delete()
            pending = path?.let { PendingAttachment(it, AttachmentKind.IMAGE) }
            importing = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        // Same reason as for photos: the picker's read permission on the URI is
        // short-lived, the copy in our own storage is not.
        viewModel.importFile(context, uri) { path ->
            pending = path?.let { PendingAttachment(it, AttachmentKind.FILE) }
            importing = false
            if (path == null) feedback.show(fileFailed)
        }
    }

    // The photo is taken by the camera app via ACTION_IMAGE_CAPTURE, which needs no
    // CAMERA permission as long as the manifest does not declare one.
    fun launchCamera() {
        val file = Attachments.newFile(context, "jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraTarget = file
        runCatching { cameraCapture.launch(uri) }.onFailure {
            cameraTarget = null
            file.delete()
            feedback.show(cameraFailed)
        }
    }

    fun startRecording() {
        val file = Attachments.newFile(context, "wav")
        recordedMs = 0L
        recording = recorder.start(file)
        // Another app holding the microphone, or a device without one — say so instead
        // of leaving the button looking dead.
        if (!recording) feedback.show(recordingFailed)
    }

    fun finishRecording() {
        recording = false
        val file = recorder.stop()
        // Below ~0.5s there is nothing to transcribe — usually a mis-tap.
        if (file != null && file.length() > 16_000) {
            viewModel.submitVoiceMessage(file)
        } else {
            file?.delete()
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startRecording() }

    // Selected chat function — shown and switchable via the header dropdown.
    val mode by viewModel.mode.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    val cleared = stringResource(R.string.chat_cleared)
    val nothingToClear = stringResource(R.string.chat_clear_nothing)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp, 8.dp, 20.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(1f): the subtitle must wrap instead of squeezing the dropdown.
            Box(Modifier.weight(1f)) {
                ScreenHeader(
                    title = stringResource(R.string.tab_chat),
                    subtitle = null,
                    onOpenDrawer = onOpenDrawer,
                )
            }
            Spacer(Modifier.width(10.dp))
            ModeDropdown(mode = mode, onSelect = { viewModel.mode.value = it })
            // Only offered when there is something to clear.
            if (history.isNotEmpty()) {
                androidx.compose.material3.IconButton(
                    onClick = { showClearDialog = true },
                    // Hit target migration: was 36 dp, now [HitTarget.min] = 48 dp.
                    // The chat-header Row grows by 12 dp as a result — visible in
                    // diff but acceptable; the row still fits in the header band.
                    modifier = Modifier.size(HitTarget.min),
                ) {
                    TrashIcon(tint = PlannerColors.muted)
                }
            }
        }
        if (showClearDialog) {
            ClearChatDialog(
                onDismiss = { showClearDialog = false },
                onConfirm = {
                    showClearDialog = false
                    player.stop()
                    playingPath = null
                    viewModel.clearChat { removed ->
                        feedback.show(if (removed > 0) cleared else nothingToClear)
                    }
                },
            )
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Always jump to the newest message — on opening, on new inputs AND
        // when the AI response for the last entry arrives (does not change size).
        LaunchedEffect(history.size, history.lastOrNull()?.message?.id) {
            if (history.isNotEmpty()) listState.scrollToItem(history.lastIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(history, key = { it.capture.id }) { entry ->
                ChatBubbles(
                    entry = entry,
                    isPlaying = playingPath != null && playingPath == entry.capture.attachmentPath,
                    onTogglePlay = { path ->
                        player.toggle(path) { playingPath = null }
                        playingPath = if (player.isPlaying(path)) path else null
                    },
                    onRetry = {
                        if (entry.capture.errorMessage?.startsWith("transcription") == true) {
                            viewModel.retryTranscription(entry.capture)
                        } else {
                            viewModel.retry()
                        }
                    },
                    onUndo = { viewModel.undo(it) },
                    onDiscard = { viewModel.discardCapture(it) },
                    onSaveResearch = { viewModel.saveResearchAsNote(entry) },
                    onSaveToWiki = { viewModel.saveTextAsWikiNote(it) },
                )
            }
        }
        // One rounded container holds the text and the actions — the field spans the
        // full width instead of competing with the buttons for it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlannerColors.background)
                .imePadding()
                .padding(12.dp, 6.dp, 12.dp, 14.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(PlannerColors.surface)
                .border(1.dp, PlannerColors.outline, RoundedCornerShape(22.dp))
                .padding(top = 4.dp, bottom = 6.dp),
        ) {
            pending?.let { attachment ->
                PendingAttachmentPreview(attachment = attachment, onRemove = {
                    Attachments.delete(attachment.path)
                    pending = null
                })
            }
            val canSend = input.isNotBlank() || pending != null
            // Text on top, actions in one row at the bottom right: the text gets the
            // full container width, and both buttons sit side by side on one baseline
            // instead of being stacked in a column beside the field.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                if (recording) {
                    RecordingIndicator(
                        elapsedMs = recordedMs,
                        level = level,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                } else {
                    val hintRes = when (mode) {
                        ChatMode.CAPTURE -> R.string.chat_input_hint
                        ChatMode.ASK -> R.string.chat_hint_ask
                        ChatMode.REVIEW -> R.string.chat_hint_review
                        ChatMode.RESEARCH -> R.string.chat_hint_research
                        ChatMode.STRUCTURE -> R.string.chat_hint_structure
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = {
                            Text(stringResource(hintRes), color = PlannerColors.muted)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedTextColor = PlannerColors.text,
                            focusedTextColor = PlannerColors.text,
                            cursorColor = PlannerColors.primary,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recording) {
                    // Discarding a recording must be possible without sending it.
                    ChatActionButton(
                        onClick = {
                            recording = false
                            recorder.cancel()
                        },
                        background = PlannerColors.background,
                        border = PlannerColors.outline,
                    ) {
                        TrashIcon(tint = PlannerColors.overdue)
                    }
                } else {
                    // One button, two sources: tapping opens a short menu instead of
                    // going straight to the gallery, so the camera is one tap away too.
                    Box {
                        ChatActionButton(
                            onClick = { attachMenu = true },
                            background = PlannerColors.background,
                            border = PlannerColors.outline,
                            enabled = !importing,
                        ) {
                            // A plus, not a photo icon: the button covers camera, gallery
                            // and any file now.
                            PlusIcon(tint = if (importing) PlannerColors.faint else PlannerColors.muted)
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = attachMenu,
                            onDismissRequest = { attachMenu = false },
                            containerColor = PlannerColors.surface,
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.chat_attach_camera),
                                        color = PlannerColors.text,
                                    )
                                },
                                leadingIcon = { CameraIcon(tint = PlannerColors.muted) },
                                onClick = {
                                    attachMenu = false
                                    launchCamera()
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.chat_attach_gallery),
                                        color = PlannerColors.text,
                                    )
                                },
                                leadingIcon = { ImageIcon(tint = PlannerColors.muted) },
                                onClick = {
                                    attachMenu = false
                                    imagePicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.chat_attach_file),
                                        color = PlannerColors.text,
                                    )
                                },
                                leadingIcon = { FileIcon(tint = PlannerColors.muted) },
                                onClick = {
                                    attachMenu = false
                                    // Everything is offered; what can actually be read out
                                    // is decided during extraction, see [Documents].
                                    filePicker.launch(arrayOf("*/*"))
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Mic until there is something to send — same slot, so the thumb never
                // has to move between recording and sending.
                ChatActionButton(
                    onClick = {
                        when {
                            recording -> finishRecording()
                            canSend -> {
                                viewModel.submit(
                                    input,
                                    pending?.path,
                                    pending?.kind ?: AttachmentKind.NONE
                                )
                                input = ""
                                pending = null
                            }

                            else -> {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startRecording()
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    },
                    background = if (recording) PlannerColors.overdue else PlannerColors.primary,
                ) {
                    when {
                        recording -> StopIcon(tint = PlannerColors.onPrimary)
                        canSend -> SendIcon(tint = PlannerColors.onPrimary)
                        else -> MicIcon(tint = PlannerColors.onPrimary)
                    }
                }
            }
        }
    }
}

/**
 * One shared style for both actions in the chat input: same 40 dp circle, same icon
 * slot — only the fill differs, so the attach/discard button reads as a sibling of the
 * send button rather than a loose icon next to it.
 */
@Composable
private fun ChatActionButton(
    onClick: () -> Unit,
    background: Color,
    enabled: Boolean = true,
    border: Color? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}


/**
 * Attached document inside a sent message. There is nothing to show of the file itself —
 * the name is what the user recognizes it by; the extracted text is in the bubble anyway.
 */
@Composable
private fun AttachmentFileRow(name: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PlannerColors.onPrimary.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(tint = PlannerColors.onPrimary)
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = PlannerColors.onPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Attachment picked but not sent yet: the imported copy plus what kind of thing it is. */
private data class PendingAttachment(val path: String, val kind: AttachmentKind)

/** Running recording: red dot, elapsed time, live level. */
@Composable
private fun RecordingIndicator(elapsedMs: Long, level: Float, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(PlannerColors.overdue),
        )
        Spacer(Modifier.width(10.dp))
        Text(formatDuration(elapsedMs), color = PlannerColors.text)
        Spacer(Modifier.width(12.dp))
        // Simple level meter: bars of decreasing sensitivity light up with the input level.
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(12) { index ->
                val threshold = (index + 1) / 14f
                Box(
                    Modifier
                        .padding(end = 3.dp)
                        .size(3.dp, (6 + index % 4 * 4).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (level > threshold) PlannerColors.primary else PlannerColors.outline),
                )
            }
        }
    }
}

/** Picked but unsent photo — sits inside the input container, above the text. */
@Composable
private fun PendingAttachmentPreview(attachment: PendingAttachment, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attachment.kind == AttachmentKind.IMAGE) {
            AttachmentImage(
                path = attachment.path,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            // A document has no thumbnail — the icon plus its name is what identifies it.
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PlannerColors.background),
                contentAlignment = Alignment.Center,
            ) {
                FileIcon(tint = PlannerColors.muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (attachment.kind == AttachmentKind.FILE) {
                Text(
                    Attachments.fileName(attachment.path),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlannerColors.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(
                    if (attachment.kind == AttachmentKind.FILE) {
                        R.string.chat_file_attached
                    } else {
                        R.string.chat_image_attached
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PlannerColors.muted,
            )
        }
        androidx.compose.material3.IconButton(
            onClick = onRemove,
            // Hit target migration: was 36 dp. Pending-image-preview Row grows by
            // 12 dp; this list is small and the extra height pushes only the
            // chat-input visual up by a couple of pixels overall.
            modifier = Modifier.size(HitTarget.min),
        ) {
            CloseIcon(tint = PlannerColors.muted)
        }
    }
}

/**
 * Decodes a stored photo off the main thread. The files are already scaled down on
 * import, so no further sampling is needed here.
 */
@Composable
private fun AttachmentImage(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(PlannerColors.outline))
    }
}

/** Voice message inside a chat bubble: play/pause plus the length of the recording. */
@Composable
private fun VoiceMessageRow(
    path: String,
    isPlaying: Boolean,
    onToggle: (String) -> Unit,
    tint: Color,
) {
    // WAV, 16 kHz mono 16-bit: the length follows from the file size, no decoder needed.
    val durationMs = remember(path) {
        val bytes = (File(path).length() - 44).coerceAtLeast(0)
        bytes / 2 * 1000 / VOICE_SAMPLE_RATE
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle(path) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) PauseIcon(tint = tint) else PlayIcon(tint = tint)
        }
        Spacer(Modifier.width(10.dp))
        // Static waveform: it marks the row as audio without pretending to show the
        // actual signal, which we do not keep after recording.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val heights = listOf(6, 12, 18, 10, 22, 14, 8, 16, 20, 9, 13, 7)
            heights.forEach { h ->
                Box(
                    Modifier
                        .padding(end = 3.dp)
                        .size(3.dp, h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint.copy(alpha = 0.55f)),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            formatDuration(durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = tint.copy(alpha = 0.8f),
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Clearing is not undoable, so it asks first — the entries created from it are unaffected. */
@Composable
private fun ClearChatDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_clear_title)) },
        text = { Text(stringResource(R.string.chat_clear_confirm)) },
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

/** Readable label for a chat function. */
private fun modeLabelRes(mode: ChatMode): Int = when (mode) {
    ChatMode.CAPTURE -> R.string.chat_mode_capture
    ChatMode.ASK -> R.string.chat_mode_ask
    ChatMode.REVIEW -> R.string.chat_mode_review
    ChatMode.RESEARCH -> R.string.chat_mode_research
    ChatMode.STRUCTURE -> R.string.chat_mode_structure
}

/**
 * Function selector in the header: determines which system prompt processes
 * the input. Lives up here so the chat list keeps the full height.
 */
@Composable
private fun ModeDropdown(mode: ChatMode, onSelect: (ChatMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, PlannerColors.outline),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = PlannerColors.surface,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                stringResource(modeLabelRes(mode)),
                style = MaterialTheme.typography.labelLarge,
                color = PlannerColors.text,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.rotate(-90f)) {
                BackChevronIcon(tint = PlannerColors.muted)
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PlannerColors.surface,
        ) {
            ChatMode.entries.forEach { m ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(stringResource(modeLabelRes(m)), color = PlannerColors.text)
                    },
                    onClick = {
                        onSelect(m)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
private fun ChatBubbles(
    entry: ChatEntry,
    isPlaying: Boolean,
    onTogglePlay: (String) -> Unit,
    onRetry: () -> Unit,
    onUndo: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
    onSaveResearch: () -> Unit,
    onSaveToWiki: (String) -> Unit,
) {
    val capture = entry.capture
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        var showUserMenu by remember(capture.id) { mutableStateOf(false) }
        Box {
        Column(
            Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(PlannerColors.primary)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showUserMenu = true },
                )
                .padding(14.dp, 11.dp),
        ) {
            when (capture.attachmentKind) {
                AttachmentKind.IMAGE -> capture.attachmentPath?.let { path ->
                    AttachmentImage(
                        path = path,
                        // FillWidth, not Crop: on a photographed note or receipt the
                        // left and right edges carry text that must not be cut away.
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                AttachmentKind.AUDIO -> capture.attachmentPath?.let { path ->
                    VoiceMessageRow(
                        path = path,
                        isPlaying = isPlaying,
                        onToggle = onTogglePlay,
                        tint = PlannerColors.onPrimary,
                    )
                    if (capture.rawText.isNotBlank()) Spacer(Modifier.height(6.dp))
                }

                AttachmentKind.FILE -> capture.attachmentPath?.let { path ->
                    AttachmentFileRow(name = Attachments.fileName(path))
                    Spacer(Modifier.height(8.dp))
                }

                AttachmentKind.NONE -> Unit
            }
            if (capture.rawText.isNotBlank()) {
                Text(capture.rawText, color = PlannerColors.onPrimary)
            }
            // Date and time stamp at the bottom right of the bubble.
            BubbleTimestamp(capture.createdAt, color = PlannerColors.onPrimary.copy(alpha = 0.7f))
        }
        MessageActionsMenu(
            expanded = showUserMenu,
            onDismiss = { showUserMenu = false },
            text = capture.rawText,
            onDelete = { onDiscard(capture.id) },
            onSaveToWiki = onSaveToWiki,
        )
        }
        Spacer(Modifier.size(6.dp))
        when (capture.status) {
            CaptureStatus.TRANSCRIBING -> Text(
                stringResource(R.string.status_transcribing),
                style = MaterialTheme.typography.bodySmall,
                color = PlannerColors.muted,
            )

            CaptureStatus.PENDING, CaptureStatus.PROCESSING -> Text(
                stringResource(
                    if (capture.status == CaptureStatus.PENDING) R.string.status_pending else R.string.status_processing,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PlannerColors.muted,
            )

            CaptureStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    failureText(capture),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A6D2F),
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = onRetry) {
                    RetryIcon(tint = PlannerColors.primary)
                    Spacer(Modifier.size(5.dp))
                    Text(stringResource(R.string.action_retry))
                }
                TextButton(onClick = { onDiscard(capture.id) }) { Text(stringResource(R.string.action_delete)) }
            }

            CaptureStatus.DONE -> entry.message?.let { msg ->
                val json = runCatching { JSONObject(msg.summaryJson) }.getOrNull()
                // Free-text answer from a chat function (ask/review/research) — the text a
                // long-press on this bubble copies or adds to the wiki. Structured
                // items/links/commands bubbles have no single text of their own, so those
                // stay delete-only in the menu.
                val answer = json?.optString("answer")?.takeIf { it.isNotBlank() }
                var showAssistantMenu by remember(capture.id) { mutableStateOf(false) }
                Box {
                Column(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                        .background(PlannerColors.surface)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showAssistantMenu = true },
                        )
                        .padding(14.dp, 12.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    if (answer != null) {
                        MarkdownText(answer)
                        json.optJSONArray("sources")?.takeIf { it.length() > 0 }?.let {
                            ResearchSources(it)
                        }
                        // Research and structured-thought answers are free text without an
                        // entry of their own — they become durable knowledge only if the
                        // user wants them to.
                        if (capture.mode == ChatMode.RESEARCH || capture.mode == ChatMode.STRUCTURE) {
                            if (json.has("savedItemId")) {
                                Text(
                                    stringResource(R.string.chat_research_saved),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PlannerColors.muted,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            } else {
                                TextButton(
                                    onClick = onSaveResearch,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        0.dp
                                    ),
                                ) {
                                    Text(
                                        stringResource(R.string.chat_research_save),
                                        color = PlannerColors.primary,
                                    )
                                }
                            }
                        }
                        BubbleTimestamp(msg.createdAt, color = PlannerColors.faint)
                        return@Column
                    }
                    val items = json?.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.chat_items_saved,
                                items.length(),
                                items.length()
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(color = PlannerColors.primary),
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            for (i in 0 until items.length()) {
                                val obj = items.getJSONObject(i)
                                val type =
                                    runCatching { ItemType.valueOf(obj.optString("type")) }.getOrNull()
                                Text(
                                    "${type?.let { typeLabel(it) } ?: obj.optString("type")} · ${
                                        obj.optString(
                                            "title"
                                        )
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PlannerColors.chipText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.xs))
                                        .background(PlannerColors.chipBg)
                                        .padding(11.dp, 5.dp),
                                )
                            }
                        }
                    }
                    val links = json?.optJSONArray("links")
                    if (links != null) {
                        for (i in 0 until links.length()) {
                            val obj = links.getJSONObject(i)
                            Text(
                                stringResource(
                                    R.string.chat_link_created,
                                    obj.optString("aTitle"),
                                    obj.optString("bTitle")
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    val commands = json?.optJSONArray("commands")
                    if (commands != null) {
                        for (i in 0 until commands.length()) {
                            Text(
                                commands.getString(i),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    val skipped = json?.optInt("skippedCommands", 0) ?: 0
                    if (skipped > 0) {
                        Text(
                            pluralStringResource(R.plurals.chat_commands_skipped, skipped, skipped),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8A6D2F),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    val anythingApplied = (items?.length() ?: 0) > 0 ||
                            (links?.length() ?: 0) > 0 || (commands?.length() ?: 0) > 0
                    if (!anythingApplied && skipped == 0) {
                        Text(
                            stringResource(R.string.chat_nothing_applied),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlannerColors.muted,
                        )
                    }
                    if (anythingApplied) {
                        TextButton(
                            onClick = { onUndo(capture.id) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text(
                                stringResource(R.string.action_undo),
                                color = PlannerColors.mutedLight
                            )
                        }
                    }
                    BubbleTimestamp(msg.createdAt, color = PlannerColors.faint)
                }
                MessageActionsMenu(
                    expanded = showAssistantMenu,
                    onDismiss = { showAssistantMenu = false },
                    text = answer.orEmpty(),
                    onDelete = { onDiscard(capture.id) },
                    onSaveToWiki = onSaveToWiki,
                )
                }
            }
        }
    }
}

/**
 * Long-press context menu for one chat bubble: copy / add to wiki (only when there is
 * text worth acting on) plus delete, which always removes the whole capture.
 */
@Composable
private fun MessageActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    text: String,
    onDelete: () -> Unit,
    onSaveToWiki: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val feedback = LocalFeedback.current
    val copiedMessage = stringResource(R.string.chat_message_copied)
    val savedMessage = stringResource(R.string.research_promoted)
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = PlannerColors.surface,
    ) {
        if (text.isNotBlank()) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy)) },
                onClick = {
                    onDismiss()
                    clipboard.setText(AnnotatedString(text))
                    feedback.show(copiedMessage)
                },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.research_promote)) },
                onClick = {
                    onDismiss()
                    onSaveToWiki(text)
                    feedback.show(savedMessage)
                },
            )
        }
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete), color = PlannerColors.overdue) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

/** Which failure the user is looking at — a missing key, no network, or a failed transcription. */
@Composable
private fun failureText(capture: CaptureRequest): String {
    val error = capture.errorMessage.orEmpty()
    return when {
        error == "no_api_key" -> stringResource(R.string.status_no_key)
        error == "transcription_no_model" -> stringResource(R.string.status_transcription_no_model)
        error == "transcription_empty" -> stringResource(R.string.status_transcription_empty)
        error == "transcription_interrupted" ->
            stringResource(R.string.status_transcription_interrupted)

        error == "transcription_timeout" -> stringResource(R.string.status_transcription_timeout)

        // The cause is appended after the colon — showing it beats a generic sentence
        // when speech recognition fails for a device-specific reason.
        error.startsWith("transcription_failed") ->
            stringResource(R.string.status_transcription_failed) +
                    error.substringAfter(':', "").trim()
                        .takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

        else -> stringResource(R.string.status_waiting_network)
    }
}

/** Clickable source list under a research answer — same look as the research screen. */
@Composable
private fun ResearchSources(sources: org.json.JSONArray) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 12.dp)) {
        SectionLabel(stringResource(R.string.sources_header))
        for (i in 0 until sources.length()) {
            val source = sources.getJSONObject(i)
            val url = source.optString("url")
            Text(
                source.optString("title", url),
                color = PlannerColors.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, url.toUri()),
                            )
                        }
                    }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/** Date and time stamp at the bottom right inside a chat bubble. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BubbleTimestamp(
    at: Long,
    color: Color,
) {
    Text(
        formatDateTime(at),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .align(Alignment.End)
            .padding(top = 4.dp),
    )
}
