package com.shivam.glasslauncher.overlay.island

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Charging power estimated as current × voltage (docs/CONSTRAINTS.md: an estimate, and some
 * devices report 0, in which case nothing is shown).
 *
 * Inputs are the raw values Android hands out: BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
 * (documented as µA; its sign differs between vendors, and a few devices report mA) and
 * BatteryManager.EXTRA_VOLTAGE (documented as mV; a few devices report V or µV).
 */
object ChargingEstimate {

    /** Values BatteryManager returns when a property is unsupported. */
    private const val UNSUPPORTED_INT = Int.MIN_VALUE
    private const val UNSUPPORTED_LONG = Long.MIN_VALUE
    private const val MAX_PLAUSIBLE_WATTS = 300.0
    private const val MIN_SHOWN_WATTS = 0.1

    /** Normalises a voltage reading to millivolts, or 0 when unknown. */
    fun voltageMillivolts(raw: Int): Int = when {
        raw == UNSUPPORTED_INT || raw <= 0 -> 0
        raw < 100 -> raw * 1000          // reported in volts
        raw > 100_000 -> raw / 1000       // reported in microvolts
        else -> raw
    }

    /**
     * Current in amps (absolute). Microamps by default; a reading in 100..15 000 that would mean
     * under 0.1 W is treated as milliamps (devices that ignore the µA unit), except when the battery
     * is nearly full, where a few milliamps of trickle current is real.
     */
    fun currentAmps(raw: Long, voltageMv: Int, levelPercent: Int = 50): Double {
        if (raw == UNSUPPORTED_LONG || raw == 0L) return 0.0
        val magnitude = abs(raw)
        val asMicro = magnitude / 1_000_000.0
        val volts = voltageMv / 1000.0
        val looksLikeMilli = magnitude in 100L..15_000L && asMicro * volts < MIN_SHOWN_WATTS && levelPercent < 98
        return if (looksLikeMilli) magnitude / 1000.0 else asMicro
    }

    /** Estimated watts, or null when the device reports 0 / nothing usable (then hide the number). */
    fun watts(currentRaw: Long, voltageRaw: Int, levelPercent: Int = 50): Float? {
        val mv = voltageMillivolts(voltageRaw)
        if (mv == 0) return null
        val amps = currentAmps(currentRaw, mv, levelPercent)
        if (amps == 0.0) return null
        val w = amps * mv / 1000.0
        if (w < MIN_SHOWN_WATTS || w > MAX_PLAUSIBLE_WATTS) return null
        return w.toFloat()
    }

    /** "≈ 4.2 W" below 10 W, "≈ 33 W" above. */
    fun format(watts: Float): String =
        if (watts < 10f) "≈ ${(watts * 10).roundToInt() / 10.0} W" else "≈ ${watts.roundToInt()} W"
}
