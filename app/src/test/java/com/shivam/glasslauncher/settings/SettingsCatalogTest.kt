package com.shivam.glasslauncher.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsCatalogTest {
    @Test fun blankQueryDoesNotDumpEverySetting() {
        assertTrue(SettingsCatalog.search("  ").isEmpty())
    }
    @Test fun exactTitleRanksAheadOfRelatedDestinations() {
        assertEquals("wallpapers", SettingsCatalog.search("  WALLPAPERS  ").first().route)
        assertEquals("glass", SettingsCatalog.search("Glass tuning").first().route)
    }
    @Test fun FindsControlsInsideTheirParentPage() {
        for (query in listOf("badges", "true tone", "infinite scroll", "parallax")) {
            assertEquals(query, "home", SettingsCatalog.search(query).first().route)
        }
    }
    @Test fun allWordsMustMatchRegardlessOfOrderOrSpacing() {
        assertEquals("gestures", SettingsCatalog.search(" recent   navigation ").single().route)
        assertTrue(SettingsCatalog.search("wallpaper fingerprint").isEmpty())
    }
    @Test fun unknownQueryHasNoResults() {
        assertTrue(SettingsCatalog.search("zzzzzz").isEmpty())
    }
    @Test fun destinationsAreUnique() {
        assertEquals(SettingsCatalog.entries.size, SettingsCatalog.entries.map { it.route }.distinct().size)
    }
}
