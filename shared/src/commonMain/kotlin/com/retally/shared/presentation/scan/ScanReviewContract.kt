package com.retally.shared.presentation.scan

import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.core.Effect
import com.retally.shared.core.newId
import com.retally.shared.domain.cloud.ReceiptBackup
import com.retally.shared.domain.favorites.FavoriteStoresRepository
import com.retally.shared.domain.receipt.DuplicateMatcher
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import com.retally.shared.domain.scan.ReceiptParser
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Receipt view. Two entry points share it:
 *  - after a scan, [Action.TextRecognized] parses the OCR lines, **saves immediately** and
 *    shows the result (no confirm step — the shopper is standing at the register);
 *  - from Home, [Action.LoadReceipt] shows a receipt that already exists.
 *
 * Edits are limited to resolving duplicate flags — every flag offers **Keep both** (it really
 * was bought twice; the line is marked distinct) or **Remove one** (the extra line is deleted
 * after the shopper confirms; nothing is ever removed automatically). Both re-save.
 * [Action.CloseClicked] emits [SideEffect.Closed] so the host pops.
 */
interface ScanReviewContract {

    data class State(
        val receipt: Receipt? = null,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        /** Parse finished but produced no priced lines — nothing was saved. */
        val nothingFound: Boolean = false,
        val savedAt: kotlin.time.Instant? = null,
        /** Line the shopper asked to remove; the UI shows a confirmation until this is null. */
        val pendingRemoval: ReceiptItem? = null,
        /** Whether the receipt's store is a favorite (toggled by the top-bar heart). */
        val isFavoriteStore: Boolean = false,
    ) : com.retally.shared.core.State {
        val duplicates: List<ReceiptItem> get() = receipt?.duplicates.orEmpty()
        val overchargeAmount: Double get() = receipt?.overchargeAmount ?: 0.0
    }

    sealed class Action : com.retally.shared.core.Action {
        /** OCR result from the scanner. Idempotent: the first call wins. */
        data class TextRecognized(val lines: List<String>, val imagePath: String? = null) : Action()
        /** Show an existing receipt (from the Home list). */
        data class LoadReceipt(val id: String) : Action()
        /** Manual override when the parser missed or over-flagged a double charge. */
        data class ItemDuplicateToggled(val id: String) : Action()
        /** "Keep both": the flagged line was a real second purchase. */
        data class KeepBoth(val id: String) : Action()
        /** "Remove one": ask before deleting the flagged line. */
        data class RemoveOneRequested(val id: String) : Action()
        data object RemoveOneConfirmed : Action()
        data object RemoveOneCancelled : Action()
        /** Top-bar heart: favorite/unfavorite this receipt's store. */
        data object FavoriteStoreToggled : Action()
        data object CloseClicked : Action()
    }

    sealed class SideEffect : Effect {
        /** Informational — the screen stays open showing the saved receipt. */
        data class Saved(val receiptId: String) : SideEffect()
        data object Closed : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val repository: ReceiptRepository,
        private val backup: ReceiptBackup,
        private val favorites: FavoriteStoresRepository,
        private val parser: ReceiptParser = ReceiptParser(),
        private val clock: Clock = Clock.System,
        private val strings: StringResolver = EnglishStringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        private var started = false

        init {
            // Re-derive the heart state when favorites change. The receipt setters below
            // derive it the moment a receipt arrives — the flow's first emission fires
            // before an async LoadReceipt completes, so it cannot be the only source.
            launch {
                favorites.favorites.collect {
                    val name = currentState.receipt?.storeName
                    currentState = currentState.copy(isFavoriteStore = name != null && favorites.isFavorite(name))
                }
            }
        }

        override fun initState() = State()

        override fun dispatch(action: Action) {
            when (action) {
                is Action.TextRecognized -> if (!started) { started = true; parseAndSave(action.lines, action.imagePath) }
                is Action.LoadReceipt -> if (!started) { started = true; load(action.id) }
                is Action.ItemDuplicateToggled -> toggleDuplicate(action.id)
                is Action.KeepBoth -> keepBoth(action.id)
                is Action.RemoveOneRequested -> currentState = currentState.copy(
                    pendingRemoval = currentState.receipt?.items?.firstOrNull { it.id == action.id },
                )
                Action.RemoveOneCancelled -> currentState = currentState.copy(pendingRemoval = null)
                Action.RemoveOneConfirmed -> removeOne()
                Action.FavoriteStoreToggled -> toggleFavoriteStore()
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
            }
        }

        private fun toggleFavoriteStore() {
            val name = currentState.receipt?.storeName?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val wasFavorite = favorites.isFavorite(name)
            favorites.toggle(name)
            sideEffect(
                SideEffect.ShowMessage(
                    if (wasFavorite) strings.getString(StringAssetIdentifiers.MORE_FAVORITE_REMOVED, name)
                    else strings.getString(StringAssetIdentifiers.MORE_FAVORITE_ADDED, name),
                ),
            )
        }

        private fun parseAndSave(lines: List<String>, imagePath: String?) {
            val parsed = parser.parse(lines)
            if (parsed.items.isEmpty()) {
                currentState = currentState.copy(isLoading = false, nothingFound = true)
                return
            }
            val now = clock.now()
            val receipt = Receipt(
                id = newId(),
                storeName = parsed.storeName.trim(),
                storeAddress = parsed.storeAddress,
                purchaseDate = parsed.purchaseDate ?: now,
                paymentInfo = parsed.paymentInfo,
                items = parsed.items.map {
                    ReceiptItem(id = it.id, itemNumber = it.itemNumber, name = it.name, price = it.price, isDuplicate = it.isDuplicate)
                },
                subtotal = parsed.subtotal,
                tax = parsed.tax,
                receiptTotal = parsed.total,
                barcode = parsed.barcode,
                imagePath = imagePath,
                createdAt = now,
            )
            // Show it right away; persistence completes in the background.
            currentState = currentState.copy(
                receipt = receipt,
                isLoading = false,
                isSaving = true,
                isFavoriteStore = favorites.isFavorite(receipt.storeName),
            )
            persist(receipt, announce = true)
        }

        private fun load(id: String) {
            launch {
                val receipt = repository.getReceipt(id)
                currentState = currentState.copy(
                    receipt = receipt,
                    isLoading = false,
                    nothingFound = receipt == null,
                    // The favorites flow's first emission fires before this async load
                    // finishes, so derive the heart here instead of waiting for it.
                    isFavoriteStore = receipt?.storeName?.let { favorites.isFavorite(it) } == true,
                )
            }
        }

        private fun toggleDuplicate(itemId: String) {
            val receipt = currentState.receipt ?: return
            val updated = receipt.copy(
                items = receipt.items.map { if (it.id == itemId) it.copy(isDuplicate = !it.isDuplicate) else it },
                syncedAt = null, // changed locally → needs re-upload
            )
            currentState = currentState.copy(receipt = updated, isSaving = true)
            persist(updated, announce = false)
        }

        /** Marks the line distinct and re-runs the matcher so its partner is unflagged if it was the only match. */
        private fun keepBoth(itemId: String) {
            val receipt = currentState.receipt ?: return
            val items = receipt.items.map { if (it.id == itemId) it.copy(keptAsDistinct = true, isDuplicate = false) else it }
            val updated = receipt.copy(items = DuplicateMatcher.flag(items), syncedAt = null)
            currentState = currentState.copy(receipt = updated, isSaving = true)
            persist(updated, announce = false)
            sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.RECEIPT_MSG_KEPT_BOTH)))
        }

        /** Deletes the confirmed line; the printed total is kept, the item sum recomputes. */
        private fun removeOne() {
            val receipt = currentState.receipt ?: return
            val target = currentState.pendingRemoval ?: return
            val remaining = receipt.items.filterNot { it.id == target.id }
            val updated = receipt.copy(items = DuplicateMatcher.flag(remaining), syncedAt = null)
            currentState = currentState.copy(receipt = updated, pendingRemoval = null, isSaving = true)
            persist(updated, announce = false)
            sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.RECEIPT_MSG_REMOVED_ONE, target.name)))
        }

        private fun persist(receipt: Receipt, announce: Boolean) {
            launch {
                runCatching { repository.upsert(receipt) }
                    .onSuccess {
                        currentState = currentState.copy(isSaving = false, savedAt = clock.now())
                        if (announce) sideEffect(SideEffect.Saved(receipt.id))
                        backup.backUp(receipt)
                    }
                    .onFailure { error ->
                        error.printStackTrace()
                        currentState = currentState.copy(isSaving = false)
                        val reason = error.message?.takeIf { it.isNotBlank() } ?: (error::class.simpleName ?: "unknown error")
                        sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.RECEIPT_MSG_SAVE_FAILED, reason)))
                    }
            }
        }
    }
}
