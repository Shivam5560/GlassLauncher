package com.shivam.glasslauncher.polish

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.icons.IconOverrideStore
import com.shivam.glasslauncher.icons.IconVariant
import com.shivam.glasslauncher.shortcuts.PinnedShortcutStore
import com.shivam.glasslauncher.ui.glass.GlassRuntimeTheme
import com.shivam.glasslauncher.ui.glass.WallpaperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Phase 4 polish, wired up once from [GlassApp]: theme (default, from wallpaper, or the theme
 * editor's colours), icon style (light/dark/tinted pack variants), per-app icons, font, pinned
 * shortcuts and the bundled wallpapers with automatic light/dark switching.
 */
class Polish(private val app: GlassApp) {

    val settings = PolishRepository(app, app.scope)
    val iconOverrides = IconOverrideStore(app, app.scope)
    val shortcuts = PinnedShortcutStore(app, app.scope)
    val wallpapers = GlassWallpapers(app)

    private val _night = MutableStateFlow(isNight(app.resources.configuration))
    /** System dark mode. */
    val night: StateFlow<Boolean> = _night.asStateFlow()

    private val _wallpaperPalette = MutableStateFlow(PaletteExtractor.DEFAULT)
    /** Colours derived from the current wallpaper (shown in the theme editor). */
    val wallpaperPalette: StateFlow<ThemePalette> = _wallpaperPalette.asStateFlow()

    private val wallpaperLock = Mutex()

    fun start() {
        iconOverrides.load()
        shortcuts.load()
        app.icons.customDir = iconOverrides.customDir
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) { _night.value = isNight(newConfig) }
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })

        app.scope.launch { iconOverrides.overrides.collect { app.icons.setOverrides(it) } }

        app.scope.launch {
            app.wallpaper.snapshot.collect { snap ->
                _wallpaperPalette.value = withContext(Dispatchers.Default) { paletteOf(snap) }
            }
        }

        // Theme + icon style.
        app.scope.launch {
            combine(settings.state.filterNotNull(), _night, _wallpaperPalette) { s, n, p -> Triple(s, n, p) }.collect { (s, n, palette) ->
                val (accent, tint) = when (s.themeSource) {
                    ThemeSource.Default -> GlassRuntimeTheme.DefaultAccent.toArgb() to null
                    ThemeSource.Wallpaper -> palette.accent to palette.glassTint
                    ThemeSource.Manual -> s.manualAccent to s.manualTint
                }
                GlassRuntimeTheme.accent = Color(accent)
                GlassRuntimeTheme.wallpaperGlassTint = if (s.themeSource == ThemeSource.Manual) null else Color(palette.glassTint)
                if (tint != null) {
                    val glass = app.settings.state.value?.glass
                    if (glass != null && glass.tintColor != tint) app.settings.setGlass(glass.copy(tintColor = tint))
                }
                val variant = when (s.iconStyle) {
                    IconStyle.Auto -> if (n) IconVariant.Dark else IconVariant.Light
                    IconStyle.Light -> IconVariant.Light
                    IconStyle.Dark -> IconVariant.Dark
                    IconStyle.Tinted -> IconVariant.Tinted
                }
                app.icons.setStyle(variant, if (s.tintedIconColor != 0) s.tintedIconColor else accent)
            }
        }

        // Font.
        app.scope.launch {
            settings.state.filterNotNull().map { it.fontId }.distinctUntilChanged().collect { id ->
                GlassRuntimeTheme.fontFamily = withContext(Dispatchers.IO) { Fonts.family(app, id) }
            }
        }

        // Bundled wallpaper: follow the system light/dark theme.
        app.scope.launch {
            combine(settings.state.filterNotNull(), _night) { s, n -> s to n }.collect { (s, n) -> syncWallpaper(s, n) }
        }
    }

    private suspend fun syncWallpaper(s: PolishSettings, night: Boolean) {
        val id = s.wallpaperId ?: return
        if (!s.wallpaperAutoDark) return
        val want = if (night) "dark" else "light"
        if (s.wallpaperApplied == want) return
        val current = withContext(Dispatchers.IO) { app.wallpaper.currentWallpaperId() }
        if (current != null && s.wallpaperSystemId != 0 && current != s.wallpaperSystemId) {
            // The user set another wallpaper outside Glass: stop switching.
            settings.setWallpaperApplied(null, null, 0, false)
            return
        }
        applyWallpaper(id, want, s.wallpaperLockToo)
    }

    /** Sets a bundled wallpaper through Glass (Path A, so refraction works). */
    suspend fun applyWallpaper(id: String, mode: String, lockToo: Boolean): Result<Unit> = wallpaperLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val bmp = wallpapers.loadFull(id, mode) ?: error("Wallpaper $id missing")
                // Full-size (about 18 MB) decode: free it as soon as WallpaperManager has it.
                val systemId = try { app.wallpaper.setWallpaperBitmap(bmp, lockToo) } finally { bmp.recycle() }
                settings.setWallpaperApplied(id, mode, systemId, lockToo)
            }.onFailure { Log.w(TAG, "Could not set wallpaper $id", it) }
        }
    }

    private fun paletteOf(snap: WallpaperSource.Snapshot): ThemePalette {
        snap.image?.let { img ->
            return try {
                val bmp = img.asAndroidBitmap()
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                PaletteExtractor.fromPixels(px, step = 3)
            } catch (e: Exception) {
                PaletteExtractor.DEFAULT
            }
        }
        val colors: WallpaperColors = try {
            WallpaperManager.getInstance(app).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        } catch (e: Exception) {
            null
        } ?: return PaletteExtractor.DEFAULT
        return PaletteExtractor.fromWallpaperColors(
            colors.primaryColor.toArgb(),
            colors.secondaryColor?.toArgb(),
            colors.tertiaryColor?.toArgb(),
            colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0,
        )
    }

    companion object {
        private const val TAG = "GlassPolish"
        fun isNight(c: Configuration) = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
