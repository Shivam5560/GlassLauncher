package com.shivam.glasslauncher.settings

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.WindowManager
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.badges.BadgeListenerService
import com.shivam.glasslauncher.badges.BadgeStore
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.overlay.OverlayChecks
import com.shivam.glasslauncher.ui.glass.LiquidGlassShader
import com.shivam.glasslauncher.ui.glass.WallpaperPath
import com.shivam.glasslauncher.ui.glass.WallpaperSource

enum class CheckStatus { Ok, Warning, Off, Info }

/** How a check is fixed: open a system screen, request a runtime permission, or go to an in-app screen. */
sealed interface CheckFix {
    data class OpenIntents(val intents: List<Intent>) : CheckFix
    data class RequestPermissions(val permissions: Array<String>) : CheckFix
    data class InApp(val screen: String) : CheckFix
    data object None : CheckFix
}

data class CheckResult(
    val id: String,
    val title: String,
    /** One-line reason the feature needs it. */
    val reason: String,
    val status: CheckStatus,
    val statusText: String,
    val fixLabel: String?,
    val fix: CheckFix,
    /** HyperOS settings we cannot read: the user confirms them by hand. */
    val confirmable: Boolean = false,
)

/**
 * Every permission and HyperOS setting Phase 1 needs (docs/CONSTRAINTS.md), with its live status
 * and a fix, followed by the Phase 2 widget rows from `WidgetChecks`. Shared by Settings → System
 * check and the first-run setup guide.
 */
object SystemChecks {

    fun isDefaultHome(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java)
        return rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun ignoringBattery(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    private val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("Xiaomi", true) || Build.BRAND.equals("Redmi", true) || Build.BRAND.equals("POCO", true)

    /** MIUI/HyperOS autostart via its private app-op 10008; null when it cannot be read. */
    fun autostart(context: Context): Boolean? {
        if (!isXiaomi) return null
        return try {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val m = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            (m.invoke(ops, 10008, Process.myUid(), context.packageName) as Int) == AppOpsManager.MODE_ALLOWED
        } catch (e: Throwable) {
            null
        }
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun homeIntents(context: Context): List<Intent> {
        val rm = context.getSystemService(RoleManager::class.java)
        return listOfNotNull(
            rm?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }?.createRequestRoleIntent(RoleManager.ROLE_HOME),
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        )
    }

    fun accessibilityIntents(context: Context): List<Intent> {
        val component = ComponentName(context, GlassGestureService::class.java).flattenToString()
        return listOf(
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, component),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
    }

    fun notificationIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, BadgeListenerService::class.java).flattenToString()),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    fun autostartIntents(context: Context): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        appDetails(context),
    )

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntents(context: Context): List<Intent> = listOf(
        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", "Glass Launcher"),
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        appDetails(context),
    )

    fun allFilesIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )

    /** Starts the first intent that resolves; false when none does. */
    fun launchFirst(activity: Activity, intents: List<Intent>): Boolean {
        for (i in intents) {
            try {
                // The role request only works when started for a result (it checks the caller).
                if (i.action == "android.app.role.action.REQUEST_ROLE") activity.startActivityForResult(i, 42)
                else activity.startActivity(i)
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    fun all(context: Context, settings: LauncherSettings, wallpaper: WallpaperSource.Snapshot): List<CheckResult> {
        val home = isDefaultHome(context)
        val gestures = GlassGestureService.isEnabled(context)
        val badges = BadgeStore.isAccessGranted(context)
        val restrictedInferred = gestures || badges || settings.restrictedSettingsConfirmed
        val auto = autostart(context)
        val battery = ignoringBattery(context)
        val crossBlur = context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        val shaderOk = LiquidGlassShader.createEffectShader() != null
        val contacts = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val calls = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val shortcuts = GlassApp.from(context).actions.hasShortcutAccess()

        return listOf(
            CheckResult(
                "home", "Default Home app", "Needed to replace the HyperOS launcher and to show app shortcuts.",
                if (home) CheckStatus.Ok else CheckStatus.Off, if (home) "Glass is your Home app" else "Not default",
                if (home) null else "Set as default", CheckFix.OpenIntents(homeIntents(context)),
            ),
            CheckResult(
                "restricted", "Allow restricted settings",
                "Sideloaded apps must allow this (App info → ⋮ → Allow restricted settings) before Accessibility or Notification access can be turned on.",
                if (restrictedInferred) CheckStatus.Ok else CheckStatus.Warning,
                when {
                    gestures || badges -> "Allowed"
                    settings.restrictedSettingsConfirmed -> "Confirmed by you"
                    else -> "Can't be read — confirm after allowing"
                },
                if (restrictedInferred) null else "Open App info", CheckFix.OpenIntents(listOf(appDetails(context))),
                confirmable = !restrictedInferred,
            ),
            CheckResult(
                "gestures", "Glass Gestures (Accessibility)", "HyperOS forces 3-button navigation for every third-party launcher; this service brings back Home/Back/Recents swipes and the home pill, plus double-tap to lock and hiding the nav bar in ultra-immersive mode.",
                if (gestures) CheckStatus.Ok else CheckStatus.Off, if (gestures) "On" else "Off",
                if (gestures) null else "Turn on", CheckFix.OpenIntents(accessibilityIntents(context)),
            ),
            CheckResult(
                "badges", "Notification access", "Red badges on icons. Counts are per notification, not each app's own unread number.",
                if (badges) CheckStatus.Ok else CheckStatus.Warning, if (badges) "On" else "Off — no badges",
                if (badges) null else "Turn on", CheckFix.OpenIntents(notificationIntents(context)),
            ),
            CheckResult(
                "autostart", "Autostart", "HyperOS kills the gesture overlay and badge listener without it.",
                when {
                    auto == true || (auto == null && settings.autostartConfirmed) -> CheckStatus.Ok
                    auto == false -> CheckStatus.Off
                    else -> CheckStatus.Warning
                },
                when {
                    auto == true -> "On"
                    auto == false -> "Off"
                    settings.autostartConfirmed -> "Confirmed by you"
                    else -> "Can't be read — check and confirm"
                },
                if (auto == true) null else "Open Autostart", CheckFix.OpenIntents(autostartIntents(context)),
                confirmable = auto == null,
            ),
            CheckResult(
                "battery", "Battery: No restrictions", "Keeps gestures and badges alive in the background.",
                if (battery) CheckStatus.Ok else CheckStatus.Off, if (battery) "No restrictions" else "Restricted",
                if (battery) null else "Fix", CheckFix.OpenIntents(batteryIntents(context)),
            ),
            CheckResult(
                "wallpaper", "Wallpaper for refraction", wallpaper.path.detail,
                when (wallpaper.path) {
                    WallpaperPath.OwnCopy, WallpaperPath.AllFilesAccess -> CheckStatus.Ok
                    WallpaperPath.None -> CheckStatus.Warning
                },
                wallpaper.path.title + if (wallpaper.isLive) " (live wallpaper)" else "",
                if (wallpaper.path == WallpaperPath.None) "Choose wallpaper" else null, CheckFix.InApp("wallpaper"),
            ),
            CheckResult(
                "blur", "Background blur", "Compositor blur behind Glass. Off in battery saver and on some HyperOS builds; Glass then uses a pre-blurred wallpaper or solid tinted glass.",
                if (crossBlur) CheckStatus.Ok else CheckStatus.Warning, if (crossBlur) "Available" else "Unavailable — using fallback",
                if (crossBlur) null else "Battery settings", CheckFix.OpenIntents(listOf(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))),
            ),
            CheckResult(
                "shader", "Liquid lens (AGSL)", "Refraction, chromatic rim and tilt lighting need Android 13+ and a working shader compiler.",
                if (shaderOk) CheckStatus.Ok else CheckStatus.Info,
                if (shaderOk) "Active" else if (Build.VERSION.SDK_INT < 33) "Android 12: frosted glass only" else "Shader failed: frosted glass only",
                null, CheckFix.None,
            ),
            CheckResult(
                "contacts", "Contacts (Spotlight)", "Find people in Spotlight. Asked the first time you search; optional.",
                if (contacts) CheckStatus.Ok else CheckStatus.Info, if (contacts) "Allowed" else "Not allowed",
                if (contacts) null else "Allow", CheckFix.RequestPermissions(arrayOf(Manifest.permission.READ_CONTACTS)),
            ),
            CheckResult(
                "calls", "Phone calls", "One-tap Call from Spotlight. Without it the dialer opens with the number filled in.",
                if (calls) CheckStatus.Ok else CheckStatus.Info, if (calls) "Allowed" else "Dialer fallback",
                if (calls) null else "Allow", CheckFix.RequestPermissions(arrayOf(Manifest.permission.CALL_PHONE)),
            ),
            CheckResult(
                "shortcuts", "App shortcuts", "Long-press shortcuts only work while Glass is the default Home app.",
                if (shortcuts) CheckStatus.Ok else CheckStatus.Warning, if (shortcuts) "Available" else "Unavailable",
                if (shortcuts) null else "Set as default", CheckFix.OpenIntents(homeIntents(context)),
            ),
        ) + com.shivam.glasslauncher.widgets.WidgetChecks.all(context) + OverlayChecks.all(context) + phase4Checks(context, home) + phase5Checks(context, settings, home, gestures)
    }

    /** Phase 5 rows (docs/CONSTRAINTS.md, "Smart Launcher parity"). No new runtime permissions. */
    private fun phase5Checks(context: Context, settings: LauncherSettings, home: Boolean, gestures: Boolean): List<CheckResult> {
        val glassIcons = try {
            context.packageManager.getPackageInfo(GlassApp.GLASS_ICONS_PACKAGE, 0); true
        } catch (e: Exception) { false }
        val iconsApplied = settings.iconPack == GlassApp.GLASS_ICONS_PACKAGE
        val compass = context.getSystemService(android.hardware.SensorManager::class.java)
            ?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR) != null
        return listOf(
            CheckResult(
                "glassicons", "Glass Icons", "Optional icon pack. By default Glass shows each app's own icon, logo untouched, under a glass finish.",
                if (iconsApplied) CheckStatus.Ok else CheckStatus.Info,
                when {
                    iconsApplied -> "In use"
                    glassIcons -> "Installed, not in use"
                    else -> "Not installed — app icons"
                },
                null, CheckFix.InApp("iconpack"),
            ),
            CheckResult(
                "drawershortcuts", "Drawer shortcuts", "App shortcuts added to drawer categories open only while Glass is the default Home app.",
                if (home) CheckStatus.Ok else CheckStatus.Warning, if (home) "Available" else "Unavailable",
                if (home) null else "Set as default", CheckFix.OpenIntents(homeIntents(context)),
            ),
            CheckResult(
                "immersive", "Ultra-immersive", "Hides the navigation bar on Home and in the drawer, only with Glass Gestures on, because HyperOS forces 3-button navigation for other launchers.",
                when {
                    !settings.immersive -> CheckStatus.Info
                    gestures -> CheckStatus.Ok
                    else -> CheckStatus.Warning
                },
                when {
                    !settings.immersive -> "Off"
                    gestures -> "Status and navigation bars hidden"
                    else -> "Status bar only — turn on Glass Gestures"
                },
                if (settings.immersive && !gestures) "Turn on gestures" else null, CheckFix.OpenIntents(accessibilityIntents(context)),
            ),
            CheckResult(
                "compass", "Compass widget", "Uses the rotation-vector sensor (no permission). Without it the widget isn't offered.",
                if (compass) CheckStatus.Ok else CheckStatus.Info, if (compass) "Sensor present" else "No sensor — widget hidden",
                null, CheckFix.None,
            ),
        )
    }

    /** Phase 4 rows (docs/CONSTRAINTS.md, "Icons, wallpapers, polish"). */
    private fun phase4Checks(context: Context, home: Boolean): List<CheckResult> {
        val fonts = com.shivam.glasslauncher.polish.Fonts.downloadsAvailable(context)
        return listOf(
            CheckResult(
                "fonts", "Downloadable fonts", "Google Fonts in the font picker come through Google Play services. Without it the bundled fonts are used.",
                if (fonts) CheckStatus.Ok else CheckStatus.Warning, if (fonts) "Available" else "Bundled fonts only",
                null, CheckFix.InApp("fonts"),
            ),
            CheckResult(
                "pinned", "Pinned shortcuts", "Other apps' “Add to Home screen” requests reach Glass only while it is the default Home app.",
                if (home) CheckStatus.Ok else CheckStatus.Warning, if (home) "Accepted" else "Unavailable",
                if (home) null else "Set as default", CheckFix.OpenIntents(homeIntents(context)),
            ),
        )
    }
}
