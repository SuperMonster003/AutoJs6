package org.autojs.autojs.core.virtualscreen

import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.runtime.ScriptRuntime
import org.json.JSONObject
import java.io.Closeable
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * One virtual screen. Instances are returned to JavaScript as Java objects, so their public methods
 * are the script API (`screen.tap(...)`, `screen.capture()`). A session may also be *detached*
 * (created with a null [runtime] by the viewer), in which case it outlives any script and has to be
 * released explicitly from the viewer.
 *
 * The app process only owns the [ImageReader] surface; the display itself is held by the Shizuku
 * user service process. Script-bound sessions are registered with `ScriptRuntime.closeableManager`,
 * so a screen created by a script is released automatically when that script exits.
 *
 * While the viewer is open, [startPreview] makes a [VirtualScreenFramePump] the sole consumer of
 * the ImageReader, and [snapshotBitmap] reads that pump's reusable bitmap instead of competing for
 * the two image buffers. Preview and script screenshots therefore coexist without starving either.
 *
 * zh-CN: 一块虚拟屏. 实例作为 Java 对象返回给 JavaScript, 公开方法即脚本 API. 由查看器创建、
 * [runtime] 为空的「独立屏」不随脚本退出而销毁, 需在查看器中显式释放. app 进程只持有 ImageReader surface,
 * 显示屏本身由 Shizuku 用户服务进程持有; 脚本持有的会话登记到 closeableManager, 脚本退出自动释放.
 * 查看器打开期间由帧泵独占 ImageReader, 脚本截图改读帧泵的复用 Bitmap, 两边不再互相抢缓冲.
 */
class VirtualScreenSession internal constructor(
    private val runtime: ScriptRuntime?,
    private val service: IUserService,
    private val reader: ImageReader,
    private val displayId: Int,
    private val width: Int,
    private val height: Int,
    private val dpi: Int,
    private val creationInfo: JSONObject,
) : Closeable {

    private val alive = AtomicBoolean(true)
    private val frameTimeNanos = AtomicLong(0L)
    private val previewLock = Any()

    @Volatile
    private var pump: VirtualScreenFramePump? = null

    fun getDisplayId(): Int = displayId
    fun getWidth(): Int = width
    fun getHeight(): Int = height
    fun getDpi(): Int = dpi
    fun getFlags(): Int = creationInfo.optInt("flags")
    fun isTrusted(): Boolean = creationInfo.optBoolean("trusted")
    fun hasOwnFocus(): Boolean = creationInfo.optBoolean("ownFocus")
    fun isReleased(): Boolean = !alive.get()
    fun getFrameTime(): Long = pump?.lastFrameNanos?.takeIf { it != 0L } ?: frameTimeNanos.get()

    /** True for a screen created by the viewer: it is not tied to any script's lifecycle. */
    fun isDetached(): Boolean = runtime == null

    /** JSON state of this screen: geometry, flags, preview status and whether the last frame is fresh. */
    fun state(): String = JSONObject(creationInfo.toString())
        .put("displayId", displayId)
        .put("width", width)
        .put("height", height)
        .put("dpi", dpi)
        .put("released", isReleased())
        .put("detached", isDetached())
        .put("previewing", isPreviewing())
        .put("frameTimeNanos", getFrameTime())
        .put("frameCount", previewFrameCount())
        .put("previewError", previewError().orEmpty())
        .put("rotation", currentRotation())
        .toString()

    /** Launches an app on this display. 0=ok, 1=no launcher entry, 2=error, 3=invalid display. */
    fun launch(packageName: String): Int = call {
        service.launchOnDisplay(displayId, packageName, "", false)
    }.getOrElse { errorCodeFor(it) }

    /** Launches an explicit `package/activity` component on this display. */
    fun launchComponent(component: String): Int = call {
        service.launchOnDisplay(displayId, "", component, false)
    }.getOrElse { errorCodeFor(it) }

    /** Opens an ACTION_VIEW app link on this display instead of the physical screen. */
    fun launchUri(uri: String): Boolean = call {
        service.launchUriOnDisplay(displayId, uri) == 0
    }.getOrDefault(false)

    /** Like [launch] but clears the existing task first (a cold start on this display). */
    fun launchNew(packageName: String): Int = call {
        service.launchOnDisplay(displayId, packageName, "", true)
    }.getOrElse { errorCodeFor(it) }

    /** Stops an app that was launched on this display. */
    fun forceStop(packageName: String): Boolean = call {
        service.forceStopOnDisplay(displayId, packageName)
    }.getOrDefault(false)

    fun tap(x: Int, y: Int): Boolean = call { service.tapOnDisplay(displayId, x, y) }.getOrDefault(false)

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean = call {
        service.swipeOnDisplay(displayId, x1, y1, x2, y2, durationMs)
    }.getOrDefault(false)

    fun key(keyCode: Int): Boolean = call { service.keyOnDisplay(displayId, keyCode) }.getOrDefault(false)

    /** ASCII text only; for CJK text use accessibility ACTION_SET_TEXT on a node of this display. */
    fun text(value: String): Boolean = call { service.textOnDisplay(displayId, value) }.getOrDefault(false)

    /** A long press is a zero-distance swipe on the shell side (down, hold, up). */
    fun longPress(x: Int, y: Int, durationMs: Int): Boolean = swipe(x, y, x, y, durationMs)

    // ---- Live preview (viewer) ----

    /**
     * Starts the frame feed. [onFrame] is called on a background HandlerThread, so the caller must
     * hop to the main thread before invalidating a view. Returns false when the listener cannot be
     * attached (the viewer then falls back to polling [snapshotBitmap]).
     */
    fun startPreview(onFrame: () -> Unit): Boolean {
        ensureAlive()
        return synchronized(previewLock) {
            pump?.let { return@synchronized true }
            val created = VirtualScreenFramePump(reader, width, height, onFrame)
            if (created.start()) {
                pump = created
                true
            } else {
                false
            }
        }
    }

    fun stopPreview() {
        synchronized(previewLock) {
            pump?.stop()
            pump = null
        }
    }

    fun isPreviewing(): Boolean = pump != null

    /** The reusable preview bitmap, held by the UI thread for drawing. Never recycle it here. */
    fun previewBitmap(): Bitmap? = pump?.bitmap?.takeIf { pump?.hasFrame == true }

    fun previewFrameCount(): Long = pump?.frameCount ?: 0L

    /** Why the preview has no frame (unsupported plane, closed reader, ...), or null when healthy. */
    fun previewError(): String? = pump?.lastError

    /** Uptime millis of the last successful frame; 0 when no frame has ever arrived. */
    fun previewLastWriteMillis(): Long = pump?.lastWriteMillis ?: 0L

    /** Forces an immediate read, e.g. right after an app was launched on this display. */
    fun drainPreview() {
        pump?.drainNow()
    }

    // ---- Screenshots ----

    /**
     * Latest frame of the virtual display as an [ImageWrapper], so all `images.*` helpers (find
     * color, template match, save) work on it. Coordinates are the logical pixels of this display.
     * Works while the viewer is previewing because it copies the pump's bitmap instead of consuming
     * an ImageReader buffer.
     *
     * zh-CN: 取虚拟屏最新一帧并返回 ImageWrapper, 可直接用 images.* 的找色/找图/保存能力.
     * 查看器预览期间从帧泵的复用 Bitmap 复制, 不占用 ImageReader 缓冲.
     */
    fun capture(): ImageWrapper {
        ensureAlive()
        val owner = runtime
            ?: throw IllegalStateException("A detached virtual screen has no script runtime; use the viewer to save a frame")
        val bitmap = snapshotBitmap() ?: throw IllegalStateException("No frame on virtual display $displayId")
        return ImageWrapper.ofBitmap(owner, bitmap)
    }

    /** Writes the latest frame as PNG, without allocating an ImageWrapper. */
    fun captureToFile(path: String): Boolean {
        ensureAlive()
        val bitmap = snapshotBitmap() ?: return false
        return try {
            FileOutputStream(path).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            Log.w(TAG, "captureToFile failed: $path", t)
            false
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * A standalone copy of the latest frame, safe for the caller to recycle. Shared by the script
     * screenshot path and the viewer's "save PNG" action.
     */
    fun snapshotBitmap(): Bitmap? {
        ensureAlive()
        val active = pump
        if (active != null) {
            // While previewing, the pump is the ONLY ImageReader consumer. Acquiring here would
            // starve both consumers and reintroduce the black-preview deadlock.
            if (!active.hasFrame) return null
            val copy = active.bitmap.copy(active.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            return upright(copy)
        }
        val image = latestImage() ?: return null
        return try {
            toBitmap(image)
        } finally {
            image.close()
        }
    }

    /** Releases the display. Safe to call more than once; also unregisters from the script runtime. */
    fun release(): Boolean {
        if (!alive.compareAndSet(true, false)) return false
        stopPreview()
        // Must not go through call(): it asserts alive, and we have already flipped the flag.
        val ok = runCatching { service.releaseVirtualDisplay(displayId) }
            .onFailure { Log.w(TAG, "releaseVirtualDisplay($displayId) failed", it) }
            .getOrDefault(false)
        runCatching { reader.close() }
        runCatching { runtime?.closeableManager?.remove(this) }
        VirtualScreenManager.unregister(displayId, this)
        return ok
    }

    /** [Closeable] so the script runtime releases this screen on exit. */
    override fun close() {
        release()
    }

    override fun toString(): String = "VirtualScreen(displayId=$displayId, ${width}x$height@$dpi, released=${isReleased()})"

    // region internals

    private fun <T> call(block: () -> T): Result<T> {
        ensureAlive()
        return runCatching { block() }.onFailure { Log.w(TAG, "virtual display $displayId call failed", it) }
    }

    private fun ensureAlive() {
        check(alive.get()) { "Virtual display $displayId has been released" }
    }

    private fun errorCodeFor(error: Throwable): Int {
        Log.w(TAG, "launch failed on display $displayId", error)
        return ShellVirtualDisplay.LAUNCH_ERROR
    }

    private fun latestImage(): Image? {
        val deadline = SystemClock.uptimeMillis() + CAPTURE_TIMEOUT_MILLIS
        do {
            val image = runCatching { reader.acquireLatestImage() }.getOrNull()
            if (image != null) {
                // Producer timestamp, not the time the buffer was copied (Mika's FrameFreshness).
                frameTimeNanos.set(image.timestamp)
                return image
            }
            SystemClock.sleep(FRAME_POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        return null
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val stride = plane.rowStride / plane.pixelStride
        val buffer = plane.buffer
        buffer.rewind()
        val raw = Bitmap.createBitmap(stride, height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buffer)
        val cropped = if (stride == width) {
            raw
        } else {
            Bitmap.createBitmap(raw, 0, 0, width.coerceAtMost(stride), height).also { raw.recycle() }
        }
        return upright(cropped)
    }

    private fun upright(bitmap: Bitmap): Bitmap {
        val degrees = when (currentRotation()) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun currentRotation(): Int = runCatching {
        GlobalAppContext.get()
            .getSystemService(DisplayManager::class.java)
            ?.getDisplay(displayId)?.rotation
    }.getOrNull() ?: Surface.ROTATION_0

    // endregion

    private companion object {
        const val TAG = "VirtualScreenSession"
        const val CAPTURE_TIMEOUT_MILLIS = 2_000L
        const val FRAME_POLL_MILLIS = 20L
    }

}
