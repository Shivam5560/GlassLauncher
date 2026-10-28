package com.shivam.glasslauncher.overlay

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import com.shivam.glasslauncher.badges.BadgeStore
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.overlay.island.IslandKind
import com.shivam.glasslauncher.settings.ScreenScaffold
import com.shivam.glasslauncher.settings.SettingsNav
import com.shivam.glasslauncher.settings.SystemChecks
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassSliderRow
import com.shivam.glasslauncher.ui.glass.GlassSwitchRow
import com.shivam.glasslauncher.ui.glass.GlassToastState
import com.shivam.glasslauncher.ui.glass.GlassVariant
import kotlinx.coroutines.delay

/** Settings screens for Phase 3, reached from the single "Island & Centers" entry in Settings. */
object OverlayScreens {
    const val ROOT = "overlays"
    const val ISLAND = "overlays_island"
    const val CONTROL_CENTER = "overlays_cc"
    const val NOTIFICATION_CENTER = "overlays_nc"
    val ALL = setOf(ROOT, ISLAND, CONTROL_CENTER, NOTIFICATION_CENTER)
}

@Composable
fun OverlaySettingsScreen(nav: SettingsNav, screen: String, resumeTick: Int, toast: GlassToastState) {
    val context = LocalContext.current
    LaunchedEffect(resumeTick) { OverlayRuntime.refresh() }
    when (screen) {
        OverlayScreens.ISLAND -> IslandSettingsScreen(nav, resumeTick, toast)
        OverlayScreens.CONTROL_CENTER -> ControlCenterSettingsScreen(nav, resumeTick, toast)
        OverlayScreens.NOTIFICATION_CENTER -> NotificationCenterSettingsScreen(nav, resumeTick, toast)
        else -> OverlayRootScreen(nav, resumeTick, context)
    }
}

/** A row action that opens the first resolvable system screen. */
private fun opener(activity: Activity, intents: List<android.content.Intent>): () -> Unit = { SystemChecks.launchFirst(activity, intents) }

/** The settings-row subtitle for the main Settings screen. */
fun overlaySummary(context: android.content.Context): String {
    val s = OverlayPrefs.state(context).value
    val on = listOfNotNull(
        "Island".takeIf { s.island.enabled },
        "Control Center".takeIf { s.controlCenter },
        "Notification Center".takeIf { s.notificationCenter },
    )
    return if (on.isEmpty()) "Off" else on.joinToString(" · ")
}

@Composable
private fun OverlayRootScreen(nav: SettingsNav, resumeTick: Int, context: android.content.Context) {
    val s by OverlayPrefs.state(context).collectAsState()
    val host by OverlayRuntime.host.collectAsState()
    val gestures = remember(resumeTick) { GlassGestureService.isEnabled(context) }
    ScreenScaffold("Island & Centers", nav) {
        item {
            GlassListGroup(footer = "Everything here is drawn by Glass Gestures (an accessibility overlay). Without it the island can still use \"Display over other apps\", but the corner swipes and panels can't.") {
                GlassRow(
                    "Overlay host",
                    subtitle = host.title,
                    icon = OverlayIcons.Island,
                    iconTint = if (host == IslandHost.Accessibility) GlassColors.Green else if (host == IslandHost.DisplayOverlay) GlassColors.Orange else GlassColors.Red,
                    onClick = if (gestures) null else opener(context as Activity, SystemChecks.accessibilityIntents(context)),
                )
            }
        }
        item {
            GlassListGroup {
                GlassRow("Dynamic Island", subtitle = if (s.island.enabled) "On" else "Off", icon = OverlayIcons.Island, iconTint = Color(0xFF1C1C1E), onClick = { nav.push(OverlayScreens.ISLAND) })
                GlassRow("Control Center", subtitle = if (s.controlCenter) "Swipe down from the top-right corner" else "Off", icon = OverlayIcons.ControlCenter, iconTint = GlassColors.Accent, onClick = { nav.push(OverlayScreens.CONTROL_CENTER) })
                GlassRow("Notification Center", subtitle = if (s.notificationCenter) "Swipe down from the top-left corner" else "Off", icon = GlassIcons.Bell, iconTint = GlassColors.Red, onClick = { nav.push(OverlayScreens.NOTIFICATION_CENTER) })
            }
        }
        item {
            GlassListGroup(title = "Limits", footer = "These come from Android and HyperOS; Glass can't change them.") {
                GlassRow("HyperOS pop-up banners", subtitle = "Can't be suppressed: they may appear alongside the island's peek.")
                GlassRow("Status bar pull", subtitle = "A pull from the middle of the status bar may still open the HyperOS shade. Use the corners for Glass.")
                GlassRow("Radios", subtitle = "Wi-Fi, Bluetooth, mobile data and airplane mode can't be toggled by apps; Control Center opens the system panels.")
            }
        }
    }
}

@Composable
private fun IslandSettingsScreen(nav: SettingsNav, resumeTick: Int, toast: GlassToastState) {
    val context = LocalContext.current
    val activity = context as Activity
    val saved by OverlayPrefs.state(context).collectAsState()
    val host by OverlayRuntime.host.collectAsState()
    var s by remember { mutableStateOf(saved.island) }
    LaunchedEffect(s) {
        delay(120)
        if (s != OverlayPrefs.state(context).value.island) OverlayPrefs.setIsland(context, s)
    }
    // Show the idle pill while this screen is open so it can be lined up with the camera.
    DisposableEffect(Unit) {
        OverlayRuntime.setCalibrating(true)
        onDispose { OverlayRuntime.setCalibrating(false) }
    }
    val canOverlay = remember(resumeTick) { Settings.canDrawOverlays(context) }
    ScreenScaffold("Dynamic Island", nav) {
        item {
            GlassListGroup(footer = "A black pill around the punch-hole camera that shows live activities. Tap to expand, long-press to open the app, swipe it away to hide that activity until it ends. Hidden on the lock screen, in fullscreen video and in games.") {
                GlassSwitchRow("Dynamic Island", s.enabled, { s = s.copy(enabled = it) })
                GlassRow(
                    "Drawn by",
                    subtitle = host.title,
                    icon = OverlayIcons.Island,
                    iconTint = if (host == IslandHost.Accessibility) GlassColors.Green else if (host == IslandHost.DisplayOverlay) GlassColors.Orange else GlassColors.Red,
                    onClick = if (host == IslandHost.Accessibility) null else opener(activity, SystemChecks.accessibilityIntents(context)),
                )
                if (host != IslandHost.Accessibility && !canOverlay) {
                    GlassRow(
                        "Allow display over other apps",
                        subtitle = "Fallback while Glass Gestures is off (drawn below the status bar)",
                        icon = GlassIcons.Plus,
                        onClick = opener(activity, OverlayChecks.overlayPermissionIntent(context)),
                    )
                }
                GlassSwitchRow("Show the pill when idle", s.showIdlePill, { s = s.copy(showIdlePill = it) }, subtitle = "Covers the camera like iOS; needs a punch-hole camera")
                GlassSwitchRow("Camera & mic dots", s.privacyDots, { s = s.copy(privacyDots = it) }, subtitle = "Best effort; HyperOS shows its own dots too")
            }
        }
        item {
            GlassListGroup(title = "Position & size", footer = "Starts from the camera position Android reports (DisplayCutout). Nudge it until the pill sits evenly around the camera; the idle pill shows while this screen is open.") {
                GlassSliderRow("Horizontal offset", s.offsetX, -60f..60f, { "${it.toInt()} dp" }) { s = s.copy(offsetX = it) }
                GlassSliderRow("Vertical offset", s.offsetY, -30f..30f, { "${it.toInt()} dp" }) { s = s.copy(offsetY = it) }
                GlassSliderRow("Size", s.scale, 0.7f..1.5f, { "%.2fx".format(it) }) { s = s.copy(scale = it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GlassButton("Preview", onClick = {
                        OverlayRuntime.preview(context)
                        if (host == IslandHost.None) toast.show("Turn on Glass Gestures to see the island")
                    })
                    GlassButton("Reset", onClick = { s = s.copy(offsetX = 0f, offsetY = 0f, scale = 1f) })
                }
            }
        }
        item {
            GlassListGroup(title = "Activities", footer = "Everything except charging, ringer and the camera/mic dots comes from notifications or media sessions: if an app doesn't post an ongoing or progress notification, nothing shows for it. Timers and stopwatches come from the Clock app; navigation from Google Maps.") {
                IslandKind.entries.forEach { k ->
                    GlassSwitchRow(k.title, s.isOn(k), { on ->
                        s = s.copy(disabledKinds = if (on) s.disabledKinds - k else s.disabledKinds + k)
                    })
                }
            }
        }
    }
}

@Composable
private fun ControlCenterSettingsScreen(nav: SettingsNav, resumeTick: Int, toast: GlassToastState) {
    val context = LocalContext.current
    val activity = context as Activity
    val s by OverlayPrefs.state(context).collectAsState()
    val gestures = remember(resumeTick) { GlassGestureService.isEnabled(context) }
    val canWrite = remember(resumeTick) { Settings.System.canWrite(context) }
    val dnd = remember(resumeTick) {
        context.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    }
    var cornerW by remember { mutableStateOf(s.cornerWidth) }
    var cornerH by remember { mutableStateOf(s.cornerHeight) }
    LaunchedEffect(cornerW, cornerH) {
        delay(200)
        val cur = OverlayPrefs.state(context).value
        if (cur.cornerWidth != cornerW || cur.cornerHeight != cornerH) OverlayPrefs.setCorners(context, cornerW, cornerH)
    }
    ScreenScaffold("Control Center", nav) {
        item {
            GlassListGroup(footer = "Swipe down from the top-right corner. In Control Center, tap the pencil to rearrange: drag to reorder, tap a control to resize, remove or add controls. Long-press anywhere for the full HyperOS quick settings.") {
                GlassSwitchRow("Control Center", s.controlCenter, { OverlayPrefs.setControlCenter(context, it) })
                if (!gestures) {
                    GlassRow("Needs Glass Gestures", subtitle = "Turn it on to use the corner swipes", icon = GlassIcons.Hand, iconTint = GlassColors.Red, onClick = opener(activity, SystemChecks.accessibilityIntents(context)))
                }
                GlassRow("Open Control Center now", icon = OverlayIcons.ControlCenter, onClick = {
                    if (!OverlayRuntime.openPanel(PanelKind.ControlCenter)) toast.show("Turn on Glass Gestures first")
                })
            }
        }
        item {
            GlassListGroup(title = "Permissions") {
                GlassRow(
                    "Modify system settings",
                    subtitle = if (canWrite) "Allowed: brightness, rotation lock, screen timeout" else "Needed for brightness, rotation lock and screen timeout",
                    icon = OverlayIcons.Sun,
                    iconTint = if (canWrite) GlassColors.Green else GlassColors.Orange,
                    onClick = if (canWrite) null else opener(activity, OverlayChecks.writeSettingsIntent(context)),
                )
                GlassRow(
                    "Do Not Disturb access",
                    subtitle = if (dnd) "Allowed" else "Optional: without it Glass asks through notification access",
                    icon = OverlayIcons.Moon,
                    iconTint = if (dnd) GlassColors.Green else Color(0xFF5E5CE6),
                    onClick = if (dnd) null else opener(activity, OverlayChecks.dndAccessIntent()),
                )
                GlassRow("Wi-Fi, Bluetooth, data, airplane", subtitle = "Open system panels: Android blocks direct toggles", icon = OverlayIcons.Wifi)
            }
        }
        item {
            GlassListGroup(title = "Corner swipe zones", footer = "A pull from the middle of the status bar may still open the HyperOS shade; Android doesn't let apps block it. The corners pause in games, fullscreen video and apps excluded from gestures.") {
                GlassSliderRow("Corner width", cornerW, 0.15f..0.45f, { "${(it * 100).toInt()}% of screen" }) { cornerW = it }
                GlassSliderRow("Corner height", cornerH, 16f..56f, { "${it.toInt()} dp" }) { cornerH = it }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GlassButton("Reset Control Center layout", onClick = {
                    OverlayPrefs.setLayout(context, com.shivam.glasslauncher.overlay.controlcenter.ControlCenterLayout.DEFAULT)
                    toast.show("Layout reset")
                }, variant = GlassVariant.Regular)
            }
        }
    }
}

@Composable
private fun NotificationCenterSettingsScreen(nav: SettingsNav, resumeTick: Int, toast: GlassToastState) {
    val context = LocalContext.current
    val activity = context as Activity
    val s by OverlayPrefs.state(context).collectAsState()
    val gestures = remember(resumeTick) { GlassGestureService.isEnabled(context) }
    val listener = remember(resumeTick) { BadgeStore.isAccessGranted(context) }
    ScreenScaffold("Notification Center", nav) {
        item {
            GlassListGroup(footer = "Swipe down from the top-left corner. Notifications are grouped per app into stacks: tap a stack to expand it, swipe a card to dismiss, use action buttons and reply inline. The current music is pinned at the top.") {
                GlassSwitchRow("Notification Center", s.notificationCenter, { OverlayPrefs.setNotificationCenter(context, it) })
                GlassRow(
                    "Notification access",
                    subtitle = if (listener) "On" else "Off — needed to show notifications",
                    icon = GlassIcons.Bell,
                    iconTint = if (listener) GlassColors.Green else GlassColors.Red,
                    onClick = if (listener) null else opener(activity, SystemChecks.notificationIntents(context)),
                )
                if (!gestures) {
                    GlassRow("Needs Glass Gestures", subtitle = "Turn it on to use the corner swipes", icon = GlassIcons.Hand, iconTint = GlassColors.Red, onClick = opener(activity, SystemChecks.accessibilityIntents(context)))
                }
                GlassRow("Open Notification Center now", icon = GlassIcons.Bell, onClick = {
                    if (!OverlayRuntime.openPanel(PanelKind.NotificationCenter)) toast.show("Turn on Glass Gestures first")
                })
            }
        }
        item {
            GlassListGroup(title = "Limits") {
                GlassRow("HyperOS pop-up banners", subtitle = "Can't be suppressed by another app, so they still appear at the top.")
                GlassRow("Status bar pull", subtitle = "A pull from the middle of the status bar may still open the HyperOS shade.")
            }
        }
    }
}
