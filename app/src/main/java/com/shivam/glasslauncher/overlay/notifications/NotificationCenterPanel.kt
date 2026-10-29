package com.shivam.glasslauncher.overlay.notifications

import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberUpdatedState
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.overlay.OverlayAppIcon
import com.shivam.glasslauncher.overlay.OverlayIcons
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.overlay.PanelHost
import com.shivam.glasslauncher.ui.glass.OverlayWallpaperBackdrop
import com.shivam.glasslauncher.ui.glass.PremiumDim
import com.shivam.glasslauncher.ui.glass.premiumDim
import com.shivam.glasslauncher.overlay.island.AudioBars
import com.shivam.glasslauncher.overlay.island.LinearBar
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassChip
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.SurfaceKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A wallpaper-backed inbox with app stacks, live media, inline actions and a separate bottom bar.
 * The scroll viewport ends above the controls, including when the reply keyboard is open.
 */
@Composable
fun NotificationCenterPanel(host: PanelHost, progress: Float) {
    val context = host.context
    val all by NotificationHub.entries.collectAsState()
    val hidden by NotificationHub.hidden.collectAsState()
    // Honor cards hidden by earlier sessions while keeping ongoing activities out of Clear All.
    val entries = remember(all, hidden) { all.filter { (hidden[it.key] ?: -1L) < it.postTime } }
    val connected by NotificationHub.connected.collectAsState()
    val media by MediaHub.state.collectAsState()
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var filter by remember { mutableStateOf(NotificationFilter.ALL) }
    val filtered = remember(entries, filter) { filter.select(entries) }
    val stacks = remember(filtered, expanded) { NotificationGrouping.stacks(filtered, expanded) }
    val reducedMotion = remember(context) { com.shivam.glasslauncher.widgets.reducedMotion(context) }

    val wallpaper by GlassApp.from(context).wallpaper.snapshot.collectAsState()
    val panelDim = remember(wallpaper) {
        PremiumDim.alphaFor(wallpaper.luminance(0f, 0f, wallpaper.screenWidth.toFloat(), wallpaper.screenHeight.toFloat()))
    }
    OverlayWallpaperBackdrop(wallpaper, drawToScreen = true, blurRadius = 32.dp, dimAlpha = panelDim) {
        Box(
            Modifier
                .fillMaxSize()
                .premiumDim(if (wallpaper.image == null) panelDim * progress else 0f)
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(Unit) { detectTapGestures(onTap = { host.close() }) },
        ) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 92.dp)
                    .statusBarsPadding()
                    .graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        translationY = -(1f - progress) * 120.dp.toPx()
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("clock") { PanelHeader(onSwitch = { host.switchPanel() }) }
                item("filters") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotificationFilter.entries.forEach { option ->
                            GlassChip(option.title, selected = filter == option, onClick = { filter = option }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (!connected) {
                    item("access") {
                        GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
                            Column(Modifier.padding(16.dp)) {
                                GlassText("Notification access is off", style = GlassType.Headline)
                                GlassText(
                                    "Glass needs notification access to list, group, dismiss and reply to notifications, and to show music. Allow restricted settings first if Android greys it out.",
                                    style = GlassType.Callout, secondary = true,
                                )
                                Spacer(Modifier.height(10.dp))
                                GlassButton("Turn on", onClick = { host.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, prominent = true)
                            }
                        }
                    }
                }
                val m = media
                if (m != null) item("media") { MediaCard(m, host, SurfaceKind.Panel) }
                if (stacks.isNotEmpty()) {
                    item("header") {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                GlassText(if (filter == NotificationFilter.ALL) "Recent" else filter.title, style = GlassType.Headline, color = Color.White)
                                val count = stacks.sumOf { it.count }
                                GlassText("$count ${if (count == 1) "notification" else "notifications"}", style = GlassType.Caption, color = Color.White.copy(alpha = 0.75f))
                            }
                            // Include dismissible summaries so a cleared group cannot return as an empty pile.
                            val clear = filtered.filter { !it.isMedia && it.isClearable }.map { it.key }
                            // Ongoing activities remain available after clearing.
                            if (clear.isNotEmpty()) GlassButton("Clear", onClick = { NotificationHub.dismissAll(clear) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Clear dismissible notifications" })
                        }
                    }
                }
                stacks.forEach { stack ->
                    if (stack.expanded) {
                        item("h:" + stack.packageName) {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassText(stack.appLabel, Modifier.weight(1f).padding(start = 4.dp), style = GlassType.Headline, color = Color.White, maxLines = 1)
                                GlassChip("Show less", selected = false, onClick = { expanded = expanded - stack.packageName })
                                Spacer(Modifier.width(8.dp))
                                if (appKeys(filtered, stack.packageName).isNotEmpty()) GlassIconButton(GlassIcons.Close, onClick = { NotificationHub.dismissAll(appKeys(filtered, stack.packageName)) }, size = 48.dp, modifier = Modifier.semantics { contentDescription = "Clear notifications from ${stack.appLabel}" })
                            }
                        }
                        items(stack.items, key = { it.key }) { e ->
                            SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(listOf(e.key)) }, enabled = e.isClearable) {
                                NotificationCard(e, host, showActions = true, onClick = { if (NotificationHub.open(context, e.key)) host.close() }, kind = SurfaceKind.Panel)
                            }
                        }
                    } else {
                        item("s:" + stack.packageName) {
                            Box(if (reducedMotion) Modifier else Modifier.cascade(progress, stacks.indexOf(stack))) {
                                SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(appKeys(filtered, stack.packageName)) }, enabled = stack.clearableKeys.isNotEmpty()) {
                                    CollapsedStack(stack, host, onExpand = { expanded = expanded + stack.packageName }, kind = SurfaceKind.Panel)
                                }
                            }
                        }
                    }
                }
                if (connected && stacks.isEmpty()) {
                    item("empty") {
                        GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.rounded(28.dp), role = null) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                GlassText(if (filter == NotificationFilter.ALL) "You’re all caught up" else "Nothing ${filter.title.lowercase()} right now", style = GlassType.Headline.copy(textAlign = TextAlign.Center))
                                Spacer(Modifier.height(8.dp))
                                GlassText("New notifications will appear here.", style = GlassType.Callout.copy(textAlign = TextAlign.Center), secondary = true)
                            }
                        }
                    }
                }
            }
            QuickButtons(host, Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp).graphicsLayer { alpha = progress.coerceIn(0f, 1f) })
        }
    }
}

@Composable
private fun QuickButtons(host: PanelHost, modifier: Modifier) {
    val context = host.context
    val camera = remember { context.getSystemService(android.hardware.camera2.CameraManager::class.java) }
    val torchId = remember {
        try {
            camera?.cameraIdList?.firstOrNull { id ->
                camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }
    var torchOn by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(camera) {
        val cb = object : android.hardware.camera2.CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
        }
        camera?.registerTorchCallback(cb, null)
        onDispose { camera?.unregisterTorchCallback(cb) }
    }
    GlassSurface(modifier.fillMaxWidth(), shape = GlassShape.Capsule, role = null) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                OverlayIcons.Flashlight,
                onClick = { torchId?.let { try { camera?.setTorchMode(it, !torchOn) } catch (_: Exception) { } } },
                size = 52.dp,
                modifier = Modifier.semantics { contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on" },
                tint = if (torchOn) Color.White else null,
                tintGlyph = if (torchOn) Color.Black else null,
            )
            GlassButton("Manage", onClick = {
                val action = if (android.os.Build.VERSION.SDK_INT >= 33) Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS else Settings.ACTION_SETTINGS
                if (!host.launch(Intent(action))) host.launch(Intent(Settings.ACTION_SETTINGS))
            }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Manage app notifications" })
            GlassIconButton(
                OverlayIcons.Camera,
                onClick = { host.launch(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
                size = 52.dp,
                modifier = Modifier.semantics { contentDescription = "Open camera" },
            )
        }
    }
}

/**
 * Optional lock-screen stacks show app names and counts only when the native list is off.
 * Message text, media and actions remain private; the system keyguard still owns authentication.
 */
@Composable
fun LockScreenNotifications(host: PanelHost, maxHeight: androidx.compose.ui.unit.Dp) {
    val all by NotificationHub.entries.collectAsState()
    val entries = remember(all) { lockScreenEntries(all) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val stacks = remember(entries, expanded) { NotificationGrouping.stacks(entries, expanded) }
    if (stacks.isEmpty()) return
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = maxHeight),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("privacy") {
            GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Capsule, role = null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassText("${stacks.sumOf { it.count }} notifications", Modifier.weight(1f), style = GlassType.Subhead)
                    GlassText("Unlock to view", style = GlassType.Caption, secondary = true)
                }
            }
        }
        stacks.forEach { stack ->
            if (stack.expanded) {
                item("collapse:" + stack.packageName) {
                    GlassButton("Show less · ${stack.appLabel}", onClick = { expanded = expanded - stack.packageName })
                }
                items(stack.items, key = { "lock:" + it.key }) { e ->
                    NotificationCard(e, host, showActions = false, onClick = { expanded = expanded - stack.packageName }, kind = SurfaceKind.Panel)
                }
            } else {
                item("lock-stack:" + stack.packageName) {
                    CollapsedStack(stack, host, onExpand = { expanded = expanded + stack.packageName }, kind = SurfaceKind.Panel, locked = true)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(onSwitch: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val time = remember(now) {
        val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        DateFormat.format(pattern, now).toString()
    }
    val date = remember(now) {
        DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Date(now)).toString()
    }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassText(time, Modifier.weight(1f), style = GlassType.LargeTitle.copy(fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-1).sp), color = Color.White, maxLines = 1)
            GlassIconButton(OverlayIcons.ControlCenter, onClick = onSwitch, size = 48.dp, modifier = Modifier.semantics { contentDescription = "Open system quick settings" })
        }
        GlassText(date, style = GlassType.Subhead, color = Color.White.copy(alpha = 0.8f), maxLines = 2)
        Spacer(Modifier.height(18.dp))
        GlassText("Notifications", style = GlassType.LargeTitle.copy(fontSize = 30.sp, lineHeight = 36.sp), color = Color.White)
    }
}

/** Horizontal swipe that throws the card off screen, then calls [onDismiss]. */
@Composable
private fun SwipeToDismiss(onDismiss: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val context = androidx.compose.ui.platform.LocalContext.current
    val reducedMotion = remember(context) { com.shivam.glasslauncher.widgets.reducedMotion(context) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableStateOf(1) }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.8f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > width * 0.35f) {
                                if (!reducedMotion) offset.animateTo(if (offset.value > 0) width * 1.2f else -width * 1.2f, spring(stiffness = 600f))
                                dismiss()
                                offset.snapTo(0f)
                            } else {
                                if (reducedMotion) offset.snapTo(0f) else offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { if (reducedMotion) offset.snapTo(0f) else offset.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dx) }
                }
            },
    ) { content() }
}

/** An iOS stack: the newest card in front with up to two smaller glass slivers peeking below. */
@Composable
private fun CollapsedStack(stack: NotifStack, host: PanelHost, onExpand: () -> Unit, kind: SurfaceKind? = null, locked: Boolean = false) {
    val layers = stack.visibleLayers
    val openLatest: () -> Unit = { if (NotificationHub.open(host.context, stack.latest.key)) host.close() }
    Box(Modifier.fillMaxWidth().padding(bottom = (8 * (layers - 1)).dp)) {
        for (i in (layers - 1) downTo 1) {
            NotifSurface(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val s = 1f - 0.05f * i
                        scaleX = s
                        translationY = (9 * i).dp.toPx()
                        alpha = 1f - 0.25f * i
                    },
                onClick = null,
                kind = kind,
            ) {}
        }
        NotificationCard(
            stack.latest,
            host,
            showActions = stack.count == 1 && !locked,
            badge = if (stack.hiddenCount > 0) "+${stack.hiddenCount}" else null,
            onClick = if (locked || stack.count > 1) onExpand else openLatest,
            kind = kind,
        )
    }
}

@Composable
private fun NotificationCard(e: NotifEntry, host: PanelHost, showActions: Boolean, onClick: () -> Unit, badge: String? = null, kind: SurfaceKind? = null) {
    val context = host.context
    var replyIndex by remember(e.key) { mutableStateOf<Int?>(null) }
    var reply by remember(e.key) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    NotifSurface(Modifier.fillMaxWidth(), onClick = onClick, kind = kind) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayAppIcon(e.packageName, 24.dp)
                Spacer(Modifier.width(8.dp))
                GlassText(e.appLabel, Modifier.weight(1f), style = GlassType.Subhead, secondary = true, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                GlassText(relativeTime(e.postTime), style = GlassType.Caption, secondary = true, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            GlassText(e.title.ifBlank { e.appLabel }, style = GlassType.Headline.copy(lineHeight = 23.sp), maxLines = 3)
            if (e.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                GlassText(e.text, style = GlassType.Callout.copy(lineHeight = 22.sp), maxLines = if (showActions) 6 else 3)
            }
            if (e.progressMax > 0 || e.progressIndeterminate) {
                Spacer(Modifier.height(10.dp))
                LinearBar(if (e.progressIndeterminate || e.progressMax <= 0) null else e.progress.toFloat() / e.progressMax, GlassColors.Accent)
            }
            if (badge != null) {
                Spacer(Modifier.height(10.dp))
                GlassText("$badge more · Tap to expand", style = GlassType.Caption, secondary = true, maxLines = 2)
            }
            if (showActions && e.actions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.actions.take(3).forEach { a ->
                        GlassChip(
                            a.title,
                            selected = replyIndex == a.index,
                            onClick = {
                                if (a.hasRemoteInput) {
                                    replyIndex = if (replyIndex == a.index) null else a.index
                                } else if (NotificationHub.runAction(context, e.key, a.index)) {
                                    host.close()
                                }
                            },
                        )
                    }
                }
            }
            val ri = replyIndex
            if (ri != null) {
                Spacer(Modifier.height(10.dp))
                LaunchedEffect(ri) { try { focus.requestFocus() } catch (_: Exception) { } }
                fun send() {
                    if (reply.isBlank()) return
                    if (NotificationHub.reply(context, e.key, ri, reply)) {
                        reply = ""
                        replyIndex = null
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassTextField(
                        reply, { reply = it },
                        Modifier.weight(1f),
                        placeholder = "Reply",
                        leadingIcon = OverlayIcons.Reply,
                        focusRequester = focus,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        variant = GlassVariant.Clear,
                    )
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(OverlayIcons.Send, onClick = { send() }, size = 48.dp, tint = GlassColors.Accent)
                }
            }
        }
    }
}

/** The current media session, pinned above the notifications as a live card. */
@Composable
private fun MediaCard(m: MediaInfo, host: PanelHost, kind: SurfaceKind? = null) {
    val open: () -> Unit = { if (MediaHub.openPlayer(host.context)) host.close() }
    NotifSurface(Modifier.fillMaxWidth(), onClick = open, kind = kind) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(m.art) { m.art?.asImageBitmap() }
                if (bmp != null) {
                    Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(GlassShape.rounded(12.dp)))
                } else {
                    OverlayAppIcon(m.packageName, 56.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    GlassText(m.title.ifBlank { m.appLabel }, style = GlassType.Headline, maxLines = 1)
                    GlassText(m.artist.ifBlank { m.appLabel }, style = GlassType.Callout, secondary = true, maxLines = 1)
                }
                AudioBars(m.playing, Modifier.size(width = 24.dp, height = 18.dp), m.art)
            }
            if (m.durationMs > 0) {
                Spacer(Modifier.height(10.dp))
                var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
                LaunchedEffect(m.playing) {
                    while (m.playing) {
                        delay(500)
                        now = android.os.SystemClock.elapsedRealtime()
                    }
                }
                LinearBar(m.livePosition(now).toFloat() / m.durationMs, Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(OverlayIcons.Previous, onClick = { MediaHub.previous() }, size = 48.dp)
                GlassIconButton(if (m.playing) OverlayIcons.Pause else OverlayIcons.Play, onClick = { MediaHub.toggle() }, size = 50.dp)
                GlassIconButton(OverlayIcons.Next, onClick = { MediaHub.next() }, size = 48.dp)
            }
        }
    }
}

private fun relativeTime(t: Long): String {
    val now = System.currentTimeMillis()
    return if (now - t < 60_000) "now"
    else DateUtils.getRelativeTimeSpanString(t, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

/** Every non-media notification of [pkg], group summaries included (see Clear All). */
private fun appKeys(entries: List<NotifEntry>, pkg: String) = entries.filter { it.packageName == pkg && !it.isMedia && it.isClearable }.map { it.key }


/**
 * iOS-style entrance: each card rises and fades in a little after the one above it as the panel
 * opens ([progress] 0→1), so the list flows in instead of appearing at once.
 */
private fun Modifier.cascade(progress: Float, index: Int): Modifier = graphicsLayer {
    val delay = (index.coerceAtMost(6) * 0.08f)
    val t = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    alpha = eased
    translationY = (1f - eased) * 28.dp.toPx()
}

/** Shared adaptive glass keeps text legible and follows the user's material settings. */
@Composable
private fun NotifSurface(modifier: Modifier, onClick: (() -> Unit)?, kind: SurfaceKind?, content: @Composable () -> Unit) {
    GlassSurface(
        modifier = modifier,
        shape = GlassShape.rounded(28.dp),
        variant = GlassVariant.Regular,
        onClick = onClick,
        role = if (onClick != null) androidx.compose.ui.semantics.Role.Button else null,
        kind = kind ?: SurfaceKind.Panel,
    ) { content() }
}
