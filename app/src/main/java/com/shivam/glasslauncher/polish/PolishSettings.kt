package com.shivam.glasslauncher.polish

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phase 4 keeps its own DataStore so it never collides with the other phases' settings keys. */
val Context.polishStore: DataStore<Preferences> by preferencesDataStore(name = "polish")

/** Which variant of an icon pack's artwork to use (see README, "Icon pack variant convention"). */
enum class IconStyle(val title: String) { Auto("Auto"), Light("Light"), Dark("Dark"), Tinted("Tinted") }

/** Where accent and glass tint come from. */
enum class ThemeSource(val title: String) { Default("Default"), Wallpaper("Wallpaper"), Manual("Custom") }

data class PolishSettings(
    /** Phase 5: Dark by default when the selected pack provides a dark variant. */
    val iconStyle: IconStyle = IconStyle.Dark,
    /** Glyph colour for tinted icons; 0 = use the accent colour. */
    val tintedIconColor: Int = 0,
    val themeSource: ThemeSource = ThemeSource.Default,
    val manualAccent: Int = 0xFF0A84FF.toInt(),
    val manualTint: Int = 0xFF1E2028.toInt(),
    val fontId: String = Fonts.DEFAULT_ID,
    /** Bundled Glass wallpaper currently set through the picker, or null. */
    val wallpaperId: String? = null,
    val wallpaperAutoDark: Boolean = true,
    val wallpaperLockToo: Boolean = false,
    /** "light" / "dark": which version of [wallpaperId] is on screen now. */
    val wallpaperApplied: String? = null,
    /** WallpaperManager id of the wallpaper we set; auto-switching stops if the user changes it elsewhere. */
    val wallpaperSystemId: Int = 0,
)

class PolishRepository(private val context: Context, private val scope: CoroutineScope) {

    private object K {
        val iconStyle = stringPreferencesKey("icon_style")
        val tintedColor = intPreferencesKey("tinted_icon_color")
        val themeSource = stringPreferencesKey("theme_source")
        val accent = intPreferencesKey("manual_accent")
        val tint = intPreferencesKey("manual_tint")
        val font = stringPreferencesKey("font_id")
        val wallpaper = stringPreferencesKey("wallpaper_id")
        val wallpaperAuto = booleanPreferencesKey("wallpaper_auto_dark")
        val wallpaperLock = booleanPreferencesKey("wallpaper_lock_too")
        val wallpaperApplied = stringPreferencesKey("wallpaper_applied")
        val wallpaperSystemId = intPreferencesKey("wallpaper_system_id")
    }

    val flow: Flow<PolishSettings> = context.polishStore.data.map { p ->
        val d = PolishSettings()
        PolishSettings(
            iconStyle = p[K.iconStyle]?.let { v -> IconStyle.entries.firstOrNull { it.name == v } } ?: d.iconStyle,
            tintedIconColor = p[K.tintedColor] ?: d.tintedIconColor,
            themeSource = p[K.themeSource]?.let { v -> ThemeSource.entries.firstOrNull { it.name == v } } ?: d.themeSource,
            manualAccent = p[K.accent] ?: d.manualAccent,
            manualTint = p[K.tint] ?: d.manualTint,
            fontId = p[K.font] ?: d.fontId,
            wallpaperId = p[K.wallpaper],
            wallpaperAutoDark = p[K.wallpaperAuto] ?: d.wallpaperAutoDark,
            wallpaperLockToo = p[K.wallpaperLock] ?: d.wallpaperLockToo,
            wallpaperApplied = p[K.wallpaperApplied],
            wallpaperSystemId = p[K.wallpaperSystemId] ?: 0,
        )
    }

    val state: StateFlow<PolishSettings?> = flow.stateIn(scope, SharingStarted.Eagerly, null)

    private fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch { context.polishStore.edit { block(it) } }
    }

    fun setIconStyle(v: IconStyle) = edit { it[K.iconStyle] = v.name }
    fun setTintedIconColor(v: Int) = edit { it[K.tintedColor] = v }
    fun setThemeSource(v: ThemeSource) = edit { it[K.themeSource] = v.name }
    fun setManualAccent(v: Int) = edit { it[K.accent] = v }
    fun setManualTint(v: Int) = edit { it[K.tint] = v }
    fun setFont(id: String) = edit { it[K.font] = id }
    fun setWallpaperAutoDark(v: Boolean) = edit { it[K.wallpaperAuto] = v }

    fun setWallpaperApplied(id: String?, mode: String?, systemId: Int, lockToo: Boolean) = edit {
        if (id == null) {
            it.remove(K.wallpaper); it.remove(K.wallpaperApplied); it.remove(K.wallpaperSystemId)
        } else {
            it[K.wallpaper] = id
            it[K.wallpaperApplied] = mode ?: "light"
            it[K.wallpaperSystemId] = systemId
            it[K.wallpaperLock] = lockToo
        }
    }
}
