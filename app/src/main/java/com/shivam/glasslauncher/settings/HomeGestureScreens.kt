package com.shivam.glasslauncher.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.currentAccent
import com.shivam.glasslauncher.ui.home.AppIconImage
import com.shivam.glasslauncher.ui.home.GestureBinding
import com.shivam.glasslauncher.ui.home.HomeAction
import com.shivam.glasslauncher.ui.home.HomeGesture
import com.shivam.glasslauncher.ui.home.HomeGestureMap

/** Settings → Home gestures: "homegestures", "homegesture:<Gesture>", "homegestureapp:<Gesture>". */
fun isHomeGestureScreen(screen: String) = screen == "homegestures" || screen.startsWith("homegesture:") || screen.startsWith("homegestureapp:")

@Composable
fun HomeGestureScreen(nav: SettingsNav, settings: LauncherSettings, resumeTick: Int) {
    val screen = nav.current
    val gesture = HomeGesture.entries.firstOrNull { it.name == screen.substringAfter(':') }
    when {
        screen.startsWith("homegesture:") && gesture != null -> ActionPicker(nav, settings, gesture, resumeTick)
        screen.startsWith("homegestureapp:") && gesture != null -> AppPicker(nav, settings, gesture)
        else -> GestureList(nav, settings)
    }
}

@Composable
private fun GestureList(nav: SettingsNav, settings: LauncherSettings) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val apps by app.apps.apps.collectAsState()
    ScreenScaffold("Home gestures", nav) {
        item("gestures") {
            GlassListGroup(footer = "On empty Home Screen space. Swipe left and right still change pages.") {
                HomeGesture.entries.forEach { g ->
                    val b = HomeGestureMap.of(settings.homeGestures, g)
                    val subtitle = if (b.action == HomeAction.LaunchApp) apps.firstOrNull { it.key == b.appKey }?.label ?: "Open an app" else b.action.title
                    GlassRow(g.title, subtitle = subtitle, onClick = { nav.push("homegesture:${g.name}") })
                }
            }
        }
        item("reset") {
            GlassListGroup {
                GlassRow("Reset to defaults", icon = GlassIcons.Gear, onClick = { app.settings.setHomeGestures(HomeGestureMap.defaults) })
            }
        }
    }
}

@Composable
private fun ActionPicker(nav: SettingsNav, settings: LauncherSettings, gesture: HomeGesture, resumeTick: Int) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val gesturesOn = remember(resumeTick) { GlassGestureService.isEnabled(context) }
    val current = HomeGestureMap.of(settings.homeGestures, gesture)
    ScreenScaffold(gesture.title, nav) {
        item("actions") {
            GlassListGroup(footer = if (gesturesOn) null else "Actions marked \"needs Glass Gestures\" work once the accessibility service is on (Settings → System check).") {
                HomeAction.entries.forEach { a ->
                    GlassRow(
                        a.title,
                        subtitle = if (a.needsService && !gesturesOn) "Needs Glass Gestures" else null,
                        onClick = {
                            if (a == HomeAction.LaunchApp) nav.push("homegestureapp:${gesture.name}")
                            else {
                                app.settings.setHomeGesture(settings.homeGestures, gesture, GestureBinding(a))
                                nav.pop()
                            }
                        },
                        showChevron = a == HomeAction.LaunchApp,
                    ) {
                        if (current.action == a) GlassIcon(GlassIcons.Check, size = 18.dp, tint = currentAccent())
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPicker(nav: SettingsNav, settings: LauncherSettings, gesture: HomeGesture) {
    val app = GlassApp.from(LocalContext.current)
    val apps by app.apps.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val list = remember(apps, query) { if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) } }
    ScreenScaffold("Choose an app", nav) {
        item("search") {
            GlassListGroup {
                Box(Modifier.padding(10.dp)) { GlassTextField(query, { query = it }, placeholder = "Search apps", variant = GlassVariant.Clear) }
            }
        }
        items(list.chunked(12), key = { it.first().key }) { chunk ->
            GlassListGroup {
                chunk.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            app.settings.setHomeGesture(settings.homeGestures, gesture, GestureBinding(HomeAction.LaunchApp, e.key))
                            nav.pop(); nav.pop()
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(e, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        GlassText(e.label, Modifier.weight(1f), style = GlassType.Body, maxLines = 1)
                    }
                }
            }
        }
    }
}
