package com.shivam.glasslauncher.backup

import com.shivam.glasslauncher.icons.IconOverride
import com.shivam.glasslauncher.icons.IconOverrideStore
import com.shivam.glasslauncher.shortcuts.PinnedShortcut
import com.shivam.glasslauncher.shortcuts.PinnedShortcutStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCodecTest {

    private val layout = """{"version":1,"pages":[[{"t":"app","k":"com.a/.Main"},null,{"t":"folder","id":"f1","n":"Social","apps":["com.b/.B","com.c/.C"]}]],"dock":[{"t":"app","k":"com.d/.D"},null,null,null],"labels":{},"known":["com.a/.Main"]}"""

    private val sample = BackupData(
        createdAt = 1_760_000_000_000L,
        appVersion = "0.1.0",
        files = mapOf(
            "layout.json" to layout,
            IconOverrideStore.FILE_NAME to IconOverrideStore.encode(mapOf(
                "com.a/.Main" to IconOverride.Pack("com.shivam.glassicons", "phone"),
                "com.d/.D" to IconOverride.Custom("abc.png"),
            )),
            PinnedShortcutStore.FILE_NAME to PinnedShortcutStore.encode(listOf(PinnedShortcut("com.android.chrome", "site-1", "News", 0L))),
        ),
        prefs = mapOf(
            "settings" to listOf(
                PrefEntry("glass_blur", 28.5f),
                PrefEntry("glass_tint", 0xFF1E2028.toInt()),
                PrefEntry("icon_pack", "com.shivam.glassicons"),
                PrefEntry("hidden_apps", setOf("com.x/.X", "com.y/.Y")),
                PrefEntry("locked_apps", setOf("com.z/.Z")),
                PrefEntry("show_labels", true),
            ),
            "polish" to listOf(
                PrefEntry("icon_style", "Tinted"),
                PrefEntry("big_number", 1234567890123L),
                PrefEntry("ratio", 0.125),
            ),
        ),
        customIcons = mapOf("abc.png" to byteArrayOf(-119, 80, 78, 71, 0, 1, 2, 3)),
    )

    @Test
    fun roundTripKeepsEverything() {
        val back = BackupCodec.decode(BackupCodec.encode(sample))
        assertEquals(sample.createdAt, back.createdAt)
        assertEquals(sample.appVersion, back.appVersion)
        assertEquals(sample.files, back.files)
        assertEquals(sample.prefs, back.prefs)
        assertArrayEquals(sample.customIcons["abc.png"], back.customIcons["abc.png"])
        assertEquals(sample, back)
        // Types survive (a Float must not come back as Double, an Int not as Long).
        val prefs = back.prefs.getValue("settings").associate { it.name to it.value }
        assertEquals(28.5f, prefs["glass_blur"])
        assertEquals(0xFF1E2028.toInt(), prefs["glass_tint"])
        assertEquals(1234567890123L, back.prefs.getValue("polish").first { it.name == "big_number" }.value)
    }

    @Test
    fun nestedStoresRoundTrip() {
        val back = BackupCodec.decode(BackupCodec.encode(sample))
        val overrides = IconOverrideStore.decode(back.files.getValue(IconOverrideStore.FILE_NAME))
        assertEquals(IconOverride.Pack("com.shivam.glassicons", "phone"), overrides["com.a/.Main"])
        assertEquals(IconOverride.Custom("abc.png"), overrides["com.d/.D"])
        assertEquals("News", PinnedShortcutStore.decode(back.files.getValue(PinnedShortcutStore.FILE_NAME)).single().label)
    }

    @Test
    fun summaryForPreview() {
        val s = BackupCodec.summarize(sample)
        assertEquals(4, s.homeApps)
        assertEquals(1, s.folders)
        assertEquals(2, s.hiddenApps)
        assertEquals(1, s.lockedApps)
        assertEquals(9, s.settingsCount)
        assertEquals(1, s.customIcons)
        assertEquals(2, s.iconOverrides)
        assertEquals(1, s.pinnedShortcuts)
        assertEquals("com.shivam.glassicons", s.iconPack)
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsOtherJson() {
        BackupCodec.decode("""{"hello":"world"}""")
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsFutureVersion() {
        BackupCodec.decode(BackupCodec.encode(sample).replace("\"version\": 1", "\"version\": 99"))
    }

    @Test
    fun dropsPathTraversalNames() {
        val evil = BackupCodec.encode(sample.copy(files = mapOf("../evil.json" to "{}", "ok.json" to "{}"), customIcons = mapOf("../../x.png" to byteArrayOf(1))))
        val back = BackupCodec.decode(evil)
        assertEquals(setOf("ok.json"), back.files.keys)
        assertFalse(back.customIcons.containsKey("../../x.png"))
    }
}
