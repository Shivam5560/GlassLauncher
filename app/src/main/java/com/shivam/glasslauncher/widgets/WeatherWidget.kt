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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    // Weather-tinted gradient on top of the glass, like iOS: blue by day, indigo by night.
    val sky = if (c.isDay) listOf(Color(0x553A8DDE), Color(0x22274F8F)) else listOf(Color(0x55202A5C), Color(0x22101633))

    WidgetFrame(modifier, onClick = onTap, padding = 0.dp) {
        Canvas(Modifier.fillMaxSize().clip(WidgetShape)) { drawRect(Brush.verticalGradient(sky)) }
        Column(Modifier.fillMaxSize().padding(14.dp)) {
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
                        style = GlassType.LargeTitle.copy(fontSize = if (size == WidgetSize.SMALL) 40.sp else 44.sp, fontWeight = FontWeight.Normal),
                        maxLines = 1,
                    )
                }
                if (size != WidgetSize.SMALL) {
                    Column(horizontalAlignment = Alignment.End) {
                        GlassIcon(WidgetGlyphs.weather(glyph), size = 26.dp, tint = WidgetGlyphs.weatherTint(glyph))
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
                Spacer(Modifier.weight(1f))
                HourlyStrip(data, fahrenheit)
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
                    drawLine(content.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
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
