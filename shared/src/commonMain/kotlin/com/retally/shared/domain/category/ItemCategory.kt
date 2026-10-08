package com.retally.shared.domain.category

import kotlinx.serialization.Serializable

/**
 * An item spending category, pushed from the server (`getCategories`) so the list
 * can evolve without an app release. The app caches the last fetched list and
 * falls back to [bundled] when offline or before the first fetch.
 *
 * The FIRST category in the list is the default: items matching no keyword land
 * there.
 */
@Serializable
data class ItemCategory(
    val id: String,
    val name: String,
    val keywords: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_ID = "grocery"

        /** Used before the first successful fetch and when offline with no cache. */
        fun bundled(): List<ItemCategory> = listOf(
            ItemCategory(id = "grocery", name = "Grocery"),
            ItemCategory(id = "misc", name = "Miscellaneous"),
        )
    }
}
