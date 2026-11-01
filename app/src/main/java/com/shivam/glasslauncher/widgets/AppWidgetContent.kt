package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
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
    val blend = ProviderGlass.enabled(inst)
    val backgroundOpacity = ProviderGlass.backgroundOpacity(inst)
    // u.sage's Claude usage gets Glass's own Liquid Tank; its widget stays underneath, invisible,
    // so it keeps receiving updates. Unrecognised layouts fall back to u.sage's widget.
    val claude = info.provider.packageName == ClaudeUsage.PACKAGE
    var usage by androidx.compose.runtime.remember(id) { androidx.compose.runtime.mutableStateOf<ClaudeUsage?>(null) }
    // The session countdown starts from when u.sage's text last changed, not from every redraw.
    var lastTexts by androidx.compose.runtime.remember(id) { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
    var readAt by androidx.compose.runtime.remember(id) { androidx.compose.runtime.mutableLongStateOf(0L) }
    val hostView = androidx.compose.runtime.remember(id) { arrayOfNulls<GlassAppWidgetHostView>(1) }
    androidx.compose.foundation.layout.Box(modifier) {
    WidgetFrame(Modifier.matchParentSize().alpha(if (usage != null) 0f else 1f), padding = 4.dp) {
        BoxWithConstraints(Modifier.fillMaxSize().clip(GlassShape.rounded(
            (com.shivam.glasslauncher.ui.glass.LocalGlassStyle.current.widgetCornerRadius - 4f).coerceAtLeast(0f).dp,
        ))) {
            val w = maxWidth.value
            val h = maxHeight.value
            key(id) {
                AndroidView(
                    factory = { ctx -> host.createView(ctx, id, info) },
                    // Blend only compatible background paint; preserve full-opacity provider content.
                    modifier = Modifier.fillMaxSize().alpha(if (blend) 1f else com.shivam.glasslauncher.ui.glass.LocalGlassStyle.current.appWidgetOpacity),
                    update = { view ->
                        (view as? GlassAppWidgetHostView)?.setGlassBlend(blend, backgroundOpacity)
                        if (claude) (view as? GlassAppWidgetHostView)?.let { v ->
                            hostView[0] = v
                            v.onTexts = { texts ->
                                if (texts != lastTexts) { lastTexts = texts; readAt = System.currentTimeMillis(); usage = ClaudeUsage.parse(texts) }
                            }
                        }
                        host.updateSize(view, w, h)
                    },
                )
            }
        }
    }
    usage?.let { u ->
        // Any tap only refreshes; it never opens u.sage.
        val refresh = if (env?.editMode == true) null else ({ hostView[0]?.clickRefresh(); Unit })
        ClaudeLiquidTank(u, Modifier.matchParentSize(), readAt = readAt, onRefresh = refresh, onClick = refresh)
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
