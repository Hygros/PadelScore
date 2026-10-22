package ch.hygro.padelscore.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepDeltaAccumulatorTest {
    @Test fun firstReadingIsBaselineNotDailySteps() {
        val counter = StepDeltaAccumulator()
        assertEquals(0L, counter.accept(50000L))
        assertEquals(12L, counter.accept(50012L))
    }
    @Test fun resetNeverAddsSinceBootValue() {
        val counter = StepDeltaAccumulator(20L)
        counter.accept(1000L)
        assertEquals(20L, counter.accept(3L))
        assertEquals(24L, counter.accept(7L))
    }
    @Test fun resumedSegmentPreservesTotalWithoutCountingGap() {
        val counter = StepDeltaAccumulator(42L)
        assertEquals(42L, counter.accept(90000L))
        assertEquals(43L, counter.accept(90001L))
        assertNull(counter.accept(-1L))
    }
}
