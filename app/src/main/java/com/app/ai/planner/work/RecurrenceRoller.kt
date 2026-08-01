package com.app.ai.planner.work

import android.content.Context
import com.app.ai.planner.data.ItemType
import com.app.ai.planner.data.PlannerRepository
import com.app.ai.planner.data.Recurrence
import com.app.ai.planner.reminders.ReminderScheduler
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Rolls recurring entries forward: once an occurrence has passed (appointment) or is
 * completed (task with a due date), it is closed and a new entry with the next
 * occurrence is created. This keeps every instance available for the review.
 * Runs at app start and daily in the BriefingWorker.
 */
object RecurrenceRoller {

    suspend fun roll(context: Context, repo: PlannerRepository) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()

        for (item in repo.itemDao.allOnce()) {
            val rule = item.recurrence ?: continue
            val dueAt = item.dueAt ?: continue
            val passed = dueAt < nowMs
            val shouldRoll = when (item.type) {
                ItemType.APPOINTMENT -> passed
                else -> item.done || passed
            }
            if (!shouldRoll) continue

            // Find the next occurrence in the future (also catches up after longer gaps,
            // but always creates only ONE follow-up entry).
            var next = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueAt), zone)
            var guard = 0
            while (!next.isAfter(now) && guard < 1000) {
                next = Recurrence.next(rule, next) ?: break
                guard++
            }
            if (!next.isAfter(now)) continue

            // Close the old occurrence (unless already done) and cut the series
            // so it is not rolled again.
            repo.itemDao.update(
                item.copy(
                    done = true,
                    doneAt = item.doneAt ?: dueAt,
                    recurrence = null,
                ),
            )
            val nextItem = item.copy(
                id = 0,
                dueAt = next.atZone(zone).toInstant().toEpochMilli(),
                done = false,
                doneAt = null,
                createdAt = nowMs,
                // No sourceCaptureId: undoing the original capture must not hit follow-up occurrences.
                sourceCaptureId = null,
                snoozedUntil = null,
                researchSuggested = false,
            )
            val newId = repo.itemDao.insert(nextItem)
            if (nextItem.type == ItemType.APPOINTMENT) {
                ReminderScheduler.schedule(context, nextItem.copy(id = newId))
            }
        }
    }
}
