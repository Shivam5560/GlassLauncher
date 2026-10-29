package com.shivam.glasslauncher.ui.spotlight

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.search.ContactResult
import com.shivam.glasslauncher.search.SpotlightResults
import com.shivam.glasslauncher.ui.LauncherViewModel
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassMorph
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassScrim
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.home.AppIconImage
import com.shivam.glasslauncher.ui.home.LauncherUiState
import com.shivam.glasslauncher.ui.home.rememberLast

private var askedContacts = false

/**
 * Spotlight: a full-screen glass sheet that grows out of where it was summoned (swipe-down
 * point or the search pill). The search field is focused and the keyboard raised immediately.
 */
@Composable
fun SpotlightOverlay(
    ui: LauncherUiState,
    vm: LauncherViewModel,
    label: (AppEntry) -> String,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val visible = ui.spotlightOpen
    val origin = rememberLast(ui.spotlight)
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val activity = LocalContext.current as Activity
    var progress by remember { mutableFloatStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onContactsPermissionChanged()
    }

    fun close() {
        keyboard?.hide()
        ui.spotlightOpen = false
    }

    LaunchedEffect(visible) {
        if (visible) {
            vm.query.value = ""
            // the field only exists once GlassMorph has composed it, so retry until focus sticks
            var focused = false
            repeat(20) {
                if (!focused) {
                    try { focus.requestFocus(); focused = true } catch (_: Exception) { delay(40) }
                }
            }
            keyboard?.show()
            if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                !askedContacts
            ) {
                askedContacts = true
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
            }
        }
    }
    if (visible) BackHandler { close() }
    if (!visible && progress <= 0.01f) {
        // keep composition cheap when closed
    }

    Box(Modifier.fillMaxSize()) {
        if (visible || progress > 0.01f) GlassScrim(progress * 0.6f) { close() }
        GlassMorph(
            visible = visible,
            origin = origin,
            modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
            contentAlignment = Alignment.TopCenter,
            onProgress = { progress = it },
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        modifier = Modifier.weight(1f),
                        placeholder = "Search",
                        focusRequester = focus,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val top = results.apps.firstOrNull()
                            if (top != null) { close(); onLaunch(top, null) }
                            else if (query.isNotBlank()) { close(); vm.actions.webSearch(activity, query) }
                        }),
                    )
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Cancel", onClick = { close() })
                }
                Spacer(Modifier.height(12.dp))
                SpotlightResultsList(
                    query = query,
                    results = results,
                    suggestions = suggestions,
                    label = label,
                    onLaunch = { e, b -> close(); onLaunch(e, b) },
                    onCall = { c -> c.number?.let { close(); vm.actions.call(activity, it) } },
                    onWhatsApp = { c -> c.number?.let { close(); vm.actions.whatsApp(activity, it) } },
                    onSetting = { s -> close(); if (!vm.actions.startIntent(activity, s.intent())) ui.toast.show("Not available on this phone") },
                    onWeb = { close(); vm.actions.webSearch(activity, query) },
                    onAllowContacts = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                    },
                    onOpenSettings = { close(); onOpenSettings() },
                )
            }
        }
    }
}

@Composable
private fun SpotlightResultsList(
    query: String,
    results: SpotlightResults,
    suggestions: List<AppEntry>,
    label: (AppEntry) -> String,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onCall: (ContactResult) -> Unit,
    onWhatsApp: (ContactResult) -> Unit,
    onSetting: (com.shivam.glasslauncher.search.SettingsShortcut) -> Unit,
    onWeb: () -> Unit,
    onAllowContacts: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (suggestions.isNotEmpty() && query.isBlank()) {
            item("suggestions") {
                GlassListGroup(title = "Siri Suggestions") {
                    AppIconRow(suggestions, label, onLaunch)
                }
            }
        }
        if (query.isBlank()) {
            item("hint") {
                GlassListGroup(footer = "Type to search apps, contacts and settings, do maths like 12*7+3 or convert units like 5 km to mi.") {
                    GlassRow("Glass Settings", icon = GlassIcons.Gear, iconTint = Color(0xFF8E8E93), onClick = onOpenSettings)
                }
            }
        }
        results.calculation?.let { value ->
            item("calc") {
                GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.shivam.glasslauncher.ui.glass.GlassIcon(GlassIcons.Calculator, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            GlassText(results.calculationExpression ?: "", style = GlassType.Callout, secondary = true, maxLines = 2)
                        }
                        Spacer(Modifier.height(4.dp))
                        GlassText("= $value", style = GlassType.LargeTitle, maxLines = 2)
                    }
                }
            }
        }
        results.conversion?.let { c ->
            item("convert") {
                GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.shivam.glasslauncher.ui.glass.GlassIcon(GlassIcons.Ruler, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            GlassText("${c.from.name} → ${c.to.name}", style = GlassType.Callout, secondary = true)
                        }
                        Spacer(Modifier.height(4.dp))
                        GlassText(c.text, style = GlassType.Title, maxLines = 2)
                    }
                }
            }
        }
        if (results.apps.isNotEmpty()) {
            item("apps") {
                GlassListGroup(title = "Apps") {
                    AppIconRow(results.apps.take(4), label, onLaunch)
                    results.apps.drop(4).forEach { e ->
                        AppResultRow(e, label(e), onLaunch)
                    }
                }
            }
        }
        if (results.contacts.isNotEmpty()) {
            item("contacts") {
                GlassListGroup(title = "Contacts") {
                    results.contacts.forEach { c ->
                        GlassRow(
                            title = c.name,
                            subtitle = c.number,
                            icon = GlassIcons.Person,
                            iconTint = Color(0xFF8E8E93),
                            showChevron = false,
                        ) {
                            if (c.number != null) {
                                GlassIconButton(GlassIcons.Phone, onClick = { onCall(c) }, size = 38.dp, tint = GlassColors.Green)
                                if (results.whatsAppInstalled) {
                                    Spacer(Modifier.width(8.dp))
                                    GlassIconButton(GlassIcons.Chat, onClick = { onWhatsApp(c) }, size = 38.dp, tint = Color(0xFF25D366))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (results.needsContactsPermission) {
            item("contactsPerm") {
                GlassListGroup(footer = "Contacts stay on your phone; Glass only reads them to show matches here.") {
                    GlassRow("Search your contacts too", icon = GlassIcons.Person, onClick = onAllowContacts)
                }
            }
        }
        if (results.settings.isNotEmpty()) {
            item("settings") {
                GlassListGroup(title = "Settings") {
                    results.settings.forEach { s ->
                        GlassRow(s.title, icon = GlassIcons.Gear, iconTint = Color(0xFF8E8E93), onClick = { onSetting(s) })
                    }
                }
            }
        }
        if (query.isNotBlank()) {
            item("web") {
                GlassListGroup {
                    GlassRow("Search Google for “$query”", icon = GlassIcons.Globe, onClick = onWeb)
                }
            }
        }
    }
}

@Composable
private fun AppIconRow(apps: List<AppEntry>, label: (AppEntry) -> String, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        apps.forEach { e ->
            val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
            GlassSurface(
                modifier = Modifier.width(78.dp).onGloballyPositioned { coords[0] = it },
                shape = GlassShape.rounded(18.dp),
                variant = GlassVariant.Clear,
                onClick = { onLaunch(e, coords[0]?.boundsInWindow()?.toAndroid()) },
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIconImage(e, 54.dp)
                    Spacer(Modifier.height(5.dp))
                    GlassText(label(e), style = GlassType.IconLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AppResultRow(e: AppEntry, label: String, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit) {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    GlassSurface(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).onGloballyPositioned { coords[0] = it },
        shape = GlassShape.Row,
        variant = GlassVariant.Clear,
        onClick = { onLaunch(e, coords[0]?.boundsInWindow()?.toAndroid()) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(e, 36.dp)
            Spacer(Modifier.width(12.dp))
            GlassText(label, style = GlassType.Body, maxLines = 1)
        }
    }
}

private fun Rect.toAndroid() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

