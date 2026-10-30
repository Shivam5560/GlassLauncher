package com.shivam.glasslauncher.overlay

/** AUTO yields to firmware with a native island; it never modifies an OEM feature flag. */
enum class IslandCompatibility(val title: String) {
    AUTO("Automatic"), SYSTEM("System island"), GLASS("Glass island");

    fun allowsGlass(nativeIsland: Boolean): Boolean = when (this) {
        AUTO -> !nativeIsland
        SYSTEM -> false
        GLASS -> true
    }
}

/**
 * HyperOS's own island (`support_dynamic_island`). In GLASS mode Glass turns it off so only one island
 * shows, and puts the original value back when the user leaves GLASS mode. Needs WRITE_SECURE_SETTINGS.
 */
object NativeIslandFlag {
    private const val KEY = "support_dynamic_island"
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("native_island", android.content.Context.MODE_PRIVATE)
    fun uri(): android.net.Uri = android.provider.Settings.Global.getUriFor(KEY)

    /** Whether the firmware has an island, even while Glass has switched it off. */
    fun present(c: android.content.Context): Boolean = prefs(c).getBoolean("owned", false) ||
        try { android.provider.Settings.Global.getInt(c.contentResolver, KEY, 0) == 1 } catch (_: Exception) { false }

    fun suppress(c: android.content.Context) {
        val cr = c.contentResolver
        try {
            val p = prefs(c)
            if (!p.getBoolean("owned", false)) {
                val original = android.provider.Settings.Global.getInt(cr, KEY, -1)
                if (original != 1) return // no native island to hide
                p.edit().putBoolean("owned", true).putInt("original", original).commit()
            }
            if (android.provider.Settings.Global.getInt(cr, KEY, 0) != 0) android.provider.Settings.Global.putInt(cr, KEY, 0)
        } catch (_: RuntimeException) { }
    }

    fun restore(c: android.content.Context) {
        val p = prefs(c)
        if (!p.getBoolean("owned", false)) return
        try {
            android.provider.Settings.Global.putInt(c.contentResolver, KEY, p.getInt("original", 1))
            p.edit().clear().commit()
        } catch (_: RuntimeException) { }
    }
}
