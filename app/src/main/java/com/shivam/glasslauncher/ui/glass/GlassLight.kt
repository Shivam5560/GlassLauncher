package com.shivam.glasslauncher.ui.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.sqrt

/** Default light: from the top-left, like iOS. */
val DefaultLight = Offset(-0.5f, -0.866f)

/**
 * Light direction for the specular highlights that follows how the phone is tilted. Uses the
 * game rotation vector (gyro + accelerometer, no magnetometer) with a low-pass filter, falling
 * back to gravity / accelerometer. Listening only happens while the lifecycle is RESUMED, so it
 * stops when the screen turns off or another app is in front.
 */
@Composable
fun rememberGlassLight(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { mutableStateOf(DefaultLight) }
    DisposableEffect(enabled, lifecycle) {
        if (!enabled) {
            state.value = DefaultLight
            return@DisposableEffect onDispose { }
        }
        val tracker = TiltTracker(context) { state.value = it }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tracker.start()
                Lifecycle.Event.ON_PAUSE -> tracker.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tracker.start()
        onDispose {
            lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }
    return state
}

private class TiltTracker(context: Context, private val onLight: (Offset) -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rot = FloatArray(9)
    private var fx = 0f
    private var fy = 0f
    private var last = DefaultLight
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        sm?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        // "up" (world Z) expressed in device coordinates
        val ux: Float
        val uy: Float
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rot, event.values)
            ux = rot[6]; uy = rot[7]
        } else {
            val g = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
            if (g < 0.1f) return
            ux = event.values[0] / g; uy = event.values[1] / g
        }
        // low-pass filter
        fx += (ux - fx) * 0.12f
        fy += (uy - fy) * 0.12f
        // Tilting the right edge down moves the light to the right; tilting the top towards
        // the user moves it down. Screen y grows downwards, device y grows upwards.
        val lx = DefaultLight.x - fx * 1.1f
        val ly = DefaultLight.y + (fy - 0.7f) * 0.9f
        val len = sqrt(lx * lx + ly * ly).coerceAtLeast(0.001f)
        val next = Offset(lx / len, ly / len)
        if (abs(next.x - last.x) + abs(next.y - last.y) > 0.03f) {
            last = next
            onLight(next)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
