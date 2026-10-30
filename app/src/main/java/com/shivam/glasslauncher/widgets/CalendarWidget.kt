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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.shivam.glasslauncher.ui.home.ownsVerticalScroll
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
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.glassPressable
import com.shivam.glasslauncher.ui.glass.ResumedLoop
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Upcoming events grouped under their own day (Today, Tomorrow, …) with coloured calendar markers. READ_CALENDAR is asked the
 * first time the user taps the prompt; tapping an event opens it in the calendar app.
 */
@Composable
fun CalendarWidget(size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val tick = remember { mutableIntStateOf(0) }
    val ask = rememberPermissionRequest(Manifest.permission.READ_CALENDAR) { tick.intValue++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick.intValue++ }
    ResumedLoop(Unit) {
        // Drop finished events and roll over at midnight (only while resumed; ON_RESUME ticks too).
        while (true) {
            delay(60_000L)
            tick.intValue++
        }
    }
    LaunchedEffect(Unit) { CalendarRepository.changes(context).collect { tick.intValue++ } }
    val granted = remember(tick.intValue) { CalendarRepository.hasPermission(context) }
    val events by produceState<List<CalendarEvent>?>(null, tick.intValue, granted) {
        value = if (granted) CalendarRepository.upcoming(context, days = 7, limit = 30) else emptyList()
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
    WidgetFrame(modifier, onClick = {
        if (!CalendarRepository.openCalendar(context)) env?.toast?.invoke("No calendar app is available")
    }) {
        when (size) {
            WidgetSize.SMALL, WidgetSize.BAR -> Column(Modifier.fillMaxSize()) {
                val next = list.firstOrNull()
                // The day of the next event, not today's date: "Tomorrow" must never read as today.
                GlassText(if (next == null) "TODAY" else dayTitle(eventDay(next), today), style = GlassType.Caption, color = GlassColors.Red, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (next == null) {
                    GlassText("No upcoming events", style = GlassType.Footnote, secondary = true, maxLines = 2)
                } else {
                    EventRow(next, compact = true)
                }
            }
            WidgetSize.MEDIUM, WidgetSize.LARGE -> {
                if (list.isEmpty()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        GlassText("UP NEXT", style = GlassType.Caption, color = GlassColors.Red, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        GlassText("Nothing planned for the next 7 days", style = GlassType.Callout, secondary = true)
                    }
                } else {
                    // Grouped by day and scrollable: each event sits under its own day, so later days never read as today.
                    val days = list.groupBy { eventDay(it) }
                    LazyColumn(Modifier.fillMaxSize().ownsVerticalScroll(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        days.forEach { (day, items) ->
                            item(key = "d$day") {
                                GlassText(
                                    dayTitle(day, today),
                                    style = GlassType.Caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (day == today) GlassColors.Red else Color.White.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = if (day == days.keys.first()) 0.dp else 8.dp, bottom = 2.dp),
                                )
                            }
                            items(items, key = { "e${it.eventId}_${it.begin}" }) { EventRow(it, compact = false) }
                        }
                    }
                }
            }
        }
    }
}

/** The day an event belongs to in the list: today for ones already running. */
private fun eventDay(e: CalendarEvent): LocalDate =
    Instant.ofEpochMilli(e.begin.coerceAtLeast(System.currentTimeMillis())).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun EventRow(e: CalendarEvent, compact: Boolean) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    // The widget is the glass surface. Event rows share it instead of adding a lens per row.
    Row(
        Modifier.fillMaxWidth()
            .glassPressable(onClick = {
                if (!CalendarRepository.openEvent(context, e)) env?.toast?.invoke("No calendar app is available")
            }, shape = GlassShape.rounded(10.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(if (compact) 32.dp else 34.dp)
            .clip(GlassShape.Capsule).background(Color(e.color or 0xFF000000.toInt())))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            GlassText(e.title, style = GlassType.Subhead, maxLines = if (compact) 2 else 1)
            Spacer(Modifier.height(2.dp))
            GlassText(timeLabel(context, e), style = GlassType.Caption, secondary = true, maxLines = 1)
        }
    }
}

private fun timeLabel(context: android.content.Context, e: CalendarEvent): String {
    // Full-day blocks (e.g. "Out of office" stored as 12:00 am – 12:00 am) read as all day.
    if (e.allDay || e.end - e.begin >= 23 * 3600_000L) return "All day"
    val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    val zone = ZoneId.systemDefault()
    val b = Instant.ofEpochMilli(e.begin).atZone(zone)
    val en = Instant.ofEpochMilli(e.end).atZone(zone)
    val now = System.currentTimeMillis()
    return if (e.begin <= now) "Now – ${en.format(fmt)}" else "${b.format(fmt)} – ${en.format(fmt)}"
}

private fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "TODAY · " + d.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
    today.plusDays(1) -> "TOMORROW · " + d.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
    else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMM")).uppercase()
}
