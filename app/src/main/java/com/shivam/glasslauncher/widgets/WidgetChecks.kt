package com.shivam.glasslauncher.widgets

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.shivam.glasslauncher.badges.BadgeStore
import com.shivam.glasslauncher.settings.CheckFix
import com.shivam.glasslauncher.settings.CheckResult
import com.shivam.glasslauncher.settings.CheckStatus
import com.shivam.glasslauncher.settings.SystemChecks

/** Phase 2 rows for Settings → System check (docs/CONSTRAINTS.md, "Widgets & Today view"). */
object WidgetChecks {

    fun all(context: Context): List<CheckResult> {
        val music = BadgeStore.isAccessGranted(context)
        val calendar = CalendarRepository.hasPermission(context)
        val weather = WeatherRepository.get(context)
        val location = weather.hasLocationPermission()
        val city = weather.prefs.weather.value?.let { it.mode == WeatherLocationMode.CITY && it.latitude != null } == true
        val bluetooth = BatteryRepository.hasBluetoothPermission(context)
        val online = try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
        return listOf(
            CheckResult(
                "music", "Music widget (notification access)",
                "Reads the playing song through media sessions, which Android only allows with notification access. Album art and seeking only if the player provides them.",
                if (music) CheckStatus.Ok else CheckStatus.Warning, if (music) "On" else "Off — music widget shows a prompt",
                if (music) null else "Turn on", CheckFix.OpenIntents(SystemChecks.notificationIntents(context)),
            ),
            CheckResult(
                "calendar", "Calendar", "Today's events in the calendar widget. Asked the first time you tap the widget.",
                if (calendar) CheckStatus.Ok else CheckStatus.Info, if (calendar) "Allowed" else "Not allowed",
                if (calendar) null else "Allow", CheckFix.RequestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)),
            ),
            CheckResult(
                "location", "Approximate location (weather)",
                "Coarse location is enough for the weather widget. Without it, pick a city in the widget's settings.",
                when {
                    location -> CheckStatus.Ok
                    city -> CheckStatus.Ok
                    else -> CheckStatus.Info
                },
                when {
                    location -> "Allowed"
                    city -> "Using a picked city"
                    else -> "Not allowed — pick a city instead"
                },
                if (location) null else "Allow", CheckFix.RequestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)),
            ),
            CheckResult(
                "bluetooth", "Nearby devices (earbud battery)",
                "Battery levels of connected Bluetooth devices. Many earbuds don't report one; those rows are hidden.",
                if (bluetooth) CheckStatus.Ok else CheckStatus.Info, if (bluetooth) "Allowed" else "Phone battery only",
                if (bluetooth) null else "Allow", CheckFix.RequestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)),
            ),
            CheckResult(
                "network", "Internet (weather only)",
                "Weather comes from Open-Meteo (no account, no key). It's the only network use besides Google search intents; the last forecast stays cached offline.",
                if (online) CheckStatus.Ok else CheckStatus.Info, if (online) "Online" else "Offline — showing saved forecast",
                null, CheckFix.None,
            ),
            CheckResult(
                "appwidgets", "Third-party widgets",
                "Hosted with AppWidgetHost. Some widgets ask you to allow Glass to create widgets the first time you add one, and some open their own setup screen.",
                CheckStatus.Info, "Asked per widget", null, CheckFix.None,
            ),
            CheckResult(
                "photos", "Photos widget",
                "Uses the system Photo Picker, so Glass needs no storage permission and only sees the photos you pick.",
                CheckStatus.Ok, "No permission needed", null, CheckFix.None,
            ),
        )
    }
}
