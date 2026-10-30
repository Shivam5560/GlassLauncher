package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.toArgb
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
            val spans = remember(text, inst[WidgetKeys.QUOTE_COLORS]) { QuoteSpans.clip(QuoteSpans.decode(inst[WidgetKeys.QUOTE_COLORS]), text.length) }
            val ink = LocalGlassContentColor.current
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) {
                androidx.compose.material3.Text(
                    quoteText(text, spans),
                    style = GlassType.Body.copy(
                        fontSize = if (size == WidgetSize.LARGE) 26.sp else 18.sp,
                        lineHeight = if (size == WidgetSize.LARGE) 38.sp else 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink,
                    ),
                )
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
    val initial = inst[WidgetKeys.QUOTE_TEXT] ?: QuoteDefaults.TEXT
    var value by remember(inst.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial)) }
    var spans by remember(inst.id) { mutableStateOf(QuoteSpans.clip(QuoteSpans.decode(inst[WidgetKeys.QUOTE_COLORS]), initial.length)) }
    var author by rememberSaveable(inst.id) { mutableStateOf(inst[WidgetKeys.QUOTE_AUTHOR].orEmpty()) }
    val text = value.text
    val accent = currentAccent()
    // Colours the selection, or the whole quote when nothing is selected.
    fun paint(color: Int?) {
        val sel = value.selection
        val (a, b) = if (sel.collapsed) 0 to text.length else minOf(sel.start, sel.end) to maxOf(sel.start, sel.end)
        spans = QuoteSpans.apply(spans, a, b, color)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassText("Your words, your perspective", style = GlassType.Headline)
        // Above the text box so the swatches stay visible while the keyboard is up.
        GlassText(if (value.selection.collapsed) "Tap a colour for the whole quote, or select words first" else "Colour the selected words", style = GlassType.Subhead)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            (QuotePalette + ("Accent" to accent.toArgb())).forEach { (name, argb) ->
                Box(
                    Modifier.size(36.dp)
                        .glassPressable(onClick = { paint(argb) }, shape = GlassShape.Circle)
                        .background(androidx.compose.ui.graphics.Color(argb), GlassShape.Circle)
                        .semantics { contentDescription = "Colour $name" },
                )
            }
            GlassChip("Clear", selected = false, onClick = { paint(null) })
        }
        GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
            BasicTextField(
                value,
                { v ->
                    val t = v.text.take(QuoteDefaults.MAX_TEXT)
                    spans = QuoteSpans.shift(spans, text, t)
                    value = if (t == v.text) v else v.copy(text = t)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp).padding(16.dp).semantics { contentDescription = "Quote text" },
                textStyle = GlassType.Body.copy(color = LocalGlassContentColor.current, lineHeight = 28.sp),
                cursorBrush = SolidColor(accent),
                // Colours show while typing, exactly as on the widget.
                visualTransformation = { t -> androidx.compose.ui.text.input.TransformedText(quoteText(t.text, QuoteSpans.clip(spans, t.text.length)), androidx.compose.ui.text.input.OffsetMapping.Identity) },
            )
        }
        GlassText("${text.length} / ${QuoteDefaults.MAX_TEXT}", style = GlassType.Caption, secondary = true)
        GlassTextField(author, { author = it.take(QuoteDefaults.MAX_AUTHOR) }, placeholder = "Author or source (optional)", leadingIcon = null)
        GlassButton("Save quote", onClick = {
            val t = text.trim()
            // Trimming leading spaces moves every colour left by the same amount.
            val lead = text.length - text.trimStart().length
            val saved = QuoteSpans.clip(spans.map { it.copy(start = (it.start - lead).coerceAtLeast(0), end = (it.end - lead).coerceAtLeast(0)) }, t.length)
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_TEXT, t)
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_COLORS, QuoteSpans.encode(saved))
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_AUTHOR, author.trim())
            widgets.editing = null
        }, prominent = true, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
        GlassButton("Use Hindi quote", onClick = { value = androidx.compose.ui.text.input.TextFieldValue(QuoteDefaults.TEXT); spans = emptyList(); author = "" })
        GlassText("Saved on this device. Tap the widget to edit; scroll longer quotes to keep every word readable.", style = GlassType.Caption, secondary = true)
    }
}

/** Soft tones that stay readable on glass; the accent is added at runtime. */
private val QuotePalette = listOf(
    "Pearl" to 0xFFF5F5F7.toInt(), "Gold" to 0xFFFFD479.toInt(), "Rose" to 0xFFFF8FA3.toInt(), "Coral" to 0xFFFF9F6E.toInt(),
    "Mint" to 0xFF7BE0B5.toInt(), "Sky" to 0xFF7CC4FF.toInt(), "Lavender" to 0xFFB9A6FF.toInt(),
)

/** Quote text with its coloured runs; each coloured run gets a faint glow of its own colour to lift it off the glass. */
private fun quoteText(text: String, spans: List<QuoteSpan>): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        spans.forEach { sp ->
            val c = androidx.compose.ui.graphics.Color(sp.color)
            addStyle(androidx.compose.ui.text.SpanStyle(
                color = c,
                shadow = androidx.compose.ui.graphics.Shadow(c.copy(alpha = 0.45f), blurRadius = 14f),
            ), sp.start, sp.end)
        }
    }
