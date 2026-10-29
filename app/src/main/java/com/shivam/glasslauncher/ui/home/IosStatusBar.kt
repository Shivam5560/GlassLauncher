@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.shivam.glasslauncher.ui.home

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.widgets.BatteryRepository
import com.shivam.glasslauncher.widgets.PhoneBattery
import kotlinx.coroutines.delay

/**
 * iOS-style status bar drawn by Glass while immersive mode hides the system one on home: the time
 * centred in the space left of the island, signal, Wi-Fi and battery centred in the space right of it.
 */
@Composable
fun IosStatusBar(color: Color, islandWidth: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val height = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var signal by remember { mutableIntStateOf(0) }
    var wifi by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        // ponytail: 15 s poll for signal/Wi-Fi; switch to callbacks if it ever looks laggy
        while (true) {
            now = System.currentTimeMillis()
            signal = try { tm?.signalStrength?.level ?: 0 } catch (_: Exception) { 0 }
            wifi = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            delay(minOf(15_000L, 60_000 - now % 60_000))
        }
    }
    val battery by remember { BatteryRepository.phone(context.applicationContext) }.collectAsState(initial = null)
    val time = remember(now) { DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now).toString() }

    Row(modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            GlassText(time, style = GlassType.Headline.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
        }
        Spacer(Modifier.width(islandWidth))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SignalBars(signal, color)
            Spacer(Modifier.width(6.dp))
            if (wifi) {
                WifiGlyph(color)
                Spacer(Modifier.width(6.dp))
            }
            BatteryGlyph(battery, color)
        }
    }
}

@Composable
private fun SignalBars(level: Int, color: Color) {
    Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
        val w = size.width / 4.6f
        for (i in 0 until 4) {
            val h = size.height * (0.4f + 0.2f * i)
            drawRoundRect(
                if (i < level.coerceIn(1, 4)) color else color.copy(alpha = 0.3f),
                Offset(i * w * 1.2f, size.height - h), Size(w, h), CornerRadius(w / 3f),
            )
        }
    }
}

@Composable
private fun WifiGlyph(color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        val stroke = size.height * 0.17f
        val center = Offset(size.width / 2f, size.height * 0.98f)
        for (k in 1..2) {
            val r = size.height * (0.38f + 0.33f * k) - stroke / 2f
            drawArc(
                color, startAngle = -135f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // Inner wedge as a filled sector.
        val r0 = size.height * 0.38f
        drawArc(color, -135f, 90f, useCenter = true, topLeft = Offset(center.x - r0, center.y - r0), size = Size(r0 * 2, r0 * 2))
    }
}

@Composable
private fun BatteryGlyph(b: PhoneBattery?, color: Color) {
    val percent = b?.percent ?: 100
    val fill = when {
        b?.charging == true -> Color(0xFF34C759)
        percent <= 20 -> Color(0xFFFF3B30)
        else -> color
    }
    Canvas(Modifier.size(width = 27.dp, height = 13.dp)) {
        val nub = 2.dp.toPx()
        val body = Size(size.width - nub - 1.dp.toPx(), size.height)
        val r = CornerRadius(body.height * 0.3f)
        drawRoundRect(color.copy(alpha = 0.35f), Offset.Zero, body, r, style = Stroke(1.dp.toPx()))
        val inset = 2.dp.toPx()
        val inner = Size((body.width - inset * 2) * (percent / 100f).coerceIn(0.06f, 1f), body.height - inset * 2)
        drawRoundRect(fill, Offset(inset, inset), inner, CornerRadius(r.x * 0.6f))
        drawRoundRect(
            color.copy(alpha = 0.4f), Offset(body.width + 1.dp.toPx(), body.height * 0.32f),
            Size(nub, body.height * 0.36f), CornerRadius(nub / 2f),
        )
    }
}
