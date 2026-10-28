package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import kotlinx.coroutines.delay

/** A quick note, edited in place on the glass. Saved half a second after typing stops. */
@Composable
fun NotesWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val repo = remember { WidgetRepository.get(context) }
    val saved = inst[WidgetKeys.NOTE_TEXT] ?: ""
    var text by remember(inst.id) { mutableStateOf(saved) }
    LaunchedEffect(text) {
        if (text == saved) return@LaunchedEffect
        delay(500)
        repo.setConfig(inst.id, WidgetKeys.NOTE_TEXT, text)
    }
    val editable = env?.editMode != true && !inst.id.startsWith(PREVIEW_PREFIX)
    WidgetFrame(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(WidgetGlyphs.Notes, size = 16.dp, tint = GlassColors.Orange)
                Spacer(Modifier.width(6.dp))
                GlassText("Note", style = GlassType.Subhead, color = GlassColors.Orange)
            }
            Spacer(Modifier.height(6.dp))
            val color = LocalGlassContentColor.current
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (text.isEmpty()) GlassText(if (size == WidgetSize.SMALL) "Tap to write…" else "Tap to write a quick note…", style = GlassType.Callout, secondary = true)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = editable,
                    textStyle = (if (size == WidgetSize.SMALL) GlassType.Callout else GlassType.Body).copy(color = color),
                    cursorBrush = SolidColor(GlassColors.Accent),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** A glass search bar that opens Spotlight, morphing out of the bar. */
@Composable
fun SearchBarWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnv.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    GlassSurface(
        modifier = modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
        shape = GlassShape.Capsule,
        variant = GlassVariant.Regular,
        onClick = { env?.openSpotlight?.invoke(bounds) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(GlassIcons.Search, size = 20.dp, tint = LocalGlassContentColor.current.copy(alpha = 0.75f))
            Spacer(Modifier.width(10.dp))
            GlassText("Search", style = GlassType.Body, secondary = true, maxLines = 1)
        }
    }
}

/** Ids of throwaway instances used for live previews in the widget picker. */
const val PREVIEW_PREFIX = "preview-"
