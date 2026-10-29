package com.shivam.glasslauncher.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class PhoneBattery(
    val percent: Int,
    val charging: Boolean,
    val plugged: Boolean,
    /** Estimated minutes to full (charging) or to empty (discharging); null while estimating. */
    val estimateMinutes: Int?,
)

data class BluetoothBattery(val address: String, val name: String, val percent: Int)

/**
 * Phone battery (sticky ACTION_BATTERY_CHANGED) and the battery level of connected Bluetooth
 * devices. docs/CONSTRAINTS.md: Bluetooth levels need BLUETOOTH_CONNECT and many earbuds don't
 * report one; those rows are hidden.
 */
object BatteryRepository {

    /** Discharge samples (elapsedRealtime ms, percent) for the time-left estimate. */
    private val samples = ArrayDeque<Pair<Long, Int>>()

    fun phone(context: Context): Flow<PhoneBattery> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(parse(c, intent))
            }
        }
        val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        if (sticky != null) trySend(parse(context, sticky))
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun parse(context: Context, intent: Intent): PhoneBattery {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) level * 100 / scale else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val estimate = if (charging) {
            val bm = context.getSystemService(BatteryManager::class.java)
            val ms = try { bm?.computeChargeTimeRemaining() ?: -1L } catch (e: Exception) { -1L }
            samples.clear()
            if (ms > 0) (ms / 60_000L).toInt() else null
        } else {
            dischargeEstimate(percent)
        }
        return PhoneBattery(percent, charging, plugged, estimate)
    }

    /** Minutes left from the observed discharge rate (needs a 2% drop over at least 10 minutes). */
    private fun dischargeEstimate(percent: Int): Int? {
        val now = SystemClock.elapsedRealtime()
        if (samples.isEmpty() || samples.last().second != percent) samples.addLast(now to percent)
        while (samples.size > 2 && now - samples.first().first > 6 * 3600_000L) samples.removeFirst()
        val first = samples.firstOrNull() ?: return null
        val dropped = first.second - percent
        val elapsed = now - first.first
        if (dropped < 2 || elapsed < 10 * 60_000L) return null
        val perMinute = dropped / (elapsed / 60_000.0)
        return (percent / perMinute).toInt().coerceIn(1, 7 * 24 * 60)
    }

    fun hasBluetoothPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Connected Bluetooth devices that report a battery level; others are left out. */
    @SuppressLint("MissingPermission")
    fun bluetooth(context: Context): Flow<List<BluetoothBattery>> = callbackFlow {
        if (!hasBluetoothPermission(context)) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val proxies = HashMap<Int, BluetoothProfile>()
        val reported = HashMap<String, Int>()

        fun publish() {
            val connected = LinkedHashMap<String, BluetoothDevice>()
            proxies.values.forEach { p ->
                try { p.connectedDevices.forEach { connected[it.address] = it } } catch (_: Exception) { }
            }
            val list = connected.values.mapNotNull { d ->
                val level = reported[d.address] ?: batteryLevel(d)
                if (level < 0) null else BluetoothBattery(d.address, try { d.alias ?: d.name } catch (e: Exception) { null } ?: "Bluetooth device", level)
            }
            trySend(list)
        }

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                proxies[profile] = proxy
                publish()
            }
            override fun onServiceDisconnected(profile: Int) {
                proxies.remove(profile)
                publish()
            }
        }
        val profiles = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, PROFILE_LE_AUDIO)
        profiles.forEach { p -> try { adapter.getProfileProxy(context, listener, p) } catch (_: Exception) { } }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == ACTION_BATTERY_LEVEL_CHANGED) {
                    val device = androidx.core.content.IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    if (device != null) reported[device.address] = level
                }
                publish()
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        publish()
        awaitClose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
            proxies.forEach { (p, proxy) -> try { adapter.closeProfileProxy(p, proxy) } catch (_: Exception) { } }
        }
    }

    /** `BluetoothDevice.getBatteryLevel()` is not public API; -1 when unavailable. */
    private fun batteryLevel(device: BluetoothDevice): Int = try {
        (BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int) ?: -1
    } catch (e: Throwable) {
        -1
    }

    private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val PROFILE_LE_AUDIO = 22
}
