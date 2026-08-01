package com.app.ai.planner.export

import com.app.ai.planner.data.PlannerRepository
import org.json.JSONArray
import org.json.JSONObject

/** Exports all data as versioned JSON (basis for a later import). */
object JsonExporter {
    const val VERSION = 1

    suspend fun export(repo: PlannerRepository): String {
        val items = JSONArray()
        repo.itemDao.allIncludingArchived().forEach { item ->
            items.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type.name)
                    .put("title", item.title)
                    .put("notes", item.notes ?: JSONObject.NULL)
                    .put("category", item.category ?: JSONObject.NULL)
                    .put("priority", item.priority.name)
                    .put("dueAt", item.dueAt ?: JSONObject.NULL)
                    .put("listName", item.listName ?: JSONObject.NULL)
                    .put("quantity", item.quantity ?: JSONObject.NULL)
                    .put("tags", JSONArray(item.tags))
                    .put("done", item.done)
                    .put("doneAt", item.doneAt ?: JSONObject.NULL)
                    .put("createdAt", item.createdAt)
                    .put("snoozedUntil", item.snoozedUntil ?: JSONObject.NULL)
                    .put("archivedAt", item.archivedAt ?: JSONObject.NULL)
                    .put("researchSuggested", item.researchSuggested)
                    .put("recurrence", item.recurrence ?: JSONObject.NULL),
            )
        }
        val links = JSONArray()
        repo.linkDao.allOnce().forEach { link ->
            links.put(
                JSONObject()
                    .put("fromId", link.fromId)
                    .put("toId", link.toId)
                    .put("relation", link.relation.name)
                    .put("reason", link.reason ?: JSONObject.NULL)
                    .put("createdBy", link.createdBy.name)
                    .put("createdAt", link.createdAt),
            )
        }
        val research = JSONArray()
        repo.researchDao.allOnce().forEach { note ->
            research.put(
                JSONObject()
                    .put("itemId", note.itemId)
                    .put("summary", note.summary)
                    .put("sources", JSONArray(note.sourcesJson))
                    .put("createdAt", note.createdAt),
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("items", items)
            .put("links", links)
            .put("researchNotes", research)
            .toString(2)
    }
}
