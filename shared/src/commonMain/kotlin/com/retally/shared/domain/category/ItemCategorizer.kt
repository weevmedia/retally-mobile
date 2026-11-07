/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.category

import com.retally.shared.domain.receipt.ReceiptRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.first

/**
 * Instant on-device guess from the server-pushed category list: the first category whose
 * keyword appears in the item name wins (case-insensitive substring). Anything else is
 * "Uncategorized" — never silently Grocery — until the server's AI pass
 * ([ItemCategoryClassifier]) places it.
 *
 * Runs entirely on-device — no network.
 */
object ItemCategorizer {
    fun categorize(itemName: String, categories: List<ItemCategory>): String {
        // Same text on both platforms: iOS OCR can put non-breaking or repeated spaces between
        // words, which made multi-word keywords ("ground beef") miss there.
        val lower = itemName.replace('\u00A0', ' ').replace(WHITESPACE, " ").trim().lowercase()
        return categories.firstOrNull { category ->
            category.keywords.any { keyword -> keyword.lowercase() in lower }
        }?.id ?: ItemCategory.UNCATEGORIZED_ID
    }

    private val WHITESPACE = Regex("\\s+")
}

/**
 * One-time pass (per install) that sends every stored receipt's auto-tagged items to the
 * server's AI categoriser. Items whose category differs from what tagging would have given
 * are treated as the shopper's own fix: they're locked and left alone. Marked done only when
 * every receipt went through, so an offline launch retries next time.
 */
suspend fun backfillAiCategories(
    receipts: ReceiptRepository,
    classifier: ItemCategoryClassifier,
    categories: List<ItemCategory>,
    settings: Settings,
) {
    if (settings.getBoolean(KEY_AI_CATEGORIZED, false)) return
    val knownIds = categories.map { it.id }.toSet()
    var allDone = true
    for (receipt in receipts.observeReceipts().first()) {
        val marked = receipt.copy(
            items = receipt.items.map { item ->
                val auto = item.categoryId == ItemCategory.DEFAULT_ID ||
                    item.categoryId == ItemCategory.UNCATEGORIZED_ID ||
                    item.categoryId == ItemCategorizer.categorize(item.name, categories)
                if (!auto && !item.categoryLocked) item.copy(categoryLocked = true) else item
            },
        )
        if (marked.items.none { !it.categoryLocked }) continue
        val names = marked.items.filter { !it.categoryLocked }.map { it.name }.distinct()
        val result = classifier.classify(marked.storeName, names)
        if (result == null) {
            allDone = false
            break // offline / server down: try again next launch
        }
        val refined = marked.copy(
            items = marked.items.map { item ->
                val id = result[item.name]
                if (!item.categoryLocked && id != null && id in knownIds) item.copy(categoryId = id) else item
            },
        )
        if (refined != receipt) receipts.upsert(refined.copy(syncedAt = null))
    }
    if (allDone) settings.putBoolean(KEY_AI_CATEGORIZED, true)
}

// v2: re-run once so items left "Uncategorized" by the iOS name-matching bug (fixed in
// HttpItemCategoryClassifier) get their AI category. Hand-set categories stay locked.
private const val KEY_AI_CATEGORIZED = "items.categorized.ai.v2"

/**
 * One-time pass over stored receipts: tags every item with the current category
 * list. Runs once (guarded by a Settings flag) right after the feature lands —
 * afterwards items keep their stored category unless the shopper changes it by
 * hand, so later category-list updates never clobber manual fixes.
 */
suspend fun backfillItemCategories(
    receipts: ReceiptRepository,
    categories: List<ItemCategory>,
    settings: Settings,
) {
    if (settings.getBoolean(KEY_CATEGORIZED, false)) return
    val all = receipts.observeReceipts().first()
    for (receipt in all) {
        var touched = false
        val updated = receipt.items.map { item ->
            val id = ItemCategorizer.categorize(item.name, categories)
            if (id != item.categoryId) {
                touched = true
                item.copy(categoryId = id)
            } else {
                item
            }
        }
        if (touched) receipts.upsert(receipt.copy(items = updated))
    }
    settings.putBoolean(KEY_CATEGORIZED, true)
}

private const val KEY_CATEGORIZED = "items.categorized.v1"
