package com.app.mindunload.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class ItemType { TASK, IDEA, GOAL, APPOINTMENT, SHOPPING_ITEM, NOTE }

enum class Priority { HIGH, MEDIUM, LOW, NONE }

enum class LinkRelation { RELATED, SAME_TOPIC, DEPENDS_ON, PART_OF }

enum class LinkOrigin { AI, USER }

/**
 * TRANSCRIBING is only used by voice messages: the recording is already in the chat,
 * the speech-to-text step still runs. The worker deliberately ignores that state — it
 * would otherwise process a capture whose text does not exist yet.
 */
enum class CaptureStatus { TRANSCRIBING, PENDING, PROCESSING, DONE, FAILED }

/**
 * What a chat input is for — determines the system prompt during processing:
 * CAPTURE = capturing/commands (structured output), ASK = queries/summaries
 * (tasks by topic, appointments, goals, knowledge, day planning), REVIEW =
 * retrospective over a period, RESEARCH = web research on a free topic, savable
 * as a knowledge entry, STRUCTURE = turns a (typically long, spoken) stream of
 * thought into a structured Markdown note, also savable as a knowledge entry.
 */
enum class ChatMode { CAPTURE, ASK, REVIEW, RESEARCH, STRUCTURE }

/** What is attached to a chat message besides its text — see [CaptureRequest.attachmentPath]. */
enum class AttachmentKind { NONE, IMAGE, AUDIO }

/**
 * Named accent theme, picked in Settings → Design. Only the accent-driven colors (primary,
 * chips, the briefing card panel) change between palettes — the neutral background/surface
 * tones stay the same editorial warm/dark pair for every palette; see
 * [com.app.mindunload.ui.theme.PlannerColors].
 */
enum class ColorPalette {
    WARM, OCEAN, VIOLET, SLATE;

    companion object {
        val DEFAULT = WARM
        fun fromName(name: String?): ColorPalette = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Dark-mode preference, picked in Settings → Design — a plain on/off, no "follow system"
 *  option (deliberately dropped: the app's palette is already a fixed, non-dynamic look,
 *  see [com.app.mindunload.ui.theme.PlannerColors]). */
enum class DarkModePreference {
    LIGHT, DARK;

    companion object {
        val DEFAULT = LIGHT
        fun fromName(name: String?): DarkModePreference =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

@Entity(tableName = "items")
data class PlannerItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val title: String,
    val notes: String? = null,
    val category: String? = null,
    val priority: Priority = Priority.NONE,
    /** Due date/appointment time as epoch millis. */
    val dueAt: Long? = null,
    /** Name of the shopping list, only for SHOPPING_ITEM. */
    val listName: String? = null,
    val quantity: String? = null,
    val tags: List<String> = emptyList(),
    val done: Boolean = false,
    /** When the entry was completed — basis for the review. */
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Resurfacing: do not resurface again until this point in time ("Later"). */
    val snoozedUntil: Long? = null,
    /**
     * Archived instead of deleted: disappears from all active views and
     * AI queries, but is kept for the review.
     */
    val archivedAt: Long? = null,
    /** Flagged by the AI during parsing: research would be worthwhile here. */
    val researchSuggested: Boolean = false,
    /**
     * Recurrence rule for recurring appointments/tasks, see [Recurrence]:
     * "daily[:n]" | "weekly[:n]" | "monthly[:n]" | "monthly_weekday[:n]" | "yearly".
     * [dueAt] is always the next/current occurrence.
     */
    val recurrence: String? = null,
    /** Capture batch the item originated from — basis for "Undo". */
    val sourceCaptureId: Long? = null,
)

@Entity(
    tableName = "links",
    foreignKeys = [
        ForeignKey(
            entity = PlannerItem::class,
            parentColumns = ["id"],
            childColumns = ["fromId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannerItem::class,
            parentColumns = ["id"],
            childColumns = ["toId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fromId"), Index("toId")],
)
data class ItemLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val relation: LinkRelation = LinkRelation.RELATED,
    val reason: String? = null,
    val createdBy: LinkOrigin = LinkOrigin.AI,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceCaptureId: Long? = null,
)

@Entity(
    tableName = "research_notes",
    foreignKeys = [
        ForeignKey(
            entity = PlannerItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class ResearchNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val summary: String,
    /** JSON array of {"title","url"}. */
    val sourcesJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "captures")
data class CaptureRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val status: CaptureStatus = CaptureStatus.PENDING,
    val errorMessage: String? = null,
    /** Selected chat function — determines how the worker processes the input. */
    val mode: ChatMode = ChatMode.CAPTURE,
    /**
     * Absolute path of the attached photo/voice message in the app's private storage
     * (see [Attachments]); null when the message is plain text.
     */
    val attachmentPath: String? = null,
    val attachmentKind: AttachmentKind = AttachmentKind.NONE,
    /**
     * Whether the attachment has already been turned into text (photo → OCR,
     * voice message → transcript). Without it a retry after a failed parse would
     * pay for the extraction a second time.
     */
    val attachmentExtracted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A single API call with token usage — basis for the usage dashboard. */
@Entity(tableName = "api_usage")
data class ApiUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Which feature triggered the call (e.g. "parseCommand", "generateBriefing"). */
    val feature: String,
    /** Model id of the call (e.g. "anthropic/claude-haiku-4.5"). */
    val model: String,
    val inputTokens: Long,
    val cacheWriteTokens: Long,
    val cacheReadTokens: Long,
    val outputTokens: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Snapshot of the assistant response in the chat history for a completed capture:
 * saved items, new links, applied change commands — as ready-made JSON text so the
 * chat bubble can be rendered without relation joins.
 *
 * Chat functions store a free-text answer instead: {"answer":"…"}, research
 * additionally {"sources":[{"title","url"}]} and, once adopted as a knowledge entry,
 * {"savedItemId":<id>}.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = CaptureRequest::class,
            parentColumns = ["id"],
            childColumns = ["captureId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("captureId", unique = true)],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val captureId: Long,
    /** JSON object {"items":[{"type","title"}],"links":[{"aTitle","bTitle","relation"}],"commands":["..."]} */
    val summaryJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

private const val TAG_SEP = "\u001F"

class Converters {
    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString(TAG_SEP)

    @TypeConverter
    fun stringToTags(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(TAG_SEP)
}
