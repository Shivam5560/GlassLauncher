package com.shivam.glasslauncher

import android.app.Application
import android.content.Context
import com.shivam.glasslauncher.data.AppRepository
import com.shivam.glasslauncher.data.LayoutRepository
import com.shivam.glasslauncher.data.SettingsRepository
import com.shivam.glasslauncher.data.UsageTracker
import com.shivam.glasslauncher.icons.IconRenderer
import com.shivam.glasslauncher.launch.AppActions
import com.shivam.glasslauncher.search.SearchEngine
import com.shivam.glasslauncher.ui.glass.WallpaperSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Process-wide singletons (no DI framework: the graph is small). */
class GlassApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings by lazy { SettingsRepository(this, scope) }
    val apps by lazy { AppRepository(this, scope) }
    val layout by lazy { LayoutRepository(this, scope) }
    val usage by lazy { UsageTracker(this, scope) }
    val icons by lazy { IconRenderer(this, scope) }
    val wallpaper by lazy { WallpaperSource(this, scope) }
    val search by lazy { SearchEngine(this) }
    val actions by lazy { AppActions(this) }
    /** Phase 4: theme, fonts, icon style and per-app icons, wallpapers, pinned shortcuts. */
    val polish by lazy { com.shivam.glasslauncher.polish.Polish(this) }

    override fun onCreate() {
        super.onCreate()
        apps.start()
        layout.load()
        wallpaper.start()
        polish.start()
        scope.launch {
            settings.state.filterNotNull().map { it.trueTone to it.trueToneStrength }.distinctUntilChanged()
                .collect { (on, strength) -> com.shivam.glasslauncher.ui.glass.TrueTone.configure(this@GlassApp, on, strength) }
        }
        scope.launch {
            settings.state.filterNotNull().map { it.iconPack }.distinctUntilChanged().collect { icons.setIconPack(it) }
        }
        scope.launch {
            apps.packageChanges.collect { if (it > 0) icons.invalidatePackage(apps.lastChangedPackage) }
        }
        scope.launch {
            settings.state.filterNotNull().map { it.appearance.iconShape }.distinctUntilChanged().collect { icons.setShape(it) }
        }
        scope.launch {
            settings.state.filterNotNull().map { it.greyscaleIcons }.distinctUntilChanged().collect { icons.setGreyscale(it) }
        }
        scope.launch {
            settings.state.filterNotNull().map { it.systemAppIcons }.distinctUntilChanged().collect { icons.setSystemAppIcons(it) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Background or worse: drop most icon bitmaps (they re-render from the same sources on demand).
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) icons.trimCache()
    }

    companion object {
        const val GLASS_ICONS_PACKAGE = "com.shivam.glassicons"
        fun from(context: Context) = context.applicationContext as GlassApp
    }
}
