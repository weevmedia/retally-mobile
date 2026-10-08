package com.retally.shared.domain.ai.learning

import kotlin.math.roundToInt

/**
 * Online interval learner for one entity: an item's gaps between buys, or the gaps
 * between grocery trips. The learned parameter is the predicted interval, a posterior
 * mean blending a weak prior with the observed gaps:
 *
 *     predicted = (priorWeight * priorDays + sum(gaps)) / (priorWeight + n)
 *
 * With a handful of receipts the prior keeps predictions sane; as history grows the
 * user's data dominates. The receipt history is the training data: every new receipt
 * retrains the model.
 *
 * Pure functions: training is just arithmetic over the gap list, so the same code
 * runs on-device today and inside the hosted function later.
 */
class IntervalModel(
    private val priorWeight: Double = 1.0,
    private val priorDays: Double = 7.0,
) {
    /** The trained state: what the model believes, and how much data backs it. */
    data class Belief(val predictedDays: Double, val observations: Int)

    fun train(gaps: List<Double>): Belief {
        val n = gaps.size
        val predicted = (priorWeight * priorDays + gaps.sum()) / (priorWeight + n)
        return Belief(predictedDays = predicted, observations = n)
    }

    /** Whole days overdue vs the prediction; negative means not due yet. */
    fun overdueDays(belief: Belief, daysSince: Double): Int =
        (daysSince - belief.predictedDays).roundToInt()
}
