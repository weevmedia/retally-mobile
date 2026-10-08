package com.retally.shared.domain.category

import kotlinx.coroutines.flow.StateFlow

/**
 * The current item spending categories. Implemented by [CategoryRepository]
 * (server-pushed, cached); tests use a fake.
 */
interface CategorySource {
    val categories: StateFlow<List<ItemCategory>>
}
