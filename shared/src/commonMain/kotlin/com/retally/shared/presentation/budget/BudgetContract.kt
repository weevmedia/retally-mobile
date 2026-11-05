/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.budget

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.budget.BudgetRepository
import com.retally.shared.domain.budget.BudgetScope
import com.retally.shared.domain.category.CategorySource
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Monthly grocery budget, opened by tapping the Spent tile on Home.
 *
 * The budget lives in the shared `budget` table keyed `"yyyy-MM"`: a new month starts
 * with no row (the user sets it), so budgets reset on the 1st while history stays for
 * future spending-habit analysis and savings predictions.
 *
 * The 3-color bar is pace-based, not just % spent: `projected = spent / timeProgress`
 * estimates the month-end total at the current burn rate.
 *  - green:  projected <= budget            (on track)
 *  - orange: budget < projected <= 1.2×budget (warning)
 *  - red:    projected > 1.2×budget, or already spent >= budget (over)
 */
interface BudgetContract {

    enum class ProgressColor { GREEN, ORANGE, RED }

    data class State(
        val isLoading: Boolean = true,
        /** "2026-10" — the budget table key. */
        val monthKey: String = "",
        /** "October 2026" — the header label. */
        val monthLabel: String = "",
        /** "October" — for the "October Budget" title. */
        val monthName: String = "",
        /** "November" — for the "Resets Nov 1" label. */
        val nextMonthLabel: String = "",
        /** Null until the user sets this month's budget. */
        val budgetAmount: Double? = null,
        /** Text-field draft while editing. */
        val draftAmount: String = EMPTY_AMOUNT,
        val isEditing: Boolean = false,
        val spentThisMonth: Double = 0.0,
        val dayOfMonth: Int = 1,
        val daysInMonth: Int = 30,
        /** Last 12 calendar months, oldest first, ending with this month — the history chart. */
        val history: List<MonthSpend> = emptyList(),
        /** What this month's budget tracks: all spending or the chosen categories. */
        val scope: BudgetScope = BudgetScope.Default,
        /** Categories offered as chips (everything but "Uncategorized"). */
        val categories: List<ItemCategory> = emptyList(),
        /** This month's items not categorised yet — shown as a hint under a category budget. */
        val uncategorizedCount: Int = 0,
    ) : com.retally.shared.core.State {
        /** Average monthly spend over the months in [history] that have receipts. */
        val historyAverage: Double?
            get() = history.filter { it.total > 0 }.takeIf { it.isNotEmpty() }?.map { it.total }?.average()

        val daysLeft: Int get() = (daysInMonth - dayOfMonth).coerceAtLeast(0)
        val remaining: Double? get() = budgetAmount?.minus(spentThisMonth)

        /** Month-end estimate at the current burn rate; null without a budget. */
        val projected: Double?
            get() {
                val budget = budgetAmount ?: return null
                if (budget <= 0) return null
                val timeProgress = dayOfMonth.toDouble() / daysInMonth.toDouble()
                if (timeProgress <= 0) return spentThisMonth
                return spentThisMonth / timeProgress
            }

        val progressColor: ProgressColor?
            get() {
                val budget = budgetAmount ?: return null
                if (budget <= 0) return null
                val proj = projected ?: return null
                return when {
                    spentThisMonth >= budget -> ProgressColor.RED
                    proj > budget * 1.2 -> ProgressColor.RED
                    proj > budget -> ProgressColor.ORANGE
                    else -> ProgressColor.GREEN
                }
            }

        /** 0..1 fill of the bar against the budget. */
        val progressFraction: Float
            get() {
                val budget = budgetAmount ?: return 0f
                if (budget <= 0) return 0f
                return (spentThisMonth / budget).toFloat().coerceIn(0f, 1f)
            }

        /** Save is enabled only when the draft is a valid amount different from the saved budget. */
        val canSave: Boolean
            get() {
                val draft = draftAmount.trim().toDoubleOrNull() ?: return false
                if (draft <= 0) return false
                return draft != budgetAmount
            }
    }

    /** One month in the history chart. [budget] is that month's saved budget, if any. */
    data class MonthSpend(
        /** "2026-10". */
        val key: String,
        /** "Oct". */
        val shortLabel: String,
        /** "October 2026". */
        val label: String,
        val total: Double,
        val budget: Double?,
        val isCurrent: Boolean,
    )

    sealed class Action : com.retally.shared.core.Action {
        data class AmountChanged(val text: String) : Action()
        data object EditClicked : Action()
        data object SaveClicked : Action()
        data object EditCancelled : Action()
        data object CloseClicked : Action()
        /** Track every dollar on the receipts. */
        data object TrackAllSpending : Action()
        /** Track grocery items only. */
        data object TrackCategories : Action()
        /** Add/remove a category; the last one can't be removed. */
        data class CategoryToggled(val categoryId: String) : Action()
    }

    sealed class SideEffect : Effect {
        data object Closed : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val budgets: BudgetRepository,
        private val receipts: ReceiptRepository,
        private val categories: CategorySource? = null,
        private val clock: Clock = Clock.System,
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        override fun initState() = State()

        /** Every receipt on the device; totals are re-derived when it or the scope changes. */
        private var allReceipts: List<Receipt> = emptyList()
        private lateinit var today: LocalDate

        init {
            val zone = TimeZone.currentSystemDefault()
            today = clock.now().toLocalDateTime(zone).date
            val monthKey = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}"
            val monthName = today.month.name.lowercase().replaceFirstChar { it.uppercase() }
            val monthLabel = "$monthName ${today.year}"
            val nextMonth = if (today.monthNumber == 12) 1 else today.monthNumber + 1
            val nextMonthName = when (nextMonth) {
                1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
                7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
            }
            // Days in month (manual — avoids kotlinx-datetime API uncertainty).
            val daysInMonth = when (today.monthNumber) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if ((today.year % 4 == 0 && today.year % 100 != 0) || today.year % 400 == 0) 29 else 28
                else -> 30
            }
            currentState = currentState.copy(
                monthKey = monthKey,
                monthLabel = monthLabel,
                monthName = monthName,
                nextMonthLabel = nextMonthName,
                dayOfMonth = today.day,
                daysInMonth = daysInMonth,
                scope = budgets.getScope(monthKey),
            )
            categories?.categories
                ?.onEach { list ->
                    updateStateSafely { it.copy(categories = list.filter { c -> c.id != ItemCategory.UNCATEGORIZED_ID }) }
                }
                ?.launchIn(this)
            launch {
                val amount = budgets.getBudget(monthKey)
                updateStateSafely {
                    it.copy(
                        isLoading = false,
                        budgetAmount = amount,
                        draftAmount = amount?.let { formatWhole(it) } ?: EMPTY_AMOUNT,
                    )
                }
            }
            receipts.observeReceipts()
                .onEach { all ->
                    allReceipts = all
                    recompute()
                }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                is Action.AmountChanged -> currentState = currentState.copy(draftAmount = centsEntry(action.text))
                Action.EditClicked -> currentState = currentState.copy(
                    isEditing = true,
                    draftAmount = currentState.budgetAmount?.let { formatWhole(it) } ?: EMPTY_AMOUNT,
                )
                Action.EditCancelled -> currentState = currentState.copy(isEditing = false)
                Action.SaveClicked -> save()
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
                Action.TrackAllSpending -> changeScope(currentState.scope.copy(allSpending = true))
                Action.TrackCategories -> changeScope(BudgetScope.Default) // grocery only
                is Action.CategoryToggled -> {
                    val ids = currentState.scope.categoryIds
                    val next = if (action.categoryId in ids) ids - action.categoryId else ids + action.categoryId
                    if (next.isNotEmpty()) changeScope(currentState.scope.copy(allSpending = false, categoryIds = next))
                }
            }
        }

        private fun changeScope(scope: BudgetScope) {
            if (scope == currentState.scope) return
            budgets.setScope(currentState.monthKey, scope)
            currentState = currentState.copy(scope = scope)
            launch { recompute() }
        }

        /** Spent this month, the history chart and the uncategorised hint, for the current scope. */
        private suspend fun recompute() {
            val scope = currentState.scope
            val zone = TimeZone.currentSystemDefault()
            val thisMonth = allReceipts.filter {
                val d = it.purchaseDate.toLocalDateTime(zone).date
                d.year == today.year && d.monthNumber == today.monthNumber
            }
            val history = lastTwelveMonths(allReceipts, today)
            updateStateSafely {
                it.copy(
                    spentThisMonth = scope.spentIn(thisMonth),
                    uncategorizedCount = scope.uncategorizedIn(thisMonth),
                    history = history,
                )
            }
        }

        private fun save() {
            val amount = currentState.draftAmount.trim().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.BUDGET_INVALID)))
                return
            }
            val key = currentState.monthKey
            launch {
                budgets.setBudget(key, amount)
                updateStateSafely {
                    it.copy(
                        budgetAmount = amount,
                        isEditing = false,
                        history = it.history.map { m -> if (m.key == key) m.copy(budget = amount) else m },
                    )
                }
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.BUDGET_SAVED)))
            }
        }

        /**
         * Spend per calendar month for the 12 months ending with [today]'s, from the receipts
         * stored on this device, each month counted by its own budget scope (so changing this
         * month's choice doesn't rewrite the past), plus each month's budget.
         */
        private fun lastTwelveMonths(all: List<Receipt>, today: LocalDate): List<MonthSpend> {
            val zone = TimeZone.currentSystemDefault()
            val totals = all.groupBy {
                val d = it.purchaseDate.toLocalDateTime(zone).date
                d.year * 12 + (d.monthNumber - 1)
            }
            val budgetByMonth = runCatching { budgets.history() }.getOrDefault(emptyList()).associate { it.month to it.amount }
            val currentIndex = today.year * 12 + (today.monthNumber - 1)
            return (11 downTo 0).map { back ->
                val index = currentIndex - back
                val year = index / 12
                val month = index % 12 + 1
                val key = "$year-${month.toString().padStart(2, '0')}"
                val name = MONTH_NAMES[month - 1]
                MonthSpend(
                    key = key,
                    shortLabel = name.take(3),
                    label = "$name $year",
                    total = totals[index]?.let { list -> budgets.getScope(key).spentIn(list) } ?: 0.0,
                    budget = budgetByMonth[key],
                    isCurrent = back == 0,
                )
            }
        }

        /** "500.00" — always two decimals for the amount field. */
        private fun formatWhole(amount: Double): String =
            formatCents(kotlin.math.round(amount * 100).toLong())
    }
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** Shown when no amount has been typed yet. */
const val EMPTY_AMOUNT = "00.00"

/** Max digits typed into the budget field (999,999.99). */
private const val MAX_AMOUNT_DIGITS = 8

/**
 * Cash-register style entry: digits fill in from the right, so typing 1, 2, 3, 4
 * shows 00.01 -> 00.12 -> 01.23 -> 12.34, and backspace pulls them back out.
 * Anything that isn't a digit (the decimal point, a pasted "$", commas) is ignored.
 */
fun centsEntry(text: String): String {
    val digits = text.filter { it.isDigit() }.trimStart('0').take(MAX_AMOUNT_DIGITS)
    return formatCents(digits.toLongOrNull() ?: 0L)
}

/** 1234 -> "12.34", 5 -> "00.05" (dollars padded to two digits). */
fun formatCents(cents: Long): String {
    val dollars = (cents / 100).toString().padStart(2, '0')
    val remainder = (cents % 100).toString().padStart(2, '0')
    return "$dollars.$remainder"
}
