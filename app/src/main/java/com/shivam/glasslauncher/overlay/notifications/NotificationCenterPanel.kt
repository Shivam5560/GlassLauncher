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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.composed
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
import androidx.compose.ui.text.font.FontWeight
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
    // Cleared sticky notifications are already left out by NotificationHub.
    val entries by NotificationHub.entries.collectAsState()
    val connected by NotificationHub.connected.collectAsState()
    val media by MediaHub.state.collectAsState()
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    // iOS 26 has no filter chips: everything is one list.
    val filtered = entries
    val stacks = remember(filtered, expanded) { NotificationGrouping.stacks(filtered, expanded) }
    val reducedMotion = remember(context) { com.shivam.glasslauncher.widgets.reducedMotion(context) }

    val wallpaper by GlassApp.from(context).wallpaper.snapshot.collectAsState()
    val panelDim = remember(wallpaper) {
        PremiumDim.alphaFor(wallpaper.luminance(0f, 0f, wallpaper.screenWidth.toFloat(), wallpaper.screenHeight.toFloat()))
    }
    // iOS 26 keeps the wallpaper sharp behind Notification Centre; only a faint dim for text.
    OverlayWallpaperBackdrop(wallpaper, drawToScreen = true, dimAlpha = 0.12f) {
        Box(
            Modifier
                .fillMaxSize()
                .premiumDim(if (wallpaper.image == null) panelDim * progress else 0f)
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(Unit) { detectTapGestures(onTap = { host.close() }) },
        ) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 118.dp)
                    .graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        translationY = -(1f - progress) * 120.dp.toPx()
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 0.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item("clock") { LockClock(listState, onSwitch = { host.switchPanel() }) }
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
                // "Notification Centre" with a round clear button, as on iOS 26.
                item("title") {
                    Row(Modifier.fillMaxWidth().padding(start = 9.dp, end = 0.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassText("Notification Centre", Modifier.weight(1f), style = GlassType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1)
                        // Include dismissible summaries so a cleared group cannot return as an empty pile.
                        val clear = filtered.filter { !it.isMedia && it.canDismiss }.map { it.key }
                        if (clear.isNotEmpty()) GlassIconButton(GlassIcons.Close, onClick = { NotificationHub.dismissAll(clear) }, size = 33.dp, contentDescription = "Clear all notifications")
                    }
                }
                stacks.forEach { stack ->
                    if (stack.expanded) {
                        item("h:" + stack.packageName) {
                            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassText(stack.appLabel, Modifier.weight(1f), style = GlassType.Headline, color = Color.White, maxLines = 1)
                                GlassChip("Show less", selected = false, onClick = { expanded = expanded - stack.packageName })
                                Spacer(Modifier.width(8.dp))
                                if (appKeys(filtered, stack.packageName).isNotEmpty()) GlassIconButton(GlassIcons.Close, onClick = { NotificationHub.dismissAll(appKeys(filtered, stack.packageName)) }, size = 34.dp, contentDescription = "Clear notifications from ${stack.appLabel}")
                            }
                        }
                        items(stack.items, key = { it.key }) { e ->
                            Box(Modifier.animateItem().scrollStack(listState, e.key)) {
                                SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(listOf(e.key)) }, enabled = e.canDismiss) {
                                    NotificationCard(e, host, showActions = true, onClick = { if (NotificationHub.open(context, e.key)) host.close() }, kind = SurfaceKind.Panel)
                                }
                            }
                        }
                    } else {
                        item("s:" + stack.packageName) {
                            Box(Modifier.animateItem().scrollStack(listState, "s:" + stack.packageName).then(if (reducedMotion) Modifier else Modifier.cascade(progress, stacks.indexOf(stack)))) {
                                SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(appKeys(filtered, stack.packageName)) }, enabled = stack.clearableKeys.isNotEmpty()) {
                                    CollapsedStack(stack, host, onExpand = { expanded = expanded + stack.packageName }, kind = SurfaceKind.Panel)
                                }
                            }
                        }
                    }
                }
                if (connected && stacks.isEmpty()) {
                    item("empty") {
                        GlassText("No older notifications", Modifier.fillMaxWidth().padding(top = 18.dp), style = GlassType.Subhead.copy(textAlign = TextAlign.Center), color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            QuickButtons(host, Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp, bottom = 36.dp).graphicsLayer { alpha = progress.coerceIn(0f, 1f) })
        }
    }
}

/**
 * iOS 26 lock-screen header: the date over tall glass numerals. As cards scroll up it shrinks, lifts and
 * fades, like the clock giving way to Notification Centre.
 */
@Composable
private fun LockClock(state: androidx.compose.foundation.lazy.LazyListState, onSwitch: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val time = remember(now) { DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now).toString() }
    val date = remember(now) { DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), Date(now)).toString() }
    Box(
        Modifier.fillMaxWidth().graphicsLayer {
            val off = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else size.height
            val p = (off / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val sc = 1f - 0.3f * p
            scaleX = sc; scaleY = sc
            translationY = off * 0.45f
            alpha = 1f - p
            transformOrigin = TransformOrigin(0.5f, 0f)
        },
    ) {
        // Date below the camera row (iOS: ~11% of the height), clock right under it; nothing shares the island's row.
        Column(Modifier.fillMaxWidth().padding(top = 78.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassText(date, style = GlassType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.9f), maxLines = 1)
            androidx.compose.material3.Text(
                time,
                modifier = Modifier.graphicsLayer { scaleY = 1.12f; transformOrigin = TransformOrigin(0.5f, 0f) }.padding(top = 0.dp, bottom = 22.dp),
                maxLines = 1,
                style = GlassType.LargeTitle.copy(
                    // Light glass numerals from the system sans (Inter here ships no light weight).
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontSize = 190.sp, lineHeight = 190.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp,
                    textAlign = TextAlign.Center, fontFeatureSettings = "tnum",
                    // Translucent blue-white glass with a bright specular top.
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0xB3DCE8FF), Color(0x80C8D8FF))),
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0x26000000), androidx.compose.ui.geometry.Offset(0f, 2f), 12f),
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
        GlassIconButton(OverlayIcons.ControlCenter, onClick = onSwitch, size = 38.dp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp), contentDescription = "Open system quick settings")
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
    // iOS lock-screen corners: torch on the left, camera on the right.
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(
            OverlayIcons.Flashlight,
            onClick = { torchId?.let { try { camera?.setTorchMode(it, !torchOn) } catch (_: Exception) { } } },
            size = 54.dp,
            contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
            tint = if (torchOn) Color.White else null,
            tintGlyph = if (torchOn) Color.Black else null,
        )
        GlassIconButton(
            OverlayIcons.Camera,
            onClick = { host.launch(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
            size = 54.dp,
            contentDescription = "Open camera",
        )
    }
}

/**
 * Optional lock-screen stacks show app names and counts only when the native list is off.
 * Message text, media and actions remain private; the system keyguard still owns authentication.
 */
@Composable
fun LockScreenNotifications(host: PanelHost, maxHeight: androidx.compose.ui.unit.Dp) {
    val all by NotificationHub.entries.collectAsState()
    // Read once per lock: the window is rebuilt every time the phone locks.
    val privacy = remember {
        val context = host.context
        LockPrivacy(
            deviceSecure = context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure != false,
            allowPrivate = Settings.Secure.getInt(context.contentResolver, "lock_screen_allow_private_notifications", 1) != 0,
            lockedPackages = GlassApp.from(context).settings.state.value?.locked.orEmpty().mapTo(HashSet()) { it.substringBefore('/') },
        )
    }
    val entries = remember(all, privacy) { lockScreenEntries(all, privacy) }
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
                    if (entries.any { it.text == LOCK_REDACTED_TEXT }) GlassText("Unlock to view", style = GlassType.Caption, secondary = true)
                }
            }
        }
        stacks.forEach { stack ->
            if (stack.expanded) {
                item("collapse:" + stack.packageName) {
                    GlassButton("Show less · ${stack.appLabel}", onClick = { expanded = expanded - stack.packageName })
                }
                items(stack.items, key = { "lock:" + it.key }) { e ->
                    // Expanded cards open their notification after unlock; "Show less" collapses.
                    NotificationCard(e, host, showActions = false, onClick = { UnlockAndOpenActivity.start(host.context, e.key) }, kind = SurfaceKind.Panel)
                }
            } else {
                item("lock-stack:" + stack.packageName) {
                    CollapsedStack(stack, host, onExpand = { expanded = expanded + stack.packageName }, kind = SurfaceKind.Panel, locked = true)
                }
            }
        }
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
                                // A spring keeps settling off-screen (~0.5 s of empty gap); a short tween hands off as soon as the card is gone.
                                if (!reducedMotion) offset.animateTo(if (offset.value > 0) width * 1.1f else -width * 1.1f, androidx.compose.animation.core.tween(140))
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
            badge = if (stack.count > 1) "${stack.count}" else null,
            onClick = when {
                stack.count > 1 -> onExpand
                locked -> { { UnlockAndOpenActivity.start(host.context, stack.latest.key) } }
                else -> openLatest
            },
            kind = kind,
        )
    }
}

/**
 * iOS 26 card: app icon on the left (with the stack's count on it), sender in bold with the time on the
 * right, the message underneath. Actions and replies stay tucked away until a long press.
 */
@Composable
private fun NotificationCard(e: NotifEntry, host: PanelHost, showActions: Boolean, onClick: () -> Unit, badge: String? = null, kind: SurfaceKind? = null) {
    val context = host.context
    var replyIndex by remember(e.key) { mutableStateOf<Int?>(null) }
    var reply by remember(e.key) { mutableStateOf("") }
    var actionsOpen by remember(e.key) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val canAct = showActions && e.actions.isNotEmpty()
    NotifSurface(Modifier.fillMaxWidth(), onClick = onClick, onLongClick = if (canAct) ({ actionsOpen = !actionsOpen }) else null, kind = kind) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OverlayAppIcon(e.packageName, 38.dp)
                if (badge != null) {
                    // iOS: black count badge on the icon's top-right corner.
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).heightIn(min = 18.dp).widthIn(min = 18.dp)
                            .clip(GlassShape.Capsule).background(Color.Black, GlassShape.Capsule).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) { GlassText(badge, style = GlassType.Caption.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1) }
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    GlassText(e.title.ifBlank { e.appLabel }, Modifier.weight(1f), style = GlassType.Subhead.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 2)
                    Spacer(Modifier.width(8.dp))
                    GlassText(relativeTime(e.postTime), style = GlassType.Footnote, color = Color(0x8CE6EEFF), maxLines = 1)
                }
                if (e.text.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    GlassText(e.text, style = GlassType.Subhead.copy(fontSize = 15.sp, lineHeight = 20.sp), color = Color.White.copy(alpha = 0.9f), maxLines = if (actionsOpen) 8 else 4)
                }
                if (e.progressMax > 0 || e.progressIndeterminate) {
                    Spacer(Modifier.height(8.dp))
                    LinearBar(if (e.progressIndeterminate || e.progressMax <= 0) null else e.progress.toFloat() / e.progressMax, GlassColors.Accent)
                }
                if (canAct && actionsOpen) {
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
                            actionsOpen = false
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
                        GlassIconButton(OverlayIcons.Send, onClick = { send() }, size = 44.dp, tint = GlassColors.Accent)
                    }
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
                    GlassText(m.title.ifBlank { m.appLabel }, style = GlassType.Headline, color = Color.White, maxLines = 1)
                    GlassText(m.artist.ifBlank { m.appLabel }, style = GlassType.Callout, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
                AudioBars(m.playing, Modifier.size(width = 24.dp, height = 18.dp), m.art)
            }
            if (m.durationMs > 0) {
                Spacer(Modifier.height(6.dp))
                com.shivam.glasslauncher.overlay.island.SeekBar(m, Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                // Bare white glyphs, no circles (iOS 26).
                BareGlyph(OverlayIcons.Previous, "Previous", 30.dp) { MediaHub.previous() }
                BareGlyph(if (m.playing) OverlayIcons.Pause else OverlayIcons.Play, if (m.playing) "Pause" else "Play", 38.dp) { MediaHub.toggle() }
                BareGlyph(OverlayIcons.Next, "Next", 30.dp) { MediaHub.next() }
            }
        }
    }
}

@Composable
private fun BareGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(GlassShape.Circle)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { GlassIcon(icon, size = size, tint = Color.White) }
}

private fun relativeTime(t: Long): String {
    val now = System.currentTimeMillis()
    return if (now - t < 60_000) "now"
    else DateUtils.getRelativeTimeSpanString(t, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

/** Every non-media notification of [pkg], group summaries included (see Clear All). */
private fun appKeys(entries: List<NotifEntry>, pkg: String) = entries.filter { it.packageName == pkg && !it.isMedia && it.canDismiss }.map { it.key }


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
private fun NotifSurface(modifier: Modifier, onClick: (() -> Unit)?, kind: SurfaceKind?, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    GlassSurface(
        modifier = modifier,
        // Same glass recipe as the home widgets (frosted, rim-lit), at iOS 26's round corner (~30% of a two-line card).
        shape = GlassShape.rounded(28.dp),
        variant = GlassVariant.Regular,
        onClick = onClick,
        onLongClick = onLongClick,
        role = if (onClick != null) androidx.compose.ui.semantics.Role.Button else null,
        kind = SurfaceKind.Widget,
    ) { content() }
}

/**
 * iOS 26 scrolling: cards slide up under the clock and fade; at the bottom the next card tucks behind
 * the one above as a narrower sliver (a pile) and comes out of it as you scroll. Positions are read in
 * the layer block (draw only); the behind/in-front order changes only when a card enters the pile.
 */
private fun Modifier.scrollStack(state: androidx.compose.foundation.lazy.LazyListState, key: Any): Modifier = composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val peek = with(density) { 14.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    fun end(info: androidx.compose.foundation.lazy.LazyListLayoutInfo) = info.viewportEndOffset - info.afterContentPadding
    val inPile by remember(state, key) {
        androidx.compose.runtime.derivedStateOf {
            val info = state.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key }
            item != null && item.offset > 0 && item.offset + item.size > end(info)
        }
    }
    zIndex(if (inPile) -1f else 0f).graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
        val h = item.size.toFloat().coerceAtLeast(1f)
        val bottom = end(info)
        when {
            item.offset < 0 -> {
                // Leaving at the top: slide under and fade, barely shrinking.
                val p = (-item.offset / h).coerceIn(0f, 1f)
                translationY = -item.offset * 0.35f
                val sc = 1f - 0.04f * p
                scaleX = sc; scaleY = sc
                alpha = 1f - p
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            item.offset + h > bottom -> {
                // Would be cut by the edge: tuck behind the card above instead, leaving a sliver (iOS pile).
                val t = ((item.offset + h - bottom) / h).coerceIn(0f, 1f)
                translationY = -t * (h - peek + gap)
                val sc = 1f - 0.07f * t
                scaleX = sc; scaleY = sc
                alpha = 1f - 0.35f * t
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
        }
    }
}
