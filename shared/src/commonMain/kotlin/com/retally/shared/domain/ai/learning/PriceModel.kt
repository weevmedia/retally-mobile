package com.retally.shared.domain.ai.learning

import kotlin.math.sqrt

/**
 * Online price learner for one item (Welford's algorithm): learns the running mean
 * and spread of the prices the user has paid. A new price is a spike when it lands
 * far outside the learned band — z-score >= [zThreshold] — or >= 20% above the mean
 * when the price has never varied (spread ~ 0, e.g. only two observations so far).
 *
 * Trained on the prices *before* the latest purchase; the latest price is the
 * candidate being judged, never part of its own baseline.
 */
object PriceModel {
    /** The trained state for one item. */
    data class Belief(val mean: Double, val sd: Double, val observations: Int)

    fun train(priorPrices: List<Double>): Belief {
        var mean = 0.0
        var m2 = 0.0
        priorPrices.forEachIndexed { i, p ->
            val delta = p - mean
            mean += delta / (i + 1)
            m2 += delta * (p - mean)
        }
        val variance = if (priorPrices.size > 1) m2 / (priorPrices.size - 1) else 0.0
        return Belief(mean = mean, sd = sqrt(variance), observations = priorPrices.size)
    }

    /**
     * Spike percent vs the learned mean when [lastPrice] is anomalous, else null.
     * Needs at least 2 prior prices for a trustworthy band.
     */
    fun spikePercent(belief: Belief, lastPrice: Double, zThreshold: Double = 2.0): Int? {
        if (belief.observations < 2 || belief.mean <= 0.0) return null
        val anomalous = if (belief.sd < belief.mean * 0.01) {
            lastPrice >= belief.mean * 1.2
        } else {
            (lastPrice - belief.mean) / belief.sd >= zThreshold
        }
        if (!anomalous) return null
        return ((lastPrice - belief.mean) / belief.mean * 100).toInt()
    }
}
