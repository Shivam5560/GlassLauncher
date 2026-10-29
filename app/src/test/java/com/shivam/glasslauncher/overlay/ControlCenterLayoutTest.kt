package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.controlcenter.CcModule
import com.shivam.glasslauncher.overlay.controlcenter.CcModuleId
import com.shivam.glasslauncher.overlay.controlcenter.CcSize
import com.shivam.glasslauncher.overlay.controlcenter.ControlCenterLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterLayoutTest {

    @Test
    fun defaultLayoutPacksLikeIos() {
        val p = ControlCenterLayout.pack(ControlCenterLayout.DEFAULT).associateBy { it.module.id }
        assertEquals(0 to 0, p.getValue(CcModuleId.Wifi).let { it.col to it.row })
        assertEquals(1 to 0, p.getValue(CcModuleId.Bluetooth).let { it.col to it.row })
        assertEquals(2 to 0, p.getValue(CcModuleId.Music).let { it.col to it.row })
        assertEquals(0 to 1, p.getValue(CcModuleId.MobileData).let { it.col to it.row })
        assertEquals(1 to 1, p.getValue(CcModuleId.Airplane).let { it.col to it.row })
        assertEquals(0 to 2, p.getValue(CcModuleId.Brightness).let { it.col to it.row })
        assertEquals(1 to 2, p.getValue(CcModuleId.Volume).let { it.col to it.row })
        assertEquals(2 to 2, p.getValue(CcModuleId.Flashlight).let { it.col to it.row })
    }

    @Test
    fun noOverlaps() {
        val placements = ControlCenterLayout.pack(ControlCenterLayout.DEFAULT.map { ControlCenterLayout.cycleSize(it) })
        val cells = HashSet<Pair<Int, Int>>()
        for (p in placements) {
            assertTrue(p.right <= ControlCenterLayout.COLUMNS)
            for (x in p.col until p.right) for (y in p.row until p.bottom) assertTrue("overlap at $x,$y", cells.add(x to y))
        }
    }

    @Test
    fun moveAndResize() {
        val list = ControlCenterLayout.DEFAULT
        val moved = ControlCenterLayout.move(list, 0, 2)
        assertEquals(CcModuleId.Bluetooth, moved[0].id)
        assertEquals(CcModuleId.Wifi, moved[2].id)
        assertEquals(list, ControlCenterLayout.move(list, 0, 99))

        val wifi = CcModule(CcModuleId.Wifi, CcSize.Small)
        assertEquals(CcSize.Wide, ControlCenterLayout.cycleSize(wifi).size)
        assertEquals(CcSize.Small, ControlCenterLayout.cycleSize(CcModule(CcModuleId.Wifi, CcSize.Large)).size)
        assertEquals(CcSize.Small, ControlCenterLayout.cycleSize(CcModule(CcModuleId.Brightness, CcSize.Tall)).size)
    }

    @Test
    fun serializationRoundTripsAndTolerates() {
        val custom = listOf(CcModule(CcModuleId.Music, CcSize.Wide), CcModule(CcModuleId.Flashlight, CcSize.Large))
        assertEquals(custom, ControlCenterLayout.parse(ControlCenterLayout.serialize(custom)))
        assertEquals(ControlCenterLayout.DEFAULT, ControlCenterLayout.parse(""))
        val parsed = ControlCenterLayout.parse("Bogus:Small,Wifi:Huge,Wifi:Wide,Brightness:Wide")
        assertEquals(listOf(CcModule(CcModuleId.Wifi, CcSize.Small), CcModule(CcModuleId.Brightness, CcSize.Tall)), parsed)
    }

    @Test
    fun missingModulesCanBeAdded() {
        val few = listOf(CcModule(CcModuleId.Wifi, CcSize.Small))
        val missing = ControlCenterLayout.missing(few)
        assertFalse(CcModuleId.Wifi in missing)
        assertEquals(CcModuleId.entries.size - 1, missing.size)
    }
}
