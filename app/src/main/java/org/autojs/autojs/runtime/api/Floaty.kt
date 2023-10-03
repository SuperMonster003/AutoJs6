package org.autojs.autojs.runtime.api

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.floaty.RawWindow
import org.autojs.autojs.core.floaty.RawWindow.RawFloaty
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.util.ViewUtils.setViewMeasure
import org.autojs.autojs6.R
import java.lang.Exception
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by Stardust on 2017/12/5.
 * Modified by SuperMonster003 as of Mar 27, 2022.
 * Transformed by SuperMonster003 on Mar 27, 2022.
 */
class Floaty(private val mUiHandler: UiHandler, private val mRuntime: ScriptRuntime) {

    private val mContext = ContextThemeWrapper(mUiHandler.context, R.style.ScriptTheme)
    private val mWindows = CopyOnWriteArraySet<JsWindow>()
    private val mDisplayOverOtherAppsPerm = DisplayOverOtherAppsPermission(mContext)

    fun window(supplier: BaseResizableFloatyWindow.ViewSupplier): JsResizableWindow {
        try {
            mDisplayOverOtherAppsPerm.waitFor()
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException()
        }
        return JsResizableWindow(supplier).also { addWindow(it) }
    }

    fun window(view: View): JsResizableWindow = window(object : BaseResizableFloatyWindow.ViewSupplier {
        override fun inflate(context: Context?, parent: ViewGroup?) = view
    })

    fun rawWindow(floaty: RawFloaty): JsRawWindow {
        try {
            mDisplayOverOtherAppsPerm.waitFor()
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException()
        }
        return JsRawWindow(floaty).also { addWindow(it) }
    }

    fun rawWindow(view: View) = JsRawWindow(object : RawFloaty {
        override fun inflateWindowView(service: Context?, parent: ViewGroup?) = view
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

    private fun isUiThread() = Looper.myLooper() == Looper.getMainLooper()

    interface JsWindow {
        fun close(removeFromWindows: Boolean)
    }

    inner class JsRawWindow(floaty: RawFloaty) : JsWindow {

        private var mWindow = RawWindow(floaty, mUiHandler.context)
        private var mExitOnClose = false

        init {
            mUiHandler.context.startService(Intent(mUiHandler.context, FloatyService::class.java))
            runWithWindow { FloatyService.addWindow(mWindow) }
        }

        fun findView(id: String?): View? {
            return JsViewHelper.findViewByStringId(mWindow.contentView, id)
        }

        val x: Int
            get() = mWindow.windowBridge.x
        val y: Int
            get() = mWindow.windowBridge.y
        val width: Int
            get() = mWindow.windowView.width
        val height: Int
            get() = mWindow.windowView.height

        fun setSize(w: Int, h: Int) = runWithWindow {
            mWindow.windowBridge.updateMeasure(w, h)
            setViewMeasure(mWindow.windowView, w, h)
        }

        fun setTouchable(touchable: Boolean) = runWithWindow {
            mWindow.setTouchable(touchable)
        }

        private fun runWithWindow(r: Runnable) {
            if (isUiThread()) {
                r.run()
            } else {
                mUiHandler.post(r)
            }
        }

        fun setPosition(x: Int, y: Int) = runWithWindow {
            mWindow.windowBridge.updatePosition(x, y)
        }

        fun exitOnClose() {
            mExitOnClose = true
        }

        fun requestFocus() = mWindow.requestWindowFocus()

        fun disableFocus() = mWindow.disableWindowFocus()

        fun close() = close(true)

        override fun close(removeFromWindows: Boolean) {
            if (!removeFromWindows || removeWindow(this)) {
                runWithWindow {
                    mWindow.close()
                    if (mExitOnClose) {
                        mRuntime.exit()
                    }
                }
            }
        }
    }

    inner class JsResizableWindow(supplier: BaseResizableFloatyWindow.ViewSupplier) : JsWindow {

        private lateinit var mView: View

        @Volatile
        private var mWindow = BaseResizableFloatyWindow(mUiHandler.context, object : BaseResizableFloatyWindow.ViewSupplier {
            override fun inflate(context: Context?, parent: ViewGroup?): View {
                return supplier.inflate(context, parent).also { mView = it }
            }
        })
        private var mExitOnClose = false

        init {
            mUiHandler.context.startService(Intent(mUiHandler.context, FloatyService::class.java))
            runWithWindow { FloatyService.addWindow(mWindow) }
            mWindow.setOnCloseButtonClickListener { _ -> close() }
            // setSize(mWindow.getWindowBridge().getScreenWidth() / 2, mWindow.getWindowBridge().getScreenHeight() / 2);
        }

        fun findView(id: String?) = JsViewHelper.findViewByStringId(mView, id)

        val x: Int
            get() = mWindow.windowBridge.x
        val y: Int
            get() = mWindow.windowBridge.y
        val width: Int
            get() = mWindow.rootView.width
        val height: Int
            get() = mWindow.rootView.height

        fun setSize(w: Int, h: Int) = runWithWindow {
            mWindow.windowBridge.updateMeasure(w, h)
            setViewMeasure(mWindow.rootView, w, h)
        }

        private fun runWithWindow(r: Runnable) {
            if (isUiThread()) {
                r.run()
            } else {
                mUiHandler.post(r)
            }
        }

        fun setPosition(x: Int, y: Int) = runWithWindow {
            mWindow.windowBridge.updatePosition(x, y)
        }

        var isAdjustEnabled: Boolean
            get() = mWindow.isAdjustEnabled
            set(enabled) {
                runWithWindow { mWindow.isAdjustEnabled = enabled }
            }

        fun exitOnClose() {
            mExitOnClose = true
        }

        fun requestFocus() = mWindow.requestWindowFocus()

        fun disableFocus() = mWindow.disableWindowFocus()

        fun close() = close(true)

        override fun close(removeFromWindows: Boolean) {
            if (!removeFromWindows || removeWindow(this)) {
                runWithWindow {
                    mWindow.close()
                    if (mExitOnClose) {
                        mRuntime.exit()
                    }
                }
            }
        }
    }

}