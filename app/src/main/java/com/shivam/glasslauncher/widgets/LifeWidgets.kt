package com.shivam.glasslauncher.widgets

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Process
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.ResumedLoop
import com.shivam.glasslauncher.ui.glass.currentAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** "3h 12m", "45m". */
internal fun durationLabel(ms: Long): String {
    val m = (ms / 60_000L).coerceAtLeast(0)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private fun Color.lighter(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

// ---------------------------------------------------------------- screen time

private data class AppUse(val label: String, val icon: ImageBitmap?, val ms: Long)
private data class ScreenTime(val totalMs: Long, val top: List<AppUse>)

private fun hasUsageAccess(context: Context): Boolean =
    context.getSystemService(AppOpsManager::class.java)
        ?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

/** UsageEvents types this cares about (android.app.usage.UsageEvents.Event). */
internal object UsageType {
    const val RESUMED = 1
    const val PAUSED = 2
    const val STOPPED = 23
    const val SHUTDOWN = 26
}

/** One usage event: [screen] is the activity class, so a close can be matched to what's in front. */
internal data class UsageEv(val pkg: String, val screen: String, val type: Int, val time: Long)

/**
 * Foreground time per package, clipped to [start, now], counted the way Digital Wellbeing does
 * (its event filter, read from its APK: resumed, paused, stopped, shutdown; no screen-off types):
 * one screen is in front at a time. An app's time runs from when one of its screens comes to the
 * front until that same screen pauses or stops, another app comes forward, or the phone shuts
 * down. A stop for one of the app's older screens doesn't end it: matching only by app cut
 * Instagram's day from 5 h to 2.5 h on the owner's phone (checked against Digital Wellbeing,
 * 2026-11-01). A first close of the day with nothing open means it was in front at [start].
 * Events must be in time order; same-time events keep their logged order.
 */
internal fun foregroundTime(events: List<UsageEv>, start: Long, now: Long): Map<String, Long> {
    val total = HashMap<String, Long>()
    var current: UsageEv? = null
    var since = start
    var any = false
    fun close(at: Long) {
        current?.let { total[it.pkg] = (total[it.pkg] ?: 0L) + (at.coerceIn(start, now) - since).coerceAtLeast(0L) }
        current = null
    }
    for (e in events) {
        when (e.type) {
            UsageType.RESUMED -> {
                if (current?.pkg != e.pkg) { close(e.time); since = e.time.coerceAtLeast(start) }
                current = e
            }
            UsageType.PAUSED, UsageType.STOPPED -> when {
                current?.pkg == e.pkg && current?.screen == e.screen -> close(e.time)
                current == null && !any -> { current = e; since = start; close(e.time) }
            }
            UsageType.SHUTDOWN -> close(e.time)
        }
        any = true
    }
    close(now)
    return total
}

/** Today's foreground time per launchable app (Glass itself left out), most used first. */
private suspend fun screenTimeToday(context: Context): ScreenTime = withContext(Dispatchers.IO) {
    val usm = context.getSystemService(UsageStatsManager::class.java)
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val pm = context.packageManager
    // Daily usage buckets can begin before midnight (they over-counted to 17 h); events are exact.
    val list = ArrayList<UsageEv>()
    val it = usm.queryEvents(start, now)
    val e = android.app.usage.UsageEvents.Event()
    while (it.hasNextEvent()) {
        it.getNextEvent(e)
        if (e.eventType == UsageType.RESUMED || e.eventType == UsageType.PAUSED || e.eventType == UsageType.STOPPED || e.eventType == UsageType.SHUTDOWN) {
            list += UsageEv(e.packageName.orEmpty(), e.className.orEmpty(), e.eventType, e.timeStamp)
        }
    }
    val byApp = foregroundTime(list, start, now)
        .filter { (pkg, ms) -> ms >= 60_000L && pkg != context.packageName && pm.getLaunchIntentForPackage(pkg) != null }
    val top = byApp.entries.sortedByDescending { it.value }.take(3).map { (pkg, ms) ->
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        AppUse(
            info?.loadLabel(pm)?.toString() ?: pkg,
            info?.let { runCatching { it.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull() },
            ms,
        )
    }
    ScreenTime(byApp.values.sum(), top)
}

/**
 * Screen time as app vials: today's top three apps as test tubes of the theme's liquid, filled
 * relative to the most used one, with the day's total.
 */
@Composable
fun ScreenTimeWidget(size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val granted = remember(tick) { hasUsageAccess(context) }
    if (!granted) {
        WidgetPrompt(WidgetGlyphs.Glance, "Allow usage access to see today's screen time.", action = "Allow", onAction = {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, size = size, modifier = modifier)
        return
    }
    var data by remember { mutableStateOf<ScreenTime?>(null) }
    // Every 5 minutes while Home is in front.
    ResumedLoop(tick) { while (true) { data = screenTimeToday(context); delay(5 * 60_000L) } }
    val accent = currentAccent()
    val d = data
    WidgetFrame(modifier, padding = 16.dp, onClick = {
        // Digital Wellbeing when it's installed; nothing to open otherwise.
        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing")?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }) {
        val small = size == WidgetSize.SMALL
        if (small) {
            // Small: one number, and an hourglass whose fallen sand is the share of today so far
            // spent on screen.
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val share = d?.let { (it.totalMs.toFloat() / (System.currentTimeMillis() - start).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    GlassText("SCREEN", style = GlassType.Caption.copy(fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = accent, maxLines = 1)
                    GlassText("TIME", style = GlassType.Caption.copy(fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = accent, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    val total = d?.totalMs ?: 0L
                    GlassText(if (d == null) "–" else "${total / 3_600_000L}h", style = GlassType.Title.copy(fontSize = 34.sp, fontWeight = FontWeight.Light), color = Color.White, maxLines = 1)
                    GlassText(if (d == null) "" else "${(total / 60_000L) % 60}m", style = GlassType.Title.copy(fontSize = 22.sp, fontWeight = FontWeight.Light), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                    GlassText("${(share * 100).toInt()}% of today", style = GlassType.Caption.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
                Hourglass(share, accent, Modifier.width(38.dp).height(78.dp))
            }
            return@WidgetFrame
        }
        Column(Modifier.fillMaxSize()) {
            GlassText("SCREEN TIME", style = GlassType.Caption.copy(fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = accent, maxLines = 1)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Spacer(Modifier.weight(1f))
                    GlassText(d?.let { durationLabel(it.totalMs) } ?: "–", style = GlassType.Title.copy(fontSize = if (small) 24.sp else 34.sp, fontWeight = FontWeight.Light), color = Color.White, maxLines = 1)
                    GlassText("today", style = GlassType.Caption, color = Color.White.copy(alpha = 0.7f))
                    if (!small) {
                        Spacer(Modifier.height(6.dp))
                        d?.top?.forEach { a ->
                            GlassText("${a.label}  ${durationLabel(a.ms)}", style = GlassType.Caption.copy(fontSize = 11.sp), color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.width(if (small) 6.dp else 12.dp))
                val top = d?.top.orEmpty()
                val max = top.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L
                Row(Modifier.fillMaxHeight().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 10.dp)) {
                    top.forEachIndexed { i, a -> Vial(a, a.ms.toFloat() / max * 0.85f, accent.lighter(i * 0.2f), if (small) 14.dp else 18.dp) }
                }
            }
        }
    }
}

/** Two glass bulbs: the top holds what's left of today, the bottom the share already on screen. */
@Composable
private fun Hourglass(spent: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        val neck = 3.dp.toPx()
        fun bulbs() = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f)
            quadraticTo(w, mid * 0.7f, w / 2 + neck, mid)
            quadraticTo(w, mid * 1.3f, w, h)
            lineTo(0f, h)
            quadraticTo(0f, mid * 1.3f, w / 2 - neck, mid)
            quadraticTo(0f, mid * 0.7f, 0f, 0f)
            close()
        }
        val glass = bulbs()
        drawPath(glass, Color.White.copy(alpha = 0.08f))
        clipPath(glass) {
            // Top bulb: the rest of the day, settled at the neck.
            val left = 1f - spent
            val topY = mid - (mid - 4.dp.toPx()) * left
            drawRect(color.copy(alpha = 0.85f), Offset(0f, topY), Size(w, mid - topY))
            // Bottom bulb: a mound of what's been spent.
            val pile = (h - mid) * spent
            val mound = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h); lineTo(0f, h - pile * 0.75f)
                quadraticTo(w / 2, h - pile * 1.3f, w, h - pile * 0.75f)
                lineTo(w, h); close()
            }
            drawPath(mound, color.copy(alpha = 0.6f))
            if (spent in 0.01f..0.99f) drawLine(color.copy(alpha = 0.9f), Offset(w / 2, mid), Offset(w / 2, h - pile), 1.5.dp.toPx())
        }
        drawPath(glass, Color.White.copy(alpha = 0.45f), style = Stroke(1.5.dp.toPx()))
        drawLine(Color.White.copy(alpha = 0.6f), Offset(-2.dp.toPx(), 0f), Offset(w + 2.dp.toPx(), 0f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(-2.dp.toPx(), h), Offset(w + 2.dp.toPx(), h), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun Vial(a: AppUse, fill: Float, color: Color, tube: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxHeight().width(tube + 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.weight(1f).width(tube)) {
            val r = CornerRadius(size.width / 2)
            drawRoundRect(Color.White.copy(alpha = 0.1f), cornerRadius = r)
            val h = size.height * fill.coerceIn(0.06f, 1f)
            drawRoundRect(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)), startY = size.height - h, endY = size.height), Offset(0f, size.height - h), Size(size.width, h), r)
            drawRoundRect(Color.White.copy(alpha = 0.4f), cornerRadius = r, style = Stroke(1.dp.toPx()))
        }
        Spacer(Modifier.height(4.dp))
        if (a.icon != null) Image(a.icon, a.label, Modifier.size(tube + 2.dp)) else Spacer(Modifier.size(tube + 2.dp))
    }
}

// ---------------------------------------------------------------- steps

/**
 * Today's steps from the step counter, which counts since boot: the first reading of each day is
 * kept as that day's start.
 * ponytail: steps taken before Glass first reads the sensor that day (or before a mid-day reboot)
 * are missed; a background sampler at midnight fixes that if it matters.
 */
internal object StepBaseline {
    fun stepsToday(context: Context, counter: Long): Long {
        val p = context.getSharedPreferences("steps", Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        var base = p.getLong("base", -1)
        if (p.getLong("day", -1) != today || base < 0 || counter < base) {
            base = counter
            p.edit().putLong("day", today).putLong("base", base).apply()
        }
        return counter - base
    }
}

private const val STEP_GOAL = 8000

/** Steps as a ring toward an 8,000-step goal, in the theme's colour, with a bright tip. */
@Composable
fun StepsWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val ask = rememberPermissionRequest(Manifest.permission.ACTIVITY_RECOGNITION) { tick++ }
    val granted = remember(tick) { context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    val sm = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember { sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    if (sensor == null) {
        WidgetPrompt(WidgetGlyphs.Compass, "This phone has no step counter", action = null, onAction = {}, size = WidgetSize.SMALL, modifier = modifier)
        return
    }
    if (!granted) {
        WidgetPrompt(WidgetGlyphs.Compass, "Allow activity access to count steps", action = "Allow", onAction = ask, size = WidgetSize.SMALL, modifier = modifier)
        return
    }
    var steps by remember { mutableLongStateOf(-1L) }
    DisposableEffect(sensor) {
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { steps = StepBaseline.stepsToday(context, e.values[0].toLong()) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm?.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    val accent = currentAccent()
    val p = (steps.coerceAtLeast(0).toFloat() / STEP_GOAL).coerceIn(0f, 1f)
    WidgetFrame(modifier, padding = 14.dp) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(Color.White.copy(alpha = 0.1f), r, c, style = Stroke(stroke))
            drawArc(Brush.sweepGradient(listOf(accent.copy(alpha = 0.5f), accent.lighter(0.25f), accent.copy(alpha = 0.5f)), c), -90f, 360f * p, false,
                Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            val a = (-90f + 360f * p) * PI.toFloat() / 180f
            val tip = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), tip, stroke * 1.3f), stroke * 1.3f, tip)
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            GlassText(if (steps < 0) "–" else "%,d".format(steps), style = GlassType.Title.copy(fontSize = 26.sp, fontWeight = FontWeight.Light), color = Color.White, maxLines = 1)
            GlassText("of %,d steps".format(STEP_GOAL), style = GlassType.Caption.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- alarm

/** How far the dawn sun has risen: 0 with 12 h or more to go, 1 when the alarm rings. */
internal fun dawnProgress(msLeft: Long): Float = (1f - msLeft / (12 * 3600_000f)).coerceIn(0f, 1f)

/**
 * The next alarm from the system (any clock app), with a sun rising over the horizon in the
 * theme's colour as it gets closer. Tap to open the alarm.
 */
@Composable
fun AlarmWidget(widgetSize: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(AlarmManager::class.java) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var next by remember { mutableStateOf(am?.nextAlarmClock) }
    ResumedLoop(Unit) { while (true) { now = System.currentTimeMillis(); next = am?.nextAlarmClock; delay(60_000L - now % 60_000L) } }
    val accent = currentAccent()
    val alarm = next
    WidgetFrame(modifier, padding = 0.dp, onClick = {
        runCatching { alarm?.showIntent?.send() ?: context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) {
        val left = alarm?.let { it.triggerTime - now } ?: Long.MAX_VALUE
        val rise = if (alarm == null) 0f else dawnProgress(left)
        // Edge to edge, clipped to the card: inside the padding the glow showed a square edge.
        Canvas(Modifier.fillMaxSize().clip(WidgetShape)) {
            val horizon = size.height * 0.8f
            val sunY = horizon + 18.dp.toPx() - rise * size.height * 0.45f
            val sun = Offset(size.width * (if (size.width > size.height * 1.5f) 0.78f else 0.5f), sunY)
            clipRect(bottom = horizon) {
                drawCircle(Brush.radialGradient(listOf(accent.lighter(0.4f).copy(alpha = 0.3f + 0.5f * rise), accent.copy(alpha = 0.15f), Color.Transparent), sun, size.height * 0.9f), size.height * 0.9f, sun)
                drawCircle(accent.lighter(0.55f), 16.dp.toPx(), sun)
            }
            drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            GlassText("ALARM", style = GlassType.Caption.copy(fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = accent)
            if (alarm == null) {
                Spacer(Modifier.height(6.dp))
                GlassText("No alarm set", style = GlassType.Subhead, color = Color.White.copy(alpha = 0.8f))
            } else {
                val at = Instant.ofEpochMilli(alarm.triggerTime).atZone(ZoneId.systemDefault())
                val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
                GlassText(at.format(fmt), style = GlassType.Title.copy(fontSize = 32.sp, fontWeight = FontWeight.Light), color = Color.White, maxLines = 1)
                val day = when (at.toLocalDate()) { LocalDate.now() -> "Today"; LocalDate.now().plusDays(1) -> "Tomorrow"; else -> at.format(DateTimeFormatter.ofPattern("EEE d MMM")) }
                GlassText("$day · in ${durationLabel(left)}", style = GlassType.Caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
        }
    }
}
