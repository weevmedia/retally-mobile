package com.retally.shared.presentation.receipts

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.ui.format.formatMonthYearShort
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Receipts tab: every receipt, newest first, filtered by month and paged in as the list
 * scrolls. Home shows only the newest two and hands over here through "See all".
 *
 * Receipts arrive from [ReceiptRepository] as one local flow, so paging is a window over the
 * in-memory list rather than a backend query — [State.visibleCount] grows by
 * [State.PAGE_SIZE] each time the list reaches the end.
 */
interface ReceiptsContract {

    data class State(
        val receipts: List<Receipt> = emptyList(),
        val isLoading: Boolean = true,
        /** Month label from [months], or null for "All". */
        val selectedMonth: String? = null,
        val visibleCount: Int = PAGE_SIZE,
        /** Receipt awaiting the user's delete confirmation, if any. */
        val pendingDelete: Receipt? = null,
    ) : com.retally.shared.core.State {

        /** Newest first; every other derived list builds on this order. */
        val ordered: List<Receipt> get() = receipts.sortedByDescending { it.purchaseDate }

        /**
         * Months that actually have receipts, newest first — the filter chips.
         *
         * The formatted label doubles as the key: "Sep 2026" is unique per month and year, so
         * there is no separate id to keep in sync with what the chip shows.
         */
        val months: List<String>
            get() = ordered.map { formatMonthYearShort(it.purchaseDate) }.distinct()

        /** [ordered] with the month filter applied. */
        val filtered: List<Receipt>
            get() = selectedMonth
                ?.let { month -> ordered.filter { formatMonthYearShort(it.purchaseDate) == month } }
                ?: ordered

        /** The page of [filtered] currently rendered. */
        val visible: List<Receipt> get() = filtered.take(visibleCount)

        val canLoadMore: Boolean get() = visibleCount < filtered.size

        val isEmpty: Boolean get() = !isLoading && receipts.isEmpty()

        /** True when the filter is on and hides everything — a different empty message. */
        val isFilteredEmpty: Boolean get() = !isLoading && receipts.isNotEmpty() && filtered.isEmpty()

        companion object {
            /** Rows added each time the list reaches the end. */
            const val PAGE_SIZE = 20
        }
    }

    sealed class Action : com.retally.shared.core.Action {
        /** A filter chip; null is "All". Resets paging so the new filter starts at page one. */
        data class MonthSelected(val month: String?) : Action()
        /** The list reached the end of what is rendered. Ignored when nothing is left. */
        data object LoadMore : Action()
        data class ReceiptClicked(val id: String) : Action()
        data class DeleteRequested(val id: String) : Action()
        data object DeleteConfirmed : Action()
        data object DeleteCancelled : Action()
    }

    sealed class SideEffect : Effect {
        data class OpenReceipt(val id: String) : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val repository: ReceiptRepository,
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        override fun initState() = State()

        init {
            repository.observeReceipts()
                .onEach { receipts ->
                    updateStateSafely { it.copy(receipts = receipts, isLoading = false) }
                }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                is Action.MonthSelected -> currentState = currentState.copy(
                    // Toggle: tapping the active chip clears the filter.
                    selectedMonth = action.month.takeIf { it != currentState.selectedMonth },
                    visibleCount = State.PAGE_SIZE,
                )

                Action.LoadMore -> if (currentState.canLoadMore) {
                    currentState = currentState.copy(visibleCount = currentState.visibleCount + State.PAGE_SIZE)
                }

                is Action.ReceiptClicked -> sideEffect(SideEffect.OpenReceipt(action.id))

                is Action.DeleteRequested -> currentState = currentState.copy(
                    pendingDelete = currentState.receipts.firstOrNull { it.id == action.id },
                )

                Action.DeleteCancelled -> currentState = currentState.copy(pendingDelete = null)

                Action.DeleteConfirmed -> {
                    val receipt = currentState.pendingDelete ?: return
                    currentState = currentState.copy(pendingDelete = null)
                    launch {
                        repository.delete(receipt.id)
                        sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.HOME_MSG_DELETED, receipt.storeName)))
                    }
                }
            }
        }
    }
}
