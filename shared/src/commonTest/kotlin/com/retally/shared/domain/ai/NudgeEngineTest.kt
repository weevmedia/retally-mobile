package com.retally.shared.domain.ai

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class NudgeEngineTest {

    private val now = Instant.parse("2026-09-20T12:00:00Z")

    private fun receipt(
        id: String,
        daysAgo: Long,
        total: Double? = null,
        items: Array<Pair<String, Double>>,
    ): Receipt = Receipt(
        id = id,
        storeName = "Store",
        purchaseDate = now.minus(daysAgo.days),
        createdAt = now,
        receiptTotal = total,
        items = items.mapIndexed { i, (name, price) ->
            ReceiptItem(id = "$id-$i", name = name, price = price)
        },
    )

    @Test
    fun runningLowFiresForMostOverdueItem() {
        val receipts = listOf(
            receipt("r1", 30, items = arrayOf("Milk" to 1.0)),
            receipt("r2", 20, items = arrayOf("Milk" to 1.0)),
            receipt("r3", 10, items = arrayOf("Milk" to 1.0)),
        )
        // Gaps 10, 10 with the weak 7-day prior → predicts every 9 days;
        // last bought 10 days ago → 1 day overdue, priority 20 + 1.
        assertEquals(
            Nudge.RunningLow(itemName = "Milk", intervalDays = 9, overdueDays = 1, priority = 21),
            NudgeEngine.topNudge(receipts, now),
        )
    }

    @Test
    fun priceSpikeFiresAboveTwentyPercent() {
        val receipts = listOf(
            receipt("r1", 30, items = arrayOf("Eggs" to 3.0)),
            receipt("r2", 20, items = arrayOf("Eggs" to 3.0)),
            receipt("r3", 10, items = arrayOf("Eggs" to 4.0)),
        )
        // Usual 3.0, last 4.0 → +33%; outranks the also-firing running-low nudge.
        assertEquals(
            Nudge.PriceSpike(itemName = "Eggs", lastPrice = 4.0, usualPrice = 3.0, priority = 63),
            NudgeEngine.topNudge(receipts, now),
        )
    }

    @Test
    fun priceSpikeNeedsThreePurchases() {
        val receipts = listOf(
            receipt("r1", 10, items = arrayOf("Eggs" to 3.0)),
            receipt("r2", 5, items = arrayOf("Eggs" to 4.5)),
        )
        assertTrue(NudgeEngine.allNudges(receipts, now).none { it is Nudge.PriceSpike })
    }

    @Test
    fun groceryRunOverdueFires() {
        val receipts = listOf(
            receipt("r1", 21, items = arrayOf("A" to 1.0)),
            receipt("r2", 14, items = arrayOf("B" to 1.0)),
            receipt("r3", 8, items = arrayOf("C" to 1.0)),
        )
        // Trip intervals 7, 6 → usual 7; last trip 8 days ago → 1 day overdue.
        assertEquals(
            Nudge.GroceryRunOverdue(daysSinceLastTrip = 8, usualIntervalDays = 7, priority = 16),
            NudgeEngine.topNudge(receipts, now),
        )
    }

    @Test
    fun doubleBoughtFiresWithinAWeek() {
        val receipts = listOf(
            receipt("r1", 6, items = arrayOf("Bread" to 2.0)),
            receipt("r2", 2, items = arrayOf("Bread" to 2.0)),
        )
        assertEquals(
            Nudge.DoubleBought(itemName = "Bread", receiptCount = 2, priority = 40),
            NudgeEngine.topNudge(receipts, now),
        )
    }

    @Test
    fun highestPriorityNudgeWins() {
        val receipts = listOf(
            receipt("r1", 30, items = arrayOf("Milk" to 1.0)),
            receipt("r2", 20, items = arrayOf("Milk" to 1.0)),
            receipt("r3", 10, items = arrayOf("Milk" to 1.0)),
            receipt("r4", 6, items = arrayOf("Bread" to 2.0)),
            receipt("r5", 2, items = arrayOf("Bread" to 2.0)),
        )
        val ranked = NudgeEngine.allNudges(receipts, now)
        // Double-bought (40) outranks running low (21).
        assertTrue(ranked[0] is Nudge.DoubleBought)
        assertTrue(ranked[1] is Nudge.RunningLow)
        assertEquals(ranked[0], NudgeEngine.topNudge(receipts, now))
    }

    @Test
    fun savingsRecapFiresWhenMonthDeviates() {
        val receipts = listOf(
            receipt("r1", 60, total = 100.0, items = arrayOf("Sofa" to 100.0)),
            receipt("r2", 40, total = 100.0, items = arrayOf("Chair" to 100.0)),
            receipt("r3", 5, total = 150.0, items = arrayOf("Table" to 150.0)),
        )
        // Sep so far 150 vs usual 100 → +50%.
        assertEquals(
            Nudge.SavingsRecap(monthToDate = 150.0, usualMonthly = 100.0, priority = 5),
            NudgeEngine.topNudge(receipts, now),
        )
    }

    @Test
    fun emptyHistoryReturnsNull() {
        assertNull(NudgeEngine.topNudge(emptyList(), now))
    }
}
