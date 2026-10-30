package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.island.IslandGeometry
import org.junit.Assert.*
import org.junit.Test

class IslandGeometryTest {
    @Test fun expandedTextStartsBelowPhysicalCameraEvenAfterVisualOffset() {
        assertEquals(70f, IslandGeometry.contentTop(cameraBottom = 60f, pillTop = 0f, compactHeight = 34f), 0.01f)
        assertEquals(90f, IslandGeometry.contentTop(cameraBottom = 60f, pillTop = -20f, compactHeight = 34f), 0.01f)
        assertEquals(34f, IslandGeometry.contentTop(cameraBottom = null, pillTop = 0f, compactHeight = 34f), 0.01f)
    }

    @Test fun compactTextCannotGrowThroughTheCamera() {
        val textStart = IslandGeometry.trailingStart(cameraRight = 190f, pillLeft = 100f, width = 200f, glyphLane = 40f)
        assertTrue(textStart + 100f >= 190f + 10f)
        assertEquals(200f, IslandGeometry.trailingStart(400f, 100f, 200f, 40f), 0.01f)
    }

    @Test fun largerFontsGetMoreReadingRoomRatherThanSmallerText() {
        val normal = IslandGeometry.peekHeight(48f, 1f)
        val large = IslandGeometry.peekHeight(48f, 2f)
        assertEquals(186f, normal, 0.01f)
        assertEquals(2, IslandGeometry.peekMessageLines(1f))
        assertEquals(3, IslandGeometry.peekMessageLines(1.5f))
        assertTrue(large > normal)
        assertEquals(normal, IslandGeometry.peekHeight(48f, 0.8f), 0.01f)
    }

    @org.junit.Test
    fun peekFitsShortMessages() {
        val short = com.shivam.glasslauncher.overlay.island.IslandGeometry.peekHeight(48f, 1f, "Team: Ana", "Are we on?")
        val long = com.shivam.glasslauncher.overlay.island.IslandGeometry.peekHeight(48f, 1f, "Team: Ana", "x".repeat(200))
        org.junit.Assert.assertEquals(20f, long - short, 0.01f) // same one-line title; one vs two message lines
    }
}
