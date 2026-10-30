package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import com.shivam.glasslauncher.ui.home.ownsVerticalScroll
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.shivam.glasslauncher.ui.glass.ResumedLoop
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * A swipeable vertical stack of widgets with page dots on the side. With rotation on, it turns to
 * the widget that suits the time of day (calendar in the morning, music in the evening) whenever
 * the period changes; a manual swipe is kept until the next period.
 */
/** True for widgets drawn inside a Smart Stack. */
val LocalInStack = androidx.compose.runtime.staticCompositionLocalOf { false }

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
    ResumedLoop(rotate, children.map { it.kind }) {
        if (!rotate) return@ResumedLoop
        while (true) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val period = StackRotation.period(hour)
            if (period != appliedPeriod) {
                appliedPeriod = period
                StackRotation.pick(children.map { it.kind }, hour)?.let { target ->
                    if (target != pager.currentPage && !pager.isScrollInProgress) {
                        try {
                            pager.animateScrollToPage(target)
                        } finally {
                            // A cancelled animation (recomposed loop, new touch) must not leave two cards half-visible.
                            if (pager.currentPageOffsetFraction != 0f && !pager.isScrollInProgress) {
                                withContext(NonCancellable) { pager.scrollToPage(pager.currentPage) }
                            }
                        }
                    }
                }
            }
            delay(60_000L)
        }
    }

    Box(modifier) {
        VerticalPager(
            state = pager,
            // Vertical swipes here flip the stack, not open Spotlight / the drawer.
            modifier = Modifier.fillMaxSize().then(if (children.size > 1) Modifier.ownsVerticalScroll() else Modifier),
            key = { children.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)),
        ) { page ->
            val child = children.getOrNull(page)
            // Inside a stack a vertical swipe flips widgets, so children don't scroll themselves (as on iOS).
            if (child != null) androidx.compose.runtime.CompositionLocalProvider(LocalInStack provides true) {
                WidgetContent(child, Modifier.fillMaxSize(), size = if (size in child.kind.sizes) size else child.kind.sizes.first())
            }
        }
        if (children.size > 1) {
            // Page dots on a Clear glass capsule at the trailing edge.
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            fun go(y: Float, h: Int) {
                val i = (y / h.coerceAtLeast(1) * children.size).toInt().coerceIn(0, children.size - 1)
                if (i != pager.currentPage) scope.launch { pager.animateScrollToPage(i) }
            }
            // The dot bar is a control too: tap a dot or drag along the bar to move through the stack.
            GlassSurface(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                    .pointerInput(children.size) { detectTapGestures { go(it.y, this.size.height) } }
                    .pointerInput(children.size) { detectVerticalDragGestures { change, _ -> change.consume(); go(change.position.y, this.size.height) } },
                shape = GlassShape.Capsule,
                variant = GlassVariant.Clear,
                role = null,
            ) {
                Column(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val c = LocalGlassContentColor.current
                    children.indices.forEach { i ->
                        val color = if (i == pager.currentPage) c else c.copy(alpha = 0.35f)
                        // 10 dp tall rows: big enough to hit with a finger.
                        Box(Modifier.padding(horizontal = 3.dp).size(width = 5.dp, height = 10.dp).drawBehind { drawCircle(color, radius = 2.5.dp.toPx()) })
                    }
                }
            }
        }
    }
}
