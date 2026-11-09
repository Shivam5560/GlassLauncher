package com.shivam.glasslauncher.polish

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WallpaperCatalogTest {
    @Test fun everyDesignHasUniqueIdCompletePairsAndPalette() {
        val dir = listOf(File("src/main/assets/wallpapers"), File("app/src/main/assets/wallpapers")).first { it.isDirectory }
        val root = JSONObject(File(dir, "index.json").readText())
        assertEquals(1440, root.getInt("width")); assertEquals(3200, root.getInt("height"))
        val entries = root.getJSONArray("wallpapers")
        assertTrue(entries.length() >= 20)
        val ids = mutableSetOf<String>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val id = entry.getString("id")
            assertTrue(id.matches(Regex("[a-z0-9]+"))); assertTrue(ids.add(id))
            for (mode in listOf("light", "dark")) {
                assertEquals(3, entry.getJSONObject("palette").getJSONArray(mode).length())
                for (suffix in listOf("", "_thumb")) {
                    val file = File(dir, "${id}_${mode}${suffix}.webp")
                    assertTrue("Missing $file", file.length() > 20)
                    assertTrue("Asset budget exceeded: $file", file.length() < 1_000_000)
                    val bytes = file.inputStream().use { it.readNBytes(12) }
                    assertEquals("RIFF", String(bytes, 0, 4)); assertEquals("WEBP", String(bytes, 8, 4))
                }
            }
            assertTrue(entry.getDouble("focusX") in 0.0..1.0)
            assertTrue(entry.getDouble("focusY") in 0.0..1.0)
        }
    }
}
