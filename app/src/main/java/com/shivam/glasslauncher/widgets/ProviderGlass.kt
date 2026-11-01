package com.shivam.glasslauncher.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/** Per-instance opt-in: provider text/images keep full opacity while compatible card paint softens. */
object ProviderGlass {
    fun enabled(inst: WidgetInstance) = inst[WidgetKeys.PROVIDER_GLASS] == "1"
    fun backgroundOpacity(inst: WidgetInstance): Float =
        inst[WidgetKeys.PROVIDER_BACKGROUND]?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.25f
}

/**
 * Only plain container backgrounds are blended. Never fades text, images, charts or RemoteViews
 * content, never captures pixels, and restores original drawables before provider reapplication.
 */
class GlassAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private data class Paint(val original: Drawable, val blended: Drawable)
    private val paints = IdentityHashMap<View, Paint>()
    private var blend = false
    private var backgroundOpacity = 0.25f
    private val refreshPaint = Runnable { applyBackgrounds(); reportTexts() }

    /** Receives the widget's visible texts, in layout order, after every update (see [ClaudeUsage]). */
    var onTexts: ((List<String>) -> Unit)? = null
        set(value) { field = value; reportTexts() }

    private fun reportTexts() {
        val cb = onTexts ?: return
        val out = ArrayList<String>()
        fun visit(v: View, depth: Int) {
            if (depth > 12) return
            if (v is android.widget.TextView) v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            if (v is ViewGroup) for (i in 0 until v.childCount.coerceAtMost(64)) visit(v.getChildAt(i), depth + 1)
        }
        visit(this, 0)
        if (out.isNotEmpty()) cb(out)
    }

    /**
     * Presses the provider's own refresh control: the top-most small tappable element that shows
     * no text (u.sage's refresh icon; Glance puts the tap on a box around the image). Returns
     * false when there is none. The provider's PendingIntent does the actual work.
     */
    fun clickRefresh(): Boolean {
        fun hasText(v: View): Boolean = when (v) {
            is android.widget.TextView -> !v.text.isNullOrBlank()
            is ViewGroup -> (0 until v.childCount).any { hasText(v.getChildAt(it)) }
            else -> false
        }
        val found = ArrayList<View>()
        fun visit(v: View, depth: Int) {
            if (depth > 14) return
            if (v !== this && v.hasOnClickListeners() && v.width in 1..(width / 3) && v.height in 1..(height / 3) && !hasText(v)) found += v
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i), depth + 1)
        }
        visit(this, 0)
        val loc = IntArray(2)
        val target = found.minByOrNull { v -> v.getLocationInWindow(loc); loc[1] * 4 - loc[0] } ?: return false
        return target.performClick()
    }

    fun setGlassBlend(enabled: Boolean, opacity: Float) {
        if (blend == enabled && backgroundOpacity == opacity) return
        restoreBackgrounds()
        blend = enabled
        backgroundOpacity = opacity
        applyBackgrounds()
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        restoreBackgrounds()
        super.updateAppWidget(remoteViews)
        removeCallbacks(refreshPaint)
        post(refreshPaint)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        applyBackgrounds()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshPaint)
        super.onDetachedFromWindow()
    }

    private fun restoreBackgrounds() {
        paints.forEach { (view, paint) -> if (view.background === paint.blended) view.background = paint.original }
        paints.clear()
    }

    private fun applyBackgrounds() {
        if (!blend || width <= 0 || height <= 0) return
        // Providers commonly wrap one full-size card. Limit traversal; preserve smaller inner cards.
        fun visit(view: View, depth: Int) {
            if (depth > 8 || view !is ViewGroup) return
            val bg = view.background
            val isOuterCard = view.width >= width * 0.8f && view.height >= height * 0.8f
            if (isOuterCard && bg != null && paints[view]?.blended !== bg &&
                (bg is ColorDrawable || bg is GradientDrawable)) {
                val copy = bg.constantState?.newDrawable(view.resources)?.mutate()
                if (copy != null) {
                    copy.alpha = (bg.alpha * backgroundOpacity).roundToInt().coerceIn(0, 255)
                    paints[view] = Paint(bg, copy)
                    view.background = copy
                }
            }
            for (i in 0 until view.childCount.coerceAtMost(64)) visit(view.getChildAt(i), depth + 1)
        }
        for (i in 0 until childCount) visit(getChildAt(i), 0)
    }
}
