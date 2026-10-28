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

    override fun onCreate() {
        super.onCreate()
        apps.start()
        layout.load()
        wallpaper.start()
        scope.launch {
            settings.state.filterNotNull().map { it.iconPack }.distinctUntilChanged().collect { icons.setIconPack(it) }
        }
        scope.launch {
            apps.packageChanges.collect { if (it > 0) icons.invalidatePackage(apps.lastChangedPackage) }
        }
    }

    companion object {
        fun from(context: Context) = context.applicationContext as GlassApp
    }
}
