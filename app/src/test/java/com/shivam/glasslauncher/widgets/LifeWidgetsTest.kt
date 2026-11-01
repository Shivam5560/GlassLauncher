package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class LifeWidgetsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun ev(begin: Long, end: Long, allDay: Boolean) = CalendarEvent(1, "x", begin, end, allDay, 0, null)

    @Test fun durations() {
        assertEquals("45m", durationLabel(45 * 60_000L))
        assertEquals("3h 12m", durationLabel((3 * 60 + 12) * 60_000L))
    }

    @Test fun dawnRisesOverTwelveHours() {
        assertEquals(0f, dawnProgress(13 * 3600_000L))
        assertEquals(0.5f, dawnProgress(6 * 3600_000L))
        assertEquals(1f, dawnProgress(0))
    }

    @Test fun allDayEventsUseTheirUtcDate() {
        // A true all-day event on 1 Nov is stored as 00:00-24:00 UTC; it must not spill into 2 Nov in IST.
        val start = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val e = ev(start, start + 86_400_000L, allDay = true)
        assertTrue(isAllDay(e))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 1), allDayDates(e, ist))
    }

    @Test fun localMidnightBlocksCountAsAllDay() {
        val start = LocalDate.of(2026, 11, 1).atStartOfDay(ist).toInstant().toEpochMilli()
        val e = ev(start, start + 86_400_000L, allDay = false)
        assertTrue(isAllDay(e))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 1), allDayDates(e, ist))
    }
}

class ForegroundTimeTest {
    private fun ev(pkg: String, screen: String, type: Int, t: Long) = UsageEv(pkg, screen, type, t)
    private val R = UsageType.RESUMED; private val P = UsageType.PAUSED; private val S = UsageType.STOPPED

    @Test fun countsLikeDigitalWellbeing() {
        val t = foregroundTime(listOf(
            ev("a", "A", P, 1500),                                        // in front since midnight
            ev("b", "B1", R, 2000), ev("b", "B1", P, 2500), ev("b", "B2", R, 2500),
            ev("b", "B1", S, 2600),                                       // old screen stops: b still in front
            ev("b", "B2", P, 3000), ev("b", "B2", S, 3100),
            ev("z", "Z", R, 4000),                                        // never paused...
            ev("y", "Y", R, 5000), ev("y", "Y", P, 6000),                 // ...ends when y comes forward
            ev("c", "C", R, 9000),                                        // still open: counts to now
        ), start = 1000L, now = 10_000L)
        assertEquals(500L, t["a"])
        assertEquals(1000L, t["b"])
        assertEquals(1000L, t["z"])
        assertEquals(1000L, t["y"])
        assertEquals(1000L, t["c"])
    }

    @Test fun shutdownEndsTheApp() {
        val t = foregroundTime(listOf(ev("a", "A", R, 1000), ev("", "", UsageType.SHUTDOWN, 2000)), start = 0L, now = 10_000L)
        assertEquals(1000L, t["a"])
    }
}
