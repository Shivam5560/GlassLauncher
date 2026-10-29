package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.island.IslandActivity
import com.shivam.glasslauncher.overlay.island.IslandArbiter
import com.shivam.glasslauncher.overlay.island.IslandClassifier
import com.shivam.glasslauncher.overlay.island.IslandContent
import com.shivam.glasslauncher.overlay.island.IslandKind
import com.shivam.glasslauncher.overlay.island.RingerMode
import com.shivam.glasslauncher.overlay.notifications.NotifEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandArbiterTest {

    private fun act(kind: IslandKind, since: Long = 0, id: String = "${kind.name}:$since") =
        IslandActivity(id, kind, since, IslandContent.Ringer(RingerMode.Silent))

    @Test
    fun emptyGivesNothing() {
        val s = IslandArbiter.resolve(emptyList())
        assertNull(s.primary)
        assertNull(s.secondary)
        assertFalse(s.isSplit)
    }

    @Test
    fun singleActivityHasNoSplit() {
        val s = IslandArbiter.resolve(listOf(act(IslandKind.Music)))
        assertEquals(IslandKind.Music, s.primary?.kind)
        assertNull(s.secondary)
    }

    @Test
    fun higherPriorityWinsAndRunnerUpGoesToSplit() {
        val s = IslandArbiter.resolve(listOf(act(IslandKind.Music), act(IslandKind.Timer), act(IslandKind.Progress)))
        assertEquals(IslandKind.Timer, s.primary?.kind)
        assertEquals(IslandKind.Progress, s.secondary?.kind)
        assertTrue(s.isSplit)
    }

    @Test
    fun callBeatsNavigationAndMusic() {
        val s = IslandArbiter.resolve(listOf(act(IslandKind.Music), act(IslandKind.Navigation), act(IslandKind.OngoingCall)))
        assertEquals(IslandKind.OngoingCall, s.primary?.kind)
        assertEquals(IslandKind.Navigation, s.secondary?.kind)
    }

    @Test
    fun incomingCallTakesWholeIsland() {
        val s = IslandArbiter.resolve(listOf(act(IslandKind.Music), act(IslandKind.IncomingCall), act(IslandKind.Ringer)))
        assertEquals(IslandKind.IncomingCall, s.primary?.kind)
        assertNull(s.secondary)
    }

    @Test
    fun transientTakesMainSlotAndPushesRunningActivityToSplit() {
        val s = IslandArbiter.resolve(listOf(act(IslandKind.Music), act(IslandKind.Charging)))
        assertEquals(IslandKind.Charging, s.primary?.kind)
        assertEquals(IslandKind.Music, s.secondary?.kind)
    }

    @Test
    fun transientNeverGoesToSplitSlot() {
        val s = IslandArbiter.resolve(listOf(act(IslandKind.Ringer), act(IslandKind.Peek), act(IslandKind.Charging)))
        assertEquals(IslandKind.Ringer, s.primary?.kind)
        assertNull(s.secondary)
    }

    @Test
    fun splitSlotNeedsADifferentKind() {
        val s = IslandArbiter.resolve(listOf(act(IslandKind.Progress, 1), act(IslandKind.Progress, 2)))
        assertEquals("Progress:2", s.primary?.id) // newest wins ties
        assertNull(s.secondary)

        val withMusic = IslandArbiter.resolve(listOf(act(IslandKind.Progress, 1), act(IslandKind.Progress, 2), act(IslandKind.Music)))
        assertEquals(IslandKind.Music, withMusic.secondary?.kind)
    }

    @Test
    fun disabledKindsAreSkipped() {
        val s = IslandArbiter.resolve(
            listOf(act(IslandKind.Music), act(IslandKind.Timer), act(IslandKind.Navigation)),
            enabled = { it != IslandKind.Navigation },
        )
        assertEquals(IslandKind.Timer, s.primary?.kind)
        assertEquals(IslandKind.Music, s.secondary?.kind)
    }

    @Test
    fun dismissedActivityStaysHiddenUntilItEnds() {
        val timer = act(IslandKind.Timer, id = "timer:1")
        val music = act(IslandKind.Music, id = "music:x")
        val s = IslandArbiter.resolve(listOf(timer, music), dismissed = setOf("timer:1"))
        assertEquals(IslandKind.Music, s.primary?.kind)
        assertNull(s.secondary)

        assertEquals(setOf("timer:1"), IslandArbiter.pruneDismissed(setOf("timer:1"), listOf(timer, music)))
        assertTrue(IslandArbiter.pruneDismissed(setOf("timer:1"), listOf(music)).isEmpty())
    }

    @Test
    fun priorityOrderMatchesDesign() {
        val order = IslandKind.entries.sortedByDescending { it.priority }
        assertEquals(IslandKind.IncomingCall, order.first())
        assertEquals(IslandKind.Music, order.last())
        assertTrue(IslandKind.OngoingCall.priority > IslandKind.Navigation.priority)
        assertTrue(IslandKind.Navigation.priority > IslandKind.Timer.priority)
        assertTrue(IslandKind.Timer.priority > IslandKind.Progress.priority)
    }

    // --- classifier feeding the arbiter ---

    private fun e(
        key: String,
        pkg: String = "com.app",
        category: String? = null,
        ongoing: Boolean = false,
        chrono: Boolean = false,
        countDown: Boolean = false,
        callType: Int = 0,
        fullScreen: Boolean = false,
        max: Int = 0,
        progress: Int = 0,
        title: String = "Title",
        text: String = "Text",
        template: String? = null,
    ) = NotifEntry(
        key = key, packageName = pkg, appLabel = "App", title = title, text = text, postTime = 1000,
        category = category, isOngoing = ongoing, isClearable = !ongoing, usesChronometer = chrono,
        chronometerCountDown = countDown, whenTime = 500, callType = callType, hasFullScreenIntent = fullScreen,
        progressMax = max, progress = progress, template = template,
    )

    @Test
    fun classifierRecognisesActivities() {
        assertEquals(IslandKind.IncomingCall, IslandClassifier.classify(e("c", category = "call", fullScreen = true, ongoing = true))?.kind)
        assertEquals(IslandKind.OngoingCall, IslandClassifier.classify(e("c", category = "call", chrono = true, ongoing = true))?.kind)
        assertEquals(IslandKind.IncomingCall, IslandClassifier.classify(e("c", template = "CallStyle", callType = 1))?.kind)
        assertEquals(IslandKind.Timer, IslandClassifier.classify(e("t", pkg = "com.google.android.deskclock", ongoing = true, chrono = true, countDown = true))?.kind)
        assertEquals(IslandKind.Stopwatch, IslandClassifier.classify(e("s", pkg = "com.android.deskclock", ongoing = true, chrono = true))?.kind)
        assertEquals(IslandKind.Navigation, IslandClassifier.classify(e("n", pkg = "com.google.android.apps.maps", ongoing = true, title = "200 m", text = "Turn left"))?.kind)
        assertEquals(IslandKind.Progress, IslandClassifier.classify(e("d", max = 100, progress = 40))?.kind)
        assertNull(IslandClassifier.classify(e("m")))
        assertNull(IslandClassifier.classify(e("alarm", pkg = "com.android.deskclock", ongoing = true, title = "Alarm", text = "7:00")))
    }

    @Test
    fun uploadsAreTold() {
        val up = IslandClassifier.classify(e("u", max = 10, progress = 5, title = "Uploading 3 photos"))?.content as IslandContent.Progress
        assertTrue(up.upload)
        assertEquals(0.5f, up.fraction, 0.0001f)
        val down = IslandClassifier.classify(e("d", max = 10, progress = 5, title = "song.mp3"))?.content as IslandContent.Progress
        assertFalse(down.upload)
    }

    @Test
    fun peekOnlyForPlainMessages() {
        assertTrue(IslandClassifier.isPeekCandidate(e("m"), "com.shivam.glasslauncher"))
        assertFalse(IslandClassifier.isPeekCandidate(e("m", pkg = "com.shivam.glasslauncher"), "com.shivam.glasslauncher"))
        assertFalse(IslandClassifier.isPeekCandidate(e("o", ongoing = true), "x"))
        assertFalse(IslandClassifier.isPeekCandidate(e("p", max = 100), "x"))
        assertFalse(IslandClassifier.isPeekCandidate(e("q", title = "", text = ""), "x"))
    }
}
