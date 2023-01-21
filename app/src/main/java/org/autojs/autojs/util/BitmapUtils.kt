package org.autojs.autojs.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Created by Stardust on 2017/4/22.
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
        else -> Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            .also { drawable.draw(Canvas(it)) }
    }

}