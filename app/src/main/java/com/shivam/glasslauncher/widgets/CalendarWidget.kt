package com.shivam.glasslauncher.widgets

import android.Manifest
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today's date and "Up next" events with coloured calendar markers. READ_CALENDAR is asked the
 * first time the user taps the prompt; tapping an event opens it in the calendar app.
 */
@Composable
fun CalendarWidget(size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tick = remember { mutableIntStateOf(0) }
    val ask = rememberPermissionRequest(Manifest.permission.READ_CALENDAR) { tick.intValue++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick.intValue++ }
    LaunchedEffect(Unit) {
        // Drop finished events and roll over at midnight.
        while (true) {
            delay(60_000L)
            tick.intValue++
        }
    }
    LaunchedEffect(Unit) { CalendarRepository.changes(context).collect { tick.intValue++ } }
    val granted = remember(tick.intValue) { CalendarRepository.hasPermission(context) }
    val events by produceState<List<CalendarEvent>?>(null, tick.intValue, granted) {
        value = if (granted) CalendarRepository.upcoming(context, days = if (size == WidgetSize.LARGE) 7 else 3) else emptyList()
    }

    if (!granted) {
        WidgetPrompt(
            icon = WidgetGlyphs.Calendar,
            text = if (size == WidgetSize.SMALL) "Show your events" else "Allow calendar access to see what's up next.",
            action = "Allow",
            onAction = ask,
            size = size,
            modifier = modifier,
        )
        return
    }

    val today = LocalDate.now()
    val list = events ?: emptyList()
    WidgetFrame(modifier, onClick = { CalendarRepository.openCalendar(context) }) {
        when (size) {
            WidgetSize.SMALL, WidgetSize.BAR -> Column(Modifier.fillMaxSize()) {
                DateHeader(today, big = true)
                Spacer(Modifier.weight(1f))
                val next = list.firstOrNull()
                if (next == null) {
                    GlassText("No more events today", style = GlassType.Footnote, secondary = true, maxLines = 2)
                } else {
                    EventRow(next, compact = true)
                }
            }
            WidgetSize.MEDIUM -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(96.dp).fillMaxHeight()) {
                    DateHeader(today, big = true)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (list.isEmpty()) {
                        GlassText("No more events", style = GlassType.Callout, secondary = true)
                    } else {
                        list.take(3).forEach { EventRow(it, compact = false) }
                    }
                }
            }
            WidgetSize.LARGE -> Column(Modifier.fillMaxSize()) {
                DateHeader(today, big = false)
                Spacer(Modifier.height(10.dp))
                if (list.isEmpty()) {
                    GlassText("Nothing planned for the next 7 days", style = GlassType.Callout, secondary = true)
                } else {
                    var lastDay: LocalDate? = null
                    list.take(7).forEach { e ->
                        val d = Instant.ofEpochMilli(e.begin.coerceAtLeast(System.currentTimeMillis())).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (d != lastDay) {
                            lastDay = d
                            GlassText(dayTitle(d, today), style = GlassType.Caption, secondary = true, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
                        }
                        EventRow(e, compact = false)
                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(day: LocalDate, big: Boolean) {
    GlassText(day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(), style = GlassType.Caption, color = GlassColors.Red, maxLines = 1)
    if (big) {
        GlassText("${day.dayOfMonth}", style = GlassType.LargeTitle.copy(fontSize = 40.sp, fontWeight = FontWeight.Normal), maxLines = 1)
    } else {
        GlassText(day.format(DateTimeFormatter.ofPattern("d MMMM")), style = GlassType.Title, maxLines = 1)
    }
}

@Composable
private fun EventRow(e: CalendarEvent, compact: Boolean) {
    val context = LocalContext.current
    GlassSurface(
        Modifier.fillMaxWidth(),
        shape = GlassShape.rounded(10.dp),
        variant = GlassVariant.Clear,
        onClick = { CalendarRepository.openEvent(context, e) },
        role = androidx.compose.ui.semantics.Role.Button,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Calendar colour marker.
            Box(Modifier.width(4.dp).height(if (compact) 30.dp else 32.dp).clip(GlassShape.Capsule).background(Color(e.color or 0xFF000000.toInt())))
            Spacer(Modifier.width(7.dp))
            Column {
                GlassText(e.title, style = GlassType.Subhead, maxLines = if (compact) 2 else 1)
                GlassText(timeLabel(context, e), style = GlassType.Caption, secondary = true, maxLines = 1)
            }
        }
    }
}

private fun timeLabel(context: android.content.Context, e: CalendarEvent): String {
    if (e.allDay) return "All day"
    val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    val zone = ZoneId.systemDefault()
    val b = Instant.ofEpochMilli(e.begin).atZone(zone)
    val en = Instant.ofEpochMilli(e.end).atZone(zone)
    val now = System.currentTimeMillis()
    return if (e.begin <= now) "Now – ${en.format(fmt)}" else "${b.format(fmt)} – ${en.format(fmt)}"
}

private fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "TODAY"
    today.plusDays(1) -> "TOMORROW"
    else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMM")).uppercase()
}
