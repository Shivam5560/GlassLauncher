package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassSegmented
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The widget picker: a glass sheet listing our widgets with live previews and size choices, then
 * every third-party Android widget, grouped by app, added through AppWidgetHost.
 */
@Composable
fun WidgetPickerSheet(widgets: WidgetUiState) {
    val target = com.shivam.glasslauncher.ui.home.rememberLast(widgets.picker)
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val providers by produceState<List<ProviderEntry>?>(null, widgets.picker != null) {
        if (widgets.picker != null) value = withContext(Dispatchers.IO) { GlassWidgetHost.get(context).providers() }
    }
    val ours = WidgetKind.entries.filter { it != WidgetKind.APP_WIDGET && it.isAvailable(context) }
        .filter { query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) }
    val theirs = (providers ?: emptyList()).filter {
        query.isBlank() || it.label.contains(query, true) || it.appLabel.contains(query, true)
    }.groupBy { it.appLabel + if (it.isWorkProfile) " (Work)" else "" }

    GlassSheet(
        visible = widgets.picker != null,
        title = if (target is PickerTarget.Today) "Add to Today" else "Widgets",
        onDismiss = { widgets.picker = null; query = "" },
    ) {
        Box(Modifier.padding(horizontal = 14.dp)) {
            GlassTextField(query, { query = it }, placeholder = "Search widgets", variant = GlassVariant.Clear)
        }
        Spacer(Modifier.height(10.dp))
        // Widgets in previews behave as if in edit mode (notes not editable, etc.).
        val env = LocalWidgetEnv.current
        val previewEnv = remember(env) {
            env?.let { WidgetEnv(it.openSpotlight, it.toast, it.toastAction, it.openSettings, {}, editMode = true, reducedMotion = it.reducedMotion) }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(ours, key = { it.name }) { kind ->
                CompositionLocalProvider(LocalWidgetEnv provides previewEnv) {
                    OurWidgetCard(kind) { size -> target?.let { widgets.addOurs(kind, size, it) } }
                }
            }
            if (theirs.isNotEmpty() || providers == null) {
                item("appsHeader") {
                    GlassText(
                        "APP WIDGETS",
                        Modifier.padding(start = 20.dp, top = 6.dp),
                        style = GlassType.Caption,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            if (providers == null) item("loading") { GlassListGroup { GlassRow("Loading app widgets…") } }
            theirs.forEach { (app, list) ->
                item("app:$app") {
                    GlassListGroup(title = app, footer = null) {
                        list.forEach { p ->
                            ProviderRow(p) { target?.let { widgets.addAppWidget(p, it) } }
                        }
                    }
                }
            }
            item("note") {
                GlassText(
                    "Some apps ask you to allow Glass to create widgets the first time, and some open a setup screen. Widgets that can resize get handles in edit mode.",
                    Modifier.padding(horizontal = 20.dp),
                    style = GlassType.Footnote,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/** One of our widgets: title, size picker, a live preview at that size and an Add button. */
@Composable
private fun OurWidgetCard(kind: WidgetKind, onAdd: (WidgetSize) -> Unit) {
    var size by remember { mutableStateOf(kind.sizes.first()) }
    val preview = remember(kind, size) {
        val cfg = if (kind == WidgetKind.CLOCK) mapOf(WidgetKeys.CLOCK_STYLE to ClockStyles.ANALOG) else emptyMap()
        WidgetInstance(PREVIEW_PREFIX + kind.name, kind, size, cfg)
    }
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(WidgetGlyphs.kind(kind), size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    GlassText(kind.title, style = GlassType.Headline)
                    GlassText(kind.description, style = GlassType.Footnote, secondary = true, maxLines = 2)
                }
            }
            if (kind.sizes.size > 1) {
                Spacer(Modifier.height(10.dp))
                GlassSegmented(kind.sizes.map { it.title }, kind.sizes.indexOf(size), { size = kind.sizes[it] })
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val small: Dp = minOf(170.dp, (maxWidth - 12.dp) / 2)
                val (w, h) = when (size) {
                    WidgetSize.SMALL -> small to small
                    WidgetSize.MEDIUM -> maxWidth to small
                    WidgetSize.LARGE -> maxWidth to (small * 2 + 12.dp)
                    WidgetSize.BAR -> maxWidth to 52.dp
                }
                Box(Modifier.size(w, h)) {
                    WidgetContent(preview, Modifier.fillMaxSize(), size)
                    // Blocks taps on the preview without stopping the list from scrolling.
                    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAdd(size) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GlassButton("Add Widget", onClick = { onAdd(size) }, prominent = true, icon = GlassIcons.Plus)
            }
        }
    }
}

@Composable
private fun ProviderRow(p: ProviderEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, p.info.provider) {
        value = withContext(Dispatchers.IO) {
            try {
                GlassWidgetHost.get(context).preview(p.info)?.let { d ->
                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 192
                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 192
                    val scale = minOf(1f, 360f / maxOf(w, h))
                    d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    val density = context.resources.displayMetrics.density
    val span = remember(p) { GlassWidgetHost.defaultSpan(p.info, 84f, 104f, density) }
    GlassRow(p.label, subtitle = "${span.first}×${span.second}" + if (p.info.configure != null) " · has setup" else "", onClick = onClick) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 72.dp, height = 52.dp))
        }
    }
}
