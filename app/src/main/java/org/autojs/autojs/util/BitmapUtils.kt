package org.autojs.autojs.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.net.URL

/**
 * Created by Stardust on Apr 22, 2017.
 * Modified by SuperMonster003 as of Jan 21, 2023.
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

    @JvmStatic
    @Throws(DecodeException::class)
    fun bitmapFromByteArrayOrThrow(bytes: ByteArray): Bitmap {
        return bitmapFromByteArray(bytes) ?: throw DecodeException("Failed to decode bitmap from bytes")
    }

    @JvmStatic
    fun bitmapFromByteArray(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Decode and downsample based on inSampleSize (zh-CN: 解码并按 inSampleSize 降采样)
     *
     * @param data      Compressed image byte stream (zh-CN: 压缩格式图片字节流)
     * @param reqWidth  Target max width (zh-CN: 目标最大宽)
     * @param reqHeight Target max height (zh-CN: 目标最大高)
     * @param withAlpha Whether to keep alpha channel; default true=ARGB_8888, false=RGB_565.<br>
     *                  zh-CN: 是否保留透明通道; 默认 true=ARGB_8888, false=RGB_565.
     */
    @JvmStatic
    @JvmOverloads
    fun downsample(
        data: ByteArray,
        reqWidth: Int,
        reqHeight: Int,
        withAlpha: Boolean = true,
    ): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)

        opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight, reqWidth, reqHeight)
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = if (withAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        if (!withAlpha) bmp?.setHasAlpha(false)
        return bmp
    }

    @JvmStatic
    @JvmOverloads
    fun downsample(
        filePath: String,
        reqWidth: Int,
        reqHeight: Int,
        withAlpha: Boolean = true,
    ): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, opts)

        opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight, reqWidth, reqHeight)
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = if (withAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

        val bmp = BitmapFactory.decodeFile(filePath, opts)
        if (!withAlpha) bmp?.setHasAlpha(false)
        return bmp
    }

    @JvmStatic
    @JvmOverloads
    fun downsample(
        url: URL,
        reqWidth: Int,
        reqHeight: Int,
        withAlpha: Boolean = true,
    ): Bitmap? = runCatching {
        // Need to run in coroutine or thread in production to avoid blocking main thread.
        // zh-CN: 生产环境需放到协程或线程中, 避免阻塞主线程.
        val bytes = url.openStream().use { it.readBytes() }
        downsample(bytes, reqWidth, reqHeight, withAlpha)
    }.getOrNull()

    @JvmStatic
    @JvmOverloads
    fun downsample(
        src: Bitmap,
        reqWidth: Int,
        reqHeight: Int,
        withAlpha: Boolean = true,
    ): Bitmap {
        // Already a Bitmap, cannot use inSampleSize, can only scale proportionally.
        // zh-CN: 已经是 Bitmap, 无法再用 inSampleSize, 只能等比缩放.
        val scale = calcScale(src.width, src.height, reqWidth, reqHeight)
        val dstW = (src.width / scale).coerceAtLeast(1)
        val dstH = (src.height / scale).coerceAtLeast(1)

        val scaled = src.scale(dstW, dstH)

        // If need to adjust pixel format / alpha channel.
        // zh-CN: 如需调整像素格式 / 透明通道.
        return when (withAlpha) {
            scaled.hasAlpha() -> scaled
            else -> scaled.copy(
                if (withAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565,
                /* isMutable = */ false,
            ).apply { if (!withAlpha) setHasAlpha(false) }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun downsample(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int,
        withAlpha: Boolean = true,
    ): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return@runCatching null

        opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight, reqWidth, reqHeight)
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = if (withAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (!withAlpha) bmp?.setHasAlpha(false)
        bmp
    }.getOrNull()

    private fun calcScale(
        srcW: Int, srcH: Int,
        dstW: Int, dstH: Int,
    ): Int {
        var scale = 1
        if (srcH > dstH || srcW > dstW) {
            while ((srcH / scale) > dstH || (srcW / scale) > dstW) {
                scale++
            }
        }
        return scale
    }

    @JvmStatic
    fun computeSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {

        var inSampleSize = 1

        if (outHeight > reqHeight || outWidth > reqWidth) {
            val halfHeight = outHeight / 2
            val halfWidth = outWidth / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            // zh-CN: 计算满足所需高度和宽度的2的幂次方的最大采样率.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    class DecodeException(message: String) : Exception(message)

}