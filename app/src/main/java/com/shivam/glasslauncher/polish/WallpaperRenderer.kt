package com.shivam.glasslauncher.polish

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

object WallpaperRenderer {
    /** Caller owns the returned bitmap. Never mutates or recycles the source. */
    fun render(source: Bitmap, width: Int, height: Int, edit: WallpaperEdit): Bitmap {
        val crop = edit.crop(source.width, source.height, width, height)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            setRectToRect(
                android.graphics.RectF(crop.left, crop.top, crop.right, crop.bottom),
                android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), Matrix.ScaleToFit.FILL,
            )
        }
        Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(edit.colorMatrix())
        })
        return output
    }
}
