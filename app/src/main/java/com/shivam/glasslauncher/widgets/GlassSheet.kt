package com.shivam.glasslauncher.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassScrim
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSprings
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant

/**
 * A glass bottom sheet: a Regular glass panel that springs up from the bottom over a scrim. Drag
 * the grabber down or tap the scrim to dismiss. Not a Material bottom sheet (REVIEW.md rule 1).
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    heightFraction: Float = 0.9f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(visible) }
    var drag by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            drag = 0f
            progress.animateTo(1f, GlassSprings.Open)
        } else {
            progress.animateTo(0f, GlassSprings.Close)
            composed = false
        }
    }
    if (!composed) return
    if (visible) BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        GlassScrim(progress.value, if (visible) onDismiss else null)
        GlassSurface(
            Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .padding(horizontal = 6.dp)
                .graphicsLayer {
                    translationY = (1f - progress.value) * size.height + drag
                },
            shape = GlassShape.rounded(34.dp),
            variant = GlassVariant.Regular,
            role = null,
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                // Grabber + title row; dragging it down dismisses.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (drag > with(density) { 120.dp.toPx() }) onDismiss()
                                    drag = 0f
                                },
                                onDragCancel = { drag = 0f },
                            ) { change, amount ->
                                change.consume()
                                drag = (drag + amount).coerceAtLeast(0f)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    GlassSurface(Modifier.width(40.dp).height(5.dp), shape = GlassShape.Capsule, variant = GlassVariant.Clear, role = null)
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassText(title, style = GlassType.Title, maxLines = 1, modifier = Modifier.weight(1f))
                        GlassIconButton(GlassIcons.Close, onClick = onDismiss, size = 34.dp)
                    }
                }
                content()
            }
        }
    }
}
