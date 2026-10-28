package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.GRID_COLUMNS
import com.shivam.glasslauncher.data.GRID_ROWS
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.ui.glass.GlassAlert
import com.shivam.glasslauncher.ui.glass.GlassAlertAction
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor

/**
 * Page manager: add, remove and reorder home pages and pick the default page (the one Home
 * returns to). Each page shows a miniature of its icons and widgets.
 */
@Composable
fun PageManagerSheet(widgets: WidgetUiState, layout: HomeLayout) {
    var confirmRemove by remember { mutableStateOf<Int?>(null) }
    GlassSheet(visible = widgets.pageManager, title = "Pages", onDismiss = { widgets.pageManager = false }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("hint") {
                GlassText(
                    "The starred page is where Home takes you. Removing a page keeps its apps in the App Library.",
                    Modifier.padding(horizontal = 8.dp),
                    style = GlassType.Footnote,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            layout.pages.indices.forEach { p ->
                item("page$p") {
                    PageRow(
                        page = p,
                        layout = layout,
                        onDefault = { widgets.setDefaultPage(p) },
                        onUp = if (p > 0) ({ widgets.movePage(p, p - 1) }) else null,
                        onDown = if (p < layout.pages.size - 1) ({ widgets.movePage(p, p + 1) }) else null,
                        onRemove = if (layout.pages.size > 1) ({
                            val empty = layout.pages[p].all { it == null } && layout.widgetsOn(p).isEmpty()
                            if (empty) widgets.removePage(p) else confirmRemove = p
                        }) else null,
                    )
                }
            }
            item("add") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton("Add Page", onClick = { widgets.addPage() }, prominent = true, icon = GlassIcons.Plus)
                }
            }
        }
    }
    val pending = confirmRemove
    GlassAlert(
        visible = pending != null,
        title = "Remove page ${(pending ?: 0) + 1}?",
        message = "Its apps stay in the App Library. Its widgets are removed.",
        onDismiss = { confirmRemove = null },
        actions = listOf(
            GlassAlertAction("Cancel") { confirmRemove = null },
            GlassAlertAction("Remove", destructive = true) {
                pending?.let { widgets.removePage(it) }
                confirmRemove = null
            },
        ),
        icon = WidgetGlyphs.Pages,
    )
}

@Composable
private fun PageRow(
    page: Int,
    layout: HomeLayout,
    onDefault: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val isDefault = layout.defaultPage == page
    val apps = layout.pages[page].count { it != null }
    val widgetCount = layout.widgetsOn(page).size
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PageMiniature(layout, page)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                GlassText("Page ${page + 1}" + if (isDefault) " · Home" else "", style = GlassType.Headline)
                GlassText(
                    listOf("$apps app${if (apps == 1) "" else "s"}", if (widgetCount > 0) "$widgetCount widget${if (widgetCount == 1) "" else "s"}" else null)
                        .filterNotNull().joinToString(" · "),
                    style = GlassType.Footnote, secondary = true,
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIconButton(
                        if (isDefault) WidgetGlyphs.StarFill else WidgetGlyphs.Star,
                        onClick = onDefault,
                        size = 36.dp,
                        tint = if (isDefault) GlassColors.Orange else null,
                    )
                    if (onUp != null) GlassIconButton(WidgetGlyphs.ArrowUp, onClick = onUp, size = 36.dp)
                    if (onDown != null) GlassIconButton(WidgetGlyphs.ArrowDown, onClick = onDown, size = 36.dp)
                    if (onRemove != null) GlassIconButton(GlassIcons.Minus, onClick = onRemove, size = 36.dp, tint = GlassColors.Red)
                }
            }
        }
    }
}

/** A tiny drawing of a page: dots for icons, rounded blocks for widgets. */
@Composable
private fun PageMiniature(layout: HomeLayout, page: Int) {
    val ink = LocalGlassContentColor.current
    Box(Modifier.size(width = 64.dp, height = 96.dp)) {
        GlassSurface(Modifier.fillMaxSize(), shape = GlassShape.rounded(12.dp), variant = GlassVariant.Clear, role = null)
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val cw = size.width / GRID_COLUMNS
            val ch = size.height / GRID_ROWS
            val items = layout.pages[page]
            for (i in items.indices) {
                if (items[i] == null) continue
                val c = i % GRID_COLUMNS
                val r = i / GRID_COLUMNS
                drawCircle(ink.copy(alpha = 0.8f), radius = minOf(cw, ch) * 0.28f, center = Offset(cw * (c + 0.5f), ch * (r + 0.5f)))
            }
            layout.widgetsOn(page).forEach { w ->
                val pad = minOf(cw, ch) * 0.1f
                drawRoundRect(
                    ink.copy(alpha = 0.45f),
                    topLeft = Offset(cw * w.rect.col + pad, ch * w.rect.row + pad),
                    size = Size(cw * w.rect.w - pad * 2, ch * w.rect.h - pad * 2),
                    cornerRadius = CornerRadius(cw * 0.3f),
                )
            }
        }
    }
}
