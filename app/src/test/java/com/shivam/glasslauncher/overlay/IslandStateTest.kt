package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.island.IslandActivity
import com.shivam.glasslauncher.overlay.island.IslandArbiter
import com.shivam.glasslauncher.overlay.island.IslandContent
import com.shivam.glasslauncher.overlay.island.IslandEvent
import com.shivam.glasslauncher.overlay.island.IslandKind
import com.shivam.glasslauncher.overlay.island.IslandReducer
import com.shivam.glasslauncher.overlay.island.IslandSlots
import com.shivam.glasslauncher.overlay.island.IslandState
import com.shivam.glasslauncher.overlay.island.RingerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rapid state changes, driven through the same reducer and arbiter the engine uses. */
class IslandStateTest {

    private fun music(since: Long = 1) = IslandActivity("music:spotify", IslandKind.Music, since, IslandContent.Music("Song", "Artist", true), "spotify")
    private fun ongoingCall(since: Long = 2) = IslandActivity("call:ongoing", IslandKind.OngoingCall, since, IslandContent.Call("Ana", false, since, "k1"), "dialer")
    private fun incomingCall(since: Long = 3) = IslandActivity("call:incoming", IslandKind.IncomingCall, since, IslandContent.Call("Ana", true, since, "k2"), "dialer")
    private fun timer(since: Long = 4) = IslandActivity("timer:1", IslandKind.Timer, since, IslandContent.Clock("Pasta", 0, true, false, "5:00"))
    private fun peek(since: Long = 5) = IslandActivity("peek:n1", IslandKind.Peek, since, IslandContent.Peek("Messages", "Mum", "Dinner?"), "sms", "n1")
    private fun charging(since: Long = 6) = IslandActivity("charging:$since", IslandKind.Charging, since, IslandContent.Charging(80, 12f))
    private fun ringer(since: Long = 7) = IslandActivity("ringer:$since", IslandKind.Ringer, since, IslandContent.Ringer(RingerMode.Silent))

    private fun sync(s: IslandState, vararg live: IslandActivity) = IslandReducer.reduce(s, IslandEvent.Sync(live.toList()))
    private fun dismiss(s: IslandState, id: String) = IslandReducer.reduce(s, IslandEvent.Dismiss(id))
    private fun slots(s: IslandState) = IslandArbiter.resolve(s.activities, dismissed = s.dismissed)
    private fun IslandSlots.mainKind() = primary?.kind
    private fun IslandSlots.splitKind() = secondary?.kind

    @Test
    fun callStartsWhileMusicPlaysThenMusicReturnsWhenItEnds() {
        var s = sync(IslandState(), music())
        assertEquals(IslandKind.Music, slots(s).mainKind())

        s = sync(s, music(), incomingCall())
        val during = slots(s)
        assertEquals(IslandKind.IncomingCall, during.mainKind())
        assertNull("an incoming call takes the whole island", during.secondary)

        s = sync(s, music())
        assertEquals(IslandKind.Music, slots(s).mainKind())
    }

    @Test
    fun ongoingCallWithMusicSplitsTheIsland() {
        val s = sync(IslandState(), music(), ongoingCall())
        assertEquals(IslandKind.OngoingCall, slots(s).mainKind())
        assertEquals(IslandKind.Music, slots(s).splitKind())
    }

    @Test
    fun musicStopsWhileCallIsActiveKeepsTheCall() {
        var s = sync(IslandState(), music(), ongoingCall())
        s = sync(s, ongoingCall())
        val out = slots(s)
        assertEquals(IslandKind.OngoingCall, out.mainKind())
        assertNull("no music left, so no split", out.secondary)
    }

    @Test
    fun notificationDuringAnIncomingCallDoesNotTakeTheIsland() {
        val s = sync(IslandState(), incomingCall(), peek())
        val out = slots(s)
        assertEquals("incoming call (100) beats peek (85)", IslandKind.IncomingCall, out.mainKind())
        assertNull(out.secondary)
    }

    @Test
    fun notificationDuringAnOngoingCallShowsThePeekAndKeepsTheCallInSplit() {
        val s = sync(IslandState(), ongoingCall(), peek())
        val out = slots(s)
        assertEquals(IslandKind.Peek, out.mainKind())
        assertEquals("the call is pushed aside, not hidden", IslandKind.OngoingCall, out.splitKind())
    }

    @Test
    fun timerFinishingFallsBackToMusicAndThenIdle() {
        var s = sync(IslandState(), music(), timer())
        assertEquals(IslandKind.Timer, slots(s).mainKind())
        s = sync(s, music())
        assertEquals(IslandKind.Music, slots(s).mainKind())
        s = sync(s)
        assertEquals(IslandSlots.EMPTY, slots(s))
        assertTrue(s.dismissed.isEmpty())
    }

    @Test
    fun sourcesDroppingAnAppEmptiesTheIslandAndClearsDismissals() {
        var s = sync(IslandState(), music(), ongoingCall())
        s = dismiss(s, "call:ongoing")
        assertEquals(IslandKind.Music, slots(s).mainKind())
        // There is no Recents hook: swiping an app away shows up as its notifications and media session leaving the sources.
        s = sync(s)
        assertEquals(IslandSlots.EMPTY, slots(s))
        assertTrue("dismissals die with their activity", s.dismissed.isEmpty())
    }

    @Test
    fun aDismissedActivityStaysHiddenWhileItLivesAndReturnsWhenRecreated() {
        var s = sync(IslandState(), ongoingCall())
        s = dismiss(s, "call:ongoing")
        s = sync(s, ongoingCall())
        assertNull("still hidden while the same activity exists", slots(s).primary)
        s = sync(s)
        s = sync(s, ongoingCall(since = 99))
        assertEquals("a new activity with the same id shows again", IslandKind.OngoingCall, slots(s).mainKind())
    }

    @Test
    fun everySequenceReturnsToIdleWithNoStuckState() {
        val pool = listOf(
            { music() }, { ongoingCall() }, { incomingCall() }, { timer() },
            { peek() }, { charging() }, { ringer() },
        )
        val rnd = Random(20261029)
        repeat(500) {
            var s = IslandState()
            repeat(40) {
                val live = pool.filter { rnd.nextBoolean() }.map { it() }
                s = sync(s, *live.toTypedArray())
                // Random swipe-aways of whatever is on screen, like the user would do.
                slots(s).primary?.takeIf { rnd.nextInt(4) == 0 }?.let { s = dismiss(s, it.id) }
                // Every state must show at most one main activity and a split only of another kind.
                val out = slots(s)
                out.secondary?.let { assertFalse(it.kind == out.primary?.kind) }
                assertTrue(s.dismissed.all { id -> s.activities.any { it.id == id } })
            }
            s = sync(s)
            assertEquals(IslandSlots.EMPTY, slots(s))
            assertTrue(s.dismissed.isEmpty())
        }
    }
}
