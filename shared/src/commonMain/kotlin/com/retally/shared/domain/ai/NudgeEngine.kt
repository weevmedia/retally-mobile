package com.retally.shared.domain.ai

import com.retally.shared.domain.ai.learning.IntervalModel
import com.retally.shared.domain.ai.learning.PriceModel
import com.retally.shared.domain.receipt.CrossReceiptDuplicateFinder
import com.retally.shared.domain.receipt.DuplicateMatcher
import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Nudge engine for the Home card — now backed by trained per-user models.
 *
 * Each generator trains a small online model from the receipt history (the training
 * data) and fires a [Nudge] when the model's prediction says something is due or
 * anomalous. The models are Bayesian-style with weak priors, so they behave sanely
 * on thin history and sharpen as the user scans more. [allNudges] ranks everything
 * that fired by priority; [topNudge] is the single card Home shows.
 *
 * Pure functions of the receipt list: deterministic, testable, no I/O. The same code
 * runs on-device today and inside the hosted function later.
 */
object NudgeEngine {

    /** Shared interval learner: weak 7-day prior, user's gaps take over with history. */
    private val intervalModel = IntervalModel(priorWeight = 1.0, priorDays = 7.0)

    /** Every nudge firing right now, highest priority first. */
    fun allNudges(receipts: List<Receipt>, now: Instant = Clock.System.now()): List<Nudge> =
        listOfNotNull(
            runningLow(receipts, now),
            priceSpike(receipts),
            groceryRunOverdue(receipts, now),
            doubleBought(receipts),
            savingsRecap(receipts, now),
        ).sortedByDescending { it.priority }

    /** The single nudge for the Home card, or null while history is too thin. */
    fun topNudge(receipts: List<Receipt>, now: Instant = Clock.System.now()): Nudge? =
        allNudges(receipts, now).firstOrNull()

    // ── Generators ──

    /**
     * Trains an interval model per item from its buy gaps; the most overdue
     * prediction wins. Needs buys on 2+ distinct days.
     */
    private fun runningLow(receipts: List<Receipt>, now: Instant): Nudge.RunningLow? {
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

        var best: Nudge.RunningLow? = null
        var bestOverdue = -1
        for (entries in byItem.values) {
            val days = entries.map { it.first }.distinct().sorted()
            if (days.size < 2) continue
            val gaps = days.zipWithNext { a, b -> (b.toEpochDays() - a.toEpochDays()).toDouble() }
            val belief = intervalModel.train(gaps)
            val daysSince = (today.toEpochDays() - days.last().toEpochDays()).toDouble()
            val overdue = intervalModel.overdueDays(belief, daysSince)
            if (overdue >= 0 && overdue > bestOverdue) {
                bestOverdue = overdue
                best = Nudge.RunningLow(
                    itemName = entries.maxBy { it.first }.second,
                    intervalDays = belief.predictedDays.roundToInt(),
                    overdueDays = overdue,
                    priority = 20 + overdue.coerceAtMost(20),
                )
            }
        }
        return best
    }

    /**
     * Trains a price model per item on the prices before the latest purchase; fires
     * when the latest price is anomalous vs the learned band. Needs 3+ purchases.
     */
    private fun priceSpike(receipts: List<Receipt>): Nudge.PriceSpike? {
        if (receipts.isEmpty()) return null
        val byItem = mutableMapOf<String, MutableList<PricePoint>>()
        for (receipt in receipts) {
            for (item in receipt.items) {
                val key = DuplicateMatcher.normalize(item.name)
                if (key.isEmpty()) continue
                byItem.getOrPut(key) { mutableListOf() }
                    .add(PricePoint(receipt.purchaseDate, item.price, item.name.trim()))
            }
        }
        var best: Nudge.PriceSpike? = null
        for (points in byItem.values) {
            val byDate = points.sortedBy { it.at }
            if (byDate.size < 3) continue
            val belief = PriceModel.train(byDate.dropLast(1).map { it.price })
            val last = byDate.last()
            val spikePct = PriceModel.spikePercent(belief, last.price) ?: continue
            val candidate = Nudge.PriceSpike(
                itemName = last.displayName,
                lastPrice = last.price,
                usualPrice = belief.mean,
                priority = 30 + spikePct.coerceAtMost(20),
            )
            if (best == null || candidate.priority > best.priority) best = candidate
        }
        return best
    }

    private data class PricePoint(val at: Instant, val price: Double, val displayName: String)

    /**
     * Trains the interval model on the gaps between shopping trips; fires when the
     * learned trip cadence says a run is overdue. Needs 3+ distinct trip days.
     */
    private fun groceryRunOverdue(receipts: List<Receipt>, now: Instant): Nudge.GroceryRunOverdue? {
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date
        val tripDays = receipts
            .map { it.purchaseDate.toLocalDateTime(zone).date }
            .distinct()
            .sorted()
        if (tripDays.size < 3) return null
        val gaps = tripDays.zipWithNext { a, b -> (b.toEpochDays() - a.toEpochDays()).toDouble() }
        val belief = intervalModel.train(gaps)
        val daysSince = (today.toEpochDays() - tripDays.last().toEpochDays()).toDouble()
        val overdue = intervalModel.overdueDays(belief, daysSince)
        if (overdue < 1) return null
        return Nudge.GroceryRunOverdue(
            daysSinceLastTrip = daysSince.roundToInt(),
            usualIntervalDays = belief.predictedDays.roundToInt(),
            priority = 15 + overdue.coerceAtMost(15),
        )
    }

    /**
     * Same item (same matcher rule as the Duplicates tab) on 2+ receipts within a week.
     * Reuses [CrossReceiptDuplicateFinder] so "Keep both" resolutions are respected.
     */
    private val doubleBoughtFinder = CrossReceiptDuplicateFinder(window = 7.days)

    private fun doubleBought(receipts: List<Receipt>): Nudge.DoubleBought? {
        val top = doubleBoughtFinder.find(receipts).maxByOrNull { it.count } ?: return null
        return Nudge.DoubleBought(
            itemName = top.name.trim(),
            receiptCount = top.occurrences.map { it.receiptId }.distinct().size,
            priority = 40,
        )
    }

    /**
     * This month's spend is >= 15% off the average of the previous full months.
     * Needs 2+ prior months so "usual" isn't a single data point.
     */
    private fun savingsRecap(receipts: List<Receipt>, now: Instant): Nudge.SavingsRecap? {
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date
        val thisMonth = today.year * 12 + today.monthNumber
        val spendByMonth = receipts.groupBy {
            val d = it.purchaseDate.toLocalDateTime(zone).date
            d.year * 12 + d.monthNumber
        }.mapValues { (_, rs) -> rs.sumOf { it.total ?: 0.0 } }
        val monthToDate = spendByMonth[thisMonth] ?: 0.0
        val prior = spendByMonth.entries
            .filter { it.key < thisMonth }
            .sortedByDescending { it.key }
            .take(3)
            .map { it.value }
        if (prior.size < 2) return null
        val usual = prior.average()
        if (usual <= 0.0) return null
        if (kotlin.math.abs(monthToDate - usual) / usual < 0.15) return null
        return Nudge.SavingsRecap(
            monthToDate = monthToDate,
            usualMonthly = usual,
            priority = 5,
        )
    }
}
