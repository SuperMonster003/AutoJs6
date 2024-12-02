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
import org.autojs.autojs.util.KotlinUtils.ifNull
import org.mozilla.javascript.BaseFunction
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
        mTimePerDraw = (if (maxFps <= 0) 0L else 100L / maxFps)
    }

    @Synchronized
    private fun performDraw() {
        ::mDrawingThreadPool.ifNull {
            Executors.newCachedThreadPool()
        }.run {
            execute {
                var canvas: Canvas? = null
                var time = SystemClock.uptimeMillis()
                val scriptCanvas = ScriptCanvas()
                try {
                    while (mDrawing) {
                        canvas = lockCanvas()
                        scriptCanvas.setCanvas(canvas)
                        emit("draw", scriptCanvas, this@JsCanvasView)
                        if (canvas != null) {
                            unlockCanvasAndPost(canvas)
                            canvas = null
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
                    if (canvas != null) {
                        unlockCanvasAndPost(canvas)
                    }
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
        performDraw()
        Log.d(LOG_TAG, "onSurfaceTextureAvailable: ${this}, width = $width, height = $height")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mDrawing = false
        mDrawingThreadPool?.shutdown()
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
