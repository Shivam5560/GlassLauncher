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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.shivam.glasslauncher.overlay.controlcenter.ControlCenterPanel
import com.shivam.glasslauncher.overlay.controlcenter.SystemControls
import com.shivam.glasslauncher.overlay.island.CutoutInfo
import com.shivam.glasslauncher.overlay.island.IslandEngine
import com.shivam.glasslauncher.overlay.island.IslandKind
import com.shivam.glasslauncher.overlay.island.IslandWindow
import com.shivam.glasslauncher.overlay.notifications.LockScreenNotifications
import com.shivam.glasslauncher.ui.glass.OverlayWallpaperBackdrop
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
 * The island hides in landscape (and shows only live activities when locked), while the status bar is hidden (fullscreen
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

    private var screenInteractive = service.getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
    private var locked = service.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
    private var statusBarHidden = false
    private var landscape = false
    private var gameInFront = false
    private var excludedInFront = false
    /** Glass home hides the status bar by choice (immersive), not for video: keep island and corners. */
    private var homeInFront = false
    private val fullscreen get() = statusBarHidden && !homeInFront
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
        scope.launch { settingsFlow.map { it.island }.distinctUntilChanged().collect { island?.setSettings(it); updateHidden() } }
        scope.launch { engine.signals.locked.collect { locked = it; updateHidden(); updateLockScreen(); rebuildCorners() } }
        scope.launch { engine.signals.interactive.collect { screenInteractive = it; updateHidden(); updateLockScreen(); rebuildCorners() } }
        if (accessibility != null) {
            scope.launch {
                com.shivam.glasslauncher.overlay.notifications.NotificationHub.entries
                    .map { es -> es.any { com.shivam.glasslauncher.overlay.island.IslandClassifier.classify(it)?.kind.let { k -> k == IslandKind.IncomingCall || k == IslandKind.OngoingCall } } }
                    .distinctUntilChanged()
                    .collect { callActive = it; updateLockScreen() }
            }
            scope.launch {
                settingsFlow.map { listOf(it.loaded, it.controlCenter, it.notificationCenter, it.lockScreenCards, it.cornerWidth, it.cornerHeight) }
                    .distinctUntilChanged()
                    .collect { rebuildCorners(); updateLockScreen() }
            }
        }
        if (accessibility != null) {
            service.contentResolver.registerContentObserver(NativeIslandFlag.uri(), false, nativeIslandObserver)
            scope.launch {
                settingsFlow.map { it.loaded to (it.island.enabled && it.island.compatibility == IslandCompatibility.GLASS) }
                    .distinctUntilChanged()
                    .collect { (loaded, glassOnly) ->
                        suppressNative = loaded && glassOnly
                        if (!loaded) return@collect
                        if (glassOnly) NativeIslandFlag.suppress(service) else NativeIslandFlag.restore(service)
                    }
            }
        }
        OverlayRuntime.calibrating.let { flow -> scope.launch { flow.collect { island?.setCalibrating(it) } } }
        if (accessibility != null) {
            // Preserve the dialer's incoming-call experience while Glass home is in front.
            scope.launch {
                com.shivam.glasslauncher.overlay.notifications.NotificationHub.posted.collect { e ->
                    if (homeInFront && com.shivam.glasslauncher.overlay.island.IslandClassifier.classify(e)?.kind == IslandKind.IncomingCall) {
                        com.shivam.glasslauncher.overlay.notifications.NotificationHub.openFullScreen(service, e.key)
                    }
                }
            }
        }
    }

    private var suppressNative = false

    /** HyperOS switches its island back on at boot: switch it off again while GLASS mode wants one island. */
    private val nativeIslandObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { if (suppressNative) NativeIslandFlag.suppress(service) }
    }

    fun detach() {
        try { service.contentResolver.unregisterContentObserver(nativeIslandObserver) } catch (_: Exception) { }
        lockWindow?.destroy()
        lockWindow = null
        closePanel(immediate = true)
        panelWindow?.destroy()
        panelWindow = null
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
        // HyperOS declares the punch hole as a strip down to the status bar's bottom (80 x 156 px on
        // the Turbo 5); the hole is round, so its width is the real size.
        val info = rect?.let { CutoutInfo(it.exactCenterX(), it.exactCenterY(), it.width().toFloat(), minOf(it.width(), it.height()).toFloat(), it.bottom.toFloat()) }
        island?.setScreen(bounds.width(), info)
        updateHidden()
    }

    /** Status bar visibility from a 1-px, untouchable overlay's insets: hidden = fullscreen video. */
    @SuppressLint("ClickableViewAccessibility")
    private fun addProbe() {
        val v = View(service)
        v.setOnApplyWindowInsetsListener { _, insets ->
            // Accessibility overlays get stale insets on HyperOS; that host uses [onStatusBar].
            if (accessibility == null) setStatusBarHidden(!insets.isVisible(WindowInsets.Type.statusBars()))
            insets
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 1, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Must overlap the status bar: insets only report a bar's visibility to windows it covers.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GlassInsetsProbe"
        }
        try {
            wm.addView(v, p)
            probe = v
        } catch (_: Exception) { }
    }

    /** Last corner touched and when, to tell a corner pull from a plain status bar pull. */
    private var cornerDown: Pair<PanelKind, Long>? = null

    /**
     * From the accessibility service when the system shade appears. In apps SystemUI steals
     * top-edge drags from our corner zones, so if a corner was just touched we swap its shade for our panel.
     */
    fun onSystemShade() {
        val (kind, at) = cornerDown ?: return
        cornerDown = null
        if (android.os.SystemClock.uptimeMillis() - at > 800) return
        // Our panel may already be up (the corner fired too): the shade under it would be left behind on close.
        accessibility?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        if (panelKind == null) openPanel(kind)
    }

    /** From the accessibility service on window changes: HyperOS doesn't always send USER_PRESENT on a swipe unlock. */
    fun refreshLocked() = engine.signals.updateLocked()

    /** From the accessibility service: whether the status bar window is on screen. */
    fun onStatusBar(visible: Boolean) = setStatusBarHidden(!visible)

    private fun setStatusBarHidden(hidden: Boolean) {
        if (hidden == statusBarHidden) return
        statusBarHidden = hidden
        updateHidden()
        rebuildCorners()
    }

    private fun updateHidden() {
        val reason = when {
            !settingsFlow.value.loaded -> "loading"
            !screenInteractive -> "screen-off"
            // The app behind the keyguard doesn't count, and HyperOS reports no status bar window while locked.
            excludedInFront && !locked -> "excluded"
            landscape -> "landscape"
            fullscreen && !locked -> "fullscreen"
            gameInFront && !locked -> "game"
            else -> null
        }
        island?.setLocked(locked)
        island?.setHidden(reason)
    }

    // --- lock screen notifications (accessibility host only) ---

    private var lockWindow: ComposeOverlayWindow? = null

    private val lockHost = object : PanelHost {
        override val context: Context get() = service
        override fun close() = Unit
        override fun launch(intent: Intent?): Boolean = host.launch(intent)
        override fun openSystemQuickSettings() = Unit
        override fun switchPanel() = Unit
    }

    /**
     * Privacy-safe glass cards supplement the lock screen only when its native list is off.
     */
    private var wasLocked = false
    /** A call is ringing or live (same classification as the island's call activities). */
    private var callActive = false

    private fun updateLockScreen() {
        // A panel left open when the phone locks would sit behind the lock cards (two backgrounds).
        if (locked && !wasLocked) closePanel(immediate = true)
        wasLocked = locked
        val prefs = settingsFlow.value
        // Never disable SystemUI's notifications. Only supplement a lock screen whose native list
        // is already off; otherwise leave its layout and privacy policy in control.
        val nativeList = try {
            android.provider.Settings.Secure.getInt(service.contentResolver, "lock_screen_show_notifications", 1) != 0
        } catch (_: Exception) { true }
        val show = com.shivam.glasslauncher.overlay.notifications.shouldShowLockCards(
            optedIn = prefs.lockScreenCards, nativeNotificationsShown = nativeList, locked = locked,
            ready = prefs.loaded && prefs.notificationCenter && accessibility != null && screenInteractive,
            callActive = callActive,
        )
        if (show == (lockWindow != null)) return
        if (!show) {
            lockJob?.cancel()
            lockWindow?.destroy()
            lockWindow = null
            return
        }
        val bounds = wm.currentWindowMetrics.bounds
        val params = ComposeOverlayWindow.params(
            windowType,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            "GlassLockNotifications",
        ).apply {
            gravity = Gravity.TOP
            // Below HyperOS's lock clock, which grows to ~45% of the screen once its own list is off.
            y = (bounds.height() * 0.495f).toInt()
        }
        val maxHeight = with(androidx.compose.ui.unit.Density(density)) { (bounds.height() * 0.355f).toDp() }
        val w = ComposeOverlayWindow(service, params) {
            OverlayTheme {
                val snap by com.shivam.glasslauncher.GlassApp.from(service).wallpaper.snapshot.collectAsState()
                OverlayWallpaperBackdrop(snap) { LockScreenNotifications(lockHost, maxHeight) }
            }
        }
        if (!w.show()) return
        lockWindow = w
        // Unlock events are unreliable on HyperOS and a leftover window would eat home swipes:
        // poll the keyguard while the cards are up.
        lockJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(300)
                engine.signals.updateLocked()
                // The pattern/PIN pad needs the whole lock screen: hide the cards while it is up.
                lockWindow?.setVisible(!isBouncerUp())
            }
        }
    }

    /** Whether SystemUI's pattern/PIN/password pad is on screen (only a view-id lookup; no text is read). */
    private fun isBouncerUp(): Boolean {
        val service = accessibility ?: return false
        return try {
            service.windows.any { w ->
                w.root?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_bouncer_container")
                    ?.any { it.isVisibleToUser } == true
            }
        } catch (_: Exception) { false }
    }

    private var lockJob: Job? = null

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
        val home = pkg == service.packageName
        val changed = game != gameInFront || excluded != excludedInFront || home != homeInFront
        homeInFront = home
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
        if (!s.loaded || !screenInteractive || locked || fullscreen || gameInFront || excludedInFront) return
        val width = (wm.currentWindowMetrics.bounds.width() * s.cornerWidth.coerceIn(0.1f, 0.45f)).toInt()
        val height = dp(s.cornerHeight.coerceIn(12f, 60f))
        if (s.notificationCenter) leftCorner = addCorner(Gravity.TOP or Gravity.START, width, height, PanelKind.NotificationCenter)
        // Top-right pull = HyperOS's own control centre. Apps get it natively; Glass home hides the status
        // bar, so there a corner zone asks for it (openPanel(ControlCenter) → quick settings).
        if (s.controlCenter && homeInFront) rightCorner = addCorner(Gravity.TOP or Gravity.END, width, height, PanelKind.ControlCenter)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addCorner(gravity: Int, w: Int, h: Int, kind: PanelKind): View? {
        val v = View(service)
        var downX = 0f
        var downY = 0f
        var fired = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; fired = false
                    cornerDown = kind to android.os.SystemClock.uptimeMillis()
                }
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
    /** What the panel window composes; outlives [panelKind] so a hidden panel reopens without recomposing. */
    private var shownKind by mutableStateOf<PanelKind?>(null)
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
        override fun switchPanel() = openPanel(PanelKind.ControlCenter)
    }

    fun openPanel(kind: PanelKind) {
        if (accessibility == null || !settingsFlow.value.loaded) return
        if (service.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) {
            accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            return
        }
        // Glass's Control Center is retired (it could hang): HyperOS's own control centre opens instead.
        if (kind == PanelKind.ControlCenter) {
            closePanel(immediate = true)
            // HyperOS ignores GLOBAL_ACTION_QUICK_SETTINGS; StatusBarManager (EXPAND_STATUS_BAR) works.
            val opened = try {
                val sb = service.getSystemService("statusbar")
                sb.javaClass.getMethod("expandSettingsPanel").invoke(sb)
                true
            } catch (e: Exception) { false }
            if (!opened) accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            return
        }
        val s = settingsFlow.value
        if (kind == PanelKind.ControlCenter && !s.controlCenter) return
        if (kind == PanelKind.NotificationCenter && !s.notificationCenter) return
        panelKind = kind
        shownKind = kind
        updateHidden()
        panelWindow?.let { w -> if (w.root.visibility != android.view.View.VISIBLE) setPanelShown(w, true) }
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
                    when (shownKind) {
                        PanelKind.ControlCenter -> ControlCenterPanel(host, c, panelProgress)
                        PanelKind.NotificationCenter -> NotificationCenterPanel(host, panelProgress)
                        null -> Unit
                    }
                }
            }
            if (!w.show()) return
            panelWindow = w
            // iOS keeps the Dynamic Island over Notification Centre.
            island?.raise()
        }
        animatePanel(1f)
    }

    fun closePanel(immediate: Boolean = false) {
        if (panelWindow == null || panelKind == null) return
        if (immediate) {
            panelJob?.cancel()
            hidePanel()
            return
        }
        animatePanel(0f)
    }

    /**
     * Closing hides the panel window instead of removing it: re-adding it and composing the panel
     * from scratch made the first frame of every pull 90–180 ms (a visible hitch). While hidden it
     * is GONE (no surface or buffers), untouchable and unfocusable, and its composition stays warm.
     */
    private fun setPanelShown(w: ComposeOverlayWindow, shown: Boolean) {
        val off = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        w.params.flags = if (shown) w.params.flags and off.inv() else w.params.flags or off
        w.update()
        w.setVisible(shown)
    }

    private fun hidePanel() {
        panelWindow?.let { setPanelShown(it, false) }
        panelKind = null
        panelProgress = 0f
        scope.launch(AndroidUiDispatcher.Main) { progressAnim.snapTo(0f) }
        updateHidden()
    }

    private fun animatePanel(target: Float) {
        panelJob?.cancel()
        val blurOk = wm.isCrossWindowBlurEnabled
        // Notification Center already blurs its wallpaper once; avoid blurring the covered app too.
        val hasOwnBackdrop = panelKind == PanelKind.NotificationCenter && com.shivam.glasslauncher.GlassApp.from(service).wallpaper.snapshot.value.image != null
        val maxBlur = if (hasOwnBackdrop) 0 else dp(36f)
        // AndroidUiDispatcher.Main carries the frame clock Compose animations need outside composition.
        // Blur is set once per open/close: updating the window every frame (an IPC to the window
        // manager) stalled HyperOS and left frozen, blurred panels on screen.
        panelWindow?.let { w ->
            if (blurOk && target > 0f && w.params.blurBehindRadius != maxBlur) {
                w.params.blurBehindRadius = maxBlur
                w.update()
            }
        }
        panelJob = scope.launch(AndroidUiDispatcher.Main) {
            if (target == 0f) panelWindow?.let { w ->
                if (blurOk) { w.params.blurBehindRadius = 0; w.update() }
            }
            // Never leave a panel behind if the frame clock stalls: close after 1.5 s regardless.
            kotlinx.coroutines.withTimeoutOrNull(1_500) {
                progressAnim.animateTo(target, spring(dampingRatio = if (target > 0f) 0.78f else 1f, stiffness = if (target > 0f) 320f else 600f)) {
                    panelProgress = value
                }
            }
            if (target == 0f) hidePanel()
        }
    }
}
