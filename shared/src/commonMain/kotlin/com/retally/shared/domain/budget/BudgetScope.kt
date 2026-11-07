/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.budget

import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.serialization.Serializable

/**
 * What a month's budget tracks: every dollar on the receipts ([allSpending], receipt totals,
 * tax included) or grocery items only (item prices; tax and fees aren't items, so they don't
 * count). The UI offers just those two; [categoryIds] stays a set so more could come back later.
 * A budget that was never given a scope tracks all spending ([Default]); [GroceryOnly] is the
 * opt-in.
 */
@Serializable
data class BudgetScope(
    val allSpending: Boolean = false,
    val categoryIds: Set<String> = setOf(ItemCategory.DEFAULT_ID),
) {
    /** What [receipt] contributes to this budget. */
    fun spentOn(receipt: Receipt): Double =
        if (allSpending) {
            receipt.total ?: 0.0
        } else {
            receipt.items.filter { it.categoryId in categoryIds }.sumOf { it.price }
        }

    fun spentIn(receipts: List<Receipt>): Double = receipts.sumOf { spentOn(it) }

    /** Items in [receipts] still "Uncategorized" — they can't count toward a category budget yet. */
    fun uncategorizedIn(receipts: List<Receipt>): Int =
        if (allSpending) 0 else receipts.sumOf { r -> r.items.count { it.categoryId == ItemCategory.UNCATEGORIZED_ID } }

    /** "All spending", "Grocery", "Grocery + Household", "Grocery + 2 more". */
    fun label(categories: List<ItemCategory>, allLabel: String): String {
        if (allSpending) return allLabel
        val names = categories.filter { it.id in categoryIds }.map { it.name }
            .ifEmpty { categoryIds.toList() }
        return when {
            names.size <= 2 -> names.joinToString(" + ")
            else -> "${names.first()} + ${names.size - 1} more"
        }
    }

    companion object {
        /** All spending — what a budget tracks until the user picks otherwise. */
        val Default = BudgetScope(allSpending = true)
        /** Grocery items only. */
        val GroceryOnly = BudgetScope()
    }
}
