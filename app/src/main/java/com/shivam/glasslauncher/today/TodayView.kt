package com.shivam.glasslauncher.today

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.home.RemoveBadge
import com.shivam.glasslauncher.ui.home.jiggleRotation
import com.shivam.glasslauncher.widgets.PickerTarget
import com.shivam.glasslauncher.widgets.WidgetContent
import com.shivam.glasslauncher.widgets.WidgetGlyphs
import com.shivam.glasslauncher.widgets.WidgetInstance
import com.shivam.glasslauncher.widgets.WidgetKeys
import com.shivam.glasslauncher.widgets.WidgetKind
import com.shivam.glasslauncher.widgets.WidgetMenu
import com.shivam.glasslauncher.widgets.WidgetSize
import com.shivam.glasslauncher.widgets.WidgetUiState
import com.shivam.glasslauncher.widgets.widgetLongPress
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A row of the Today view: one full-width widget, or two small widgets side by side. */
private data class TodayRow(val items: List<WidgetInstance>) {
    val key: String get() = items.first().id
}

/**
 * The Today view: the leftmost page, reached by swiping right from the first home page. The big
 * date on top, then a scrolling column of glass widgets. Edit mode adds, removes and reorders.
 */
@Composable
fun TodayView(widgets: WidgetUiState, editMode: Boolean, setEditMode: (Boolean) -> Unit, lightWallpaper: Boolean) {
    val state by widgets.repo.state.collectAsState()
    LaunchedEffect(state != null) { if (state != null) widgets.repo.seedTodayIfNeeded() }
    val s = state ?: return
    val order = s.today
    val instances = order.mapNotNull { s.instances[it] }
    val rows = remember(instances) { pairSmalls(instances) }
    val textColor = if (lightWallpaper) Color(0xFF1C1C1E) else Color.White
    val shadow = if (lightWallpaper) null else Shadow(Color(0x88000000), Offset(0f, 1f), 6f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 14.dp
        val side = 16.dp
        val small: Dp = (maxWidth - side * 2 - gap) / 2
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item("date") { BigDate(textColor, shadow) }
            items(rows, key = { it.key }) { row ->
                if (row.items.size == 2 || row.items.first().size == WidgetSize.SMALL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.items.forEach { inst -> TodayItem(inst, order, widgets, editMode, small, small) }
                    }
                } else {
                    val inst = row.items.first()
                    val h = when {
                        inst.kind == WidgetKind.APP_WIDGET -> (small / 2) * ((inst[WidgetKeys.TODAY_ROWS]?.toIntOrNull() ?: 2).coerceIn(1, 6))
                        inst.size == WidgetSize.LARGE -> small * 2 + gap
                        inst.size == WidgetSize.BAR -> 52.dp
                        else -> small
                    }
                    TodayItem(inst, order, widgets, editMode, maxWidth - side * 2, h)
                }
            }
            item("buttons") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    if (editMode) {
                        GlassButton("Add Widget", onClick = { widgets.picker = PickerTarget.Today }, icon = GlassIcons.Plus)
                        Spacer(Modifier.width(10.dp))
                        GlassButton("Done", onClick = { setEditMode(false) }, prominent = true)
                    } else {
                        GlassButton("Edit", onClick = { setEditMode(true) })
                    }
                }
            }
        }
    }
}

/** Pairs consecutive small widgets into one row, like the iOS Today view. */
private fun pairSmalls(list: List<WidgetInstance>): List<TodayRow> {
    val rows = ArrayList<TodayRow>()
    var i = 0
    while (i < list.size) {
        val a = list[i]
        val b = list.getOrNull(i + 1)
        if (a.size == WidgetSize.SMALL && a.kind != WidgetKind.APP_WIDGET && b != null && b.size == WidgetSize.SMALL && b.kind != WidgetKind.APP_WIDGET) {
            rows += TodayRow(listOf(a, b))
            i += 2
        } else {
            rows += TodayRow(listOf(a))
            i++
        }
    }
    return rows
}

@Composable
private fun BigDate(color: Color, shadow: Shadow?) {
    val today by produceState(LocalDate.now()) {
        while (true) {
            delay(60_000L)
            value = LocalDate.now()
        }
    }
    Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
        GlassText(
            today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(),
            style = GlassType.Headline.copy(shadow = shadow),
            color = GlassColors.Red,
            maxLines = 1,
        )
        GlassText(
            today.format(DateTimeFormatter.ofPattern("d MMMM")),
            style = GlassType.LargeTitle.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, shadow = shadow),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun TodayItem(
    inst: WidgetInstance,
    order: List<String>,
    widgets: WidgetUiState,
    editMode: Boolean,
    width: Dp,
    height: Dp,
) {
    val haptics = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val index = order.indexOf(inst.id)
    val rotation = jiggleRotation(editMode)
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { rotationZ = rotation * 0.5f }
            .onGloballyPositioned { coords[0] = it }
            .widgetLongPress(enabled = !editMode) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                widgets.menu = WidgetMenu(inst.id, coords[0]?.boundsInWindow() ?: Rect.Zero, onHome = false)
            },
    ) {
        WidgetContent(inst, Modifier.fillMaxSize())
        if (editMode) {
            // Blocks the widget while editing; tap opens its settings.
            Box(Modifier.fillMaxSize().pointerInput(inst.id) { detectTapGestures { if (inst.kind != WidgetKind.SEARCH) widgets.editing = inst.id } })
            RemoveBadge(onClick = { widgets.repo.removeFromToday(inst.id) }, modifier = Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp))
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (index > 0) GlassIconButton(WidgetGlyphs.ArrowUp, onClick = { widgets.repo.moveToday(index, index - 1) }, size = 32.dp)
                if (index in 0 until order.size - 1) GlassIconButton(WidgetGlyphs.ArrowDown, onClick = { widgets.repo.moveToday(index, index + 1) }, size = 32.dp)
            }
        }
    }
}
