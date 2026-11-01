package com.shivam.glasslauncher.widgets

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Open-Meteo weather: current conditions (small), plus an hourly strip (medium) and a 7-day
 * forecast (large). Tapping refreshes on demand. Without a location it asks for coarse location
 * or a manually picked city (docs/CONSTRAINTS.md fallback).
 */
@Composable
fun WeatherWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val repo = remember { WeatherRepository.get(context) }
    val state by repo.state.collectAsState()
    val prefs by repo.prefs.weather.collectAsState()
    val fahrenheit = prefs?.fahrenheit == true
    val askLocation = rememberPermissionRequest(Manifest.permission.ACCESS_COARSE_LOCATION) { repo.useDeviceLocation() }

    LaunchedEffect(Unit) { repo.refreshIfStale() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { repo.refreshIfStale() }

    val data = state.data
    if (data == null) {
        when {
            state.needsLocation || (prefs?.latitude == null && !repo.hasLocationPermission()) -> WidgetPrompt(
                icon = WidgetGlyphs.Pin,
                text = if (size == WidgetSize.SMALL) "Weather needs a location" else "Weather needs your approximate location, or pick a city.",
                action = if (repo.hasLocationPermission()) "Choose City" else "Allow",
                onAction = { if (repo.hasLocationPermission()) env?.editWidget?.invoke(inst.id) else askLocation() },
                secondaryAction = if (repo.hasLocationPermission()) null else "Choose City",
                onSecondary = { env?.editWidget?.invoke(inst.id) },
                size = size,
                modifier = modifier,
            )
            else -> WidgetPrompt(
                icon = WidgetGlyphs.Cloud,
                text = if (state.loading) "Loading weather…" else state.error ?: "No weather yet",
                action = if (state.loading) null else "Retry",
                onAction = { repo.refreshIfStale(0) },
                size = size,
                modifier = modifier,
            )
        }
        return
    }

    val onTap = {
        repo.refreshIfStale(60_000L)
        env?.toast?.invoke("Updating weather…")
        Unit
    }
    val c = data.current
    val glyph = WeatherCodes.glyph(c.code, c.isDay)
    val today = data.daily.firstOrNull()
    val place = state.place ?: "Weather"

    WidgetFrame(modifier, onClick = onTap, padding = 0.dp) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassText(place, style = GlassType.Headline, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (prefs?.mode == WeatherLocationMode.DEVICE) {
                            Spacer(Modifier.width(3.dp))
                            GlassIcon(WidgetGlyphs.Pin, size = 12.dp)
                        }
                    }
                    GlassText(
                        "${WeatherCodes.display(c.tempC, fahrenheit)}°",
                        style = GlassType.LargeTitle.copy(fontSize = if (size == WidgetSize.SMALL) 44.sp else 52.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                }
                if (size != WidgetSize.SMALL) {
                    Column(horizontalAlignment = Alignment.End) {
                        GlassIcon(WidgetGlyphs.weather(glyph), size = 32.dp, tint = WidgetGlyphs.weatherTint(glyph))
                        Spacer(Modifier.height(4.dp))
                        GlassText(WeatherCodes.description(c.code), style = GlassType.Subhead, maxLines = 1)
                        today?.let { GlassText("H:${WeatherCodes.display(it.maxC, fahrenheit)}° L:${WeatherCodes.display(it.minC, fahrenheit)}°", style = GlassType.Subhead, maxLines = 1) }
                    }
                }
            }
            if (size == WidgetSize.SMALL) {
                Spacer(Modifier.weight(1f))
                GlassIcon(WidgetGlyphs.weather(glyph), size = 22.dp, tint = WidgetGlyphs.weatherTint(glyph))
                GlassText(WeatherCodes.description(c.code), style = GlassType.Subhead, maxLines = 1)
                today?.let { GlassText("H:${WeatherCodes.display(it.maxC, fahrenheit)}° L:${WeatherCodes.display(it.minC, fahrenheit)}°", style = GlassType.Subhead, maxLines = 1) }
            } else {
                if (inst[WidgetKeys.WEATHER_STYLE] == "classic") {
                    Spacer(Modifier.weight(1f))
                    HourlyStrip(data, fahrenheit)
                } else {
                    // The wave takes whatever room the card has left (more in a tall stack).
                    HourlyWave(data, fahrenheit, Modifier.weight(if (size == WidgetSize.LARGE) 2f else 1f).heightIn(min = 60.dp))
                }
                if (size == WidgetSize.LARGE) {
                    Spacer(Modifier.height(10.dp))
                    DailyList(data, fahrenheit, Modifier.weight(3f))
                }
            }
            if (state.error != null && size != WidgetSize.SMALL) {
                GlassText(state.error ?: "", style = GlassType.Caption, secondary = true, maxLines = 1)
            }
        }
    }
}

/**
 * Weather 3, the hourly wave: the next ten hours of temperature as a liquid surface in the
 * theme's colour, warmest and coolest hours labelled, a glowing dot for now.
 */
@Composable
private fun HourlyWave(data: WeatherData, fahrenheit: Boolean, modifier: Modifier) {
    val hours = OpenMeteo.upcomingHours(data, 10)
    if (hours.size < 2) return HourlyStrip(data, fahrenheit)
    val accent = com.shivam.glasslauncher.ui.glass.currentAccent()
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val temps = hours.map { WeatherCodes.display(it.tempC, fahrenheit) }
    // The curve follows the exact forecast; whole degrees would turn it into flat steps.
    val exact = hours.map { (if (fahrenheit) it.tempC * 9 / 5 + 32 else it.tempC).toFloat() }
    val hi = exact.indices.maxBy { exact[it] }
    val lo = exact.indices.minBy { exact[it] }
    Canvas(modifier.fillMaxWidth()) {
        val labelH = 14.dp.toPx()
        val top = labelH + 4.dp.toPx()
        val bottom = size.height - labelH - 2.dp.toPx()
        val pad = 10.dp.toPx()
        val tMin = exact.min()
        // At least 4° of range, so a one-degree drift stays a gentle swell.
        val span = (exact.max() - tMin).coerceAtLeast(4f)
        val pts = exact.mapIndexed { i, t ->
            // The coolest hour still sits a quarter up, so there's always a body of liquid under the curve.
            Offset(pad + (size.width - 2 * pad) * i / (temps.size - 1), bottom - (bottom - top) * (0.25f + 0.62f * (t - tMin) / span))
        }
        val curve = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val mx = (pts[i - 1].x + pts[i].x) / 2
                cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
            }
        }
        val fill = androidx.compose.ui.graphics.Path().apply {
            addPath(curve)
            lineTo(pts.last().x, bottom)
            lineTo(pts[0].x, bottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0.06f)), startY = top, endY = bottom))
        drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), startY = top, endY = top + 26.dp.toPx()))
        drawPath(curve, Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        pts.forEachIndexed { i, p ->
            // Hours along the bottom (every other one), temperatures above the warmest, coolest and now.
            if (i % 2 == 0) {
                val t = measurer.measure(if (i == 0) "Now" else hourLabel(hours[i].time), androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f)))
                drawText(t, topLeft = Offset((p.x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), size.height - labelH))
            }
            if (i == 0 || i == hi || i == lo) {
                val t = measurer.measure("${temps[i]}°", androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White))
                drawText(t, topLeft = Offset((p.x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), p.y - t.size.height - 4.dp.toPx()))
            }
        }
        drawCircle(accent.copy(alpha = 0.45f), 9.dp.toPx(), pts[0])
        drawCircle(Color.White, 4.dp.toPx(), pts[0])
    }
}

@Composable
private fun HourlyStrip(data: WeatherData, fahrenheit: Boolean) {
    val hours = OpenMeteo.upcomingHours(data, 6)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        hours.forEachIndexed { i, h ->
            val g = WeatherCodes.glyph(h.code, h.isDay)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassText(if (i == 0) "Now" else hourLabel(h.time), style = GlassType.Caption, secondary = true, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                GlassIcon(WidgetGlyphs.weather(g), size = 20.dp, tint = WidgetGlyphs.weatherTint(g))
                Spacer(Modifier.height(4.dp))
                GlassText("${WeatherCodes.display(h.tempC, fahrenheit)}°", style = GlassType.Subhead, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DailyList(data: WeatherData, fahrenheit: Boolean, modifier: Modifier) {
    val days = data.daily.take(7)
    if (days.isEmpty()) return
    val lo = days.minOf { it.minC }
    val hi = days.maxOf { it.maxC }
    val content = LocalGlassContentColor.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
        days.forEachIndexed { i, d ->
            val g = WeatherCodes.glyph(d.code, true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassText(if (i == 0) "Today" else dayLabel(d.date), style = GlassType.Subhead, maxLines = 1, modifier = Modifier.width(52.dp))
                Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIcon(WidgetGlyphs.weather(g), size = 18.dp, tint = WidgetGlyphs.weatherTint(g))
                        val p = d.precipProbability
                        if (p != null && p >= 30) GlassText("$p%", style = GlassType.Caption, color = Color(0xFF64D2FF), maxLines = 1)
                    }
                }
                GlassText("${WeatherCodes.display(d.minC, fahrenheit)}°", style = GlassType.Subhead, secondary = true, maxLines = 1, modifier = Modifier.width(34.dp))
                Canvas(Modifier.weight(1f).height(5.dp)) {
                    val span = (hi - lo).coerceAtLeast(1.0)
                    val x0 = ((d.minC - lo) / span).toFloat() * size.width
                    val x1 = ((d.maxC - lo) / span).toFloat() * size.width
                    val y = size.height / 2
                    drawLine(content.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
                    drawLine(
                        Brush.horizontalGradient(listOf(Color(0xFF64D2FF), Color(0xFFFFD60A), Color(0xFFFF9F0A)), startX = 0f, endX = size.width),
                        Offset(x0, y), Offset(x1.coerceAtLeast(x0 + 1f), y), strokeWidth = size.height, cap = StrokeCap.Round,
                    )
                }
                GlassText("${WeatherCodes.display(d.maxC, fahrenheit)}°", style = GlassType.Subhead, maxLines = 1, modifier = Modifier.padding(start = 8.dp).width(34.dp))
            }
        }
    }
}

private fun hourLabel(iso: String): String = try {
    val t = LocalDateTime.parse(iso)
    val h = t.hour % 12
    "${if (h == 0) 12 else h}${if (t.hour < 12) "AM" else "PM"}"
} catch (e: Exception) {
    iso.takeLast(5)
}

private fun dayLabel(iso: String): String = try {
    LocalDate.parse(iso).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
} catch (e: Exception) {
    iso
}
