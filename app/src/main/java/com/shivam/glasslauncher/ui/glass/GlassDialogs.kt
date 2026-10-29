package com.shivam.glasslauncher.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Dims what is behind a modal glass element; tapping it dismisses. Not a surface itself. */
@Composable
fun GlassScrim(alpha: Float, onDismiss: (() -> Unit)?) {
    // Fully faded out: draw nothing and stop catching taps. Overlays that keep their last target
    // (rememberLast) stay composed after they close; a lingering invisible scrim froze every home touch.
    if (alpha <= 0.001f) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * alpha.coerceIn(0f, 1f)))
            .pointerInput(onDismiss) { detectTapGestures { onDismiss?.invoke() } },
    )
}

data class GlassAlertAction(val label: String, val prominent: Boolean = false, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * Confirmation dialog drawn in-window as a Regular glass panel (never a Material AlertDialog),
 * so it refracts the same backdrop as everything else. Grows out of [origin] when given.
 */
@Composable
fun GlassAlert(
    visible: Boolean,
    title: String,
    message: String?,
    actions: List<GlassAlertAction>,
    onDismiss: () -> Unit,
    origin: Rect? = null,
    icon: ImageVector? = null,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    if (visible) BackHandler(onBack = onDismiss)
    if (visible || progress > 0.01f) GlassScrim(progress, if (visible) onDismiss else null)
    GlassMorph(visible = visible, origin = origin, modifier = Modifier.fillMaxSize(), onProgress = { progress = it }) {
        GlassSurface(
            modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
            shape = GlassShape.Panel,
            variant = GlassVariant.Regular,
            role = null,
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (icon != null) {
                    GlassIcon(icon, size = 34.dp)
                    Spacer(Modifier.height(10.dp))
                }
                GlassText(title, style = GlassType.Headline, maxLines = 3)
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.Text(
                        message,
                        style = GlassType.Callout,
                        color = LocalGlassContentColor.current.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                if (actions.size <= 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        actions.forEach { a ->
                            GlassButton(a.label, onClick = a.onClick, modifier = Modifier.weight(1f), prominent = a.prominent, destructive = a.destructive)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.forEach { a ->
                            GlassButton(a.label, onClick = a.onClick, modifier = Modifier.fillMaxWidth(), prominent = a.prominent, destructive = a.destructive)
                        }
                    }
                }
            }
        }
    }
}

/** Holder for transient glass toasts (snackbar replacement). */
@Stable
class GlassToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var actionLabel by mutableStateOf<String?>(null)
        private set
    internal var action: (() -> Unit)? = null
    internal var serial by mutableStateOf(0)

    fun show(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        message = text
        this.actionLabel = actionLabel
        this.action = action
        serial++
    }

    internal fun clear() { message = null; actionLabel = null; action = null }
}

/** Shows [state]'s toast as a Clear glass capsule near the bottom of the screen. */
@Composable
fun GlassToastHost(state: GlassToastState, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(state.serial) {
        val m = state.message ?: return@LaunchedEffect
        text = m
        visible = true
        delay(if (state.actionLabel != null) 4000 else 2200)
        visible = false
        state.clear()
    }
    Box(modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 110.dp), contentAlignment = Alignment.BottomCenter) {
        GlassMorph(visible = visible, origin = null) {
            GlassSurface(shape = GlassShape.Capsule, variant = GlassVariant.Regular, role = null) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassText(text, style = GlassType.Callout, maxLines = 2)
                    val label = state.actionLabel
                    if (label != null) {
                        Spacer(Modifier.padding(start = 12.dp))
                        GlassButton(label, onClick = { state.action?.invoke(); visible = false }, prominent = true)
                    }
                }
            }
        }
    }
}
