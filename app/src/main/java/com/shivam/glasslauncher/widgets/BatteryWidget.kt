package com.shivam.glasslauncher.widgets

import android.Manifest
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor

private data class BatteryRow(val key: String, val name: String, val icon: ImageVector, val percent: Int, val charging: Boolean)

/**
 * Phone battery with charging state and a time estimate, plus connected Bluetooth devices that
 * report a level (rows for devices without one are hidden, docs/CONSTRAINTS.md).
 */
@Composable
fun BatteryWidget(size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val btTick = remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { btTick.intValue++ }
    val askBluetooth = rememberPermissionRequest(Manifest.permission.BLUETOOTH_CONNECT) { btTick.intValue++ }
    val phoneFlow = remember { BatteryRepository.phone(context) }
    val phone by phoneFlow.collectAsState(initial = null)
    val btGranted = remember(btTick.intValue) { BatteryRepository.hasBluetoothPermission(context) }
    val btFlow = remember(btGranted) { BatteryRepository.bluetooth(context) }
    val devices by btFlow.collectAsState(initial = emptyList())

    val rows = buildList {
        phone?.let { add(BatteryRow("phone", "Phone", WidgetGlyphs.Phone, it.percent, it.charging)) }
        devices.forEach { add(BatteryRow(it.address, it.name, WidgetGlyphs.Earbuds, it.percent, false)) }
    }

    WidgetFrame(modifier) {
        if (size == WidgetSize.SMALL) {
            // Up to four rings in a 2x2 grid, like iOS.
            val shown = rows.take(4)
            if (shown.size == 1) {
                val device = shown.first()
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val diameter = minOf(maxWidth, (maxHeight - 28.dp).coerceAtLeast(40.dp)) * 0.85f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(device, diameter, prominent = true)
                        Spacer(Modifier.height(8.dp))
                        GlassText(if (device.charging) "Charging" else device.name,
                            style = GlassType.Subhead, secondary = true, maxLines = 1)
                    }
                }
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                shown.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        pair.forEach { r ->
                            BoxWithConstraints(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Ring(r, minOf(maxWidth, maxHeight) * 0.92f)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (shown.size <= 2) Spacer(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                rows.take(3).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIcon(r.icon, size = 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassText(r.name, style = GlassType.Subhead, maxLines = 1, modifier = Modifier.weight(1f))
                                if (r.charging) GlassIcon(WidgetGlyphs.Bolt, size = 13.dp, tint = GlassColors.Green)
                                GlassText(" ${r.percent}%", style = GlassType.Subhead, maxLines = 1)
                            }
                            Spacer(Modifier.height(4.dp))
                            Bar(r)
                            if (r.key == "phone") {
                                phone?.let { p -> GlassText(estimateLabel(p), style = GlassType.Caption, secondary = true, maxLines = 1) }
                            }
                        }
                    }
                }
                if (!btGranted) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        GlassButton("Show Bluetooth devices", onClick = askBluetooth)
                    }
                } else if (devices.isEmpty()) {
                    GlassText("No connected devices report a battery level", style = GlassType.Caption, secondary = true, maxLines = 1)
                }
            }
        }
    }
}

private fun estimateLabel(p: PhoneBattery): String {
    val m = p.estimateMinutes
    return when {
        p.charging && p.percent >= 100 -> "Charged"
        p.charging && m != null -> "Full in ${hm(m)}"
        p.charging -> "Charging"
        m != null -> "About ${hm(m)} left"
        else -> "Estimating time left…"
    }
}

private fun hm(minutes: Int): String = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

private fun levelColor(r: BatteryRow): Color = when {
    r.charging -> GlassColors.Green
    r.percent <= 20 -> GlassColors.Red
    else -> GlassColors.Green
}

@Composable
private fun Ring(r: BatteryRow, size: Dp, prominent: Boolean = false) {
    val track = LocalGlassContentColor.current.copy(alpha = 0.12f)
    val color = levelColor(r)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.065f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * r.percent / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassIcon(if (r.charging) WidgetGlyphs.Bolt else r.icon, size = size * if (prominent) 0.20f else 0.3f, tint = if (r.charging) GlassColors.Green else LocalGlassContentColor.current)
            GlassText("${r.percent}%", style = if (prominent) GlassType.Title.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum") else GlassType.Caption, maxLines = 1)
        }
    }
}

@Composable
private fun Bar(r: BatteryRow) {
    val track = LocalGlassContentColor.current.copy(alpha = 0.12f)
    val color = levelColor(r)
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val y = size.height / 2
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, y), Offset(size.width * r.percent / 100f, y), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}
