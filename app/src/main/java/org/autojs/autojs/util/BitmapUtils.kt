package org.autojs.autojs.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

/**
 * Created by Stardust on Apr 22, 2017.
 */
object BitmapUtils {

    @JvmStatic
    fun scaleBitmap(origin: Bitmap?, newWidth: Int, newHeight: Int): Bitmap? {
        origin ?: return null

        val height = origin.height
        val width = origin.width
        val matrix = Matrix().apply {
            val scaledWidth = newWidth.toFloat() / width
            val scaledHeight = newHeight.toFloat() / height
            /* 使用后乘. */
            postScale(scaledWidth, scaledHeight)
        }
        return Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false)
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap = when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        else -> createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            .also { drawable.draw(Canvas(it)) }
    }

    @JvmStatic
    fun bitmapToRgba(bmp: Bitmap): ByteArray {
        val w = bmp.getWidth()
        val h = bmp.getHeight()

        require(w > 0 && h > 0) { "bitmap size is 0" }

        val pixelCount = w * h
        val byteCount = pixelCount * 4

        val rgba = ByteArray(byteCount)
        val argb = IntArray(pixelCount)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)

        var i4 = 0
        for (p in argb) {
            rgba[i4++] = ((p shr 16) and 0xFF).toByte() // R
            rgba[i4++] = ((p shr 8) and 0xFF).toByte() // G
            rgba[i4++] = (p and 0xFF).toByte() // B
            rgba[i4++] = ((p shr 24) and 0xFF).toByte() // A
        }
        return rgba
    }

}