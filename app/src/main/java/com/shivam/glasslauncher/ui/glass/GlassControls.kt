package com.shivam.glasslauncher.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import kotlin.math.roundToInt

/** Text that defaults to the colour of the glass it sits on. */
@Composable
fun GlassText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = GlassType.Body,
    color: Color = LocalGlassContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    secondary: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = if (secondary) color.copy(alpha = 0.6f) else color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun GlassIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalGlassContentColor.current) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Capsule glass button. [prominent] tints it with the accent colour. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    prominent: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    variant: GlassVariant = GlassVariant.Clear,
) {
    GlassSurface(
        modifier = modifier.heightIn(min = 44.dp),
        shape = GlassShape.Capsule,
        variant = variant,
        onClick = if (enabled) onClick else null,
        tintOverride = when {
            prominent -> currentAccent()
            destructive -> GlassColors.Red
            else -> null
        },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val color = if (prominent || destructive) Color.White else LocalGlassContentColor.current
            if (icon != null) {
                GlassIcon(icon, size = 18.dp, tint = color.copy(alpha = if (enabled) 1f else 0.4f))
                Spacer(Modifier.width(8.dp))
            }
            GlassText(text, style = GlassType.Headline, color = color.copy(alpha = if (enabled) 1f else 0.4f), maxLines = 1)
        }
    }
}

/** Round glass button holding a glyph. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color? = null,
    tintGlyph: Color? = null,
    contentDescription: String = icon.name,
) {
    GlassSurface(
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        shape = GlassShape.Circle,
        variant = GlassVariant.Clear,
        onClick = onClick,
        tintOverride = tint,
        contentAlignment = Alignment.Center,
    ) {
        GlassIcon(icon, size = size * 0.45f, tint = tintGlyph ?: if (tint != null) Color.White else LocalGlassContentColor.current)
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.height(34.dp),
        shape = GlassShape.Capsule,
        variant = GlassVariant.Clear,
        onClick = onClick,
        highlighted = selected,
        tintOverride = if (selected) currentAccent() else null,
        contentAlignment = Alignment.Center,
    ) {
        GlassText(
            text,
            Modifier.padding(horizontal = 14.dp),
            style = GlassType.Subhead,
            color = if (selected) Color.White else LocalGlassContentColor.current,
            maxLines = 1,
        )
    }
}

/** iOS-style switch: a glass track with a Clear glass thumb that swells while touched. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "switch")
    val interactions = remember { MutableInteractionSource() }
    val touching by interactions.collectIsPressedAsState()
    Box(
        modifier
            .size(width = 56.dp, height = 48.dp)
            .toggleable(value = checked, role = Role.Switch, interactionSource = interactions, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        GlassSurface(
            modifier = Modifier.size(width = 56.dp, height = 32.dp),
            shape = GlassShape.Capsule,
            variant = GlassVariant.Clear,
            tintOverride = if (checked) (LocalGlassAccent.current ?: GlassColors.Green) else null,
            role = null,
        )
        GlassSurface(
            modifier = Modifier
                .offset { IntOffset((pos * 24.dp.toPx()).roundToInt() + 2.dp.roundToPx(), 0) }
                .size(width = if (touching) 34.dp else 28.dp, height = 28.dp),
            shape = GlassShape.Capsule,
            variant = GlassVariant.Clear,
            highlighted = touching,
            tintOverride = Color.White,
            role = null,
        )
    }
}

/** Glass slider: thin track with accent fill and a Clear glass thumb that becomes a lens while dragged. */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val thumb = 28.dp
    val onChange by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val thumbPx = with(density) { thumb.toPx() }

    fun valueAt(x: Float): Float {
        val f = ((x - thumbPx / 2) / (widthPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return valueRange.start + f * span
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                setProgress { target ->
                    onChange(target.coerceIn(valueRange))
                    onValueChangeFinished()
                    true
                }
            }
            .pointerInput(valueRange) {
                detectTapGestures { onChange(valueAt(it.x)); onValueChangeFinished() }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { dragging = true; onChange(valueAt(it.x)) },
                    onDragEnd = { dragging = false; onValueChangeFinished() },
                    onDragCancel = { dragging = false },
                ) { change, _ -> change.consume(); onChange(valueAt(change.position.x)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        widthPx = with(density) { maxWidth.toPx() }
        val content = LocalGlassContentColor.current
        val accent = currentAccent()
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val y = size.height / 2
            val x0 = thumbPx / 2
            val x1 = size.width - thumbPx / 2
            drawLine(content.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(x0, y), androidx.compose.ui.geometry.Offset(x1, y), strokeWidth = size.height, cap = StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(x0, y), androidx.compose.ui.geometry.Offset(x0 + (x1 - x0) * fraction, y), strokeWidth = size.height, cap = StrokeCap.Round)
        }
        val travel = with(density) { (maxWidth - thumb).toPx() }
        GlassSurface(
            modifier = Modifier
                .offset { IntOffset((fraction * travel).roundToInt(), 0) }
                .size(width = if (dragging) thumb + 10.dp else thumb, height = if (dragging) 30.dp else 26.dp),
            shape = GlassShape.Capsule,
            variant = GlassVariant.Clear,
            highlighted = dragging,
            tintOverride = if (dragging) null else Color.White,
            role = null,
        )
    }
}

/** Segmented control: a Regular glass capsule with a Clear glass lens sliding under the selection. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (options.isEmpty()) return
    val safeSelection = selected.takeIf { it in options.indices }
    val density = LocalDensity.current
    var segmentHeight by remember { mutableStateOf(40.dp) }
    val pos by animateFloatAsState((safeSelection ?: 0).toFloat(), spring(dampingRatio = 0.85f, stiffness = 400f), label = "segment")
    GlassSurface(modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), shape = GlassShape.rounded(16.dp), variant = GlassVariant.Regular, role = null) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
            val w = maxWidth / options.size
            if (safeSelection != null) GlassSurface(
                modifier = Modifier.offset(x = w * pos).width(w).height(segmentHeight),
                shape = GlassShape.rounded(12.dp), variant = GlassVariant.Clear,
                tintOverride = currentAccent(), role = null,
            )
            Row(Modifier.fillMaxWidth().selectableGroup().onSizeChanged { segmentHeight = with(density) { it.height.toDp() } }.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                options.forEachIndexed { i, label ->
                    Box(Modifier.weight(1f).fillMaxHeight().heightIn(min = 40.dp)
                        .clip(GlassShape.rounded(12.dp))
                        .selectable(selected = i == selected, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        GlassText(label, style = GlassType.Subhead.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2)
                    }
                }
            }
        }
    }
}

/** Capsule glass text field with an optional leading glyph and trailing clear button. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = GlassIcons.Search,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    variant: GlassVariant = GlassVariant.Regular,
    shape: GlassShape = GlassShape.Capsule,
) {
    GlassSurface(modifier = modifier.height(48.dp), shape = shape, variant = variant, role = null, contentAlignment = Alignment.CenterStart) {
        val color = LocalGlassContentColor.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                GlassIcon(leadingIcon, size = 20.dp, tint = color.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) GlassText(placeholder, style = GlassType.Body, color = color.copy(alpha = 0.5f), maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = GlassType.Body.copy(color = color),
                    cursorBrush = SolidColor(currentAccent()),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }.let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
                )
            }
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                GlassIconButton(GlassIcons.Close, onClick = { onValueChange("") }, size = 40.dp, contentDescription = "Clear search")
            }
        }
    }
}

/**
 * Section of grouped rows on ONE glass card (iOS "inset grouped" list): rows inside are plain
 * (no glass of their own) and separated by hairline dividers, so a section reads as a single
 * calm panel instead of a stack of slabs.
 */
@Composable
fun GlassListGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val screenColor = LocalScreenContentColor.current
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            GlassText(
                title.uppercase(),
                Modifier.padding(start = 6.dp, bottom = 10.dp).semantics { heading() },
                style = GlassType.Caption.copy(letterSpacing = 1.4.sp, shadow = screenTextShadow(screenColor)),
                color = screenColor.copy(alpha = 0.8f),
            )
        }
        GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null, kind = SurfaceKind.Cards, variant = GlassVariant.Regular) {
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                val scope = this
                DividedColumn(LocalGlassContentColor.current.copy(alpha = 0.09f), inset = 18.dp) { scope.content() }
            }
        }
        if (footer != null) {
            GlassText(
                footer,
                Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                style = GlassType.Footnote.copy(shadow = screenTextShadow(screenColor)),
                color = screenColor.copy(alpha = 0.75f),
            )
        }
    }
}

/** A soft shadow under white text drawn straight on a wallpaper; none under dark text. */
fun screenTextShadow(color: Color): Shadow? =
    if (color.luminance() > 0.5f) Shadow(Color(0x80000000), androidx.compose.ui.geometry.Offset(0f, 1f), 6f) else null

/** Stacks children vertically and draws a hairline between consecutive non-empty children. */
@Composable
private fun DividedColumn(dividerColor: Color, inset: Dp, content: @Composable () -> Unit) {
    val dividers = remember { ArrayList<Int>() }
    Layout(
        content = content,
        modifier = Modifier.fillMaxWidth().drawWithContent {
            drawContent()
            val x0 = inset.toPx()
            val stroke = 1f.coerceAtLeast(density * 0.5f)
            for (y in dividers) {
                drawLine(dividerColor, androidx.compose.ui.geometry.Offset(x0, y.toFloat()), androidx.compose.ui.geometry.Offset(size.width, y.toFloat()), strokeWidth = stroke)
            }
        },
    ) { measurables, constraints ->
        val child = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(child) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (placeables.maxOfOrNull { it.width } ?: 0)
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE)
        layout(width, height) {
            dividers.clear()
            var y = 0
            var first = true
            for (p in placeables) {
                if (p.height > 0) {
                    if (!first) dividers += y
                    first = false
                }
                p.placeRelative(0, y)
                y += p.height
            }
        }
    }
}

/**
 * A plain row inside a [GlassListGroup]: no glass of its own (the group card is the glass);
 * pressing it shows a soft highlight.
 */
@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = currentAccent(),
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val content = LocalGlassContentColor.current
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .background(if (pressed && onClick != null) content.copy(alpha = 0.10f) else Color.Transparent)
            .heightIn(min = 64.dp)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(34.dp).clip(GlassShape.rounded(11.dp)).background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlassIcon(icon, size = 19.dp, tint = content)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            GlassText(title, style = GlassType.Body.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 2)
            if (subtitle != null) GlassText(subtitle, Modifier.padding(top = 4.dp), style = GlassType.Footnote.copy(lineHeight = 18.sp), color = content.copy(alpha = 0.65f), maxLines = 3)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            GlassIcon(GlassIcons.ChevronRight, size = 16.dp, tint = content.copy(alpha = 0.5f))
        }
    }
}

/** A labelled slider row for tuning screens. */
@Composable
fun GlassSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassText(title, Modifier.weight(1f), style = GlassType.Callout)
            GlassText(format(value), style = GlassType.Callout, secondary = true)
        }
        GlassSlider(value, onValueChange, valueRange = valueRange)
    }
}

/** A row with a [GlassSwitch]. */
@Composable
fun GlassSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null, iconTint: Color = currentAccent()) {
    GlassRow(title = title, subtitle = subtitle, icon = icon, iconTint = iconTint,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange), showChevron = false) {
        GlassSwitch(checked, onCheckedChange, Modifier.clearAndSetSemantics {})
    }
}

/** Vertical A–Z index scrubber on a Clear glass capsule. */
@Composable
fun GlassIndexScrubber(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var heightPx by remember { mutableFloatStateOf(1f) }
    var last by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(false) }
    fun pick(y: Float) {
        if (letters.isEmpty()) return
        val i = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
        val l = letters[i]
        if (l != last) {
            last = l
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(l)
        }
    }
    GlassSurface(
        modifier = modifier
            .width(26.dp)
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letters) { detectTapGestures { pick(it.y) } }
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { active = true; pick(it.y) },
                    onDragEnd = { active = false; last = null },
                    onDragCancel = { active = false; last = null },
                ) { change, _ -> change.consume(); pick(change.position.y) }
            },
        shape = GlassShape.Capsule,
        variant = GlassVariant.Clear,
        highlighted = active,
        role = null,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            letters.forEach { GlassText(it, style = GlassType.Caption, maxLines = 1) }
        }
    }
}

/**
 * Makes a plain (non-glass) element pressable with a soft highlight: used for rows inside a glass
 * card or menu, so only the card is glass.
 */
@Composable
fun Modifier.glassPressable(onClick: () -> Unit, shape: androidx.compose.ui.graphics.Shape = GlassShape.Row, enabled: Boolean = true): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val content = LocalGlassContentColor.current
    val highlight = animateFloatAsState(
        if (pressed && enabled) 0.10f else 0f,
        spring(dampingRatio = 1f, stiffness = 1200f), label = "rowHighlight",
    )
    return this
        .clip(shape)
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .drawBehind { drawRect(content.copy(alpha = highlight.value)) }
}

/** A row of a glass context menu: text and a trailing glyph, no glass of its own. */
@Composable
fun GlassMenuRow(text: String, icon: ImageVector?, destructive: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassPressable(onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (destructive) GlassColors.Red else LocalGlassContentColor.current
        GlassText(text, Modifier.weight(1f), style = GlassType.Body, color = color, maxLines = 2)
        Spacer(Modifier.width(16.dp))
        when {
            trailing != null -> trailing()
            icon != null -> GlassIcon(icon, size = 20.dp, tint = color)
        }
    }
}

/** A single optical surface for a menu; rows share its backdrop and scroll on short screens. */
@Composable
fun GlassMenuPanel(content: @Composable ColumnScope.() -> Unit) {
    val style = LocalGlassStyle.current
    val panel = style.role(SurfaceKind.Panel)
    val menuStyle = remember(style) {
        style.copy(roles = style.roles + (SurfaceKind.Panel to panel.copy(
            // Menus need a firmer material than decorative glass: every action stays legible.
            opacity = maxOf(panel.opacity ?: style.tintOpacity, 0.42f),
            shadow = if (panel.shadow > 0f) panel.shadow else 16f,
        )))
    }
    val scroll = rememberScrollState()
    CompositionLocalProvider(LocalGlassStyle provides menuStyle) {
        GlassSurface(
            Modifier.width(280.dp).heightIn(max = 640.dp)
                .semantics { paneTitle = "Actions"; isTraversalGroup = true },
            shape = GlassShape.rounded(26.dp), variant = GlassVariant.Regular,
            kind = SurfaceKind.Panel, role = null,
        ) {
            val ink = LocalGlassContentColor.current
            Box(Modifier.drawWithContent {
                drawContent()
                if (scroll.maxValue > 0) {
                    val inset = 24.dp.toPx()
                    val track = (size.height - 2f * inset).coerceAtLeast(0f)
                    val thumb = (track * size.height / (size.height + scroll.maxValue)).coerceAtLeast(16.dp.toPx()).coerceAtMost(track)
                    val y = inset + (track - thumb) * scroll.value / scroll.maxValue
                    drawRoundRect(ink.copy(alpha = 0.32f),
                        Offset(size.width - 5.dp.toPx(), y), Size(2.dp.toPx(), thumb), CornerRadius(1.dp.toPx()))
                }
            }) {
                Column(Modifier.verticalScroll(scroll).padding(6.dp), content = content)
            }
        }
    }
}

/** Quiet separation between shortcuts, editing actions and destructive actions. */
@Composable
fun GlassMenuDivider() {
    val color = LocalGlassContentColor.current
    Spacer(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
        .height(0.5.dp).background(color.copy(alpha = 0.12f)))
}
