package com.shivam.glasslauncher.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Clock styles stored in the widget config. */
object ClockStyles {
    const val DIGITAL = "digital"
    const val ANALOG = "analog"
    const val WORLD = "world"

    val defaultZones = listOf("America/New_York", "Europe/London", "Asia/Tokyo", "Australia/Sydney")

    /** "Asia/Kolkata" -> "Kolkata". */
    fun cityName(zone: String): String = zone.substringAfterLast('/').replace('_', ' ')

    /** Region/City zones for the world clock picker, alphabetical by city. */
    val pickableZones: List<String> by lazy {
        ZoneId.getAvailableZoneIds()
            .filter { z -> z.contains('/') && !z.startsWith("Etc/") && !z.startsWith("SystemV/") && z.first().isUpperCase() && z.count { it == '/' } <= 2 }
            .sortedBy { cityName(it) }
    }
}

/** Ticks once per second (or per minute when seconds are not shown). */
@Composable
private fun rememberNow(everySecond: Boolean): Long {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), everySecond, lifecycle) {
        // Only ticks while Glass is resumed (no wakeups in the background).
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                val t = System.currentTimeMillis()
                value = t
                val step = if (everySecond) 1000L else 60_000L
                delay(step - t % step + 5)
            }
        }
    }
    return now
}

@Composable
fun ClockWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    when (inst[WidgetKeys.CLOCK_STYLE] ?: ClockStyles.DIGITAL) {
        ClockStyles.ANALOG -> AnalogClockWidget(size, modifier)
        ClockStyles.WORLD -> WorldClockWidget(inst.list(WidgetKeys.CLOCK_ZONES).ifEmpty { ClockStyles.defaultZones }, size, modifier)
        else -> DigitalClockWidget(size, modifier)
    }
}

@Composable
private fun DigitalClockWidget(size: WidgetSize, modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberNow(everySecond = false)
    val is24 = DateFormat.is24HourFormat(context)
    val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val time = t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val date = t.format(DateTimeFormatter.ofPattern(if (size == WidgetSize.SMALL) "EEE d MMM" else "EEEE, d MMMM"))
    WidgetFrame(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            GlassText(date.uppercase(), style = GlassType.Caption, color = GlassColors.Orange, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                GlassText(time, style = GlassType.LargeTitle.copy(fontSize = if (size == WidgetSize.SMALL) 46.sp else 64.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                if (!is24) GlassText(if (t.hour < 12) " AM" else " PM", style = GlassType.Headline, secondary = true)
            }
            GlassText(ZoneId.systemDefault().id.let { ClockStyles.cityName(it) }, style = GlassType.Footnote, secondary = true, maxLines = 1)
        }
    }
}

@Composable
private fun AnalogClockWidget(size: WidgetSize, modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberNow(everySecond = true)
    val zone = ZoneId.systemDefault()
    val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
    WidgetFrame(modifier, padding = 10.dp) {
        if (size == WidgetSize.SMALL) {
            Dial(t, Modifier.fillMaxSize(), seconds = true)
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Dial(t, Modifier.fillMaxHeight().aspectRatio(1f), seconds = true)
                Spacer(Modifier.width(16.dp))
                Column {
                    GlassText(t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(), style = GlassType.Caption, color = GlassColors.Orange)
                    GlassText(t.format(DateTimeFormatter.ofPattern("d MMMM")), style = GlassType.Title, maxLines = 1)
                    GlassText(
                        t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")),
                        style = GlassType.Callout, secondary = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldClockWidget(zones: List<String>, size: WidgetSize, modifier: Modifier) {
    val now = rememberNow(everySecond = false)
    val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val shown = zones.take(if (size == WidgetSize.SMALL) 2 else 4)
    WidgetFrame(modifier, padding = 10.dp) {
        if (size == WidgetSize.SMALL) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                shown.forEach { z -> CityRow(z, now, local) }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                shown.forEach { z ->
                    val t = zoned(now, z) ?: return@forEach
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Dial(t, Modifier.size(minOf(maxWidth, maxHeight)), seconds = false, night = t.hour < 6 || t.hour >= 18)
                        }
                        Spacer(Modifier.height(4.dp))
                        GlassText(ClockStyles.cityName(z), style = GlassType.Caption, maxLines = 1)
                        GlassText(offsetLabel(t, local), style = GlassType.Caption, secondary = true, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CityRow(zone: String, now: Long, local: ZonedDateTime) {
    val context = LocalContext.current
    val t = zoned(now, zone) ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            GlassText(ClockStyles.cityName(zone), style = GlassType.Subhead, maxLines = 1)
            GlassText(offsetLabel(t, local), style = GlassType.Caption, secondary = true, maxLines = 1)
        }
        GlassText(t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")), style = GlassType.Title, maxLines = 1)
    }
}

private fun zoned(now: Long, zone: String): ZonedDateTime? =
    try { ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.of(zone)) } catch (e: Exception) { null }

/** "Today, +4h" / "Tomorrow, -5h30m". */
private fun offsetLabel(t: ZonedDateTime, local: ZonedDateTime): String {
    val diffMin = (t.offset.totalSeconds - local.offset.totalSeconds) / 60
    val day = when (t.toLocalDate().compareTo(local.toLocalDate())) {
        0 -> "Today"
        in 1..Int.MAX_VALUE -> "Tomorrow"
        else -> "Yesterday"
    }
    if (diffMin == 0) return day
    val sign = if (diffMin > 0) "+" else "-"
    val h = kotlin.math.abs(diffMin) / 60
    val m = kotlin.math.abs(diffMin) % 60
    return "$day, $sign${h}h" + if (m != 0) "${m}m" else ""
}

/** An analog dial drawn on the glass: ticks, hour and minute hands, optional orange seconds. */
@Composable
private fun Dial(t: ZonedDateTime, modifier: Modifier, seconds: Boolean, night: Boolean = false) {
    val content = LocalGlassContentColor.current
    val face = if (night) Color(0xFF1C1C1E).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f)
    val ink = if (night) Color.White else content
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(face, r, c)
        drawCircle(ink.copy(alpha = 0.25f), r, c, style = Stroke(width = r * 0.02f))
        for (i in 0 until 60) {
            val major = i % 5 == 0
            if (!major && r < 40.dp.toPx()) continue
            val a = i / 60f * 2f * PI.toFloat()
            val outer = r * 0.92f
            val inner = if (major) r * 0.78f else r * 0.86f
            drawLine(
                ink.copy(alpha = if (major) 0.9f else 0.35f),
                c + polar(a, inner), c + polar(a, outer),
                strokeWidth = if (major) r * 0.035f else r * 0.015f, cap = StrokeCap.Round,
            )
        }
        val sec = t.second
        val min = t.minute + sec / 60f
        val hr = (t.hour % 12) + min / 60f
        hand(ink, c, hr / 12f, r * 0.5f, r * 0.07f)
        hand(ink, c, min / 60f, r * 0.75f, r * 0.05f)
        if (seconds) hand(GlassColors.Orange, c, sec / 60f, r * 0.82f, r * 0.02f)
        drawCircle(if (seconds) GlassColors.Orange else ink, r * 0.05f, c)
    }
}

private fun polar(angle: Float, radius: Float) = Offset(sin(angle) * radius, -cos(angle) * radius)

private fun DrawScope.hand(color: Color, c: Offset, fraction: Float, length: Float, width: Float) {
    val a = fraction * 2f * PI.toFloat()
    drawLine(color, c - polar(a, length * 0.14f), c + polar(a, length), strokeWidth = width, cap = StrokeCap.Round)
}
