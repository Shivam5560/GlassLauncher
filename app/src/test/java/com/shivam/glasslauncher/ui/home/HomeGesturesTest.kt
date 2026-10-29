package com.shivam.glasslauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeGesturesTest {

    @Test fun gestureMapRoundTrips() {
        val map = HomeGestureMap.defaults + (HomeGesture.PinchOut to GestureBinding(HomeAction.LaunchApp, "com.a/.Main"))
        assertEquals(map, HomeGestureMap.decode(HomeGestureMap.encode(map)))
    }

    @Test fun unknownEntriesAreSkipped() {
        val m = HomeGestureMap.decode("SwipeUp=Spotlight\nWiggle=AppDrawer\nSwipeDown=Teleport\ngarbage")!!
        assertEquals(mapOf(HomeGesture.SwipeUp to GestureBinding(HomeAction.Spotlight)), m)
        assertNull(HomeGestureMap.decode(null))
    }

    @Test fun ringWithoutLoopIsIdentity() {
        val r = PagerRing(5, infinite = false)
        assertEquals(5, r.pagerCount)
        assertEquals(3, r.of(3))
        assertEquals(4, r.nearest(0, 4))
    }

    @Test fun loopMapsAndTakesShortestWay() {
        val r = PagerRing(5, infinite = true)
        assertEquals(0, r.of(r.start))
        assertEquals(4, r.of(r.start - 1))
        // From Today (ring 0), the App Library (ring 4) is one page back, not four forward.
        assertEquals(r.start - 1, r.nearest(r.start, 4))
        assertEquals(r.start + 2, r.nearest(r.start, 2))
        // Never more than half the ring composed on each side, so no page appears twice.
        assertEquals(2, r.beyondViewport)
    }
}
