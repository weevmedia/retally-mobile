package com.retally.shared.domain.shopping

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * One suggestion on a store's AI shopping list (built server-side by `getShoppingList`).
 * [usualStore] is set only for "grab here too" items: where the shopper normally buys it.
 */
@Serializable
data class SuggestedItem(
    val item: String,
    val reason: String = "",
    val confidence: Double = 0.0,
    val usualStore: String? = null,
)

/** The AI shopping list for one store. Mirrors the `ShoppingListDto` the Cloud Function returns. */
@Serializable
data class ShoppingList(
    val insightId: String,
    val storeKey: String,
    val storeName: String,
    val dueHere: List<SuggestedItem> = emptyList(),
    val alsoHere: List<SuggestedItem> = emptyList(),
    val frequent: List<SuggestedItem> = emptyList(),
    val usualShoppingDay: String? = null,
    /** "gemini" or "fallback". */
    val decidedBy: String = "gemini",
    /** ISO-8601 instant the server built this list. */
    val generatedAt: String = "",
)

/** Outcome of asking the backend for lists. Never thrown — failures are values. */
sealed interface ShoppingListLoadResult {
    data class Success(val lists: List<ShoppingList>, val noHistory: Boolean) : ShoppingListLoadResult
    /** Guest (anonymous) user: the AI list needs a signed-in account. */
    data object SignInRequired : ShoppingListLoadResult
    data class Failure(val reason: String) : ShoppingListLoadResult
}

/**
 * AI shopping lists plus the shopper's on-device edits (checked, dismissed, added items).
 * Lists are cached on the device so the screen opens instantly and the store geofence
 * notification can show real items while the app is closed or offline.
 */
interface ShoppingListRepository {
    /** Last lists from the server (cached), newest first. */
    val lists: StateFlow<List<ShoppingList>>

    /**
     * Asks the backend for lists. [refresh] = true rebuilds them now; otherwise the server
     * returns its saved lists and only rebuilds when they are missing or stale.
     */
    suspend fun load(storeName: String? = null, refresh: Boolean = false): ShoppingListLoadResult

    /** Cached list for a store name as printed on receipts / geofences; null when none. */
    fun cachedListFor(storeName: String): ShoppingList?

    fun checkedItems(storeKey: String): Set<String>
    fun setChecked(storeKey: String, item: String, checked: Boolean)
    /** Hides the item from this store's list ("not needed this time") until the list changes. */
    fun dismiss(storeKey: String, item: String)
    fun dismissedItems(storeKey: String): Set<String>
    /** Items the shopper added by hand or from staples, in insertion order. */
    fun addedItems(storeKey: String): List<String>
    fun addItem(storeKey: String, item: String)
    /** "Done shopping": clears checked items and the checked hand-added items. */
    fun finishShopping(storeKey: String)

    /**
     * Store to open the list on, set when the shopper taps a "you're near {store}"
     * notification. The Shopping List screen consumes (and clears) it on appear.
     */
    var pendingStoreName: String?
}

/** Same normalization the receipt upload uses for `merchantNameNormalized` (and the backend). */
fun normalizeStoreName(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * Body text for the "you're near a store" notification, from the cached list.
 * Null when there is no list or nothing is due (the caller keeps its generic copy).
 */
fun ShoppingList.notificationSummary(dismissed: Set<String> = emptySet()): String? {
    val due = dueHere.map { it.item }.filterNot { it in dismissed }
    val also = alsoHere.map { it.item }.filterNot { it in dismissed }
    if (due.isEmpty() && also.isEmpty()) return null
    val shown = (due.ifEmpty { also }).take(3)
    val more = (due.ifEmpty { also }).size - shown.size
    val head = buildString {
        append(shown.joinToString(", "))
        if (more > 0) append(" + $more more")
    }
    return when {
        due.isNotEmpty() && also.isNotEmpty() ->
            "${due.size} due: $head · ${also.size} you usually get elsewhere"
        due.isNotEmpty() -> "${due.size} due: $head"
        else -> "Grab here too: $head"
    }
}
