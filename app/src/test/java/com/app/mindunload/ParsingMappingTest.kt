package com.app.mindunload

import com.app.mindunload.ai.LinkSuggestion
import com.app.mindunload.ai.ParsedAction
import com.app.mindunload.ai.ParsedItem
import com.app.mindunload.ai.dueAtMillis
import com.app.mindunload.ai.resolveLinkRef
import com.app.mindunload.ai.toItemType
import com.app.mindunload.ai.toPriority
import com.app.mindunload.ai.toPriorityOrNull
import com.app.mindunload.ai.toRelation
import com.app.mindunload.ai.toRelationOrDefault
import com.app.mindunload.data.ItemType
import com.app.mindunload.data.LinkRelation
import com.app.mindunload.data.Priority
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingMappingTest {

    @Test
    fun `item type mapping is case insensitive and defaults to task`() {
        assertEquals(ItemType.IDEA, ParsedItem().apply { type = "Idea" }.toItemType())
        assertEquals(
            ItemType.SHOPPING_ITEM,
            ParsedItem().apply { type = "shopping_item" }.toItemType()
        )
        assertEquals(ItemType.NOTE, ParsedItem().apply { type = "note" }.toItemType())
        assertEquals(ItemType.TASK, ParsedItem().apply { type = "unknown" }.toItemType())
    }

    @Test
    fun `nullable priority mapping is null for unknown values`() {
        assertEquals(Priority.HIGH, "high".toPriorityOrNull())
        assertEquals(Priority.NONE, "none".toPriorityOrNull())
        assertNull((null as String?).toPriorityOrNull())
        assertNull("garbage".toPriorityOrNull())
    }

    @Test
    fun `action relation mapping defaults to related`() {
        assertEquals(
            LinkRelation.PART_OF,
            ParsedAction().apply { relation = "part_of" }.toRelationOrDefault()
        )
        assertEquals(
            LinkRelation.RELATED,
            ParsedAction().apply { relation = null }.toRelationOrDefault()
        )
    }

    @Test
    fun `action target and link refs resolve like link suggestions`() {
        val newIds = listOf(201L, 202L)
        val action = ParsedAction().apply { targetRef = "existing:9"; linkTargetRef = "new:1" }
        assertEquals(9L, resolveLinkRef(action.targetRef, newIds))
        assertEquals(202L, resolveLinkRef(action.linkTargetRef!!, newIds))
    }

    @Test
    fun `priority mapping defaults to none`() {
        assertEquals(Priority.HIGH, ParsedItem().apply { priority = "HIGH" }.toPriority())
        assertEquals(Priority.NONE, ParsedItem().apply { priority = "whatever" }.toPriority())
    }

    @Test
    fun `dueAt parses ISO local datetime in the given zone`() {
        val zone = ZoneId.of("Europe/Berlin")
        val item = ParsedItem().apply { dueAt = "2026-07-07T10:00:00" }
        val expected = LocalDateTime.of(2026, 7, 7, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, item.dueAtMillis(zone))
    }

    @Test
    fun `invalid dueAt maps to null instead of crashing`() {
        assertNull(ParsedItem().apply { dueAt = "next tuesday" }.dueAtMillis())
    }

    @Test
    fun `link refs resolve new indices and existing ids`() {
        val newIds = listOf(101L, 102L)
        assertEquals(101L, resolveLinkRef("new:0", newIds))
        assertEquals(102L, resolveLinkRef("new:1", newIds))
        assertEquals(42L, resolveLinkRef("existing:42", newIds))
        assertNull(resolveLinkRef("new:5", newIds))
        assertNull(resolveLinkRef("garbage", newIds))
        assertNull(resolveLinkRef("existing:abc", newIds))
    }

    @Test
    fun `relation mapping defaults to related`() {
        assertEquals(
            LinkRelation.SAME_TOPIC,
            LinkSuggestion().apply { relation = "same_topic" }.toRelation()
        )
        assertEquals(LinkRelation.RELATED, LinkSuggestion().apply { relation = "???" }.toRelation())
    }
}
