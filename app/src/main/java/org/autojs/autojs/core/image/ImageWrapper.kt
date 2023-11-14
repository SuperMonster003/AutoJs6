package org.autojs.autojs.core.image

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import org.autojs.autojs.AutoJs.Companion.instance
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.opencv.Mat
import org.autojs.autojs.core.opencv.OpenCVHelper
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.opencv.android.Utils
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * Created by Stardust on 2017/11/25.
 * Modified by SuperMonster003 as of May 16, 2023.
 * Transformed by SuperMonster003 on May 16, 2023.
 */
class ImageWrapper(bitmap: Bitmap?, mat: Mat?) : Recyclable {

    // @Hint by SuperMonster003
    //  ! It is ensured that bitmap and mat will not be null at the same time.

    private var mMat: Mat? = null
    private var mBitmap: Bitmap? = null

    private var mWidth = 0
    private var mHeight = 0
    private val mScriptRuntime = instance.runtime
    private var mIsRecycled = false
    private var mIsOneShot = false

    constructor(mat: Mat) : this(null, mat)

    constructor(bitmap: Bitmap) : this(bitmap, null)

    constructor(width: Int, height: Int) : this(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

    init {
        if (mat != null) {
            mMat = mat
        }
        if (bitmap != null) {
            mBitmap = bitmap
            mWidth = bitmap.width
            mHeight = bitmap.height
        } else {
            if (mat != null) {
                mWidth = mat.cols()
                mHeight = mat.rows()
            } else {
                throw Exception(str(R.string.error_both_bitmap_and_mat_are_null))
            }
        }
        addToList(this)
    }

    val width
        get() = mWidth.also { ensureNotRecycled() }

    val height
        get() = mHeight.also { ensureNotRecycled() }

    val size
        get() = Size(mWidth.toDouble(), mHeight.toDouble()).also { ensureNotRecycled() }

    val bitmap: Bitmap
        get() {
            ensureNotRecycled()
            if (mBitmap == null && mMat != null) {
                mBitmap = Bitmap.createBitmap(mMat!!.width(), mMat!!.height(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(mMat, mBitmap)
            }
            return mBitmap!!
        }

    val mat: Mat
        get() {
            ensureNotRecycled()
            if (mMat == null && mBitmap != null) {
                mMat = Mat()
                Utils.bitmapToMat(mBitmap, mMat)
            }
            return mMat!!
        }

    private fun addToList(image: Any) {
        imageList.add(WeakReference(image))
    }

    fun saveTo(path: String?) {
        ensureNotRecycled()
        val nicePath = mScriptRuntime.files.path(path)
        if (mBitmap != null) {
            try {
                mBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(nicePath))
            } catch (e: FileNotFoundException) {
                throw UncheckedIOException(e)
            }
        } else {
            Imgcodecs.imwrite(nicePath, mMat)
        }
    }

    fun pixel(x: Int, y: Int): Int {
        ensureNotRecycled()
        val result = if (mBitmap != null) {
            mBitmap!!.getPixel(x, y)
        } else {
            val channels = mat[x, y]
            Color.argb(channels[3].toInt(), channels[0].toInt(), channels[1].toInt(), channels[2].toInt())
        }
        return result
    }

    override fun recycle() {
        mBitmap?.run { recycle().also { mBitmap = null } }
        mMat?.run { OpenCVHelper.release(mMat).also { mMat = null } }
        mIsRecycled = true
    }

    override fun setOneShot(b: Boolean) = also { mIsOneShot = b }

    override fun shoot() {
        if (mIsOneShot) recycle()
    }

    override fun isRecycled() = mIsRecycled

    @ScriptInterface
    fun ensureNotRecycled() {
        check(!isRecycled) { str(R.string.error_image_has_been_recycled) }
    }

    fun clone(): ImageWrapper {
        ensureNotRecycled()
        return if (mBitmap == null) {
            ofMat(mMat!!.clone())!!
        } else if (mMat == null) {
            ofBitmap(mBitmap!!.copy(mBitmap!!.config, true))!!
        } else {
            ImageWrapper(mBitmap!!.copy(mBitmap!!.config, true), mMat!!.clone())
        }
    }

    companion object {

        private val imageList = ArrayList<WeakReference<Any>>()

        @JvmStatic
        @Synchronized
        fun recycleAll() {
            imageList.forEach {
                when (val i = it.get()) {
                    is Recyclable -> i.recycle()
                    is Bitmap -> i.recycle()
                }
            }
            imageList.clear()
        }

        @JvmStatic
        fun ofImage(image: Image?) = image?.let { ImageWrapper(toBitmap(it)) }

        @JvmStatic
        fun ofMat(mat: Mat?) = mat?.let { ImageWrapper(it) }

        @JvmStatic
        fun ofBitmap(bitmap: Bitmap?) = bitmap?.let { ImageWrapper(it) }

        @ScriptInterface
        fun toBitmap(image: Image): Bitmap {
            val plane = image.planes[0]
            val buffer = plane.buffer.apply { position(0) }
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888,
            ).apply { copyPixelsFromBuffer(buffer) }
            return when (rowPadding == 0) {
                true -> bitmap
                else -> Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            }
        }

    }

}