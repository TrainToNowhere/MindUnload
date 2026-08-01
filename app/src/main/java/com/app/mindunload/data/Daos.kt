package com.app.mindunload.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerItemDao {
    @Insert
    suspend fun insert(item: PlannerItem): Long

    @Update
    suspend fun update(item: PlannerItem)

    @Delete
    suspend fun delete(item: PlannerItem)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): PlannerItem?

    @Query("SELECT * FROM items WHERE id = :id")
    fun byIdFlow(id: Long): Flow<PlannerItem?>

    // All "active" queries hide archived entries (archivedAt IS NULL) — archived
    // entries only exist for the review and byId access.

    /** Active entries of one type; completed ones disappear once [doneCutoff] (usually now - 1 day) has passed their doneAt. */
    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL AND type = :type " +
                "AND (done = 0 OR doneAt >= :doneCutoff) ORDER BY done ASC, priority ASC, createdAt DESC",
    )
    fun byType(type: ItemType, doneCutoff: Long): Flow<List<PlannerItem>>

    /** Active appointments; past ones disappear once [cutoff] (usually now - 1 day) has passed them. */
    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL AND type = 'APPOINTMENT' " +
                "AND (dueAt IS NULL OR dueAt >= :cutoff) ORDER BY done ASC, dueAt ASC",
    )
    fun appointments(cutoff: Long): Flow<List<PlannerItem>>

    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL AND type = 'SHOPPING_ITEM' " +
                "AND (done = 0 OR doneAt >= :doneCutoff) ORDER BY listName, done ASC, createdAt DESC",
    )
    fun shoppingItems(doneCutoff: Long): Flow<List<PlannerItem>>

    @Query("SELECT * FROM items WHERE archivedAt IS NULL")
    fun all(): Flow<List<PlannerItem>>

    @Query("SELECT * FROM items WHERE archivedAt IS NULL")
    suspend fun allOnce(): List<PlannerItem>

    /** Truly everything, including the archive — only for export/backup. */
    @Query("SELECT * FROM items")
    suspend fun allIncludingArchived(): List<PlannerItem>

    /** Keyword search for the AI tools: targeted backlog access instead of a full dump into the prompt. */
    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL " +
                "AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' " +
                "OR category LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') " +
                "AND (:type IS NULL OR type = :type) ORDER BY done ASC, createdAt DESC LIMIT 20",
    )
    suspend fun searchItems(query: String, type: String?): List<PlannerItem>

    @Query("SELECT * FROM items WHERE archivedAt IS NULL AND (:type IS NULL OR type = :type) ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentItems(type: String?, limit: Int): List<PlannerItem>

    /**
     * All open entries, optionally of one type — for the AI's list_open tool ("welche Aufgaben
     * habe ich noch?"). Dated ones first in date order, then the undated ones, newest first.
     */
    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL AND done = 0 " +
                "AND (:type IS NULL OR type = :type) " +
                "ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC LIMIT :limit",
    )
    suspend fun openItems(type: String?, limit: Int): List<PlannerItem>

    /** How many open entries exist per type — the AI's inventory overview. */
    @Query(
        "SELECT type AS type, COUNT(*) AS count FROM items " +
                "WHERE archivedAt IS NULL AND done = 0 GROUP BY type",
    )
    suspend fun openCountsByType(): List<TypeCount>

    /** Dated entries in a period, for the AI's list_agenda tool ("was steht im August an?"). */
    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL AND done = 0 " +
                "AND dueAt BETWEEN :from AND :to ORDER BY dueAt ASC LIMIT 50",
    )
    suspend fun agendaItems(from: Long, to: Long): List<PlannerItem>

    @Query("SELECT DISTINCT listName FROM items WHERE archivedAt IS NULL AND listName IS NOT NULL AND listName != ''")
    suspend fun listNames(): List<String>

    @Query("SELECT DISTINCT category FROM items WHERE archivedAt IS NULL AND category IS NOT NULL AND category != ''")
    suspend fun categoriesOnce(): List<String>

    @Query("SELECT * FROM items WHERE archivedAt IS NULL AND type = 'APPOINTMENT' AND done = 0 AND dueAt > :now")
    suspend fun upcomingAppointments(now: Long): List<PlannerItem>

    @Query("DELETE FROM items WHERE sourceCaptureId = :captureId")
    suspend fun deleteByCapture(captureId: Long)

    @Query("UPDATE items SET done = :done, doneAt = CASE WHEN :done THEN :at ELSE NULL END WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, at: Long = System.currentTimeMillis())

    /** Archive instead of delete: gone from all active views, kept for the review. */
    @Query("UPDATE items SET archivedAt = :at WHERE id = :id")
    suspend fun archive(id: Long, at: Long = System.currentTimeMillis())

    @Query("UPDATE items SET researchSuggested = 0 WHERE id = :id")
    suspend fun clearResearchSuggested(id: Long)

    /**
     * Resurfacing: old, undated, uncompleted entries (no shopping items/notes/appointments)
     * that are not snoozed via "Later" — oldest first.
     */
    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL AND done = 0 AND dueAt IS NULL " +
                "AND type IN ('TASK', 'IDEA', 'GOAL') AND createdAt < :olderThan " +
                "AND (snoozedUntil IS NULL OR snoozedUntil < :now) " +
                "ORDER BY createdAt ASC LIMIT :limit",
    )
    fun resurfaceCandidates(olderThan: Long, now: Long, limit: Int): Flow<List<PlannerItem>>

    @Query("UPDATE items SET snoozedUntil = :until WHERE id = :id")
    suspend fun snooze(id: Long, until: Long)

    @Query("SELECT * FROM items WHERE archivedAt IS NULL AND type = 'NOTE' ORDER BY createdAt DESC")
    fun notes(): Flow<List<PlannerItem>>

    @Query("SELECT * FROM items WHERE archivedAt IS NULL AND type = 'NOTE'")
    suspend fun notesOnce(): List<PlannerItem>

    /** Distribution of the freely assigned categories for the drawer counts. */
    @Query(
        "SELECT category AS name, COUNT(*) AS count FROM items " +
                "WHERE archivedAt IS NULL AND category IS NOT NULL AND category != '' GROUP BY category ORDER BY count DESC",
    )
    fun categoryCounts(): Flow<List<CategoryCount>>

    @Query(
        "SELECT * FROM items WHERE archivedAt IS NULL AND category = :category " +
                "AND (done = 0 OR doneAt >= :doneCutoff) ORDER BY done ASC, type, createdAt DESC",
    )
    fun byCategory(category: String, doneCutoff: Long): Flow<List<PlannerItem>>

    /**
     * Review: everything that happened in the period — completed, newly captured, took place
     * (appointments), or archived. Deliberately includes archived entries.
     */
    @Query(
        "SELECT * FROM items WHERE (doneAt BETWEEN :from AND :to) " +
                "OR (createdAt BETWEEN :from AND :to) " +
                "OR (type = 'APPOINTMENT' AND dueAt BETWEEN :from AND :to) " +
                "OR (archivedAt BETWEEN :from AND :to) " +
                "ORDER BY createdAt ASC",
    )
    suspend fun reviewItems(from: Long, to: Long): List<PlannerItem>
}

data class CategoryCount(val name: String, val count: Int)

data class TypeCount(val type: ItemType, val count: Int)

@Dao
interface ItemLinkDao {
    @Insert
    suspend fun insert(link: ItemLink): Long

    @Delete
    suspend fun delete(link: ItemLink)

    @Query("SELECT * FROM links WHERE fromId = :itemId OR toId = :itemId")
    fun linksOf(itemId: Long): Flow<List<ItemLink>>

    @Query("SELECT * FROM links WHERE fromId = :itemId OR toId = :itemId")
    suspend fun linksOfOnce(itemId: Long): List<ItemLink>

    @Query(
        "SELECT * FROM links WHERE (fromId = :a AND toId = :b) OR (fromId = :b AND toId = :a)",
    )
    suspend fun between(a: Long, b: Long): List<ItemLink>

    @Query("SELECT * FROM links")
    fun all(): Flow<List<ItemLink>>

    @Query("SELECT * FROM links")
    suspend fun allOnce(): List<ItemLink>
}

@Dao
interface ResearchNoteDao {
    @Insert
    suspend fun insert(note: ResearchNote): Long

    @Delete
    suspend fun delete(note: ResearchNote)

    @Query("SELECT * FROM research_notes WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun notesOf(itemId: Long): Flow<List<ResearchNote>>

    @Query("SELECT * FROM research_notes WHERE id = :id")
    fun byIdFlow(id: Long): Flow<ResearchNote?>

    @Query("SELECT * FROM research_notes")
    suspend fun allOnce(): List<ResearchNote>
}

@Dao
interface CaptureDao {
    @Insert
    suspend fun insert(capture: CaptureRequest): Long

    @Update
    suspend fun update(capture: CaptureRequest)

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun byId(id: Long): CaptureRequest?

    @Query("SELECT * FROM captures WHERE id = :id")
    fun byIdFlow(id: Long): Flow<CaptureRequest?>

    @Query("SELECT COUNT(*) FROM items WHERE sourceCaptureId = :captureId")
    suspend fun itemCountOfCapture(captureId: Long): Int

    // PROCESSING is included: if a capture gets stuck there due to a process death,
    // it is picked up again on the next run (worker runs as unique work, sequentially).
    // TRANSCRIBING is not: those captures have no text yet.
    //
    // The attachment condition is what keeps a failed transcription out of here. FAILED is
    // a retry state, so without it the worker would pick up a voice message whose speech
    // recognition just failed, send its empty text to Claude, get a "what do you mean?"
    // answer back and mark the whole thing DONE — erasing the actual error on the way.
    // A voice message is only ready once its transcript exists; a photo carries its own
    // content and is extracted by the worker itself.
    @Query(
        "SELECT * FROM captures WHERE status IN ('PENDING', 'FAILED', 'PROCESSING') " +
                "AND (attachmentKind != 'AUDIO' OR attachmentExtracted = 1) " +
                "ORDER BY createdAt ASC",
    )
    suspend fun pendingOnce(): List<CaptureRequest>

    /**
     * Fills in the text of a capture afterwards — the transcript of a voice message, or
     * the text extracted from a photo. The chat bubble then shows what was processed.
     */
    @Query("UPDATE captures SET rawText = :text, attachmentExtracted = 1 WHERE id = :id")
    suspend fun setExtractedText(id: Long, text: String)

    /**
     * Voice messages left in TRANSCRIBING by a process death (or a crash inside the
     * speech recognition). Nothing picks those up on its own — the transcription runs in
     * memory, not in the outbox — so at app start they are marked failed and get their
     * retry button back.
     */
    @Query("UPDATE captures SET status = 'FAILED', errorMessage = :reason WHERE status = 'TRANSCRIBING'")
    suspend fun failStaleTranscriptions(reason: String): Int

    /** Attachment paths of captures being deleted — the files have to go with them. */
    @Query("SELECT attachmentPath FROM captures WHERE attachmentPath IS NOT NULL AND status IN ('DONE', 'FAILED')")
    suspend fun finishedAttachmentPaths(): List<String>

    @Query("SELECT * FROM captures WHERE status != 'DONE' ORDER BY createdAt DESC")
    fun unfinished(): Flow<List<CaptureRequest>>

    /** Entire chat history, oldest first. */
    @Query("SELECT * FROM captures ORDER BY createdAt ASC")
    fun allOrdered(): Flow<List<CaptureRequest>>

    @Query("UPDATE captures SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: CaptureStatus, error: String? = null)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Clears the chat history (chat_messages follow via CASCADE). Entries already created
     * from it are kept — they live in `items` without a foreign key. Captures still being
     * processed stay so the running worker does not lose its input.
     */
    @Query("DELETE FROM captures WHERE status IN ('DONE', 'FAILED')")
    suspend fun deleteFinished(): Int
}

/** Aggregated token usage per model within a period. */
data class UsageByModel(
    val model: String,
    val calls: Int,
    val inputTokens: Long,
    val cacheWriteTokens: Long,
    val cacheReadTokens: Long,
    val outputTokens: Long,
)

@Dao
interface ApiUsageDao {
    @Insert
    suspend fun insert(usage: ApiUsage): Long

    @Query(
        "SELECT model, COUNT(*) AS calls, SUM(inputTokens) AS inputTokens, " +
                "SUM(cacheWriteTokens) AS cacheWriteTokens, SUM(cacheReadTokens) AS cacheReadTokens, " +
                "SUM(outputTokens) AS outputTokens FROM api_usage WHERE createdAt >= :from " +
                "GROUP BY model ORDER BY outputTokens DESC",
    )
    fun usageByModelSince(from: Long): Flow<List<UsageByModel>>
}

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM chat_messages WHERE captureId = :captureId LIMIT 1")
    suspend fun byCaptureId(captureId: Long): ChatMessage?

    /** Rewrites the response snapshot — used to record an adopted research result. */
    @Query("UPDATE chat_messages SET summaryJson = :json WHERE id = :id")
    suspend fun updateSummary(id: Long, json: String)

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    fun allOrdered(): Flow<List<ChatMessage>>
}
