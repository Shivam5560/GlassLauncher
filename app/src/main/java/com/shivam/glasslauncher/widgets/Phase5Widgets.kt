package com.shivam.glasslauncher.widgets

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.ui.glass.Contrast
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassSwitch
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalFallbackLuminance
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import com.shivam.glasslauncher.ui.home.AppIconImage
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** True when [kind] can work on this phone (the compass needs a rotation-vector sensor). */
fun WidgetKind.isAvailable(context: Context): Boolean = when (this) {
    WidgetKind.COMPASS -> context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    else -> true
}

/**
 * "At a glance" home header: a large centred clock, the date, current weather and the next
 * calendar event, drawn straight on the wallpaper (no panel) with text coloured by contrast.
 * Tap the time for alarms, the date for the calendar.
 */
@Composable
fun GlanceWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L * (60 - now.second).coerceAtLeast(1))
        }
    }
    val weatherRepo = remember { WeatherRepository.get(context) }
    val weather by weatherRepo.state.collectAsState()
    val prefs by weatherRepo.prefs.weather.collectAsState()
    LaunchedEffect(Unit) { weatherRepo.refreshIfStale() }
    val calTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { CalendarRepository.changes(context).collect { calTick.intValue++ } }
    val next by produceState<CalendarEvent?>(null, calTick.intValue, now.hour) {
        value = if (CalendarRepository.hasPermission(context)) CalendarRepository.upcoming(context, days = 1, limit = 1).firstOrNull() else null
    }

    val lum = LocalFallbackLuminance.current
    val color = Color(Contrast.pickText(Contrast.gammaToLinear(lum)))
    val shadow = if (color == Color.White) Shadow(Color(0x66000000), Offset(0f, 2f), 10f) else null
    val is24 = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))

    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).uppercase()
    // iOS lock-screen style: a small date line, then tall glass numerals filling the widget.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val nextLine = next != null && maxHeight > 150.dp
        val clockBox = maxHeight - 26.dp - (if (nextLine) 22.dp else 0.dp)
        // Digits are ~0.72 of the font size tall; stretched 1.2x. Width keeps "12:59" inside the widget.
        val stretch = 1.2f
        val bySize = (clockBox.value / (0.72f * stretch) * 0.92f)
        val byWidth = maxWidth.value / 2.75f
        val fontSize = minOf(bySize, byWidth).coerceAtLeast(40f).sp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.height(26.dp).pointerInput(Unit) { detectTapGestures { CalendarRepository.openCalendar(context) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassText(date, style = GlassType.Footnote.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, shadow = shadow), color = color.copy(alpha = 0.9f), maxLines = 1)
                val data = weather.data
                if (data != null) {
                    val g = WeatherCodes.glyph(data.current.code, data.current.isDay)
                    Spacer(Modifier.width(10.dp))
                    GlassIcon(WidgetGlyphs.weather(g), size = 15.dp, tint = color.copy(alpha = 0.9f))
                    Spacer(Modifier.width(3.dp))
                    GlassText("${WeatherCodes.display(data.current.tempC, prefs?.fahrenheit == true)}°", style = GlassType.Footnote.copy(fontWeight = FontWeight.SemiBold, shadow = shadow), color = color.copy(alpha = 0.9f), maxLines = 1)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectTapGestures { openAlarms(context) } }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(
                    time,
                    modifier = Modifier.graphicsLayer { scaleY = stretch },
                    maxLines = 1,
                    style = GlassType.LargeTitle.copy(
                        // Same light glass numerals as Notification Centre.
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-fontSize.value * 0.02f).sp,
                        textAlign = TextAlign.Center,
                        fontFeatureSettings = "tnum",
                        // Translucent blue-white glass with a bright top (dark glass on light wallpapers).
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            if (color == Color.White) listOf(Color(0xF2FFFFFF), Color(0xB3DCE8FF), Color(0x80C8D8FF))
                            else listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.7f), color.copy(alpha = 0.5f)),
                        ),
                        shadow = Shadow(if (color == Color.White) Color(0x26000000) else Color(0x22FFFFFF), Offset(0f, 2f), 12f),
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }
            if (nextLine) next?.let { e ->
                GlassText(
                    "Next · ${e.title}",
                    Modifier.height(22.dp).pointerInput(e) { detectTapGestures { CalendarRepository.openEvent(context, e) } },
                    style = GlassType.Footnote.copy(shadow = shadow),
                    color = color.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun openAlarms(context: Context) {
    try {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { }
}

/**
 * Compass from the rotation-vector sensor (no permission). The widget is not offered when the
 * sensor is missing ([isAvailable]); if it disappears anyway a short note is shown.
 */
@Composable
fun CompassWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember { sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var azimuth by remember { mutableFloatStateOf(0f) }
    // Unwrapped heading, so the dial animates the short way round instead of spinning through 360°.
    var unwrapped by remember { mutableFloatStateOf(0f) }
    if (sensors != null && sensor != null) {
        // Registered only while Glass is resumed, so the rotation sensor is off in the background.
        LifecycleResumeEffect(sensor) {
            val rot = FloatArray(9)
            val orient = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rot, event.values)
                    SensorManager.getOrientation(rot, orient)
                    val deg = Math.toDegrees(orient[0].toDouble()).toFloat()
                    val a = (deg + 360f) % 360f
                    val delta = ((a - unwrapped) % 360f + 540f) % 360f - 180f
                    unwrapped += delta
                    azimuth = a
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
            }
            sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onPauseOrDispose { sensors.unregisterListener(listener) }
        }
    }
    val shown by animateFloatAsState(unwrapped, spring(dampingRatio = 0.8f, stiffness = 120f), label = "compass")

    WidgetFrame(modifier) {
        if (sensor == null) {
            GlassText("No compass sensor on this phone", Modifier.align(Alignment.Center), style = GlassType.Footnote, secondary = true)
            return@WidgetFrame
        }
        val content = LocalGlassContentColor.current
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val d = minOf(maxWidth, maxHeight)
                Canvas(Modifier.size(d)) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(content.copy(alpha = 0.25f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                    rotate(-shown, c) {
                        for (i in 0 until 36) {
                            val major = i % 9 == 0
                            val a = Math.toRadians(i * 10.0)
                            val o = Offset(c.x + (r * kotlin.math.sin(a)).toFloat(), c.y - (r * kotlin.math.cos(a)).toFloat())
                            val inner = r * if (major) 0.80f else 0.88f
                            val p = Offset(c.x + (inner * kotlin.math.sin(a)).toFloat(), c.y - (inner * kotlin.math.cos(a)).toFloat())
                            drawLine(content.copy(alpha = if (major) 0.9f else 0.4f), o, p, strokeWidth = (if (major) 2.dp else 1.dp).toPx(), cap = StrokeCap.Round)
                        }
                        // North needle in red, south in the content colour.
                        val n = Path().apply {
                            moveTo(c.x, c.y - r * 0.62f); lineTo(c.x - r * 0.1f, c.y); lineTo(c.x + r * 0.1f, c.y); close()
                        }
                        val s = Path().apply {
                            moveTo(c.x, c.y + r * 0.62f); lineTo(c.x - r * 0.1f, c.y); lineTo(c.x + r * 0.1f, c.y); close()
                        }
                        drawPath(n, GlassColors.Red)
                        drawPath(s, content.copy(alpha = 0.7f))
                    }
                    drawCircle(content, r * 0.05f, c)
                }
            }
            GlassText("${azimuth.roundToInt() % 360}° ${cardinal(azimuth)}", style = GlassType.Headline, maxLines = 1)
        }
    }
}

/** Eight-point compass label for a heading in degrees. */
fun cardinal(deg: Float): String {
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((deg % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % 8]
}

/**
 * Icon group: 4 (small, 2x2) or 16 (large, 4x4) chosen apps on one glass card. Tapping an app
 * opens it through the launcher (lock check included); apps are chosen in the widget's settings.
 */
@Composable
fun IconGroupWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val apps by GlassApp.from(context).apps.apps.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val cols = if (size == WidgetSize.LARGE) 4 else 2
    val chosen = inst.list(WidgetKeys.GROUP_APPS).mapNotNull { byKey[it] }.take(cols * cols)
    WidgetFrame(modifier, padding = 10.dp) {
        if (chosen.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GlassIcon(WidgetGlyphs.Widgets, size = 24.dp)
                Spacer(Modifier.height(6.dp))
                GlassText(
                    "Choose apps",
                    Modifier.pointerInput(inst.id) { detectTapGestures { env?.editWidget?.invoke(inst.id) } },
                    style = GlassType.Callout,
                    maxLines = 1,
                )
            }
            return@WidgetFrame
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cell = minOf(maxWidth / cols, maxHeight / cols)
            val icon = cell * 0.74f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until cols) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0 until cols) {
                            val e = chosen.getOrNull(r * cols + c)
                            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                if (e != null) GroupIcon(e, icon, enabled = env?.editMode != true, launch = env?.launchApp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupIcon(e: AppEntry, size: androidx.compose.ui.unit.Dp, enabled: Boolean, launch: ((AppEntry, android.graphics.Rect?) -> Unit)?) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    AppIconImage(
        e,
        size,
        Modifier
            .onGloballyPositioned { val b = it.boundsInWindow(); bounds = android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()) }
            .pointerInput(e.key, enabled) {
                if (enabled) detectTapGestures {
                    if (launch != null) launch(e, bounds) else GlassApp.from(context).actions.let { a ->
                        (context as? android.app.Activity)?.let { act -> a.launch(act, e, bounds) }
                    }
                }
            },
    )
}

/** Icon group settings: pick up to 4 / 16 apps. */
@Composable
fun IconGroupSettings(widgets: WidgetUiState, inst: WidgetInstance) {
    val context = LocalContext.current
    val apps by GlassApp.from(context).apps.apps.collectAsState()
    val hidden = GlassApp.from(context).settings.state.collectAsState().value?.hidden ?: emptySet()
    val max = if (inst.size == WidgetSize.LARGE) 16 else 4
    val chosen = inst.list(WidgetKeys.GROUP_APPS)
    var query by remember { mutableStateOf("") }
    val list = apps.filter { it.key !in hidden && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
    Column {
        GlassListGroup(title = "Apps (${chosen.size}/$max)", footer = "Large groups hold 16 apps, small ones 4. Order follows the order you pick them in.") {
            Box(Modifier.padding(10.dp)) {
                GlassTextField(query, { query = it }, placeholder = "Search apps", variant = GlassVariant.Clear)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassListGroup {
            list.take(80).forEach { e ->
                val on = e.key in chosen
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(e, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    GlassText(e.label, Modifier.weight(1f), style = GlassType.Body, maxLines = 1)
                    GlassSwitch(on, { v ->
                        val next = if (v) (chosen + e.key).distinct().take(max) else chosen - e.key
                        widgets.repo.setConfig(inst.id, WidgetKeys.GROUP_APPS, next.joinToString(","))
                    })
                }
            }
            if (list.size > 80) GlassRow("Search to find more apps")
        }
    }
}
