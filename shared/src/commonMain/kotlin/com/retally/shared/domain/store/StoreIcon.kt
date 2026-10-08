package com.retally.shared.domain.store

import kotlinx.serialization.Serializable

/**
 * A store's logo, pushed from the server (`getStoreIcons`). The [key] is the
 * normalized store identity (see [iconKeyOf]) and doubles as the Firestore
 * document id; [iconUrl] is a Firebase Storage download URL.
 */
@Serializable
data class StoreIcon(
    val key: String,
    val name: String,
    val iconUrl: String,
)

/**
 * Normalizes an OCR store name to the icon key used as the Firestore document
 * id. Strips store numbers ("#2813"), punctuation, and generic suffix words so
 * messy receipt headers still match:
 *
 *   "WALMART SUPERCENTER #2813" -> "walmart"
 *   "Costco Wholesale"           -> "costco"
 *   "Trader Joe's"               -> "traderjoes"
 *   "7-Eleven"                   -> "7eleven"
 *   "Stop & Shop"                -> "stopshop"
 */
fun iconKeyOf(storeName: String): String {
    val suffixes = setOf(
        "supercenter", "supermarket", "wholesale", "market", "markets",
        "store", "stores", "grocery", "groceries", "foods", "food",
    )
    return storeName.lowercase()
        .replace(Regex("#\\d+"), " ")
        .replace(Regex("[^a-z0-9 ]"), "")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it !in suffixes }
        .joinToString("")
        .ifEmpty { "unknown" }
}
