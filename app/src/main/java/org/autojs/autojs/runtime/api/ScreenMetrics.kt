package org.autojs.autojs.runtime.api

import android.app.Activity
import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Resources
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.Surface.ROTATION_0
import android.view.WindowManager
import org.autojs.autojs.app.GlobalAppContext
import java.lang.ref.WeakReference
import kotlin.math.absoluteValue

/**
 * Created by Stardust on Apr 26, 2017.
 * Modified by SuperMonster003 as of Jan 11, 2026.
 */
@Suppress("unused")
class ScreenMetrics {

    private var mDesignWidth = 0
    private var mDesignHeight = 0

    fun setScreenMetrics(width: Int, height: Int) {
        mDesignWidth = width
        mDesignHeight = height
    }

    @JvmOverloads
    fun scaleX(x: Int, width: Int = mDesignWidth) = when {
        width == 0 || !mIsInitialized -> x
        else -> x * deviceScreenWidth / width
    }

    @JvmOverloads
    fun scaleY(y: Int, height: Int = mDesignHeight) = when {
        height == 0 || !mIsInitialized -> y
        else -> y * deviceScreenHeight / height
    }

    @JvmOverloads
    fun rescaleX(x: Int, width: Int = mDesignWidth) = when {
        width == 0 || !mIsInitialized -> x
        else -> x * width / deviceScreenWidth
    }

    @JvmOverloads
    fun rescaleY(y: Int, height: Int = mDesignHeight) = when {
        height == 0 || !mIsInitialized -> y
        else -> y * height / deviceScreenHeight
    }

    companion object {

        val resources: Resources?
            get() = mActivityRef.get()?.resources

        private val mWindowManager: WindowManager
            get() = mActivityRef.get()?.windowManager
                ?: GlobalAppContext.get().getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Prefer DisplayManager for rotation to reduce Activity/ROM inconsistencies.
        // zh-CN: rotation 优先使用 DisplayManager, 以降低 Activity 引用/ROM 行为差异导致的方向读数不一致.
        private val mDisplayManager: DisplayManager?
            get() = GlobalAppContext.get().getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

        private var mIsInitialized = false
        private var mActivityRef = WeakReference<Activity?>(null)

        @Suppress("DEPRECATION")
        @JvmStatic
        val rotation: Int
            get() {
                // Prefer system Display rotation when available.
                // zh-CN: 尽可能使用系统 Display 的 rotation.
                val dm = mDisplayManager
                val display: Display? = dm?.getDisplay(Display.DEFAULT_DISPLAY)
                val rot = display?.rotation
                if (rot != null) return rot

                // Fallback to legacy WindowManager/defaultDisplay.
                // zh-CN: 回退到旧实现 WindowManager/defaultDisplay.
                return mWindowManager.defaultDisplay?.rotation ?: ROTATION_0
            }

        // Cache stable long/short sides to avoid inconsistent width/height on some ROMs.
        // zh-CN: 缓存稳定的长边/短边, 避免部分 ROM 在多次运行脚本/跨应用切换后出现宽高读数不一致.
        @Volatile
        private var sStableLongSide: Int = 0

        @Volatile
        private var sStableShortSide: Int = 0

        private fun ensureStableSides() {
            if (sStableLongSide > 0 && sStableShortSide > 0) return

            // Prefer WindowMetrics on Android R+.
            // zh-CN: 在 Android R+ 优先使用 WindowMetrics.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = mWindowManager.maximumWindowMetrics.bounds
                val w = bounds.width().absoluteValue
                val h = bounds.height().absoluteValue
                if (w > 0 && h > 0) {
                    sStableLongSide = maxOf(w, h)
                    sStableShortSide = minOf(w, h)
                    return
                }
            }

            // Fallback to getRealMetrics().
            // zh-CN: 回退到 getRealMetrics().
            @Suppress("DEPRECATION")
            run {
                val metricsLegacy = DisplayMetrics()
                mWindowManager.defaultDisplay.apply { getRealMetrics(metricsLegacy) }
                val w = metricsLegacy.widthPixels
                val h = metricsLegacy.heightPixels
                if (w > 0 && h > 0) {
                    sStableLongSide = maxOf(w, h)
                    sStableShortSide = minOf(w, h)
                }
            }
        }

        @JvmStatic
        val deviceScreenWidth: Int
            get() {
                ensureStableSides()
                val rot = rotation
                val isLandscape = rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270
                return if (isLandscape) sStableLongSide else sStableShortSide
            }

        @JvmStatic
        val deviceScreenHeight: Int
            get() {
                ensureStableSides()
                val rot = rotation
                val isLandscape = rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270
                return if (isLandscape) sStableShortSide else sStableLongSide
            }

        @JvmStatic
        val orientation: Int
            get() = resources?.configuration?.orientation ?: ORIENTATION_PORTRAIT

        @JvmStatic
        val isScreenPortrait: Boolean
            get() = orientation == ORIENTATION_PORTRAIT

        @JvmStatic
        val isScreenLandscape: Boolean
            get() = orientation == ORIENTATION_LANDSCAPE

        @JvmStatic
        @Suppress("DEPRECATION")
        val deviceScreenDensity: Int
            get() {
                val metricsLegacy = DisplayMetrics()
                mWindowManager.defaultDisplay?.apply { getRealMetrics(metricsLegacy) }
                return metricsLegacy.densityDpi
            }

        @JvmStatic
        fun init(activity: Activity) {
            mActivityRef = WeakReference(activity)
            mIsInitialized = true

            // Reset stable cache when Activity is (re)initialized.
            // zh-CN: Activity 初始化/重建时重置稳定缓存, 以适配可能的显示模式变化.
            sStableLongSide = 0
            sStableShortSide = 0
        }

        private fun toOriAwarePoint(a: Int, b: Int) = arrayOf(minOf(a, b), maxOf(a, b))
            .apply { if (isScreenLandscape) reverse() }
            .let { Point(it[0], it[1]) }

        @JvmStatic
        fun getOrientationAwareScreenWidth(orientation: Int): Int {
            // Use stable sides instead of querying deviceScreenWidth/Height multiple times.
            // zh-CN: 使用稳定长边/短边, 避免重复查询导致的不一致.
            ensureStableSides()
            val longSide = sStableLongSide
            val shortSide = sStableShortSide
            return when (orientation) {
                ORIENTATION_LANDSCAPE -> longSide
                else -> shortSide
            }
        }

        @JvmStatic
        fun getOrientationAwareScreenHeight(orientation: Int): Int {
            // Use stable sides instead of querying deviceScreenWidth/Height multiple times.
            // zh-CN: 使用稳定长边/短边, 避免重复查询导致的不一致.
            ensureStableSides()
            val longSide = sStableLongSide
            val shortSide = sStableShortSide
            return when (orientation) {
                ORIENTATION_LANDSCAPE -> shortSide
                else -> longSide
            }
        }

    }

}