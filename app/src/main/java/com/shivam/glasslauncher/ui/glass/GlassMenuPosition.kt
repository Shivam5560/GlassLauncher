package com.shivam.glasslauncher.ui.glass

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Keep anchored menus inside the status/navigation safe area, including with hidden system bars. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Modifier.glassMenuPosition(anchor: Rect, alignEnd: Boolean = false): Modifier {
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    return fillMaxSize().layout { measurable, constraints ->
        val margin = 12.dp.roundToPx()
        val top = (topInset + margin).coerceAtMost(constraints.maxHeight)
        val bottom = (constraints.maxHeight - bottomInset - margin).coerceAtLeast(top)
        val child = measurable.measure(constraints.copy(
            minWidth = 0, minHeight = 0,
            maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0),
            maxHeight = bottom - top,
        ))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val below = anchor.bottom.roundToInt() + margin
            val preferredY = if (below + child.height <= bottom) below
                else anchor.top.roundToInt() - margin - child.height
            val y = preferredY.coerceIn(top, (bottom - child.height).coerceAtLeast(top))
            val preferredX = if (alignEnd) anchor.right.roundToInt() - child.width
                else anchor.center.x.roundToInt() - child.width / 2
            val x = preferredX.coerceIn(margin, (constraints.maxWidth - child.width - margin).coerceAtLeast(margin))
            child.place(x, y)
        }
    }
}
