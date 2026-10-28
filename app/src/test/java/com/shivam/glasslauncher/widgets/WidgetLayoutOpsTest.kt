package com.shivam.glasslauncher.widgets

import com.shivam.glasslauncher.data.AppItem
import com.shivam.glasslauncher.data.DropTarget
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.data.LayoutOps
import com.shivam.glasslauncher.data.PAGE_SIZE
import com.shivam.glasslauncher.data.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Grid placement and collision on the home layout: widgets own cells, icons reflow around them. */
class WidgetLayoutOpsTest {
    private val apps = (1..30).map { "a$it" }

    private fun keysOn(l: HomeLayout, page: Int) = l.pages[page].map { (it as? AppItem)?.appKey }

    @Test fun addingWidgetReflowsIconsInOrder() {
        val l = LayoutOps.initial(apps.take(8), emptyList(), emptySet())
        val out = LayoutOps.addWidget(l, "w1", 0, 4, 2, at = GridRect(0, 0, 4, 2))
        assertEquals(GridRect(0, 0, 4, 2), out.widgets.single().rect)
        // First two rows belong to the widget; the 8 icons moved down, order kept.
        assertTrue(keysOn(out, 0).take(8).all { it == null })
        assertEquals(apps.take(8), keysOn(out, 0).drop(8).take(8))
        assertEquals(8, out.placedKeys().size)
    }

    @Test fun reflowOverflowsToNextPage() {
        val l = LayoutOps.initial(apps.take(PAGE_SIZE), emptyList(), emptySet())
        val out = LayoutOps.addWidget(l, "w1", 0, 4, 4, at = GridRect(0, 0, 4, 4))
        assertEquals(2, out.pages.size)
        assertEquals(PAGE_SIZE, out.placedKeys().size)
        assertEquals("a1", keysOn(out, 0)[16])
        assertEquals(apps.take(PAGE_SIZE).drop(8), keysOn(out, 1).take(16))
    }

    @Test fun widgetsNeverOverlap() {
        val l = LayoutOps.addWidget(LayoutOps.initial(apps.take(4), emptyList(), emptySet()), "w1", 0, 2, 2, at = GridRect(0, 0, 2, 2))
        // Requested on top of w1: goes to the free spot that displaces the fewest icons instead.
        val two = LayoutOps.addWidget(l, "w2", 0, 2, 2, at = GridRect(0, 0, 2, 2))
        val r1 = two.widgets.first { it.id == "w1" }.rect
        val r2 = two.widgets.first { it.id == "w2" }.rect
        assertTrue(!r1.overlaps(r2))
        // Moving w2 onto w1 lands on the nearest free spot.
        val moved = LayoutOps.moveWidget(two, "w2", 0, GridRect(1, 0, 2, 2))
        val m2 = moved.widgets.first { it.id == "w2" }.rect
        assertTrue(!m2.overlaps(r1))
        // Resizing into another widget is refused.
        val big = LayoutOps.resizeWidget(moved, "w1", 4, 2)
        assertEquals(r1, big.widgets.first { it.id == "w1" }.rect)
    }

    @Test fun fullPageSendsWidgetToNextPage() {
        var l = LayoutOps.initial(apps.take(2), emptyList(), emptySet())
        l = LayoutOps.addWidget(l, "big1", 0, 4, 4)
        l = LayoutOps.addWidget(l, "mid", 0, 4, 2)
        l = LayoutOps.addWidget(l, "big2", 0, 4, 4)
        assertEquals(1, l.widgets.first { it.id == "big2" }.page)
        assertEquals(2, l.placedKeys().size)
    }

    @Test fun iconDropOnWidgetCellLandsInOpenCell() {
        var l = LayoutOps.initial(apps.take(3), emptyList(), emptySet())
        l = LayoutOps.addWidget(l, "w", 0, 2, 2, at = GridRect(2, 0, 2, 2))
        val moved = LayoutOps.move(l, Slot.Page(0, 0), DropTarget.Cell(0, 2, merge = false))
        val blocked = moved.blockedCells(0)
        moved.pages[0].forEachIndexed { i, item -> if (blocked[i]) assertNull(item) }
        assertEquals(3, moved.placedKeys().size)
    }

    @Test fun reconcilePlacesNewAppsAroundWidgets() {
        var l = LayoutOps.initial(listOf("a1"), emptyList(), emptySet())
        l = LayoutOps.addWidget(l, "w", 0, 4, 2, at = GridRect(0, 0, 4, 2))
        val r = LayoutOps.reconcile(l, listOf("a1", "a2"), emptySet())
        assertEquals("a2", keysOn(r, 0)[9])
    }

    @Test fun removeWidgetFreesCellsAndKeepsIcons() {
        var l = LayoutOps.initial(apps.take(5), emptyList(), emptySet())
        l = LayoutOps.addWidget(l, "w", 0, 4, 2, at = GridRect(0, 0, 4, 2))
        val out = LayoutOps.removeWidget(l, "w")
        assertTrue(out.widgets.isEmpty())
        assertEquals(5, out.placedKeys().size)
    }

    @Test fun pageWithOnlyAWidgetIsKept() {
        var l = LayoutOps.initial(apps.take(2), emptyList(), emptySet())
        l = LayoutOps.addPage(l)
        assertEquals(2, l.pages.size)
        l = LayoutOps.addWidget(l, "w", 1, 2, 2)
        val moved = LayoutOps.move(l, Slot.Page(0, 0), DropTarget.Cell(0, 5, merge = false))
        assertEquals(2, moved.pages.size)
        assertEquals(1, moved.widgets.single().page)
    }

    @Test fun pageManagerAddMoveRemoveAndDefault() {
        var l = LayoutOps.initial(apps.take(PAGE_SIZE + 2), emptyList(), emptySet())
        l = LayoutOps.addPage(l)
        assertEquals(3, l.pages.size)
        l = LayoutOps.addWidget(l, "w", 2, 2, 2)
        l = LayoutOps.setDefaultPage(l, 1)
        // Move the widget page to the front: widget, default page and keep flags follow.
        l = LayoutOps.movePage(l, 2, 0)
        assertEquals(0, l.widgets.single().page)
        assertEquals(2, l.defaultPage)
        assertEquals("a25", keysOn(l, 2)[0])
        // Removing the default page resets the default; its apps leave Home but stay known.
        l = LayoutOps.removePage(l, 2)
        assertEquals(2, l.pages.size)
        assertEquals(0, l.defaultPage)
        assertTrue("a25" !in l.placedKeys())
        assertTrue("a25" in l.known)
        val r = LayoutOps.reconcile(l, apps.take(PAGE_SIZE + 2), emptySet())
        assertTrue("a25" !in r.placedKeys())
    }

    @Test fun cannotRemoveLastPage() {
        val l = LayoutOps.initial(apps.take(2), emptyList(), emptySet())
        assertEquals(l, LayoutOps.removePage(l, 0))
    }

    @Test fun jsonRoundTripKeepsWidgetsAndPages() {
        var l = LayoutOps.initial(apps.take(3), emptyList(), emptySet())
        l = LayoutOps.addPage(l)
        l = LayoutOps.addWidget(l, "w", 0, 4, 2, at = GridRect(0, 2, 4, 2))
        l = LayoutOps.setDefaultPage(l, 1)
        val back = HomeLayout.fromJson(l.toJson())
        assertEquals(l.widgets, back.widgets)
        assertEquals(l.keptPages, back.keptPages)
        assertEquals(1, back.defaultPage)
        assertNotEquals(0, back.pages.size)
    }
}
