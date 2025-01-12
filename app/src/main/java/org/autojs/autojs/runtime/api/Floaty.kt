package org.autojs.autojs.runtime.api

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Runnable
import org.autojs.autojs.annotation.ScriptInterface
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
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.ViewUtils.setViewMeasure
import org.autojs.autojs6.R
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Created by Stardust on Dec 5, 2017.
 * Modified by SuperMonster003 as of Mar 27, 2022.
 * Transformed by SuperMonster003 on Mar 27, 2022.
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

    fun window(view: View): JsResizableWindow = window(object : BaseResizableFloatyWindow.ViewSupplier {
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

    fun rawWindow(view: View) = JsRawWindow(object : BaseResizableFloatyWindow.ViewSupplier {
        override fun inflate(context: Context, parent: ViewGroup?) = view
    })

    @Synchronized
    private fun addWindow(window: JsWindow) {
        mWindows.add(window)
    }

    @Synchronized
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

    @Synchronized
    fun closeAll() {
        mWindows.apply {
            forEach { it.close(false) }
            clear()
        }
    }

    @ScriptInterface
    fun getClip(delay: Long = 0L): String {
        val start = System.currentTimeMillis()
        val timeoutForAttachedToWindowManager: Long = 2_000
        val latch = CountDownLatch(1)
        val win = window(View(mContext).apply { visibility = View.INVISIBLE })
        UI.postRhinoRuntime(scriptRuntime, newBaseFunction("action", {
            while (System.currentTimeMillis() - start < timeoutForAttachedToWindowManager) {
                if (runCatching { win.requestFocus() }.isSuccess) break
            }
        }, NOT_CONSTRUCTABLE))
        latch.await(max(0, delay - (System.currentTimeMillis() - start)), TimeUnit.MILLISECONDS)
        return scriptRuntime.clip.also { win.close() }
    }

    interface JsWindow {
        fun close(removeFromWindows: Boolean)
    }

    inner class JsRawWindow(supplier: BaseResizableFloatyWindow.ViewSupplier) : JsWindow {

        private var mWindow: RawWindow? = null
        private var mExitOnClose = false

        init {
            val context = uiHandler.applicationContext
            val window = RawWindow(supplier).also { mWindow = it }
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
            return mWindow?.let { JsViewHelper.findViewByStringId(it.contentView, id) }
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