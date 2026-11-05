/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

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
        /** Bucket for receipts stored before categories existed (they decode to this). */
        const val DEFAULT_ID = "grocery"
        /** New items that no keyword matches, until the server's AI pass places them. */
        const val UNCATEGORIZED_ID = "uncategorized"

        /**
         * Used before the first successful fetch and when offline with no cache. Mirrors the
         * server's AI categories (functions/src/categories), so every id it returns has a name.
         */
        fun bundled(): List<ItemCategory> = listOf(
            ItemCategory(id = "grocery", name = "Grocery"),
            ItemCategory(id = "household", name = "Household"),
            ItemCategory(id = "personal_care", name = "Personal care"),
            ItemCategory(id = "health", name = "Health & pharmacy"),
            ItemCategory(id = "baby", name = "Baby"),
            ItemCategory(id = "pet", name = "Pet"),
            ItemCategory(id = "alcohol", name = "Alcohol"),
            ItemCategory(id = "electronics", name = "Electronics"),
            ItemCategory(id = "clothing", name = "Clothing"),
            ItemCategory(id = "home", name = "Home & garden"),
            ItemCategory(id = "other", name = "Other"),
            ItemCategory(id = UNCATEGORIZED_ID, name = "Uncategorized"),
        )
    }
}
