package com.shivam.glasslauncher.widgets

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.currentAccent
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.LocalGlassLight
import kotlin.math.PI
import kotlin.math.sin

/**
 * Claude usage as shown by u.sage's widget. Glass hosts that widget, so it reads the same texts
 * u.sage draws (no login, no network) and paints its own glass version over them.
 */
data class ClaudeUsage(
    val session: Int,
    val sessionNote: String?,
    val weekly: Int,
    val resets: String?,
    /** Share of the week already gone (u.sage's last %), if shown. */
    val weekGone: Int?,
    /** Minutes until the 5-hour session window resets, from "Resets in 4 hr 23 min". */
    val sessionResetMinutes: Int? = null,
) {
    companion object {
        const val PACKAGE = "u.sage"
        private val PERCENT = Regex("""^(\d{1,3})\s*%$""")

        private fun pct(s: String) = PERCENT.find(s)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
        private val HOURS = Regex("(\\d+)\\s*(?:hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE)
        private val MINUTES = Regex("(\\d+)\\s*(?:minutes?|mins?|m)\\b", RegexOption.IGNORE_CASE)

        /** "Resets in 4 hr 23 min" -> 263; null when the note holds no duration. */
        fun minutesIn(note: String?): Int? {
            if (note == null || !note.contains(" in ", ignoreCase = true)) return null
            val h = HOURS.find(note)?.groupValues?.get(1)?.toIntOrNull()
            val m = MINUTES.find(note)?.groupValues?.get(1)?.toIntOrNull()
            if (h == null && m == null) return null
            return (h ?: 0) * 60 + (m ?: 0)
        }

        /** 263 -> "4h 23m", 45 -> "45m". */
        fun formatLeft(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes.coerceAtLeast(0)}m"

        /**
         * Reads u.sage's texts, in layout order: "Current session", "0%", note, "Weekly limits",
         * "78%", "Resets Mon, 10:30 pm", "83%". Null when the layout isn't recognised, so the
         * caller shows u.sage's own widget instead of wrong numbers.
         */
        fun parse(texts: List<String>): ClaudeUsage? {
            val s = texts.indexOfFirst { it.contains("session", ignoreCase = true) }
            val w = texts.indexOfFirst { it.contains("weekly", ignoreCase = true) }
            if (s < 0 || w < 0) return null
            val session = texts.subList(s + 1, w).firstNotNullOfOrNull(::pct) ?: return null
            val note = texts.subList(s + 1, w).firstOrNull { pct(it) == null }
            val after = texts.drop(w + 1)
            val weekly = after.firstNotNullOfOrNull(::pct) ?: return null
            val resets = after.firstOrNull { it.startsWith("Resets", ignoreCase = true) }
            val weekGone = after.mapNotNull(::pct).getOrNull(1)
            return ClaudeUsage(session, note, weekly, resets, weekGone, minutesIn(note))
        }
    }
}

private val Hot = Color(0xFFFF6B6B)
private val ClaudeOrange = Color(0xFFE07A52)

/** The theme's accent, turning red from 90 % as a warning. */
internal fun liquidColor(p: Float, accent: Color): Color = if (p >= 90f) Hot else accent

/**
 * The Liquid Tank: weekly use is liquid filling the glass, sloshing with the phone's tilt; the
 * session rises through it as bubbles. A dashed line marks how much of the week has gone, so
 * liquid above the line means you're using it faster than time passes.
 */
@Composable
fun ClaudeLiquidTank(
    usage: ClaudeUsage,
    modifier: Modifier = Modifier,
    /** When the session reset was read (ms), for a live countdown between u.sage's updates. */
    readAt: Long = System.currentTimeMillis(),
    onRefresh: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val still = remember(context) { reducedMotion(context) }
    val level by animateFloatAsState(usage.weekly / 100f, spring(dampingRatio = 0.6f, stiffness = 60f), label = "level")
    val color = liquidColor(usage.weekly.toFloat(), currentAccent())
    val light by LocalGlassLight.current
    val tiltTarget = (light.x * 0.9f).coerceIn(-1f, 1f)
    val tilt by animateFloatAsState(tiltTarget, spring(dampingRatio = 0.35f, stiffness = 40f), label = "slosh")
    // The liquid moves when disturbed (Home shown, a tilt, a refresh) and settles still within ~6 s.
    // A forever-running wave redrew at 120 fps and kept 1.3 CPU cores busy while Home sat idle,
    // even from the page next door (the pager keeps neighbours composed). Measured 2026-11-01.
    val wave = remember { androidx.compose.animation.core.Animatable(0f) }
    var stir by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { stir++ }
    val tiltStep = (tiltTarget * 8).toInt() // a noticeable tilt, not sensor jitter
    androidx.compose.runtime.LaunchedEffect(tiltStep, usage.weekly, usage.session) { stir++ }
    androidx.compose.runtime.LaunchedEffect(stir) {
        if (!still) wave.animateTo(wave.value + 1.5f, tween(6000, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    val phase = wave.value
    val bubbles = remember { List(24) { i -> Triple(0.08f + (i * 0.618f) % 1f * 0.84f, (i * 0.377f) % 1f, 1.5f + (i % 4)) } }

    WidgetFrame(modifier, onClick = onClick, padding = 0.dp) {
        Canvas(Modifier.fillMaxSize().clip(WidgetShape)) {
            val w = size.width
            val h = size.height
            val surface = h - h * level
            val slope = tilt * h * 0.08f
            // The liquid is a triangle strip, not a filled Path: Skia rasterised each frame's new wave
            // path into a cached software mask (661 masks, 209 MB of GPU memory, measured 2026-11-01).
            val steps = 32
            val top = Path()
            val n2 = (steps + 1) * 2
            val fill = FloatArray(n2 * 2); val gloss = FloatArray(n2 * 2)
            val fillC = IntArray(n2); val glossC = IntArray(n2)
            val deep = color.copy(alpha = 0.85f).toArgb(); val shallow = color.copy(alpha = 0.35f).toArgb()
            val shine = Color.White.copy(alpha = 0.22f).toArgb(); val clear = Color.White.copy(alpha = 0f).toArgb()
            val band = 28.dp.toPx()
            for (i in 0..steps) {
                val x = w * i / steps
                val t = i.toFloat() / steps
                val y = surface +
                    sin(t * 2.4f * PI.toFloat() + phase * 2f * PI.toFloat()) * 5.dp.toPx() +
                    sin(t * 5.1f * PI.toFloat() - phase * 4f * PI.toFloat()) * 2.dp.toPx() +
                    (t - 0.5f) * slope
                if (i == 0) top.moveTo(x, y) else top.lineTo(x, y)
                val k = i * 4
                fill[k] = x; fill[k + 1] = y; fill[k + 2] = x; fill[k + 3] = h
                gloss[k] = x; gloss[k + 1] = y; gloss[k + 2] = x; gloss[k + 3] = minOf(y + band, h)
                fillC[i * 2] = deep; fillC[i * 2 + 1] = shallow
                glossC[i * 2] = shine; glossC[i * 2 + 1] = clear
            }
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val paint = android.graphics.Paint()
                val mode = android.graphics.Canvas.VertexMode.TRIANGLE_STRIP
                nc.drawVertices(mode, fill.size, fill, 0, null, 0, fillC, 0, null, 0, 0, paint)
                nc.drawVertices(mode, gloss.size, gloss, 0, null, 0, glossC, 0, null, 0, 0, paint)
            }
            drawPath(top, Color.White.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
            // Session bubbles: more of them the busier the session (a few always drift).
            val n = (bubbles.size * maxOf(usage.session, 8) / 100f).toInt()
            bubbles.take(n).forEach { (bx, by, r) ->
                val y = ((by - phase * (0.6f + r / 10f)) % 1f + 1f) % 1f
                val py = surface + (h - surface) * y
                if (py > surface + 6.dp.toPx()) drawCircle(Color.White.copy(alpha = 0.4f), r.dp.toPx(), Offset(bx * w, py))
            }
            usage.weekGone?.let { gone ->
                val y = h - h * gone / 100f
                drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, y), Offset(w, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            }
        }
        // Ticks once a minute so the session countdown stays live between u.sage's updates.
        val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
            while (true) { kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000); value = System.currentTimeMillis() }
        }
        val left = usage.sessionResetMinutes?.let { it - ((now - readAt) / 60_000).toInt() }
        // Header A: no header row. The spark beside the number is the refresh button and spins
        // until u.sage's new numbers arrive (or 6 s pass).
        var syncing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(readAt) { syncing = false }
        androidx.compose.runtime.LaunchedEffect(syncing) { if (syncing) { kotlinx.coroutines.delay(6000); syncing = false } }
        val spin = rememberInfiniteTransition(label = "sync")
        val angle by if (syncing && !still) spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
            else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val ago = ((now - readAt) / 60_000).toInt()
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Top) {
                        GlassText("${usage.weekly}%", style = GlassType.Title.copy(fontSize = 52.sp, fontWeight = FontWeight.Light), color = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.padding(top = 8.dp)) {
                            val tap = onRefresh?.let { r -> { if (!syncing) { syncing = true; r() } } }
                            Canvas(
                                Modifier.size(24.dp)
                                    .then(if (tap != null) Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = "Refresh Claude usage", onClick = tap) else Modifier)
                                    .rotate(angle),
                            ) { drawSpark(ClaudeOrange) }
                            GlassText("CLAUDE", style = GlassType.Caption.copy(fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold), color = Color.White)
                            GlassText(
                                when { syncing -> "syncing…"; ago < 1 -> "just now"; ago < 60 -> "${ago}m ago"; else -> "${ago / 60}h ago" },
                                style = GlassType.Caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }
                    usage.resets?.let { GlassText(it, style = GlassType.Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = Color.White.copy(alpha = 0.85f), maxLines = 1) }
                }
            }
            GlassText("Session ${usage.session}%", style = GlassType.Caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
            val sessionLine = when {
                left != null && left > 0 -> "${ClaudeUsage.formatLeft(left)} left in this window"
                left != null -> "Window reset, refresh to update"
                else -> usage.sessionNote
            }
            sessionLine?.let { GlassText(it, style = GlassType.Caption.copy(fontSize = 11.sp), color = Color.White.copy(alpha = 0.85f), maxLines = 1) }
        }
    }
}

/** The Claude spark: six strokes crossing at the centre (twelve rays). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpark(color: Color) {
    val c = center
    val r = size.minDimension / 2 * 0.9f
    for (i in 0 until 6) {
        val a = i * PI.toFloat() / 6
        val d = Offset(kotlin.math.cos(a) * r, sin(a) * r)
        drawLine(color, c - d, c + d, 2.2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
