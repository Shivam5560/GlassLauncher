package com.shivam.glasslauncher.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerConfigTest {

    private fun app(key: String, label: String, cat: String, installed: Long = 0L) = DrawerApp(key, label, cat, installed)

    private val apps = listOf(
        app("a/.A", "Zoom", DrawerCategorizer.SOCIAL, 3),
        app("b/.B", "apple", DrawerCategorizer.SOCIAL, 1),
        app("c/.C", "Chrome", DrawerCategorizer.INTERNET, 2),
        app("d/.D", "Mystery", "nonexistent", 4),
    )

    @Test
    fun jsonRoundTripKeepsEverything() {
        var c = DrawerConfig()
        c = DrawerOps.addCategory(c, "Work", "briefcase")
        val work = c.categories.last().id
        c = DrawerOps.renameCategory(c, DrawerCategorizer.MEDIA, "Fun")
        c = DrawerOps.setInBar(c, DrawerCategorizer.GAMES, false)
        c = DrawerOps.moveCategory(c, c.categories.lastIndex, 0)
        c = DrawerOps.assign(c, "a/.A", work)
        c = DrawerOps.addFolder(c, DrawerCategorizer.SOCIAL, "Chats", listOf("b/.B"))
        c = DrawerOps.addShortcut(c, work, "c/.C", "c", "incognito", "New incognito tab")
        c = DrawerOps.setSort(c, DrawerSort.InstallDate)

        val back = DrawerConfig.fromJson(JSONObject(c.toJson().toString()))
        assertEquals(c, back)
        assertEquals(work, back.categories.first().id)
        assertEquals("Fun", back.category(DrawerCategorizer.MEDIA)?.title)
        assertFalse(back.category(DrawerCategorizer.GAMES)!!.inBar)
    }

    @Test
    fun layoutJsonCarriesDrawerAndLayoutOpsKeepIt() {
        val c = DrawerOps.addCategory(DrawerConfig(), "Work")
        val layout = LayoutOps.initial(listOf("a/.A", "b/.B"), emptyList(), emptySet()).copy(drawer = c)
        val back = HomeLayout.fromJson(layout.toJson())
        assertEquals(c, back.drawer)
        // Working.build() must carry the drawer through every layout edit.
        val moved = LayoutOps.removeFromHome(back, Slot.Page(0, 0))
        assertEquals(c, moved.drawer)
        assertEquals(c, LayoutOps.reconcile(moved, listOf("a/.A", "b/.B", "e/.E"), emptySet()).drawer)
    }

    @Test
    fun oldLayoutWithoutDrawerGetsDefaults() {
        val old = HomeLayout.EMPTY.toJson().let { JSONObject(it).apply { remove("drawer") }.toString() }
        assertEquals(DrawerConfig(), HomeLayout.fromJson(old).drawer)
    }

    @Test
    fun corruptAssignmentsToMissingCategoriesAreDropped() {
        val json = JSONObject(DrawerConfig().toJson().toString())
        json.getJSONObject("assign").put("x/.X", "gone")
        assertNull(DrawerConfig.fromJson(json).assignments["x/.X"])
    }

    @Test
    fun categoryResolution() {
        var c = DrawerConfig()
        assertEquals(DrawerCategorizer.OTHER, DrawerOps.categoryOf(c, apps[3]))
        assertEquals(DrawerCategorizer.SOCIAL, DrawerOps.categoryOf(c, apps[0]))
        c = DrawerOps.assign(c, "a/.A", DrawerCategorizer.MEDIA, DrawerCategorizer.SOCIAL)
        assertEquals(DrawerCategorizer.MEDIA, DrawerOps.categoryOf(c, apps[0]))
        // Moving back to the automatic category clears the manual move.
        c = DrawerOps.assign(c, "a/.A", DrawerCategorizer.SOCIAL, DrawerCategorizer.SOCIAL)
        assertTrue(c.assignments.isEmpty())
    }

    @Test
    fun deletingACategorySendsAppsBack() {
        var c = DrawerOps.addCategory(DrawerConfig(), "Work")
        val work = c.categories.last().id
        c = DrawerOps.assign(c, "a/.A", work)
        c = DrawerOps.addFolder(c, work, "F", listOf("b/.B"))
        c = DrawerOps.deleteCategory(c, work)
        assertNull(c.category(work))
        assertEquals(DrawerCategorizer.SOCIAL, DrawerOps.categoryOf(c, apps[0]))
        assertTrue(c.folders.isEmpty())
        // Built-in categories can be deleted (their apps fall back to Other); Other cannot.
        c = DrawerOps.deleteCategory(c, DrawerCategorizer.SOCIAL)
        assertEquals(DrawerCategorizer.OTHER, DrawerOps.categoryOf(c, apps[0]))
        assertSame(c, DrawerOps.deleteCategory(c, DrawerCategorizer.OTHER))
    }

    @Test
    fun barKeepsAtLeastOneCategory() {
        var c = DrawerConfig(categories = listOf(DrawerCategory("other", "Other", "other")))
        c = DrawerOps.setInBar(c, "other", false)
        assertTrue(c.categories.single().inBar)
    }

    @Test
    fun blankNamesAreIgnored() {
        val c = DrawerConfig()
        assertSame(c, DrawerOps.addCategory(c, "   "))
        assertSame(c, DrawerOps.renameCategory(c, DrawerCategorizer.MEDIA, ""))
    }

    @Test
    fun sorting() {
        val social = apps.take(2)
        assertEquals(listOf("apple", "Zoom"), DrawerOps.sort(social, DrawerSort.AZ, emptyMap()).map { it.label })
        assertEquals(listOf("Zoom", "apple"), DrawerOps.sort(social, DrawerSort.InstallDate, emptyMap()).map { it.label })
        assertEquals(listOf("Zoom", "apple"), DrawerOps.sort(social, DrawerSort.MostUsed, mapOf("a/.A" to 0)).map { it.label })
        // Unranked apps go last, A–Z among themselves.
        assertEquals(listOf("Chrome", "apple", "Zoom"), DrawerOps.sort(apps.take(3), DrawerSort.MostUsed, mapOf("c/.C" to 0)).map { it.label })
    }

    @Test
    fun pagesPutFoldersFirstAndNeverRepeatFolderApps() {
        var c = DrawerOps.addFolder(DrawerConfig(), DrawerCategorizer.INTERNET, "Mixed", listOf("b/.B"))
        val pages = DrawerOps.pages(c, apps, emptyMap())
        val internet = pages.getValue(DrawerCategorizer.INTERNET)
        assertTrue(internet.first() is DrawerItem.Folder)
        assertEquals(listOf(DrawerItem.App("a/.A")), pages.getValue(DrawerCategorizer.SOCIAL))
        assertTrue(pages.getValue(DrawerCategorizer.GAMES).isEmpty())
        // Moving the app out of the folder (to a category) dissolves the now-empty folder.
        c = DrawerOps.assign(c, "b/.B", DrawerCategorizer.MEDIA)
        assertTrue(c.folders.isEmpty())
        assertNotNull(DrawerOps.pages(c, apps, emptyMap()).getValue(DrawerCategorizer.MEDIA).firstOrNull { it.key == "b/.B" })
    }

    @Test
    fun shortcutsAreDeduplicatedAndFollowTheirApp() {
        var c = DrawerOps.addShortcut(DrawerConfig(), DrawerCategorizer.INTERNET, "c/.C", "c", "s1", "Tab")
        assertSame(c, DrawerOps.addShortcut(c, DrawerCategorizer.INTERNET, "c/.C", "c", "s1", "Tab"))
        assertEquals(1, DrawerOps.pages(c, apps, emptyMap()).getValue(DrawerCategorizer.INTERNET).count { it is DrawerItem.Shortcut })
        // Uninstalled app: its shortcut is not shown.
        assertEquals(0, DrawerOps.pages(c, apps.drop(3), emptyMap()).getValue(DrawerCategorizer.INTERNET).count { it is DrawerItem.Shortcut })
        c = DrawerOps.removeShortcut(c, c.shortcuts.single().id)
        assertTrue(c.shortcuts.isEmpty())
    }
}
