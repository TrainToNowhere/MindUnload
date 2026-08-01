package com.app.mindunload.work

import android.content.Context
import com.app.mindunload.R
import com.app.mindunload.ai.SettingsStore
import com.app.mindunload.ai.WeatherService
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.PlannerRepository
import java.time.LocalDate
import java.time.ZoneId

/** Everything the morning briefing looks at. */
data class BriefingInput(
    val appointmentsToday: List<PlannerItem>,
    val dueSoonTasks: List<PlannerItem>,
    val overdueTasks: List<PlannerItem>,
    val weather: String?,
    val backlogSuggestions: List<PlannerItem>,
    val shoppingLists: Map<String, Int>,
    /** Open tasks without a date — otherwise invisible to the briefing until they age into the backlog. */
    val openTasks: List<PlannerItem>,
    val ideas: List<PlannerItem>,
    val goals: List<PlannerItem>,
)

/** From this age on, undated entries count as "left lying around" (same as resurfacing). */
private const val BACKLOG_AGE_MS = 21L * 24 * 60 * 60 * 1000

/** Caps per section: the briefing is meant to stay a summary, and the prompt to stay cheap. */
private const val MAX_OPEN_TASKS = 10
private const val MAX_IDEAS = 5
private const val MAX_GOALS = 5

/**
 * Collects the briefing input from the database. Shared by the [BriefingWorker] and the manual
 * "generate now" on the Today screen so both produce the same briefing.
 */
suspend fun collectBriefingInput(
    context: Context,
    repo: PlannerRepository,
    settings: SettingsStore,
    now: Long = System.currentTimeMillis(),
): BriefingInput {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val allItems = repo.itemDao.allOnce()
    // "Later" on the Today screen means exactly that — not "remind me tomorrow morning".
    val open = allItems.filter { !it.done && (it.snoozedUntil == null || it.snoozedUntil < now) }

    val appointmentsToday = open.filter {
        it.type == ItemType.APPOINTMENT && it.dueAt != null && it.dueAt in startOfDay until endOfDay
    }
    val tasks = open.filter { it.type == ItemType.TASK }
    val overdueTasks = tasks.filter { it.dueAt != null && it.dueAt < now }
    val dueSoonTasks = tasks.filter {
        it.dueAt != null && it.dueAt in now until endOfDay + 2 * 86_400_000L && it !in overdueTasks
    }

    val backlogSuggestions = open
        .filter {
            it.dueAt == null && it.createdAt < now - BACKLOG_AGE_MS &&
                    it.type in setOf(ItemType.TASK, ItemType.IDEA, ItemType.GOAL)
        }
        .sortedBy { it.createdAt }
        .take(3)

    // The backlog entries already have their own, stronger role in the prompt — listing them
    // again below would just make the model repeat itself.
    fun openOfType(type: ItemType, limit: Int) = open
        .filter { it.type == type && it !in backlogSuggestions }
        .sortedBy { it.createdAt }
        .take(limit)

    val lat = settings.weatherLatitude
    val lon = settings.weatherLongitude

    return BriefingInput(
        appointmentsToday = appointmentsToday,
        dueSoonTasks = dueSoonTasks,
        overdueTasks = overdueTasks,
        weather = if (lat != null && lon != null) {
            WeatherService.fetchTodaySummary(lat, lon)
        } else {
            null
        },
        backlogSuggestions = backlogSuggestions,
        shoppingLists = open
            .filter { it.type == ItemType.SHOPPING_ITEM }
            .groupingBy { it.listName ?: context.getString(R.string.shopping_default_list) }
            .eachCount(),
        openTasks = tasks
            .filter { it.dueAt == null && it !in backlogSuggestions }
            .sortedBy { it.createdAt }
            .take(MAX_OPEN_TASKS),
        ideas = openOfType(ItemType.IDEA, MAX_IDEAS),
        goals = openOfType(ItemType.GOAL, MAX_GOALS),
    )
}
