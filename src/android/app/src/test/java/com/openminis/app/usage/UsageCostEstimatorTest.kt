package com.openminis.app.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCostEstimatorTest {
    @Test
    fun `million input is three dollars`() {
        assertEquals(3.0, UsageCostEstimator.estimateUsd(1_000_000, 0), 1e-9)
        assertEquals(15.0, UsageCostEstimator.estimateUsd(0, 1_000_000), 1e-9)
        assertEquals("$3.0000", UsageCostEstimator.formatUsd(3.0))
    }
}
