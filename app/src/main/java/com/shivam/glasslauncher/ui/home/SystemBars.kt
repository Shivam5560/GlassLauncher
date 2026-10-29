package com.shivam.glasslauncher.ui.home

import android.app.Activity
import android.app.WallpaperManager
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Ultra-immersive mode (Phase 5): hides the status bar on home and in the drawer, plus the
 * navigation bar when Glass Gestures replace it (HyperOS forces 3-button navigation for third-party
 * launchers, so without the service the buttons must stay). Bars come back transiently with a
 * swipe from the edge.
 */
object ImmersiveMode {
    fun apply(window: Window, enabled: Boolean, hideNavigation: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            if (hideNavigation) controller.hide(WindowInsetsCompat.Type.navigationBars())
            else controller.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}

/**
 * Wallpaper offsets for live wallpapers (KLWP and other scrolling wallpapers). Static wallpapers
 * keep the centred offset set in `MainActivity` so the wallpaper copy lines up for refraction.
 */
object WallpaperOffsets {
    fun scroll(activity: Activity, position: Float, pageCount: Int) {
        val token = activity.window.decorView.windowToken ?: return
        val steps = if (pageCount > 1) 1f / (pageCount - 1) else 0f
        val x = if (pageCount > 1) (position / (pageCount - 1)).coerceIn(0f, 1f) else 0.5f
        try {
            val wm = WallpaperManager.getInstance(activity)
            wm.setWallpaperOffsetSteps(steps, 0f)
            wm.setWallpaperOffsets(token, x, 0.5f)
        } catch (_: Exception) { }
    }
}
