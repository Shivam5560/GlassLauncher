package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.island.ChargingEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingEstimateTest {

    @Test
    fun microampsTimesMillivolts() {
        // 3 A at 4.2 V = 12.6 W
        assertEquals(12.6f, ChargingEstimate.watts(3_000_000L, 4200)!!, 0.01f)
    }

    @Test
    fun signDoesNotMatter() {
        // Several vendors (including Xiaomi) report charging current as negative.
        assertEquals(12.6f, ChargingEstimate.watts(-3_000_000L, 4200)!!, 0.01f)
    }

    @Test
    fun zeroCurrentHidesWatts() {
        assertNull(ChargingEstimate.watts(0L, 4200))
    }

    @Test
    fun unsupportedValuesHideWatts() {
        assertNull(ChargingEstimate.watts(Long.MIN_VALUE, 4200))
        assertNull(ChargingEstimate.watts(2_000_000L, Int.MIN_VALUE))
        assertNull(ChargingEstimate.watts(2_000_000L, 0))
    }

    @Test
    fun milliampDevicesAreDetected() {
        // 2500 "µA" at 4 V would be 0.01 W; these devices actually mean 2500 mA = 10 W.
        assertEquals(10f, ChargingEstimate.watts(2500L, 4000)!!, 0.01f)
    }

    @Test
    fun trickleCurrentNearFullIsNotMistakenForMilliamps() {
        // 5 mA trickle at 99 % is real: far below the display threshold, so hidden.
        assertNull(ChargingEstimate.watts(5000L, 4400, levelPercent = 99))
    }

    @Test
    fun voltageUnitsAreNormalised() {
        assertEquals(4200, ChargingEstimate.voltageMillivolts(4200))
        assertEquals(4000, ChargingEstimate.voltageMillivolts(4))
        assertEquals(4200, ChargingEstimate.voltageMillivolts(4_200_000))
        assertEquals(0, ChargingEstimate.voltageMillivolts(-1))
    }

    @Test
    fun fastChargingIsPlausible() {
        // Dual-cell fast charging: 11 A at 8.4 V ≈ 92 W.
        val w = ChargingEstimate.watts(11_000_000L, 8400)
        assertNotNull(w)
        assertEquals(92.4f, w!!, 0.05f)
    }

    @Test
    fun implausibleReadingsAreHidden() {
        assertNull(ChargingEstimate.watts(400_000_000L, 4200))
    }

    @Test
    fun formatting() {
        assertEquals("≈ 4.2 W", ChargingEstimate.format(4.2f))
        assertEquals("≈ 33 W", ChargingEstimate.format(33.4f))
    }
}
