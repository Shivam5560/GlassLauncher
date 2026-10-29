package com.shivam.glasslauncher.overlay

import org.junit.Assert.*
import org.junit.Test

class IslandCompatibilityTest {
    @Test fun automaticYieldsToNativeIsland() {
        assertFalse(IslandCompatibility.AUTO.allowsGlass(nativeIsland = true))
        assertTrue(IslandCompatibility.AUTO.allowsGlass(nativeIsland = false))
    }

    @Test fun explicitChoicesAreStableAcrossSerialization() {
        for (native in listOf(false, true)) {
            assertFalse(IslandCompatibility.valueOf("SYSTEM").allowsGlass(native))
            assertTrue(IslandCompatibility.valueOf("GLASS").allowsGlass(native))
        }
    }

    @Test fun disabledAlwaysWinsOverCompatibility() {
        IslandCompatibility.entries.forEach { mode ->
            assertFalse(IslandSettings(enabled = false, compatibility = mode).showsGlass)
        }
    }
}
