package com.shivam.glasslauncher

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import com.shivam.glasslauncher.settings.SettingsActivity
import com.shivam.glasslauncher.ui.LauncherViewModel
import com.shivam.glasslauncher.ui.home.LauncherScreen

/** The HOME activity. Its window is transparent and shows the system wallpaper. */
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) holdFirstFrames()
        setContent {
            LauncherScreen(
                vm = vm,
                homePresses = homePresses.intValue,
                openSettings = { screen -> startActivity(SettingsActivity.intent(this, screen)) },
            )
        }
    }

    /**
     * A recreated home (HyperOS swaps a system overlay when the navigation mode flips, e.g. around
     * payment apps) drew a few half-laid-out frames: clock shifted, icons and dock empty. Measure and
     * layout still run while the draw is held, so the first frame shown is the settled one.
     */
    private fun holdFirstFrames() {
        val v = window.decorView
        val until = android.os.SystemClock.uptimeMillis() + 300
        var passes = 0
        v.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (++passes < 4 && android.os.SystemClock.uptimeMillis() < until) return false
                v.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Keep the wallpaper centred (no parallax) so the in-app wallpaper copy lines up with it.
        try {
            val wm = WallpaperManager.getInstance(this)
            wm.setWallpaperOffsetSteps(0f, 0f)
            wm.setWallpaperOffsets(window.decorView.windowToken, 0.5f, 0.5f)
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        // Picks up newly granted wallpaper access (Path B) or a wallpaper changed elsewhere.
        vm.refreshWallpaper()
        com.shivam.glasslauncher.gestures.GlassGestureService.resumeAfterPaymentApp(this)
        // Give a normal (re)bind a moment before treating the service as dead.
        window.decorView.postDelayed({ com.shivam.glasslauncher.gestures.GestureGuard.ensureRunning(this) }, 2_000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses.intValue++
        }
    }
}
