package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.ui.glass.*

object QuoteDefaults {
    const val TEXT = "जो ग़म है, उसे लेकर क्यों रोते हो?\nजो होता है, अच्छे के लिए होता है।"
    const val MAX_TEXT = 600
    const val MAX_AUTHOR = 80
}

@Composable
fun QuotesWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val env = LocalWidgetEnv.current
    val text = inst[WidgetKeys.QUOTE_TEXT]?.takeIf { it.isNotBlank() } ?: QuoteDefaults.TEXT
    val author = inst[WidgetKeys.QUOTE_AUTHOR].orEmpty()
    val editable = !inst.id.startsWith(PREVIEW_PREFIX) && env?.editMode != true
    WidgetFrame(modifier, padding = 18.dp, onClick = if (editable) ({ env?.editWidget?.invoke(inst.id) }) else null) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(WidgetGlyphs.Quote, size = 20.dp, tint = currentAccent())
                Spacer(Modifier.width(8.dp))
                GlassText("A little perspective", style = GlassType.Caption.copy(letterSpacing = 0.3.sp), secondary = true)
            }
            Spacer(Modifier.height(if (size == WidgetSize.LARGE) 20.dp else 8.dp))
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) {
                GlassText(text, style = GlassType.Body.copy(
                    fontSize = if (size == WidgetSize.LARGE) 26.sp else 18.sp,
                    lineHeight = if (size == WidgetSize.LARGE) 38.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                ))
            }
            if (author.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                GlassText("— $author", style = GlassType.Caption, secondary = true, maxLines = 2)
            }
        }
    }
}

@Composable
fun QuotesSettings(widgets: WidgetUiState, inst: WidgetInstance) {
    var text by rememberSaveable(inst.id) { mutableStateOf(inst[WidgetKeys.QUOTE_TEXT] ?: QuoteDefaults.TEXT) }
    var author by rememberSaveable(inst.id) { mutableStateOf(inst[WidgetKeys.QUOTE_AUTHOR].orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassText("Your words, your perspective", style = GlassType.Headline)
        GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
            BasicTextField(
                text, { text = it.take(QuoteDefaults.MAX_TEXT) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp).padding(16.dp).semantics { contentDescription = "Quote text" },
                textStyle = GlassType.Body.copy(color = LocalGlassContentColor.current, lineHeight = 28.sp),
                cursorBrush = SolidColor(currentAccent()),
            )
        }
        GlassText("${text.length} / ${QuoteDefaults.MAX_TEXT}", style = GlassType.Caption, secondary = true)
        GlassTextField(author, { author = it.take(QuoteDefaults.MAX_AUTHOR) }, placeholder = "Author or source (optional)", leadingIcon = null)
        GlassButton("Save quote", onClick = {
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_TEXT, text.trim())
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_AUTHOR, author.trim())
            widgets.editing = null
        }, prominent = true, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
        GlassButton("Use Hindi quote", onClick = { text = QuoteDefaults.TEXT; author = "" })
        GlassText("Saved on this device. Tap the widget to edit; scroll longer quotes to keep every word readable.", style = GlassType.Caption, secondary = true)
    }
}
