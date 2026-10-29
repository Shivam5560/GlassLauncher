package com.shivam.glasslauncher.ui.glass

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import com.shivam.glasslauncher.ui.glass.LiquidGlassShader.setGlassUniforms

/**
 * Liquid Glass for plain Views in overlay windows (the gesture service), where no backdrop can be
 * read. Uses the self-contained variant of the same AGSL shader (rim light, sheen, tint, squircle
 * SDF) on a translucent base; falls back to fill + gradient-free 1px rim below API 33.
 */
@SuppressLint("ViewConstructor")
open class GlassShapeView(context: Context) : View(context) {
    private val shader: RuntimeShader? = LiquidGlassShader.createPaintShader()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    /** 0 = dark glass, 1 = light glass. */
    var lightness = 0.85f
        set(value) { field = value; invalidate() }
    var opacity = 0.55f
        set(value) { field = value; invalidate() }
    var brighten = 0f
        set(value) { field = value; invalidate() }
    var specular = 0.8f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Draws a glass capsule/rounded rect at the given bounds within this view. */
    protected fun drawGlass(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, radius: Float, exponent: Float) {
        if (width <= 0f || height <= 0f) return
        val v = lightness
        val base = 0.12f + 0.8f * v
        val s = shader
        if (s != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val a = opacity
            s.setFloatUniform("baseColor", base * a, base * a, base * a, a)
            s.setGlassUniforms(
                width, height, radius, exponent,
                rim = height * 0.45f, refraction = 0f, chroma = 0f,
                lightX = DefaultLight.x, lightY = DefaultLight.y,
                specular = specular, tintR = base, tintG = base, tintB = base,
                tintAmount = 0.15f, brighten = brighten,
            )
            paint.shader = s
            canvas.save()
            canvas.translate(left, top)
            canvas.drawRect(0f, 0f, width, height, paint)
            canvas.restore()
        } else {
            path.reset()
            path.addPath(superellipseRectPath(width, height, radius, exponent, left, top))
            val c = (base * 255).toInt().coerceIn(0, 255)
            paint.shader = null
            paint.color = Color.argb((opacity * 255).toInt(), c, c, c)
            canvas.drawPath(path, paint)
            strokePaint.strokeWidth = resources.displayMetrics.density * 0.8f
            strokePaint.color = Color.argb((140 * specular).toInt(), 255, 255, 255)
            canvas.drawPath(path, strokePaint)
        }
    }
}

/**
 * The home indicator: a slim glass capsule centred in a slightly larger view, with a soft dark
 * halo so it stays visible over light apps, that stretches while the finger pulls it up.
 */
class GlassPillView(context: Context) : GlassShapeView(context) {
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(3f * context.resources.displayMetrics.density, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    /** 0..1 pull of the bottom swipe: the pill widens and thickens a little. */
    var stretch = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val baseW = width - 16f * d
        val w = baseW * (1f + 0.14f * stretch)
        val h = 5f * d * (1f + 0.3f * stretch)
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        canvas.drawRoundRect(left, top, left + w, top + h, h / 2f, h / 2f, halo)
        drawGlass(canvas, left, top, w, h, h / 2f, 2f)
    }
}

/**
 * Back-swipe feedback: a glass bubble that stretches out of the screen edge as the finger moves,
 * with a chevron that appears once the swipe will trigger.
 */
class GlassBubbleView(context: Context, private val fromLeft: Boolean) : GlassShapeView(context) {
    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(230, 30, 30, 34)
    }
    private val chevronPath = Path()

    /** 0..1 progress of the swipe towards the trigger threshold (can exceed 1). */
    var progress = 0f
        set(value) { field = value; invalidate() }
    /** Vertical centre in this view's coordinates. */
    var centerY = 0f
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val p = progress.coerceIn(0f, 1.3f)
        if (p <= 0.01f) return
        val d = resources.displayMetrics.density
        val maxW = 46f * d
        // liquid stretch: grows outwards while getting slightly thinner
        val w = maxW * minOf(p, 1f) + 6f * d * (p - 1f).coerceAtLeast(0f)
        val h = (72f - 14f * minOf(p, 1f)) * d
        val top = centerY - h / 2f
        val left = if (fromLeft) -w * 0.35f else width - w * 0.65f
        brighten = if (p >= 1f) 1f else 0f
        drawGlass(canvas, left, top, w, h, h / 2f, 2.6f)
        if (p > 0.5f) {
            val a = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            chevron.alpha = (230 * a).toInt()
            chevron.strokeWidth = 2.6f * d
            val cx = if (fromLeft) left + w * 0.62f else left + w * 0.38f
            val s = 6f * d
            chevronPath.reset()
            if (fromLeft) {
                chevronPath.moveTo(cx + s * 0.4f, centerY - s)
                chevronPath.lineTo(cx - s * 0.5f, centerY)
                chevronPath.lineTo(cx + s * 0.4f, centerY + s)
            } else {
                chevronPath.moveTo(cx - s * 0.4f, centerY - s)
                chevronPath.lineTo(cx + s * 0.5f, centerY)
                chevronPath.lineTo(cx - s * 0.4f, centerY + s)
            }
            canvas.drawPath(chevronPath, chevron)
        }
    }
}
