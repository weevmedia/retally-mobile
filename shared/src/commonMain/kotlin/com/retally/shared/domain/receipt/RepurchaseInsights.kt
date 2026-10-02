package com.retally.shared.domain.receipt

import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** One repurchase prediction for the Home AI card: what the shopper buys and how often. */
data class RepurchaseInsight(
    /** Item name as printed on the most recent receipt. */
    val itemName: String,
    /** Median days between purchases. */
    val intervalDays: Int,
    /** Days past the median interval (0 = due today); drives nudge priority. */
    val overdueDays: Int,
)

/**
 * On-device repurchase predictions from receipt history. Every item bought on at least two
 * different days gets a median buy interval (names match under [DuplicateMatcher]'s
 * normalisation, so "MILK" and "milk" merge); an item is "due" when the days since its
 * last purchase reach that interval. Returns the most overdue one, or null when nothing
 * is due yet.
 */
object RepurchaseInsights {

    fun mostOverdue(receipts: List<Receipt>, now: Instant = Clock.System.now()): RepurchaseInsight? {
        if (receipts.isEmpty()) return null
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date

        val byItem = mutableMapOf<String, MutableList<Pair<LocalDate, String>>>()
        for (receipt in receipts) {
            val day = receipt.purchaseDate.toLocalDateTime(zone).date
            for (item in receipt.items) {
                val name = item.name.trim()
                val key = DuplicateMatcher.normalize(name)
                if (key.isEmpty()) continue
                byItem.getOrPut(key) { mutableListOf() }.add(day to name)
            }
        }

        var best: RepurchaseInsight? = null
        var bestOverdueDays = -1
        for (entries in byItem.values) {
            val days = entries.map { it.first }.distinct().sorted()
            if (days.size < 2) continue
            val intervals = days.zipWithNext { a, b -> b.toEpochDays() - a.toEpochDays() }.sorted()
            val median = intervals[intervals.size / 2].toInt()
            if (median < 1) continue
            val overdue = (today.toEpochDays() - days.last().toEpochDays()).toInt() - median
            if (overdue >= 0 && overdue > bestOverdueDays) {
                bestOverdueDays = overdue
                best = RepurchaseInsight(
                    itemName = entries.maxBy { it.first }.second,
                    intervalDays = median,
                    overdueDays = overdue,
                )
            }
        }
        return best
    }
}
