package com.retally.shared.domain.category

import com.retally.shared.domain.receipt.ReceiptRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.first

/**
 * On-device item tagging from the server-pushed category list. First category
 * whose keyword appears in the item name wins (case-insensitive substring);
 * anything else falls back to the list's first category (the default).
 *
 * Runs entirely on-device — no network, no PII leaves the phone.
 */
object ItemCategorizer {
    fun categorize(itemName: String, categories: List<ItemCategory>): String {
        val lower = itemName.lowercase()
        return categories.firstOrNull { category ->
            category.keywords.any { keyword -> keyword.lowercase() in lower }
        }?.id ?: categories.firstOrNull()?.id ?: ItemCategory.DEFAULT_ID
    }
}

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
