package com.retally.shared.presentation.duplicates

import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.core.Effect
import com.retally.shared.domain.receipt.CrossReceiptDuplicate
import com.retally.shared.domain.receipt.CrossReceiptDuplicateFinder
import com.retally.shared.domain.receipt.DuplicateMatcher
import com.retally.shared.domain.receipt.DuplicateOccurrence
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * "Duplicates found" list: every item repeated across receipts (milk bought Sep 12 and again
 * Sep 15), plus the unresolved in-receipt double charges, each with **Keep both** /
 * **Remove one**. Nothing is deleted without [Action.RemoveOneConfirmed].
 */
interface DuplicatesContract {

    data class State(
        val isLoading: Boolean = true,
        val crossReceipt: List<CrossReceiptDuplicate> = emptyList(),
        /** Receipts that still carry an unresolved in-receipt double charge. */
        val inReceipt: List<Receipt> = emptyList(),
        /** The latest occurrence the shopper asked to remove; UI confirms until null. */
        val pendingRemoval: CrossReceiptDuplicate? = null,
    ) : com.retally.shared.core.State {
        val isEmpty: Boolean get() = !isLoading && crossReceipt.isEmpty() && inReceipt.isEmpty()
        val total: Int get() = crossReceipt.size + inReceipt.sumOf { it.duplicates.size }
    }

    sealed class Action : com.retally.shared.core.Action {
        /** "Keep both" for a cross-receipt repeat: all its lines are marked kept and it leaves the list. */
        data class KeepBoth(val key: String) : Action()
        /** "Remove one": ask before deleting the most recent occurrence. */
        data class RemoveOneRequested(val key: String) : Action()
        data object RemoveOneConfirmed : Action()
        data object RemoveOneCancelled : Action()
        data class ReceiptClicked(val id: String) : Action()
        data object CloseClicked : Action()
    }

    sealed class SideEffect : Effect {
        data class OpenReceipt(val id: String) : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
        data object Closed : SideEffect()
    }

    class Store(
        private val repository: ReceiptRepository,
        private val finder: CrossReceiptDuplicateFinder = CrossReceiptDuplicateFinder(),
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        private var receipts: List<Receipt> = emptyList()

        override fun initState() = State()

        init {
            repository.observeReceipts()
                .onEach { all ->
                    receipts = all
                    updateStateSafely {
                        it.copy(
                            isLoading = false,
                            crossReceipt = finder.find(all),
                            inReceipt = all.filter { r -> r.duplicates.isNotEmpty() }.sortedByDescending { r -> r.purchaseDate },
                        )
                    }
                }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                is Action.KeepBoth -> keepBoth(action.key)
                is Action.RemoveOneRequested -> currentState = currentState.copy(
                    pendingRemoval = currentState.crossReceipt.firstOrNull { it.key == action.key },
                )
                Action.RemoveOneCancelled -> currentState = currentState.copy(pendingRemoval = null)
                Action.RemoveOneConfirmed -> removeOne()
                is Action.ReceiptClicked -> sideEffect(SideEffect.OpenReceipt(action.id))
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
            }
        }

        private fun keepBoth(key: String) {
            val group = currentState.crossReceipt.firstOrNull { it.key == key } ?: return
            launch {
                group.occurrences.groupBy { it.receiptId }.forEach { (receiptId, occs) ->
                    val receipt = receipts.firstOrNull { it.id == receiptId } ?: return@forEach
                    val ids = occs.map { it.item.id }.toSet()
                    repository.upsert(
                        receipt.copy(
                            items = receipt.items.map { if (it.id in ids) it.copy(keptAcrossReceipts = true) else it },
                            syncedAt = null,
                        ),
                    )
                }
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.DUPLICATES_MSG_KEPT_BOTH, group.name)))
            }
        }

        /** Removes the most recent occurrence (the one most likely to be the mistake). */
        private fun removeOne() {
            val group = currentState.pendingRemoval ?: return
            currentState = currentState.copy(pendingRemoval = null)
            val target: DuplicateOccurrence = group.latest
            val receipt = receipts.firstOrNull { it.id == target.receiptId } ?: return
            launch {
                val remaining = receipt.items.filterNot { it.id == target.item.id }
                repository.upsert(receipt.copy(items = DuplicateMatcher.flag(remaining), syncedAt = null))
                sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.DUPLICATES_MSG_REMOVED, group.name, receipt.storeName)))
            }
        }
    }
}
