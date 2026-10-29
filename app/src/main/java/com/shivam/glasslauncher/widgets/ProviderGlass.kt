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
    private val refreshPaint = Runnable { applyBackgrounds() }

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
