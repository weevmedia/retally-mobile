/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.budget

import kotlinx.datetime.number
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import com.retally.shared.db.ReTallyDatabase
import kotlin.time.Clock
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * Monthly budgets in the shared `retally.db` (`budget` table), plus what each one tracks
 * ([BudgetScope]: all spending or chosen categories, kept in Settings).
 *
 * One row per calendar month, keyed `"yyyy-MM"` (e.g. `"2026-10"`). A new month starts
 * without a row — the user sets it from the Budget screen — so budgets effectively
 * "reset on the 1st" while every past month stays on disk for spending-habit analysis
 * and savings predictions.
 */
class BudgetRepository(
    private val database: ReTallyDatabase,
    private val clock: Clock = Clock.System,
    /** Holds each month's [BudgetScope] (no schema change needed). Null = always the default. */
    private val settings: Settings? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Bumped on every save so observers (the Home budget card) re-read. */
    private val changes = MutableStateFlow(0)

    /** [month]'s budget, re-emitted whenever a budget is saved; null while none is set. */
    fun observeBudget(month: String): Flow<Double?> = changes.map { getBudget(month) }

    /** This month's budget amount, or null when the user hasn't set one yet. */
    fun getBudget(month: String): Double? =
        database.budgetQueries.selectByMonth(month).executeAsOneOrNull()?.amount

    /** Creates or replaces this month's budget. */
    fun setBudget(month: String, amount: Double) {
        database.budgetQueries.insertOrReplace(
            month = month,
            amount = amount,
            createdAt = clock.now().toEpochMilliseconds(),
        )
        changes.value++
    }

    /**
     * What [month]'s budget tracks. A month that never had one picks up the last scope the
     * user chose, so the choice carries into the new month with the budget reset.
     */
    fun getScope(month: String): BudgetScope {
        val store = settings ?: return BudgetScope.Default
        val raw = store.getStringOrNull(scopeKey(month)) ?: store.getStringOrNull(KEY_LAST_SCOPE)
        val saved = raw?.let { runCatching { json.decodeFromString(BudgetScope.serializer(), it) }.getOrNull() }
            ?: return BudgetScope.Default
        // Only "All spending" and "Grocery only" are offered: older multi-category picks read as grocery.
        return if (saved.allSpending) saved else BudgetScope.Default
    }

    /** Saves what [month]'s budget tracks (and remembers it for next month). */
    fun setScope(month: String, scope: BudgetScope) {
        val store = settings ?: return
        val raw = json.encodeToString(BudgetScope.serializer(), scope)
        store.putString(scopeKey(month), raw)
        store.putString(KEY_LAST_SCOPE, raw)
        changes.value++
    }

    /** [month]'s scope, re-emitted whenever a budget or scope is saved. */
    fun observeScope(month: String): Flow<BudgetScope> = changes.map { getScope(month) }

    private fun scopeKey(month: String) = "$KEY_SCOPE_PREFIX$month"

    private companion object {
        const val KEY_SCOPE_PREFIX = "budget.scope.v1."
        const val KEY_LAST_SCOPE = "budget.scope.v1.last"
    }

    /** Newest first — feeds future AI (habits, savings predictions). */
    fun history(): List<BudgetMonth> =
        database.budgetQueries.selectAll().executeAsList().map {
            BudgetMonth(month = it.month, amount = it.amount)
        }
}

data class BudgetMonth(val month: String, val amount: Double)

/** The budget table key for [date]'s month: "2026-10". */
fun budgetMonthKey(date: LocalDate): String = "${date.year}-${date.month.number.toString().padStart(2, '0')}"

/** How spending is tracking against a month's budget (same rules as the Budget screen). */
enum class BudgetPace { ON_TRACK, WARNING, OVER }

/**
 * Pace at the current burn rate: projected month-end = spent ÷ (day ÷ days in month).
 * Over when already at/over budget or projected > 120%; warning when projected > 100%.
 */
fun budgetPace(spent: Double, budget: Double, dayOfMonth: Int, daysInMonth: Int): BudgetPace {
    if (budget <= 0 || spent >= budget) return BudgetPace.OVER
    val projected = projectedMonthEnd(spent, dayOfMonth, daysInMonth)
    return when {
        projected > budget * 1.2 -> BudgetPace.OVER
        projected > budget -> BudgetPace.WARNING
        else -> BudgetPace.ON_TRACK
    }
}

/** Days in [date]'s month. */
fun daysInMonth(date: LocalDate): Int = when (date.month.number) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if ((date.year % 4 == 0 && date.year % 100 != 0) || date.year % 400 == 0) 29 else 28
}
