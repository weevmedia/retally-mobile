package com.retally.shared.domain.receipt

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class CrossReceiptDuplicateFinderTest {

    private val sep12 = Instant.parse("2026-09-12T10:00:00Z")
    private val sep15 = Instant.parse("2026-09-15T10:00:00Z")
    private val aug01 = Instant.parse("2026-08-01T10:00:00Z")

    private fun receipt(id: String, store: String, date: Instant, vararg items: ReceiptItem) =
        Receipt(id = id, storeName = store, purchaseDate = date, createdAt = date, items = items.toList())

    private fun item(id: String, name: String, price: Double, keptAcross: Boolean = false, itemNumber: String? = null) =
        ReceiptItem(id = id, itemNumber = itemNumber, name = name, price = price, keptAcrossReceipts = keptAcross)

    @Test
    fun milk_bought_on_two_receipts_within_the_window_is_reported_oldest_first() {
        val found = CrossReceiptDuplicateFinder(window = 30.days).find(
            listOf(
                receipt("r2", "Costco", sep15, item("b", "WHOLE MILK", 2.59), item("c", "Bananas", 1.19)),
                receipt("r1", "Costco", sep12, item("a", "Whole Milk", 2.59)),
            ),
        )
        assertEquals(1, found.size)
        val dup = found.single()
        assertEquals(2, dup.count)
        assertEquals(listOf("r1", "r2"), dup.occurrences.map { it.receiptId })
        assertEquals(sep15, dup.latest.purchaseDate)
    }

    @Test
    fun repeats_outside_the_window_and_same_receipt_repeats_are_not_cross_receipt_duplicates() {
        val found = CrossReceiptDuplicateFinder(window = 30.days).find(
            listOf(
                receipt("old", "Costco", aug01, item("a", "Whole Milk", 2.59)),
                receipt("r2", "Costco", sep15, item("b", "Whole Milk", 2.59), item("b2", "Whole Milk", 2.59)),
            ),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun kept_across_receipts_hides_the_group() {
        val found = CrossReceiptDuplicateFinder().find(
            listOf(
                receipt("r1", "Costco", sep12, item("a", "Whole Milk", 2.59, keptAcross = true)),
                receipt("r2", "Costco", sep15, item("b", "Whole Milk", 2.59, keptAcross = true)),
            ),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun different_prices_are_separate_groups() {
        val found = CrossReceiptDuplicateFinder().find(
            listOf(
                receipt("r1", "Costco", sep12, item("a", "Whole Milk", 2.59)),
                receipt("r2", "Costco", sep15, item("b", "Whole Milk", 2.99)),
            ),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun same_item_number_links_receipts_despite_ocr_name_drift() {
        val found = CrossReceiptDuplicateFinder(window = 30.days).find(
            listOf(
                receipt("r1", "Costco", sep12, item("a", "KS ALMONDS", 13.49, itemNumber = "284601")),
                receipt("r2", "Costco", sep15, item("b", "KS ALM0NDS", 13.49, itemNumber = "284601")),
            ),
        )
        assertEquals(1, found.size)
        assertEquals(2, found.single().count)
    }

    @Test
    fun different_item_numbers_are_not_a_repeat_despite_identical_names() {
        val found = CrossReceiptDuplicateFinder(window = 30.days).find(
            listOf(
                receipt("r1", "Costco", sep12, item("a", "KS ALMONDS", 13.49, itemNumber = "284601")),
                receipt("r2", "Costco", sep15, item("b", "KS ALMONDS", 13.49, itemNumber = "284602")),
            ),
        )
        assertTrue(found.isEmpty())
    }
}
