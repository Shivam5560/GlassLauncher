package com.shivam.glasslauncher.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.shivam.glasslauncher.data.GestureSettings
import com.shivam.glasslauncher.data.SettingsRepository
import com.shivam.glasslauncher.overlay.OverlayController
import com.shivam.glasslauncher.overlay.OverlayRuntime
import com.shivam.glasslauncher.ui.glass.GlassBubbleView
import com.shivam.glasslauncher.ui.glass.GlassPillView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * "Glass Gestures": thin TYPE_ACCESSIBILITY_OVERLAY touch zones that turn swipes into global
 * actions, replacing HyperOS full-screen gestures (which are disabled while a third-party
 * launcher is the default home app):
 *  - swipe up from the bottom edge -> HOME
 *  - swipe up and hold ~300 ms -> RECENTS
 *  - swipe in from the left/right edge -> BACK (with a glass "stretch" bubble)
 * It also exposes [lockScreen] for the launcher's double-tap-to-lock. It never reads window
 * content (canRetrieveWindowContent=false).
 *
 * Phase 3: it also hosts the Dynamic Island, the top-corner swipes for Notification Center (left)
 * and Control Center (right) and both panels, all as accessibility overlays ([OverlayRuntime]).
 */
class GlassGestureService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private var config = GestureSettings()

    private var bottomZone: View? = null
    private var leftZone: View? = null
    private var rightZone: View? = null
    private var pill: GlassPillView? = null
    private var leftBubble: GlassBubbleView? = null
    private var rightBubble: GlassBubbleView? = null
    private var overlays: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        overlays = OverlayRuntime.attachAccessibility(this)
        contentResolver.registerContentObserver(Settings.Global.getUriFor("force_fsg_nav_bar"), false, navObserver)
        // No window-state event arrives for the app already in front (e.g. Glass home after an update).
        rootInActiveWindow?.packageName?.toString()?.let {
            foregroundPackage = it
            overlays?.onForegroundApp(it, false)
        }
        val repo = SettingsRepository(applicationContext, scope)
        scope.launch {
            repo.flow.map { it.gestures }.distinctUntilChanged().collect {
                config = it
                hideSystemNavButtons()
                rebuild()
            }
        }
    }

    /**
     * HyperOS falls back to the 3-button bar while a third-party home is default. Its full-screen
     * gesture flag brings back gesture mode (no buttons); our pill replaces the system gesture line.
     * Needs `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` once; no-op without it.
     */
    /** HyperOS turns its gesture flag back off during boot (after we set it): set it again whenever it changes. */
    private val navObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (Settings.Global.getInt(contentResolver, "force_fsg_nav_bar", 0) != 1) hideSystemNavButtons()
        }
    }

    private fun hideSystemNavButtons() {
        try {
            Settings.Global.putInt(contentResolver, "force_fsg_nav_bar", 1)
            Settings.Global.putInt(contentResolver, "hide_gesture_line", if (config.showPill) 1 else 0)
        } catch (_: SecurityException) { }
    }

    private var foregroundPackage: String? = null
    private var activeAppWindow = -1
    private var keyboardUp = false
    private var paused = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                val cls = event.className?.toString() ?: return
                // Only real activities change the foreground app (not toasts, dialogs or the IME).
                val isActivity = try {
                    packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                    true
                } catch (e: Exception) { false }
                if (isActivity && pkg != foregroundPackage) {
                    foregroundPackage = pkg
                    overlays?.onForegroundApp(pkg, pkg in config.excludedApps)
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val ws = try { windows } catch (e: Exception) { emptyList() }
                overlays?.refreshLocked()
                keyboardUp = ws.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                // HyperOS skips the window-state event when Recents' "clear all" drops back to home, so the
                // app in front is also read from the active app window (only when that window changes).
                val app = ws.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
                if (app != null && app.id != activeAppWindow) {
                    activeAppWindow = app.id
                    val pkg = try { app.root?.packageName?.toString() } catch (e: Exception) { null }
                    if (pkg != null && pkg != foregroundPackage) {
                        foregroundPackage = pkg
                        overlays?.onForegroundApp(pkg, pkg in config.excludedApps)
                    }
                }
                // The status bar is a short TYPE_SYSTEM window at the top (the shade is full height).
                val bounds = Rect()
                overlays?.onStatusBar(ws.any {
                    it.type == AccessibilityWindowInfo.TYPE_SYSTEM &&
                        bounds.also(it::getBoundsInScreen).top <= 0 && bounds.height() in 1..dp(80f).toInt()
                })
                val screenH = resources.displayMetrics.heightPixels
                if (ws.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && bounds.also(it::getBoundsInScreen).top <= 0 && bounds.height() > screenH / 2 }) {
                    overlays?.onSystemShade()
                }
            }
        }
        updatePause()
    }

    private var edgesPaused = false

    /**
     * Excluded apps lose the back-swipe edge zones (the home swipe stays so you can always leave);
     * while the keyboard is up every zone and the pill are hidden.
     */
    private fun updatePause() {
        val c = config
        val keyboard = c.pauseWithKeyboard && keyboardUp
        val excluded = foregroundPackage != null && foregroundPackage in c.excludedApps
        val pauseEdges = keyboard || excluded
        if (keyboard != paused) {
            paused = keyboard
            val vis = if (keyboard) View.GONE else View.VISIBLE
            listOf(bottomZone, pill).forEach { it?.visibility = vis }
        }
        if (pauseEdges != edgesPaused) {
            edgesPaused = pauseEdges
            val vis = if (pauseEdges) View.GONE else View.VISIBLE
            listOf(leftZone, rightZone).forEach { it?.visibility = vis }
        }
    }
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(navObserver)
        OverlayRuntime.detachAccessibility(this)
        overlays = null
        removeAll()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun globalAction(action: Int): Boolean = performGlobalAction(action)

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun overlayParams(w: Int, h: Int, gravity: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "GlassGestures"
    }

    private fun safeAdd(v: View, p: WindowManager.LayoutParams) {
        try { wm.addView(v, p) } catch (e: Exception) { android.util.Log.w("GlassGestures", "addView failed", e) }
    }

    private fun removeAll() {
        listOf(bottomZone, leftZone, rightZone, pill, leftBubble, rightBubble).forEach { v ->
            if (v != null) try { wm.removeView(v) } catch (_: Exception) { }
        }
        bottomZone = null; leftZone = null; rightZone = null; pill = null; leftBubble = null; rightBubble = null
    }

    private fun rebuild() {
        removeAll()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val c = config
        val zoneH = dp(c.bottomZoneHeight).toInt()
        if (c.bottomEnabled) {
            val v = View(this)
            v.setOnTouchListener(BottomSwipe())
            safeAdd(v, overlayParams((screenW * c.bottomZoneWidth).toInt(), zoneH, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, true))
            bottomZone = v
        }
        if (c.showPill) {
            val p = GlassPillView(this)
            // View is larger than the capsule so its halo and stretch are not clipped.
            safeAdd(p, overlayParams(dp(140f).toInt(), dp(16f).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, false).apply { y = dp(3f).toInt() })
            pill = p
        }
        val edgeW = dp(c.edgeZoneWidth).toInt()
        val edgeH = (screenH * c.edgeZoneHeight).toInt()
        val bubbleW = dp(64f).toInt()
        if (c.leftEnabled) {
            val bubble = GlassBubbleView(this, fromLeft = true)
            safeAdd(bubble, overlayParams(bubbleW, screenH, Gravity.START or Gravity.TOP, false))
            leftBubble = bubble
            val v = View(this)
            v.setOnTouchListener(EdgeSwipe(fromLeft = true, bubble))
            safeAdd(v, overlayParams(edgeW, edgeH, Gravity.START or Gravity.BOTTOM, true).apply { y = zoneH })
            leftZone = v
        }
        if (c.rightEnabled) {
            val bubble = GlassBubbleView(this, fromLeft = false)
            safeAdd(bubble, overlayParams(bubbleW, screenH, Gravity.END or Gravity.TOP, false))
            rightBubble = bubble
            val v = View(this)
            v.setOnTouchListener(EdgeSwipe(fromLeft = false, bubble))
            safeAdd(v, overlayParams(edgeW, edgeH, Gravity.END or Gravity.BOTTOM, true).apply { y = zoneH })
            rightZone = v
        }
        paused = false
        edgesPaused = false
        updatePause()
    }

    private fun haptic(v: View) {
        if (config.haptics) v.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    }

    /** Distance a swipe must travel, scaled by sensitivity (higher sensitivity = shorter swipe). */
    private fun threshold(baseDp: Float) = dp(baseDp) / config.sensitivity.coerceIn(0.4f, 2.5f)

    private inner class BottomSwipe : View.OnTouchListener {
        private val handler = Handler(Looper.getMainLooper())
        private var downY = 0f
        private var downX = 0f
        private var armed = false
        private var fired = false
        private var lastMoveTime = 0L
        private var lastY = 0f
        private var vt: VelocityTracker? = null
        private val holdCheck = object : Runnable {
            override fun run() {
                if (!armed || fired) return
                val still = SystemClock.uptimeMillis() - lastMoveTime >= config.holdForRecentsMs
                if (still) {
                    fired = true
                    bottomZone?.let { haptic(it) }
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                } else {
                    handler.postDelayed(this, 40)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; lastY = e.rawY
                    armed = false; fired = false
                    lastMoveTime = SystemClock.uptimeMillis()
                    vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(e) }
                    pill?.brighten = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dy = downY - e.rawY
                    // Real fingers wobble: only movement beyond 12 dp restarts the hold timer.
                    if (abs(e.rawY - lastY) > dp(12f)) {
                        lastMoveTime = SystemClock.uptimeMillis()
                        lastY = e.rawY
                    }
                    pill?.translationY = -(dy.coerceIn(0f, dp(40f)) * 0.5f)
                    pill?.stretch = (dy / threshold(48f)).coerceIn(0f, 1f)
                    if (!armed && dy > threshold(48f) && dy > abs(e.rawX - downX)) {
                        armed = true
                        handler.postDelayed(holdCheck, 40)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    vt?.addMovement(e)
                    vt?.computeCurrentVelocity(1000)
                    val vy = -(vt?.yVelocity ?: 0f)
                    handler.removeCallbacks(holdCheck)
                    val dy = downY - e.rawY
                    val flung = vy > dp(900f) * config.sensitivity.coerceAtLeast(0.4f).let { 1f / it } && dy > dp(16f)
                    // iOS: a slow drag released mid-screen opens the app switcher; a quick flick goes home.
                    val slowLong = armed && !flung && vy < dp(700f) && dy > dp(120f)
                    if (!fired && slowLong) {
                        haptic(v)
                        performGlobalAction(GLOBAL_ACTION_RECENTS)
                    } else if (!fired && (armed || flung)) {
                        haptic(v)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    reset()
                }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(holdCheck); reset() }
            }
            return true
        }

        private fun reset() {
            armed = false
            vt?.recycle(); vt = null
            pill?.animate()?.translationY(0f)?.setDuration(220)?.setInterpolator(android.view.animation.OvershootInterpolator(1.6f))?.start()
            pill?.brighten = 0f
            pill?.stretch = 0f
        }
    }

    private inner class EdgeSwipe(private val fromLeft: Boolean, private val bubble: GlassBubbleView) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var vibrated = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val dx = if (fromLeft) e.rawX - downX else downX - e.rawX
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; vibrated = false
                    bubble.centerY = e.rawY
                    bubble.progress = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = dx / threshold(70f)
                    bubble.centerY = e.rawY
                    bubble.progress = p.coerceAtLeast(0f)
                    if (p >= 1f && !vibrated) { vibrated = true; haptic(v) }
                    if (p < 0.9f) vibrated = false
                }
                MotionEvent.ACTION_UP -> {
                    val horizontal = abs(dx) > abs(e.rawY - downY) * 0.8f
                    if (dx >= threshold(70f) && horizontal) performGlobalAction(GLOBAL_ACTION_BACK)
                    bubble.progress = 0f
                }
                MotionEvent.ACTION_CANCEL -> bubble.progress = 0f
            }
            return true
        }
    }

    companion object {
        @Volatile var instance: GlassGestureService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, GlassGestureService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
