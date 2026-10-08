package org.autojs.autojs.runtime.api

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import kotlinx.coroutines.Runnable
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.floaty.RawWindow
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.core.ui.inflater.Exceptions
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ui.UI
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.ViewUtils.setViewMeasure
import org.autojs.autojs6.R
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.Closeable

/**
 * Created by Stardust on Dec 5, 2017.
 * Transformed by SuperMonster003 on Mar 27, 2022.
 * Modified by SuperMonster003 as of Jan 16, 2026.
 */
class Floaty(private val uiHandler: UiHandler, private val scriptRuntime: ScriptRuntime) {

    private val mContext = ContextThemeWrapper(uiHandler.applicationContext, R.style.ScriptTheme)
    private val mWindows = CopyOnWriteArraySet<JsWindow>()
    private val mDisplayOverOtherAppsPerm = DisplayOverOtherAppsPermission(mContext)

    fun window(supplier: BaseResizableFloatyWindow.ViewSupplier): JsResizableWindow {
        try {
            mDisplayOverOtherAppsPerm.waitFor()
        } catch (_: InterruptedException) {
            throw ScriptInterruptedException()
        }
        return JsResizableWindow(supplier).also { addWindow(it) }
    }

    fun window(view: View) = window(object : BaseResizableFloatyWindow.ViewSupplier {
        override fun inflate(context: Context, parent: ViewGroup?) = view
    })

    fun rawWindow(supplier: BaseResizableFloatyWindow.ViewSupplier): JsRawWindow {
        try {
            mDisplayOverOtherAppsPerm.waitFor()
        } catch (_: InterruptedException) {
            throw ScriptInterruptedException()
        }
        return JsRawWindow(supplier).also { addWindow(it) }
    }

    fun rawWindow(view: View) = rawWindow(object : BaseResizableFloatyWindow.ViewSupplier {
        override fun inflate(context: Context, parent: ViewGroup?) = view
    })

    private fun addWindow(window: JsWindow) {
        mWindows.add(window)
    }

    private fun removeWindow(window: JsWindow): Boolean {
        return mWindows.remove(window)
    }

    @ScriptInterface
    fun hasPermission() = mDisplayOverOtherAppsPerm.has()

    @ScriptInterface
    fun requestPermission() = mDisplayOverOtherAppsPerm.request()

    @ScriptInterface
    fun ensurePermission() {
        if (!hasPermission()) {
            throw Exception(mContext.getString(R.string.error_no_display_over_other_apps_permission))
        }
    }

    fun closeAll() {

        // Close windows synchronously on UI thread.
        // Reason:
        // - close(false) is usually posted to UI thread via UiHandler.
        // - Under heavy start/stop stress, UI queue may be congested.
        // - If we clear mWindows before actual close runs, leaked windows may remain and block touch.
        //
        // zh-CN:
        //
        // 在 UI 线程同步关闭窗口.
        // 原因:
        // - close(false) 通常会通过 UiHandler 投递到 UI 线程异步执行.
        // - 在频繁启动/停止脚本的压力场景下, UI 队列可能严重拥堵.
        // - 如果在实际 close 执行前就清空 mWindows, 可能出现窗口残留并拦截触摸.
        val snapshot = mWindows.toList()

        // Fast path: already on UI thread.
        // zh-CN: 快速路径: 已处于 UI 线程.
        if (isUiThread()) {
            snapshot.forEach { runCatching { it.close(false) } }
            mWindows.removeAll(snapshot.toSet())
            return
        }

        // Slow path: post to UI thread and wait with timeout.
        // zh-CN: 慢路径: 投递到 UI 线程并等待, 同时提供超时保护.
        val latch = CountDownLatch(1)
        uiHandler.post {
            try {
                snapshot.forEach { runCatching { it.close(false) } }
                mWindows.removeAll(snapshot.toSet())
            } finally {
                latch.countDown()
            }
        }

        // Wait a little to ensure removal is performed before runtime fully exits.
        // zh-CN: 等待一小段时间, 确保在运行时完全退出前窗口已被移除.
        runCatching { latch.await(1500, TimeUnit.MILLISECONDS) }
    }

    @ScriptInterface
    fun getClip(maxDelayAfterWindowReady: Long = 500L): String {
        val start = System.currentTimeMillis()
        val timeoutForAttachedToWindowManager = 1000L
        val view = FrameLayout(mContext)
        val win = rawWindow(view)
        UI.runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
            while (System.currentTimeMillis() - start < timeoutForAttachedToWindowManager) {
                if (runCatching { win.requestFocus() }.isSuccess) break
            }
        }, NOT_CONSTRUCTABLE))
        val getClipboardData = {
            val getter = { scriptRuntime.clip.also { win.close() } }
            val startTime = System.currentTimeMillis()
            var result = getter()
            while (System.currentTimeMillis() - startTime < maxDelayAfterWindowReady) {
                if (result.isNotBlank()) break
                Thread.sleep(10)
                result = getter()
            }
            result
        }
        return when {
            isMainThread() -> {
                getClipboardData()
            }
            else -> {
                val result = VolatileDispose<String>()
                scriptRuntime.uiHandler.post { result.setAndNotify(getClipboardData()) }
                result.blockedGetOrThrow(ScriptInterruptedException::class.java)
            }
        }
    }

    interface JsWindow {
        fun close(removeFromWindows: Boolean)
    }
    private data class ManagedGeometry(val x: Int = -1, val y: Int = -1, val width: Int = 0, val height: Int = 0)

    /** Register ownership before any main-thread view creation, without blocking its script caller. */
    internal fun reserveManagedClose(onClosed: () -> Unit): Closeable {
        val notified = AtomicBoolean()
        val pending = object : JsWindow {
            override fun close(removeFromWindows: Boolean) {
                if (removeFromWindows) removeWindow(this)
                if (notified.compareAndSet(false, true)) onClosed()
            }
        }
        addWindow(pending)
        // A closeAll snapshot already containing this entry must still deliver its close after
        // promotion to a real window. Removing the registry entry does not invalidate that snapshot.
        return Closeable { removeWindow(pending) }
    }

    /**
     * Main-thread, nonblocking window creation for an in-process renderer. Native windows stay in
     * the same registry as legacy JS windows, including while FloatyService is still starting.
     */
    internal fun createManagedWindow(
        raw: Boolean,
        supplier: BaseResizableFloatyWindow.ViewSupplier,
        onCreated: (ManagedWindow, Throwable?) -> Unit,
        onClosed: () -> Unit,
        rawWindowParameters: ((WindowManager.LayoutParams) -> Unit)? = null,
    ): ManagedWindow {
        check(isUiThread()) { "Managed floating windows require the main thread" }
        return ManagedWindow(raw, supplier, onCreated, onClosed, rawWindowParameters).also {
            addWindow(it)
            it.start()
        }
    }

    /** No script callbacks run here. Owners marshal notifications back to their script dispatcher. */
    internal inner class ManagedWindow(
        val raw: Boolean,
        supplier: BaseResizableFloatyWindow.ViewSupplier,
        onCreated: (ManagedWindow, Throwable?) -> Unit,
        onClosed: () -> Unit,
        rawWindowParameters: ((WindowManager.LayoutParams) -> Unit)? = null,
    ) : JsWindow {
        @Volatile private var native: FloatyWindow? = if (raw) RawWindow(mContext, supplier, rawWindowParameters) else BaseResizableFloatyWindow(mContext, supplier)
        @Volatile var isClosed = false
            private set
        private var created = false
        @Volatile private var geometry = ManagedGeometry()
        private var observedView: View? = null
        private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateGeometry() }
        private var creationCallback: ((ManagedWindow, Throwable?) -> Unit)? = onCreated
        private var closeCallback: (() -> Unit)? = onClosed

        init {
            when (val window = native) {
                is RawWindow -> {
                    window.creationObserver = ::creationResult
                    window.closeObserver = ::nativeClosed
                }
                is BaseResizableFloatyWindow -> {
                    window.creationObserver = ::creationResult
                    window.closeObserver = ::nativeClosed
                    window.setOnCloseButtonClickListener { close(true) }
                }
            }
        }

        val x get() = geometry.x
        val y get() = geometry.y
        val width get() = geometry.width
        val height get() = geometry.height
        internal fun start() {
            check(isUiThread())
            if (isClosed) return
            try {
                val context = uiHandler.applicationContext
                checkNotNull(context.startService(Intent(context, FloatyService::class.java))) { "Floating window service did not start" }
                native?.let(FloatyService::addWindow)
            } catch (failure: Exception) { creationResult(failure) }
            catch (failure: LinkageError) { creationResult(failure) }
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
            try { callback?.invoke(this, failure) }
            finally { if (failure != null) close(true) }
        }

        fun setPosition(x: Int, y: Int) {
            check(isUiThread())
            val window = native ?: return
            val bridge = window.windowBridge ?: return
            val params = window.windowLayoutParams
            // Preserve the existing resizable content offset, but do not swallow WM rejections.
            params.x = x - (bridge.x - params.x)
            params.y = y - (bridge.y - params.y)
            window.updateWindowLayoutParams(params)
            updateGeometry()
        }
        fun setSize(width: Int, height: Int) {
            check(isUiThread())
            native?.windowBridge?.updateMeasure(width, height)
            measureView()?.let { setViewMeasure(it, width, height) }
            updateGeometry()
        }
        fun setTouchable(touchable: Boolean) {
            check(isUiThread())
            val window = native ?: return
            val params = window.windowLayoutParams
            params.flags = if (touchable) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            window.updateWindowLayoutParams(params)
        }
        fun requestFocus() {
            check(isUiThread())
            when (val window = native) {
                is RawWindow -> window.requestWindowFocus()
                is BaseResizableFloatyWindow -> window.requestWindowFocus()
            }
        }
        fun disableFocus() {
            check(isUiThread())
            when (val window = native) {
                is RawWindow -> window.disableWindowFocus()
                is BaseResizableFloatyWindow -> window.disableWindowFocus()
            }
        }
        fun setAdjustEnabled(enabled: Boolean) {
            check(isUiThread())
            val window = native as? BaseResizableFloatyWindow ?: throw IllegalArgumentException("Raw windows have no adjustment controls")
            window.isAdjustEnabled = enabled
        }
        private fun measureView(): View? = when (val window = native) {
            is BaseResizableFloatyWindow -> window.rootView
            else -> window?.windowView
        }
        private fun updateGeometry() {
            val window = native ?: return
            val measured = measureView()
            geometry = ManagedGeometry(window.windowBridge?.x ?: -1, window.windowBridge?.y ?: -1,
                measured?.width ?: 0, measured?.height ?: 0)
        }
        private fun stopObserving() {
            observedView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
            observedView = null
            geometry = ManagedGeometry()
        }
        private fun nativeClosed() {
            if (isClosed) return
            isClosed = true
            stopObserving()
            native = null
            creationCallback = null
            removeWindow(this)
            closeCallback.also { closeCallback = null }?.invoke()
        }

        override fun close(removeFromWindows: Boolean) {
            check(isUiThread())
            if (isClosed) return
            isClosed = true
            stopObserving()
            if (removeFromWindows) removeWindow(this)
            val window = native.also { native = null }
            creationCallback = null
            try { window?.close() }
            finally { closeCallback.also { closeCallback = null }?.invoke() }
        }
    }

    inner class JsRawWindow(supplier: BaseResizableFloatyWindow.ViewSupplier) : JsWindow {

        private var mWindow: RawWindow? = null
        private var mExitOnClose = false

        init {
            val context = uiHandler.applicationContext
            val window = RawWindow(mContext, supplier).also { mWindow = it }
            val r = Runnable {
                context.startService(Intent(context, FloatyService::class.java))
                FloatyService.addWindow(window)
            }
            if (isUiThread()) {
                r.run()
            } else {
                uiHandler.post(r)
                window.waitForCreation()?.let { if (it != Exceptions.NO_EXCEPTION) throw it }
            }
        }

        fun findView(id: String?): View? {
            val win = mWindow ?: return null
            val contentView = win.contentView ?: return null
            return JsViewHelper.findViewByStringId(contentView, id)
        }

        val x: Int
            get() = mWindow?.windowBridge?.x ?: -1
        val y: Int
            get() = mWindow?.windowBridge?.y ?: -1
        val width: Int
            get() = mWindow?.windowView?.width ?: 0
        val height: Int
            get() = mWindow?.windowView?.height ?: 0

        fun setSize(w: Int, h: Int) = runWithWindow {
            mWindow!!.windowBridge?.updateMeasure(w, h)
            mWindow!!.windowView?.let { setViewMeasure(it, w, h) }
        }

        fun setTouchable(touchable: Boolean) = runWithWindow {
            mWindow!!.setTouchable(touchable)
        }

        private fun runWithWindow(r: Runnable) {
            when {
                mWindow == null -> return
                isUiThread() -> r.run()
                else -> uiHandler.post { mWindow?.let { r.run() } }
            }
        }

        fun setPosition(x: Int, y: Int) = runWithWindow {
            mWindow!!.windowBridge?.updatePosition(x, y)
        }

        fun exitOnClose() {
            mExitOnClose = true
        }

        fun requestFocus() = mWindow?.requestWindowFocus()

        fun disableFocus() = mWindow?.disableWindowFocus()

        fun close() = close(true)

        override fun close(removeFromWindows: Boolean) {
            if (!removeFromWindows || removeWindow(this)) {
                runWithWindow {
                    mWindow!!.close()
                    mWindow = null
                    if (mExitOnClose) {
                        scriptRuntime.exit()
                    }
                }
            }
        }
    }

    inner class JsResizableWindow(supplier: BaseResizableFloatyWindow.ViewSupplier) : JsWindow {

        @Volatile
        private var mWindow: BaseResizableFloatyWindow? = null
        private var mView: View? = null
        private var mExitOnClose = false

        init {
            val context = uiHandler.applicationContext
            val window = BaseResizableFloatyWindow(mContext, object : BaseResizableFloatyWindow.ViewSupplier {
                override fun inflate(context: Context, parent: ViewGroup?): View {
                    return supplier.inflate(context, parent).also { mView = it }
                }
            }).also { mWindow = it }
            val r = Runnable {
                context.startService(Intent(context, FloatyService::class.java))
                FloatyService.addWindow(window)
            }
            if (isUiThread()) {
                r.run()
            } else {
                uiHandler.post(r)
                window.waitForCreation()?.let { if (it != Exceptions.NO_EXCEPTION) throw it }
            }
            window.setOnCloseButtonClickListener { close() }
        }

        fun findView(id: String?) = mView?.let { JsViewHelper.findViewByStringId(it, id) }

        val x: Int
            get() = mWindow?.windowBridge?.x ?: -1
        val y: Int
            get() = mWindow?.windowBridge?.y ?: -1
        val width: Int
            get() = mWindow?.rootView?.width ?: 0
        val height: Int
            get() = mWindow?.rootView?.height ?: 0

        fun setSize(w: Int, h: Int) = runWithWindow {
            mWindow!!.windowBridge?.updateMeasure(w, h)
            setViewMeasure(mWindow!!.rootView, w, h)
        }

        private fun runWithWindow(r: Runnable) {
            when {
                mWindow == null -> return
                isUiThread() -> r.run()
                else -> uiHandler.post { mWindow?.let { r.run() } }
            }
        }

        fun setPosition(x: Int, y: Int) = runWithWindow {
            mWindow!!.windowBridge?.updatePosition(x, y)
        }

        var isAdjustEnabled: Boolean
            get() = mWindow?.isAdjustEnabled == true
            set(enabled) {
                runWithWindow { mWindow!!.isAdjustEnabled = enabled }
            }

        fun exitOnClose() {
            mExitOnClose = true
        }

        fun requestFocus() = mWindow?.requestWindowFocus()

        fun disableFocus() = mWindow?.disableWindowFocus()

        fun close() = close(true)

        override fun close(removeFromWindows: Boolean) {
            if (!removeFromWindows || removeWindow(this)) {
                runWithWindow {
                    mWindow!!.close()
                    mWindow = null
                    if (mExitOnClose) {
                        scriptRuntime.exit()
                    }
                }
            }
        }
    }
}
