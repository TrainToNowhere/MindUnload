package com.app.mindunload

import com.app.mindunload.data.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The only real calendar arithmetic in the app. Its mistakes surface weeks later as a
 * single appointment on the wrong day, where nobody recognizes them as a bug — so the
 * awkward cases (month ends, a 5th weekday that does not exist) are pinned down here.
 */
class RecurrenceTest {

    @Test
    fun `interval defaults to one and is read from the rule`() {
        val from = LocalDateTime.of(2026, 3, 10, 9, 0)
        assertEquals(LocalDateTime.of(2026, 3, 11, 9, 0), Recurrence.next("daily", from))
        assertEquals(LocalDateTime.of(2026, 3, 13, 9, 0), Recurrence.next("daily:3", from))
        assertEquals(LocalDateTime.of(2026, 3, 24, 9, 0), Recurrence.next("weekly:2", from))
        assertEquals(LocalDateTime.of(2026, 5, 10, 9, 0), Recurrence.next("monthly:2", from))
        assertEquals(LocalDateTime.of(2027, 3, 10, 9, 0), Recurrence.next("yearly", from))
    }

    @Test
    fun `weekly keeps the weekday and the time of day`() {
        val from = LocalDateTime.of(2026, 3, 10, 17, 45)
        val next = Recurrence.next("weekly", from)!!
        assertEquals(from.dayOfWeek, next.dayOfWeek)
        assertEquals(from.toLocalTime(), next.toLocalTime())
        assertEquals(LocalDateTime.of(2026, 3, 17, 17, 45), next)
    }

    @Test
    fun `monthly clamps to the end of a shorter month`() {
        // 31 January + 1 month has no 31st to land on.
        assertEquals(
            LocalDateTime.of(2026, 2, 28, 8, 0),
            Recurrence.next("monthly", LocalDateTime.of(2026, 1, 31, 8, 0)),
        )
        // 2028 is a leap year, so the same rule reaches the 29th there.
        assertEquals(
            LocalDateTime.of(2028, 2, 29, 8, 0),
            Recurrence.next("monthly", LocalDateTime.of(2028, 1, 31, 8, 0)),
        )
        // A clamped occurrence does not pull later ones back to the 28th: rolling on
        // from the clamped date is what the roller does, and it stays clamped.
        assertEquals(
            LocalDateTime.of(2026, 3, 28, 8, 0),
            Recurrence.next("monthly", LocalDateTime.of(2026, 2, 28, 8, 0)),
        )
    }

    @Test
    fun `yearly on a leap day falls back to the 28th`() {
        assertEquals(
            LocalDateTime.of(2029, 2, 28, 12, 0),
            Recurrence.next("yearly", LocalDateTime.of(2028, 2, 29, 12, 0)),
        )
    }

    @Test
    fun `monthly_weekday keeps the ordinal weekday`() {
        // 2026-03-13 is the second Friday of March.
        val secondFriday = LocalDateTime.of(2026, 3, 13, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 4, 10, 10, 0),
            Recurrence.next("monthly_weekday", secondFriday),
        )
        // First Monday of March 2026 → first Monday of May 2026 with interval 2.
        val firstMonday = LocalDateTime.of(2026, 3, 2, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 5, 4, 10, 0),
            Recurrence.next("monthly_weekday:2", firstMonday),
        )
    }

    @Test
    fun `a fifth weekday falls back to the last one in the target month`() {
        // 2026-01-30 is the fifth Friday of January; February 2026 has only four.
        assertEquals(
            LocalDateTime.of(2026, 2, 27, 10, 0),
            Recurrence.next("monthly_weekday", LocalDateTime.of(2026, 1, 30, 10, 0)),
        )
    }

    @Test
    fun `rules are case and whitespace tolerant`() {
        val from = LocalDateTime.of(2026, 3, 10, 9, 0)
        assertEquals(LocalDateTime.of(2026, 3, 11, 9, 0), Recurrence.next("  DAILY  ", from))
    }

    @Test
    fun `a malformed interval falls back to one instead of standing still`() {
        val from = LocalDateTime.of(2026, 3, 10, 9, 0)
        assertEquals(LocalDateTime.of(2026, 3, 11, 9, 0), Recurrence.next("daily:x", from))
        // 0 or a negative interval would otherwise return the same date forever.
        assertEquals(LocalDateTime.of(2026, 3, 11, 9, 0), Recurrence.next("daily:0", from))
        assertEquals(LocalDateTime.of(2026, 3, 11, 9, 0), Recurrence.next("daily:-2", from))
    }

    @Test
    fun `unknown rules yield null and are rejected by isValid`() {
        assertNull(Recurrence.next("fortnightly", LocalDateTime.of(2026, 3, 10, 9, 0)))
        assertNull(Recurrence.next("", LocalDateTime.of(2026, 3, 10, 9, 0)))
        assertFalse(Recurrence.isValid("fortnightly"))
        assertFalse(Recurrence.isValid(null))
        assertTrue(Recurrence.isValid("monthly_weekday:3"))
    }
}
