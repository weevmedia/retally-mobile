/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.scan

import com.retally.shared.core.crash.CrashReporting
import com.retally.shared.domain.category.ItemCategoryClassifier
import com.retally.shared.data.store.StoreIconRepository
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.core.Effect
import com.retally.shared.core.locale.LocaleInfo
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
import com.retally.shared.domain.scan.AddressExtractor
import com.retally.shared.domain.scan.StoreAddressNormalizer
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
        /** Non-null while the "Edit item" sheet is open (swipe a line item → Edit). */
        val itemEdit: ItemEdit? = null,
        /** Server-pushed spending categories, for the picker and item badges. */
        val categories: List<ItemCategory> = emptyList(),
        /** Whether the receipt's store is a favorite (toggled by the top-bar heart). */
        val isFavoriteStore: Boolean = false,
        /** Non-null while the "Edit store" page is open: the shopper's draft name and address. */
        val storeEdit: StoreEdit? = null,
        /** True while the "Delete receipt?" confirmation is up (top-bar trash icon). */
        val confirmingDelete: Boolean = false,
    ) : com.retally.shared.core.State {
        val duplicates: List<ReceiptItem> get() = receipt?.duplicates.orEmpty()
        val overchargeAmount: Double get() = receipt?.overchargeAmount ?: 0.0
    }

    /**
     * Draft in the "Edit item" sheet: name, item number, price, spending category and the
     * duplicate flag of one line. Price is kept as typed so "4." or "" don't fight the
     * keyboard; it's parsed on Save.
     */
    data class ItemEdit(
        val itemId: String,
        val name: String,
        val itemNumber: String,
        val price: String,
        val categoryId: String,
        val isDuplicate: Boolean,
    ) {
        val parsedPrice: Double? get() = price.trim().replace(",", ".").removePrefix("$").toDoubleOrNull()?.takeIf { it >= 0.0 }
        val canSave: Boolean get() = name.isNotBlank() && parsedPrice != null

        companion object {
            fun of(item: ReceiptItem) = ItemEdit(
                itemId = item.id,
                name = item.name,
                itemNumber = item.itemNumber.orEmpty(),
                price = formatPriceInput(item.price),
                categoryId = item.categoryId,
                isDuplicate = item.isDuplicate && !item.keptAsDistinct,
            )

            /** 4.5 → "4.50", 3.0 → "3.00" (two decimals, no locale grouping). */
            internal fun formatPriceInput(value: Double): String {
                val cents = kotlin.math.round(value * 100).toLong()
                val sign = if (cents < 0) "-" else ""
                val abs = kotlin.math.abs(cents)
                return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
            }
        }
    }

    /** A US address split for editing (see [StoreEdit.splitAddress]). */
    data class AddressParts(val street: String, val city: String, val state: String, val zip: String)

    /**
     * Draft on the "Edit receipt details" page. The address is edited as street / city /
     * state / ZIP and saved back as the receipt's usual two-line text
     * ("123 Main St\nEdison, NJ 08817"), so nothing else changes.
     */
    data class StoreEdit(
        val name: String,
        val street: String,
        val city: String,
        val state: String,
        val zip: String,
        val purchaseDate: kotlin.time.Instant,
    ) {
        val canSave: Boolean get() = name.isNotBlank()

        /** The address as stored on the receipt: street line(s), then "City, ST ZIP". */
        val address: String
            get() {
                val region = listOf(state.trim(), zip.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                val cityLine = listOf(city.trim(), region).filter { it.isNotEmpty() }.joinToString(", ")
                return (street.lines().map { it.trim() } + cityLine).filter { it.isNotEmpty() }.joinToString("\n")
            }

        companion object {
            fun of(name: String, address: String?, purchaseDate: kotlin.time.Instant): StoreEdit {
                val parts = splitAddress(address.orEmpty())
                return StoreEdit(name, parts.street, parts.city, parts.state, parts.zip, purchaseDate)
            }

            /**
             * Split of a printed US address, via [AddressExtractor]: "123 Main St\nEDISON NJ 08817"
             * and "123 Main St, Edison, NJ 08817" both give street / city / state / ZIP. Anything
             * it can't place stays in the street, so no text is ever lost.
             */
            fun splitAddress(address: String): AddressParts {
                val parsed = AddressExtractor.extract(address)
                return AddressParts(parsed.street, parsed.city, parsed.state, parsed.zip)
            }
        }
    }

    sealed class Action : com.retally.shared.core.Action {
        /** Pencil beside the store name: open the "Edit store" page seeded with the receipt's values. */
        data object EditStoreClicked : Action()
        data class StoreNameChanged(val name: String) : Action()
        data class StoreStreetChanged(val street: String) : Action()
        data class StoreCityChanged(val city: String) : Action()
        /** Two-letter state; the store uppercases and trims it. */
        data class StoreStateChanged(val state: String) : Action()
        data class StoreZipChanged(val zip: String) : Action()
        /** New purchase date/time picked on the edit page (date and time pickers both land here). */
        data class PurchaseDateChanged(val purchaseDate: kotlin.time.Instant) : Action()
        data object StoreEditSaveClicked : Action()
        /** Back on the "Edit store" page: discard the draft. */
        data object StoreEditDismissed : Action()

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
        /** Swipe → Edit on a line item: opens the "Edit item" sheet. */
        data class ItemEditOpened(val id: String) : Action()
        data class ItemNameChanged(val name: String) : Action()
        data class ItemNumberChanged(val itemNumber: String) : Action()
        data class ItemPriceChanged(val price: String) : Action()
        data class ItemCategoryPicked(val categoryId: String) : Action()
        /** "Mark as duplicate" switch in the sheet. */
        data class ItemDuplicateChanged(val isDuplicate: Boolean) : Action()
        data object ItemEditSaveClicked : Action()
        /** Sheet swiped down / Cancel: discards the draft. */
        data object ItemEditDismissed : Action()
        /** Top-bar heart: favorite/unfavorite this receipt's store. */
        data object FavoriteStoreToggled : Action()
        /** Top-bar share: share the receipt image. */
        data object ShareClicked : Action()
        /** Save the receipt image to the device photo library. */
        data object SaveToPhotosClicked : Action()
        data object CloseClicked : Action()
        /** Top-bar trash icon: asks to confirm, then deletes the receipt and closes. */
        data object DeleteClicked : Action()
        data object DeleteConfirmed : Action()
        data object DeleteCancelled : Action()
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
        /**
         * The receipt's store name or address was edited and saved: the host re-arms the
         * nearby-store geofences so the new store/address gets its fence now.
         */
        data object StoreLocationChanged : SideEffect()
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
        /** Looks up the store's logo right after a scan / store edit (ignoring a cached "no logo"). */
        private val storeIcons: StoreIconRepository? = null,
        /** Server AI categories, applied right after a new scan is saved. Null = keywords only. */
        private val itemClassifier: ItemCategoryClassifier? = null,
        /** Device region → the currency stamped on every new receipt. */
        private val locale: LocaleInfo = LocaleInfo.US,
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
                is Action.TextRecognized -> if (!started) { started = true; launch { parseAndSave(action.lines, action.imagePath) } }
                is Action.LoadReceipt -> if (!started) { started = true; load(action.id) }
                is Action.ItemDuplicateToggled -> toggleDuplicate(action.id)
                is Action.KeepBoth -> keepBoth(action.id)
                is Action.RemoveOneRequested -> currentState = currentState.copy(
                    pendingRemoval = currentState.receipt?.items?.firstOrNull { it.id == action.id },
                )
                Action.RemoveOneCancelled -> currentState = currentState.copy(pendingRemoval = null)
                Action.RemoveOneConfirmed -> removeOne()
                is Action.ItemEditOpened -> currentState.receipt?.items?.firstOrNull { it.id == action.id }?.let { item ->
                    currentState = currentState.copy(itemEdit = ItemEdit.of(item))
                }
                is Action.ItemNameChanged -> currentState.itemEdit?.let {
                    currentState = currentState.copy(itemEdit = it.copy(name = action.name))
                }
                is Action.ItemNumberChanged -> currentState.itemEdit?.let {
                    currentState = currentState.copy(itemEdit = it.copy(itemNumber = action.itemNumber.trim().take(32)))
                }
                is Action.ItemPriceChanged -> currentState.itemEdit?.let {
                    val price = action.price.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10)
                    currentState = currentState.copy(itemEdit = it.copy(price = price))
                }
                is Action.ItemCategoryPicked -> currentState.itemEdit?.let {
                    currentState = currentState.copy(itemEdit = it.copy(categoryId = action.categoryId))
                }
                is Action.ItemDuplicateChanged -> currentState.itemEdit?.let {
                    currentState = currentState.copy(itemEdit = it.copy(isDuplicate = action.isDuplicate))
                }
                Action.ItemEditSaveClicked -> saveItemEdit()
                Action.ItemEditDismissed -> currentState = currentState.copy(itemEdit = null)
                Action.FavoriteStoreToggled -> toggleFavoriteStore()
                Action.ShareClicked -> sideEffect(SideEffect.ShareReceipt(currentState.receipt?.imagePath))
                Action.SaveToPhotosClicked -> sideEffect(SideEffect.SaveToPhotos(currentState.receipt?.imagePath))
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
                Action.DeleteClicked -> if (currentState.receipt != null && !currentState.isSaving) {
                    currentState = currentState.copy(confirmingDelete = true)
                }
                Action.DeleteCancelled -> currentState = currentState.copy(confirmingDelete = false)
                Action.DeleteConfirmed -> deleteReceipt()
                Action.EditStoreClicked -> currentState.receipt?.let { receipt ->
                    currentState = currentState.copy(
                        storeEdit = StoreEdit.of(receipt.storeName, receipt.storeAddress, receipt.purchaseDate),
                    )
                }
                is Action.StoreNameChanged -> currentState.storeEdit?.let {
                    currentState = currentState.copy(storeEdit = it.copy(name = action.name))
                }
                is Action.StoreStreetChanged -> currentState.storeEdit?.let {
                    currentState = currentState.copy(storeEdit = it.copy(street = action.street))
                }
                is Action.StoreCityChanged -> currentState.storeEdit?.let {
                    currentState = currentState.copy(storeEdit = it.copy(city = action.city))
                }
                is Action.StoreStateChanged -> currentState.storeEdit?.let {
                    val state = action.state.filter { c -> c.isLetter() }.take(2).uppercase()
                    currentState = currentState.copy(storeEdit = it.copy(state = state))
                }
                is Action.StoreZipChanged -> currentState.storeEdit?.let {
                    val zip = action.zip.filter { c -> c.isDigit() || c == '-' }.take(10)
                    currentState = currentState.copy(storeEdit = it.copy(zip = zip))
                }
                is Action.PurchaseDateChanged -> currentState.storeEdit?.let {
                    currentState = currentState.copy(storeEdit = it.copy(purchaseDate = action.purchaseDate))
                }
                Action.StoreEditSaveClicked -> saveStoreEdit()
                Action.StoreEditDismissed -> currentState = currentState.copy(storeEdit = null)
            }
        }

        /**
         * The shopper's fix for a misread store name/address. Saved like any other correction
         * (local first, then re-uploaded); a typed name also counts as a confirmed merchant.
         */
        private fun saveStoreEdit() {
            val receipt = currentState.receipt ?: return
            val draft = currentState.storeEdit ?: return
            if (!draft.canSave) return
            val name = draft.name.trim()
            val address = draft.address.lines().map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString("\n").ifEmpty { null }
            val dateChanged = draft.purchaseDate != receipt.purchaseDate
            if (name == receipt.storeName && address == receipt.storeAddress && !dateChanged) {
                currentState = currentState.copy(storeEdit = null)
                return
            }
            val updated = receipt.copy(
                storeName = name,
                storeAddress = address,
                purchaseDate = draft.purchaseDate,
                // A typed name / picked date is the shopper confirming it.
                missingFields = receipt.missingFields - "merchant" - (if (dateChanged) setOf("date") else emptySet()),
                syncedAt = null, // changed locally → needs re-upload
            )
            currentState = currentState.copy(
                receipt = updated,
                storeEdit = null,
                isSaving = true,
                isFavoriteStore = favorites.isFavorite(name),
            )
            val storeChanged = name != receipt.storeName || address != receipt.storeAddress
            persist(updated, announce = false, storeChanged = storeChanged)
            sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.RECEIPT_STORE_UPDATED)))
        }

        /** Same delete as the swipe on Home / Receipts (local + cloud soft-delete), then close. */
        private fun deleteReceipt() {
            val receipt = currentState.receipt ?: return
            currentState = currentState.copy(confirmingDelete = false)
            launch {
                repository.delete(receipt.id)
                sideEffect(SideEffect.Closed)
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

        private suspend fun parseAndSave(lines: List<String>, imagePath: String?) {
            val parseStart = clock.now()
            val parsed = parser.parse(lines)
            // One clean "street / City, ST ZIP" block whatever the chain's header layout —
            // the platform address detector over the raw header, regex split as fallback.
            val storeAddress = StoreAddressNormalizer.normalize(lines, parsed.storeAddress)
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
                storeAddress = storeAddress,
                currency = locale.currencyCode,
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
                CrashReporting.setValue("lastParseSource", "CLOUD_AI")
                currentState = currentState.copy(isLoading = true, isAiParsing = true)
                tryAiFallback(imagePath!!, receipt)
                return
            }

            println("[ReTallyScan] parseSource=LOCAL outcome=$outcome status=$scanStatus")
            CrashReporting.setValue("lastParseSource", "LOCAL")
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
                        CrashReporting.record(IllegalStateException("AI receipt read failed: ${result.reason}"))
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
                // The AI sometimes lists discounts / refunds as negative "items"; they aren't.
                // Then flag duplicates exactly like the local parser does — without this, an
                // AI-read receipt never showed a double charge (4× WHOLE MILK $2.47, no flags).
                items = dto.items.filter { it.price > 0.0 }.map {
                    ReceiptItem(
                        id = newId(),
                        // Product code lets duplicates and price drops match by code, like a local read.
                        itemNumber = it.itemNumber?.trim()?.takeIf { code -> code.count(Char::isDigit) >= 3 },
                        name = it.name,
                        price = it.price,
                        categoryId = ItemCategorizer.categorize(it.name, categories.categories.value),
                    )
                }.takeIf { it.isNotEmpty() }?.let(DuplicateMatcher::flag) ?: fallback.items,
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

        /**
         * Saves the "Edit item" sheet into the open receipt (local first, then re-uploaded) and
         * closes it. Name / number / price feed duplicate matching, so the flags are re-run
         * when any of those changed. The sheet's own "Mark as duplicate" switch is the
         * shopper's word and wins over the matcher for this line; a category-only change
         * touches nothing else. A hand-picked category is never auto-retagged.
         * The printed receipt TOTAL is never touched.
         */
        private fun saveItemEdit() {
            val receipt = currentState.receipt ?: return
            val draft = currentState.itemEdit ?: return
            val price = draft.parsedPrice ?: return
            if (draft.name.isBlank()) return
            val original = receipt.items.firstOrNull { it.id == draft.itemId } ?: run {
                currentState = currentState.copy(itemEdit = null)
                return
            }
            val wasFlagged = original.isDuplicate && !original.keptAsDistinct
            val flagChanged = draft.isDuplicate != wasFlagged
            val edited = original.copy(
                name = draft.name.trim(),
                itemNumber = draft.itemNumber.trim().ifEmpty { null },
                price = price,
                categoryId = draft.categoryId,
                // A hand-picked category is never overwritten by the server's AI pass.
                categoryLocked = original.categoryLocked || draft.categoryId != original.categoryId,
                isDuplicate = if (flagChanged) draft.isDuplicate else original.isDuplicate,
                // Flagging by hand undoes an earlier "Keep both"; unflagging just clears it.
                keptAsDistinct = if (flagChanged) false else original.keptAsDistinct,
            )
            if (edited == original) {
                currentState = currentState.copy(itemEdit = null)
                return
            }
            val matchingChanged = edited.name != original.name ||
                edited.itemNumber != original.itemNumber ||
                edited.price != original.price
            var items = receipt.items.map { if (it.id == edited.id) edited else it }
            if (matchingChanged) {
                items = DuplicateMatcher.flag(items)
                // The matcher must not undo what the shopper just set on this line.
                if (flagChanged) items = items.map { if (it.id == edited.id) it.copy(isDuplicate = draft.isDuplicate, keptAsDistinct = false) else it }
            }
            val updated = receipt.copy(
                items = items,
                syncedAt = null, // changed locally → needs re-upload
            )
            currentState = currentState.copy(receipt = updated, itemEdit = null, isSaving = true)
            persist(updated, announce = false)
            sideEffect(SideEffect.ShowMessage(strings.getString(StringAssetIdentifiers.RECEIPT_ITEM_UPDATED)))
        }

        /**
         * Asks the server's AI for categories and applies them to the receipt on screen (items
         * the shopper set by hand in the meantime are kept). Silent: on failure the keyword
         * guesses stay.
         */
        private fun refineCategories(receiptId: String) {
            val classifier = itemClassifier ?: return
            launch {
                val current = currentState.receipt?.takeIf { it.id == receiptId } ?: return@launch
                val knownIds = categories.categories.value.map { it.id }.toSet()
                val names = current.items.filter { !it.categoryLocked }.map { it.name }.distinct()
                if (names.isEmpty()) return@launch
                val result = runCatching { classifier.classify(current.storeName, names) }.getOrNull() ?: return@launch
                // Re-read: the shopper may have edited the receipt while we waited.
                val latest = currentState.receipt?.takeIf { it.id == receiptId } ?: return@launch
                var changed = false
                val items = latest.items.map { item ->
                    val id = result[item.name]
                    if (!item.categoryLocked && id != null && id in knownIds && id != item.categoryId) {
                        changed = true
                        item.copy(categoryId = id)
                    } else {
                        item
                    }
                }
                if (!changed) return@launch
                val updated = latest.copy(items = items, syncedAt = null)
                currentState = currentState.copy(receipt = updated)
                persist(updated, announce = false)
            }
        }

        private fun persist(receipt: Receipt, announce: Boolean, storeChanged: Boolean = false) {
            launch {
                runCatching { repository.upsert(receipt) }
                    .onSuccess {
                        currentState = currentState.copy(isSaving = false, savedAt = clock.now())
                        if (announce) sideEffect(SideEffect.Saved(receipt.id))
                        // A new scan: let the server's AI replace the keyword guesses.
                        if (announce) refineCategories(receipt.id)
                        // After the write, so the geofence rebuild reads the edited store.
                        if (storeChanged) sideEffect(SideEffect.StoreLocationChanged)
                        // A just-scanned (or renamed) store: ask for its logo now, even if the
                        // device remembered "no logo" — it may have been added since.
                        launch { runCatching { storeIcons?.resolve(receipt.storeName, recheckMissing = true) } }
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
