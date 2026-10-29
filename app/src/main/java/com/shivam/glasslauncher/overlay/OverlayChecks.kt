package com.shivam.glasslauncher.overlay

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.shivam.glasslauncher.badges.BadgeStore
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.overlay.island.IslandEngine
import com.shivam.glasslauncher.settings.CheckFix
import com.shivam.glasslauncher.settings.CheckResult
import com.shivam.glasslauncher.settings.CheckStatus
import com.shivam.glasslauncher.settings.SystemChecks

/**
 * System check rows for Phase 3 (docs/CONSTRAINTS.md): every permission and setting the island,
 * Control Center and Notification Center use, plus the platform limits the user should know about.
 */
object OverlayChecks {

    fun overlayPermissionIntent(context: Context) = listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    )

    fun writeSettingsIntent(context: Context) = listOf(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS),
    )

    fun dndAccessIntent() = listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun all(context: Context): List<CheckResult> {
        val s = OverlayPrefs.state(context).value
        val gestures = GlassGestureService.isEnabled(context)
        val listener = BadgeStore.isAccessGranted(context)
        val canOverlay = Settings.canDrawOverlays(context)
        val canWrite = Settings.System.canWrite(context)
        val dnd = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        val panels = s.controlCenter || s.notificationCenter
        val currentSensor = IslandEngine.get(context).signals.currentSensorAvailable()

        return listOf(
            CheckResult(
                "p3_island", "Dynamic Island overlay",
                "Drawn by Glass Gestures as an accessibility overlay (no extra permission). Without it, \"Display over other apps\" is the fallback, drawn below the status bar. Hidden on the lock screen, in fullscreen video and in games.",
                when {
                    !s.island.showsGlass -> CheckStatus.Info
                    gestures -> CheckStatus.Ok
                    canOverlay && listener -> CheckStatus.Warning
                    else -> CheckStatus.Off
                },
                when {
                    !s.island.showsGlass -> if (s.island.enabled) "System island preferred — no overlapping Glass pill" else "Turned off"
                    gestures -> "Accessibility overlay"
                    canOverlay && listener -> "Fallback: display over other apps"
                    else -> "Not shown — turn on Glass Gestures"
                },
                if (s.island.showsGlass && !gestures) "Turn on" else null,
                CheckFix.OpenIntents(SystemChecks.accessibilityIntents(context)),
            ),
            CheckResult(
                "p3_overlay", "Display over other apps (optional)",
                "Fallback for the island when Glass Gestures is off. Not needed while Glass Gestures is on.",
                if (canOverlay) CheckStatus.Ok else CheckStatus.Info,
                if (canOverlay) "Allowed" else "Not allowed",
                if (canOverlay) null else "Allow", CheckFix.OpenIntents(overlayPermissionIntent(context)),
            ),
            CheckResult(
                "p3_notifications", "Notification access (island & Notification Center)",
                "Lists, groups, dismisses and replies to notifications; music, calls, timers, navigation and downloads in the island all come from notifications or media sessions. If an app posts no ongoing or progress notification, nothing shows for it.",
                if (listener) CheckStatus.Ok else CheckStatus.Off,
                if (listener) "On" else "Off",
                if (listener) null else "Turn on", CheckFix.OpenIntents(SystemChecks.notificationIntents(context)),
            ),
            CheckResult(
                "p3_corners", "Control & Notification Center swipes",
                "Swipe down from the top-left corner for Notification Center and the top-right for Control Center. They need Glass Gestures. A pull from the middle of the status bar may still open the HyperOS shade; Android doesn't let apps block it.",
                when {
                    !panels -> CheckStatus.Info
                    gestures -> CheckStatus.Ok
                    else -> CheckStatus.Off
                },
                when {
                    !panels -> "Turned off"
                    gestures -> "On (centre pull opens HyperOS shade)"
                    else -> "Needs Glass Gestures"
                },
                if (panels && !gestures) "Turn on" else null,
                CheckFix.OpenIntents(SystemChecks.accessibilityIntents(context)),
            ),
            CheckResult(
                "p3_write_settings", "Modify system settings",
                "Brightness slider, rotation lock and screen timeout in Control Center. You grant it once.",
                if (canWrite) CheckStatus.Ok else CheckStatus.Warning,
                if (canWrite) "Allowed" else "Not allowed — those controls open this setting",
                if (canWrite) null else "Allow", CheckFix.OpenIntents(writeSettingsIntent(context)),
            ),
            CheckResult(
                "p3_dnd", "Do Not Disturb access",
                "Lets the Control Center Do Not Disturb toggle switch it directly. Without it Glass asks through notification access instead.",
                when {
                    dnd -> CheckStatus.Ok
                    listener -> CheckStatus.Info
                    else -> CheckStatus.Warning
                },
                when {
                    dnd -> "Allowed"
                    listener -> "Using notification access instead"
                    else -> "Not allowed"
                },
                if (dnd) null else "Allow", CheckFix.OpenIntents(dndAccessIntent()),
            ),
            CheckResult(
                "p3_radios", "Wi-Fi, Bluetooth, mobile data, airplane mode",
                "Android doesn't let apps switch radios since Android 10, so these Control Center buttons show the state and open the system panel or settings page.",
                CheckStatus.Info, "System panels", null, CheckFix.None,
            ),
            CheckResult(
                "p3_banners", "HyperOS pop-up banners",
                "Heads-up banners come from HyperOS and can't be suppressed by another app. Glass shows its own island peek as well; turn it off in Dynamic Island settings if you get both.",
                CheckStatus.Info, "Can't be suppressed", null, CheckFix.None,
            ),
            CheckResult(
                "p3_watts", "Charging watts",
                "Estimated from battery current × voltage. Some phones report 0, and then the island shows the percentage only.",
                CheckStatus.Info, if (currentSensor) "Current reported" else "Not reported — watts hidden", null, CheckFix.None,
            ),
            CheckResult(
                "p3_privacy", "Camera & microphone dots",
                "Best effort: camera use comes from camera availability, microphone use from recording callbacks, which Android only partly reports. HyperOS shows its own dots too.",
                CheckStatus.Info, if (s.island.privacyDots) "On (best effort)" else "Off", null, CheckFix.None,
            ),
        )
    }
}
