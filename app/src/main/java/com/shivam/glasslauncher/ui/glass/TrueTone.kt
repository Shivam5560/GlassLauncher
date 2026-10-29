package com.shivam.glasslauncher.ui.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.ln

/**
 * True Tone for Glass's own windows. Phones have an ambient light sensor but no colour sensor, so this
 * is an approximation: the dimmer the room (warm indoor light), the stronger a soft warm veil over
 * Glass's screens; in daylight there is none. Nothing outside Glass is changed, and the sensor only
 * runs while it is switched on and the screen is on.
 */
object TrueTone {
    /** Veil opacity 0..[MAX_ALPHA], already combined with the user's strength. */
    val alpha = MutableStateFlow(0f)

    private const val MAX_ALPHA = 0.14f
    private val WARM = Color(0xFFFFB05C)

    private var enabled = false
    private var strength = 0.6f
    private var warmth = 0f
    private var registered = false
    private var screenOn = true
    private var appContext: Context? = null
    private var receiver: BroadcastReceiver? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // lux -> warmth: dark room 1, ordinary indoor light about 0.55, bright light 0.
            val lux = event.values[0].coerceAtLeast(0f)
            val target = when {
                lux <= 5f -> 1f
                lux <= 150f -> 1f - 0.45f * (ln(lux / 5f) / ln(30f))
                lux <= 1500f -> 0.55f * (1f - ln(lux / 150f) / ln(10f))
                else -> 0f
            }.coerceIn(0f, 1f)
            warmth += (target - warmth) * 0.25f
            publish()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun configure(context: Context, on: Boolean, strength: Float) {
        appContext = context.applicationContext
        this.strength = strength.coerceIn(0f, 1f)
        if (on != enabled) {
            enabled = on
            if (on) listenForScreen() else stopListeningForScreen()
        }
        sync()
        publish()
    }

    private fun publish() {
        val next = if (enabled) warmth * strength * MAX_ALPHA else 0f
        if (abs(next - alpha.value) > 0.002f || (next == 0f && alpha.value != 0f)) alpha.value = next
    }

    private fun sync() {
        val context = appContext ?: return
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        val want = enabled && screenOn
        if (want && !registered) {
            val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
            // Batched: a veil that follows the room needs no more than a few updates a second.
            registered = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, 2_000_000)
        } else if (!want && registered) {
            sm.unregisterListener(listener)
            registered = false
        }
    }

    private fun listenForScreen() {
        val context = appContext ?: return
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        screenOn = pm?.isInteractive != false
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                screenOn = i.action == Intent.ACTION_SCREEN_ON
                sync()
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
    }

    private fun stopListeningForScreen() {
        receiver?.let { r -> appContext?.unregisterReceiver(r) }
        receiver = null
    }
}

/** Wraps [content] and tints it with the True Tone veil; layout-transparent. */
@Composable
internal fun TrueToneVeil(content: @Composable () -> Unit) {
    val alpha: State<Float> = TrueTone.alpha.collectAsState()
    Box(
        Modifier.drawWithContent {
            drawContent()
            val a = alpha.value
            if (a > 0f) drawRect(Color(0xFFFFB05C).copy(alpha = a))
        },
        propagateMinConstraints = true,
    ) { content() }
}
