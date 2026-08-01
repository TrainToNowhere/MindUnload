package com.app.mindunload.export

import com.app.mindunload.data.ItemLink
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.LinkOrigin
import com.app.mindunload.data.LinkRelation
import com.app.mindunload.data.PlannerItem
import com.app.mindunload.data.PlannerRepository
import com.app.mindunload.data.Priority
import com.app.mindunload.data.ResearchNote
import org.json.JSONArray
import org.json.JSONObject

/**
 * Counterpart to [JsonExporter]: reads an export JSON and adds everything as new
 * entries (ids are reassigned and remapped in links/research notes).
 * Deliberately additive — existing data stays untouched.
 */
object JsonImporter {

    data class Result(val items: Int, val links: Int, val researchNotes: Int)

    suspend fun import(repo: PlannerRepository, json: String): Result {
        val root = JSONObject(json)
        val idMap = mutableMapOf<Long, Long>()

        var itemCount = 0
        root.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type =
                    runCatching { ItemType.valueOf(o.getString("type")) }.getOrNull() ?: continue
                val title = o.optString("title")
                if (title.isBlank()) continue
                val tags = o.optJSONArray("tags")
                    ?.let { t -> List(t.length()) { t.getString(it) } } ?: emptyList()
                val newId = repo.itemDao.insert(
                    PlannerItem(
                        type = type,
                        title = title,
                        notes = o.optStringOrNull("notes"),
                        category = o.optStringOrNull("category"),
                        priority = runCatching { Priority.valueOf(o.optString("priority")) }
                            .getOrDefault(Priority.NONE),
                        dueAt = o.optLongOrNull("dueAt"),
                        listName = o.optStringOrNull("listName"),
                        quantity = o.optStringOrNull("quantity"),
                        tags = tags,
                        done = o.optBoolean("done"),
                        doneAt = o.optLongOrNull("doneAt"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        snoozedUntil = o.optLongOrNull("snoozedUntil"),
                        archivedAt = o.optLongOrNull("archivedAt"),
                        researchSuggested = o.optBoolean("researchSuggested"),
                        recurrence = o.optStringOrNull("recurrence"),
                    ),
                )
                o.optLongOrNull("id")?.let { idMap[it] = newId }
                itemCount++
            }
        }

        var linkCount = 0
        root.optJSONArray("links")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fromId = idMap[o.optLong("fromId")] ?: continue
                val toId = idMap[o.optLong("toId")] ?: continue
                repo.linkDao.insert(
                    ItemLink(
                        fromId = fromId,
                        toId = toId,
                        relation = runCatching { LinkRelation.valueOf(o.optString("relation")) }
                            .getOrDefault(LinkRelation.RELATED),
                        reason = o.optStringOrNull("reason"),
                        createdBy = runCatching { LinkOrigin.valueOf(o.optString("createdBy")) }
                            .getOrDefault(LinkOrigin.AI),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    ),
                )
                linkCount++
            }
        }

        var noteCount = 0
        root.optJSONArray("researchNotes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val itemId = idMap[o.optLong("itemId")] ?: continue
                repo.researchDao.insert(
                    ResearchNote(
                        itemId = itemId,
                        summary = o.optString("summary"),
                        sourcesJson = (o.optJSONArray("sources") ?: JSONArray()).toString(),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    ),
                )
                noteCount++
            }
        }

        return Result(itemCount, linkCount, noteCount)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)
}
