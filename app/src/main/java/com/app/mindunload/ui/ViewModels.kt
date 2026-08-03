package com.app.mindunload.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.mindunload.PlannerApp
import com.app.mindunload.ai.Whisper
import com.app.mindunload.ai.WhisperModel
import com.app.mindunload.ai.WhisperModelMissingException
import com.app.mindunload.R
import com.app.mindunload.data.AttachmentKind
import com.app.mindunload.data.Attachments
import com.app.mindunload.data.CaptureRequest
import com.app.mindunload.data.CaptureStatus
import com.app.mindunload.data.ChatMessage
import com.app.mindunload.data.ChatMode
import com.app.mindunload.data.ItemLink
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.LinkOrigin
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.ResearchNote
import com.app.mindunload.export.JsonExporter
import com.app.mindunload.reminders.BriefingScheduler
import com.app.mindunload.reminders.ReminderScheduler
import com.app.mindunload.work.CaptureWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

val AndroidViewModel.app: PlannerApp
    get() = getApplication<Application>() as PlannerApp

/** Upper bound for one voice-message transcription; see [ChatViewModel.transcribe]. */
private const val TRANSCRIPTION_TIMEOUT_MS = 5 * 60 * 1000L

/** One entry in the chat history: the user input plus (when finished) the assistant summary. */
data class ChatEntry(val capture: CaptureRequest, val message: ChatMessage?)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = app.repository

    /**
     * Selected chat function — determines the system prompt of the processing. Capture is the
     * default; a chosen function stays put until it is switched back by hand, so several
     * questions in a row work without reselecting it every time.
     */
    val mode = MutableStateFlow(ChatMode.CAPTURE)

    val history: StateFlow<List<ChatEntry>> = combine(
        repo.captureDao.allOrdered(),
        repo.chatMessageDao.allOrdered(),
    ) { captures, messages ->
        val byCapture = messages.associateBy { it.captureId }
        captures.map { ChatEntry(it, byCapture[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Copies a picked photo into the app's storage (decoding and scaling happen off the
     * main thread) and reports the resulting path — null when the image was unreadable.
     */
    fun importImage(
        context: android.content.Context,
        uri: android.net.Uri,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Attachments.importImage(context, uri)
            }
            onDone(path)
        }
    }

    /**
     * Sends a message. [imagePath] is a photo already imported into the app's storage —
     * its text is extracted during processing, so a photo alone (without a caption) is a
     * complete message.
     */
    fun submit(text: String, imagePath: String? = null) {
        if (text.isBlank() && imagePath == null) return
        val selectedMode = mode.value
        viewModelScope.launch {
            repo.enqueueCapture(
                rawText = text,
                mode = selectedMode,
                attachmentPath = imagePath,
                attachmentKind = if (imagePath == null) AttachmentKind.NONE else AttachmentKind.IMAGE,
            )
            CaptureWorker.enqueue(app)
        }
    }

    /**
     * Puts a finished recording into the chat right away and transcribes it afterwards —
     * the voice message stays visible and playable the whole time, and only the text
     * arrives late. Runs in appScope: leaving the chat must not abort the transcription.
     */
    fun submitVoiceMessage(file: java.io.File) {
        val selectedMode = mode.value
        app.appScope.launch {
            val captureId = repo.enqueueCapture(
                rawText = "",
                mode = selectedMode,
                attachmentPath = file.absolutePath,
                attachmentKind = AttachmentKind.AUDIO,
                status = CaptureStatus.TRANSCRIBING,
            )
            transcribe(captureId, file)
        }
    }

    /** Second attempt for a voice message whose transcription failed. */
    fun retryTranscription(capture: CaptureRequest) {
        val path = capture.attachmentPath ?: return
        app.appScope.launch {
            repo.captureDao.setStatus(capture.id, CaptureStatus.TRANSCRIBING)
            transcribe(capture.id, java.io.File(path))
        }
    }

    private suspend fun transcribe(captureId: Long, file: java.io.File) {
        try {
            // A hard ceiling so the message can never sit in "transcribing" forever:
            // whatever goes wrong down there, the status resolves and the retry button
            // comes back. Generous — a long recording on the small model is slow.
            val text = kotlinx.coroutines.withTimeout(TRANSCRIPTION_TIMEOUT_MS) {
                Whisper.transcribe(app, app.settings.whisperModel, file)
            }
            if (text.isBlank()) {
                repo.captureDao.setStatus(captureId, CaptureStatus.FAILED, "transcription_empty")
                return
            }
            repo.captureDao.setExtractedText(captureId, text)
            repo.captureDao.setStatus(captureId, CaptureStatus.PENDING)
            CaptureWorker.enqueue(app)
        } catch (e: WhisperModelMissingException) {
            // Nothing to retry until the model is downloaded — say which one is missing
            // instead of showing a generic failure.
            repo.captureDao.setStatus(captureId, CaptureStatus.FAILED, "transcription_no_model")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            repo.captureDao.setStatus(captureId, CaptureStatus.FAILED, "transcription_timeout")
        } catch (e: Throwable) {
            // Throwable, not Exception: a broken native library fails with an Error, and
            // letting that escape is exactly what left the message stuck on
            // "transcribing" forever. Keep the real cause — it is the only clue the user
            // can pass on.
            val cause = e.message ?: e.javaClass.simpleName
            repo.captureDao.setStatus(captureId, CaptureStatus.FAILED, "transcription_failed: $cause")
        }
    }

    fun retry() = CaptureWorker.enqueue(app)

    /**
     * Adopts a research answer from the chat as a knowledge entry. The id is written back
     * into the message so the bubble still shows "saved" after an app restart — and a
     * second tap cannot create a duplicate.
     */
    fun saveResearchAsNote(entry: ChatEntry) {
        val message = entry.message ?: return
        viewModelScope.launch {
            val json = runCatching { JSONObject(message.summaryJson) }.getOrNull() ?: return@launch
            if (json.has("savedItemId")) return@launch
            val summary = json.optString("answer").takeIf { it.isNotBlank() } ?: return@launch
            val title = summary.lineSequence().firstOrNull { it.isNotBlank() }
                ?.trimStart('#', ' ')?.take(60)?.takeIf { it.isNotBlank() }
                ?: entry.capture.rawText.take(60)
            val id = repo.itemDao.insert(
                PlannerItem(
                    type = ItemType.NOTE,
                    title = title,
                    notes = summary + sourcesSection(app, json.optJSONArray("sources"), summary),
                ),
            )
            repo.chatMessageDao.updateSummary(message.id, json.put("savedItemId", id).toString())
        }
    }

    fun undo(captureId: Long) {
        viewModelScope.launch { repo.undoCapture(captureId) }
    }

    fun discardCapture(captureId: Long) {
        viewModelScope.launch { repo.deleteCapture(captureId) }
    }

    /**
     * Empties the chat history. Only the conversation goes — tasks, appointments and notes
     * created from it stay, and so do inputs still being processed.
     */
    fun clearChat(onCleared: (Int) -> Unit) {
        viewModelScope.launch { onCleared(repo.clearFinishedCaptures()) }
    }
}

/** One line in the Today activity feed: what exactly was saved or changed. */
sealed interface ActivityLine {
    data class SavedItem(val type: ItemType?, val title: String) : ActivityLine
    data class Link(val aTitle: String, val bTitle: String) : ActivityLine
    data class Command(val text: String) : ActivityLine
}

/** One processed capture in the Today activity feed with timestamp and detail lines. */
data class ActivityEntry(val at: Long, val lines: List<ActivityLine>)

/** One pending weekly-cleanup suggestion (action: delete/complete/none) with the affected entries. */
data class CleanupEntry(
    val action: String,
    val itemIds: List<Long>,
    val description: String,
    /** Resolved affected entries (id → title), for the expandable card. */
    val items: List<Pair<Long, String>> = emptyList(),
)

/** From this age on, undated entries are resurfaced; "Later" snoozes for the same duration. */
private const val RESURFACE_AGE_MS = 21L * 24 * 60 * 60 * 1000
private const val RESURFACE_SNOOZE_MS = RESURFACE_AGE_MS

/**
 * Grace period for the lists: past appointments (after dueAt) and checked-off entries
 * (after doneAt) stay visible this long, then disappear from the lists.
 */
private const val LIST_HIDE_GRACE_MS = 24L * 60 * 60 * 1000

private fun listCutoff(): Long = System.currentTimeMillis() - LIST_HIDE_GRACE_MS

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = app.repository
    private val claude = app.claudeService
    private val settings = app.settings

    private val _briefing = MutableStateFlow(loadBriefingIfToday())
    val briefingText: StateFlow<String?> = _briefing
    val generating = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    val todaysAppointments: Flow<List<PlannerItem>> =
        repo.itemDao.appointments(listCutoff()).map { list ->
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            list.filter {
                !it.done && it.dueAt != null &&
                        Instant.ofEpochMilli(it.dueAt).atZone(zone).toLocalDate() == today
            }
        }

    /** Resurfacing: old, undated entries to look at again (Done/Later/Discard). */
    val resurfaceItems: Flow<List<PlannerItem>> = repo.itemDao.resurfaceCandidates(
        olderThan = System.currentTimeMillis() - RESURFACE_AGE_MS,
        now = System.currentTimeMillis(),
        limit = 3,
    )

    fun resurfaceDone(id: Long) {
        viewModelScope.launch { repo.itemDao.setDone(id, true) }
    }

    fun resurfaceLater(id: Long) {
        viewModelScope.launch {
            repo.itemDao.snooze(
                id,
                System.currentTimeMillis() + RESURFACE_SNOOZE_MS
            )
        }
    }

    fun resurfaceDiscard(item: PlannerItem) {
        viewModelScope.launch { repo.itemDao.archive(item.id) }
    }

    // --- Weekly cleanup: suggestions from the settings, apply/ignore ---

    private val _cleanup = MutableStateFlow<List<CleanupEntry>>(emptyList())
    val cleanupEntries: StateFlow<List<CleanupEntry>> = _cleanup
    val cleanupRunning = MutableStateFlow(false)

    init {
        reloadCleanup()
    }

    /** Starts the cleanup manually and reloads the suggestions after completion. */
    fun runCleanupNow() {
        if (cleanupRunning.value) return
        cleanupRunning.value = true
        val workId = com.app.mindunload.work.CleanupWorker.runNow(app)
        viewModelScope.launch {
            androidx.work.WorkManager.getInstance(app)
                .getWorkInfoByIdFlow(workId)
                .first { it?.state?.isFinished == true }
            reloadCleanup()
            cleanupRunning.value = false
        }
    }

    /** Reload in case the CleanupWorker wrote in the meantime; resolves the item titles. */
    fun reloadCleanup() {
        viewModelScope.launch {
            _cleanup.value = loadCleanup().map { entry ->
                entry.copy(
                    items = entry.itemIds.mapNotNull { id ->
                        repo.itemDao.byId(id)?.let { id to it.title }
                    },
                )
            }
        }
    }

    private fun loadCleanup(): List<CleanupEntry> = runCatching {
        val json = settings.cleanupJson ?: return emptyList()
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idArr = obj.optJSONArray("itemIds")
                val ids = idArr?.let { a -> List(a.length()) { a.getLong(it) } } ?: emptyList()
                add(CleanupEntry(obj.optString("action"), ids, obj.optString("description")))
            }
        }
    }.getOrDefault(emptyList())

    fun applyCleanup(entry: CleanupEntry) {
        viewModelScope.launch {
            for (id in entry.itemIds) {
                val item = repo.itemDao.byId(id) ?: continue
                when (entry.action) {
                    "delete" -> {
                        if (item.type == ItemType.APPOINTMENT) ReminderScheduler.cancel(app, item)
                        repo.itemDao.archive(item.id)
                    }

                    "complete" -> repo.itemDao.setDone(id, true)
                }
            }
            removeCleanup(entry)
        }
    }

    fun dismissCleanup(entry: CleanupEntry) = removeCleanup(entry)

    private fun removeCleanup(entry: CleanupEntry) {
        val rest = _cleanup.value - entry
        _cleanup.value = rest
        settings.cleanupJson = if (rest.isEmpty()) {
            null
        } else {
            JSONArray(
                rest.map {
                    JSONObject()
                        .put("action", it.action)
                        .put("itemIds", JSONArray(it.itemIds))
                        .put("description", it.description)
                },
            ).toString()
        }
    }

    val recentActivity: StateFlow<List<ActivityEntry>> = combine(
        repo.captureDao.allOrdered(),
        repo.chatMessageDao.allOrdered(),
    ) { captures, messages ->
        val byCapture = messages.associateBy { it.captureId }
        captures.sortedByDescending { it.createdAt }.take(5).mapNotNull { capture ->
            val msg = byCapture[capture.id] ?: return@mapNotNull null
            val json =
                runCatching { JSONObject(msg.summaryJson) }.getOrNull() ?: return@mapNotNull null
            val lines = buildList {
                json.optJSONArray("items")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val type =
                            runCatching { ItemType.valueOf(obj.optString("type")) }.getOrNull()
                        add(ActivityLine.SavedItem(type, obj.optString("title")))
                    }
                }
                json.optJSONArray("links")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(ActivityLine.Link(obj.optString("aTitle"), obj.optString("bTitle")))
                    }
                }
                json.optJSONArray("commands")?.let { arr ->
                    for (i in 0 until arr.length()) add(ActivityLine.Command(arr.getString(i)))
                }
            }
            if (lines.isEmpty()) null else ActivityEntry(capture.createdAt, lines)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun loadBriefingIfToday(): String? =
        settings.briefingText?.takeIf { settings.briefingDate == LocalDate.now().toString() }

    fun generateNow() {
        viewModelScope.launch {
            generating.value = true
            error.value = null
            try {
                val today = LocalDate.now()
                // Same input as the BriefingWorker — a manually triggered briefing must not
                // differ from the scheduled one.
                val text = claude.generateBriefing(
                    com.app.mindunload.work.collectBriefingInput(app, repo, settings),
                )
                settings.briefingDate = today.toString()
                settings.briefingText = text
                _briefing.value = text
            } catch (e: Exception) {
                error.value = e.message ?: e.javaClass.simpleName
            } finally {
                generating.value = false
            }
        }
    }
}

class WikiViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = app.repository
    private val wiki = app.wikiService

    val notes: StateFlow<List<PlannerItem>> = repo.itemDao.notes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val answering = MutableStateFlow(false)
    val answer = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)

    fun ask(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            answering.value = true
            error.value = null
            answer.value = null
            try {
                answer.value = wiki.answer(question, repo.itemDao.notesOnce())
            } catch (e: Exception) {
                error.value = e.message ?: e.javaClass.simpleName
            } finally {
                answering.value = false
            }
        }
    }
}

/** Category in the drawer with its most frequent entry type (determines the icon). */
data class CategoryEntry(val name: String, val count: Int, val topType: ItemType)

class DrawerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = app.repository

    val noteCount = repo.itemDao.notes().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val ideaCount = repo.itemDao.byType(ItemType.IDEA, listCutoff()).map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val goalCount = repo.itemDao.byType(ItemType.GOAL, listCutoff()).map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val categories: StateFlow<List<CategoryEntry>> = repo.itemDao.all().map { items ->
        val cutoff = listCutoff()
        items.filter { !it.category.isNullOrBlank() }
            // Same rule as the category screen: long-completed entries no longer count.
            .filter { !it.done || (it.doneAt ?: 0L) >= cutoff }
            .groupBy { it.category!! }
            .map { (name, group) ->
                CategoryEntry(
                    name = name,
                    count = group.size,
                    topType = group.groupingBy { it.type }.eachCount()
                        .maxByOrNull { it.value }!!.key,
                )
            }
            .sortedByDescending { it.count }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class ItemsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = app.repository

    fun items(type: ItemType): Flow<List<PlannerItem>> = when (type) {
        ItemType.APPOINTMENT -> repo.itemDao.appointments(listCutoff())
        ItemType.SHOPPING_ITEM -> repo.itemDao.shoppingItems(listCutoff())
        else -> repo.itemDao.byType(type, listCutoff())
    }

    fun byCategory(name: String): Flow<List<PlannerItem>> =
        repo.itemDao.byCategory(name, listCutoff())

    /** Appointments of one calendar month, past months included. */
    fun appointmentsOfMonth(month: YearMonth): Flow<List<PlannerItem>> {
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return repo.itemDao.appointmentsBetween(from, to)
    }

    fun setDone(item: PlannerItem, done: Boolean) {
        viewModelScope.launch {
            repo.itemDao.setDone(item.id, done)
            if (item.type == ItemType.APPOINTMENT) ReminderScheduler.cancel(app, item)
        }
    }

    fun delete(item: PlannerItem) {
        viewModelScope.launch {
            if (item.type == ItemType.APPOINTMENT) ReminderScheduler.cancel(app, item)
            // Archive instead of delete: kept for the review.
            repo.itemDao.archive(item.id)
        }
    }

    /** Goal progress = completed / total linked tasks+appointments (null = none linked). */
    suspend fun progressOf(goalId: Long): Float? {
        val links = repo.linkDao.linksOfOnce(goalId)
        val others = links.mapNotNull { link ->
            val otherId = if (link.fromId == goalId) link.toId else link.fromId
            repo.itemDao.byId(otherId)
        }.filter { it.type == ItemType.TASK || it.type == ItemType.APPOINTMENT }
        if (others.isEmpty()) return null
        return others.count { it.done }.toFloat() / others.size
    }
}

data class LinkedItem(val link: ItemLink, val other: PlannerItem)

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = app.repository
    private val research = app.researchService

    val researching = MutableStateFlow(false)
    val researchError = MutableStateFlow<String?>(null)

    fun item(id: Long): Flow<PlannerItem?> = repo.itemDao.byIdFlow(id)

    fun notes(id: Long): Flow<List<ResearchNote>> = repo.researchDao.notesOf(id)

    fun researchNote(id: Long): Flow<ResearchNote?> = repo.researchDao.byIdFlow(id)

    fun linkedItems(id: Long): Flow<List<LinkedItem>> =
        repo.linkDao.linksOf(id).map { links ->
            links.mapNotNull { link ->
                val otherId = if (link.fromId == id) link.toId else link.fromId
                repo.itemDao.byId(otherId)?.let { LinkedItem(link, it) }
            }
        }

    fun startResearch(itemId: Long) {
        // Deliberately appScope instead of viewModelScope: the research runs 1-2 minutes, and
        // on leaving the screen viewModelScope would cancel the coroutine after the API call
        // but before saving — the result would be silently lost.
        app.appScope.launch {
            val item = repo.itemDao.byId(itemId) ?: return@launch
            researching.value = true
            researchError.value = null
            try {
                repo.researchDao.insert(research.research(item))
            } catch (e: Exception) {
                researchError.value = e.message ?: e.javaClass.simpleName
            } finally {
                researching.value = false
            }
        }
    }

    /** Manual editing of date/time; reschedules the reminder. */
    fun setDueAt(itemId: Long, millis: Long) {
        viewModelScope.launch {
            val item = repo.itemDao.byId(itemId) ?: return@launch
            val updated = item.copy(dueAt = millis)
            repo.itemDao.update(updated)
            if (updated.type == ItemType.APPOINTMENT) ReminderScheduler.schedule(app, updated)
        }
    }

    fun deleteLink(link: ItemLink) {
        viewModelScope.launch { repo.linkDao.delete(link) }
    }

    fun addLink(fromId: Long, toId: Long) {
        viewModelScope.launch {
            repo.linkDao.insert(ItemLink(fromId = fromId, toId = toId, createdBy = LinkOrigin.USER))
        }
    }

    fun allItems(): Flow<List<PlannerItem>> = repo.itemDao.all()

    /** Adopts a research note as a permanent wiki article (NOTE item). */
    fun promoteToWiki(title: String, note: ResearchNote, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                repo.itemDao.insert(
                    PlannerItem(
                        type = ItemType.NOTE,
                        title = title,
                        // Sources go into the body: a NOTE has no sources column, so without
                        // this the research sources are lost on promoting to the wiki.
                        notes = note.summary + sourcesSection(
                            app,
                            runCatching { JSONArray(note.sourcesJson) }.getOrNull(),
                            note.summary,
                        ),
                    ),
                )
            }.isSuccess
            onResult(ok)
        }
    }
}

/**
 * The sources as a Markdown section appended to the entry — otherwise they are lost on saving.
 * [MarkdownText] renders the list as tappable links. Sources already cited inline in [body]
 * are skipped so they do not show up twice.
 */
private fun sourcesSection(app: Application, sources: JSONArray?, body: String): String {
    if (sources == null || sources.length() == 0) return ""
    val lines = buildList {
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val url = source.optString("url").takeIf { it.isNotBlank() } ?: continue
            if (url in body) continue
            val label = source.optString("title").takeIf { it.isNotBlank() } ?: url
            add("- [$label]($url)")
        }
    }
    if (lines.isEmpty()) return ""
    return "\n\n## ${app.getString(R.string.sources_header)}\n${lines.joinToString("\n")}"
}

/** Prices per 1M tokens in USD (as of 07/2026); cache write = 1.25× input, cache read = 0.1× input. */
private data class ModelPricing(val inputPerMTok: Double, val outputPerMTok: Double)

private fun pricingFor(model: String): ModelPricing = when {
    "haiku" in model.lowercase() -> ModelPricing(1.0, 5.0)
    "sonnet" in model.lowercase() -> ModelPricing(3.0, 15.0)
    "opus" in model.lowercase() -> ModelPricing(5.0, 25.0)
    else -> ModelPricing(3.0, 15.0)
}

/** One row in the usage dashboard: consumption + estimated cost of one model. */
data class UsageRow(
    val model: String,
    val calls: Int,
    val inputTokens: Long,
    val cacheWriteTokens: Long,
    val cacheReadTokens: Long,
    val outputTokens: Long,
    val costUsd: Double,
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = app.repository
    private val settings = app.settings

    val budgetUsd = MutableStateFlow(settings.monthlyBudgetUsd)

    private val monthStart: Long = LocalDate.now().withDayOfMonth(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val rows: StateFlow<List<UsageRow>> = repo.apiUsageDao.usageByModelSince(monthStart)
        .map { list ->
            list.map { usage ->
                val price = pricingFor(usage.model)
                val cost = usage.inputTokens * price.inputPerMTok +
                        usage.cacheWriteTokens * price.inputPerMTok * 1.25 +
                        usage.cacheReadTokens * price.inputPerMTok * 0.1 +
                        usage.outputTokens * price.outputPerMTok
                UsageRow(
                    model = usage.model,
                    calls = usage.calls,
                    inputTokens = usage.inputTokens,
                    cacheWriteTokens = usage.cacheWriteTokens,
                    cacheReadTokens = usage.cacheReadTokens,
                    outputTokens = usage.outputTokens,
                    costUsd = cost / 1_000_000.0,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBudget(value: Float) {
        settings.monthlyBudgetUsd = value
        budgetUsd.value = value
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = app.settings
    private val repo = app.repository

    val hasKey = MutableStateFlow(settings.apiKey != null)
    val briefingTime = MutableStateFlow(settings.briefingHour to settings.briefingMinute)
    val weatherLocation = MutableStateFlow(settings.weatherLatitude to settings.weatherLongitude)

    fun setWeatherLocation(lat: Double?, lon: Double?) {
        settings.weatherLatitude = lat
        settings.weatherLongitude = lon
        weatherLocation.value = lat to lon
    }

    fun saveKey(key: String) {
        settings.apiKey = key
        hasKey.value = settings.apiKey != null
    }

    val reminderOffsets = MutableStateFlow(settings.reminderOffsets)

    /** Turns one lead time on or off and re-applies the result to all upcoming appointments. */
    fun toggleReminderOffset(minutes: Int) {
        val current = reminderOffsets.value
        val next = if (minutes in current) current - minutes else current + minutes
        settings.reminderOffsets = next
        reminderOffsets.value = settings.reminderOffsets
        viewModelScope.launch { ReminderScheduler.rescheduleAll(app) }
    }

    fun setBriefingTime(hour: Int, minute: Int) {
        settings.briefingHour = hour
        settings.briefingMinute = minute
        briefingTime.value = hour to minute
        // Immediately reschedule the existing alarm to the new time.
        BriefingScheduler.scheduleNext(app)
    }

    val cleanupRunning = MutableStateFlow(false)

    /**
     * Starts the cleanup and reports back whether the worker finished successfully — otherwise
     * the button would look like it did nothing for the 10-30s the API call takes.
     */
    fun runCleanupNow(onFinished: (Boolean) -> Unit) {
        if (cleanupRunning.value) return
        cleanupRunning.value = true
        val workId = com.app.mindunload.work.CleanupWorker.runNow(app)
        viewModelScope.launch {
            try {
                val info = androidx.work.WorkManager.getInstance(app)
                    .getWorkInfoByIdFlow(workId)
                    .first { it?.state?.isFinished == true }
                onFinished(info?.state == androidx.work.WorkInfo.State.SUCCEEDED)
            } finally {
                cleanupRunning.value = false
            }
        }
    }

    // --- On-device speech model for voice messages ---

    val whisperModel = MutableStateFlow(settings.whisperModel)
    val installedWhisperModels = MutableStateFlow(Whisper.installedModels(app))

    /** null = no download running; otherwise 0..1. */
    val whisperDownload = MutableStateFlow<Float?>(null)
    val whisperError = MutableStateFlow<String?>(null)

    fun selectWhisperModel(model: WhisperModel) {
        settings.whisperModel = model
        whisperModel.value = model
    }

    /**
     * Downloads the selected speech model. Runs in appScope: the download takes minutes
     * on a slow connection and must survive leaving the settings screen.
     */
    fun downloadWhisperModel(model: WhisperModel) {
        if (whisperDownload.value != null) return
        whisperDownload.value = 0f
        whisperError.value = null
        app.appScope.launch {
            try {
                Whisper.downloadModel(app, model) { whisperDownload.value = it }
                installedWhisperModels.value = Whisper.installedModels(app)
            } catch (e: Exception) {
                whisperError.value = e.message ?: e.javaClass.simpleName
            } finally {
                whisperDownload.value = null
            }
        }
    }

    fun deleteWhisperModel(model: WhisperModel) {
        Whisper.deleteModel(app, model)
        installedWhisperModels.value = Whisper.installedModels(app)
    }

    suspend fun exportJson(): String = JsonExporter.export(repo)

    suspend fun importJson(json: String): com.app.mindunload.export.JsonImporter.Result =
        com.app.mindunload.export.JsonImporter.import(repo, json)
}
