package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shivam.glasslauncher.ui.glass.GlassShape

/**
 * A third-party Android widget (AppWidgetHostView) wrapped in glass: a Regular glass frame with
 * the widget's own view clipped inside it. The widget is told its size in dp whenever it changes.
 */
@Composable
fun AppWidgetContent(inst: WidgetInstance, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val host = remember { GlassWidgetHost.get(context) }
    val id = inst.appWidgetId
    val info = remember(id) { if (id >= 0) host.info(id) else null }
    if (info == null) {
        WidgetPrompt(
            icon = WidgetGlyphs.Widgets,
            text = "This widget is no longer available",
            action = "Remove",
            onAction = { env?.editWidget?.invoke(inst.id) },
            size = WidgetSize.SMALL,
            modifier = modifier,
        )
        return
    }
    WidgetFrame(modifier, padding = 4.dp) {
        BoxWithConstraints(Modifier.fillMaxSize().clip(GlassShape.rounded(
            (com.shivam.glasslauncher.ui.glass.LocalGlassStyle.current.widgetCornerRadius - 4f).coerceAtLeast(0f).dp,
        ))) {
            val w = maxWidth.value
            val h = maxHeight.value
            key(id) {
                AndroidView(
                    factory = { ctx -> host.createView(ctx, id, info) },
                    // Third-party widgets paint an opaque card; fading it lets the liquid glass frame show through.
                    modifier = Modifier.fillMaxSize().alpha(com.shivam.glasslauncher.ui.glass.LocalGlassStyle.current.appWidgetOpacity),
                    update = { view -> host.updateSize(view, w, h) },
                )
            }
        }
    }
}

/** Keeps the widget host listening for updates while the launcher is started. */
@Composable
fun WidgetHostLifecycle() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val host = GlassWidgetHost.get(context)
        var listening = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!listening) { host.startListening(); listening = true }
                Lifecycle.Event.ON_STOP -> if (listening) { host.stopListening(); listening = false }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (listening) host.stopListening()
        }
    }
}
