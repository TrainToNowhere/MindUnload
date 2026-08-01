package com.app.mindunload.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Compact recurrence rules for recurring appointments/tasks:
 * "daily[:n]", "weekly[:n]" (same weekday), "monthly[:n]" (same day of month,
 * clamped to end of month for 29-31), "monthly_weekday[:n]" (e.g. every 2nd Friday),
 * "yearly". n = interval, default 1.
 */
object Recurrence {

    /** Next occurrence after [from], or null for an unknown rule. */
    fun next(rule: String, from: LocalDateTime): LocalDateTime? {
        val parts = rule.trim().lowercase().split(":")
        val n = parts.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(1) ?: 1L
        return when (parts[0]) {
            "daily" -> from.plusDays(n)
            "weekly" -> from.plusWeeks(n)
            "monthly" -> from.plusMonths(n)
            "monthly_weekday" -> nextNthWeekday(from, n)
            "yearly" -> from.plusYears(n)
            else -> null
        }
    }

    /** Same n-th weekday in the following month (a 5th occurrence falls back to the last). */
    private fun nextNthWeekday(from: LocalDateTime, months: Long): LocalDateTime {
        val weekday: DayOfWeek = from.dayOfWeek
        val ordinal = (from.dayOfMonth - 1) / 7 + 1
        val targetMonth = from.plusMonths(months)
        val candidate = targetMonth.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, weekday))
        // Ordinal 5 does not exist in every month — take the last occurrence then.
        return if (candidate.month != targetMonth.month) {
            targetMonth.with(TemporalAdjusters.lastInMonth(weekday))
        } else {
            candidate
        }
    }

    fun isValid(rule: String?): Boolean =
        rule != null && next(rule, LocalDateTime.now()) != null
}
