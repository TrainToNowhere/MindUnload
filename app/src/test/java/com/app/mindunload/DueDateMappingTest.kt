package com.app.mindunload

import com.app.mindunload.ai.ParsedAction
import com.app.mindunload.ai.ParsedItem
import com.app.mindunload.ai.dueAtMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The step from the model's ISO string to the epoch millis stored in the database. The
 * time zone is the trap: the same string is a different instant in Berlin and in Tokyo,
 * and everything downstream (reminders, "today", overdue) reads only the millis.
 */
class DueDateMappingTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun millisOf(local: LocalDateTime, zone: ZoneId) =
        local.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `an ISO local date-time is resolved in the given zone`() {
        val item = ParsedItem().apply { dueAt = "2026-07-07T10:00:00" }
        assertEquals(
            millisOf(LocalDateTime.of(2026, 7, 7, 10, 0), berlin),
            item.dueAtMillis(berlin),
        )
    }

    @Test
    fun `the same string in another zone is another instant`() {
        val item = ParsedItem().apply { dueAt = "2026-07-07T10:00:00" }
        val tokyo = ZoneId.of("Asia/Tokyo")
        // Seven hours apart in July (CEST vs JST) — a wall clock time is not an instant.
        assertEquals(
            7 * 3_600_000L,
            item.dueAtMillis(berlin)!! - item.dueAtMillis(tokyo)!!,
        )
    }

    @Test
    fun `seconds are optional, a bare date is not accepted`() {
        assertEquals(
            millisOf(LocalDateTime.of(2026, 7, 7, 10, 0), berlin),
            ParsedItem().apply { dueAt = "2026-07-07T10:00" }.dueAtMillis(berlin),
        )
        // Documented behaviour, not an oversight: without a time the entry stays undated
        // rather than silently landing at midnight.
        assertNull(ParsedItem().apply { dueAt = "2026-07-07" }.dueAtMillis(berlin))
    }

    @Test
    fun `garbage and null yield null instead of throwing`() {
        assertNull(ParsedItem().apply { dueAt = null }.dueAtMillis(berlin))
        assertNull(ParsedItem().apply { dueAt = "" }.dueAtMillis(berlin))
        assertNull(ParsedItem().apply { dueAt = "morgen früh" }.dueAtMillis(berlin))
        // A trailing zone offset is not a local date-time either.
        assertNull(ParsedItem().apply { dueAt = "2026-07-07T10:00:00Z" }.dueAtMillis(berlin))
    }

    @Test
    fun `actions map exactly like items`() {
        val value = "2026-07-07T10:00:00"
        assertEquals(
            ParsedItem().apply { dueAt = value }.dueAtMillis(berlin),
            ParsedAction().apply { dueAt = value }.dueAtMillis(berlin),
        )
        assertNull(ParsedAction().apply { dueAt = "later" }.dueAtMillis(berlin))
    }

    @Test
    fun `a time inside the DST gap still resolves`() {
        // 2026-03-29 02:30 does not exist in Berlin — java.time shifts it forward rather
        // than failing, and the entry must not be dropped because of it.
        val item = ParsedItem().apply { dueAt = "2026-03-29T02:30:00" }
        assertEquals(
            millisOf(LocalDateTime.of(2026, 3, 29, 3, 30), berlin),
            item.dueAtMillis(berlin),
        )
    }
}
