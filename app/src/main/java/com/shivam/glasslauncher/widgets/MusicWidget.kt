package com.shivam.glasslauncher.widgets

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSlider
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import com.shivam.glasslauncher.ui.glass.ResumedLoop
import kotlinx.coroutines.delay

/**
 * Now playing from the active media session: album art (when the player exposes it), title,
 * artist, a progress bar that seeks when the session supports it, and transport controls.
 * Without notification access it shows a glass prompt that deep-links to grant it.
 */
@Composable
fun MusicWidget(size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { MediaRepository.get(context) }
    DisposableEffect(repo) {
        repo.start()
        onDispose { repo.stop() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { repo.retry() }
    val state by repo.state.collectAsState()

    when (val s = state) {
        MusicState.NoAccess -> WidgetPrompt(
            icon = WidgetGlyphs.Note,
            text = if (size == WidgetSize.SMALL) "Needs notification access" else "Music controls need notification access.",
            action = "Allow",
            onAction = { openNotificationAccess(context) },
            size = size,
            modifier = modifier,
        )
        MusicState.Idle -> WidgetFrame(modifier) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                ArtTile(null, if (size == WidgetSize.LARGE) 96.dp else 52.dp)
                Spacer(Modifier.height(8.dp))
                GlassText("Not Playing", style = GlassType.Headline, maxLines = 1)
                if (size != WidgetSize.SMALL) GlassText("Start music in any app", style = GlassType.Footnote, secondary = true, maxLines = 1)
            }
        }
        is MusicState.Playing -> PlayingContent(s.now, size, repo, modifier)
    }
}

@Composable
private fun PlayingContent(np: NowPlaying, size: WidgetSize, repo: MediaRepository, modifier: Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    ResumedLoop(np.playing, np.positionUpdatedAt) {
        while (np.playing) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }
    val position = np.positionNow(now)
    val open = { repo.openPlayer(); Unit }

    when (size) {
        WidgetSize.SMALL, WidgetSize.BAR -> WidgetFrame(modifier, onClick = open) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Top) {
                    ArtTile(np.art, 56.dp)
                    Spacer(Modifier.weight(1f))
                    PlayPause(np.playing, 40.dp) { repo.playPause() }
                }
                Spacer(Modifier.weight(1f))
                GlassText(np.title, style = GlassType.Headline, maxLines = 1)
                np.artist?.let { GlassText(it, style = GlassType.Footnote, secondary = true, maxLines = 1) }
                Spacer(Modifier.height(6.dp))
                ProgressLine(np, position)
            }
        }
        WidgetSize.MEDIUM -> WidgetFrame(modifier, onClick = open) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(1f)) { ArtTile(np.art, maxHeight) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        GlassText(np.title, style = GlassType.Headline, maxLines = 1)
                        np.artist?.let { GlassText(it, style = GlassType.Footnote, secondary = true, maxLines = 1) }
                    }
                    Seekbar(np, position, repo)
                    Controls(np, repo, 36.dp)
                }
            }
        }
        WidgetSize.LARGE -> WidgetFrame(modifier, onClick = open, padding = 18.dp) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true)) { ArtTile(np.art, maxHeight) }
                Spacer(Modifier.height(12.dp))
                GlassText(np.title, style = GlassType.Title, maxLines = 1)
                np.artist?.let { GlassText(it, style = GlassType.Callout, secondary = true, maxLines = 1) }
                Spacer(Modifier.height(6.dp))
                Seekbar(np, position, repo)
                Row(Modifier.fillMaxWidth()) {
                    GlassText(formatTime(position), style = GlassType.Caption, secondary = true)
                    Spacer(Modifier.weight(1f))
                    if (np.durationMs > 0) GlassText("-" + formatTime(np.durationMs - position), style = GlassType.Caption, secondary = true)
                }
                Spacer(Modifier.height(6.dp))
                Controls(np, repo, 48.dp)
            }
        }
    }
}

@Composable
private fun ArtTile(art: android.graphics.Bitmap?, size: Dp) {
    val shape = GlassShape.rounded((size.value * 0.18f).dp)
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        GlassSurface(Modifier.size(size), shape = shape, variant = GlassVariant.Clear, tintOverride = GlassColors.Red, role = null, contentAlignment = Alignment.Center) {
            GlassIcon(WidgetGlyphs.Note, size = size * 0.45f, tint = androidx.compose.ui.graphics.Color.White)
        }
    }
}

@Composable
private fun PlayPause(playing: Boolean, size: Dp, onClick: () -> Unit) {
    GlassIconButton(if (playing) WidgetGlyphs.Pause else WidgetGlyphs.Play, onClick = onClick, size = size)
}

@Composable
private fun Controls(np: NowPlaying, repo: MediaRepository, size: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(WidgetGlyphs.Previous, onClick = { repo.previous() }, size = size * 0.85f, tintGlyph = LocalGlassContentColor.current.copy(alpha = if (np.canSkipPrevious) 1f else 0.4f))
        PlayPause(np.playing, size) { repo.playPause() }
        GlassIconButton(WidgetGlyphs.Next, onClick = { repo.next() }, size = size * 0.85f, tintGlyph = LocalGlassContentColor.current.copy(alpha = if (np.canSkipNext) 1f else 0.4f))
    }
}

/** A glass slider that seeks when the session allows it, else a plain progress line. */
@Composable
private fun Seekbar(np: NowPlaying, position: Long, repo: MediaRepository) {
    if (np.durationMs <= 0) return
    if (!np.canSeek) {
        ProgressLine(np, position)
        return
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: (position.toFloat() / np.durationMs).coerceIn(0f, 1f)
    GlassSlider(
        value = fraction,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { repo.seekTo((it * np.durationMs).toLong()) }
            dragging = null
        },
    )
}

@Composable
private fun ProgressLine(np: NowPlaying, position: Long) {
    if (np.durationMs <= 0) return
    val f = (position.toFloat() / np.durationMs).coerceIn(0f, 1f)
    val c = LocalGlassContentColor.current
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val y = size.height / 2
        drawLine(c.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
        drawLine(c, Offset(0f, y), Offset(size.width * f, y), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
