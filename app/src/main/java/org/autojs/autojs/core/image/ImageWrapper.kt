package org.autojs.autojs.core.image

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Color
import android.media.Image
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.opencv.Mat
import org.autojs.autojs.core.opencv.OpenCVHelper
import org.autojs.autojs.core.ref.MonitorResource
import org.autojs.autojs.core.ref.NativeObjectReference
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.runtime.api.Images
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Stardust on Nov 25, 2017.
 * Modified by SuperMonster003 as of May 16, 2023.
 * Transformed by SuperMonster003 on May 16, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 20, 2023.
open class ImageWrapper : Recyclable, MonitorResource {

    private var mMat: Mat? = null
    private var mBgrMat: Mat? = null
    private var mBitmap: Bitmap? = null
    private var mRef: NativeObjectReference<MonitorResource>? = null
    private var mPlane: Image.Plane? = null

    private var mWidth = 0
    private var mHeight = 0
    private var mIsRecycled = false
    private var mIsOneShot = false

    private var mId = 0L
    private val mNextId = AtomicLong()

    var mediaImage: Image? = null
        private set

    val width
        get() = mWidth.also { ensureNotRecycled() }

    val height
        get() = mHeight.also { ensureNotRecycled() }

    val size
        get() = Size(mWidth.toDouble(), mHeight.toDouble()).also { ensureNotRecycled() }

    val bitmap by lazy {
        ensureNotRecycled()
        if (mBitmap == null) {
            if (mMat != null) {
                mBitmap = Bitmap.createBitmap(mMat!!.width(), mMat!!.height(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(mMat, mBitmap)
            } else {
                mBitmap = mediaImage?.let { toBitmap(it) }
            }
        }
        return@lazy mBitmap ?: throw Exception("Bitmap of ImageWrapper should never be null")
    }

    val mat by lazy {
        ensureNotRecycled()
        if (mMat != null) {
            return@lazy mMat!!
        }
        if (mBitmap != null) {
            mMat = Mat()
            Utils.bitmapToMat(mBitmap, mMat)
            return@lazy mMat!!
        }
        if (mediaImage != null) {
            val plane = plane ?: throw AssertionError("Image plain is null")
            plane.buffer.position(0)
            return@lazy Mat(mHeight, mWidth, CvType.CV_8UC4, plane.buffer, plane.rowStride.toLong()).also { mMat = it }
        }
        throw AssertionError("Both bitmap and image are null")
    }

    val bgrMat
        get() = Mat().also {
            Imgproc.cvtColor(mat, it, Imgproc.COLOR_BGRA2BGR)
            mBgrMat = it
        }

    var plane: Image.Plane?
        private set(plane) {
            mPlane = plane
        }
        get() = mPlane ?: mediaImage?.planes?.get(0)

    constructor(width: Int, height: Int) : this(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

    constructor(bitmap: Bitmap) {
        mId = mNextId.incrementAndGet()
        mBitmap = bitmap.also { addToList(it) }
        mWidth = bitmap.width
        mHeight = bitmap.height
    }

    constructor(mat: Mat) {
        mId = mNextId.incrementAndGet()
        mMat = mat.also { addToList(it) }
        mWidth = mat.cols()
        mHeight = mat.rows()
    }

    constructor(mat: org.opencv.core.Mat) {
        mId = mNextId.incrementAndGet()
        mMat = when (mat.nativeObj != 0L) {
            true -> Mat(mat.nativeObj)
            else -> Mat(mat.rows(), mat.cols(), mat.type())
        }.also { addToList(it) }
        mWidth = mat.cols()
        mHeight = mat.rows()
    }

    constructor(bitmap: Bitmap, mat: Mat?) {
        mId = mNextId.incrementAndGet()
        mMat = mat?.also { addToList(it) }
        mBitmap = bitmap.also { addToList(it) }
        mWidth = bitmap.width
        mHeight = bitmap.height
    }

    constructor(mediaImage: Image) {
        mId = mNextId.incrementAndGet()
        this.mediaImage = mediaImage.also { addToList(it) }
        mWidth = mediaImage.width
        mHeight = mediaImage.height
    }

    init {
        Images.initOpenCvIfNeeded()
    }

    private fun addToList(image: Any) {
        imageList.add(WeakReference(image))
    }

    fun saveTo(path: String?): Boolean {
        ensureNotRecycled()
        path ?: return false
        if (mBitmap == null) {
            if (mMat != null) {
                return Imgcodecs.imwrite(path, mMat)
            }
            bitmap /* Getter, for initializing `mBitmap`. */
        }
        return try {
            true.also { saveWithBitmap(path) }
        } catch (e: Exception) {
            false
        }
    }

    private fun saveWithBitmap(path: String?) {
        try {
            path ?: throw Exception("Argument \"path\" cannot be null")
            mBitmap ?: throw Exception("Member \"bitmap\" cannot be null")
            mBitmap!!.compress(CompressFormat.PNG, 100, FileOutputStream(path))
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    fun pixel(x: Int, y: Int): Int {
        ensureNotRecycled()
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw ArrayIndexOutOfBoundsException("Point ($x, $y) out of bounds of $this")
        }
        mBitmap?.let { oBitmap ->
            return oBitmap.getPixel(x, y)
        }
        mMat?.let { oMat ->

            // @Caution by SuperMonster003 on Oct 30, 2024.
            //  ! Not that we should pass (y, x) instead of (x, y) here.
            //  ! Pay attention to the params of `org.opencv.core.Mat.get(row, col)` method,
            //  ! where "row" corresponds "y", and "col" corresponds "x".
            //  ! zh-CN:
            //  ! 这里需要注意不能传入 (x, y), 而需要传入 (y, x).
            //  ! 注意 `org.opencv.core.Mat.get(row, col)` 方法参数, "row" 对应 "y", "col" 对应 "x".
            val pixelList = oMat.get(/* row = */ y, /* col = */ x) ?: throw Exception("Channel list is null at ($x, $y) of $this")

            val (r, g, b, a) = pixelList.map { pixelValue -> pixelValue.toInt() }
            return Color.argb(a, r, g, b)
        }
        plane?.let { oPlane ->
            val buffer = oPlane.buffer.apply { position(0) }
            return abgrToArgb(buffer.getInt(oPlane.pixelStride * x + oPlane.rowStride * y))
        }
        throw Exception("At least one of bitmap, mat mad plane must be non-null")
    }

    override fun recycle() {
        synchronized(this) {
            mBitmap?.let {
                it.recycle()
                mBitmap = null
            }
            mMat?.let {
                OpenCVHelper.release(it)
                mMat = null
            }
            mBgrMat?.let {
                OpenCVHelper.release(it)
                mBgrMat = null
            }
            mediaImage?.let {
                it.close()
                mediaImage = null
            }
            mRef?.let {
                it.pointer = 0L
            }
            mIsRecycled = true
        }
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
        return when (val bitmap = mBitmap) {
            null -> ofMat(mat.clone())
            else -> ofBitmap(bitmap.copy(bitmap.config, true))
        }
    }

    override fun getPointer() = mId

    override fun setNativeObjectReference(reference: NativeObjectReference<MonitorResource>) {
        mRef = reference
    }

    companion object {

        private val imageList = ArrayList<WeakReference<Any>>()

        @JvmStatic
        @Synchronized
        fun recycleAll() {
            imageList.forEach {
                when (val o = it.get()) {
                    is Recyclable -> o.recycle()
                    is Bitmap -> o.recycle()
                    is org.opencv.core.Mat -> OpenCVHelper.release(o)
                    is Image -> o.close()
                }
            }
            imageList.clear()
        }

        @JvmStatic
        fun ofImage(image: Image) = ImageWrapper(image)

        @JvmStatic
        fun ofMat(mat: Mat) = ImageWrapper(mat)

        @JvmStatic
        fun ofMat(mat: org.opencv.core.Mat) = ImageWrapper(mat)

        @JvmStatic
        fun ofBitmap(bitmap: Bitmap) = ImageWrapper(bitmap)

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

        private fun abgrToArgb(color: Int): Int {
            return Color.argb(color shr 24, color and 0xFF, color shr 8 and 0xFF, color shr 16 and 0xFF)
        }

    }

}