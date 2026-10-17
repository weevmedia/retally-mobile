package com.retally.shared.presentation.budget

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.budget.BudgetRepository
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
        val draftAmount: String = "00.00",
        val isEditing: Boolean = false,
        val spentThisMonth: Double = 0.0,
        val dayOfMonth: Int = 1,
        val daysInMonth: Int = 30,
    ) : com.retally.shared.core.State {
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

    sealed class Action : com.retally.shared.core.Action {
        data class AmountChanged(val text: String) : Action()
        data object EditClicked : Action()
        data object SaveClicked : Action()
        data object EditCancelled : Action()
        data object CloseClicked : Action()
    }

    sealed class SideEffect : Effect {
        data object Closed : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val budgets: BudgetRepository,
        private val receipts: ReceiptRepository,
        private val clock: Clock = Clock.System,
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        override fun initState() = State()

        init {
            val zone = TimeZone.currentSystemDefault()
            val today = clock.now().toLocalDateTime(zone).date
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
            )
            launch {
                val amount = budgets.getBudget(monthKey)
                updateStateSafely {
                    it.copy(
                        isLoading = false,
                        budgetAmount = amount,
                        draftAmount = amount?.let { formatWhole(it) } ?: "00.00",
                    )
                }
            }
            receipts.observeReceipts()
                .onEach { all ->
                    updateStateSafely { it.copy(spentThisMonth = monthSpend(all, today.year, today.monthNumber)) }
                }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                is Action.AmountChanged -> currentState = currentState.copy(draftAmount = action.text)
                Action.EditClicked -> currentState = currentState.copy(
                    isEditing = true,
                    draftAmount = currentState.budgetAmount?.let { formatWhole(it) } ?: "",
                )
                Action.EditCancelled -> currentState = currentState.copy(isEditing = false)
                Action.SaveClicked -> save()
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
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
                updateStateSafely { it.copy(budgetAmount = amount, isEditing = false) }
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.BUDGET_SAVED)))
            }
        }

        /** This month's receipt totals — same calendar-month window as Home's Spent tile. */
        private fun monthSpend(all: List<Receipt>, year: Int, monthNumber: Int): Double {
            val zone = TimeZone.currentSystemDefault()
            return all
                .filter {
                    val d = it.purchaseDate.toLocalDateTime(zone).date
                    d.year == year && d.monthNumber == monthNumber
                }
                .sumOf { it.total ?: 0.0 }
        }

        /** "500.00" — always two decimals for the amount field. */
        private fun formatWhole(amount: Double): String {
            val cents = (amount * 100).toLong()
            val dollars = cents / 100
            val remainder = (cents % 100).toString().padStart(2, '0')
            return "$dollars.$remainder"
        }
    }
}
