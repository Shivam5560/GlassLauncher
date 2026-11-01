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

/** Fixed quote sizes in sp (longer quotes scroll). Auto picks the largest size that fits. */
enum class QuoteSize(val label: String, val sp: Float?) {
    AUTO("Auto", null), S("S", 18f), M("M", 24f), L("L", 30f), XL("XL", 36f), XXL("XXL", 44f);
    companion object { fun of(v: String?) = entries.firstOrNull { it.name == v } ?: AUTO }
}

/** A Devanagari serif with Latin too; falls back to the bundled sans if Google Fonts can't load. */
@Composable
private fun quoteFamily(): androidx.compose.ui.text.font.FontFamily {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember { com.shivam.glasslauncher.polish.Fonts.family(context, "gf:Tiro Devanagari Hindi") }
}

@Composable
fun QuotesWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val env = LocalWidgetEnv.current
    val text = inst[WidgetKeys.QUOTE_TEXT]?.takeIf { it.isNotBlank() } ?: QuoteDefaults.TEXT
    val author = inst[WidgetKeys.QUOTE_AUTHOR].orEmpty()
    val editable = !inst.id.startsWith(PREVIEW_PREFIX) && env?.editMode != true
    val large = size == WidgetSize.LARGE
    val accent = currentAccent()
    val ink = LocalGlassContentColor.current
    val family = quoteFamily()
    val context = androidx.compose.ui.platform.LocalContext.current
    val markFamily = remember { com.shivam.glasslauncher.polish.Fonts.family(context, "lora") }
    WidgetFrame(modifier, padding = 20.dp, onClick = if (editable) ({ env?.editWidget?.invoke(inst.id) }) else null) {
        Box(Modifier.fillMaxSize()) {
            // A big serif opening mark, faint, sets the tone without competing with the words.
            androidx.compose.material3.Text(
                "\u201C",
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-22).dp),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = markFamily,
                    fontSize = if (large) 150.sp else 110.sp,
                    color = accent.copy(alpha = 0.22f),
                ),
            )
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(WidgetGlyphs.Quote, size = 16.dp, tint = accent)
                    Spacer(Modifier.width(8.dp))
                    GlassText("A LITTLE PERSPECTIVE", style = GlassType.Caption.copy(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold), secondary = true)
                }
                Spacer(Modifier.height(if (large) 14.dp else 8.dp))
                val spans = remember(text, inst[WidgetKeys.QUOTE_COLORS]) { QuoteSpans.clip(QuoteSpans.decode(inst[WidgetKeys.QUOTE_COLORS]), text.length) }
                val annotated = remember(text, spans) { quoteText(text, spans) }
                val choice = QuoteSize.of(inst[WidgetKeys.QUOTE_SIZE])
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    fun style(sp: Float) = androidx.compose.ui.text.TextStyle(
                        fontFamily = family,
                        fontSize = sp.sp,
                        lineHeight = (sp * 1.5f).sp,
                        // Devanagari vowel marks (ि े ै ों) rise above the line box: keep the font's
                        // padding and the full first-line height, or the top of line one is cut off.
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                        ),
                        color = ink,
                        shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f), blurRadius = 10f),
                    )
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val sp = remember(annotated, choice, large, w, h, family) {
                        choice.sp
                            ?: QuoteFit.largest(if (large) 52f else 40f, 13f) { s ->
                                measurer.measure(annotated, style(s), constraints = androidx.compose.ui.unit.Constraints(maxWidth = w)).size.height <= h
                            }
                    }
                    // Fixed sizes can overflow: scroll so every word stays readable.
                    Box(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                        androidx.compose.material3.Text(annotated, style = style(sp))
                    }
                }
                if (author.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(22.dp).height(2.dp).background(accent.copy(alpha = 0.8f), GlassShape.Capsule))
                        Spacer(Modifier.width(10.dp))
                        GlassText(author, style = GlassType.Caption.copy(fontFamily = family, fontSize = 14.sp), secondary = true, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** Largest font size in [max] down to [min] (1 sp steps) for which [fits] holds; [min] if none does. */
internal object QuoteFit {
    fun largest(max: Float, min: Float, fits: (Float) -> Boolean): Float {
        var s = max
        while (s > min && !fits(s)) s -= 1f
        return s
    }
}

@Composable
fun QuotesSettings(widgets: WidgetUiState, inst: WidgetInstance) {
    val initial = inst[WidgetKeys.QUOTE_TEXT] ?: QuoteDefaults.TEXT
    var value by remember(inst.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial)) }
    var spans by remember(inst.id) { mutableStateOf(QuoteSpans.clip(QuoteSpans.decode(inst[WidgetKeys.QUOTE_COLORS]), initial.length)) }
    var author by rememberSaveable(inst.id) { mutableStateOf(inst[WidgetKeys.QUOTE_AUTHOR].orEmpty()) }
    var sizeChoice by remember(inst.id) { mutableStateOf(QuoteSize.of(inst[WidgetKeys.QUOTE_SIZE])) }
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
        GlassText("Text size", style = GlassType.Subhead)
        GlassSegmented(QuoteSize.entries.map { it.label }, selected = sizeChoice.ordinal, onSelect = { sizeChoice = QuoteSize.entries[it] }, modifier = Modifier.fillMaxWidth())
        GlassText("Auto uses the largest size that fits the widget.", style = GlassType.Caption, secondary = true)
        GlassTextField(author, { author = it.take(QuoteDefaults.MAX_AUTHOR) }, placeholder = "Author or source (optional)", leadingIcon = null)
        GlassButton("Save quote", onClick = {
            val t = text.trim()
            // Trimming leading spaces moves every colour left by the same amount.
            val lead = text.length - text.trimStart().length
            val saved = QuoteSpans.clip(spans.map { it.copy(start = (it.start - lead).coerceAtLeast(0), end = (it.end - lead).coerceAtLeast(0)) }, t.length)
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_TEXT, t)
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_COLORS, QuoteSpans.encode(saved))
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_AUTHOR, author.trim())
            widgets.repo.setConfig(inst.id, WidgetKeys.QUOTE_SIZE, sizeChoice.name)
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
