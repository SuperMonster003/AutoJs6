package org.autojs.autojs.core.image

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Color
import android.media.Image
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.opencv.Mat
import org.autojs.autojs.core.opencv.OpenCVHelper
import org.autojs.autojs.rhino.extension.AnyExtensions.toRuntimePath
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Images
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Created by Stardust on Nov 25, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 * Transformed by SuperMonster003 on May 16, 2023.
 *
 * Pixel data conventions (zh-CN: 像素数据约定):
 * - [bitmap] is an ARGB_8888 Android bitmap.
 * - [mat] follows the channel order produced by `Utils.bitmapToMat`, i.e. RGBA (or RGB / GRAY for
 *   images produced by OpenCV based operations), NOT the BGR order of `Imgcodecs.imread`.
 * - zh-CN: [bitmap] 为 ARGB_8888 位图; [mat] 与 `Utils.bitmapToMat` 的通道顺序一致, 即 RGBA
 *   (OpenCV 操作产生的图像也可能是 RGB / 灰度), 而非 `Imgcodecs.imread` 的 BGR 顺序.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 20, 2023.
open class ImageWrapper : Shootable<ImageWrapper> {

    private var mScriptRuntime: ScriptRuntime

    /** The runtime this image belongs to (used by the Auto.js 4 shaped `Images.concat(imgA, imgB, direction)`). */
    val scriptRuntime: ScriptRuntime
        get() = mScriptRuntime

    private var mMat: Mat? = null
    private var mBgrMat: Mat? = null
    private var mBitmap: Bitmap? = null
    private var mMediaImage: Image? = null
    private var mPlane: Image.Plane? = null

    private var mWidth = 0
    private var mHeight = 0

    @Volatile
    private var mIsRecycled = false
    private var mIsOneShot = false

    val width
        get() = mWidth.also { ensureNotRecycled() }

    val height
        get() = mHeight.also { ensureNotRecycled() }

    val size
        get() = Size(mWidth.toDouble(), mHeight.toDouble()).also { ensureNotRecycled() }

    val bitmap: Bitmap
        get() {
            ensureNotRecycled()
            if (mBitmap == null) {
                val mat = mMat
                if (mat != null) {
                    Images.initOpenCvIfNeeded()
                    val bitmap = createBitmap(mat.width(), mat.height()).also { mBitmap = it }
                    Utils.matToBitmap(mat, bitmap)
                } else {
                    mMediaImage?.let { mBitmap = toBitmap(it) }
                }
            }
            return mBitmap ?: throw Exception("Bitmap of ImageWrapper should never be null")
        }

    val mat: Mat
        get() {
            ensureNotRecycled()
            Images.initOpenCvIfNeeded()
            val mat = mMat
            if (mat != null) {
                return mat
            }
            val bitmap = mBitmap
            if (bitmap != null) {
                val newMat = Mat().also { mMat = it }
                Utils.bitmapToMat(bitmap, newMat)
                return newMat
            }
            if (mMediaImage != null) {
                val plane = plane ?: throw AssertionError("Image plain is null")
                plane.buffer.position(0)
                return Mat(mHeight, mWidth, CvType.CV_8UC4, plane.buffer, plane.rowStride.toLong()).also { mMat = it }
            }
            throw AssertionError("Both bitmap and image are null")
        }

    /**
     * The image without its alpha channel (3 channels for color images).
     * Grayscale and 3-channel images are returned as they are.
     *
     * zh-CN: 去除 alpha 通道的图像 (彩色图为 3 通道); 灰度图与 3 通道图原样返回.
     */
    val bgrMat: Mat
        get() {
            ensureNotRecycled()
            Images.initOpenCvIfNeeded()
            mBgrMat?.let { return it }
            val source = mat
            return when (source.channels()) {
                4 -> Mat().also {
                    Imgproc.cvtColor(source, it, Imgproc.COLOR_BGRA2BGR)
                    mBgrMat = it
                }
                else -> source.also { mBgrMat = it }
            }
        }

    var plane: Image.Plane?
        private set(plane) {
            mPlane = plane
        }
        get() = mPlane ?: mMediaImage?.planes?.get(0)

    constructor(scriptRuntime: ScriptRuntime, width: Int, height: Int) : this(scriptRuntime, createBitmap(width, height))

    constructor(scriptRuntime: ScriptRuntime, bitmap: Bitmap) {
        mScriptRuntime = scriptRuntime
        mBitmap = bitmap
        mWidth = bitmap.width
        mHeight = bitmap.height
        register(this)
    }

    constructor(scriptRuntime: ScriptRuntime, mat: Mat) {
        mScriptRuntime = scriptRuntime
        mMat = mat
        mWidth = mat.cols()
        mHeight = mat.rows()
        register(this)
    }

    constructor(scriptRuntime: ScriptRuntime, mat: org.opencv.core.Mat) {
        mScriptRuntime = scriptRuntime
        // Never share the native header with the caller's Mat, whose finalizer would delete it under our feet.
        // zh-CN: 不与调用方的 Mat 共享原生头部, 否则其 finalizer 会在我们仍在使用时删除该头部.
        mMat = Mat.from(mat)
        mWidth = mat.cols()
        mHeight = mat.rows()
        register(this)
    }

    constructor(scriptRuntime: ScriptRuntime, bitmap: Bitmap, mat: Mat?) {
        mScriptRuntime = scriptRuntime
        mMat = mat
        mBitmap = bitmap
        mWidth = bitmap.width
        mHeight = bitmap.height
        register(this)
    }

    constructor(scriptRuntime: ScriptRuntime, mediaImage: Image) {
        mScriptRuntime = scriptRuntime
        mWidth = mediaImage.width
        mHeight = mediaImage.height

        // Detach from ImageReader lifecycle by copying pixels immediately.
        // zh-CN: 通过立即拷贝像素来与 ImageReader 的生命周期解耦.
        mBitmap = toBitmap(mediaImage)

        // Close the original Image ASAP to avoid holding unstable buffers.
        // zh-CN: 尽快关闭原始 Image, 避免持有不稳定的底层 buffer.
        mediaImage.close()

        // Keep media references null after detaching.
        // zh-CN: 解耦后不再保留 media 引用.
        mMediaImage = null
        mPlane = null
        register(this)
    }

    fun saveTo(path: String): Boolean {
        ensureNotRecycled()
        val fullPath = path.toRuntimePath(mScriptRuntime, true)
        val file = File(fullPath)
        val parentFile = file.parentFile
        if (parentFile != null && !parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                throw RuntimeException("Failed to create directory: ${parentFile.absolutePath}")
            }
        }
        if (mBitmap == null) {
            val mat = mMat
            if (mat != null) {
                Images.initOpenCvIfNeeded()
                return imwrite(fullPath, mat)
            }
            bitmap /* Getter, for initializing `mBitmap`. */
        }
        return runCatching { saveWithBitmap(fullPath) }.getOrDefault(false)
    }

    private fun imwrite(fullPath: String, mat: Mat): Boolean {
        // Wrapper Mats use the RGB(A) order of Android bitmaps, whereas imwrite expects BGR(A);
        // writing them directly would swap red and blue in the file.
        // zh-CN: 包装的 Mat 采用 Android 位图的 RGB(A) 通道顺序, 而 imwrite 需要 BGR(A), 直接写入会导致红蓝互换.
        val code = when (mat.channels()) {
            3 -> Imgproc.COLOR_RGB2BGR
            4 -> Imgproc.COLOR_RGBA2BGRA
            else -> return Imgcodecs.imwrite(fullPath, mat)
        }
        val converted = org.opencv.core.Mat()
        try {
            Imgproc.cvtColor(mat, converted, code)
            return Imgcodecs.imwrite(fullPath, converted)
        } finally {
            converted.release()
        }
    }

    private fun saveWithBitmap(fullPath: String): Boolean {
        val bitmap = mBitmap ?: throw Exception("Member \"bitmap\" cannot be null")
        try {
            FileOutputStream(fullPath).use { stream ->
                return bitmap.compress(CompressFormat.PNG, 100, stream)
            }
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    fun pixel(x: Int, y: Int): Int {
        ensureNotRecycled()
        if (x < 0 || y < 0 || x >= mWidth || y >= mHeight) {
            throw ArrayIndexOutOfBoundsException("Point ($x, $y) out of bounds of $this")
        }
        mBitmap?.let { oBitmap ->
            return oBitmap[x, y]
        }
        mMat?.let { oMat ->

            // @Caution by SuperMonster003 on Oct 30, 2024.
            //  ! Not that we should pass (y, x) instead of (x, y) here.
            //  ! Pay attention to the params of `org.opencv.core.Mat.get(row, col)` method,
            //  ! where "row" corresponds "y", and "col" corresponds "x".
            //  ! zh-CN:
            //  ! 这里需要注意不能传入 (x, y), 而需要传入 (y, x).
            //  ! 注意 `org.opencv.core.Mat.get(row, col)` 方法参数, "row" 对应 "y", "col" 对应 "x".
            val channels = oMat.get(/* row = */ y, /* col = */ x) ?: throw Exception("Channel list is null at ($x, $y) of $this")

            // @Hint by SuperMonster003 on Apr 9, 2025.
            //  ! Here we assume that the channels are RGBA (or RGB / GRAY for fewer channels).
            //  ! If mMat does not have these channels, an IndexOutOfBoundsException would be triggered:
            //  # java.lang.IndexOutOfBoundsException: Index: 1, Size: 1
            //  ! Therefore, it is necessary to consider cases with different numbers of channels.
            //  !
            //  ! Refer to: http://issues.autojs6.com/350
            //  !
            //  ! zh-CN:
            //  ! 此处默认通道为 RGBA (通道更少时为 RGB / 灰度), 当 mMat 不是上述通道时, 将触发索引越界异常:
            //  # java.lang.IndexOutOfBoundsException: Index: 1, Size: 1
            //  ! 因此需要考虑不同通道数量的情况.
            //  !
            //  ! 参阅: http://issues.autojs6.com/350

            return when (channels.size) {
                1 -> {
                    // Single-channel image (grayscale/binary/...)
                    // zh-CN: 单通道图像 (灰度图/二值图/...)
                    val value = channels[0].toInt()
                    Color.argb(255, value, value, value)
                }
                3 -> Color.argb(255, channels[0].toInt(), channels[1].toInt(), channels[2].toInt())
                in 4..Int.MAX_VALUE -> Color.argb(channels[3].toInt(), channels[0].toInt(), channels[1].toInt(), channels[2].toInt())
                else -> throw Exception("Unsupported pixel channel count (${channels.size}) at ($x, $y) of $this")
            }
        }
        plane?.let { oPlane ->
            val buffer = oPlane.buffer.apply { position(0) }
            return abgrToArgb(buffer.getInt(oPlane.pixelStride * x + oPlane.rowStride * y))
        }
        throw Exception("At least one of bitmap, mat mad plane must be non-null")
    }

    override fun recycle() {
        synchronized(this) {
            if (mIsRecycled) return
            mBitmap?.let {
                it.recycle()
                mBitmap = null
            }
            val mat = mMat
            val bgrMat = mBgrMat
            mMat = null
            mBgrMat = null
            mat?.let { OpenCVHelper.release(it) }
            if (bgrMat != null && bgrMat !== mat) {
                OpenCVHelper.release(bgrMat)
            }
            mMediaImage?.let {
                it.close()
                mMediaImage = null
                mPlane = null
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
            null -> ofMat(mScriptRuntime, mat.clone())
            else -> ofBitmap(mScriptRuntime, bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true))
        }
    }

    companion object {

        // Images are tracked per script runtime, so that a finishing script only recycles its own images
        // and never the ones still in use by other running scripts.
        // zh-CN: 按脚本运行时分别记录图像, 脚本结束时只回收自己的图像, 不影响其他仍在运行的脚本.
        private val registry = WeakHashMap<ScriptRuntime, MutableList<WeakReference<ImageWrapper>>>()

        private const val REGISTRY_PRUNE_INTERVAL = 256

        private fun register(image: ImageWrapper) {
            synchronized(registry) {
                val list = registry.getOrPut(image.mScriptRuntime) { ArrayList() }
                // Drop references to images already collected by the GC, so long-running capture loops
                // do not accumulate stale entries.
                // zh-CN: 定期清理已被 GC 回收的引用, 避免长时间截图循环累积无效条目.
                if (list.size % REGISTRY_PRUNE_INTERVAL == REGISTRY_PRUNE_INTERVAL - 1) {
                    list.removeAll { it.get() == null }
                }
                list.add(WeakReference(image))
            }
        }

        /**
         * Recycles every image created by the given script runtime which is still reachable.
         * zh-CN: 回收指定脚本运行时创建且仍可达的全部图像.
         */
        @JvmStatic
        fun recycleAll(scriptRuntime: ScriptRuntime) {
            val list = synchronized(registry) { registry.remove(scriptRuntime) } ?: return
            list.forEach { it.get()?.recycle() }
        }

        /**
         * Recycles every tracked image of every script runtime.
         * zh-CN: 回收所有脚本运行时记录的全部图像.
         */
        @JvmStatic
        fun recycleAll() {
            val lists = synchronized(registry) { registry.values.toList().also { registry.clear() } }
            lists.forEach { list -> list.forEach { it.get()?.recycle() } }
        }

        @JvmStatic
        fun ofImage(scriptRuntime: ScriptRuntime, image: Image) = ImageWrapper(scriptRuntime, image)

        @JvmStatic
        fun ofMat(scriptRuntime: ScriptRuntime, mat: Mat) = ImageWrapper(scriptRuntime, mat)

        @JvmStatic
        fun ofMat(scriptRuntime: ScriptRuntime, mat: org.opencv.core.Mat) = ImageWrapper(scriptRuntime, mat)

        @JvmStatic
        fun ofBitmap(scriptRuntime: ScriptRuntime, bitmap: Bitmap) = ImageWrapper(scriptRuntime, bitmap)

        @ScriptInterface
        fun toBitmap(image: Image): Bitmap {
            val plane = image.planes[0]
            val buffer = plane.buffer.apply { position(0) }
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * image.width
            val bitmap = createBitmap(image.width + rowPadding / pixelStride, image.height).apply { copyPixelsFromBuffer(buffer) }
            return when (rowPadding == 0) {
                true -> bitmap
                else -> Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                    bitmap.recycle()
                }
            }
        }

        private fun abgrToArgb(color: Int): Int {
            return Color.argb(color shr 24, color and 0xFF, color shr 8 and 0xFF, color shr 16 and 0xFF)
        }

    }

}
