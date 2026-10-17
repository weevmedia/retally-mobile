package com.retally.shared.presentation.scan

import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.core.Effect
import com.retally.shared.core.newId
import com.retally.shared.domain.category.CategorySource
import com.retally.shared.domain.category.ItemCategorizer
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.cloud.ReceiptBackup
import com.retally.shared.domain.favorites.FavoriteStoresRepository
import com.retally.shared.domain.receipt.DuplicateMatcher
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import com.retally.shared.domain.receipt.model.ParseSource
import com.retally.shared.domain.receipt.model.ScanStatus
import com.retally.shared.domain.scan.ContentReport
import com.retally.shared.domain.scan.QualityReport
import com.retally.shared.domain.scan.ScanOutcome
import com.retally.shared.domain.scan.decideScanOutcome
import com.retally.shared.domain.scan.ReceiptParser
import com.retally.shared.data.cloud.TornReceiptClient
import com.retally.shared.data.cloud.TornReceiptResult
import com.retally.shared.data.cloud.readFileBytes
import com.retally.shared.data.cloud.resolveImagePath
import kotlinx.coroutines.launch
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
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
        /** True while the Gemini fallback is reading a torn receipt — drives the AI loading copy. */
        val isAiParsing: Boolean = false,
        val savedAt: kotlin.time.Instant? = null,
        /** Line the shopper asked to remove; the UI shows a confirmation until this is null. */
        val pendingRemoval: ReceiptItem? = null,
        /** Line whose category the shopper is changing; the UI shows a picker until null. */
        val categoryPickerItem: ReceiptItem? = null,
        /** Server-pushed spending categories, for the picker and item badges. */
        val categories: List<ItemCategory> = emptyList(),
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
        /** Change an item's spending category (auto-tagged at scan time). */
        data class ItemCategoryPickerOpened(val id: String) : Action()
        data class ItemCategoryPicked(val categoryId: String) : Action()
        data object ItemCategoryPickerDismissed : Action()
        /** Top-bar heart: favorite/unfavorite this receipt's store. */
        data object FavoriteStoreToggled : Action()
        /** Top-bar share: share the receipt image. */
        data object ShareClicked : Action()
        /** Save the receipt image to the device photo library. */
        data object SaveToPhotosClicked : Action()
        data object CloseClicked : Action()
    }

    sealed class SideEffect : Effect {
        /** Informational — the screen stays open showing the saved receipt. */
        data class Saved(val receiptId: String) : SideEffect()
        /** Share the receipt image (platform handles the share sheet). */
        data class ShareReceipt(val imagePath: String?) : SideEffect()
        /** Save the receipt image to the device photo library. */
        data class SaveToPhotos(val imagePath: String?) : SideEffect()
        data object Closed : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val repository: ReceiptRepository,
        private val backup: ReceiptBackup,
        private val favorites: FavoriteStoresRepository,
        private val categories: CategorySource,
        private val parser: ReceiptParser = ReceiptParser(),
        private val clock: Clock = Clock.System,
        private val strings: StringResolver = EnglishStringResolver,
        /** Gemini fallback for torn receipts. Null = AI disabled, local only. */
        private val tornReceiptClient: TornReceiptClient? = null,
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
            launch {
                categories.categories.collect { list ->
                    currentState = currentState.copy(categories = list)
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
                is Action.ItemCategoryPickerOpened -> currentState = currentState.copy(
                    categoryPickerItem = currentState.receipt?.items?.firstOrNull { it.id == action.id },
                )
                is Action.ItemCategoryPicked -> changeItemCategory(action.categoryId)
                Action.ItemCategoryPickerDismissed -> currentState = currentState.copy(categoryPickerItem = null)
                Action.FavoriteStoreToggled -> toggleFavoriteStore()
                Action.ShareClicked -> sideEffect(SideEffect.ShareReceipt(currentState.receipt?.imagePath))
                Action.SaveToPhotosClicked -> sideEffect(SideEffect.SaveToPhotos(currentState.receipt?.imagePath))
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
            val parseStart = clock.now()
            val parsed = parser.parse(lines)
            // Nothing usable at all — no items and no total.
            if (parsed.items.isEmpty() && parsed.total == null) {
                currentState = currentState.copy(isLoading = false, nothingFound = true)
                return
            }

            // Layer 3: content checks — what did OCR actually extract?
            val itemsTotal = parsed.items.sumOf { it.price }
            val tax = parsed.tax ?: 0.0
            val total = parsed.total
            // Items + tax rarely equals the total exactly (discounts, deposits, bottle
            // fees). Allow 2% or $1.00, whichever is larger — tighter than that and
            // every real receipt looks "torn."
            val tolerance = maxOf((total ?: 0.0) * 0.02, 1.0)
            val itemsMatchTotal = total != null &&
                kotlin.math.abs(itemsTotal + tax - total) <= tolerance
            val content = ContentReport(
                hasMerchant = parsed.storeName.isNotBlank() &&
                    !parsed.storeName.equals("Unknown Store", ignoreCase = true),
                hasDate = parsed.purchaseDate != null,
                hasTotal = total != null,
                itemsMatchTotal = itemsMatchTotal,
                declaredItemCount = parsed.declaredItemCount,
                parsedItemCount = parsed.items.size,
            )
            // Layer 2 (pixel checks) not yet implemented — assume acceptable.
            // When built, pass the real QualityReport here.
            val quality = QualityReport(
                sharpness = Double.MAX_VALUE,
                brightness = 128.0,
                contrast = 100.0,
                glareRatio = 0.0,
            )
            val outcome = decideScanOutcome(
                quality = quality,
                content = content,
                attempt = 1,
                previousMissing = null,
            )
            val (scanStatus, missingFields, confidence) = when (outcome) {
                ScanOutcome.ACCEPT -> Triple(ScanStatus.COMPLETE, emptySet<String>(), 1.0)
                ScanOutcome.PARTIAL -> Triple(ScanStatus.PARTIAL, content.missingFields, 0.7)
                ScanOutcome.MANUAL_ENTRY -> Triple(ScanStatus.MANUAL_ENTRY, content.missingFields, 0.3)
                // RETAKE without Layer 2 pixel data: treat as partial for now.
                ScanOutcome.RETAKE -> Triple(ScanStatus.PARTIAL, content.missingFields, 0.7)
            }

            val now = clock.now()
            val receipt = Receipt(
                id = newId(),
                storeName = parsed.storeName.trim(),
                storeAddress = parsed.storeAddress,
                purchaseDate = parsed.purchaseDate ?: now,
                paymentInfo = parsed.paymentInfo,
                items = parsed.items.map {
                    ReceiptItem(
                        id = it.id,
                        itemNumber = it.itemNumber,
                        name = it.name,
                        price = it.price,
                        isDuplicate = it.isDuplicate,
                        categoryId = ItemCategorizer.categorize(it.name, categories.categories.value),
                    )
                },
                subtotal = parsed.subtotal,
                tax = parsed.tax,
                receiptTotal = parsed.total,
                barcode = parsed.barcode,
                imagePath = imagePath,
                createdAt = now,
                scanStatus = scanStatus,
                missingFields = missingFields,
                confidence = confidence,
                parseSource = ParseSource.LOCAL,
            )

            // AI fallback for torn/damaged/incomplete receipts: if local parsing came up
            // short and we have the image + a configured client, let Gemini take a look.
            // RETAKE is included — without the retake UI built yet, those save as partial
            // and the AI might read what the local parser couldn't.
            val needsAi = (outcome == ScanOutcome.PARTIAL ||
                outcome == ScanOutcome.MANUAL_ENTRY ||
                outcome == ScanOutcome.RETAKE) &&
                tornReceiptClient != null && imagePath != null
            if (needsAi) {
                println("[ReTallyScan] parseSource=LOCAL (incomplete: ${missingFields.joinToString()}) → trying CLOUD_AI")
                currentState = currentState.copy(isLoading = true, isAiParsing = true)
                tryAiFallback(imagePath!!, receipt)
                return
            }

            println("[ReTallyScan] parseSource=LOCAL outcome=$outcome status=$scanStatus")
            // Show it right away; persistence completes in the background.
            // Keep the cooking animation visible for at least 1.2s total so it's
            // perceptible — but don't add lag if OCR already took a while. The delay
            // only covers the remainder.
            launch {
                val elapsed = clock.now() - parseStart
                val remaining = 1200 - elapsed.inWholeMilliseconds
                if (remaining > 0) kotlinx.coroutines.delay(remaining)
                currentState = currentState.copy(
                    receipt = receipt,
                    isLoading = false,
                    isAiParsing = false,
                    isSaving = true,
                    isFavoriteStore = favorites.isFavorite(receipt.storeName),
                )
                persist(receipt, announce = true)
            }
        }

        /**
         * Sends the receipt image to Gemini via the parseTornReceipt Cloud Function.
         * On success the AI result replaces the local partial; on any failure we fall
         * back to the local result. [fallback] is the local-parsed receipt to use if
         * the AI call fails.
         */
        private fun tryAiFallback(imagePath: String, fallback: Receipt) {
            val client = tornReceiptClient ?: return showLocalFallback(fallback, "no-client")
            launch {
                // Resolve the stored reference (filename or legacy path) to a full
                // filesystem path before reading — readFileBytes needs the real path.
                val resolved = try {
                    resolveImagePath(imagePath)
                } catch (e: Exception) {
                    null
                }
                val bytes = if (resolved != null) try {
                    readFileBytes(resolved)
                } catch (e: Exception) {
                    null
                } else null
                if (bytes == null) {
                    return@launch showLocalFallback(fallback, "image-read-failed")
                }
                val base64 = kotlin.io.encoding.Base64.encode(bytes)
                when (val result = client.parse(base64)) {
                    is TornReceiptResult.Success -> {
                        val aiReceipt = receiptFromAi(result.receipt, fallback)
                        println("[ReTallyScan] parseSource=CLOUD_AI items=${aiReceipt.items.size} total=${aiReceipt.receiptTotal}")
                        currentState = currentState.copy(
                            receipt = aiReceipt,
                            isLoading = false,
                            isAiParsing = false,
                            isSaving = true,
                            isFavoriteStore = favorites.isFavorite(aiReceipt.storeName),
                        )
                        persist(aiReceipt, announce = true)
                    }
                    is TornReceiptResult.Failure -> {
                        showLocalFallback(fallback, result.reason)
                    }
                }
            }
        }

        /** AI call failed — show the local partial result and log why. */
        private fun showLocalFallback(fallback: Receipt, reason: String) {
            println("[ReTallyScan] parseSource=LOCAL (ai-fallback-failed: $reason)")
            currentState = currentState.copy(
                receipt = fallback,
                isLoading = false,
                isAiParsing = false,
                isSaving = true,
                isFavoriteStore = favorites.isFavorite(fallback.storeName),
            )
            persist(fallback, announce = true)
        }

        /**
         * Merges the Gemini DTO into a Receipt. Uses the AI's data but keeps the local
         * image path, id, and timestamps. Confidence comes from the AI's per-field scores.
         */
        private fun receiptFromAi(dto: com.retally.shared.data.cloud.TornReceiptDto, fallback: Receipt): Receipt {
            val now = clock.now()
            val avgConfidence = listOf(
                dto.storeNameConfidence,
                dto.purchaseDateConfidence,
                dto.totalConfidence,
            ).average().coerceIn(0.0, 1.0)
            // Parse YYYY-MM-DD into an Instant at noon UTC.
            val aiDate: kotlin.time.Instant? = try {
                dto.purchaseDate?.let {
                    val parts = it.split("-").map { p -> p.toInt() }
                    kotlinx.datetime.LocalDate(parts[0], parts[1], parts[2])
                        .atTime(12, 0)
                        .toInstant(kotlinx.datetime.TimeZone.UTC)
                }
            } catch (e: Exception) {
                null
            }
            val missing = buildSet {
                if (dto.storeName.isNullOrBlank()) add("merchant")
                if (aiDate == null) add("date")
                if (dto.total == null) add("total")
                if (dto.items.isEmpty()) add("items")
            }
            return fallback.copy(
                storeName = dto.storeName?.trim()?.takeIf { it.isNotEmpty() } ?: fallback.storeName,
                purchaseDate = aiDate ?: fallback.purchaseDate,
                paymentInfo = dto.paymentInfo ?: fallback.paymentInfo,
                items = dto.items.map {
                    ReceiptItem(
                        id = newId(),
                        name = it.name,
                        price = it.price,
                        categoryId = ItemCategorizer.categorize(it.name, categories.categories.value),
                    )
                }.takeIf { it.isNotEmpty() } ?: fallback.items,
                subtotal = dto.subtotal ?: fallback.subtotal,
                tax = dto.tax ?: fallback.tax,
                receiptTotal = dto.total ?: fallback.receiptTotal,
                scanStatus = if (dto.total != null) ScanStatus.PARTIAL else ScanStatus.MANUAL_ENTRY,
                missingFields = missing,
                confidence = avgConfidence,
                parseSource = ParseSource.CLOUD_AI,
                createdAt = now,
            )
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

        /** Manual category fix: the shopper's choice wins and is never auto-retagged. */
        private fun changeItemCategory(categoryId: String) {
            val receipt = currentState.receipt ?: return
            val target = currentState.categoryPickerItem ?: return
            val updated = receipt.copy(
                items = receipt.items.map { if (it.id == target.id) it.copy(categoryId = categoryId) else it },
                syncedAt = null, // changed locally → needs re-upload
            )
            currentState = currentState.copy(receipt = updated, categoryPickerItem = null, isSaving = true)
            persist(updated, announce = false)
        }

        private fun persist(receipt: Receipt, announce: Boolean) {
            launch {
                runCatching { repository.upsert(receipt) }
                    .onSuccess {
                        currentState = currentState.copy(isSaving = false, savedAt = clock.now())
                        if (announce) sideEffect(SideEffect.Saved(receipt.id))
                        runCatching { backup.backUp(receipt) }
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
