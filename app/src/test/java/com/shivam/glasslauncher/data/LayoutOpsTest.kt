package com.shivam.glasslauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {
    private val apps = (1..30).map { "a$it" }

    @Test fun initialFillsPagesAndDock() {
        val l = LayoutOps.initial(apps, listOf("a1", "a2"), emptySet())
        assertEquals(AppItem("a1"), l.dock[0])
        assertEquals(2, l.pages.size)
        assertEquals(AppItem("a3"), l.pages[0][0])
        assertEquals(28, l.placedKeys().size - 2)
    }

    @Test fun mergeCreatesFolderAndCollapses() {
        val l = LayoutOps.initial(apps.take(5), emptyList(), emptySet())
        val merged = LayoutOps.move(l, Slot.Page(0, 1), DropTarget.Cell(0, 0, merge = true), "Social")
        val folder = merged.pages[0][0] as FolderItem
        assertEquals(listOf("a1", "a2"), folder.apps)
        assertEquals("Social", folder.name)
        assertNull(merged.pages[0][1])
        val out = LayoutOps.removeFromHome(merged, Slot.InFolder(folder.id, 0))
        assertEquals(AppItem("a2"), out.pages[0][0])
    }

    @Test fun insertShiftsRight() {
        val l = LayoutOps.initial(apps.take(4), emptyList(), emptySet())
        val moved = LayoutOps.move(l, Slot.Page(0, 3), DropTarget.Cell(0, 0, merge = false))
        assertEquals(listOf("a4", "a1", "a2", "a3"), moved.pages[0].take(4).map { (it as AppItem).appKey })
    }

    @Test fun fullPageOverflowsToNext() {
        val l = LayoutOps.initial(apps.take(PAGE_SIZE + 1), emptyList(), emptySet())
        val moved = LayoutOps.move(l, Slot.Page(1, 0), DropTarget.Cell(0, 0, merge = false))
        assertEquals(AppItem("a25"), moved.pages[0][0])
        assertEquals(AppItem("a24"), moved.pages[1][0])
    }

    @Test fun reconcileAddsNewRemovesUninstalledKeepsRemoved() {
        val l = LayoutOps.initial(listOf("a1", "a2", "a3"), emptyList(), emptySet())
        val removed = LayoutOps.removeFromHome(l, Slot.Page(0, 0))
        val r = LayoutOps.reconcile(removed, listOf("a1", "a3", "a9"), emptySet())
        val keys = r.placedKeys()
        assertTrue("a9" in keys)
        assertTrue("a2" !in keys)
        assertTrue("a1" !in keys) // removed by the user, not re-added
    }

    @Test fun dockSwapWhenFull() {
        val l = LayoutOps.initial(apps.take(8), apps.take(4), emptySet())
        val moved = LayoutOps.move(l, Slot.Page(0, 0), DropTarget.DockCell(1, merge = false))
        assertEquals(AppItem("a5"), moved.dock[1])
        assertEquals(AppItem("a2"), moved.pages[0][0])
    }

    @Test fun arrangeKeepsOnlyPicksPerPage() {
        val l = LayoutOps.initial(apps, emptyList(), emptySet())
        val r = LayoutOps.arrange(l, listOf("a9"), listOf(listOf("a1", "a2"), listOf("a3")))
        assertEquals(setOf("a1", "a2", "a3", "a9"), r.placedKeys())
        assertEquals(AppItem("a3"), r.pages[1][0])
        assertEquals(AppItem("a9"), r.dock[0])
        assertEquals(2, HomeLayout.fromJson(r.toJson()).version)
        assertTrue("b1" !in LayoutOps.reconcile(r, apps + "b1", emptySet(), autoAdd = false).placedKeys())
    }
}
