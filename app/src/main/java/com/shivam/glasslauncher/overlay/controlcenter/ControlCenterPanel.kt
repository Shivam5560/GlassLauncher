package com.shivam.glasslauncher.overlay.controlcenter

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shivam.glasslauncher.overlay.OverlayIcons
import com.shivam.glasslauncher.overlay.OverlayPrefs
import com.shivam.glasslauncher.overlay.PanelHost
import com.shivam.glasslauncher.overlay.notifications.MediaHub
import com.shivam.glasslauncher.overlay.notifications.MediaInfo
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import kotlin.math.roundToInt

private val Gap = 14.dp

/** Drag-to-reorder state for edit mode (grid coordinates in px). */
private class CcDrag {
    var id by mutableStateOf<CcModuleId?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var grab by mutableStateOf(Offset.Zero)
    /** Last module swapped with, so hovering over it doesn't swap back and forth. */
    var lastOver: CcModuleId? = null
}

/**
 * iOS 26-style Control Center: a grid of glass modules the user can rearrange (Edit: drag to
 * reorder, tap to resize 1×1 / 2×1 / 2×2, remove, add). Long-press anywhere opens the full
 * HyperOS quick settings.
 */
@Composable
fun ControlCenterPanel(host: PanelHost, controls: SystemControls, progress: Float) {
    val context = host.context
    val settings by OverlayPrefs.state(context).collectAsState()
    val state by controls.state.collectAsState()
    val media by MediaHub.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf(settings.ccLayout) }
    if (!editing && layout != settings.ccLayout) layout = settings.ccLayout
    val drag = remember { CcDrag() }

    DisposableEffect(Unit) {
        controls.start()
        onDispose { controls.stop() }
    }

    fun save(l: List<CcModule>) {
        layout = l
        OverlayPrefs.setLayout(context, l)
    }

    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val wiggleAngle by wiggle.animateFloat(-1.4f, 1.4f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "wiggleAngle")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * progress))
            .pointerInput(editing) {
                detectTapGestures(
                    onTap = { if (editing) editing = false else host.close() },
                    onLongPress = { if (!editing) host.openSystemQuickSettings() },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    if (dy < -24f) { change.consume(); host.close() }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = progress.coerceIn(0f, 1f)
                    val s = 0.9f + 0.1f * progress
                    scaleX = s
                    scaleY = s
                    translationY = -(1f - progress) * 60.dp.toPx()
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 44.dp, bottom = 40.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(GlassIcons.Bell, onClick = { host.switchPanel() }, size = 38.dp)
                Spacer(Modifier.weight(1f))
                if (editing) {
                    GlassButton("Done", onClick = { editing = false }, prominent = true)
                } else {
                    GlassIconButton(GlassIcons.Pencil, onClick = { editing = true }, size = 38.dp)
                }
            }
            Spacer(Modifier.height(18.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val cols = ControlCenterLayout.COLUMNS
                val cell = (maxWidth - Gap * (cols - 1)) / cols
                val cellPx = with(density) { cell.toPx() }
                val gapPx = with(density) { Gap.toPx() }
                val placements = ControlCenterLayout.pack(layout)
                val rows = ControlCenterLayout.rows(placements)
                val latestPlacements by rememberUpdatedState(placements)
                val latestLayout by rememberUpdatedState(layout)

                fun topLeft(p: CcPlacement) = Offset(p.col * (cellPx + gapPx), p.row * (cellPx + gapPx))

                Box(Modifier.fillMaxWidth().height(if (rows == 0) 0.dp else cell * rows + Gap * (rows - 1))) {
                    placements.forEach { p ->
                        key(p.module.id) {
                            val dragging = drag.id == p.module.id
                            val target = topLeft(p)
                            val animated by animateIntOffsetAsState(
                                IntOffset(target.x.roundToInt(), target.y.roundToInt()),
                                spring(dampingRatio = 0.7f, stiffness = 380f),
                                label = "ccPos",
                            )
                            val w = cell * p.module.size.w + Gap * (p.module.size.w - 1)
                            val h = cell * p.module.size.h + Gap * (p.module.size.h - 1)
                            Box(
                                Modifier
                                    .zIndex(if (dragging) 1f else 0f)
                                    .offset {
                                        if (dragging) {
                                            val o = drag.pointer - drag.grab
                                            IntOffset(o.x.roundToInt(), o.y.roundToInt())
                                        } else animated
                                    }
                                    .size(w, h)
                                    .graphicsLayer {
                                        rotationZ = if (editing && !dragging) wiggleAngle * (if (p.module.id.ordinal % 2 == 0) 1f else -1f) else 0f
                                        val s = if (dragging) 1.06f else 1f
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .pointerInput(editing, p.module.id) {
                                        if (!editing) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = start@{ local ->
                                                val me = latestPlacements.firstOrNull { it.module.id == p.module.id } ?: return@start
                                                drag.lastOver = null
                                                drag.grab = local
                                                drag.pointer = topLeft(me) + local
                                                drag.id = p.module.id
                                            },
                                            onDragEnd = {
                                                drag.id = null
                                                OverlayPrefs.setLayout(context, latestLayout)
                                            },
                                            onDragCancel = { drag.id = null },
                                        ) { change, amount ->
                                            change.consume()
                                            drag.pointer += amount
                                            val pt = drag.pointer
                                            val over = latestPlacements.firstOrNull { q ->
                                                q.module.id != p.module.id && run {
                                                    val tl = topLeft(q)
                                                    val qw = q.module.size.w * cellPx + (q.module.size.w - 1) * gapPx
                                                    val qh = q.module.size.h * cellPx + (q.module.size.h - 1) * gapPx
                                                    pt.x in tl.x..(tl.x + qw) && pt.y in tl.y..(tl.y + qh)
                                                }
                                            }
                                            if (over == null) {
                                                drag.lastOver = null
                                            } else if (over.module.id != drag.lastOver) {
                                                drag.lastOver = over.module.id
                                                val from = latestLayout.indexOfFirst { it.id == p.module.id }
                                                val to = latestLayout.indexOfFirst { it.id == over.module.id }
                                                layout = ControlCenterLayout.move(latestLayout, from, to)
                                            }
                                        }
                                    },
                            ) {
                                ModuleView(
                                    module = p.module,
                                    state = state,
                                    media = media,
                                    editing = editing,
                                    host = host,
                                    controls = controls,
                                    writeAsked = settings.writeSettingsAsked,
                                    onResize = { save(layout.map { if (it.id == p.module.id) ControlCenterLayout.cycleSize(it) else it }) },
                                )
                                if (editing) {
                                    GlassIconButton(
                                        GlassIcons.Minus,
                                        onClick = { save(layout.filterNot { it.id == p.module.id }) },
                                        modifier = Modifier.offset(x = (-6).dp, y = (-6).dp),
                                        size = 24.dp,
                                        tint = Color(0xFF8E8E93),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (editing) {
                Spacer(Modifier.height(22.dp))
                val missing = ControlCenterLayout.missing(layout)
                GlassListGroup(title = "Add controls", footer = "Drag to reorder. Tap a control to change its size (1×1, 2×1, 2×2; sliders 1×2).") {
                    if (missing.isEmpty()) GlassRow("Every control is in use")
                    missing.forEach { id ->
                        GlassRow(id.title, icon = iconFor(id, state), iconTint = GlassColors.Accent, onClick = { save(layout + CcModule(id, id.defaultSize)) }, showChevron = false) {
                            GlassIcon(GlassIcons.Plus, size = 18.dp, tint = GlassColors.Green)
                        }
                    }
                    GlassRow("Reset to default layout", icon = GlassIcons.Grid, iconTint = Color(0xFF8E8E93), onClick = { save(ControlCenterLayout.DEFAULT) }, showChevron = false)
                }
            }

            Spacer(Modifier.height(18.dp))
            GlassText(
                "Wi-Fi, Bluetooth, mobile data and airplane mode open system panels: Android doesn't let apps switch radios directly. " +
                    "Long-press anywhere for HyperOS quick settings. A pull from the middle of the status bar may still open the HyperOS shade.",
                Modifier.padding(horizontal = 6.dp),
                style = GlassType.Footnote,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

private fun iconFor(id: CcModuleId, s: ControlState): ImageVector = when (id) {
    CcModuleId.Wifi -> OverlayIcons.Wifi
    CcModuleId.Bluetooth -> OverlayIcons.Bluetooth
    CcModuleId.MobileData -> OverlayIcons.Cellular
    CcModuleId.Airplane -> OverlayIcons.Airplane
    CcModuleId.Music -> OverlayIcons.Note
    CcModuleId.Brightness -> OverlayIcons.Sun
    CcModuleId.Volume -> OverlayIcons.Speaker
    CcModuleId.Flashlight -> OverlayIcons.Flashlight
    CcModuleId.DoNotDisturb -> OverlayIcons.Moon
    CcModuleId.RotationLock -> OverlayIcons.RotationLock
    CcModuleId.ScreenTimeout -> OverlayIcons.Clock
    CcModuleId.Camera -> OverlayIcons.Camera
    CcModuleId.Timer -> OverlayIcons.Timer
    CcModuleId.Calculator -> GlassIcons.Calculator
    CcModuleId.QrScan -> OverlayIcons.Qr
}

@Composable
private fun ModuleView(
    module: CcModule,
    state: ControlState,
    media: MediaInfo?,
    editing: Boolean,
    host: PanelHost,
    controls: SystemControls,
    writeAsked: Boolean,
    onResize: () -> Unit,
) {
    val context = host.context
    val longPress: (() -> Unit)? = if (editing) null else ({ host.openSystemQuickSettings() })
    fun needsWrite(): Boolean {
        if (state.canWriteSettings) return false
        // Prompted once; afterwards the module says what's missing and System check offers the fix.
        if (!writeAsked) {
            OverlayPrefs.setWriteSettingsAsked(context)
            host.launch(controls.writeSettingsIntent())
        }
        return true
    }
    val id = module.id
    val icon = iconFor(id, state)
    when (id) {
        CcModuleId.Music -> MusicModule(module.size, media, editing, onResize, longPress, host)
        CcModuleId.Brightness -> SliderModule(
            icon = icon,
            value = state.brightness,
            label = when {
                !state.canWriteSettings -> "Allow"
                state.autoBrightness -> "Auto"
                else -> null
            },
            editing = editing,
            onResize = onResize,
            onLongClick = longPress,
            onTapLocked = { if (!state.canWriteSettings) host.launch(controls.writeSettingsIntent()) },
            onChange = { v -> if (!needsWrite()) controls.setBrightness(v) },
        )
        CcModuleId.Volume -> SliderModule(
            icon = icon, value = state.volume, label = null, editing = editing, onResize = onResize, onLongClick = longPress,
            onTapLocked = {}, onChange = { v -> controls.setVolume(v) },
        )
        else -> {
            val (on, subtitle, tint) = when (id) {
                CcModuleId.Wifi -> Triple(state.wifi, (if (state.wifi) "On" else "Off") + " · opens panel", GlassColors.Accent)
                CcModuleId.Bluetooth -> Triple(state.bluetooth, (if (state.bluetooth) "On" else "Off") + " · opens settings", GlassColors.Accent)
                CcModuleId.MobileData -> Triple(state.mobileData, (if (state.mobileData) "On" else "Off") + " · opens panel", GlassColors.Green)
                CcModuleId.Airplane -> Triple(state.airplane, (if (state.airplane) "On" else "Off") + " · opens settings", GlassColors.Orange)
                CcModuleId.Flashlight -> Triple(state.torch, if (!state.torchAvailable) "No flash" else if (state.torch) "On" else "Off", Color.White)
                CcModuleId.DoNotDisturb -> Triple(state.dnd, if (state.dnd) "On" else "Off", Color(0xFF5E5CE6))
                CcModuleId.RotationLock -> Triple(state.rotationLocked, if (!state.canWriteSettings) "Needs permission" else if (state.rotationLocked) "Locked" else "Unlocked", GlassColors.Red)
                CcModuleId.ScreenTimeout -> Triple(false, if (!state.canWriteSettings) "Needs permission" else SystemControls.timeoutLabel(state.timeoutMs), GlassColors.Accent)
                else -> Triple(false, "", GlassColors.Accent)
            }
            val act: () -> Unit = {
                when (id) {
                        CcModuleId.Wifi -> host.launch(controls.wifiPanel())
                        CcModuleId.MobileData -> host.launch(controls.internetPanel())
                        CcModuleId.Bluetooth -> host.launch(controls.bluetoothSettings())
                        CcModuleId.Airplane -> host.launch(controls.airplaneSettings())
                        CcModuleId.Flashlight -> controls.toggleTorch()
                        CcModuleId.DoNotDisturb -> if (!controls.toggleDnd()) host.launch(controls.dndAccessIntent())
                        CcModuleId.RotationLock -> if (!needsWrite()) controls.toggleRotationLock()
                        CcModuleId.ScreenTimeout -> if (!needsWrite()) controls.cycleTimeout()
                        CcModuleId.Camera -> host.launch(controls.cameraIntent())
                        CcModuleId.Timer -> host.launch(controls.timerIntent())
                        CcModuleId.Calculator -> host.launch(controls.calculatorIntent())
                        CcModuleId.QrScan -> host.launch(controls.qrIntent())
                        else -> Unit
                    }
                    Unit
                }
            ToggleModule(
                icon = icon,
                title = id.title,
                subtitle = subtitle.ifBlank { null },
                on = on,
                onTint = tint,
                size = module.size,
                onClick = if (editing) onResize else act,
                onLongClick = longPress,
            )
        }
    }
}

@Composable
private fun ToggleModule(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    on: Boolean,
    onTint: Color,
    size: CcSize,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val shape = if (size == CcSize.Small) GlassShape.Circle else GlassShape.rounded(30.dp)
    val glyph = if (on && onTint == Color.White) Color(0xFF16161A) else if (on) Color.White else LocalGlassContentColor.current
    GlassSurface(
        Modifier.fillMaxSize(),
        shape = shape,
        variant = if (size == CcSize.Small) GlassVariant.Clear else GlassVariant.Regular,
        onClick = onClick,
        onLongClick = onLongClick,
        tintOverride = if (on) onTint else null,
        contentAlignment = Alignment.Center,
    ) {
        when (size) {
            CcSize.Small -> GlassIcon(icon, size = 26.dp, tint = glyph)
            CcSize.Wide -> Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(icon, size = 24.dp, tint = glyph)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    GlassText(title, style = GlassType.Subhead, color = glyph, maxLines = 1)
                    if (subtitle != null) GlassText(subtitle, style = GlassType.Caption, color = glyph.copy(alpha = 0.7f), maxLines = 2)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                GlassIcon(icon, size = 30.dp, tint = glyph)
                Spacer(Modifier.weight(1f))
                GlassText(title, style = GlassType.Headline, color = glyph, maxLines = 2)
                if (subtitle != null) GlassText(subtitle, style = GlassType.Footnote, color = glyph.copy(alpha = 0.7f), maxLines = 2)
            }
        }
    }
}

/** Vertical glass slider: the module itself fills from the bottom, like iOS. */
@Composable
private fun SliderModule(
    icon: ImageVector,
    value: Float,
    label: String?,
    editing: Boolean,
    onResize: () -> Unit,
    onLongClick: (() -> Unit)?,
    onTapLocked: () -> Unit,
    onChange: (Float) -> Unit,
) {
    var local by remember { mutableStateOf<Float?>(null) }
    val shown = local ?: value
    val change by rememberUpdatedState(onChange)
    GlassSurface(
        Modifier.fillMaxSize(),
        shape = GlassShape.rounded(30.dp),
        variant = GlassVariant.Regular,
        onClick = if (editing) onResize else onTapLocked,
        onLongClick = onLongClick,
        role = null,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(GlassShape.rounded(30.dp))
                .pointerInput(editing) {
                    if (editing) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { local = value },
                        onDragEnd = { local = null },
                        onDragCancel = { local = null },
                    ) { c, dy ->
                        c.consume()
                        val next = ((local ?: value) - dy / size.height).coerceIn(0f, 1f)
                        local = next
                        change(next)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height * shown.coerceIn(0f, 1f)
                drawRect(Color.White.copy(alpha = 0.92f), topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (label != null) GlassText(label, style = GlassType.Caption, color = if (shown > 0.2f) Color(0xFF16161A) else Color.White, maxLines = 1)
                GlassIcon(icon, size = 24.dp, tint = if (shown > 0.12f) Color(0xFF16161A) else Color.White)
            }
        }
    }
}

@Composable
private fun MusicModule(
    size: CcSize,
    media: MediaInfo?,
    editing: Boolean,
    onResize: () -> Unit,
    onLongClick: (() -> Unit)?,
    host: PanelHost,
) {
    val playing = media?.playing == true
    val open: () -> Unit = { if (MediaHub.openPlayer(host.context)) host.close() }
    GlassSurface(
        Modifier.fillMaxSize(),
        shape = if (size == CcSize.Small) GlassShape.Circle else GlassShape.rounded(30.dp),
        variant = GlassVariant.Regular,
        onClick = if (editing) onResize else open,
        onLongClick = onLongClick,
        contentAlignment = Alignment.Center,
    ) {
        val playIcon = if (playing) OverlayIcons.Pause else OverlayIcons.Play
        when (size) {
            CcSize.Small -> GlassIconButton(playIcon, onClick = { MediaHub.toggle() }, size = 44.dp)
            CcSize.Wide -> Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(media, 40.dp)
                Spacer(Modifier.width(10.dp))
                GlassText(media?.title?.ifBlank { null } ?: "Not playing", Modifier.weight(1f), style = GlassType.Subhead, maxLines = 2)
                GlassIconButton(playIcon, onClick = { MediaHub.toggle() }, size = 36.dp)
            }
            else -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(media, 48.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        GlassText(media?.title?.ifBlank { null } ?: "Not playing", style = GlassType.Subhead, maxLines = 2)
                        if (!media?.artist.isNullOrBlank()) GlassText(media?.artist.orEmpty(), style = GlassType.Caption, secondary = true, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(OverlayIcons.Previous, onClick = { MediaHub.previous() }, size = 36.dp)
                    GlassIconButton(playIcon, onClick = { MediaHub.toggle() }, size = 44.dp)
                    GlassIconButton(OverlayIcons.Next, onClick = { MediaHub.next() }, size = 36.dp)
                }
            }
        }
    }
}

@Composable
private fun Art(media: MediaInfo?, size: Dp) {
    val art = media?.art
    val bmp = remember(art) { art?.asImageBitmap() }
    if (bmp != null) {
        Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(GlassShape.rounded(size * 0.22f)))
    } else {
        GlassSurface(Modifier.size(size), shape = GlassShape.rounded(size * 0.22f), variant = GlassVariant.Clear, contentAlignment = Alignment.Center, role = null) {
            GlassIcon(OverlayIcons.Note, size = size * 0.55f)
        }
    }
}
