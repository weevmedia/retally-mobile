/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.budget

import com.retally.shared.core.strings.EnglishStringResolver
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetRemindersTest {

    private val zone = TimeZone.UTC

    private fun plan(spent: Double, budget: Double?, now: LocalDateTime) = planBudgetReminders(
        spent = spent,
        budget = budget,
        now = now.toInstant(zone),
        timeZone = zone,
        strings = EnglishStringResolver,
        formatMoney = { "$" + it.toInt() },
    )

    private fun BudgetReminder.at() =
        kotlin.time.Instant.fromEpochMilliseconds(triggerAtEpochMillis).toLocalDateTime(zone)

    private fun List<BudgetReminder>.daily() = filter { it.id.startsWith("budget-reminder-") }
    private fun List<BudgetReminder>.summary() = singleOrNull { it.id.startsWith("budget-summary-") }

    // ---- daily reminders ----

    @Test
    fun onTrackHasNoDailyReminders() {
        // Day 15 of 30, $400 of $1000: projected $800.
        assertTrue(plan(400.0, 1000.0, LocalDateTime(2026, 11, 15, 9, 0)).daily().isEmpty())
    }

    @Test
    fun noBudgetPlansNothing() {
        assertTrue(plan(900.0, null, LocalDateTime(2026, 11, 15, 9, 0)).isEmpty())
    }

    @Test
    fun overBudgetRemindsEveryDayBeforeTheLastAtTheReminderTime() {
        val daily = plan(1200.0, 1000.0, LocalDateTime(2026, 11, 25, 9, 0)).daily()
        assertEquals((25..29).toList(), daily.map { it.at().date.day }) // the 30th gets the wrap-up
        assertTrue(daily.all { it.at().time == BUDGET_REMINDER_TIME })
        assertEquals("You're over budget", daily.first().title)
    }

    @Test
    fun todayIsSkippedOnceTheReminderTimeHasPassed() {
        assertEquals(26, plan(1200.0, 1000.0, LocalDateTime(2026, 11, 25, 18, 0)).daily().first().at().date.day)
    }

    @Test
    fun warningStopsOnTheDayThePaceTurnsGreen() {
        // Day 10 of 30, $400 of $1000: projected $1200 (orange). With no new receipts the
        // projection eases to ≤ $1000 on day 12, so only days 10 and 11 get a reminder.
        val daily = plan(400.0, 1000.0, LocalDateTime(2026, 11, 10, 9, 0)).daily()
        assertEquals(listOf(10, 11), daily.map { it.at().date.day })
        assertEquals("Budget heads-up", daily.first().title)
    }

    // ---- month-end wrap-up ----

    @Test
    fun wrapUpIsOnTheLastDayAt9pm() {
        val summary = plan(400.0, 1000.0, LocalDateTime(2026, 11, 15, 9, 0)).summary()!!
        assertEquals(LocalDateTime(2026, 11, 30, 21, 0), summary.at())
    }

    @Test
    fun underBudgetIsCongratulated() {
        val summary = plan(400.0, 1000.0, LocalDateTime(2026, 11, 15, 9, 0)).summary()!!
        assertEquals("Congratulations, you stayed under budget!", summary.title)
        assertEquals("You spent $400 of your $1000 budget this month, with $600 to spare.", summary.body)
    }

    @Test
    fun rightOnBudgetIsCongratulated() {
        val summary = plan(1000.3, 1000.0, LocalDateTime(2026, 11, 15, 9, 0)).summary()!!
        assertEquals("Congratulations, right on budget!", summary.title)
    }

    @Test
    fun overBudgetSaysByHowMuch() {
        val summary = plan(1250.0, 1000.0, LocalDateTime(2026, 11, 15, 9, 0)).summary()!!
        assertEquals("Your month in review", summary.title)
        assertEquals("You spent $1250 this month, $250 more than your $1000 budget. A fresh month starts tomorrow.", summary.body)
    }

    @Test
    fun noSpendingMeansNoWrapUp() {
        assertNull(plan(0.0, 1000.0, LocalDateTime(2026, 11, 15, 9, 0)).summary())
    }

    @Test
    fun wrapUpStillPlannedOnTheLastDayBefore9pm() {
        val reminders = plan(1200.0, 1000.0, LocalDateTime(2026, 11, 30, 18, 0))
        assertTrue(reminders.daily().isEmpty())
        assertEquals(30, reminders.summary()!!.at().date.day)
    }

    @Test
    fun nothingPlannedAfterTheWrapUpTimeOrIntoNextMonth() {
        assertTrue(plan(1200.0, 1000.0, LocalDateTime(2026, 11, 30, 21, 30)).isEmpty())
    }

    @Test
    fun projectionIsSpendOverTimeElapsed() {
        assertEquals(1200.0, projectedMonthEnd(400.0, dayOfMonth = 10, daysInMonth = 30), 0.001)
    }
}
