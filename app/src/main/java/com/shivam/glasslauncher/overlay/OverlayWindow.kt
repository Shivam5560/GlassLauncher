package com.shivam.glasslauncher.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.ui.glass.GlassStyle
import com.shivam.glasslauncher.ui.glass.GlassTheme
import com.shivam.glasslauncher.ui.glass.GlassTone
import com.shivam.glasslauncher.ui.glass.rememberGlassLight

/** Lifecycle + saved state for a ComposeView living in a WindowManager window (no Activity). */
class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun pause() { if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.STARTED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}

/** Root for overlay windows: forwards Back to [onBack] (panels are focusable, so they get keys). */
@SuppressLint("ViewConstructor")
class OverlayRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    /** A touch outside this window (needs FLAG_WATCH_OUTSIDE_TOUCH). */
    var onOutsideTouch: (() -> Unit)? = null

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * A WindowManager window hosting Compose content. Works with TYPE_ACCESSIBILITY_OVERLAY (from the
 * Glass Gestures service) or TYPE_APPLICATION_OVERLAY (display-over-apps fallback).
 */
class ComposeOverlayWindow(
    private val context: Context,
    val params: WindowManager.LayoutParams,
    onBack: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner()
    val root = OverlayRoot(context, onBack)
    var attached = false
        private set

    init {
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }
        root.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
    }

    fun show(): Boolean {
        if (attached) return true
        return try {
            wm.addView(root, params)
            attached = true
            owner.resume()
            true
        } catch (e: Exception) {
            Log.w("GlassOverlay", "addView failed", e)
            false
        }
    }

    fun update() {
        if (!attached) return
        try { wm.updateViewLayout(root, params) } catch (e: Exception) { Log.w("GlassOverlay", "update failed", e) }
    }

    fun setVisible(visible: Boolean) {
        root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun destroy() {
        if (attached) {
            try { wm.removeViewImmediate(root) } catch (_: Exception) { }
            attached = false
        }
        owner.destroy()
    }

    companion object {
        fun params(type: Int, w: Int, h: Int, flags: Int, title: String) = WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT).apply {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            this.title = title
        }
    }
}

/** Glass theme for overlay windows: the user's glass style, biased to dark glass over other apps. */
@Composable
fun OverlayTheme(trueTone: Boolean = true, content: @Composable () -> Unit) {
    val app = GlassApp.from(LocalContext.current)
    val launcher by app.settings.state.collectAsState()
    val base = launcher?.glass ?: GlassStyle()
    val style = if (base.tone == GlassTone.Auto) base.copy(tone = GlassTone.Dark) else base
    val light = rememberGlassLight(style.tiltLighting)
    GlassTheme(style = style, light = light, fallbackLuminance = 0.2f, backdrop = null, darkMode = true, trueTone = trueTone, content = content)
}
