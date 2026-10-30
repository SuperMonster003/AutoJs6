package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.floaty.RawWindow
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.util.ViewUtils.setViewMeasure

/**
 * A floating window of the JS runtimes' `floaty.window` / `floaty.rawWindow` (api-coverage P3.3) around an execution-owned
 * native View: the logic of `Floaty.ManagedWindow` without a `ScriptRuntime`. `window` uses the resizable frame of Rhino's
 * `floaty.window` (move, resize and close controls), `rawWindow` the bare [RawWindow]. Creation is asynchronous through
 * [FloatyService]: [onCreated] runs once with null or the failure; [onClosed] runs once when the window goes away, whether
 * the user pressed the close control or the owner called [close]. Everything runs on the main thread.
 * zh-CN: JS 运行时 `floaty.window` / `floaty.rawWindow` 的悬浮窗 (api-coverage P3.3), 承载执行拥有的原生 View: 即不依赖 `ScriptRuntime` 的
 * `Floaty.ManagedWindow` 逻辑. `window` 使用 Rhino `floaty.window` 的可调整窗口外框 (移动, 调整大小与关闭按钮), `rawWindow` 使用不带外框的 [RawWindow].
 * 经 [FloatyService] 异步创建: [onCreated] 以 null 或失败原因调用一次; 窗口消失时 (用户点关闭按钮或属主调用 [close]) [onClosed] 调用一次.
 * 全部在主线程上运行.
 */
internal class RuntimeFloatyWindow(
    context: Context,
    val raw: Boolean,
    private val content: View,
    onCreated: (Throwable?) -> Unit,
    onClosed: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val supplier = object : BaseResizableFloatyWindow.ViewSupplier {
        override fun inflate(context: Context, parent: ViewGroup?): View {
            (content.parent as? ViewGroup)?.removeView(content)
            parent?.addView(content)
            return content
        }
    }
    private var native: FloatyWindow? = if (raw) RawWindow(context, supplier) else BaseResizableFloatyWindow(context, supplier)
    var isClosed = false
        private set
    private var created = false
    private var geometry = Geometry()
    private var observedView: View? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateGeometry() }
    private var creationCallback: ((Throwable?) -> Unit)? = onCreated
    private var closeCallback: (() -> Unit)? = onClosed

    /** Window position and size in pixels, as `Floaty.ManagedWindow` reports them. zh-CN: 窗口位置与大小 (像素), 同 `Floaty.ManagedWindow` 的报告方式. */
    data class Geometry(val x: Int = -1, val y: Int = -1, val width: Int = 0, val height: Int = 0)

    init {
        when (val window = native) {
            is RawWindow -> {
                window.creationObserver = ::creationResult
                window.closeObserver = ::nativeClosed
            }
            is BaseResizableFloatyWindow -> {
                window.creationObserver = ::creationResult
                window.closeObserver = ::nativeClosed
                window.setOnCloseButtonClickListener { close() }
            }
        }
    }

    val currentGeometry: Geometry
        get() = geometry

    fun start() {
        if (isClosed) return
        try {
            checkNotNull(appContext.startService(Intent(appContext, FloatyService::class.java))) { "Floating window service did not start" }
            native?.let(FloatyService::addWindow)
        } catch (failure: Exception) {
            creationResult(failure)
        } catch (failure: LinkageError) {
            creationResult(failure)
        }
    }

    private fun creationResult(failure: Throwable?) {
        if (isClosed || created) return
        created = true
        if (failure == null) {
            observedView = native?.windowView
            observedView?.viewTreeObserver?.addOnGlobalLayoutListener(layoutListener)
            updateGeometry()
        }
        val callback = creationCallback.also { creationCallback = null }
        try {
            callback?.invoke(failure)
        } finally {
            if (failure != null) close()
        }
    }

    fun setPosition(x: Int, y: Int) {
        val window = native ?: return
        val bridge = window.windowBridge ?: return
        val params = window.windowLayoutParams
        // Keep the offset of the resizable frame around its content, as Floaty.ManagedWindow does.
        // zh-CN: 与 Floaty.ManagedWindow 一样保留可调整外框相对内容的偏移.
        params.x = x - (bridge.x - params.x)
        params.y = y - (bridge.y - params.y)
        window.updateWindowLayoutParams(params)
        updateGeometry()
    }

    fun setSize(width: Int, height: Int) {
        native?.windowBridge?.updateMeasure(width, height)
        measureView()?.let { setViewMeasure(it, width, height) }
        updateGeometry()
    }

    fun setTouchable(touchable: Boolean) {
        val window = native ?: return
        val params = window.windowLayoutParams
        params.flags = if (touchable) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        window.updateWindowLayoutParams(params)
    }

    fun requestFocus() {
        when (val window = native) {
            is RawWindow -> window.requestWindowFocus()
            is BaseResizableFloatyWindow -> window.requestWindowFocus()
        }
    }

    fun disableFocus() {
        when (val window = native) {
            is RawWindow -> window.disableWindowFocus()
            is BaseResizableFloatyWindow -> window.disableWindowFocus()
        }
    }

    fun setAdjustEnabled(enabled: Boolean) {
        val window = native as? BaseResizableFloatyWindow ?: throw IllegalArgumentException("Raw windows have no adjustment controls")
        window.isAdjustEnabled = enabled
    }

    fun isAdjustEnabled(): Boolean = (native as? BaseResizableFloatyWindow)?.isAdjustEnabled == true

    private fun measureView(): View? = when (val window = native) {
        is BaseResizableFloatyWindow -> window.rootView
        else -> window?.windowView
    }

    private fun updateGeometry() {
        val window = native ?: return
        val measured = measureView()
        geometry = Geometry(window.windowBridge?.x ?: -1, window.windowBridge?.y ?: -1, measured?.width ?: 0, measured?.height ?: 0)
    }

    private fun stopObserving() {
        observedView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        observedView = null
        geometry = Geometry()
    }

    private fun nativeClosed() {
        if (isClosed) return
        isClosed = true
        stopObserving()
        native = null
        creationCallback = null
        detachContent()
        closeCallback.also { closeCallback = null }?.invoke()
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        stopObserving()
        val window = native.also { native = null }
        creationCallback = null
        try {
            window?.close()
        } finally {
            detachContent()
            closeCallback.also { closeCallback = null }?.invoke()
        }
    }

    /** Releases the borrowed content so a View handle that outlives the window can be shown again. zh-CN: 释放借用的内容, 使比窗口存活更久的 View 句柄可以再次显示. */
    private fun detachContent() {
        (content.parent as? ViewGroup)?.removeView(content)
    }
}
