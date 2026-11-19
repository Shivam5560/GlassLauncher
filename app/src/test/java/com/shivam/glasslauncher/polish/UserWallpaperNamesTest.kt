package com.shivam.glasslauncher.polish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserWallpaperNamesTest {
    @Test fun cleansFileNames() {
        assertEquals("iOS27 Default", UserWallpaperNames.clean("iOS27_Default_Light.png"))
        assertEquals("iOS 26", UserWallpaperNames.clean("iOS_26_Dark.jpg"))
        assertEquals("iOS27 Blue", UserWallpaperNames.clean("iOS27_Blue.png"))
        assertEquals("My wallpaper", UserWallpaperNames.clean(null))
        assertEquals("My wallpaper", UserWallpaperNames.clean("../.png"))
        assertEquals("My wallpaper", UserWallpaperNames.clean("14472.jpg"))
    }

    @Test fun idCarriesName() {
        val id = UserWallpaperNames.newId("iOS27 Blue", 1_763_600_000_000L)
        assertTrue(UserWallpaperNames.isUser(id))
        assertEquals("iOS27 Blue", UserWallpaperNames.nameOf(id))
        assertFalse(UserWallpaperNames.isUser("mesh-aurora"))
        assertEquals("user-1763600000000-iOS 27", UserWallpaperNames.renamed(id, "iOS 27"))
    }
}
