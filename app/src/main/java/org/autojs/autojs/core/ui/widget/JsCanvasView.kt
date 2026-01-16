package org.autojs.autojs.core.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import android.view.View
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.graphics.ScriptCanvas
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.BaseFunction
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Created by Stardust on Mar 16, 2018.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
@SuppressLint("ViewConstructor")
class JsCanvasView : TextureView, TextureView.SurfaceTextureListener {

    private lateinit var mScriptRuntime: ScriptRuntime

    @Volatile
    private var mDrawing = true
    private val mEventEmitter: EventEmitter by lazy { EventEmitter(mScriptRuntime.bridges) }

    private var mDrawingThreadPool: ExecutorService? = null

    // Track the draw loop task to allow cancellation.
    // zh-CN: 跟踪绘制循环任务, 以便支持取消.
    @Volatile
    private var mDrawFuture: Future<*>? = null

    // Prevent starting draw loop multiple times.
    // zh-CN: 防止重复启动绘制循环.
    @Volatile
    private var mDrawLoopStarted = false

    // Serialize surface lock/unlock with surface destroy to avoid native crash.
    // zh-CN: 串行化 surface 的 lock/unlock 与 destroy, 避免 native 崩溃.
    private val mSurfaceLock = Any()

    // Track whether surface is alive/available for drawing.
    // zh-CN: 标记 surface 是否处于可用于绘制的存活状态.
    @Volatile
    private var mSurfaceAlive = false

    @Volatile
    private var mTimePerDraw = (1000 / 30).toLong()

    val maxListeners: Int
        get() = mEventEmitter.maxListeners

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        surfaceTextureListener = this
    }

    fun initWithScriptRuntime(scriptRuntime: ScriptRuntime) {
        mScriptRuntime = scriptRuntime
    }

    fun setMaxFps(maxFps: Int) {
        mTimePerDraw = if (maxFps <= 0) 0L else 1000L / maxFps
    }

    @Synchronized
    private fun performDraw() {
        // Ensure single-thread executor and keep the reference.
        // zh-CN: 确保使用单线程执行器并保存引用.
        if (mDrawingThreadPool == null) {
            mDrawingThreadPool = Executors.newSingleThreadExecutor()
        }

        // Do not start draw loop repeatedly.
        // zh-CN: 不要重复启动绘制循环.
        if (mDrawLoopStarted) {
            return
        }
        mDrawLoopStarted = true

        val executor = mDrawingThreadPool ?: return
        mDrawFuture = executor.submit {
            var canvas: Canvas? = null
            var time = SystemClock.uptimeMillis()
            val scriptCanvas = ScriptCanvas(mScriptRuntime)

            try {
                while (mDrawing) {

                    // Exit quickly if surface is not available.
                    // zh-CN: 如果 surface 不可用, 尽快退出循环.
                    if (!mSurfaceAlive || !isAvailable) {
                        break
                    }

                    // Serialize lockCanvas/draw/unlockCanvasAndPost with surface destroy.
                    // zh-CN: 将 lockCanvas/draw/unlockCanvasAndPost 与 surface destroy 串行化.
                    synchronized(mSurfaceLock) {
                        if (!mSurfaceAlive || !isAvailable || !mDrawing) {
                            return@synchronized
                        }

                        try {
                            canvas = lockCanvas()
                        } catch (t: Throwable) {
                            // lockCanvas may throw when surface is being destroyed.
                            // zh-CN: surface 正在销毁时 lockCanvas 可能抛出异常.
                            canvas = null
                            mDrawing = false
                            return@synchronized
                        }

                        if (canvas == null) {
                            return@synchronized
                        }

                        scriptCanvas.setCanvas(canvas)
                        emit("draw", scriptCanvas, this@JsCanvasView)

                        try {
                            unlockCanvasAndPost(canvas)
                        } catch (t: Throwable) {
                            // Guard against device-specific native crash.
                            // zh-CN: 防御设备特定的 native 崩溃风险.
                            mDrawing = false
                            return@synchronized
                        } finally {
                            canvas = null
                        }
                    }

                    val dt = mTimePerDraw - (SystemClock.uptimeMillis() - time)
                    if (dt > 0) {
                        sleep(dt)
                    }
                    time = SystemClock.uptimeMillis()
                }
            } catch (e: Exception) {
                mScriptRuntime.exit(e)
                mDrawing = false
            } finally {
                // Mark as stopped so it can be started again if needed.
                // zh-CN: 标记为已停止, 以便必要时允许再次启动.
                mDrawLoopStarted = false

                if (canvas != null) {
                    runCatching { unlockCanvasAndPost(canvas) }
                }
            }
        }
    }

    private fun sleep(dt: Long) {
        try {
            Thread.sleep(dt)
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException(e)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        Log.d(LOG_TAG, "onWindowVisibilityChanged: $this: visibility=$visibility, mDrawingThreadPool=$mDrawingThreadPool")
        val oldDrawing = mDrawing
        mDrawing = visibility == View.VISIBLE
        if (!oldDrawing && mDrawing) {
            performDraw()
        }
        super.onWindowVisibilityChanged(visibility)
    }

    fun once(eventName: String, listener: BaseFunction): EventEmitter {
        return mEventEmitter.once(eventName, listener)
    }

    fun on(eventName: String, listener: BaseFunction): EventEmitter {
        return mEventEmitter.on(eventName, listener)
    }

    fun addListener(eventName: String, listener: BaseFunction): EventEmitter {
        return mEventEmitter.addListener(eventName, listener)
    }

    fun emit(eventName: String, vararg args: Any): Boolean {
        return mEventEmitter.emit(eventName, *args)
    }

    fun eventNames(): Array<String> {
        return mEventEmitter.eventNames()
    }

    fun listenerCount(eventName: String): Int {
        return mEventEmitter.listenerCount(eventName)
    }

    fun listeners(eventName: String): Array<Any> {
        return mEventEmitter.listeners(eventName)
    }

    fun prependListener(eventName: String, listener: BaseFunction): EventEmitter {
        return mEventEmitter.prependListener(eventName, listener)
    }

    fun prependOnceListener(eventName: String, listener: BaseFunction): EventEmitter {
        return mEventEmitter.prependOnceListener(eventName, listener)
    }

    fun removeAllListeners(): EventEmitter {
        return mEventEmitter.removeAllListeners()
    }

    fun removeAllListeners(eventName: String): EventEmitter {
        return mEventEmitter.removeAllListeners(eventName)
    }

    fun removeListener(eventName: String, listener: BaseFunction): EventEmitter {
        return mEventEmitter.removeListener(eventName, listener)
    }

    fun setMaxListeners(n: Int): EventEmitter {
        return mEventEmitter.setMaxListeners(n)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        // Mark surface as alive before starting draw loop.
        // zh-CN: 在启动绘制循环前将 surface 标记为存活.
        mSurfaceAlive = true
        performDraw()
        Log.d(LOG_TAG, "onSurfaceTextureAvailable: ${this}, width = $width, height = $height")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // Stop draw loop and block until current lockCanvas/unlockCanvasAndPost critical section finishes.
        // zh-CN: 停止绘制循环, 并阻塞等待当前 lockCanvas/unlockCanvasAndPost 临界区结束.
        synchronized(mSurfaceLock) {
            mSurfaceAlive = false
            mDrawing = false
        }

        // Do NOT interrupt native drawing calls aggressively.
        // zh-CN: 不要对 native 绘制调用进行激进的中断.
        runCatching { mDrawFuture?.cancel(false) }
        mDrawFuture = null

        // Shutdown executor gracefully.
        // zh-CN: 平滑关闭执行器.
        runCatching { mDrawingThreadPool?.shutdown() }
        mDrawingThreadPool = null

        Log.d(LOG_TAG, "onSurfaceTextureDestroyed: $this")
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        /* Empty body. */
    }

    companion object {

        private const val LOG_TAG = "ScriptCanvasView"

        fun defaultMaxListeners(): Int = EventEmitter.defaultMaxListeners()

    }
}
