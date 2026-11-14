package org.autojs.autojs.core.virtualscreen

import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A low-allocation live preview feed for one virtual display.
 *
 * The frame source is an [ImageReader] with two buffers. **It is driven by active polling, not by
 * `OnImageAvailableListener`**: by the time a viewer attaches to a display that was created a
 * moment earlier, frames may already have filled both buffers. A listener registered at that point
 * is never called for the queued frames, while the producer blocks waiting for a free buffer — a
 * deadlock that shows up as a permanently black preview "waiting for the first frame". Calling
 * `acquireLatestImage()` on a dedicated thread drains those buffers and unblocks the producer
 * immediately. The same call also covers displays that only produce frames occasionally.
 *
 * While running, this pump is the *only* consumer of the reader: `VirtualScreenSession` reads a
 * copy of [bitmap] instead of acquiring images itself. Frames are written into one reusable
 * [Bitmap] (fast path `copyPixelsFromBuffer`; GPU-aligned rowStride is repacked through a reusable
 * direct buffer), so there is no per-frame bitmap allocation and no recycling race with the UI.
 * If the producer reports a different geometry (rotation, resized display), the reusable buffers
 * are resized once and the UI picks up the new bitmap on its next draw.
 *
 * zh-CN: 虚拟屏低分配实时预览帧源. **主动轮询取帧, 不依赖 OnImageAvailableListener**: 查看器附着到
 * 稍早已建好的屏时, 两个缓冲可能已被帧占满; 此时才注册的 listener 不会补发已排队帧, 生产者又在等待
 * 空闲缓冲, 形成死锁, 表现为预览一直「等待第一帧」. 用专用线程主动 acquireLatestImage() 排空缓冲即可解锁.
 * 运行期间本泵是 ImageReader 的唯一消费者, 脚本截图改读复用 Bitmap 的副本; 尺寸/旋转变化时一次性重建复用缓冲.
 *
 * Created for the virtual screen viewer on Nov 8, 2026. Fixed the black-preview deadlock on Nov 9, 2026.
 */
class VirtualScreenFramePump(
    private val reader: ImageReader,
    private val width: Int,
    private val height: Int,
    private val onFrame: () -> Unit,
) {

    /** The reusable frame. May be replaced once if the producer changes geometry; never recycled. */
    @Volatile
    var bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private set

    private var compact = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
    private var rowBytes = ByteArray(width * 4)
    private val running = AtomicBoolean(false)

    @Volatile
    private var thread: HandlerThread? = null

    @Volatile
    private var handler: Handler? = null

    /** Nanosecond timestamp of the last frame actually written, 0 before the first one. */
    @Volatile
    var lastFrameNanos: Long = 0L
        private set

    /** Uptime millis of the last successful write, for the "no frames for N ms" diagnostic. */
    @Volatile
    var lastWriteMillis: Long = 0L
        private set

    /** Monotonic count of frames written, for the viewer's FPS readout. */
    @Volatile
    var frameCount: Long = 0L
        private set

    /** Last conversion/acquisition error, cleared on the next successful frame. Null when healthy. */
    @Volatile
    var lastError: String? = null
        private set

    /** True once at least one frame has landed in [bitmap]. */
    val hasFrame: Boolean get() = lastFrameNanos != 0L

    private val pollTask = object : Runnable {
        override fun run() {
            if (!running.get()) return
            consumeOnce()
            if (running.get()) handler?.postDelayed(this, FRAME_INTERVAL_MILLIS)
        }
    }

    /** Starts consuming frames. Calling it twice is a no-op. */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            val t = HandlerThread("vs-frames-$width-$height").also { it.start() }
            thread = t
            handler = Handler(t.looper)
            // Drain any frame that was queued before we attached; this is what breaks the deadlock.
            handler?.post(pollTask)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "cannot start frame pump", error)
            lastError = "start: ${error.message}"
            running.set(false)
            thread?.quitSafely()
            thread = null
            handler = null
            false
        }
    }

    /** Requests an immediate out-of-band drain, e.g. right after launching an app on the display. */
    fun drainNow() {
        handler?.post {
            if (running.get()) consumeOnce()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun consumeOnce() {
        val image = runCatching { reader.acquireLatestImage() }
            .onFailure {
                lastError = "acquireLatestImage: ${it.message}"
                Log.w(TAG, "acquireLatestImage failed", it)
            }
            .getOrNull() ?: return
        val written = try {
            write(image)
        } catch (error: Throwable) {
            lastError = "write: ${error.message}"
            Log.w(TAG, "frame conversion failed", error)
            false
        } finally {
            image.close()
        }
        if (written) {
            lastError = null
            lastFrameNanos = SystemClock.elapsedRealtimeNanos()
            lastWriteMillis = SystemClock.uptimeMillis()
            frameCount++
            runCatching { onFrame() }
        }
    }

    /** Copies a frame's pixels into the reusable [bitmap]; returns false on an unusable plane. */
    private fun write(image: Image): Boolean {
        val sourceWidth = image.width
        val sourceHeight = image.height
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            lastError = "empty image ${sourceWidth}x${sourceHeight}"
            return false
        }
        val plane = image.planes.firstOrNull() ?: run {
            lastError = "image has no planes"
            return false
        }
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride != 4 || rowStride < sourceWidth * 4) {
            lastError = "unsupported plane: pixelStride=$pixelStride rowStride=$rowStride srcW=$sourceWidth"
            return false
        }
        ensureGeometry(sourceWidth, sourceHeight)

        val source = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
        compact.clear()
        if (rowStride == sourceWidth * 4) {
            // ImageReader RGBA_8888 is stored as little-endian RGBA, which is exactly what
            // ARGB_8888's copyPixelsFromBuffer expects on a little-endian device.
            source.rewind()
            val limit = source.capacity().coerceAtMost(sourceWidth * sourceHeight * 4)
            source.limit(limit)
            compact.put(source)
        } else {
            // Some GPUs align rowStride to 64 bytes. Repack the visible part of every row.
            for (y in 0 until sourceHeight) {
                source.position(y * rowStride)
                source.get(rowBytes, 0, sourceWidth * 4)
                compact.put(rowBytes)
            }
        }
        compact.flip()
        bitmap.copyPixelsFromBuffer(compact)
        return true
    }

    /** Rebuilds the reusable buffers once when the producer reports a different geometry. */
    private fun ensureGeometry(sourceWidth: Int, sourceHeight: Int) {
        if (bitmap.width == sourceWidth && bitmap.height == sourceHeight) return
        Log.i(TAG, "frame geometry changed ${bitmap.width}x${bitmap.height} -> ${sourceWidth}x${sourceHeight}")
        // The old bitmap is intentionally not recycled: the UI may still be drawing it. It is
        // replaced at most once, so letting the GC collect it is safe and simple.
        bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        compact = ByteBuffer.allocateDirect(sourceWidth * sourceHeight * 4).order(ByteOrder.nativeOrder())
        rowBytes = ByteArray(sourceWidth * 4)
    }

    private companion object {
        const val TAG = "VirtualScreenFramePump"
        const val FRAME_INTERVAL_MILLIS = 66L // ~15 fps preview, and ~15 fps polling of the reader
    }

}
