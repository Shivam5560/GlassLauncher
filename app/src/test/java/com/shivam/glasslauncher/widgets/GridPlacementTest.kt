package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPlacementTest {

    @Test fun overlapAndContains() {
        val a = GridRect(0, 0, 2, 2)
        assertTrue(a.overlaps(GridRect(1, 1, 2, 2)))
        assertFalse(a.overlaps(GridRect(2, 0, 2, 2)))
        assertFalse(a.overlaps(GridRect(0, 2, 4, 2)))
        assertTrue(a.contains(1, 1))
        assertFalse(a.contains(2, 1))
        assertEquals(listOf(0, 1, 4, 5), a.cells(4))
    }

    @Test fun clampKeepsInsideGrid() {
        assertEquals(GridRect(2, 4, 2, 2), GridPlacement.clamp(GridRect(3, 5, 2, 2), 4, 6))
        assertEquals(GridRect(0, 0, 4, 6), GridPlacement.clamp(GridRect(-1, -1, 9, 9), 4, 6))
    }

    @Test fun firstFreeSkipsWidgetsAndPrefersEmptyCells() {
        val others = listOf(GridRect(0, 0, 4, 2))
        assertEquals(GridRect(0, 2, 2, 2), GridPlacement.firstFree(2, 2, others, 4, 6))
        val occupied = BooleanArray(24).also { for (i in 8..15) it[i] = true } // rows 2-3 full of icons
        assertEquals(GridRect(0, 4, 4, 2), GridPlacement.firstFree(4, 2, others, 4, 6, occupied))
        assertNull(GridPlacement.firstFree(4, 4, listOf(GridRect(0, 2, 4, 2)), 4, 6))
    }

    @Test fun nearestFreeAvoidsCollisions() {
        val others = listOf(GridRect(0, 0, 2, 2))
        assertEquals(GridRect(2, 0, 2, 2), GridPlacement.nearestFree(GridRect(1, 0, 2, 2), others, 4, 6))
        assertEquals(GridRect(2, 3, 2, 2), GridPlacement.nearestFree(GridRect(2, 3, 2, 2), others, 4, 6))
        val full = listOf(GridRect(0, 0, 4, 6))
        assertNull(GridPlacement.nearestFree(GridRect(0, 0, 1, 1), full, 4, 6))
    }

    @Test fun snapRoundsAndClamps() {
        assertEquals(GridRect(1, 2, 2, 2), GridPlacement.snap(1.4f, 2.6f - 0.2f, 2, 2, 4, 6))
        assertEquals(GridRect(2, 4, 2, 2), GridPlacement.snap(3.9f, 9f, 2, 2, 4, 6))
        assertEquals(GridRect(0, 0, 4, 2), GridPlacement.snap(-2f, -1f, 4, 2, 4, 6))
    }

    @Test fun resizeSnapsToAllowedSizes() {
        val ios = WidgetKind.WEATHER.sizes.map { it.w to it.h }
        val small = GridRect(0, 0, 2, 2)
        assertEquals(GridRect(0, 0, 4, 2), GridPlacement.resize(small, 1.8f, 0.2f, 4, 6, allowed = ios))
        assertEquals(GridRect(0, 0, 4, 4), GridPlacement.resize(small, 2f, 2.1f, 4, 6, allowed = ios))
        // Not enough room to the right: stays small.
        val right = GridRect(2, 0, 2, 2)
        assertEquals(GridRect(2, 0, 2, 2), GridPlacement.resize(right, 2f, 0f, 4, 6, allowed = ios))
    }

    @Test fun resizeFreeWithinLimits() {
        val r = GridRect(1, 1, 2, 2)
        assertEquals(GridRect(1, 1, 3, 3), GridPlacement.resize(r, 1.2f, 0.6f, 4, 6))
        assertEquals(GridRect(1, 1, 3, 5), GridPlacement.resize(r, 5f, 9f, 4, 6))
        assertEquals(GridRect(1, 1, 2, 1), GridPlacement.resize(r, -3f, -3f, 4, 6, minW = 2, minH = 1))
    }
}
