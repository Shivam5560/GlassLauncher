package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * A swipeable vertical stack of widgets with page dots on the side. With rotation on, it turns to
 * the widget that suits the time of day (calendar in the morning, music in the evening) whenever
 * the period changes; a manual swipe is kept until the next period.
 */
@Composable
fun SmartStackWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val repo = remember { WidgetRepository.get(context) }
    val state by repo.state.collectAsState()
    val children: List<WidgetInstance> = remember(inst, state) {
        val stored = inst.list(WidgetKeys.STACK_CHILDREN).mapNotNull { state?.instances?.get(it) }
        if (stored.isNotEmpty() || !inst.id.startsWith(PREVIEW_PREFIX)) stored
        else listOf(WidgetKind.CALENDAR, WidgetKind.WEATHER, WidgetKind.MUSIC).map { k ->
            WidgetInstance(PREVIEW_PREFIX + "stack-" + k.name, k, if (size in k.sizes) size else k.sizes.first())
        }
    }
    if (children.isEmpty()) {
        WidgetPrompt(WidgetGlyphs.Stack, "This stack is empty", "Edit", { env?.editWidget?.invoke(inst.id) }, size, modifier)
        return
    }
    val rotate = inst[WidgetKeys.STACK_ROTATE] != "0"
    val pager = rememberPagerState { children.size }
    var appliedPeriod by remember(inst.id) { mutableStateOf<StackRotation.Period?>(null) }
    LaunchedEffect(rotate, children.map { it.kind }) {
        if (!rotate) return@LaunchedEffect
        while (true) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val period = StackRotation.period(hour)
            if (period != appliedPeriod) {
                appliedPeriod = period
                StackRotation.pick(children.map { it.kind }, hour)?.let { target ->
                    if (target != pager.currentPage && !pager.isScrollInProgress) pager.animateScrollToPage(target)
                }
            }
            delay(60_000L)
        }
    }

    Box(modifier) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { children.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
        ) { page ->
            val child = children.getOrNull(page)
            if (child != null) WidgetContent(child, Modifier.fillMaxSize(), size = if (size in child.kind.sizes) size else child.kind.sizes.first())
        }
        if (children.size > 1) {
            // Page dots on a Clear glass capsule at the trailing edge.
            GlassSurface(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                shape = GlassShape.Capsule,
                variant = GlassVariant.Clear,
                role = null,
            ) {
                Column(Modifier.padding(horizontal = 3.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val c = LocalGlassContentColor.current
                    children.indices.forEach { i ->
                        val color = if (i == pager.currentPage) c else c.copy(alpha = 0.35f)
                        Box(Modifier.padding(vertical = 2.dp).size(5.dp).drawBehind { drawCircle(color) })
                    }
                }
            }
        }
    }
}
