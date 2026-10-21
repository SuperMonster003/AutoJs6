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
import org.autojs.autojs.rhino.AutoJsContext
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
import java.util.Collections
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
 *
 * Lifecycle (docs/memory/ROADMAP.md P3.2; zh-CN: 生命周期):
 * - Every constructor (and so every factory: [ofBitmap], [ofMat], [ofImage], [clone], the images module) registers
 *   the new image under its script runtime; [recycleAll] recycles the images still reachable when that runtime exits.
 *   Images that became unreachable earlier are freed by the GC (bitmap pixels through the bitmap's native allocation
 *   registry, the OpenCV data through the finalizer of [Mat]), so recycling returns memory early but is not needed
 *   for correctness.
 * - [recycle] frees everything the image holds: the bitmap, the mat and the BGR mat derived from it, and a media
 *   image (with its plane) if one is kept. A media image passed to the constructor is copied and closed at once,
 *   so an ImageWrapper never keeps an ImageReader buffer and cannot exhaust the reader.
 * - [setOneShot] marks the image to be recycled after its next use by an images function (see [shoot]); [clone]
 *   does not copy the mark.
 * - [clone] always copies the pixels (a bitmap copy, or a mat clone when there is no bitmap); the copy belongs to the
 *   same script runtime and is independent of the original, recycling either one leaves the other intact.
 * - The image keeps only a weak reference to its runtime, so an image that escapes its script (for example into a
 *   process-wide cache) cannot keep the whole runtime alive after the script has ended (roadmap H11). Such an image
 *   was reachable when its runtime exited and has therefore been recycled by [recycleAll].
 * - zh-CN: 每个构造方法 (因而每个工厂: [ofBitmap], [ofMat], [ofImage], [clone], images 模块) 都把新图像登记到其脚本运行时下;
 *   该运行时退出时 [recycleAll] 回收仍可达的图像. 更早变为不可达的图像由 GC 释放 (位图像素经位图的原生分配登记, OpenCV 数据经 [Mat] 的 finalizer),
 *   因此回收用于尽早归还内存, 并非正确性所必需.
 *   [recycle] 释放图像持有的全部资源: 位图, mat 及由其派生的 BGR mat, 以及保留的 media image (连同其 plane). 传给构造方法的 media image 会被立即拷贝并关闭,
 *   因此 ImageWrapper 从不保留 ImageReader 的缓冲区, 也不会耗尽 ImageReader.
 *   [setOneShot] 标记图像在下一次被 images 函数使用后回收 (见 [shoot]); [clone] 不复制该标记.
 *   [clone] 总是拷贝像素 (位图拷贝, 无位图时为 mat 克隆); 副本属于同一脚本运行时且与原图互相独立, 回收其中之一不影响另一个.
 *   图像只弱引用其运行时, 逃逸出脚本的图像 (例如进入进程级缓存) 不会在脚本结束后钉住整个运行时 (路线图 H11); 这样的图像在运行时退出时可达, 因此已被 [recycleAll] 回收.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 20, 2023.
open class ImageWrapper : Shootable<ImageWrapper> {

    private val mScriptRuntimeRef: WeakReference<ScriptRuntime>

    /** The runtime this image belongs to (used by the Auto.js 4 shaped `Images.concat(imgA, imgB, direction)`). */
    val scriptRuntime: ScriptRuntime
        get() = mScriptRuntimeRef.get() ?: throw IllegalStateException("The script runtime of this image no longer exists")

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

    /** The Auto.js 4 constructor (no runtime): bound to the script running on the calling thread. */
    constructor(width: Int, height: Int) : this(currentScriptRuntime(), width, height)

    constructor(scriptRuntime: ScriptRuntime, bitmap: Bitmap) {
        mScriptRuntimeRef = WeakReference(scriptRuntime)
        mBitmap = bitmap
        mWidth = bitmap.width
        mHeight = bitmap.height
        register(this, scriptRuntime)
    }

    constructor(scriptRuntime: ScriptRuntime, mat: Mat) {
        mScriptRuntimeRef = WeakReference(scriptRuntime)
        mMat = mat
        mWidth = mat.cols()
        mHeight = mat.rows()
        register(this, scriptRuntime)
    }

    constructor(scriptRuntime: ScriptRuntime, mat: org.opencv.core.Mat) {
        mScriptRuntimeRef = WeakReference(scriptRuntime)
        // Never share the native header with the caller's Mat, whose finalizer would delete it under our feet.
        // zh-CN: 不与调用方的 Mat 共享原生头部, 否则其 finalizer 会在我们仍在使用时删除该头部.
        mMat = Mat.from(mat)
        mWidth = mat.cols()
        mHeight = mat.rows()
        register(this, scriptRuntime)
    }

    constructor(scriptRuntime: ScriptRuntime, bitmap: Bitmap, mat: Mat?) {
        mScriptRuntimeRef = WeakReference(scriptRuntime)
        mMat = mat
        mBitmap = bitmap
        mWidth = bitmap.width
        mHeight = bitmap.height
        register(this, scriptRuntime)
    }

    constructor(scriptRuntime: ScriptRuntime, mediaImage: Image) {
        mScriptRuntimeRef = WeakReference(scriptRuntime)
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
        register(this, scriptRuntime)
    }

    fun saveTo(path: String): Boolean {
        ensureNotRecycled()
        val fullPath = path.toRuntimePath(scriptRuntime, true)
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

    /**
     * A deep copy that belongs to the same script runtime; the one-shot mark is not copied.
     * zh-CN: 属于同一脚本运行时的深拷贝; 不复制一次性标记.
     */
    fun clone(): ImageWrapper {
        ensureNotRecycled()
        return when (val bitmap = mBitmap) {
            null -> ofMat(scriptRuntime, mat.clone())
            else -> ofBitmap(scriptRuntime, bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true))
        }
    }

    companion object {

        // Images are tracked per script runtime, so that a finishing script only recycles its own images
        // and never the ones still in use by other running scripts.
        // zh-CN: 按脚本运行时分别记录图像, 脚本结束时只回收自己的图像, 不影响其他仍在运行的脚本.
        private val registry = WeakHashMap<ScriptRuntime, MutableList<WeakReference<ImageWrapper>>>()

        private const val REGISTRY_PRUNE_INTERVAL = 256

        private fun register(image: ImageWrapper, scriptRuntime: ScriptRuntime) {
            synchronized(registry) {
                val list = registry.getOrPut(scriptRuntime) { ArrayList() }
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

        /**
         * Images of every script runtime that are still reachable and not recycled (docs/memory/ROADMAP.md P3.1).
         * zh-CN: 所有脚本运行时中仍可达且未回收的图像数 (docs/memory/ROADMAP.md P3.1).
         */
        @JvmStatic
        fun liveCount(): Int = synchronized(registry) {
            registry.values.sumOf { list -> list.count { it.get()?.mIsRecycled == false } }
        }

        /**
         * Images of the given script runtime that are still reachable and not recycled.
         * zh-CN: 指定脚本运行时中仍可达且未回收的图像数.
         */
        @JvmStatic
        fun liveCount(scriptRuntime: ScriptRuntime): Int = synchronized(registry) {
            registry[scriptRuntime]?.count { it.get()?.mIsRecycled == false } ?: 0
        }

        // Media images handed out by an ImageReader (see ScreenCapturer), held weakly and only counted, never closed here.
        // zh-CN: ImageReader 交出的 media image (见 ScreenCapturer), 仅以弱引用记录并计数, 此处从不关闭它们.
        private val mediaImages: MutableSet<Image> = Collections.newSetFromMap(WeakHashMap())

        /**
         * Records a media image just acquired from an ImageReader for [openMediaImageCount]; returns it unchanged.
         * zh-CN: 记录刚从 ImageReader 取得的 media image, 供 [openMediaImageCount] 统计; 原样返回.
         */
        @JvmStatic
        fun trackMediaImage(image: Image): Image = image.also { synchronized(mediaImages) { mediaImages.add(it) } }

        /**
         * Media images not closed yet: those acquired from an ImageReader and recorded by [trackMediaImage], plus the
         * ones kept by live wrappers (none in practice, the constructor closes its media image). An ImageReader stops
         * handing out frames once all of its maxImages images are open (issue #204), so this count must stay small.
         * zh-CN: 尚未关闭的 media image 数: 经 [trackMediaImage] 记录的 ImageReader 图像, 加上存活的包装对象保留的图像 (构造方法会关闭传入的 media image, 实际为零).
         * ImageReader 的 maxImages 个图像全部未关闭时将不再交出新帧 (issue #204), 因此该数应保持很小.
         */
        @JvmStatic
        fun openMediaImageCount(): Int {
            val acquired = synchronized(mediaImages) {
                mediaImages.removeAll { !it.isOpen() }
                mediaImages.size
            }
            val wrapped = synchronized(registry) {
                registry.values.sumOf { list -> list.count { ref -> ref.get()?.let { !it.mIsRecycled && it.mMediaImage != null } == true } }
            }
            return acquired + wrapped
        }

        // Every accessor of a closed image throws; reading the timestamp touches no native state.
        // zh-CN: 已关闭图像的每个访问器都会抛出异常; 读取时间戳不涉及原生状态.
        private fun Image.isOpen() = try {
            timestamp
            true
        } catch (_: IllegalStateException) {
            false
        }

        @JvmStatic
        fun ofImage(scriptRuntime: ScriptRuntime, image: Image) = ImageWrapper(scriptRuntime, image)

        @JvmStatic
        fun ofMat(scriptRuntime: ScriptRuntime, mat: Mat) = ImageWrapper(scriptRuntime, mat)

        @JvmStatic
        fun ofMat(scriptRuntime: ScriptRuntime, mat: org.opencv.core.Mat) = ImageWrapper(scriptRuntime, mat)

        @JvmStatic
        fun ofBitmap(scriptRuntime: ScriptRuntime, bitmap: Bitmap) = ImageWrapper(scriptRuntime, bitmap)

        /**
         * The runtime of the script running on the calling thread, for the Auto.js 4 shaped factories below:
         * `com.stardust.autojs.core.image.ImageWrapper` resolves to this class through `StardustClassAliases`
         * (roadmap P7.3) and its factories take no runtime.
         * zh-CN: 调用线程上正在运行的脚本的运行时, 供下面 Auto.js 4 形式的工厂方法使用: `com.stardust.autojs.core.image.ImageWrapper`
         * 经 `StardustClassAliases` 解析到本类 (roadmap P7.3), 其工厂方法不带运行时参数.
         */
        @JvmStatic
        fun currentScriptRuntime(): ScriptRuntime {
            val context = org.mozilla.javascript.Context.getCurrentContext() as? AutoJsContext
            return context?.rhinoJavaScriptEngine?.runtime
                ?: throw IllegalStateException("No script runtime is bound to the calling thread")
        }

        @JvmStatic
        fun ofImage(image: Image) = ofImage(currentScriptRuntime(), image)

        @JvmStatic
        fun ofMat(mat: Mat) = ofMat(currentScriptRuntime(), mat)

        @JvmStatic
        fun ofBitmap(bitmap: Bitmap) = ofBitmap(currentScriptRuntime(), bitmap)

        @JvmStatic
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
