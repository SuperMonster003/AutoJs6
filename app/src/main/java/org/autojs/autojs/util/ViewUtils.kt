@file:Suppress("SameParameterValue")

package org.autojs.autojs.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Resources
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.size
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Created by Stardust on Jan 24, 2017.
 * Modified by SuperMonster003 as of Sep 11, 2022.
 */
@Suppress("unused")
object ViewUtils {

    private const val TAG_STATUS_BAR_SCRIM = "status_bar_scrim"

    @JvmStatic
    var isKeepScreenOnWhenInForegroundEnabled
        get() = Pref.getBoolean(R.string.key_keep_screen_on_when_in_foreground_enabled, false)
        private set(b) = Pref.putBoolean(R.string.key_keep_screen_on_when_in_foreground_enabled, b)

    val isKeepScreenOnWhenInForegroundDisabled
        get() = Pref.keyKeepScreenOnWhenInForeground == key(R.string.key_keep_screen_on_when_in_foreground_disabled)

    val isKeepScreenOnWhenInForegroundAllPages
        get() = Pref.keyKeepScreenOnWhenInForeground == key(R.string.key_keep_screen_on_when_in_foreground_all_pages)

    private var keepScreenOnWhenInForegroundFromLastEnabledState: String
        get() = Pref.getString(R.string.key_keep_screen_on_when_in_foreground_last_enabled, key(R.string.key_keep_screen_on_when_in_foreground_homepage_only))
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

    @JvmStatic
    @JvmOverloads
    fun getStatusBarHeight(context: Context, withFallback: Boolean = true): Int {
        val window = getWindowForContext(context)

        // [1] Status bar height solution based on WindowInsets, compatible with display cutout.
        // zh-CN: 基于 WindowInsets 的状态栏高度方案, 兼容刘海 (display cutout).

        val insetsTop = window?.decorView?.let { decor ->
            ViewCompat.getRootWindowInsets(decor)?.let { insets ->
                val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val cutoutTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    insets.displayCutout?.safeInsetTop ?: 0
                } else 0
                maxOf(statusTop, cutoutTop)
            }
        } ?: 0
        if (insetsTop > 0) return insetsTop

        // [2] Visible area top solution, may be 0 in some fullscreen/immersive modes.
        // zh-CN: 可见区域 top 方案, 在某些全屏/沉浸式下可能为 0.

        val rectTop = window?.decorView?.let { decor ->
            Rect().also { rect -> decor.getWindowVisibleDisplayFrame(rect) }.top
        } ?: 0
        if (rectTop > 0) return rectTop

        // [3] Internal dimen solution, only as an estimate.
        // zh-CN: 内部 dimen 方案, 仅作为估值.

        if (withFallback) return getStatusBarHeightByDimen(context)

        // [4] Finally return 0, no constant forced.
        // zh-CN: 最终返回 0, 不强制指定常量.

        return 0
    }

    @JvmStatic
    @JvmOverloads
    fun getNavigationBarHeight(context: Context, withFallback: Boolean = true): Int {
        val window = getWindowForContext(context)

        // [1] Status bar height solution based on WindowInsets.
        // zh-CN: 基于 WindowInsets 的状态栏高度方案.

        val maxInsets = window?.decorView?.let { decor ->
            ViewCompat.getRootWindowInsets(decor)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                ?.let { insets ->
                    // Compatible with horizontal screen navigation bars: take the maximum value of bottom/left/right.
                    // zh-CN: 兼容横屏左右侧导航栏: 取 bottom/left/right 最大值.
                    maxOf(insets.bottom, insets.left, insets.right)
                }
        } ?: 0
        if (maxInsets > 0) return maxInsets

        // [2] Internal dimen solution, only as an estimate.
        // zh-CN: 内部 dimen 方案, 仅作为估值.

        if (withFallback) return getNavigationBarHeightByDimen(context)

        // [3] Finally return 0, no constant forced.
        // zh-CN: 最终返回 0, 不强制指定常量.

        return 0
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

    @JvmStatic
    fun hasSystemUiVisibility(activity: Activity, flags: Int): Boolean {
        @Suppress("DEPRECATION")
        return activity.window.decorView.systemUiVisibility and flags == flags
    }

    @JvmStatic
    fun addWindowFlags(activity: Activity, flags: Int) {
        activity.window.addFlags(flags)
    }

    @JvmStatic
    @JvmOverloads
    fun isLuminanceLight(color: Int, backgroundColorMatters: Boolean = true) = when {
        !backgroundColorMatters -> ColorUtils.isLuminanceLight(color)
        !isNightModeEnabled -> ColorUtils.luminance(color) >= 0.224
        else -> ColorUtils.luminance(color) >= 0.141
    }

    @JvmStatic
    @JvmOverloads
    fun isLuminanceDark(color: Int, backgroundColorMatters: Boolean = true) = !isLuminanceLight(color, backgroundColorMatters)

    @JvmStatic
    fun getDayOrNightColorByLuminance(context: Context, color: Int): Int {
        return context.getColor(if (isLuminanceLight(color)) R.color.day else R.color.night)
    }

    @JvmStatic
    fun getDayOrNightColorResByLuminance(color: Int): Int {
        return if (isLuminanceLight(color)) R.color.day else R.color.night
    }

    @JvmStatic
    fun isStatusBarIconLight(activity: Activity): Boolean {
        return !WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars
    }

    @JvmStatic
    fun isStatusBarIconDark(activity: Activity): Boolean {
        return !isStatusBarIconLight(activity)
    }

    fun setStatusBarIconLight(activity: Activity, isLight: Boolean) {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars = !isLight
    }

    fun setStatusBarIconLightByColorLuminance(activity: Activity, @ColorInt referenceBackgroundColor: Int) {
        val shouldBeLight = isLuminanceDark(referenceBackgroundColor)
        setStatusBarIconLight(activity, shouldBeLight)
    }

    @JvmStatic
    fun setStatusBarBackgroundColor(activity: Activity, @ColorInt color: Int) {
        @Suppress("DEPRECATION")
        activity.window.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isStatusBarContrastEnforced = false
            }
            statusBarColor = color
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            installOrUpdateScrim(activity, color)
        }
    }

    private fun installOrUpdateScrim(activity: Activity, @ColorInt color: Int) {
        val decor = activity.window.decorView as ViewGroup
        val scrim = decor.children.find { it.tag == TAG_STATUS_BAR_SCRIM }
            ?: FrameLayout(activity).also {
                it.tag = TAG_STATUS_BAR_SCRIM
                // Height will be set by Insets later.
                // zh-CN: 高度稍后由 Insets 赋值.
                val lp = FrameLayout.LayoutParams(MATCH_PARENT, 0, Gravity.TOP)
                decor.addView(it, lp)
            }
        scrim.setBackgroundColor(color)
        // Sync height with Insets change (precise after Android 15).
        // zh-CN: 每次 Insets 变化时同步高度 (Android 15 之后才能保证精确).
        ViewCompat.setOnApplyWindowInsetsListener(scrim) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (v.layoutParams.height != statusBarHeight) {
                v.layoutParams.height = statusBarHeight
                v.requestLayout()
            }
            insets
        }
    }

    @JvmStatic
    fun isNavigationBarIconLight(activity: Activity): Boolean {
        return !WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightNavigationBars
    }

    @JvmStatic
    fun isNavigationBarIconDark(activity: Activity): Boolean {
        return !isNavigationBarIconLight(activity)
    }

    fun setNavigationBarIconLight(activity: Activity, isLight: Boolean) {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightNavigationBars = isLight
    }

    fun setNavigationBarIconLightByColorLuminance(activity: Activity, @ColorInt referenceBackgroundColor: Int) {
        val shouldBeLight = isLuminanceDark(referenceBackgroundColor)
        setNavigationBarIconLight(activity, shouldBeLight)
    }

    fun setNavigationBarBackgroundColor(activity: Activity, color: Int) {
        val window = activity.window
        val decorView = window.decorView

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.navigationBarColor = color
            return
        }

        decorView.setOnApplyWindowInsetsListener { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsets.Type.navigationBars())
            val contentView: ViewGroup? = activity.findViewById(android.R.id.content)
            val navBarOverlay = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT,
                    navBarInsets.bottom, // Navigation bar height. (zh-CN: 导航栏高度.)
                ).apply { gravity = Gravity.BOTTOM }
                setBackgroundColor(color)
            }
            contentView?.addView(navBarOverlay)
            view.onApplyWindowInsets(insets)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun excludeFloatingActionButtonFromBottomNavigationBar(fab: FloatingActionButton, extraMarginBottomDp: Float = 16F) {
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.layoutParams.runCatching {
                javaClass.getField("bottomMargin")
                    .setInt(this, DisplayUtils.dpToPx(extraMarginBottomDp).roundToInt() + bottomInset)
            }
            insets
        }
    }

    @JvmStatic
    @JvmOverloads
    fun excludePaddingClippableViewFromBottomNavigationBar(view: View, extraPaddingBottomDp: Float = 0F, clipToPadding: Boolean = false) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, 0, 0, DisplayUtils.dpToPx(extraPaddingBottomDp).roundToInt() + bottomInset)
            runCatching { view.javaClass.getMethod("setClipToPadding", Boolean::class.java).invoke(view, clipToPadding) }
            insets
        }
    }

    @JvmStatic
    fun excludeContentViewFromHorizontalNavigationBar(activity: Activity) {
        val contentView = activity.findViewById<View?>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
            val sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val left = sysInsets.left
            val top = v.paddingTop
            val right = sysInsets.right
            val bottom = v.paddingBottom

            v.setPaddingRelative(left, top, right, bottom)

            insets
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

        getWindowInsetsController(activity).apply {
            isAppearanceLightStatusBars = isConfigDark
            isAppearanceLightNavigationBars = isConfigDark
        }
    }

    fun isSystemDarkModeEnabled(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
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

    fun onConfigurationChangedForNightMode(configuration: Configuration) {
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
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)?.apply {
            this.title = title
        } ?: return
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.let { actionBar ->
            toolbar.setNavigationOnClickListener { activity.finish() }
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setMenuIconsColorByThemeColorLuminance(activity)
        toolbar.setNavigationIconColorByThemeColorLuminance(activity)
    }

    @JvmStatic
    fun setToolbarAsBack(activity: AppCompatActivity, titleRes: Int) {
        setToolbarAsBack(activity, activity.getString(titleRes))
    }

    fun Toolbar.setMenuIconsColorByThemeColorLuminance(context: Context) {
        this.setMenuIconsColorByColorLuminance(context, ThemeColorManager.colorPrimary)
    }

    @JvmStatic
    fun setToolbarMenuIconsColorByThemeColorLuminance(context: Context, toolbar: Toolbar) {
        toolbar.setMenuIconsColorByThemeColorLuminance(context)
    }

    fun Toolbar.setMenuIconsColorByColorLuminance(context: Context, aimColor: Int) {
        val color = getDayOrNightColorByLuminance(context, aimColor)
        this.menu.setItemsColor(color)
        this.collapseIcon?.let { this.collapseIcon = it.applyColorFilterWith(color) }
        this.overflowIcon?.let { this.overflowIcon = it.applyColorFilterWith(color) }
    }

    fun Menu.setItemsColor(color: Int) {
        for (i in 0 until size) {
            val menuItem = this[i]
            menuItem.icon = menuItem.icon?.applyColorFilterWith(color)
            menuItem.subMenu?.setItemsColor(color)
        }
    }

    @JvmStatic
    fun setToolbarMenuIconsColorByColorLuminance(context: Context, toolbar: Toolbar, aimColor: Int) {
        toolbar.setMenuIconsColorByColorLuminance(context, aimColor)
    }

    fun Toolbar.setNavigationIconColorByThemeColorLuminance(context: Context) {
        this.setNavigationIconColorByColorLuminance(context, ThemeColorManager.colorPrimary)
    }

    @JvmStatic
    fun setToolbarNavigationIconColorByThemeColorLuminance(context: Context, toolbar: Toolbar) {
        toolbar.setNavigationIconColorByThemeColorLuminance(context)
    }

    fun Toolbar.setNavigationIconColorByColorLuminance(context: Context, aimColor: Int) {
        val color = getDayOrNightColorByLuminance(context, aimColor)
        this.navigationIcon?.let { this.navigationIcon = it.applyColorFilterWith(color) }
    }

    @JvmStatic
    fun onceViewGlobalLayout(view: View, listener: () -> Unit) {
        view.onceGlobalLayout(listener)
    }

    fun View.onceGlobalLayout(listener: () -> Unit) {
        val view = this
        val viewTreeObserver = view.viewTreeObserver
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    listener.invoke()
                    val viewTreeObserver = view.viewTreeObserver
                    if (viewTreeObserver.isAlive) {
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
        }
    }

    @JvmStatic
    fun onViewGlobalLayout(view: View, listener: () -> Unit) {
        view.onGlobalLayout(listener)
    }

    fun View.onGlobalLayout(listener: () -> Unit) {
        val view = this
        val viewTreeObserver = view.viewTreeObserver
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalLayoutListener { listener.invoke() }
        }
    }

    fun Drawable.applyColorFilterWith(color: Int): Drawable {
        return this.applyColorFilterWith(PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN))
    }

    fun Drawable.applyColorFilterWith(colorFilter: ColorFilter): Drawable {
        return this.also { this.colorFilter = colorFilter }
    }

    @JvmStatic
    fun setToolbarNavigationIconColorByColorLuminance(context: Context, toolbar: Toolbar, aimColor: Int) {
        toolbar.setNavigationIconColorByColorLuminance(context, aimColor)
    }

    fun Toolbar.setTitlesTextColorByColorLuminance(context: Context, aimColor: Int) {
        val color = getDayOrNightColorByLuminance(context, aimColor)
        setTitleTextColor(color)
        setSubtitleTextColor(color)
    }

    @JvmStatic
    fun setToolbarTitlesTextColorByColorLuminance(context: Context, toolbar: Toolbar, aimColor: Int) {
        toolbar.setTitlesTextColorByColorLuminance(context, aimColor)
    }

    fun SearchView.setColorsByColorLuminance(context: Context, aimColor: Int) {
        val isAimColorLight = isLuminanceLight(aimColor)
        val fullColor = context.getColor(if (isAimColorLight) R.color.day_full else R.color.night_full)
        val hintColor = context.getColor(if (isAimColorLight) R.color.day_alpha_70 else R.color.night_alpha_70)

        findViewById<EditText?>(androidx.appcompat.R.id.search_src_text)?.apply {
            setTextColor(fullColor)
            setHintTextColor(hintColor)
            setLinkTextColor(fullColor)
        }
        findViewById<ImageView?>(androidx.appcompat.R.id.search_close_btn)?.apply {
            setColorFilter(fullColor)
        }
        findViewById<ImageView?>(androidx.appcompat.R.id.search_mag_icon)?.apply {
            setColorFilter(fullColor)
        }
        findViewById<ImageView?>(androidx.appcompat.R.id.search_go_btn)?.apply {
            setColorFilter(fullColor)
        }
    }

    fun SearchView.setColorsByThemeColorLuminance(context: Context) {
        this.setColorsByColorLuminance(context, ThemeColorManager.colorPrimary)
    }

    @JvmStatic
    fun setSearchViewColorsByColorLuminance(context: Context, searchView: SearchView, aimColor: Int) {
        searchView.setColorsByColorLuminance(context, aimColor)
    }

    @JvmStatic
    fun setSearchViewColorsByThemeColorLuminance(context: Context, searchView: SearchView) {
        setSearchViewColorsByColorLuminance(context, searchView, ThemeColorManager.colorPrimary)
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

    fun configKeepScreenOnWhenInForeground(activity: Activity) {
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.let { flags ->
            if (isKeepScreenOnWhenInForegroundEnabled) {
                activity.window.addFlags(flags)
            } else {
                activity.window.clearFlags(flags)
            }
        }
    }

    /**
     * Show soft keyboard. (zh-CN: 显示软键盘.)
     *
     * @param target The view that needs input (like EditText).
     *               zh-CN: @param target 需要获取输入的视图 (如 EditText).
     * @param useForced Whether to use SHOW_FORCED to force showing, default false, let system decide.
     *                  zh-CN: @param useForced 是否使用 SHOW_FORCED 方式强制弹出, 默认 false, 代表由系统自行判断.
     */
    @JvmStatic
    @JvmOverloads
    fun showSoftInput(target: View, useForced: Boolean = false) {
        val imm = target.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        if (!target.hasFocus()) {
            target.requestFocus()
        }
        target.post {
            @Suppress("DEPRECATION")
            val flag = when (useForced) {
                true -> InputMethodManager.SHOW_FORCED
                else -> InputMethodManager.SHOW_IMPLICIT
            }
            imm.showSoftInput(target, flag)
        }
    }

    /**
     * Hide soft keyboard. (zh-CN: 隐藏软键盘.)
     *
     * @param target The view currently holding input focus, or any view in the same window.
     *               zh-CN: @param target 当前持有输入焦点的视图, 也可以是任意位于同一窗口的 View.
     */
    @JvmStatic
    fun hideSoftInput(target: View) {
        val imm = target.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(target.windowToken, 0)
    }

    // @Comment by SuperMonster003 on Oct 29, 2025.
    //  ! Replaced with getStatusBarHeightByWindow and improved getStatusBarHeightByDimen methods.
    //  ! zh-CN: 已替换为 getStatusBarHeightByWindow 和已改进的 getStatusBarHeightByDimen 方法.
    // FIXME by Stardust on Jan 23, 2018.
    //  ! Not working in some devices.
    //  ! https://github.com/hyb1996/Auto.js/issues/268
    //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
    //  ! 在一些设备上无法正常获取结果.
    //  ! 参阅: https://github.com/hyb1996/Auto.js/issues/268
    //  # @SuppressLint("InternalInsetResource", "DiscouragedApi")
    //  # @JvmStatic
    //  # @JvmOverloads
    //  # fun getStatusBarHeightByDimen(context: Context, withFallback: Boolean = true): Int {
    //  #     val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    //  #     val dimenStatusBarHeight = when (resourceId > 0) {
    //  #         true -> context.resources.getDimensionPixelSize(resourceId)
    //  #         else -> 0
    //  #     }
    //  #     return when {
    //  #         dimenStatusBarHeight > 0 -> dimenStatusBarHeight
    //  #         withFallback -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()
    //  #         else -> 0
    //  #     }
    //  # }
    private fun getStatusBarHeightByDimen(context: Context, withComputedOnPreR: Boolean = true): Int {
        val res = context.resources

        // [1] Priority read from internal dimen to try to cover portrait/landscape differences.
        // zh-CN: 优先从内部 dimen 读取, 尽量覆盖横竖屏差异.

        val isLandscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dimenNameCandidates = when (isLandscape) {
            true -> listOf("status_bar_height_landscape", "status_bar_height", "status_bar_height_default", "status_bar_height_portrait")
            else -> listOf("status_bar_height_portrait", "status_bar_height", "status_bar_height_default", "status_bar_height_landscape")
        }

        val dimenHeight = dimenNameCandidates.asSequence().map { res.queryDimen(it) }.firstOrNull { it > 0 } ?: 0
        if (dimenHeight > 0) return dimenHeight

        // [2] Try to estimate using DisplayMetrics on pre-Android R.
        // zh-CN: 在 Android R 以下, 尝试用 DisplayMetrics 估算.

        if (!withComputedOnPreR) return 0
        val (_, systemUiHeightInset) = getSystemUiInsetsOnPreR(context) ?: return 0

        // Navigation bar estimated value.
        // zh-CN: 导航栏估算值.
        val navBar = getNavigationBarHeightByDimen(context, false).coerceAtLeast(0)

        // "Status bar height" is approximately "vertical system area height" minus "navigation bar height".
        // zh-CN: "状态栏高度" 近似视为 "垂直系统区域高度" 减去 "导航栏高度".
        val statusGuess = (systemUiHeightInset - navBar).coerceAtLeast(0)

        // Prevent abnormal values (status bar usually not exceeding 48dp).
        // zh-CN: 防止异常值 (状态栏通常不超过 48dp).
        val maxReasonable = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, res.displayMetrics).toInt()

        if (statusGuess in 1..maxReasonable) return statusGuess

        // [3] Finally return 0, no constant forced.
        // zh-CN: 最终返回 0, 仅依赖 context 且不强制指定常量.

        return 0
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun getNavigationBarHeightByDimen(context: Context, withComputedOnPreR: Boolean = true): Int {
        val res = context.resources

        // [1] If system declares not to show navigation bar, return 0 early (non-public resource, may be inaccurate).
        // zh-CN: 如果系统声明不显示导航栏, 尽早返回 0 (非公开资源, 可能不准确).

        val showNavResId = res.getIdentifier("config_showNavigationBar", "bool", "android")
        if (showNavResId > 0) runCatching {
            if (!res.getBoolean(showNavResId)) return 0
        }

        // [2] Priority read from internal dimen to try to cover portrait/landscape differences.
        // zh-CN: 优先从内部 dimen 读取, 尽量覆盖横竖屏差异.

        val isLandscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dimenNameCandidates = when (isLandscape) {
            true -> listOf("navigation_bar_height_landscape", "navigation_bar_height", "navigation_bar_height_portrait")
            else -> listOf("navigation_bar_height_portrait", "navigation_bar_height", "navigation_bar_height_landscape")
        }

        val dimenHeight = dimenNameCandidates.asSequence().map { res.queryDimen(it) }.firstOrNull { it > 0 } ?: 0
        if (dimenHeight > 0) return dimenHeight

        // [3] Try to estimate using DisplayMetrics on pre-Android R.
        // zh-CN: 在 Android R 以下, 尝试用 DisplayMetrics 估算.

        if (!withComputedOnPreR) return 0
        val (systemUiWidthInset, systemUiHeightInset) = getSystemUiInsetsOnPreR(context) ?: return 0

        // Status bar estimated value.
        // zh-CN: 状态栏估算值.
        val statusBar = getStatusBarHeightByDimen(context, false)

        val bottomNavGuess = (systemUiHeightInset - statusBar).coerceAtLeast(0)
        val sideNavGuess = systemUiWidthInset.coerceAtLeast(0)

        // A navigation bar will be more likely on the side in landscape, and more likely on the bottom in portrait.
        // zh-CN: 导航栏横屏更可能在侧边, 竖屏更可能在底部.
        val computed = maxOf(bottomNavGuess, sideNavGuess)

        // Prevent abnormal values (navigation bar usually not exceeding 96dp).
        // zh-CN: 防止异常值 (导航栏通常不超过 96dp).
        val maxReasonable = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 96f, res.displayMetrics).toInt()

        if (computed in 1..maxReasonable) return computed

        // [4] Finally return 0, no constant forced.
        // zh-CN: 最终返回 0, 仅依赖 context 且不强制指定常量.

        return 0
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun Resources.queryDimen(name: String, def: (() -> Number)? = null): Int = runCatching {
        val id = this.getIdentifier(name, "dimen", "android")
        return if (id > 0) this.getDimensionPixelSize(id) else 0
    }.getOrDefault(def?.invoke()?.toInt() ?: 0)

    @Suppress("DEPRECATION")
    private fun getSystemUiInsetsOnPreR(context: Context): Pair<Int, Int>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return null
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
        val display = wm.defaultDisplay ?: return null

        val real = DisplayMetrics().also { display.getRealMetrics(it) }
        val usable = DisplayMetrics().also { display.getMetrics(it) }

        val systemUiWidthInset = (real.widthPixels - usable.widthPixels).coerceAtLeast(0)
        val systemUiHeightInset = (real.heightPixels - usable.heightPixels).coerceAtLeast(0)

        return Pair(systemUiWidthInset, systemUiHeightInset)
    }

    enum class MODE(val key: String) {

        DAY(key(R.string.key_night_mode_always_off)),
        NIGHT(key(R.string.key_night_mode_always_on)),
        FOLLOW(key(R.string.key_night_mode_follow_system)),
        NULL(key(R.string.key_night_mode_null)),
        ;
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

    @SuppressLint("ClickableViewAccessibility")
    class TextSizeScaleDetector(context: Context, textView: TextView) : ScaleGestureDetector(context, TextSizeScaleListener(textView)) {

        init {
            textView.apply {
                setTextIsSelectable(true)
                isLongClickable = true
                isFocusableInTouchMode = true
                setOnTouchListener { v, event ->
                    this@TextSizeScaleDetector.onTouchEvent(event)
                    when {
                        event.pointerCount > 1 -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                        else -> false
                    }
                }
            }
        }

    }

    private class TextSizeScaleListener(private val textView: TextView) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        private val mMinTextSize = 4.0f
        private val mMaxTextSize = 72.0f

        private var mLastScaleFactor = 1.0f
        private var mLastTextSize = 0.0f

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentFactor = (floor((detector.scaleFactor * 10).toDouble()) / 10).toFloat()
            if (mLastTextSize <= 0) {
                mLastTextSize = getTextSize()
            }
            if (currentFactor > 0 && mLastScaleFactor != currentFactor) {
                val currentTextSize: Float = mLastTextSize + (if (currentFactor > mLastScaleFactor) 1 else -1)
                mLastTextSize = currentTextSize.coerceIn(mMinTextSize, mMaxTextSize)
                setTextSize(mLastTextSize)
                mLastScaleFactor = currentFactor
            }
            return super.onScale(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            mLastScaleFactor = 1.0f
            super.onScaleEnd(detector)
        }

        fun setTextSize(size: Float) {
            mLastTextSize = size
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }

        fun getTextSize(): Float = DisplayUtils.pxToSp(textView.textSize)

    }

}