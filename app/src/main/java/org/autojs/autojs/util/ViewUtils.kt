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
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
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
import kotlin.math.roundToInt

/**
 * Created by Stardust on Jan 24, 2017.
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

    // FIXME by Stardust on Jan 23, 2018.
    //  ! Not working in some devices.
    //  ! https://github.com/hyb1996/Auto.js/issues/268
    //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
    //  ! 在一些设备上无法正常获取结果.
    //  ! 参阅: https://github.com/hyb1996/Auto.js/issues/268
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    @JvmStatic
    @JvmOverloads
    fun getStatusBarHeightByDimen(context: Context, withFallback: Boolean = true): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val dimenStatusBarHeight = when (resourceId > 0) {
            true -> context.resources.getDimensionPixelSize(resourceId)
            else -> 0
        }
        return when {
            dimenStatusBarHeight > 0 -> dimenStatusBarHeight
            withFallback -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()
            else -> 0
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getStatusBarHeightByWindow(context: Context, withFallback: Boolean = true): Int {
        val windowStatusBarHeight = getWindowForContext(context)?.decorView?.let { decorView ->
            Rect().also { rect ->
                decorView.getWindowVisibleDisplayFrame(rect)
            }.top
        } ?: 0
        return when {
            windowStatusBarHeight > 0 -> windowStatusBarHeight
            withFallback -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()
            else -> 0
        }
    }

    fun getTitleBarHeight(context: Context): Int {
        val window = getWindowForContext(context)
        val contentViewTop = window?.findViewById<View>(Window.ID_ANDROID_CONTENT)?.top ?: 0
        return contentViewTop - getStatusBarHeightByWindow(context)
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
    fun isStatusBarAppearanceLight(activity: Activity): Boolean {
        return !isStatusBarAppearanceDark(activity)
    }

    @JvmStatic
    fun isStatusBarAppearanceDark(activity: Activity): Boolean {
        @Suppress("DEPRECATION")
        return hasSystemUiVisibility(activity, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
    }

    fun setStatusBarAppearanceLight(activity: Activity, isLight: Boolean) {
        @Suppress("DEPRECATION")
        when {
            isStatusBarAppearanceLight(activity) == isLight -> return
            isLight -> removeSystemUiVisibility(activity, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            else -> appendSystemUiVisibility(activity, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        }
    }

    fun setStatusBarAppearanceLightByColorLuminance(activity: Activity, @ColorInt aimColor: Int) {
        val shouldBeLight = isLuminanceDark(aimColor)
        setStatusBarAppearanceLight(activity, shouldBeLight)
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

    @RequiresApi(Build.VERSION_CODES.O)
    @JvmStatic
    fun isNavigationBarAppearanceLight(activity: Activity): Boolean {
        return !isNavigationBarAppearanceDark(activity)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @JvmStatic
    fun isNavigationBarAppearanceDark(activity: Activity): Boolean {
        @Suppress("DEPRECATION")
        return hasSystemUiVisibility(activity, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setNavigationBarAppearanceLight(activity: Activity, isLight: Boolean) {
        @Suppress("DEPRECATION")
        when {
            isNavigationBarAppearanceLight(activity) == isLight -> return
            isLight -> removeSystemUiVisibility(activity, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            else -> appendSystemUiVisibility(activity, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        }
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
                    navBarInsets.bottom, // 导航栏高度
                ).apply { gravity = Gravity.BOTTOM }
                setBackgroundColor(color)
            }
            contentView?.addView(navBarOverlay)
            view.onApplyWindowInsets(insets)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun excludeFloatingActionButtonFromNavigationBar(fab: FloatingActionButton, extraMarginBottomDp: Float = 16F) {
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.layoutParams.runCatching {
                javaClass.getField("bottomMargin")
                    .setInt(this, DisplayUtils.dpToPx(extraMarginBottomDp).roundToInt() + bottomInset)
            }
            insets
        }
    }

    @JvmStatic
    @JvmOverloads
    fun excludePaddingClippableViewFromNavigationBar(view: View, extraPaddingBottomDp: Float = 0F, clipToPadding: Boolean = false) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(0, 0, 0, DisplayUtils.dpToPx(extraPaddingBottomDp).roundToInt() + bottomInset)
            runCatching { view.javaClass.getMethod("setClipToPadding", Boolean::class.java).invoke(view, clipToPadding) }
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
        val isConfigTakenAsLight = !isConfigDark

        getWindowInsetsController(activity).apply {
            isAppearanceLightStatusBars = isConfigTakenAsLight
            isAppearanceLightNavigationBars = isConfigTakenAsLight
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
     * 显示软键盘.
     *
     * @param target 需要获取输入的视图 (如 EditText)
     * @param useForced 是否使用 SHOW_FORCED 方式强制弹出, 默认 false, 代表由系统自行判断
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
     * 隐藏软键盘.
     *
     * @param target 当前持有输入焦点的视图, 也可以是任意位于同一窗口的 View
     */
    @JvmStatic
    fun hideSoftInput(target: View) {
        val imm = target.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(target.windowToken, 0)
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