package com.shivam.glasslauncher.ui.glass

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import java.util.function.Consumer

/**
 * Cross-window blur: the base layer of the glass. Blurs everything behind this activity's
 * transparent window (the wallpaper) in the compositor via [android.view.Window.setBackgroundBlurRadius].
 * Returns whether the compositor currently allows cross-window blur (it is off in battery saver,
 * on some GPUs and when the user disables it); callers fall back to the wallpaper copy then.
 */
@Composable
fun rememberCrossWindowBlur(targetRadiusDp: Float): Boolean {
    val activity = LocalContext.current as? Activity ?: return false
    val wm = activity.getSystemService(WindowManager::class.java)
    val enabled = remember { mutableStateOf(wm.isCrossWindowBlurEnabled) }
    DisposableEffect(wm) {
        val listener = Consumer<Boolean> { enabled.value = it }
        wm.addCrossWindowBlurEnabledListener(activity.mainExecutor, listener)
        onDispose { wm.removeCrossWindowBlurEnabledListener(listener) }
    }
    val density = LocalDensity.current
    val animated by animateFloatAsState(targetRadiusDp, spring(dampingRatio = 1f, stiffness = 260f), label = "windowBlur")
    LaunchedEffect(animated, enabled.value) {
        val px = if (enabled.value) with(density) { animated.coerceAtLeast(0f) * this.density }.toInt() else 0
        activity.window.setBackgroundBlurRadius(px)
    }
    return enabled.value
}
