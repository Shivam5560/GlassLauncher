package com.shivam.glasslauncher.overlay.island

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.shivam.glasslauncher.overlay.ComposeOverlayWindow
import com.shivam.glasslauncher.overlay.IslandSettings
import com.shivam.glasslauncher.overlay.OverlayTheme
import com.shivam.glasslauncher.overlay.notifications.MediaHub
import com.shivam.glasslauncher.overlay.notifications.NotificationHub
import kotlin.math.max
import kotlin.math.min

/** Where the camera is, in screen pixels (from DisplayCutout), or null without a cutout. */
data class CutoutInfo(val centerX: Float, val centerY: Float, val width: Float, val height: Float, val safeBottom: Float = centerY + height / 2f)

/**
 * Owns the island's overlay window: sizes it to the current shape (so touches elsewhere pass
 * through), positions it from the DisplayCutout plus the manual calibration, and turns taps,
 * long-presses and swipes into expand / open source app / dismiss-for-this-session.
 */
class IslandWindow(
    private val context: Context,
    private val windowType: Int,
    private val engine: IslandEngine,
) : IslandCallbacks {

    private val main = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    private var frame by mutableStateOf(
        IslandFrame(IslandPresentation.Hidden, IslandUi(), Rect.Zero, null, 0f),
    )
    private var origin by mutableStateOf(Offset.Zero)

    private val window = ComposeOverlayWindow(
        context,
        ComposeOverlayWindow.params(
            windowType, 1, 1,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            "GlassIsland",
        ).apply { gravity = Gravity.TOP or Gravity.START },
    ) {
        // The True Tone veil fills the whole window, including the transparent margin, so over a dark app
        // it reads as a square around the pill. The island draws its own black pill and needs no veil.
        OverlayTheme(trueTone = false) { IslandSurface(frame, origin, this) }
    }

    init {
        // Tapping anywhere outside an expanded island closes it, as on iOS.
        window.root.onOutsideTouch = { if (presentation() == IslandPresentation.Expanded) collapse() }
    }

    private var ui = IslandUi()
    private var settings = IslandSettings(enabled = false)
    private var cutout: CutoutInfo? = null
    private var screenW = 0
    private var hiddenReason: String? = null
    private var calibrating = false
    private var expandedId: String? = null
    /** The split activity the user tapped: shown in the main slot until it collapses. */
    private var promotedId: String? = null
    private var collapsedIds: Set<String> = emptySet()
    private var expandedAt = 0L
    private var windowRect = Rect.Zero
    private val shrink = Runnable { applyWindow(targetRect, allowShrink = true) }
    private var targetRect = Rect.Zero
    private val autoCollapse = Runnable {
        val p = slots().primary
        if (p != null && p.kind != IslandKind.IncomingCall && p.kind != IslandKind.Peek && SystemClock.elapsedRealtime() - expandedAt >= AUTO_COLLAPSE_MS - 50) {
            collapse()
        }
    }

    fun show() {
        window.show()
        render()
    }

    fun destroy() {
        main.removeCallbacks(shrink)
        main.removeCallbacks(autoCollapse)
        window.destroy()
    }

    fun update(ui: IslandUi) {
        this.ui = ui
        val live = setOfNotNull(ui.slots.primary?.id, ui.slots.secondary?.id)
        if (promotedId !in live) promotedId = null
        if (expandedId !in live) expandedId = null
        collapsedIds = collapsedIds.filterTo(HashSet()) { it in live }
        render()
    }

    /** The arbiter's slots, swapped while the user has the split activity open. */
    private fun slots(): IslandSlots {
        val s = ui.slots
        // Message previews would show their text to anyone holding the locked phone.
        if (locked && s.primary?.kind == IslandKind.Peek) return IslandSlots(s.secondary, null)
        return if (promotedId != null && s.secondary?.id == promotedId) IslandSlots(s.secondary, s.primary) else s
    }

    private var locked = false

    /** Locked: live activities (music, calls, timers) stay; previews and the empty idle pill hide. */
    fun setLocked(value: Boolean) { if (value != locked) { locked = value; render() } }

    fun setSettings(s: IslandSettings) { settings = s; render() }
    fun setScreen(widthPx: Int, cutout: CutoutInfo?) { screenW = widthPx; this.cutout = cutout; render() }
    /** Non-null hides the island (lock screen, fullscreen video or games, an open panel). */
    fun setHidden(reason: String?) { hiddenReason = reason; render() }
    fun setCalibrating(on: Boolean) { calibrating = on; render() }

    private fun presentation(): IslandPresentation {
        if (hiddenReason != null) return IslandPresentation.Hidden
        if (!settings.showsGlass) return IslandPresentation.Hidden
        val p = slots().primary
        if (p == null) {
            return if ((settings.showIdlePill && cutout != null && !locked) || calibrating) IslandPresentation.Idle else IslandPresentation.Hidden
        }
        if (p.id == expandedId) return IslandPresentation.Expanded
        if (p.kind.autoExpand && p.id !in collapsedIds) return IslandPresentation.Expanded
        return IslandPresentation.Compact
    }

    private fun dp(v: Float) = v * density

    private fun render() {
        if (screenW <= 0) return
        val pres = presentation()
        val s = settings
        val c = cutout
        val h = max((c?.height ?: 0f) + dp(12f), dp(34f)) * s.scale.coerceIn(0.6f, 1.8f)
        val cx = (c?.centerX ?: (screenW / 2f)) + dp(s.offsetX)
        val cy = (c?.centerY ?: dp(18f)) + dp(s.offsetY)
        val idleW = h * 3.1f
        val slots = slots()
        val primary = slots.primary
        val desiredCompactW = when (primary?.kind) {
            // Music keeps the idle pill's size: art and waves fit either side of the camera.
            null, IslandKind.Music -> idleW
            // Narrow enough to leave HyperOS's status bar icons (speed, VoLTE) uncovered.
            IslandKind.Navigation, IslandKind.Peek -> h * 5.0f
            IslandKind.Charging, IslandKind.Ringer -> h * 4.2f
            else -> h * 4.6f
        }
        val margin = dp(10f)
        val compactW = min(desiredCompactW, screenW - 2 * margin)
        val pill = when (pres) {
            IslandPresentation.Expanded -> {
                val w = min(screenW - 2 * margin, dp(420f))
                val cameraBand = IslandGeometry.contentTop(c?.safeBottom?.div(density), (cy - h / 2f) / density, h / density)
                val peek = primary?.content as? IslandContent.Peek
                val eh = if (peek != null) dp(IslandGeometry.peekHeight(cameraBand, context.resources.configuration.fontScale, IslandText.orFallback(peek.title, peek.appLabel), IslandText.clean(peek.text)))
                    else max(dp(expandedHeight(primary?.kind)) * s.scale.coerceIn(0.85f, 1.3f), dp(cameraBand + contentBelowCamera(primary?.kind)))
                val left = (cx - w / 2f).coerceIn(margin, screenW - margin - w)
                Rect(left, cy - h / 2f, left + w, cy - h / 2f + eh)
            }
            IslandPresentation.Compact -> {
                val left = (cx - compactW / 2f).coerceIn(margin, screenW - margin - compactW)
                Rect(left, cy - h / 2f, left + compactW, cy + h / 2f)
            }
            else -> Rect(cx - idleW / 2f, cy - h / 2f, cx + idleW / 2f, cy + h / 2f)
        }
        val bubble = if (pres == IslandPresentation.Compact && slots.secondary != null && pill.right + dp(6f) + h <= screenW - margin) {
            val l = pill.right + dp(6f)
            Rect(l, pill.top, l + h, pill.bottom)
        } else null

        val visible = pres != IslandPresentation.Hidden
        // The window is the only clip boundary: a pill whose spring overshoots past it is cut into a
        // straight edge. The margin must exceed the spring's overshoot (about 4% of the travel).
        val pad = dp(24f)
        var target = if (bubble != null) Rect(pill.left, pill.top, bubble.right, pill.bottom) else pill
        target = Rect(target.left - pad, (target.top - pad).coerceAtLeast(0f), target.right + pad, target.bottom + pad)
        targetRect = target

        // Grow the window at once (union with the old one) so the spring has room; shrink after it settles.
        if (windowRect == Rect.Zero) applyWindow(target, allowShrink = true)
        else if (!windowRect.containsRect(target)) applyWindow(union(windowRect, target), allowShrink = true)
        main.removeCallbacks(shrink)
        main.postDelayed(shrink, 700)

        val touchable = pres == IslandPresentation.Compact || pres == IslandPresentation.Expanded
        val p = window.params
        val newFlags = if (touchable) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (newFlags != p.flags) {
            p.flags = newFlags
            window.update()
        }
        window.setVisible(visible || frame.presentation != IslandPresentation.Hidden)
        val camera = c?.let { Rect(it.centerX - it.width / 2f, it.centerY - it.height / 2f, it.centerX + it.width / 2f, it.safeBottom) }
        frame = IslandFrame(pres, ui.copy(slots = slots), pill, bubble, h, camera)
        if (!visible) main.postDelayed({ if (presentation() == IslandPresentation.Hidden) window.setVisible(false) }, 400)

        main.removeCallbacks(autoCollapse)
        if (pres == IslandPresentation.Expanded) main.postDelayed(autoCollapse, AUTO_COLLAPSE_MS)
    }

    private fun Rect.containsRect(o: Rect) = o.left >= left && o.top >= top && o.right <= right && o.bottom <= bottom
    private fun union(a: Rect, b: Rect) = Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))

    private fun applyWindow(r: Rect, allowShrink: Boolean) {
        if (!allowShrink && windowRect.containsRect(r)) return
        windowRect = r
        val p = window.params
        p.x = r.left.toInt()
        p.y = r.top.toInt()
        p.width = r.width.toInt().coerceAtLeast(1)
        p.height = r.height.toInt().coerceAtLeast(1)
        origin = Offset(p.x.toFloat(), p.y.toFloat())
        window.update()
    }

    /** Content under the camera band: calls and clocks need their row (40 dp) plus a 48 dp button row. */
    private fun contentBelowCamera(kind: IslandKind?): Float = when (kind) {
        IslandKind.IncomingCall, IslandKind.OngoingCall, IslandKind.Timer, IslandKind.Stopwatch -> 122f
        else -> 80f
    }

    private fun expandedHeight(kind: IslandKind?): Float = when (kind) {
        IslandKind.Music -> 214f
        IslandKind.IncomingCall, IslandKind.OngoingCall -> 128f
        IslandKind.Peek -> 96f
        IslandKind.Charging -> 104f
        IslandKind.Navigation -> 108f
        IslandKind.Progress -> 96f
        IslandKind.Timer, IslandKind.Stopwatch -> 96f
        IslandKind.Ringer -> 64f
        null -> 64f
    }

    private fun collapse() {
        val p = slots().primary ?: return
        expandedId = null
        promotedId = null
        collapsedIds = collapsedIds + p.id
        render()
    }

    private fun haptic() {
        window.root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    // --- gestures ---

    override fun onTap(secondary: Boolean) {
        val s = slots()
        val a = (if (secondary) s.secondary else s.primary) ?: return
        haptic()
        if (a.kind == IslandKind.Peek) {
            a.notificationKey?.let { NotificationHub.open(context, it) }
            engine.dismiss(a.id)
            return
        }
        if (presentation() == IslandPresentation.Expanded && !secondary) {
            collapse()
            return
        }
        // Tapping the split bubble brings that activity into the main pill, expanded.
        if (secondary) promotedId = a.id
        expandedId = a.id
        collapsedIds = collapsedIds - a.id
        expandedAt = SystemClock.elapsedRealtime()
        render()
    }

    override fun onLongPress(secondary: Boolean) {
        val s = slots()
        val a = (if (secondary) s.secondary else s.primary) ?: return
        haptic()
        openSource(a)
    }

    override fun onSwipeAway() {
        val a = slots().primary ?: return
        haptic()
        // iOS 27: swiping an expanded activity collapses it; on the compact pill it dismisses.
        if (presentation() == IslandPresentation.Expanded) {
            collapse()
            return
        }
        engine.dismiss(a.id)
    }

    private fun openSource(a: IslandActivity) {
        val ok = when {
            a.kind == IslandKind.Music -> MediaHub.openPlayer(context)
            a.notificationKey != null -> NotificationHub.open(context, a.notificationKey)
            a.kind == IslandKind.Charging -> start(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            a.kind == IslandKind.Ringer -> start(Intent(Settings.ACTION_SOUND_SETTINGS))
            else -> false
        }
        if (!ok) a.sourcePackage?.let { NotificationHub.launchApp(context, it) }
        collapse()
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }

    companion object {
        private const val AUTO_COLLAPSE_MS = 6000L
    }
}
