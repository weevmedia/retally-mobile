package com.retally.shared.domain.budget

import com.retally.shared.db.ReTallyDatabase
import kotlin.time.Clock

/**
 * Monthly grocery budgets in the shared `retally.db` (`budget` table).
 *
 * One row per calendar month, keyed `"yyyy-MM"` (e.g. `"2026-10"`). A new month starts
 * without a row — the user sets it from the Budget screen — so budgets effectively
 * "reset on the 1st" while every past month stays on disk for spending-habit analysis
 * and savings predictions.
 */
class BudgetRepository(
    private val database: ReTallyDatabase,
    private val clock: Clock = Clock.System,
) {
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
    }

    /** Newest first — feeds future AI (habits, savings predictions). */
    fun history(): List<BudgetMonth> =
        database.budgetQueries.selectAll().executeAsList().map {
            BudgetMonth(month = it.month, amount = it.amount)
        }
}

data class BudgetMonth(val month: String, val amount: Double)
