package com.app.mindunload.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anthropic.errors.AnthropicServiceException
import com.app.mindunload.PlannerApp
import com.app.mindunload.R
import com.app.mindunload.ai.BacklogTools
import com.app.mindunload.ai.MissingApiKeyException
import com.app.mindunload.ai.ParsedAction
import com.app.mindunload.ai.dueAtMillis
import com.app.mindunload.ai.resolveLinkRef
import com.app.mindunload.ai.toItemType
import com.app.mindunload.ai.toPriority
import com.app.mindunload.ai.toPriorityOrNull
import com.app.mindunload.ai.toRelation
import com.app.mindunload.ai.toRelationOrDefault
import com.app.mindunload.data.AttachmentKind
import com.app.mindunload.data.CaptureStatus
import com.app.mindunload.data.ChatMessage
import com.app.mindunload.data.ChatMode
import com.app.mindunload.data.ItemLink
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.LinkOrigin
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.PlannerRepository
import com.app.mindunload.data.Recurrence
import com.app.mindunload.reminders.ReminderScheduler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

/**
 * Outbox processing: takes PENDING/FAILED captures, has Claude structure them
 * and saves items + link suggestions. Runs with a network constraint and backoff —
 * capturing therefore never fails, only the processing waits.
 */
class CaptureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PlannerApp
        val repo = app.repository
        val claude = app.claudeService

        // Targeted backlog access for the tool loop: the model searches itself
        // (all types, including completed) instead of getting the whole backlog in the prompt.
        val backlogTools = object : BacklogTools {
            override suspend fun search(query: String, type: ItemType?) =
                repo.itemDao.searchItems(query, type?.name)

            override suspend fun recent(type: ItemType?) =
                repo.itemDao.recentItems(type?.name, 15)

            override suspend fun open(type: ItemType?) =
                repo.itemDao.openItems(type?.name, 50)

            override suspend fun agenda(fromMillis: Long, toMillis: Long) =
                repo.itemDao.agendaItems(fromMillis, toMillis)
        }

        var anyFailedRetryable = false
        for (pending in repo.captureDao.pendingOnce()) {
            repo.captureDao.setStatus(pending.id, CaptureStatus.PROCESSING)
            try {
                // Photo messages first become text; everything after this point works on
                // plain text and does not care where it came from.
                val capture = extractImageText(repo, claude, pending)

                // Chat functions (ask/review/research) return a free-text answer
                // instead of the structured-output capture — same outbox, same retries.
                if (capture.mode != ChatMode.CAPTURE) {
                    val answer = chatAnswer(repo, claude, app.researchService, backlogTools, capture)
                    repo.chatMessageDao.insert(
                        ChatMessage(captureId = capture.id, summaryJson = answer.toString()),
                    )
                    repo.captureDao.setStatus(capture.id, CaptureStatus.DONE)
                    continue
                }
                val parsed = claude.parseCommand(
                    capture.rawText,
                    backlogTools,
                    listNames = repo.itemDao.listNames(),
                    categories = repo.itemDao.categoriesOnce(),
                )

                val savedItems = JSONArray()
                val newIds = mutableListOf<Long>()
                for (parsedItem in parsed.items) {
                    val item = PlannerItem(
                        type = parsedItem.toItemType(),
                        title = parsedItem.title.ifBlank { capture.rawText.take(80) },
                        notes = parsedItem.notes,
                        category = parsedItem.category,
                        priority = parsedItem.toPriority(),
                        dueAt = parsedItem.dueAtMillis(),
                        listName = parsedItem.listName,
                        quantity = parsedItem.quantity,
                        tags = parsedItem.tags,
                        sourceCaptureId = capture.id,
                        researchSuggested = parsedItem.research,
                        // Only accept validated rules; a series needs a start date.
                        recurrence = parsedItem.recurrence
                            ?.takeIf { Recurrence.isValid(it) && parsedItem.dueAtMillis() != null },
                    )
                    val id = repo.itemDao.insert(item)
                    newIds += id
                    if (item.type == ItemType.APPOINTMENT && item.dueAt != null) {
                        ReminderScheduler.schedule(applicationContext, item.copy(id = id))
                    }
                    savedItems.put(
                        JSONObject().put("type", item.type.name).put("title", item.title)
                    )
                }

                val newLinks = JSONArray()
                for (suggestion in parsed.links) {
                    val fromId = resolveLinkRef(suggestion.from, newIds) ?: continue
                    val toId = resolveLinkRef(suggestion.to, newIds) ?: continue
                    if (fromId == toId) continue
                    // Validate references to existing items, otherwise the FK throws.
                    val fromItem = repo.itemDao.byId(fromId) ?: continue
                    val toItem = repo.itemDao.byId(toId) ?: continue
                    repo.linkDao.insert(
                        ItemLink(
                            fromId = fromId,
                            toId = toId,
                            relation = suggestion.toRelation(),
                            reason = suggestion.reason,
                            sourceCaptureId = capture.id,
                        ),
                    )
                    newLinks.put(
                        JSONObject()
                            .put("aTitle", fromItem.title)
                            .put("bTitle", toItem.title)
                            .put("relation", suggestion.relation),
                    )
                }

                val appliedCommands = JSONArray()
                for (action in parsed.commands) {
                    val description = applyCommand(repo, action, newIds) ?: continue
                    appliedCommands.put(description)
                }

                // Always save a response — even when nothing was applicable, the chat
                // should say so instead of staying silent.
                val summary = JSONObject()
                    .put("items", savedItems)
                    .put("links", newLinks)
                    .put("commands", appliedCommands)
                val skippedCommands = parsed.commands.size - appliedCommands.length()
                if (skippedCommands > 0) summary.put("skippedCommands", skippedCommands)
                repo.chatMessageDao.insert(
                    ChatMessage(captureId = capture.id, summaryJson = summary.toString()),
                )

                repo.captureDao.setStatus(capture.id, CaptureStatus.DONE)
            } catch (e: MissingApiKeyException) {
                repo.captureDao.setStatus(pending.id, CaptureStatus.FAILED, "no_api_key")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Worker was stopped; put the capture back into the queue and rethrow.
                // NonCancellable, otherwise the ongoing cancellation swallows the DB write.
                withContext(NonCancellable) {
                    repo.captureDao.setStatus(pending.id, CaptureStatus.PENDING)
                }
                throw e
            } catch (e: AnthropicServiceException) {
                repo.captureDao.setStatus(
                    pending.id,
                    CaptureStatus.FAILED,
                    "${e.statusCode()}: ${e.message}"
                )
                // 4xx (except 408/429) is permanent — auto-retry would only drive up
                // the backoff and block the outbox for new inputs.
                if (e.statusCode() >= 500 || e.statusCode() == 408 || e.statusCode() == 429) {
                    anyFailedRetryable = true
                }
            } catch (e: Throwable) {
                // Deliberately Throwable: errors (e.g. NoSuchMethodError from missing
                // Android APIs) would otherwise leave the capture stuck on PROCESSING.
                repo.captureDao.setStatus(
                    pending.id,
                    CaptureStatus.FAILED,
                    e.message ?: e.javaClass.simpleName
                )
                anyFailedRetryable = true
            }
        }
        return if (anyFailedRetryable) Result.retry() else Result.success()
    }

    /**
     * Photo message → text. Reads the stored JPEG, has Claude transcribe it and writes the
     * result back into the capture, so the chat bubble shows what is actually being
     * processed. A caption typed alongside the photo stays in front of it — it is the
     * instruction ("auf die Einkaufsliste"), the extracted text is the material.
     *
     * Returns the capture unchanged when there is nothing to extract (no photo, or the
     * extraction already ran on an earlier attempt).
     */
    private suspend fun extractImageText(
        repo: PlannerRepository,
        claude: com.app.mindunload.ai.ClaudeService,
        capture: com.app.mindunload.data.CaptureRequest,
    ): com.app.mindunload.data.CaptureRequest {
        if (capture.attachmentKind != AttachmentKind.IMAGE || capture.attachmentExtracted) {
            return capture
        }
        val file = capture.attachmentPath?.let { java.io.File(it) }
        if (file == null || !file.exists()) {
            // The photo is gone (cleared storage, restored backup) — process the caption
            // alone instead of failing the message forever.
            repo.captureDao.setExtractedText(capture.id, capture.rawText)
            return capture.copy(attachmentExtracted = true)
        }
        val base64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
        val extracted = claude.extractTextFromImage(base64)
            .ifBlank { applicationContext.getString(R.string.image_no_text) }
        val combined = listOf(capture.rawText.trim(), extracted)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        repo.captureDao.setExtractedText(capture.id, combined)
        return capture.copy(rawText = combined, attachmentExtracted = true)
    }

    /**
     * Answers a chat input depending on the selected function — as the summaryJson of the
     * chat message: {"answer": …}, research additionally with its sources.
     */
    private suspend fun chatAnswer(
        repo: PlannerRepository,
        claude: com.app.mindunload.ai.ClaudeService,
        research: com.app.mindunload.ai.ResearchService,
        backlogTools: BacklogTools,
        capture: com.app.mindunload.data.CaptureRequest,
    ): JSONObject = when (capture.mode) {
        ChatMode.RESEARCH -> {
            val result = research.researchTopic(capture.rawText)
            JSONObject().put("answer", result.summary).put("sources", result.sources)
        }

        else -> JSONObject().put("answer", chatText(repo, claude, backlogTools, capture))
    }

    /** The free-text chat functions that answer from the user's own data. */
    private suspend fun chatText(
        repo: PlannerRepository,
        claude: com.app.mindunload.ai.ClaudeService,
        backlogTools: BacklogTools,
        capture: com.app.mindunload.data.CaptureRequest,
    ): String = when (capture.mode) {
        ChatMode.ASK -> {
            val nowMs = System.currentTimeMillis()
            val all = repo.itemDao.allOnce()
            val upcoming = all.filter {
                it.type == ItemType.APPOINTMENT && !it.done && it.dueAt != null &&
                        it.dueAt in nowMs..(nowMs + 14 * 86_400_000L)
            }.sortedBy { it.dueAt }.take(25)
            val datedTasks = all.filter { it.type == ItemType.TASK && !it.done && it.dueAt != null }
                .sortedBy { it.dueAt }
                .take(15)
            // Inventory per type: without it the model answers "nothing found" for entries
            // that are simply not in the context excerpt.
            val openCounts = repo.itemDao.openCountsByType().associate { it.type to it.count }
            claude.answerQuery(capture.rawText, backlogTools, upcoming, datedTasks, openCounts)
        }

        ChatMode.REVIEW -> {
            val text = capture.rawText.lowercase()
            val now = java.time.LocalDateTime.now()
            val (fromDate, label) = when {
                "jahr" in text || "year" in text ->
                    now.minusYears(1) to applicationContext.getString(R.string.review_year)

                "monat" in text || "month" in text ->
                    now.minusMonths(1) to applicationContext.getString(R.string.review_month)

                else -> now.minusWeeks(1) to applicationContext.getString(R.string.review_week)
            }
            val zone = java.time.ZoneId.systemDefault()
            val from = fromDate.atZone(zone).toInstant().toEpochMilli()
            val to = now.atZone(zone).toInstant().toEpochMilli()
            val items = repo.itemDao.reviewItems(from, to)
            if (items.isEmpty()) {
                applicationContext.getString(R.string.review_empty)
            } else {
                claude.generateReview(items, from, to, label)
            }
        }

        ChatMode.CAPTURE, ChatMode.RESEARCH ->
            throw IllegalStateException("${capture.mode} handled separately")
    }

    /** Applies a single change command; returns the description sentence for the chat, or null when it was not applicable. */
    private suspend fun applyCommand(
        repo: PlannerRepository,
        action: ParsedAction,
        newIds: List<Long>,
    ): String? {
        val targetId = resolveLinkRef(action.targetRef, newIds) ?: return null
        val target = repo.itemDao.byId(targetId) ?: return null

        when (action.action.lowercase()) {
            "rename" -> {
                val newTitle = action.newTitle?.takeIf { it.isNotBlank() } ?: return null
                repo.itemDao.update(target.copy(title = newTitle))
            }

            // The model sends the complete new text; it has the old one from the search results.
            "set_notes" -> {
                val newNotes = action.newNotes?.takeIf { it.isNotBlank() } ?: return null
                repo.itemDao.update(target.copy(notes = newNotes))
            }

            "append_notes" -> {
                val addition = action.newNotes?.takeIf { it.isNotBlank() } ?: return null
                val existing = target.notes?.takeIf { it.isNotBlank() }
                repo.itemDao.update(
                    target.copy(notes = if (existing == null) addition else "$existing\n\n$addition"),
                )
            }

            "set_category" -> {
                val category = action.newCategory?.takeIf { it.isNotBlank() } ?: return null
                repo.itemDao.update(target.copy(category = category))
            }

            "complete" -> repo.itemDao.setDone(targetId, true)
            "uncomplete" -> repo.itemDao.setDone(targetId, false)
            "delete" -> {
                // Archive instead of delete: kept for the review.
                if (target.type == ItemType.APPOINTMENT) ReminderScheduler.cancel(
                    applicationContext,
                    target
                )
                repo.itemDao.archive(target.id)
            }

            "set_priority" -> {
                val priority = action.priority.toPriorityOrNull() ?: return null
                repo.itemDao.update(target.copy(priority = priority))
            }

            "set_recurrence" -> {
                val rule = action.recurrence?.trim()?.lowercase()
                when {
                    rule.isNullOrEmpty() -> return null
                    rule == "none" -> repo.itemDao.update(target.copy(recurrence = null))
                    // Validate the rule; without a start date nothing can roll forward.
                    Recurrence.isValid(rule) && (target.dueAt != null || action.dueAtMillis() != null) -> {
                        val updated = target.copy(
                            recurrence = rule,
                            dueAt = action.dueAtMillis() ?: target.dueAt,
                        )
                        repo.itemDao.update(updated)
                        if (updated.type == ItemType.APPOINTMENT) {
                            ReminderScheduler.schedule(applicationContext, updated)
                        }
                    }

                    else -> return null
                }
            }

            "set_due" -> {
                val dueAt = action.dueAtMillis() ?: return null
                val updated = target.copy(dueAt = dueAt)
                repo.itemDao.update(updated)
                if (updated.type == ItemType.APPOINTMENT) ReminderScheduler.schedule(
                    applicationContext,
                    updated
                )
            }

            "link" -> {
                val otherId =
                    action.linkTargetRef?.let { resolveLinkRef(it, newIds) } ?: return null
                if (otherId == targetId) return null
                val other = repo.itemDao.byId(otherId) ?: return null
                repo.linkDao.insert(
                    ItemLink(
                        fromId = targetId,
                        toId = otherId,
                        relation = action.toRelationOrDefault(),
                        reason = action.reason,
                        createdBy = LinkOrigin.USER,
                    ),
                )
            }

            "unlink" -> {
                val otherId =
                    action.linkTargetRef?.let { resolveLinkRef(it, newIds) } ?: return null
                repo.linkDao.between(targetId, otherId).forEach { repo.linkDao.delete(it) }
            }

            else -> return null
        }
        return action.reason?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.command_applied_fallback, target.title)
    }

    companion object {
        private const val UNIQUE_NAME = "capture-processing"

        /** Kicks off the processing; calling it multiple times is harmless. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CaptureWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build()
            // REPLACE instead of APPEND_OR_REPLACE: if an earlier run hangs in retry backoff,
            // an append would block new inputs behind it (up to hours). A cancelled run is
            // harmless — captures go back to PENDING and the replacement run picks them up
            // again immediately.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
