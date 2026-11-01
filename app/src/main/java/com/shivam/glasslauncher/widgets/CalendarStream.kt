package com.shivam.glasslauncher.widgets

import android.Manifest
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.ResumedLoop
import com.shivam.glasslauncher.ui.glass.currentAccent
import com.shivam.glasslauncher.ui.glass.glassPressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** LazyRow index of today; the stream scrolls about 270 years either way. */
private const val TODAY_INDEX = 100_000
private const val HOUR = 3600_000L
private const val DAY = 24 * HOUR
/** Hours visible across the widget. */
private const val VISIBLE_HOURS = 18f

/** Day-long blocks (true all-day events, or 12 am – 12 am "Out of office") are chips, not track blocks. */
internal fun isAllDay(e: CalendarEvent) = e.allDay || e.end - e.begin >= 23 * HOUR

/** The local days an all-day event covers (all-day times are stored in UTC). */
internal fun allDayDates(e: CalendarEvent, zone: ZoneId): ClosedRange<LocalDate> {
    val z = if (e.allDay) ZoneOffset.UTC else zone
    val start = Instant.ofEpochMilli(e.begin).atZone(z).toLocalDate()
    val end = Instant.ofEpochMilli(e.end - 1).atZone(z).toLocalDate()
    return start..maxOf(start, end)
}

/**
 * Calendar 1, the liquid day stream: a glass track of the hours, where time already gone has
 * poured in as the theme's liquid up to a glowing "now". Swipe sideways to any day, past or
 * future; events float above as capsules and the next one is spelled out below.
 */
@Composable
fun CalendarStreamWidget(widgetSize: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val tick = remember { mutableIntStateOf(0) }
    val ask = rememberPermissionRequest(Manifest.permission.READ_CALENDAR) { tick.intValue++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick.intValue++ }
    LaunchedEffect(Unit) { CalendarRepository.changes(context).collect { tick.intValue++ } }
    // The now line and liquid advance once a minute while Home is in front.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    ResumedLoop(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000L - now % 60_000L) } }
    val granted = remember(tick.intValue) { CalendarRepository.hasPermission(context) }
    if (!granted) {
        WidgetPrompt(WidgetGlyphs.Calendar, "Allow calendar access to see your days.", action = "Allow", onAction = ask, size = widgetSize, modifier = modifier)
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(now / DAY) { LocalDate.now(zone) }
    val accent = currentAccent()
    val fmt = remember { DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a") }
    val open: (CalendarEvent) -> Unit = { if (!CalendarRepository.openEvent(context, it)) env?.toast?.invoke("No calendar app is available") }

    WidgetFrame(modifier, padding = 14.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewPx = with(density) { maxWidth.toPx() }
            val areaH = maxHeight
            val dayPx = viewPx * 24f / VISIBLE_HOURS
            val dayDp = with(density) { dayPx.toDp() }
            // Now sits a fifth of the way in, so most of the view is what's coming.
            fun nowPosition(): Pair<Int, Int> {
                val off = (((now - today.atStartOfDay(zone).toInstant().toEpochMilli()).toFloat() / DAY) * dayPx - viewPx * 0.2f).toInt()
                return if (off >= 0) TODAY_INDEX to off else TODAY_INDEX - 1 to (off + dayPx.toInt())
            }
            val list = nowPosition().let { (i, o) -> rememberLazyListState(i, o) }
            val scope = rememberCoroutineScope()
            // The moment at the "now" mark (a fifth in) decides the title, chips and "next" card.
            val focus by remember(dayPx) {
                derivedStateOf {
                    val px = list.firstVisibleItemScrollOffset + viewPx * 0.2f
                    val idx = list.firstVisibleItemIndex + (px / dayPx).toInt()
                    val frac = (px % dayPx) / dayPx
                    today.plusDays((idx - TODAY_INDEX).toLong()).atStartOfDay(zone).toInstant().toEpochMilli() + (frac * DAY).toLong()
                }
            }
            val focusDay = Instant.ofEpochMilli(focus).atZone(zone).toLocalDate()
            // Events for the fortnight around what's on screen, reloaded as you swipe a week away.
            val week = Math.floorDiv(focusDay.toEpochDay(), 7L)
            val events by produceState(emptyList<CalendarEvent>(), week, tick.intValue) {
                val from = LocalDate.ofEpochDay(week * 7 - 7).atStartOfDay(zone).toInstant().toEpochMilli()
                value = CalendarRepository.between(context, from, from + 21 * DAY)
            }
            val timed = remember(events) { events.filterNot(::isAllDay) }
            val allDay = remember(events, focusDay) { events.filter { isAllDay(it) && focusDay in allDayDates(it, zone) } }
            val measurer = rememberTextMeasurer()

            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val title = when (focusDay) {
                        today -> "TODAY"
                        today.plusDays(1) -> "TOMORROW"
                        today.minusDays(1) -> "YESTERDAY"
                        else -> focusDay.format(DateTimeFormatter.ofPattern("EEEE")).uppercase()
                    }
                    GlassText(title, style = GlassType.Caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp), color = accent, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    GlassText(focusDay.format(DateTimeFormatter.ofPattern("EEE d MMM")), Modifier.weight(1f), style = GlassType.Caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    allDay.firstOrNull()?.let { e ->
                        Row(
                            Modifier.clip(GlassShape.Capsule).background(Color.White.copy(alpha = 0.12f)).widthIn(max = 150.dp)
                                .then(Modifier.padding(horizontal = 9.dp, vertical = 3.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).clip(GlassShape.Circle).background(accent))
                            Spacer(Modifier.width(5.dp))
                            GlassText(e.title + if (allDay.size > 1) " +${allDay.size - 1}" else "", style = GlassType.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // The track grows with the widget: a slim rail on Medium, a deep one on Large.
                val trackDp = (areaH * 0.17f).coerceIn(22.dp, 46.dp)
                Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 70.dp)) {
                    LazyRow(Modifier.fillMaxSize(), state = list) {
                        items(count = TODAY_INDEX * 2) { i ->
                            val day = today.plusDays((i - TODAY_INDEX).toLong())
                            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                            val mine = timed.filter { it.end > start && it.begin < start + DAY }
                            Box(Modifier.width(dayDp).fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val h = trackDp.toPx()
                                    val top = size.height / 2 - h / 2
                                    fun x(t: Long) = ((t - start).toFloat() / DAY).coerceIn(0f, 1f) * size.width
                                    drawRect(Color.White.copy(alpha = 0.07f), Offset(0f, top), Size(size.width, h))
                                    // Night hours a shade darker.
                                    drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, top), Size(x(start + 6 * HOUR), h))
                                    drawRect(Color.Black.copy(alpha = 0.18f), Offset(x(start + 19 * HOUR), top), Size(size.width - x(start + 19 * HOUR), h))
                                    // Time already gone, poured in as liquid.
                                    if (now > start) {
                                        val px = x(now)
                                        drawRect(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = if (now < start + DAY) 0.85f else 0.45f)), startX = if (now < start + DAY) 0f else -size.width, endX = px), Offset(0f, top), Size(px, h))
                                    }
                                    mine.forEach { e ->
                                        val a = x(e.begin)
                                        val w = maxOf(x(e.end) - a, 4.dp.toPx())
                                        drawRoundRect(Color.White.copy(alpha = if (e.end < now) 0.45f else 0.9f), Offset(a, top + 3.dp.toPx()), Size(w, h - 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                                    }
                                    // Midnight: a bright tick; hours every 3 h underneath.
                                    drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, top - 4.dp.toPx()), Offset(0f, top + h + 4.dp.toPx()), 1.dp.toPx())
                                    for (hr in 0 until 24 step 3) {
                                        val label = if (hr == 0) day.format(DateTimeFormatter.ofPattern("EEE d")) else "${if (hr % 12 == 0) 12 else hr % 12}${if (hr < 12) "a" else "p"}"
                                        val t = measurer.measure(label, TextStyle(fontSize = 10.sp, fontWeight = if (hr == 0) FontWeight.Bold else FontWeight.Medium, color = Color.White.copy(alpha = if (hr == 0) 0.9f else 0.55f)))
                                        drawText(t, topLeft = Offset(x(start + hr * HOUR) + if (hr == 0) 3.dp.toPx() else -t.size.width / 2f, top + h + 3.dp.toPx()))
                                    }
                                    if (now in start until start + DAY) {
                                        val px = x(now)
                                        drawLine(accent.copy(alpha = 0.45f), Offset(px, top - 8.dp.toPx()), Offset(px, top + h + 2.dp.toPx()), 7.dp.toPx())
                                        drawLine(Color.White, Offset(px, top - 8.dp.toPx()), Offset(px, top + h + 2.dp.toPx()), 2.dp.toPx())
                                    }
                                }
                                // Capsules float above their block: tap to open the event.
                                mine.filter { it.begin >= start }.forEach { e ->
                                    val xDp = with(density) { (((e.begin - start).toFloat() / DAY) * dayPx).toDp() }
                                    Row(
                                        Modifier.offset(x = xDp, y = -(trackDp / 2 + 20.dp)).widthIn(max = 150.dp).clip(GlassShape.Capsule)
                                            .background(Color.White.copy(alpha = 0.16f))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), GlassShape.Capsule)
                                            .glassPressable(onClick = { open(e) }, shape = GlassShape.Capsule)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        GlassText(e.title, style = GlassType.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                val next = timed.firstOrNull { it.end > maxOf(focus, now) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (next == null) {
                        GlassText("Nothing planned around here", Modifier.weight(1f), style = GlassType.Footnote, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    } else {
                        Column(Modifier.weight(1f).glassPressable(onClick = { open(next) }, shape = GlassShape.rounded(10.dp))) {
                            val d = Instant.ofEpochMilli(next.begin).atZone(zone)
                            val dayName = when (d.toLocalDate()) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM")) }
                            val time = if (next.begin <= now) "Now – ${Instant.ofEpochMilli(next.end).atZone(zone).format(fmt)}" else "${d.format(fmt)} – ${Instant.ofEpochMilli(next.end).atZone(zone).format(fmt)}"
                            GlassText("$dayName · $time", style = GlassType.Caption.copy(fontWeight = FontWeight.Bold), color = androidx.compose.ui.graphics.lerp(accent, Color.White, 0.55f), maxLines = 1)
                            GlassText(next.title, style = GlassType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                            if (areaH > 200.dp) next.location?.takeIf { it.isNotBlank() }?.let { GlassText(it, style = GlassType.Caption, color = Color.White.copy(alpha = 0.6f), maxLines = 1) }
                        }
                    }
                    // Back to now when you've wandered off.
                    if (kotlin.math.abs(focus - now) > 10 * HOUR) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(GlassShape.Capsule).background(accent.copy(alpha = 0.35f))
                                .glassPressable(onClick = { scope.launch { nowPosition().let { (i, o) -> list.animateScrollToItem(i, o) } } }, shape = GlassShape.Capsule)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { GlassText("Now", style = GlassType.Caption.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                    }
                }
            }
        }
    }
}
