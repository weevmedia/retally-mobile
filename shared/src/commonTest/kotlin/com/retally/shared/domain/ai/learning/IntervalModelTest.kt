package com.retally.shared.domain.ai.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntervalModelTest {

    private val model = IntervalModel(priorWeight = 1.0, priorDays = 7.0)

    @Test
    fun priorTempersThinHistory() {
        // Gaps of 10, 10 with the weak 7-day prior -> predicts every 9 days,
        // not a naive 10-day median. Bought 10 days ago -> 1 day overdue.
        val belief = model.train(listOf(10.0, 10.0))
        assertEquals(9.0, belief.predictedDays, 0.001)
        assertEquals(2, belief.observations)
        assertEquals(1, model.overdueDays(belief, 10.0))
    }

    @Test
    fun noHistoryFallsBackToPrior() {
        val belief = model.train(emptyList())
        assertEquals(7.0, belief.predictedDays, 0.001)
        assertEquals(0, belief.observations)
    }

    @Test
    fun dataDominatesWithHistory() {
        // 10 observations of 30-day gaps: prediction converges near 30.
        val belief = model.train(List(10) { 30.0 })
        assertTrue(belief.predictedDays > 27.0)
        assertEquals(0, model.overdueDays(belief, 20.0))
        assertTrue(model.overdueDays(belief, 40.0) > 0)
    }
}
