package com.retally.shared.domain.ai.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PriceModelTest {

    @Test
    fun flatHistoryFallsBackToRelativeRule() {
        // Prices never varied: 4.00 is >= 20% above the learned 3.00 mean.
        val belief = PriceModel.train(listOf(3.0, 3.0))
        assertEquals(3.0, belief.mean, 0.001)
        assertEquals(0.0, belief.sd, 0.001)
        assertEquals(33, PriceModel.spikePercent(belief, 4.0))
        assertNull(PriceModel.spikePercent(belief, 3.1))
    }

    @Test
    fun variedHistoryUsesZScore() {
        // mean 3.0, sd ~1.41: 6.00 is z ~ 2.1 (spike), 5.00 is z ~ 1.4 (normal).
        val belief = PriceModel.train(listOf(2.0, 4.0))
        assertEquals(100, PriceModel.spikePercent(belief, 6.0))
        assertNull(PriceModel.spikePercent(belief, 5.0))
    }

    @Test
    fun singleObservationIsNotTrustworthy() {
        val belief = PriceModel.train(listOf(3.0))
        assertNull(PriceModel.spikePercent(belief, 9.0))
    }
}
