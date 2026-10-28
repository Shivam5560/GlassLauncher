package com.shivam.glasslauncher.widgets

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.shivam.glasslauncher.badges.BadgeStore
import com.shivam.glasslauncher.settings.ScreenScaffold
import com.shivam.glasslauncher.settings.SettingsNav
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassToastState

/** Settings → Widgets: weather location and units, and the permissions widgets use. */
@Composable
fun WidgetsSettingsScreen(nav: SettingsNav, resumeTick: Int, toast: GlassToastState) {
    val context = LocalContext.current
    val music = remember(resumeTick) { BadgeStore.isAccessGranted(context) }
    val calendar = remember(resumeTick) { CalendarRepository.hasPermission(context) }
    val bluetooth = remember(resumeTick) { BatteryRepository.hasBluetoothPermission(context) }
    val askCalendar = rememberPermissionRequest(Manifest.permission.READ_CALENDAR) { toast.show("Calendar allowed") }
    val askBluetooth = rememberPermissionRequest(Manifest.permission.BLUETOOTH_CONNECT) { toast.show("Nearby devices allowed") }

    ScreenScaffold("Widgets", nav) {
        item("intro") {
            GlassListGroup(footer = "Add widgets from Edit Home Screen (+), or from the Edit button at the bottom of the Today view (swipe right from your first page). Pages are managed from Edit Home Screen too.") {
                GlassRow("Widgets & Today view", icon = WidgetGlyphs.Widgets, iconTint = GlassColors.Accent)
            }
        }
        item("weather") { WeatherLocationSettings() }
        item("perms") {
            GlassListGroup(title = "Access", footer = "Each widget works without these and shows what it needs in place.") {
                GlassRow("Notification access", subtitle = if (music) "On — music widget works" else "Off — needed for the music widget", icon = GlassIcons.Bell, iconTint = if (music) GlassColors.Green else GlassColors.Red, onClick = { openNotificationAccess(context) })
                GlassRow("Calendar", subtitle = if (calendar) "Allowed" else "Not allowed", icon = WidgetGlyphs.Calendar, iconTint = if (calendar) GlassColors.Green else GlassColors.Orange, onClick = if (calendar) null else askCalendar)
                GlassRow("Nearby devices", subtitle = if (bluetooth) "Allowed — earbud batteries" else "Not allowed — phone battery only", icon = WidgetGlyphs.Earbuds, iconTint = if (bluetooth) GlassColors.Green else GlassColors.Orange, onClick = if (bluetooth) null else askBluetooth)
            }
        }
    }
}
