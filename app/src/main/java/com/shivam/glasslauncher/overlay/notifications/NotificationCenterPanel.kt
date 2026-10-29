package com.shivam.glasslauncher.overlay.notifications

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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.overlay.OverlayAppIcon
import com.shivam.glasslauncher.overlay.OverlayIcons
import com.shivam.glasslauncher.overlay.PanelHost
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
 * Notification Center: a lock-screen-style clock and date, the current media session pinned as a
 * live card, then notifications grouped per app as iOS 26 stacks (a collapsed pile; tap to fan it
 * out). Cards swipe away, Clear All removes every clearable one, action buttons run the app's
 * PendingIntents and inline reply fills the RemoteInput.
 */
@Composable
fun NotificationCenterPanel(host: PanelHost, progress: Float) {
    val context = host.context
    val all by NotificationHub.entries.collectAsState()
    val hidden by NotificationHub.hidden.collectAsState()
    // Cleared ongoing cards stay hidden until reposted (Clear works on HyperOS's sticky cards too).
    val entries = remember(all, hidden) { all.filter { (hidden[it.key] ?: -1L) < it.postTime } }
    val connected by NotificationHub.connected.collectAsState()
    val media by MediaHub.state.collectAsState()
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val stacks = remember(entries, expanded) { NotificationGrouping.stacks(entries, expanded) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f * progress))
            .pointerInput(Unit) { detectTapGestures(onTap = { host.close() }) },
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .imePadding()
                .graphicsLayer {
                    alpha = progress.coerceIn(0f, 1f)
                    translationY = -(1f - progress) * 120.dp.toPx()
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("clock") { BigClock(onSwitch = { host.switchPanel() }) }
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
            if (m != null) item("media") { MediaCard(m, host) }
            if (stacks.isNotEmpty()) {
                item("header") {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassText("Notification Center", Modifier.weight(1f).padding(start = 4.dp), style = GlassType.Title, color = Color.White)
                        // Every non-media key, group summaries included: apps that keep their summary after
                        // its children go would otherwise need a second Clear All.
                        val clear = entries.filter { !it.isMedia }.map { it.key }
                        if (clear.isNotEmpty()) GlassChip("Clear All", selected = false, onClick = { NotificationHub.dismissAll(clear) })
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
                            GlassIconButton(GlassIcons.Close, onClick = { NotificationHub.dismissAll(appKeys(entries, stack.packageName)) }, size = 30.dp)
                        }
                    }
                    items(stack.items, key = { it.key }) { e ->
                        SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(listOf(e.key)) }, enabled = true) {
                            NotificationCard(e, host, showActions = true, onClick = { if (NotificationHub.open(context, e.key)) host.close() })
                        }
                    }
                } else {
                    item("s:" + stack.packageName) {
                        SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(appKeys(entries, stack.packageName)) }, enabled = true) {
                            CollapsedStack(stack, host, onExpand = { expanded = expanded + stack.packageName })
                        }
                    }
                }
            }
            if (connected && stacks.isEmpty()) {
                item("empty") {
                    GlassText(
                        "No Older Notifications", Modifier.fillMaxWidth().padding(top = 24.dp),
                        style = GlassType.Headline, color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/**
 * Lock screen: the media card and the newest app stacks in Glass's widget glass, replacing HyperOS's
 * opaque lock-screen notifications. Tap a pile to fan it out; swipe a card or pile away.
 */
@Composable
fun LockScreenNotifications(host: PanelHost, maxHeight: androidx.compose.ui.unit.Dp) {
    val all by NotificationHub.entries.collectAsState()
    val hidden by NotificationHub.hidden.collectAsState()
    val entries = remember(all, hidden) { all.filter { (hidden[it.key] ?: -1L) < it.postTime } }
    val media by MediaHub.state.collectAsState()
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val stacks = remember(entries, expanded) { NotificationGrouping.stacks(entries, expanded) }
    // Capped above the unlock area; more cards scroll.
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        media?.let { MediaCard(it, host, SurfaceKind.Widget) }
        stacks.forEach { stack ->
            if (stack.expanded) {
                stack.items.take(4).forEach { e ->
                    SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(listOf(e.key)) }, enabled = true) {
                        NotificationCard(e, host, showActions = false, onClick = { expanded = expanded - stack.packageName }, kind = SurfaceKind.Widget)
                    }
                }
            } else {
                SwipeToDismiss(onDismiss = { NotificationHub.dismissAll(appKeys(entries, stack.packageName)) }, enabled = true) {
                    CollapsedStack(stack, host, onExpand = { expanded = expanded + stack.packageName }, kind = SurfaceKind.Widget)
                }
            }
        }
    }
}

@Composable
private fun BigClock(onSwitch: () -> Unit) {
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
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassIconButton(OverlayIcons.ControlCenter, onClick = onSwitch, size = 38.dp)
        }
        GlassText(date, style = GlassType.Headline.copy(fontSize = 19.sp), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        GlassText(
            time,
            style = GlassType.LargeTitle.copy(fontSize = 92.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-3).sp, lineHeight = 96.sp, textAlign = TextAlign.Center),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Horizontal swipe that throws the card off screen, then calls [onDismiss]. */
@Composable
private fun SwipeToDismiss(onDismiss: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
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
                                offset.animateTo(if (offset.value > 0) width * 1.2f else -width * 1.2f, spring(stiffness = 600f))
                                onDismiss()
                                offset.snapTo(0f)
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dx) }
                }
            },
    ) { content() }
}

/** An iOS stack: the newest card in front with up to two smaller glass slivers peeking below. */
@Composable
private fun CollapsedStack(stack: NotifStack, host: PanelHost, onExpand: () -> Unit, kind: SurfaceKind? = null) {
    val layers = stack.visibleLayers
    val openLatest: () -> Unit = { if (NotificationHub.open(host.context, stack.latest.key)) host.close() }
    val lift by animateFloatAsState(1f, spring(dampingRatio = 0.7f), label = "stack")
    Box(Modifier.fillMaxWidth().padding(bottom = (8 * (layers - 1)).dp)) {
        for (i in (layers - 1) downTo 1) {
            GlassSurface(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val s = 1f - 0.05f * i
                        scaleX = s
                        translationY = (9 * i).dp.toPx() * lift
                        alpha = 1f - 0.25f * i
                    },
                shape = GlassShape.Card,
                variant = GlassVariant.Regular,
                role = null,
                kind = kind,
            )
        }
        NotificationCard(
            stack.latest,
            host,
            showActions = false,
            badge = if (stack.hiddenCount > 0) "+${stack.hiddenCount}" else null,
            onClick = if (stack.count > 1) onExpand else openLatest,
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
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, variant = GlassVariant.Regular, onClick = onClick, kind = kind) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                OverlayAppIcon(e.packageName, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassText(e.title.ifBlank { e.appLabel }, Modifier.weight(1f), style = GlassType.Headline, maxLines = 1)
                        GlassText(relativeTime(e.postTime), style = GlassType.Caption, secondary = true, maxLines = 1)
                    }
                    if (e.text.isNotBlank()) GlassText(e.text, style = GlassType.Callout, maxLines = if (showActions) 6 else 3)
                    if (e.progressMax > 0 || e.progressIndeterminate) {
                        Spacer(Modifier.height(8.dp))
                        LinearBar(if (e.progressIndeterminate || e.progressMax <= 0) null else e.progress.toFloat() / e.progressMax, GlassColors.Accent)
                    }
                    if (badge != null) GlassText("$badge more from ${e.appLabel}", style = GlassType.Caption, secondary = true, maxLines = 1)
                }
            }
            if (showActions && e.actions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    GlassIconButton(OverlayIcons.Send, onClick = { send() }, size = 44.dp, tint = GlassColors.Accent)
                }
            }
        }
    }
}

/** The current media session, pinned above the notifications as a live card. */
@Composable
private fun MediaCard(m: MediaInfo, host: PanelHost, kind: SurfaceKind? = null) {
    val open: () -> Unit = { if (MediaHub.openPlayer(host.context)) host.close() }
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, variant = GlassVariant.Regular, onClick = open, kind = kind) {
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
                GlassIconButton(OverlayIcons.Previous, onClick = { MediaHub.previous() }, size = 40.dp)
                GlassIconButton(if (m.playing) OverlayIcons.Pause else OverlayIcons.Play, onClick = { MediaHub.toggle() }, size = 50.dp)
                GlassIconButton(OverlayIcons.Next, onClick = { MediaHub.next() }, size = 40.dp)
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
private fun appKeys(entries: List<NotifEntry>, pkg: String) = entries.filter { it.packageName == pkg && !it.isMedia }.map { it.key }
