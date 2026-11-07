/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.budget

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.time.Instant

/** One budget local notification (daily reminder or month-end wrap-up). */
@Serializable
data class BudgetReminder(
    /** Stable per day ("budget-reminder-2026-10-30"), so re-planning replaces, never duplicates. */
    val id: String,
    val triggerAtEpochMillis: Long,
    val title: String,
    val body: String,
)

/**
 * Platform side of the budget reminders: posts local notifications at given times. Android
 * uses AlarmManager, iOS UNUserNotificationCenter. Nothing runs in the background to decide
 * *whether* to remind: [planBudgetReminders] works that out up front for each remaining day.
 */
interface BudgetReminderScheduler {
    /** Cancels every pending budget reminder, then schedules [reminders] (empty = cancel all). */
    fun replaceAll(reminders: List<BudgetReminder>)
}

/** When the daily reminder fires: late afternoon, after work and before the evening shop. */
val BUDGET_REMINDER_TIME = LocalTime(hour = 17, minute = 30)

/** When the month-end wrap-up fires on the last day: evening, once the day's shopping is done. */
val BUDGET_SUMMARY_TIME = LocalTime(hour = 21, minute = 0)

/** Within this much of the budget counts as "right on budget" rather than under or over. */
private const val ON_BUDGET_TOLERANCE = 0.50

/** Month-end estimate at the current burn rate: spent ÷ (day ÷ days in month). */
fun projectedMonthEnd(spent: Double, dayOfMonth: Int, daysInMonth: Int): Double {
    val timeProgress = dayOfMonth.toDouble() / daysInMonth.coerceAtLeast(1)
    return if (timeProgress <= 0) spent else spent / timeProgress
}

/**
 * The month's budget notifications:
 *  - **Daily reminder**, at most one per day at [time], on every remaining day the budget is
 *    orange ([BudgetPace.WARNING]) or red ([BudgetPace.OVER]). Not on the last day, which gets
 *    the wrap-up instead.
 *  - **Month-end wrap-up** at [summaryTime] on the last day: congratulations when the month
 *    ends at or under budget, otherwise how much over. Skipped when nothing was spent (no
 *    receipts this month, so nothing to report).
 *
 * Spending only changes when a receipt is added in the app, which re-plans, so each day's pace
 * can be worked out now: with the same spend, the projection eases as the month goes on, and a
 * day that turns green simply gets no reminder. Today is included only if [time] is still ahead.
 * A new month has no budget until the user sets one, so nothing is planned past month-end.
 */
fun planBudgetReminders(
    spent: Double,
    budget: Double?,
    now: Instant,
    timeZone: TimeZone,
    strings: StringResolver,
    formatMoney: (Double) -> String,
    time: LocalTime = BUDGET_REMINDER_TIME,
    summaryTime: LocalTime = BUDGET_SUMMARY_TIME,
): List<BudgetReminder> {
    if (budget == null || budget <= 0) return emptyList()
    val nowLocal = now.toLocalDateTime(timeZone)
    val today = nowLocal.date
    val monthDays = daysInMonth(today)
    val lastDay = LocalDate(today.year, today.month, monthDays)
    val first = if (nowLocal.time < time) today else today.plus(1, DateTimeUnit.DAY)
    val daily = generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it < lastDay } // the last day gets the wrap-up instead
        .mapNotNull { day ->
            val pace = budgetPace(spent, budget, day.day, monthDays)
            if (pace == BudgetPace.ON_TRACK) return@mapNotNull null
            val projected = projectedMonthEnd(spent, day.day, monthDays)
            val (title, body) = reminderText(pace, spent, budget, projected, strings, formatMoney)
            BudgetReminder(
                id = budgetReminderId(day),
                triggerAtEpochMillis = LocalDateTime(day, time).toInstant(timeZone).toEpochMilliseconds(),
                title = title,
                body = body,
            )
        }
        .toList()
    val summaryAt = LocalDateTime(lastDay, summaryTime)
    val summary = if (spent > 0 && nowLocal < summaryAt) {
        val (title, body) = summaryText(spent, budget, strings, formatMoney)
        BudgetReminder(
            id = budgetSummaryId(lastDay),
            triggerAtEpochMillis = summaryAt.toInstant(timeZone).toEpochMilliseconds(),
            title = title,
            body = body,
        )
    } else {
        null
    }
    return daily + listOfNotNull(summary)
}

/** Every id a reminder could have from [from] on: lets a platform cancel without listing. */
fun budgetReminderIdsFrom(from: LocalDate, days: Int = 62): List<String> =
    (0 until days).flatMap { offset ->
        val day = from.plus(offset, DateTimeUnit.DAY)
        listOf(budgetReminderId(day), budgetSummaryId(day))
    }

private fun budgetReminderId(day: LocalDate) = "budget-reminder-$day"

private fun budgetSummaryId(day: LocalDate) = "budget-summary-$day"

/** Wrap-up copy: on budget (within 50¢), under (congratulations + what's left), or over. */
private fun summaryText(
    spent: Double,
    budget: Double,
    strings: StringResolver,
    money: (Double) -> String,
): Pair<String, String> = when {
    abs(spent - budget) <= ON_BUDGET_TOLERANCE -> strings.getString(S.BUDGET_SUMMARY_ON_TITLE) to
        strings.getString(S.BUDGET_SUMMARY_ON_BODY, money(spent), money(budget))
    spent < budget -> strings.getString(S.BUDGET_SUMMARY_UNDER_TITLE) to
        strings.getString(S.BUDGET_SUMMARY_UNDER_BODY, money(spent), money(budget), money(budget - spent))
    else -> strings.getString(S.BUDGET_SUMMARY_OVER_TITLE) to
        strings.getString(S.BUDGET_SUMMARY_OVER_BODY, money(spent), money(spent - budget), money(budget))
}

private fun reminderText(
    pace: BudgetPace,
    spent: Double,
    budget: Double,
    projected: Double,
    strings: StringResolver,
    money: (Double) -> String,
): Pair<String, String> = when {
    spent >= budget -> strings.getString(S.BUDGET_REMINDER_OVER_TITLE) to
        strings.getString(S.BUDGET_REMINDER_OVER_BODY, money(spent - budget), money(budget))
    pace == BudgetPace.OVER -> strings.getString(S.BUDGET_REMINDER_OVER_PACE_TITLE) to
        strings.getString(S.BUDGET_REMINDER_PACE_BODY, money(projected), money(budget))
    else -> strings.getString(S.BUDGET_REMINDER_WARNING_TITLE) to
        strings.getString(S.BUDGET_REMINDER_PACE_BODY, money(projected), money(budget))
}
