package org.autojs.autojs.runtime.api

import android.app.Activity
import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Resources
import android.graphics.Point
import android.util.DisplayMetrics
import android.view.Surface.ROTATION_0
import android.view.WindowManager
import org.autojs.autojs.app.GlobalAppContext
import java.lang.ref.WeakReference

/**
 * Created by Stardust on Apr 26, 2017.
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

        private var mIsInitialized = false
        private var mActivityRef = WeakReference<Activity?>(null)

        @Suppress("DEPRECATION")
        @JvmStatic
        val rotation: Int
            get() = mWindowManager.defaultDisplay?.rotation ?: ROTATION_0

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
        val deviceScreenWidth: Int
            get() = getScreenWidthCompat()

        @JvmStatic
        val deviceScreenHeight: Int
            get() = getScreenHeightCompat()

        @Suppress("DEPRECATION")
        private fun getScreenWidthCompat(): Int {
            // resources?.displayMetrics?.widthPixels?.takeIf { it > 0 }?.let { return it }
            val metricsLegacy = DisplayMetrics()
            return mWindowManager.defaultDisplay.apply { getRealMetrics(metricsLegacy) }.let { display ->
                maxOf(metricsLegacy.widthPixels, display.width, 0)
            }
        }

        @Suppress("DEPRECATION")
        private fun getScreenHeightCompat(): Int {
            // resources?.displayMetrics?.heightPixels?.takeIf { it > 0 }?.let { return it }
            val metricsLegacy = DisplayMetrics()
            return mWindowManager.defaultDisplay.apply { getRealMetrics(metricsLegacy) }.let { display ->
                maxOf(metricsLegacy.heightPixels, display.height, 0)
            }
        }

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
        }

        private fun toOriAwarePoint(a: Int, b: Int) = arrayOf(minOf(a, b), maxOf(a, b))
            .apply { if (isScreenLandscape) reverse() }
            .let { Point(it[0], it[1]) }

        @JvmStatic
        fun getOrientationAwareScreenWidth(orientation: Int) = when (orientation) {
            ORIENTATION_LANDSCAPE -> deviceScreenHeight
            else -> deviceScreenWidth
        }

        @JvmStatic
        fun getOrientationAwareScreenHeight(orientation: Int) = when (orientation) {
            ORIENTATION_LANDSCAPE -> deviceScreenWidth
            else -> deviceScreenHeight
        }

    }

}