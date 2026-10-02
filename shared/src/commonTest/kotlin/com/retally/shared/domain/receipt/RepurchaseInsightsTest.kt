package com.retally.shared.domain.receipt

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RepurchaseInsightsTest {

    private val now = Instant.parse("2026-09-20T12:00:00Z")

    private fun receipt(id: String, daysAgo: Long, vararg items: String): Receipt =
        Receipt(
            id = id,
            storeName = "Store",
            purchaseDate = now.minus(daysAgo.days),
            createdAt = now,
            items = items.mapIndexed { i, name ->
                ReceiptItem(id = "$id-$i", name = name, price = 1.0)
            },
        )

    @Test
    fun dueItemReportedWithMedianInterval() {
        val receipts = listOf(
            receipt("r1", 19, "Milk"),
            receipt("r2", 13, "Milk"),
            receipt("r3", 7, "Milk"),
        )
        // Intervals 6, 6 → median 6; last bought 7 days ago → overdue by 1.
        assertEquals(
            RepurchaseInsight(itemName = "Milk", intervalDays = 6, overdueDays = 1),
            RepurchaseInsights.mostOverdue(receipts, now),
        )
    }

    @Test
    fun notYetDueReturnsNull() {
        val receipts = listOf(
            receipt("r1", 9, "Milk"),
            receipt("r2", 3, "Milk"),
        )
        // Median 6 but bought 3 days ago → not due.
        assertNull(RepurchaseInsights.mostOverdue(receipts, now))
    }

    @Test
    fun singlePurchaseNeverDue() {
        assertNull(RepurchaseInsights.mostOverdue(listOf(receipt("r1", 30, "Milk")), now))
    }

    @Test
    fun namesMatchCaseInsensitively() {
        val receipts = listOf(
            receipt("r1", 13, "MILK"),
            receipt("r2", 7, "milk"),
        )
        assertEquals(
            RepurchaseInsight(itemName = "milk", intervalDays = 6, overdueDays = 1),
            RepurchaseInsights.mostOverdue(receipts, now),
        )
    }

    @Test
    fun mostOverdueItemWins() {
        val receipts = listOf(
            receipt("r1", 19, "Milk"),
            receipt("r2", 13, "Milk"),
            receipt("r3", 7, "Milk"),
            receipt("r4", 10, "Bread"),
            receipt("r5", 8, "Bread"),
        )
        // Milk: median 6, overdue 1. Bread: median 2, overdue 6.
        assertEquals(
            RepurchaseInsight(itemName = "Bread", intervalDays = 2, overdueDays = 6),
            RepurchaseInsights.mostOverdue(receipts, now),
        )
    }

    @Test
    fun emptyHistoryReturnsNull() {
        assertNull(RepurchaseInsights.mostOverdue(emptyList(), now))
    }
}
