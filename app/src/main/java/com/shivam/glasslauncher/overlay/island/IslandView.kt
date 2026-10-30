package com.shivam.glasslauncher.overlay.island

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.overlay.OverlayAppIcon
import com.shivam.glasslauncher.overlay.OverlayIcons
import com.shivam.glasslauncher.overlay.ProgressRing
import com.shivam.glasslauncher.overlay.formatDuration
import com.shivam.glasslauncher.overlay.notifications.MediaInfo
import com.shivam.glasslauncher.overlay.notifications.NotificationHub
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

enum class IslandPresentation { Hidden, Idle, Compact, Expanded }

/** What the island window draws this frame. Rects are in screen pixels. */
data class IslandFrame(
    val presentation: IslandPresentation,
    val ui: IslandUi,
    val pill: Rect,
    val bubble: Rect?,
    /** Compact pill height: the corner radius in compact form and the slot size. */
    val compactHeight: Float,
    /** Physical exclusion bounds, independent of the user's visual offset and scale. */
    val camera: Rect? = null,
)

interface IslandCallbacks {
    fun onTap(secondary: Boolean)
    fun onLongPress(secondary: Boolean)
    fun onSwipeAway()
}

private val IslandSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 360f)
private val Green = GlassColors.Green
private val Orange = GlassColors.Orange
private val Blue = Color(0xFF64D2FF)

/**
 * The Dynamic Island: an opaque black pill around the punch-hole camera (black like the camera
 * hole, so it reads as one shape) that morphs with springs between idle, compact, expanded and
 * split (a second bubble). Controls inside it are Clear glass.
 */
@Composable
fun IslandSurface(frame: IslandFrame, windowOrigin: Offset, callbacks: IslandCallbacks) {
    val density = LocalDensity.current
    val visible = frame.presentation != IslandPresentation.Hidden
    val expanded = frame.presentation == IslandPresentation.Expanded
    val left by animateFloatAsState(frame.pill.left, IslandSpring, label = "islandL")
    val top by animateFloatAsState(frame.pill.top, IslandSpring, label = "islandT")
    val width by animateFloatAsState(frame.pill.width, IslandSpring, label = "islandW")
    val height by animateFloatAsState(frame.pill.height, IslandSpring, label = "islandH")
    val expand by animateFloatAsState(if (expanded) 1f else 0f, IslandSpring, label = "islandExpand")
    val shown by animateFloatAsState(if (visible) 1f else 0f, spring(stiffness = 500f), label = "islandShown")
    var drag by remember { mutableFloatStateOf(0f) }

    val compactRadius = with(density) { (frame.compactHeight / 2f).toDp() }
    val radius = compactRadius + (40.dp - compactRadius) * expand
    val shape = GlassShape(radius, 2f + 1.4f * expand)

    Box(Modifier.fillMaxSize()) {
        val primary = frame.ui.slots.primary
        Box(
            Modifier
                .offset { IntOffset((left - windowOrigin.x + drag * 0.35f).roundToInt(), (top - windowOrigin.y).roundToInt()) }
                .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .graphicsLayer {
                    alpha = shown
                    val s = 0.6f + 0.4f * shown
                    scaleX = s
                    scaleY = s
                }
                .clip(shape)
                .background(Color.Black, shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { callbacks.onTap(secondary = false) },
                        onLongPress = { callbacks.onLongPress(secondary = false) },
                    )
                }
                .pointerInput(Unit) {
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { drag = 0f; dy = 0f },
                        onDragEnd = {
                            val far = 56.dp.toPx()
                            if (abs(drag) > far || dy < -far * 0.6f) callbacks.onSwipeAway()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    ) { change, amount ->
                        change.consume()
                        drag += amount.x
                        dy += amount.y
                    }
                },
        ) {
            if (primary != null) {
                if (expand < 0.98f) {
                    CompactRow(primary, frame.ui, frame.compactHeight, frame.camera, left, Modifier.alpha(1f - expand))
                }
                if (expand > 0.02f) {
                    // Reserve the physical cutout even when the user offsets or scales the pill.
                    val contentTop = if (primary.kind == IslandKind.Music) 14.dp else
                        IslandGeometry.contentTop(frame.camera?.bottom?.div(density.density), top / density.density, frame.compactHeight / density.density).dp
                    Box(Modifier.fillMaxSize().alpha(expand).padding(start = 18.dp, end = 18.dp, top = contentTop, bottom = 14.dp)) {
                        ExpandedContent(primary, frame.ui)
                    }
                }
            }
            if (frame.ui.cameraInUse || frame.ui.micInUse) PrivacyDots(frame.ui, frame.compactHeight, expand)
        }

        val bubble = frame.bubble
        val secondary = frame.ui.slots.secondary
        val bubbleIn by animateFloatAsState(if (bubble != null && secondary != null && visible) 1f else 0f, IslandSpring, label = "bubble")
        if (bubble != null && secondary != null && bubbleIn > 0.01f) {
            val bubbleSize = with(density) { bubble.width.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((bubble.left - windowOrigin.x).roundToInt(), (bubble.top - windowOrigin.y).roundToInt()) }
                    .size(bubbleSize)
                    .graphicsLayer {
                        // Splits out of the main pill: starts squeezed against it, springs out.
                        scaleX = bubbleIn
                        scaleY = 0.7f + 0.3f * bubbleIn
                        translationX = -(1f - bubbleIn) * size.width
                    }
                    .clip(GlassShape.Circle)
                    .background(Color.Black, GlassShape.Circle)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { callbacks.onTap(secondary = true) },
                            onLongPress = { callbacks.onLongPress(secondary = true) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Leading(secondary, frame.ui, bubbleSize * 0.56f)
            }
        }
    }
}

@Composable
private fun CompactRow(a: IslandActivity, ui: IslandUi, compactHeightPx: Float, camera: Rect?, pillLeft: Float, modifier: Modifier) {
    val density = LocalDensity.current.density
    val h = (compactHeightPx / density).dp
    BoxWithConstraints(modifier.fillMaxWidth().height(h)) {
        val glyphLane = h * 1.05f
        val start = IslandGeometry.trailingStart(camera?.right?.div(density), pillLeft / density, maxWidth.value, glyphLane.value).dp
        val leftRoom = camera?.let { ((it.left - pillLeft) / density - 8f).dp } ?: glyphLane
        if (leftRoom >= 16.dp) Box(Modifier.width(minOf(leftRoom, glyphLane)).height(h), contentAlignment = Alignment.Center) {
            Leading(a, ui, minOf(h * 0.58f, leftRoom - 4.dp))
        }
        Box(Modifier.fillMaxSize().padding(start = start, end = 12.dp), contentAlignment = Alignment.CenterEnd) {
            Trailing(a, ui, h)
        }
    }
}

/** Left side of the compact island (and the split bubble's glyph). */
@Composable
private fun Leading(a: IslandActivity, ui: IslandUi, size: Dp) {
    when (val c = a.content) {
        is IslandContent.Music -> AlbumArt(ui.media, size)
        is IslandContent.Charging -> GlassIcon(OverlayIcons.Bolt, size = size * 0.9f, tint = Green)
        is IslandContent.Call -> GlassIcon(GlassIcons.Phone, size = size * 0.85f, tint = Green)
        is IslandContent.Clock -> GlassIcon(OverlayIcons.Timer, size = size * 0.9f, tint = Orange)
        is IslandContent.Navigation -> GlassIcon(OverlayIcons.Navigate, size = size * 0.8f, tint = Blue)
        is IslandContent.Progress -> GlassIcon(if (c.upload) OverlayIcons.ArrowUp else OverlayIcons.ArrowDown, size = size * 0.85f, tint = Blue)
        is IslandContent.Peek -> a.sourcePackage?.let { OverlayAppIcon(it, size) } ?: GlassIcon(GlassIcons.Bell, size = size)
        is IslandContent.Ringer -> GlassIcon(ringerIcon(c.mode), size = size * 0.85f, tint = if (c.mode == RingerMode.Normal) Color.White else GlassColors.Red)
    }
}

@Composable
private fun Trailing(a: IslandActivity, ui: IslandUi, h: Dp) {
    val small = GlassType.Subhead.copy(fontWeight = FontWeight.SemiBold)
    when (val c = a.content) {
        is IslandContent.Music -> AudioBars(c.playing, Modifier.size(width = h * 0.5f, height = h * 0.36f), ui.media?.art)
        is IslandContent.Charging -> IslandLabel("${c.level}%", style = small, color = Green, maxLines = 1)
        is IslandContent.Call -> if (c.incoming) IslandLabel("Incoming", style = small, color = Green, maxLines = 1) else if (c.since == 0L) IslandLabel("Calling", style = small, color = Green, maxLines = 1) else LiveClock(c.since, false, small, Green)
        is IslandContent.Clock -> if (c.hasChronometer) LiveClock(c.base, c.countDown, small, Orange) else IslandLabel(IslandText.clean(c.stoppedLabel), style = small, color = Orange, maxLines = 1)
        is IslandContent.Navigation -> IslandLabel(IslandText.clean(c.primary), Modifier.padding(start = 8.dp), style = small, color = Color.White, maxLines = 1)
        is IslandContent.Progress -> ProgressRing(if (c.indeterminate) 0.25f else c.fraction, Blue, h * 0.5f, stroke = 2.5.dp)
        is IslandContent.Peek -> IslandLabel(IslandText.orFallback(c.title, c.appLabel), Modifier.padding(start = 4.dp), style = small, color = Color.White, maxLines = 1)
        is IslandContent.Ringer -> IslandLabel(ringerLabel(c.mode), style = small, color = if (c.mode == RingerMode.Normal) Color.White else GlassColors.Red, maxLines = 1)
    }
}

@Composable
private fun ExpandedContent(a: IslandActivity, ui: IslandUi) {
    val white = Color.White
    when (val c = a.content) {
        is IslandContent.Music -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            // As on iOS: art and waves flank the camera, the song sits below it.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(ui.media, 48.dp)
                Spacer(Modifier.weight(1f))
                AudioBars(c.playing, Modifier.size(width = 22.dp, height = 16.dp), ui.media?.art)
            }
            Column(Modifier.fillMaxWidth()) {
                IslandLabel(IslandText.musicTitle(c.title, ui.media?.appLabel), style = GlassType.Headline, color = white, maxLines = 1)
                IslandLabel(IslandText.musicArtist(c.artist), style = GlassType.Callout, color = white.copy(alpha = 0.6f), maxLines = 1)
            }
            MusicProgress(ui.media)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(OverlayIcons.Previous, onClick = { com.shivam.glasslauncher.overlay.notifications.MediaHub.previous() }, size = 40.dp, tintGlyph = white)
                GlassIconButton(if (c.playing) OverlayIcons.Pause else OverlayIcons.Play, onClick = { com.shivam.glasslauncher.overlay.notifications.MediaHub.toggle() }, size = 48.dp, tintGlyph = white)
                GlassIconButton(OverlayIcons.Next, onClick = { com.shivam.glasslauncher.overlay.notifications.MediaHub.next() }, size = 40.dp, tintGlyph = white)
            }
        }
        is IslandContent.Charging -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(c.level / 100f, Green, 64.dp, stroke = 5.dp)
                IslandLabel("${c.level}%", style = GlassType.Headline, color = white, maxLines = 1)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                IslandLabel("Charging", style = GlassType.Title, color = white, maxLines = 1)
                val w = c.watts
                IslandLabel(
                    if (w != null) ChargingEstimate.format(w) + " (estimated)" else if (ui.battery.wattsSupported) "Measuring…" else "This phone doesn't report charging current",
                    style = GlassType.Footnote, color = white.copy(alpha = 0.6f), maxLines = 2,
                )
            }
        }
        is IslandContent.Call -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                a.sourcePackage?.let { OverlayAppIcon(it, 40.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    IslandLabel(IslandText.clean(c.name), style = GlassType.Headline, color = white, maxLines = 1)
                    if (c.incoming) IslandLabel("Incoming call", style = GlassType.Footnote, color = white.copy(alpha = 0.6f), maxLines = 1)
                    else if (c.since == 0L) IslandLabel("Calling…", style = GlassType.Footnote, color = white.copy(alpha = 0.6f), maxLines = 1)
                    else LiveClock(c.since, false, GlassType.Footnote, Green)
                }
            }
            NotificationActions(c.notificationKey)
        }
        is IslandContent.Clock -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(OverlayIcons.Timer, size = 40.dp, tint = Orange)
                Spacer(Modifier.width(12.dp))
                IslandLabel(IslandText.clean(c.label), Modifier.weight(1f), style = GlassType.Headline, color = white, maxLines = 2)
                val big = GlassType.LargeTitle.copy(fontSize = 36.sp, fontWeight = FontWeight.Medium)
                if (c.hasChronometer) LiveClock(c.base, c.countDown, big, Orange) else IslandLabel(IslandText.clean(c.text), style = big, color = Orange, maxLines = 1)
            }
            // The clock app's own Stop / Silence / Pause buttons.
            a.notificationKey?.let { NotificationActions(it) }
        }
        is IslandContent.Navigation -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(OverlayIcons.Navigate, size = 44.dp, tint = Blue)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                IslandLabel(IslandText.clean(c.primary), style = GlassType.Title, color = white, maxLines = 2)
                if (IslandText.clean(c.secondary).isNotEmpty()) IslandLabel(IslandText.clean(c.secondary), style = GlassType.Callout, color = white.copy(alpha = 0.65f), maxLines = 2)
            }
        }
        is IslandContent.Progress -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(if (c.upload) OverlayIcons.ArrowUp else OverlayIcons.ArrowDown, size = 28.dp, tint = Blue)
                Spacer(Modifier.width(10.dp))
                IslandLabel(IslandText.clean(c.title), Modifier.weight(1f), style = GlassType.Headline, color = white, maxLines = 1)
                if (!c.indeterminate) IslandLabel("${(c.fraction * 100).roundToInt()}%", style = GlassType.Callout, color = white.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(12.dp))
            LinearBar(if (c.indeterminate) null else c.fraction, Blue)
        }
        is IslandContent.Peek -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                a.sourcePackage?.let { OverlayAppIcon(it, 26.dp) }
                Spacer(Modifier.width(8.dp))
                IslandLabel(IslandText.orFallback(c.appLabel, "Notification"), Modifier.weight(1f),
                    style = GlassType.Subhead, color = white.copy(alpha = 0.78f), maxLines = 1)
                IslandLabel("now", style = GlassType.Caption, color = white.copy(alpha = 0.6f), maxLines = 1)
            }
            IslandLabel(IslandText.orFallback(c.title, c.appLabel), style = GlassType.Headline.copy(lineHeight = 22.sp), color = white, maxLines = 2)
            val message = IslandText.clean(c.text)
            if (message.isNotEmpty()) IslandLabel(message, style = GlassType.Callout.copy(lineHeight = 20.sp), color = white.copy(alpha = 0.84f), maxLines = IslandGeometry.peekMessageLines(LocalDensity.current.fontScale))
        }
        is IslandContent.Ringer -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            GlassIcon(ringerIcon(c.mode), size = 30.dp, tint = if (c.mode == RingerMode.Normal) white else GlassColors.Red)
            Spacer(Modifier.width(10.dp))
            IslandLabel(ringerLabel(c.mode), style = GlassType.Headline, color = white)
        }
    }
}

@Composable
private fun NotificationActions(key: String) {
    val entries by NotificationHub.entries.collectAsState()
    // CallStyle adds its own Hang up next to the dialer's: HyperOS's in-call notification lists it twice.
    val actions = entries.firstOrNull { it.key == key }?.actions.orEmpty().distinctBy { it.title.lowercase() }.take(3)
    if (actions.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { act ->
            val t = act.title.lowercase()
            val decline = listOf("decline", "reject", "hang", "end", "dismiss").any { it in t }
            val answer = listOf("answer", "accept").any { it in t }
            GlassButton(
                act.title,
                onClick = { NotificationHub.runAction(context, key, act.index) },
                modifier = Modifier.weight(1f),
                destructive = decline,
                prominent = answer,
            )
        }
    }
}

@Composable
private fun AlbumArt(media: MediaInfo?, size: Dp) {
    val art = media?.art
    val bmp = remember(art) { art?.asImageBitmap() }
    if (bmp != null) {
        Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(GlassShape.rounded(size * 0.22f)))
    } else {
        Box(Modifier.size(size).clip(GlassShape.rounded(size * 0.22f)).background(Color(0xFF2C2C30)), contentAlignment = Alignment.Center) {
            GlassIcon(OverlayIcons.Note, size = size * 0.6f, tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

/**
 * Animated equaliser bars. Android only exposes the audio waveform with the microphone
 * permission (Visualizer), so the bars dance to a fixed rhythm while music plays and rest when paused.
 */
@Composable
fun AudioBars(playing: Boolean, modifier: Modifier, art: android.graphics.Bitmap? = null) {
    // HyperOS-style waves: each bar a gradient of the album art's two main vivid colours.
    val colors = remember(art) { runCatching { waveColors(art) }.getOrElse { listOf(Color.White, Color.White) } }
    // One continuous phase drives every bar (two mixed sines each), so the motion is fluid, not stepped.
    val phase = remember { androidx.compose.animation.core.Animatable(0f) }
    com.shivam.glasslauncher.ui.glass.ResumedLoop(playing) {
        if (playing) {
            phase.snapTo(0f)
            phase.animateTo((2 * Math.PI).toFloat(),
                infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.LinearEasing)))
        }
    }
    val calm by animateFloatAsState(if (playing) 1f else 0f, spring(stiffness = 200f), label = "barsCalm")
    Canvas(modifier) {
        val n = 5
        val gap = size.width * 0.1f
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val wave = 0.5f + 0.3f * kotlin.math.sin(phase.value * 2 + i * 1.3f) + 0.2f * kotlin.math.sin(phase.value * 3 - i * 0.9f)
            val v = 0.24f + (wave.coerceIn(0f, 1f) * 0.76f) * calm
            val bh = size.height * v
            val top = (size.height - bh) / 2f
            drawRoundRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(colors, startY = top, endY = top + bh),
                Offset(i * (w + gap), top), androidx.compose.ui.geometry.Size(w, bh), CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

@Composable
private fun MusicProgress(media: MediaInfo?) {
    if (media == null || media.durationMs <= 0) {
        Spacer(Modifier.height(4.dp))
        return
    }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.playing) {
        while (media.playing) {
            delay(500)
            now = android.os.SystemClock.elapsedRealtime()
        }
    }
    val pos = media.livePosition(now)
    // iOS: elapsed and remaining time either side of the bar.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val time = GlassType.Caption.copy(fontWeight = FontWeight.Medium)
        IslandLabel(mmss(pos), style = time, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) { LinearBar(pos.toFloat() / media.durationMs, Color.White) }
        IslandLabel("-" + mmss(media.durationMs - pos), style = time, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
    }
}

private fun mmss(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Thin progress bar; null = indeterminate (a sliding segment). */
@Composable
fun LinearBar(fraction: Float?, color: Color) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val slide by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100)), label = "slide")
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r)
        if (fraction != null) {
            drawRoundRect(color, size = androidx.compose.ui.geometry.Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
        } else {
            val segW = size.width * 0.3f
            drawRoundRect(color, Offset((size.width + segW) * slide - segW, 0f), androidx.compose.ui.geometry.Size(segW, size.height), r)
        }
    }
}

/** Ticking time from a chronometer base (wall clock): elapsed, or remaining when [countDown]. */
@Composable
private fun LiveClock(base: Long, countDown: Boolean, style: androidx.compose.ui.text.TextStyle, color: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(base) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val ms = if (countDown) base - now else now - base
    IslandLabel((if (ms < 0 && countDown) "-" else "") + formatDuration(abs(ms)), style = style, color = color, maxLines = 1)
}

@Composable
private fun PrivacyDots(ui: IslandUi, compactHeightPx: Float, expand: Float) {
    val h = with(LocalDensity.current) { compactHeightPx.toDp() }
    Row(
        Modifier.fillMaxWidth().height(h).padding(end = h * 0.22f).alpha(1f - expand),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        if (ui.micInUse) Dot(Orange, Modifier.padding(top = h * 0.2f))
        if (ui.micInUse && ui.cameraInUse) Spacer(Modifier.width(3.dp))
        if (ui.cameraInUse) Dot(Green, Modifier.padding(top = h * 0.2f))
    }
}

@Composable
private fun Dot(color: Color, modifier: Modifier) {
    Box(modifier.size(6.dp).clip(GlassShape.Circle).background(color))
}

private fun ringerIcon(mode: RingerMode) = when (mode) {
    RingerMode.Silent -> OverlayIcons.BellSlash
    RingerMode.Vibrate -> OverlayIcons.Vibrate
    RingerMode.Normal -> OverlayIcons.BellRing
}

private fun ringerLabel(mode: RingerMode) = when (mode) {
    RingerMode.Silent -> "Silent"
    RingerMode.Vibrate -> "Vibrate"
    RingerMode.Normal -> "Ring"
}

/**
 * Two vivid colours from [art] (the most common saturated hues), brightened for the black island;
 * white without art or with a greyscale cover.
 */
internal fun waveColors(art: android.graphics.Bitmap?): List<Color> {
    if (art == null) return listOf(Color.White, Color.White)
    val small = android.graphics.Bitmap.createScaledBitmap(art, 16, 16, true)
    val buckets = Array(12) { FloatArray(4) } // count, sum r, g, b
    val hsv = FloatArray(3)
    for (y in 0 until 16) for (x in 0 until 16) {
        val c = small.getPixel(x, y)
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] < 0.3f || hsv[2] < 0.3f) continue
        val b = buckets[(hsv[0] / 30f).toInt().coerceIn(0, 11)]
        b[0] += 1f; b[1] += android.graphics.Color.red(c).toFloat(); b[2] += android.graphics.Color.green(c).toFloat(); b[3] += android.graphics.Color.blue(c).toFloat()
    }
    if (small !== art) small.recycle()
    fun vivid(b: FloatArray): Color {
        android.graphics.Color.RGBToHSV((b[1] / b[0]).toInt(), (b[2] / b[0]).toInt(), (b[3] / b[0]).toInt(), hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.55f); hsv[2] = 1f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
    val top = buckets.filter { it[0] > 0f }.sortedByDescending { it[0] }
    return when {
        top.isEmpty() -> listOf(Color.White, Color.White)
        top.size == 1 -> listOf(vivid(top[0]), androidx.compose.ui.graphics.lerp(vivid(top[0]), Color.White, 0.45f))
        else -> listOf(vivid(top[0]), vivid(top[1]))
    }
}

/**
 * Island text: always ellipsised at its line limit (GlassText clips mid-glyph instead).
 * Input goes through [IslandText.clean] at the call sites.
 */
@Composable
private fun IslandLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = Color.White,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
