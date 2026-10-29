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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
 * iOS-style status bar drawn by Glass while immersive mode hides the system one on home: the time and
 * notification icons left of the island; speed, 5G/4G, signal, Wi-Fi and battery (with %) right of it.
 */
@Composable
fun IosStatusBar(color: Color, islandWidth: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val height = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var signal by remember { mutableIntStateOf(0) }
    var wifi by remember { mutableStateOf(false) }
    var netType by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableLongStateOf(0L) }
    // 5G / 4G from the display info (READ_PHONE_STATE; label hidden without it).
    androidx.compose.runtime.DisposableEffect(Unit) {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val granted = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (tm == null || !granted) return@DisposableEffect onDispose { }
        val cb = object : android.telephony.TelephonyCallback(), android.telephony.TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: android.telephony.TelephonyDisplayInfo) { netType = networkLabel(info) }
        }
        try { tm.registerTelephonyCallback(context.mainExecutor, cb) } catch (_: SecurityException) { }
        onDispose { tm.unregisterTelephonyCallback(cb) }
    }
    // Network speed (all traffic, bytes/s), refreshed every 2 s like HyperOS's indicator.
    LaunchedEffect(Unit) {
        var last = android.net.TrafficStats.getTotalRxBytes() + android.net.TrafficStats.getTotalTxBytes()
        while (true) {
            delay(2_000)
            val total = android.net.TrafficStats.getTotalRxBytes() + android.net.TrafficStats.getTotalTxBytes()
            speed = ((total - last) / 2).coerceAtLeast(0)
            last = total
        }
    }
    val notifications by com.shivam.glasslauncher.overlay.notifications.NotificationHub.entries.collectAsState()
    val notifKeys = remember(notifications) {
        notifications.filter { !it.isOngoing && !it.isMedia && !it.isGroupSummary }
            .sortedByDescending { it.postTime }.distinctBy { it.packageName }.map { it.key }
    }
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
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            GlassText(time, style = GlassType.Headline.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
            notifKeys.take(3).forEach { key ->
                Spacer(Modifier.width(5.dp))
                NotificationGlyph(key, color)
            }
            if (notifKeys.size > 3) {
                Spacer(Modifier.width(4.dp))
                Canvas(Modifier.size(4.dp)) { drawCircle(color) }
            }
        }
        Spacer(Modifier.width(islandWidth))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SpeedText(speed, color)
            Spacer(Modifier.width(5.dp))
            if (!wifi && netType != null) {
                GlassText(netType!!, style = GlassType.Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = color, maxLines = 1)
                Spacer(Modifier.width(3.dp))
            }
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

private fun networkLabel(info: android.telephony.TelephonyDisplayInfo): String? {
    val o = info.overrideNetworkType
    if (o == android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
        o == android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
    ) return "5G"
    return when (info.networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> if (o == android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA) "4G+" else "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "E"
        else -> null
    }
}

/** HyperOS-style two-line speed: value over unit. */
@Composable
private fun SpeedText(bytesPerSecond: Long, color: Color) {
    val (value, unit) = when {
        bytesPerSecond >= 1_048_576 -> "%.1f".format(bytesPerSecond / 1_048_576f) to "MB/s"
        else -> (if (bytesPerSecond >= 102_400) "%.0f" else "%.2f").format(bytesPerSecond / 1024f) to "KB/s"
    }
    val style = GlassType.Caption.copy(fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold)
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassText(value, style = style, color = color, maxLines = 1)
        GlassText(unit, style = style, color = color, maxLines = 1)
    }
}

/** The app's own small notification icon, tinted like the rest of the bar. */
@Composable
private fun NotificationGlyph(key: String, color: Color) {
    val context = LocalContext.current
    val bmp = remember(key) {
        try {
            com.shivam.glasslauncher.overlay.notifications.NotificationHub.notification(key)?.notification?.smallIcon
                ?.loadDrawable(context)?.toBitmap(48, 48)
                // Silhouettes only: a mostly opaque icon (full-colour or a filled tile) tints to a solid square.
                ?.takeIf { bmp ->
                    val px = IntArray(48 * 48).also { bmp.getPixels(it, 0, 48, 0, 0, 48, 48) }
                    px.count { android.graphics.Color.alpha(it) > 128 } < px.size * 0.7f
                }
                ?.asImageBitmap()
        } catch (_: Exception) { null }
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bmp, null, Modifier.size(14.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
        )
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
    // iOS 26: the percentage sits inside the battery.
    Box(contentAlignment = Alignment.CenterStart) {
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
    Box(Modifier.size(width = 24.dp, height = 13.dp), contentAlignment = Alignment.Center) {
        // Dark digits over a mostly full light fill (and vice versa), like iOS's cut-out number.
        val digits = if (percent > 45) (if (color == Color.White) Color.Black else Color.White) else color
        GlassText("$percent", style = GlassType.Caption.copy(fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold), color = digits, maxLines = 1)
    }
    }
}
