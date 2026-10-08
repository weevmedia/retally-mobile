package com.retally.shared.domain.receipt

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** One purchase of a repeated item: which receipt, which line, when. */
data class DuplicateOccurrence(
    val receiptId: String,
    val storeName: String,
    val purchaseDate: Instant,
    val item: ReceiptItem,
)

/**
 * The same item bought on more than one receipt inside the window — e.g. milk on Sep 12
 * and again on Sep 15. [occurrences] are oldest first. Unlike an in-receipt duplicate this
 * is often legitimate, which is why the UI never removes anything without a confirmation.
 */
data class CrossReceiptDuplicate(
    val name: String,
    val price: Double,
    val occurrences: List<DuplicateOccurrence>,
) {
    /**
     * Stable id for Keep both / Remove one. Mirrors [DuplicateMatcher]: the item number
     * when the lines have one, otherwise the normalised name — so two same-named lines
     * with different item numbers stay two separate groups.
     */
    val key: String
        get() {
            val number = occurrences.firstOrNull()?.item?.itemNumber?.let(DuplicateMatcher::normalizeItemNumber).orEmpty()
            return (if (number.isNotEmpty()) "$number|" else "") + DuplicateMatcher.normalize(name) + "|" + price
        }
    val latest: DuplicateOccurrence get() = occurrences.last()
    val earliest: DuplicateOccurrence get() = occurrences.first()
    val count: Int get() = occurrences.size
}

/**
 * Finds items that repeat **across** receipts (the in-receipt case is [DuplicateMatcher]).
 * Two lines match under the same rule — item number first when both lines have one,
 * otherwise near-identical name, always at the same price — but must sit on different
 * receipts whose purchase dates are within [window] of each other. Lines the shopper
 * already resolved with "Keep both" ([ReceiptItem.keptAcrossReceipts]) are skipped.
 */
class CrossReceiptDuplicateFinder(
    private val window: Duration = 30.days,
) {
    fun find(receipts: List<Receipt>): List<CrossReceiptDuplicate> {
        val occurrences = receipts
            .sortedBy { it.purchaseDate }
            .flatMap { receipt ->
                receipt.items
                    .filterNot { it.keptAcrossReceipts }
                    .map { DuplicateOccurrence(receipt.id, receipt.storeName, receipt.purchaseDate, it) }
            }

        // Greedy grouping: each occurrence joins the first group whose representative it matches.
        val groups = mutableListOf<MutableList<DuplicateOccurrence>>()
        for (occ in occurrences) {
            val group = groups.firstOrNull { g -> DuplicateMatcher.isSameItem(g.first().item, occ.item) }
            if (group != null) group += occ else groups += mutableListOf(occ)
        }

        return groups
            .map { group -> group.filter { occ -> group.any { other -> other.receiptId != occ.receiptId && withinWindow(other, occ) } } }
            .filter { group -> group.map { it.receiptId }.distinct().size > 1 }
            .map { group ->
                CrossReceiptDuplicate(name = group.first().item.name, price = group.first().item.price, occurrences = group)
            }
            .sortedByDescending { it.latest.purchaseDate }
    }

    private fun withinWindow(a: DuplicateOccurrence, b: DuplicateOccurrence): Boolean {
        val diff = a.purchaseDate - b.purchaseDate
        return diff.absoluteValue <= window
    }
}
