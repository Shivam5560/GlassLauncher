package com.shivam.glasslauncher.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.shivam.glasslauncher.overlay.controlcenter.ControlCenterPanel
import com.shivam.glasslauncher.overlay.controlcenter.SystemControls
import com.shivam.glasslauncher.overlay.island.CutoutInfo
import com.shivam.glasslauncher.overlay.island.IslandEngine
import com.shivam.glasslauncher.overlay.island.IslandWindow
import com.shivam.glasslauncher.overlay.notifications.NotificationCenterPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class PanelKind { ControlCenter, NotificationCenter }

/**
 * Phase 3 overlays for one window host:
 *  - with [accessibility] (Glass Gestures): TYPE_ACCESSIBILITY_OVERLAY island, the top-corner swipe
 *    zones and the Control Center / Notification Center panels;
 *  - without it (display-over-apps fallback, hosted by the notification listener): the island only,
 *    as TYPE_APPLICATION_OVERLAY, which sits below the status bar.
 * The island hides on the lock screen, in landscape, while the status bar is hidden (fullscreen
 * video) and while a game is in front.
 */
class OverlayController(
    private val service: Context,
    private val windowType: Int,
    private val accessibility: AccessibilityService?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val engine = IslandEngine.get(service)
    private val settingsFlow = OverlayPrefs.state(service)
    private var island: IslandWindow? = null
    private var probe: View? = null
    private var leftCorner: View? = null
    private var rightCorner: View? = null

    private var locked = false
    private var statusBarHidden = false
    private var landscape = false
    private var gameInFront = false
    private var excludedInFront = false
    private val gameCache = HashMap<String, Boolean>()

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            measureScreen()
            rebuildCorners()
        }
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = Unit
    }

    fun attach() {
        engine.acquire()
        island = IslandWindow(service, windowType, engine).also { it.show() }
        addProbe()
        measureScreen()
        service.registerComponentCallbacks(configCallbacks)
        scope.launch { engine.ui.collect { island?.update(it) } }
        scope.launch { settingsFlow.map { it.island }.distinctUntilChanged().collect { island?.setSettings(it) } }
        scope.launch { engine.signals.locked.collect { locked = it; updateHidden() } }
        if (accessibility != null) {
            scope.launch {
                settingsFlow.map { Triple(it.controlCenter, it.notificationCenter, it.cornerWidth to it.cornerHeight) }
                    .distinctUntilChanged()
                    .collect { rebuildCorners() }
            }
        }
        OverlayRuntime.calibrating.let { flow -> scope.launch { flow.collect { island?.setCalibrating(it) } } }
    }

    fun detach() {
        closePanel(immediate = true)
        try { service.unregisterComponentCallbacks(configCallbacks) } catch (_: Exception) { }
        removeCorners()
        probe?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) { } }
        probe = null
        island?.destroy()
        island = null
        engine.release()
        scope.cancel()
    }

    private fun dp(v: Float) = (v * density).toInt()

    // --- screen, cutout and visibility ---

    private fun measureScreen() {
        val metrics = wm.currentWindowMetrics
        val bounds = metrics.bounds
        landscape = bounds.width() > bounds.height()
        val cutout = metrics.windowInsets.displayCutout
        // The punch hole is the cutout rect touching the top edge, nearest the centre.
        val rect = cutout?.boundingRects
            ?.filter { it.top <= bounds.top + dp(4f) && it.height() > 0 }
            ?.minByOrNull { abs(it.centerX() - bounds.width() / 2) }
        val info = rect?.let { CutoutInfo(it.exactCenterX(), it.exactCenterY(), it.width().toFloat(), it.height().toFloat()) }
        island?.setScreen(bounds.width(), info)
        updateHidden()
    }

    /** Status bar visibility from a 1-px, untouchable overlay's insets: hidden = fullscreen video. */
    @SuppressLint("ClickableViewAccessibility")
    private fun addProbe() {
        val v = View(service)
        v.setOnApplyWindowInsetsListener { _, insets ->
            val hidden = !insets.isVisible(WindowInsets.Type.statusBars())
            if (hidden != statusBarHidden) {
                statusBarHidden = hidden
                updateHidden()
                rebuildCorners()
            }
            insets
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 1, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "GlassInsetsProbe"
        }
        try {
            wm.addView(v, p)
            probe = v
        } catch (_: Exception) { }
    }

    private fun updateHidden() {
        val reason = when {
            locked -> "lock screen"
            panelKind != null -> "panel"
            landscape -> "landscape"
            statusBarHidden -> "fullscreen"
            gameInFront -> "game"
            else -> null
        }
        island?.setHidden(reason)
    }

    /** From the accessibility service whenever the foreground app changes. */
    fun onForegroundApp(pkg: String?, excluded: Boolean) {
        engine.signals.updateLocked()
        val game = pkg != null && gameCache.getOrPut(pkg) {
            try {
                @Suppress("DEPRECATION")
                val info = service.packageManager.getApplicationInfo(pkg, 0)
                info.category == ApplicationInfo.CATEGORY_GAME
            } catch (_: Exception) { false }
        }
        val changed = game != gameInFront || excluded != excludedInFront
        gameInFront = game
        excludedInFront = excluded
        if (changed) {
            updateHidden()
            rebuildCorners()
        }
    }

    // --- top-corner swipe zones (accessibility host only) ---

    private fun removeCorners() {
        listOf(leftCorner, rightCorner).forEach { v -> v?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) { } } }
        leftCorner = null
        rightCorner = null
    }

    private fun rebuildCorners() {
        removeCorners()
        if (accessibility == null) return
        val s = settingsFlow.value
        // Keep the corners out of fullscreen games and video and the user's excluded apps.
        if (statusBarHidden || gameInFront || excludedInFront) return
        val width = (wm.currentWindowMetrics.bounds.width() * s.cornerWidth.coerceIn(0.1f, 0.45f)).toInt()
        val height = dp(s.cornerHeight.coerceIn(12f, 60f))
        if (s.notificationCenter) leftCorner = addCorner(Gravity.TOP or Gravity.START, width, height, PanelKind.NotificationCenter)
        if (s.controlCenter) rightCorner = addCorner(Gravity.TOP or Gravity.END, width, height, PanelKind.ControlCenter)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addCorner(gravity: Int, w: Int, h: Int, kind: PanelKind): View? {
        val v = View(service)
        var downX = 0f
        var downY = 0f
        var fired = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; fired = false }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (!fired && dy > dp(22f) && dy > abs(e.rawX - downX)) {
                        fired = true
                        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        openPanel(kind)
                    }
                }
            }
            true
        }
        val p = WindowManager.LayoutParams(
            w, h, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GlassCorner"
        }
        return try {
            wm.addView(v, p)
            v
        } catch (_: Exception) { null }
    }

    // --- panels ---

    private var panelKind by mutableStateOf<PanelKind?>(null)
    private var panelProgress by mutableFloatStateOf(0f)
    private var panelWindow: ComposeOverlayWindow? = null
    private val progressAnim = Animatable(0f)
    private var panelJob: Job? = null
    private var controls: SystemControls? = null

    private val host = object : PanelHost {
        override val context: Context get() = service
        override fun close() = closePanel()
        override fun launch(intent: Intent?): Boolean {
            if (intent == null) return false
            return try {
                service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                closePanel()
                true
            } catch (e: Exception) { false }
        }
        override fun openSystemQuickSettings() {
            closePanel(immediate = true)
            accessibility?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
        override fun switchPanel() {
            panelKind = if (panelKind == PanelKind.ControlCenter) PanelKind.NotificationCenter else PanelKind.ControlCenter
        }
    }

    fun openPanel(kind: PanelKind) {
        if (accessibility == null) return
        val s = settingsFlow.value
        if (kind == PanelKind.ControlCenter && !s.controlCenter) return
        if (kind == PanelKind.NotificationCenter && !s.notificationCenter) return
        panelKind = kind
        updateHidden()
        if (panelWindow == null) {
            val params = ComposeOverlayWindow.params(
                windowType,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                "GlassPanel",
            ).apply {
                @Suppress("DEPRECATION")
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                blurBehindRadius = 0
            }
            val c = controls ?: SystemControls(service).also { controls = it }
            val w = ComposeOverlayWindow(service, params, onBack = { closePanel() }) {
                OverlayTheme {
                    when (panelKind) {
                        PanelKind.ControlCenter -> ControlCenterPanel(host, c, panelProgress)
                        PanelKind.NotificationCenter -> NotificationCenterPanel(host, panelProgress)
                        null -> Unit
                    }
                }
            }
            if (!w.show()) return
            panelWindow = w
        }
        animatePanel(1f)
    }

    fun closePanel(immediate: Boolean = false) {
        if (panelWindow == null) return
        if (immediate) {
            panelJob?.cancel()
            destroyPanel()
            return
        }
        animatePanel(0f)
    }

    private fun destroyPanel() {
        panelWindow?.destroy()
        panelWindow = null
        panelKind = null
        panelProgress = 0f
        scope.launch(AndroidUiDispatcher.Main) { progressAnim.snapTo(0f) }
        updateHidden()
    }

    private fun animatePanel(target: Float) {
        panelJob?.cancel()
        val blurOk = wm.isCrossWindowBlurEnabled
        val maxBlur = dp(36f)
        // AndroidUiDispatcher.Main carries the frame clock Compose animations need outside composition.
        panelJob = scope.launch(AndroidUiDispatcher.Main) {
            progressAnim.animateTo(target, spring(dampingRatio = if (target > 0f) 0.78f else 1f, stiffness = if (target > 0f) 320f else 600f)) {
                panelProgress = value
                val w = panelWindow ?: return@animateTo
                if (blurOk) {
                    w.params.blurBehindRadius = (maxBlur * value.coerceIn(0f, 1f)).toInt()
                    w.update()
                }
            }
            if (target == 0f) destroyPanel()
        }
    }
}
