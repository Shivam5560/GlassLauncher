package com.shivam.glasslauncher.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import com.shivam.glasslauncher.overlay.island.IslandEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IslandHost(val title: String) {
    Accessibility("Accessibility overlay (Glass Gestures)"),
    DisplayOverlay("Display over other apps (fallback, drawn below the status bar)"),
    None("Not running"),
}

/**
 * Decides who draws the Phase 3 overlays (docs/CONSTRAINTS.md "Island overlay"):
 *  - preferred: the Glass Gestures accessibility service (TYPE_ACCESSIBILITY_OVERLAY, no extra
 *    permission; also hosts the corner swipes and both panels);
 *  - fallback: SYSTEM_ALERT_WINDOW, hosted by the always-bound notification listener, island only.
 */
object OverlayRuntime {

    private var accessibilityHost: OverlayController? = null
    private var accessibilityService: AccessibilityService? = null
    private var fallbackHost: OverlayController? = null
    private var listenerContext: Context? = null

    private val _host = MutableStateFlow(IslandHost.None)
    val host: StateFlow<IslandHost> = _host.asStateFlow()

    private val _calibrating = MutableStateFlow(false)
    /** True while the island settings screen is open: the idle pill shows so it can be aligned. */
    val calibrating: StateFlow<Boolean> = _calibrating.asStateFlow()

    fun attachAccessibility(service: AccessibilityService): OverlayController {
        fallbackHost?.detach()
        fallbackHost = null
        accessibilityHost?.detach()
        val c = OverlayController(service, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, service)
        c.attach()
        accessibilityHost = c
        accessibilityService = service
        _host.value = IslandHost.Accessibility
        return c
    }

    fun detachAccessibility(service: AccessibilityService) {
        if (accessibilityService !== service) return
        accessibilityHost?.detach()
        accessibilityHost = null
        accessibilityService = null
        _host.value = IslandHost.None
        startFallbackIfPossible()
    }

    fun onListenerConnected(context: Context) {
        listenerContext = context
        startFallbackIfPossible()
    }

    fun onListenerDisconnected(context: Context) {
        if (listenerContext !== context) return
        listenerContext = null
        fallbackHost?.detach()
        fallbackHost = null
        if (accessibilityHost == null) _host.value = IslandHost.None
    }

    /** Re-checks the display-over-apps permission (e.g. after the user granted it). */
    fun refresh() = startFallbackIfPossible()

    private fun startFallbackIfPossible() {
        if (accessibilityHost != null || fallbackHost != null) return
        val ctx = listenerContext ?: return
        if (!Settings.canDrawOverlays(ctx)) return
        val c = OverlayController(ctx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        c.attach()
        fallbackHost = c
        _host.value = IslandHost.DisplayOverlay
    }

    fun setCalibrating(on: Boolean) { _calibrating.value = on }

    /** Shows a sample activity for a few seconds (Settings → Dynamic Island → Preview). */
    fun preview(context: Context) = IslandEngine.get(context).showDemo()

    /** Opens a panel from settings; false when Glass Gestures isn't running. */
    fun openPanel(kind: PanelKind): Boolean {
        val c = accessibilityHost ?: return false
        c.openPanel(kind)
        return true
    }
}
