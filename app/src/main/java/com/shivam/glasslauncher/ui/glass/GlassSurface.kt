package com.shivam.glasslauncher.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.LiquidGlassShader.setGlassUniforms
import kotlin.math.roundToInt

/**
 * The single design primitive of the app: an iOS 26-style Liquid Glass panel.
 *
 * Layers, bottom to top:
 *  1. Base: the compositor's cross-window blur of the wallpaper (enabled per window by
 *     [rememberCrossWindowBlur]; it shows through wherever this surface is translucent).
 *  2. Backdrop: [backdrop] (recorded wallpaper copy and app content) drawn aligned to this panel,
 *     blurred with a RenderEffect.
 *  3. Lens (API 33+): an AGSL [LiquidGlassShader] that refracts the blurred backdrop inside a
 *     superellipse rim, adds chromatic aberration, tint, a tilt-following rim light and sheen.
 *     Below API 33 or if the shader fails, a blur + tint + gradient + 1px highlight fallback.
 *  4. [content], with [LocalGlassContentColor] set for legible text on this glass.
 *
 * Contrast: glass is dark-tinted by default and the text colour ([LocalGlassContentColor]) is
 * picked from the measured luminance of the backdrop mixed with the tint ([Contrast]), so text
 * meets WCAG AA and is never white on light glass. [kind] selects the per-role overrides of the
 * Global appearance editor (tint, opacity, blur, text colour, stroke, accent).
 *
 * @param variant Regular (frosted) for large panels, Clear (transparent, stronger lensing) for small controls.
 * @param onClick makes the glass pressable: pressed glass brightens and swells slightly.
 * @param highlighted brightens and swells the glass without a press (drop targets, selection).
 * @param tintOverride replaces the tint colour (e.g. a green "on" switch track).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape.Panel,
    variant: GlassVariant = LocalGlassStyle.current.variant,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    tintOverride: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    backdrop: Backdrop? = LocalBackdrop.current,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    role: Role? = Role.Button,
    kind: SurfaceKind? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val style = LocalGlassStyle.current
    val res = style.resolve(variant)
    val roleStyle = style.role(kind ?: if (variant == GlassVariant.Clear) SurfaceKind.Cards else SurfaceKind.Panel)
    val blurDp = roleStyle.blur?.let { if (variant == GlassVariant.Clear) it * 0.22f else it }?.dp ?: res.blur
    val tintOpacity = roleStyle.opacity?.let { if (variant == GlassVariant.Clear) it * 0.35f else it } ?: res.tintOpacity
    val light = LocalGlassLight.current
    val darkMode = LocalGlassDarkMode.current
    val fallbackLum = LocalFallbackLuminance.current
    val density = LocalDensity.current

    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if ((pressed && onClick != null) || highlighted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "glassPress",
    )

    val windowPos = remember { mutableStateOf(Offset.Zero) }
    val sizePx = remember { mutableStateOf(IntSize.Zero) }

    // Adaptive tone, quantised so scrolling does not recompose every frame.
    val luminance by remember(backdrop, fallbackLum) {
        derivedStateOf {
            val s = sizePx.value
            val p = windowPos.value
            val l = backdrop?.luminance(Rect(p.x, p.y, p.x + s.width, p.y + s.height)) ?: fallbackLum
            (l * 20f).roundToInt() / 20f
        }
    }
    // Dark-tinted glass by default. Auto only lightens the glass over light backdrops in light
    // mode; the text colour is picked by measured contrast either way (never white on light).
    val toneTarget = when (style.tone) {
        GlassTone.Light -> 1f
        GlassTone.Dark -> 0f
        GlassTone.Auto -> if (!darkMode && tintOverride == null) ((luminance - 0.72f) / 0.12f).coerceIn(0f, 1f) * 0.8f else 0f
    }
    val tone by animateFloatAsState(toneTarget, spring(stiffness = 120f), label = "glassTone")

    val baseTint = tintOverride ?: Color(roleStyle.color ?: style.tintColor).copy(alpha = 1f)
    val tint = if (tintOverride != null) baseTint else lerp(baseTint, lerp(baseTint, Color.White, 0.85f), tone)
    val tintAmount = if (tintOverride != null) 0.55f else (tintOpacity + 0.22f * tone).coerceAtMost(1f)
    val contentColor = roleStyle.contentColor?.let { Color(it) }
        ?: Color(Contrast.textOnGlass(luminance, tint.toArgb(), tintAmount))
    val strokeDp = roleStyle.stroke

    val shader = remember(style.liquidEffects) { if (style.liquidEffects) LiquidGlassShader.createEffectShader() else null }

    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactions,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick ?: {},
        )
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = 1f + 0.035f * press
                scaleX = s
                scaleY = s
            }
            .onGloballyPositioned {
                windowPos.value = it.positionInWindow()
                sizePx.value = it.size
            }
            .then(clickModifier),
        contentAlignment = contentAlignment,
        propagateMinConstraints = propagateMinConstraints,
    ) {
        // Glass body: blurred, refracted backdrop.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    clip = true
                    this.shape = shape
                    val blurPx = blurDp.toPx()
                    // Thin surfaces (rows, pills) get a proportionally smaller lens, so flat rows
                    // never show streaky refraction along their long edges.
                    val minSide = minOf(size.width, size.height)
                    val refractionPx = minOf(res.refraction.toPx(), minSide * 0.08f)
                    val rimPx = minOf(res.rim.toPx(), minSide * 0.3f)
                    val chroma = if (minSide < 64.dp.toPx()) res.chroma * 0.5f else res.chroma
                    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val l = light.value
                        val c = Color(tint.toArgb())
                        shader.setGlassUniforms(
                            width = size.width,
                            height = size.height,
                            radius = shape.radiusPx(size, density),
                            exponent = shape.exponent,
                            rim = rimPx,
                            refraction = refractionPx,
                            chroma = chroma,
                            lightX = l.x,
                            lightY = l.y,
                            specular = res.specular,
                            tintR = c.red,
                            tintG = c.green,
                            tintB = c.blue,
                            tintAmount = tintAmount,
                            brighten = press,
                        )
                        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
                        renderEffect = if (blurPx >= 0.5f) {
                            RenderEffect.createChainEffect(lens, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
                        } else {
                            lens
                        }.asComposeRenderEffect()
                    } else {
                        renderEffect = if (blurPx >= 0.5f) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
                    }
                }
                .drawBehind {
                    // Guarantees the effect has pixels to work on even without a backdrop.
                    drawRect(Color(0x03000000))
                    if (backdrop != null) with(backdrop) { drawBackdrop(windowPos.value) }
                },
        )
        if (shader == null) {
            // Fallback: tint + inner gradient + 1px specular edge.
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        drawOutline(outline, tint.copy(alpha = (tintAmount + 0.06f).coerceAtMost(0.9f)))
                        drawOutline(outline, Color.White.copy(alpha = 0.08f * press))
                        val e = res.specular
                        drawOutline(
                            outline,
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.12f * e),
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.06f * e),
                            ),
                        )
                        val l = light.value
                        val start = Offset(size.width * (0.5f + l.x * 0.5f), size.height * (0.5f + l.y * 0.5f))
                        val end = Offset(size.width * (0.5f - l.x * 0.5f), size.height * (0.5f - l.y * 0.5f))
                        drawOutline(
                            outline,
                            Brush.linearGradient(
                                0f to Color.White.copy(alpha = 0.6f * e),
                                0.5f to Color.White.copy(alpha = 0.08f * e),
                                1f to Color.White.copy(alpha = 0.3f * e),
                                start = start,
                                end = end,
                            ),
                            style = Stroke(width = 1f.coerceAtLeast(density.density * 0.6f)),
                        )
                    },
            )
        }
        if (strokeDp > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        drawOutline(outline, contentColor.copy(alpha = 0.35f), style = Stroke(width = strokeDp.dp.toPx()))
                    },
            )
        }
        val accent = roleStyle.accent?.let { Color(it) }
        if (accent != null) {
            CompositionLocalProvider(LocalGlassContentColor provides contentColor, LocalGlassAccent provides accent) { content() }
        } else {
            CompositionLocalProvider(LocalGlassContentColor provides contentColor) { content() }
        }
    }
}
