@file:Suppress("SameParameterValue")

package org.autojs.autojs.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.snackbar.Snackbar
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/1/24.
 * Modified by SuperMonster003 as of Sep 11, 2022.
 */
@Suppress("unused")
object ViewUtils {

    enum class MODE(val key: String) {
        DAY(key(R.string.key_night_mode_always_off)),
        NIGHT(key(R.string.key_night_mode_always_on)),
        FOLLOW(key(R.string.key_night_mode_follow_system)),
        NULL(key(R.string.key_night_mode_null)),
        ;
    }

    @JvmStatic
    var isKeepScreenOnWhenInForegroundEnabled
        get() = Pref.getBoolean(R.string.key_keep_screen_on_when_in_foreground_enabled, false)
        private set(b) = Pref.putBoolean(R.string.key_keep_screen_on_when_in_foreground_enabled, b)

    val isKeepScreenOnWhenInForegroundDisabled
        get() = Pref.keyKeepScreenOnWhenInForeground == key(R.string.key_keep_screen_on_when_in_foreground_disabled)

    val isKeepScreenOnWhenInForegroundAllPages
        get() = Pref.keyKeepScreenOnWhenInForeground == key(R.string.key_keep_screen_on_when_in_foreground_all_pages)

    private var keepScreenOnWhenInForegroundFromLastEnabledState: String
        get() = Pref.getString(R.string.key_keep_screen_on_when_in_foreground_last_enabled, key(R.string.key_keep_screen_on_when_in_foreground_homepage_only))!!
        set(s) = Pref.putString(R.string.key_keep_screen_on_when_in_foreground_last_enabled, s)

    var isAutoNightModeEnabled: Boolean
        get() = Pref.getBoolean(R.string.key_auto_night_mode_enabled, AutoNightMode.isFunctional())
        set(b) = Pref.putBoolean(R.string.key_auto_night_mode_enabled, b)

    var isNightModeEnabled: Boolean
        get() = Pref.getBoolean(R.string.key_night_mode_enabled, false)
        set(b) = Pref.putBoolean(R.string.key_night_mode_enabled, b)

    @JvmStatic
    @Suppress("FunctionName")
    fun <V : View?> `$`(view: View, @IdRes resId: Int): V {
        return view.findViewById(resId)
    }

    // FIXME: 2018/1/23 not working in some devices
    // https://github.com/hyb1996/Auto.js/issues/268
    fun getStatusBarHeightLegacy(context: Context): Int {
        return context.resources.getIdentifier("status_bar_height", "dimen", "android").let { resourceId ->
            when (resourceId > 0) {
                true -> context.resources.getDimensionPixelSize(resourceId)
                else -> 0
            }
        }
    }

    @JvmStatic
    fun getStatusBarHeight(context: Context): Int {
        return getWindowForContext(context)?.let { window ->
            Rect().let { rect ->
                window.decorView.getWindowVisibleDisplayFrame(rect)
                rect.top.takeIf { it > 0 }
            }
        } ?: TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()
    }

    fun getTitleBarHeight(context: Context): Int {
        val window = getWindowForContext(context)
        val contentViewTop = window?.findViewById<View>(Window.ID_ANDROID_CONTENT)?.top ?: 0
        return contentViewTop - getStatusBarHeight(context)
    }

    fun getScreenHeight(activity: Activity) = DisplayMetrics().let { metrics ->
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        metrics.heightPixels
    }

    fun getScreenWidth(activity: Activity) = DisplayMetrics().let { metrics ->
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        metrics.widthPixels
    }

    @JvmStatic
    fun setViewMeasure(view: View, width: Int, height: Int) {
        view.layoutParams.also {
            it.width = width
            it.height = height
            view.layoutParams = it
        }
    }

    @JvmStatic
    fun appendSystemUiVisibility(activity: Activity, flags: Int) {
        activity.window.decorView.apply {
            @Suppress("DEPRECATION")
            systemUiVisibility = systemUiVisibility or flags
        }
    }

    @JvmStatic
    fun removeSystemUiVisibility(activity: Activity, flags: Int) {
        activity.window.decorView.apply {
            @Suppress("DEPRECATION")
            systemUiVisibility = systemUiVisibility and flags.inv()
        }
    }

    private fun appendWindowFlags(activity: Activity, flags: Int) {
        activity.window.addFlags(flags)
    }

    private fun toNightMode(mode: MODE) = when (mode) {
        MODE.NIGHT -> AppCompatDelegate.MODE_NIGHT_YES
        MODE.DAY -> AppCompatDelegate.MODE_NIGHT_NO
        MODE.FOLLOW -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        MODE.NULL -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    }

    fun setDefaultNightMode(mode: MODE) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    fun makeBarsAdaptToNightMode(activity: AppCompatActivity) {
        val isConfigDark = isNightModeYes(activity)
        val isConfigTakenAsLight = !isConfigDark

        getWindowInsetsController(activity).apply {
            isAppearanceLightStatusBars = isConfigDark
            isAppearanceLightNavigationBars = isConfigTakenAsLight
        }

        activity.window.navigationBarColor = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O && isConfigTakenAsLight -> {
                // @Hint by SuperMonster003 on Sep 9, 2022.
                //  ! On devices running with Android 7.x and with dark theme disabled,
                //  ! navigation bar would be white background with white buttons.
                activity.getColor(R.color.navigation_bar_background_before_android_o)
            }
            else -> Color.TRANSPARENT
        }
    }

    @JvmStatic
    fun isNightModeYes(context: Context) = getNightFlag(context) == UI_MODE_NIGHT_YES

    fun isNightModeYes(configuration: Configuration) = getNightFlag(configuration) == UI_MODE_NIGHT_YES

    private fun getNightFlag(context: Context) = getNightFlag(context.resources.configuration)

    private fun getNightFlag(configuration: Configuration) = configuration.uiMode and UI_MODE_NIGHT_MASK

    private fun getWindowInsetsController(activity: AppCompatActivity): WindowInsetsControllerCompat {
        return WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    }

    private fun getWindowForContext(context: Context): Window? = when (context) {
        is Activity -> context.window
        is ContextWrapper -> getWindowForContext(context.baseContext)
        else -> null
    }

    fun onConfigurationChanged(configuration: Configuration) {
        if (isAutoNightModeEnabled) {
            val isNightModeYes = isNightModeYes(configuration)
            if (isNightModeEnabled != isNightModeYes) {
                isNightModeEnabled = isNightModeYes
                val mode = when (isNightModeYes) {
                    true -> MODE.NIGHT
                    else -> MODE.DAY
                }
                setDefaultNightMode(mode)
            }
        }
    }

    @JvmStatic
    fun setToolbarAsBack(activity: AppCompatActivity, title: String?) {
        activity.apply {
            findViewById<Toolbar>(R.id.toolbar).let { toolbar ->
                toolbar.title = title
                setSupportActionBar(toolbar)
                supportActionBar?.let { actionBar ->
                    toolbar.setNavigationOnClickListener { finish() }
                    actionBar.setDisplayHomeAsUpEnabled(true)
                }
            }
        }
    }

    @JvmStatic
    fun setToolbarAsBack(activity: AppCompatActivity, titleRes: Int) {
        setToolbarAsBack(activity, activity.getString(titleRes))
    }

    @JvmStatic
    fun showToast(context: Context, stringRes: Int) = showToast(context, stringRes, false)

    @JvmStatic
    fun showToast(context: Context, stringRes: Int, isLong: Boolean) = showToast(context, context.getString(stringRes), isLong)

    @JvmStatic
    fun showToast(context: Context, string: String?) = showToast(context, string, false)

    @JvmStatic
    fun showToast(context: Context, string: String?, isLong: Boolean) {
        string?.let {
            when {
                Looper.getMainLooper() == Looper.myLooper() -> {
                    Toast.makeText(context, it, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
                else -> GlobalAppContext.post {
                    Toast.makeText(context, it, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @JvmStatic
    fun showSnack(view: View, stringRes: Int) = showSnack(view, stringRes, false)

    @JvmStatic
    fun showSnack(view: View, stringRes: Int, isLong: Boolean) {
        Snackbar.make(view, stringRes, if (isLong) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun showSnack(view: View, string: CharSequence) = showSnack(view, string, false)

    @JvmStatic
    fun showSnack(view: View, string: CharSequence, isLong: Boolean) {
        Snackbar.make(view, string, if (isLong) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun showSnack(view: View, stringRes: Int, duration: Int) = Snackbar.make(view, stringRes, duration).show()

    @JvmStatic
    fun showSnack(view: View, string: CharSequence, duration: Int) = Snackbar.make(view, string, duration).show()

    fun setKeepScreenOnWhenInForegroundDisabled() {
        Pref.putString(R.string.key_keep_screen_on_when_in_foreground, key(R.string.key_keep_screen_on_when_in_foreground_disabled))
        isKeepScreenOnWhenInForegroundEnabled = false
    }

    fun setKeepScreenOnWhenInForegroundAllPages() {
        Pref.putString(R.string.key_keep_screen_on_when_in_foreground, key(R.string.key_keep_screen_on_when_in_foreground_all_pages))
        keepScreenOnWhenInForegroundFromLastEnabledState = key(R.string.key_keep_screen_on_when_in_foreground_all_pages)
        isKeepScreenOnWhenInForegroundEnabled = true
    }

    fun setKeepScreenOnWhenInForegroundHomepageOnly() {
        Pref.putString(R.string.key_keep_screen_on_when_in_foreground, key(R.string.key_keep_screen_on_when_in_foreground_homepage_only))
        keepScreenOnWhenInForegroundFromLastEnabledState = key(R.string.key_keep_screen_on_when_in_foreground_homepage_only)
        isKeepScreenOnWhenInForegroundEnabled = true
    }

    fun setKeepScreenOnWhenInForegroundFromLastEnabledState() {
        Pref.putString(R.string.key_keep_screen_on_when_in_foreground, keepScreenOnWhenInForegroundFromLastEnabledState)
        isKeepScreenOnWhenInForegroundEnabled = true
    }

    fun registerOnSharedPreferenceChangeListener(activity: Activity) {
        Pref.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == key(R.string.key_keep_screen_on_when_in_foreground)) {
                configKeepScreenOnWhenInForeground(activity)
            }
        }
    }

    fun configKeepScreenOnWhenInForeground(activity: Activity) {
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.let { flags ->
            if (isKeepScreenOnWhenInForegroundEnabled) {
                activity.window.addFlags(flags)
            } else {
                activity.window.clearFlags(flags)
            }
        }
    }

    class AutoNightMode {

        companion object {

            fun isFunctional() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

            fun dysfunctionIfNeeded() {
                if (!isFunctional()) dysfunction()
            }

            private fun dysfunction() {
                isAutoNightModeEnabled = false
            }

        }

    }

}