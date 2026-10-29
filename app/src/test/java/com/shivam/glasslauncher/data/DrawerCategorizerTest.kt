package com.shivam.glasslauncher.data

import com.shivam.glasslauncher.data.DrawerCategorizer.GAMES
import com.shivam.glasslauncher.data.DrawerCategorizer.INTERNET
import com.shivam.glasslauncher.data.DrawerCategorizer.MEDIA
import com.shivam.glasslauncher.data.DrawerCategorizer.OTHER
import com.shivam.glasslauncher.data.DrawerCategorizer.SETTINGS
import com.shivam.glasslauncher.data.DrawerCategorizer.SOCIAL
import com.shivam.glasslauncher.data.DrawerCategorizer.UTILITIES
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerCategorizerTest {

    private val undefined = -1

    private fun cat(pkg: String, system: Int = undefined, game: Boolean = false) = DrawerCategorizer.categorize(pkg, system, game)

    @Test
    fun systemCategoryWinsOverKeywords() {
        // CATEGORY_AUDIO on a package whose name says "whatsapp": the app's own category wins.
        assertEquals(MEDIA, cat("com.whatsapp", system = 1))
        assertEquals(GAMES, cat("com.example.notes", system = 0))
        assertEquals(SOCIAL, cat("com.example.thing", system = 4))
        assertEquals(INTERNET, cat("com.example.thing", system = 5))
        assertEquals(UTILITIES, cat("com.example.thing", system = 7))
        assertEquals(SETTINGS, cat("com.example.thing", system = 8))
    }

    @Test
    fun gameFlagBeforeKeywords() {
        assertEquals(GAMES, cat("com.example.music", game = true))
    }

    @Test
    fun keywordsAndPackages() {
        assertEquals(SOCIAL, cat("com.whatsapp"))
        assertEquals(SOCIAL, cat("com.google.android.apps.messaging"))
        assertEquals(SOCIAL, cat("com.android.phone"))
        assertEquals(INTERNET, cat("com.android.chrome"))
        assertEquals(INTERNET, cat("com.google.android.gm"))
        assertEquals(INTERNET, cat("com.android.vending"))
        assertEquals(MEDIA, cat("com.google.android.youtube"))
        assertEquals(MEDIA, cat("com.android.camera"))
        assertEquals(SETTINGS, cat("com.android.settings"))
        assertEquals(SETTINGS, cat("com.miui.securitycenter"))
        assertEquals(SETTINGS, cat("com.shivam.glasslauncher"))
        assertEquals(GAMES, cat("com.supercell.clashofclans"))
        assertEquals(UTILITIES, cat("com.android.deskclock"))
        assertEquals(UTILITIES, cat("com.phonepe.app"))
        assertEquals(UTILITIES, cat("com.miui.calculator"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(SOCIAL, cat("Com.WhatsApp"))
    }

    @Test
    fun unknownIsOther() {
        assertEquals(OTHER, cat("com.example.zzz"))
    }

    @Test
    fun builtInOrderAndTitles() {
        assertEquals(listOf("internet", "media", "social", "games", "utilities", "settings", "other"), DrawerCategorizer.BUILT_IN)
        assertEquals(listOf("Internet", "Media", "Social", "Games", "Utilities", "Settings", "Other"), DrawerCategorizer.BUILT_IN.map { DrawerCategorizer.defaultTitle(it) })
    }
}
