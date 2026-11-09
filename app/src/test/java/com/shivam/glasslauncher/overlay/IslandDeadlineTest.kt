package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.island.IslandEngine.Companion.nextDeadline
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandDeadlineTest {
    private fun d(peeks: List<Long> = emptyList(), ringer: Long? = null, charging: Boolean = false, plugged: Long = 0, paused: Long? = null, demo: Long = 0) =
        nextDeadline(10_000, peeks, ringer, charging, plugged, paused, demo)

    @Test fun nothingTimedMeansNoWakeup() = assertEquals(Long.MAX_VALUE, d())
    @Test fun earliestPeekWins() = assertEquals(12_000L, d(peeks = listOf(15_000, 12_000)))
    @Test fun expiredDeadlinesIgnored() = assertEquals(Long.MAX_VALUE, d(peeks = listOf(9_000), ringer = 1_000, paused = -60_000, demo = 5_000))
    @Test fun ringerExpiresAfterItsWindow() = assertEquals(9_000L + IslandEngineConst.RINGER, d(ringer = 9_000))
    @Test fun chargingRefreshesEverySecond() = assertEquals(11_000L, d(charging = true, plugged = 9_000))
    @Test fun chargingEndsAtItsWindow() = assertEquals(9_500L + 6_000, nextDeadline(15_000, emptyList(), null, true, 9_500, null, 0))
    @Test fun pausedMusicExpires() = assertEquals(5_000L + 60_000, d(paused = 5_000))
}

private object IslandEngineConst { const val RINGER = 2200L }
