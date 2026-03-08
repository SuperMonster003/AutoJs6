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
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.text.TextUtils as AndroidTextUtils

/**
 * Created by Stardust on Jan 24, 2017.
 * Modified by SuperMonster003 as of Feb 2, 2026.
 * Modified by OpenAI ChatGPT (GPT-5.2 Thinking) as of Feb 7, 2026.
 */
@Suppress("unused")
object ViewUtils {

    private const val STATUS_BAR_SCRIM_TAG = "autojs6:status_bar_scrim"
    private const val NAV_BAR_SCRIM_TAG = "autojs6:nav_bar_scrim"

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

    val Toolbar.titleView: TextView?
        get() = this.findViewById(androidx.appcompat.R.id.action_bar_title)
            ?: runCatching {
                Toolbar::class.java.getDeclaredField("mTitleTextView").apply { isAccessible = true }.get(this@titleView) as TextView?
            }.getOrNull()

    val Toolbar.subtitleView: TextView?
        get() = this.findViewById(androidx.appcompat.R.id.action_bar_subtitle)
            ?: runCatching {
                Toolbar::class.java.getDeclaredField("mSubtitleTextView").apply { isAccessible = true }.get(this@subtitleView) as TextView?
            }.getOrNull()

    @JvmStatic
    @Suppress("FunctionName")
    fun <V : View?> `$`(view: View, @IdRes resId: Int): V {
        return view.findViewById(resId)
    }

    @JvmStatic
    @JvmOverloads
    fun setBackgroundColor(view: View?, color: Int, reAssignment: Boolean = true) {
        view ?: return
        val bg = view.background?.mutate()
        when (bg) {
            is GradientDrawable -> {
                bg.clearColorFilter()
                bg.setColor(color)
            }
            is ShapeDrawable -> {
                bg.clearColorFilter()
                bg.paint.color = color
            }
            is ColorDrawable -> {
                bg.clearColorFilter()
                bg.color = color
            }
            else -> bg?.let {
                it.clearColorFilter()
                DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
                DrawableCompat.setTint(it, color)
            }
        }
        if (reAssignment) view.background = bg
    }

    // @Comment by SuperMonster003 on Nov 31, 2025.
    //  ! Replaced with improved getStatusBarHeight methods.
    //  ! zh-CN: 已替换为已改进的 getStatusBarHeight 方法.
    //  !
    // FIXME by Stardust on Jan 23, 2018.
    //  ! Not working in some devices.
    //  ! https://github.com/hyb1996/Auto.js/issues/268
    //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
    //  ! 在一些设备上无法正常获取结果.
    //  ! 参阅: https://github.com/hyb1996/Auto.js/issues/268
    //  !
    //  # fun getStatusBarHeight(context: Context): Int {
    //  #     return context.resources.getIdentifier("status_bar_height", "dimen", "android").let { resourceId ->
    //  #         when (resourceId > 0) {
    //  #             true -> context.resources.getDimensionPixelSize(resourceId)
    //  #             else -> 0
    //  #         }
    //  #     }
    //  # }
    //  !
    /**
     * Get status bar height in pixels.
     *
     * Strategy and semantics:
     * 1. WindowInsets / WindowMetrics.
     *    - Uses Type.statusBars() as the primary source.
     *    - If ignoreVisibility is true, uses "ignoring visibility" insets to get a stable height even when the bar is hidden.
     *    - If ignoreVisibility is false, uses "visible" insets to reflect current visibility.
     *    - When ignoreVisibility is true, also considers Type.displayCutout() and returns max(statusBars.top, cutout.top).
     * 2. Visible frame top, optional.
     *    - Only used when ignoreVisibility is true.
     *    - Reads decorView.getWindowVisibleDisplayFrame().top, may be 0 in immersive/fullscreen.
     * 3. Computed (display diff), optional.
     *    - Enabled only when withComputed is true, and only on pre-Android R or when window is null.
     *    - Computes system UI vertical inset by (real metrics - usable metrics), then derives a status bar height.
     * 4. Internal dimen, optional.
     *    - Reads internal android dimen resources as an estimate, may not reflect runtime visibility.
     *
     * Important notes:
     * - If ignoreVisibility is false, this API is "visibility-pure":
     *   it will not fall back to visible frame, computed, or dimen.
     *   If the status bar is not visible or cannot be determined, it returns 0.
     * - If you need a safe top inset for layout/clicking (including display cutout), prefer ignoreVisibility=true.
     *
     * zh-CN: 获取状态栏高度, 单位为 px.
     *
     * 策略与语义说明:
     * 1. WindowInsets / WindowMetrics.
     *    - 以 Type.statusBars() 为主来源.
     *    - ignoreVisibility=true 时使用 "忽略可见性" 的 insets, 即使系统栏被隐藏也尽量返回稳定高度.
     *    - ignoreVisibility=false 时使用 "可见" 的 insets, 反映当前可见状态.
     *    - ignoreVisibility=true 时额外考虑 Type.displayCutout(), 返回 max(statusBars.top, cutout.top).
     * 2. 可见区域 top, 可选.
     *    - 仅在 ignoreVisibility=true 时启用.
     *    - 读取 decorView.getWindowVisibleDisplayFrame().top, 在沉浸式/全屏下可能为 0.
     * 3. computed (显示差值法), 可选.
     *    - 仅在 withComputed=true 且 pre-Android R 或 window==null 时启用.
     *    - 通过 real metrics 与 usable metrics 的差值计算系统 UI 垂直 inset, 再推导状态栏高度.
     * 4. 内部 dimen, 可选.
     *    - 读取 android 内部 dimen 作为估值, 可能不反映运行时可见性.
     *
     * 注意事项:
     * - ignoreVisibility=false 时该 API 语义更 "纯粹": 不会回退到可见区域, computed 或 dimen, 如果无法确定或不可见则返回 0.
     * - 如需要用于布局或点击的安全 top inset (包含刘海安全区), 建议使用 ignoreVisibility=true.
     */
    @JvmStatic
    @JvmOverloads
    fun getStatusBarHeight(
        context: Context,
        withComputed: Boolean = true,
        withDimen: Boolean = true,
        ignoreVisibility: Boolean = true,
    ): Int {
        val window = getWindowForContext(context)

        // [1] WindowInsets / WindowMetrics.
        // zh-CN: 通过 WindowInsets / WindowMetrics 获取状态栏高度.
        val statusTop = window
            ?.getInsetsCompat(WindowInsetsCompat.Type.statusBars(), ignoreVisibility)?.top
            ?: run {
                // R+ fallback: WindowMetrics (no Activity/Window needed).
                // zh-CN: R+ 降级方案: WindowMetrics (无需 Activity/Window).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getInsetsFromWindowMetrics(context, WindowInsetsCompat.Type.statusBars(), ignoreVisibility).top
                } else 0
            }

        if (ignoreVisibility) {
            // Consider display cutout only for "stable" semantics.
            // zh-CN: 仅在 "稳定语义" 下考虑刘海安全区.
            val cutoutTop = window
                ?.getInsetsCompat(WindowInsetsCompat.Type.displayCutout(), true)?.top
                ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        getInsetsFromWindowMetrics(context, WindowInsetsCompat.Type.displayCutout(), true).top
                    } else 0
                }
            val stableTop = maxOf(statusTop, cutoutTop)
            if (stableTop > 0) return stableTop
        } else {
            // Visible-only semantics.
            // zh-CN: 仅可见语义.
            if (statusTop > 0) return statusTop
            return 0
        }

        // [2] Visible frame top, only when ignoreVisibility is true.
        // zh-CN: 可见区域 top, 仅在 ignoreVisibility=true 时启用.
        val rectTop = window?.decorView?.let { decor ->
            Rect().also { rect -> decor.getWindowVisibleDisplayFrame(rect) }.top
        } ?: 0
        if (rectTop > 0) return rectTop

        // [3] Computed (display diff), only on pre-R or when window is null.
        // zh-CN: 显示差值法, 仅在 pre-R 或 window==null 时启用.
        if (withComputed && (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || window == null)) {
            val computed = getStatusBarHeightByDisplayDiff(context)
            if (computed > 0) return computed
        }

        // [4] Internal dimen (estimate).
        // zh-CN: 内部 dimen 估值.
        if (withDimen) {
            val dimen = getStatusBarHeightByDimen(context)
            if (dimen > 0) return dimen
        }

        // [5] Finally return 0, no constant forced.
        // zh-CN: 最终返回 0, 不强制指定常量.
        return 0
    }

    /**
     * Compute the status bar height (px) by the difference of display metrics on pre-Android R.
     *
     * Heuristic:
     * - If the vertical system-UI inset is already within a reasonable range, treat it as the status bar height.
     * - Otherwise, subtract an estimated navigation bar height (from internal dimen only) and validate again.
     *
     * zh-CN: 通过 DisplayMetrics 差值在 Android R 以下估算状态栏高度 (单位: px).
     *
     * 经验规则 (heuristic):
     * - 若系统 UI 的垂直 inset 已落在合理范围内, 则直接视为状态栏高度.
     * - 否则先减去导航栏高度的估值 (仅来自内部 dimen), 再进行一次范围校验.
     */
    @Suppress("DEPRECATION")
    private fun getStatusBarHeightByDisplayDiff(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return 0

        val insets = getSystemUiInsetsOnPreR(context) ?: return 0
        val (_, systemUiHeightInset) = insets

        val res = context.resources
        val maxReasonable = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            48f,
            res.displayMetrics
        ).toInt()

        // If it already looks like a status bar height, accept directly.
        // zh-CN: 如果看起来已经像状态栏高度, 直接接受.
        if (systemUiHeightInset in 1..maxReasonable) return systemUiHeightInset

        // Otherwise, try to remove an estimated nav bar height (dimen only).
        // zh-CN: 否则尝试减去导航栏估值 (仅 dimen), 再次校验.
        val navBar = getNavigationBarHeightByDimen(context, withComputedOnPreR = false).coerceAtLeast(0)
        val guess = (systemUiHeightInset - navBar).coerceAtLeast(0)

        return if (guess in 1..maxReasonable) guess else 0
    }

    /**
     * Read status bar height from internal android dimen resources.
     *
     * zh-CN: 从 android 内部 dimen 资源读取状态栏高度, 仅作为估值.
     */
    private fun getStatusBarHeightByDimen(context: Context): Int {
        val res = context.resources

        // Try to cover portrait/landscape differences.
        // zh-CN: 尝试覆盖横竖屏差异.
        val isLandscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val candidates = when {
            isLandscape -> listOf(
                "status_bar_height_landscape",
                "status_bar_height",
                "status_bar_height_default",
                "status_bar_height_portrait",
            )
            else -> listOf(
                "status_bar_height_portrait",
                "status_bar_height",
                "status_bar_height_default",
                "status_bar_height_landscape",
            )
        }

        return candidates
            .asSequence()
            .map { res.queryDimen(it) }
            .firstOrNull { it > 0 }
            ?: 0
    }

    /**
     * Get navigation bar thickness in pixels.
     *
     * Strategy and semantics:
     * 1. WindowInsets / WindowMetrics.
     *    - Uses Type.navigationBars() as the primary source.
     *    - If ignoreVisibility is true, uses "ignoring visibility" insets to get a stable thickness even when bars are hidden.
     *    - If ignoreVisibility is false, uses "visible" insets to reflect current visibility.
     * 2. Computed (display diff), optional.
     *    - Enabled only when withComputed is true, and only on pre-Android R or when window is null.
     *    - Computes system UI insets by (real metrics - usable metrics), then derives a navigation bar thickness.
     * 3. Internal dimen, optional.
     *    - Reads internal android dimen resources as an estimate, may not reflect runtime visibility.
     * 4. Gesture insets, optional.
     *    - Only used when ignoreVisibility is true.
     *    - Uses mandatorySystemGestures().bottom, then systemGestures().bottom as the last resort.
     *
     * Important notes:
     * - In landscape, the navigation bar may be on the left or right side. This API returns the maximum thickness of bottom, left, and right.
     * - If ignoreVisibility is false, this API is "visibility-pure": it will not fall back to computed, dimen, or gesture insets. If the navigation bar is not visible or cannot be determined, it returns 0.
     *
     * zh-CN: 获取导航栏厚度, 单位为 px.
     *
     * 策略与语义说明:
     * 1. WindowInsets / WindowMetrics.
     *    - 以 Type.navigationBars() 为主来源.
     *    - ignoreVisibility=true 时使用 "忽略可见性" 的 insets, 即使系统栏被隐藏也尽量返回稳定厚度.
     *    - ignoreVisibility=false 时使用 "可见" 的 insets, 反映当前可见状态.
     * 2. computed (显示差值法), 可选.
     *    - 仅在 withComputed=true 且 pre-Android R 或 window==null 时启用.
     *    - 通过 real metrics 与 usable metrics 的差值计算系统 UI insets, 再推导导航栏厚度.
     * 3. 内部 dimen, 可选.
     *    - 读取 android 内部 dimen 作为估值, 可能不反映运行时可见性.
     * 4. 手势 insets, 可选.
     *    - 仅在 ignoreVisibility=true 时启用.
     *    - 依次尝试 mandatorySystemGestures().bottom 与 systemGestures().bottom 作为最终兜底.
     *
     * 注意事项:
     * - 横屏时导航栏可能位于左右侧, 本 API 返回 bottom, left, right 三者的最大厚度.
     * - ignoreVisibility=false 时该 API 语义更 "纯粹": 不会回退到 computed, dimen 或 gestures, 如果无法确定或不可见则返回 0.
     */
    @JvmStatic
    @JvmOverloads
    fun getNavigationBarHeight(
        context: Context,
        withComputed: Boolean = true,
        withDimen: Boolean = true,
        ignoreVisibility: Boolean = true,
    ): Int {
        val window = getWindowForContext(context)

        // [1] WindowInsets / WindowMetrics.
        // zh-CN: 通过 WindowInsets / WindowMetrics 获取导航栏厚度.
        val thicknessFromNavBars = window?.let {
            val nav = it.getInsetsCompat(WindowInsetsCompat.Type.navigationBars(), ignoreVisibility)
            maxOf(nav.bottom, nav.left, nav.right)
        } ?: run {
            // R+ fallback: WindowMetrics (no Activity/Window needed).
            // zh-CN: R+ 降级方案: WindowMetrics (无需 Activity/Window).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val nav = getInsetsFromWindowMetrics(context, WindowInsetsCompat.Type.navigationBars(), ignoreVisibility)
                maxOf(nav.bottom, nav.left, nav.right)
            } else 0
        }
        if (thicknessFromNavBars > 0) return thicknessFromNavBars

        // [Pure visibility semantics] If ignoreVisibility is false, do not fall back to computed/dimen/gestures.
        // zh-CN: 语义纯粹化. ignoreVisibility=false 时不回退到 computed/dimen/gestures, 直接返回 0.
        if (!ignoreVisibility) return 0

        // [2] Computed (display diff), only on pre-R or when window is null.
        // zh-CN: 显示差值法, 仅在 pre-R 或 window==null 时启用.
        if (withComputed && (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || window == null)) {
            val computed = getNavBarHeightByDisplayDiff(context)
            if (computed > 0) return computed
        }

        // [3] Internal dimen (estimate).
        // zh-CN: 内部 dimen 估值.
        if (withDimen) {
            val dimen = getNavigationBarHeightByDimen(context)
            if (dimen > 0) return dimen
        }

        // [4] Mandatory gesture insets (bottom only).
        // zh-CN: 强制手势区域, 只取 bottom 以避免左右返回手势边缘导致语义失真.
        val bottomFromMandatory = window
            ?.getInsetsCompat(WindowInsetsCompat.Type.mandatorySystemGestures(), true)?.bottom
            ?: run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getInsetsFromWindowMetrics(context, WindowInsetsCompat.Type.mandatorySystemGestures(), true).bottom
                } else 0
            }
        if (bottomFromMandatory > 0) return bottomFromMandatory

        // [5] Gesture insets (bottom only).
        // zh-CN: 普通手势区域, 同样只取 bottom.
        val bottomFromGestures = window
            ?.getInsetsCompat(WindowInsetsCompat.Type.systemGestures(), true)?.bottom
            ?: run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getInsetsFromWindowMetrics(context, WindowInsetsCompat.Type.systemGestures(), true).bottom
                } else 0
            }
        if (bottomFromGestures > 0) return bottomFromGestures

        // [6] Finally return 0, no constant forced.
        // zh-CN: 最终返回 0, 不强制指定常量.
        return 0
    }

    /**
     * Compute the navigation bar thickness (px) by the difference of display metrics on pre-Android R.
     *
     * Heuristic:
     * - Compute system UI insets by (real metrics - usable metrics).
     * - Estimate the status bar height using internal dimen only (to avoid requiring WindowInsets),
     *   then treat the remaining vertical inset as a "bottom navigation bar" candidate.
     * - Also treat the horizontal inset as a "side navigation bar" candidate (common in landscape).
     * - Return the maximum of the two candidates, with a reasonable upper bound check.
     *
     * zh-CN: 通过 DisplayMetrics 差值在 Android R 以下估算导航栏厚度 (单位: px).
     *
     * 经验规则 (heuristic):
     * - 使用 (real metrics - usable metrics) 计算系统 UI 的 inset.
     * - 仅通过内部 dimen 估算状态栏高度 (避免依赖 WindowInsets),
     *   再把剩余的垂直 inset 作为 "底部导航栏" 的候选值.
     * - 同时把水平 inset 作为 "侧边导航栏" 的候选值 (横屏更常见).
     * - 取两者最大值, 并做合理上限校验后返回.
     */
    @Suppress("DEPRECATION")
    private fun getNavBarHeightByDisplayDiff(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return 0

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return 0
        val display = wm.defaultDisplay ?: return 0

        val real = DisplayMetrics().also { display.getRealMetrics(it) }
        val usable = DisplayMetrics().also { display.getMetrics(it) }

        val systemUiWidthInset = (real.widthPixels - usable.widthPixels).coerceAtLeast(0)
        val systemUiHeightInset = (real.heightPixels - usable.heightPixels).coerceAtLeast(0)

        // Estimate status bar height by dimen only, to avoid requiring WindowInsets.
        // zh-CN: 仅使用 dimen 估算状态栏高度, 避免依赖 WindowInsets.
        val statusBar = getStatusBarHeightByDimen(context).coerceAtLeast(0)

        val bottomGuess = (systemUiHeightInset - statusBar).coerceAtLeast(0)
        val sideGuess = systemUiWidthInset.coerceAtLeast(0)

        // In landscape, nav bar may be on the side; in portrait, more likely on the bottom.
        // zh-CN: 横屏导航栏更可能在侧边, 竖屏更可能在底部.
        val computed = maxOf(bottomGuess, sideGuess)

        // Prevent abnormal values.
        // zh-CN: 防止异常值.
        val maxReasonable = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            96f,
            context.resources.displayMetrics
        ).toInt()

        return if (computed in 1..maxReasonable) computed else 0
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun getNavigationBarHeightByDimen(context: Context, withComputedOnPreR: Boolean = true): Int {
        val res = context.resources

        // [1] Check if system declares to show navigation bar.
        // zh-CN: 检查系统是否声明显示导航栏.

        val showNavDeclared: Boolean? = runCatching {
            val id = res.getIdentifier("config_showNavigationBar", "bool", "android")
            if (id > 0) res.getBoolean(id) else null
        }.getOrNull()

        // [2] Priority read from internal dimen to try to cover portrait/landscape differences.
        // zh-CN: 优先从内部 dimen 读取, 尽量覆盖横竖屏差异.

        val isLandscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val candidates = when {
            isLandscape -> listOf(
                "navigation_bar_width",
                "navigation_bar_height_landscape",
                "navigation_bar_height",
                "navigation_bar_height_portrait",
            )
            else -> listOf(
                "navigation_bar_height_portrait",
                "navigation_bar_height",
                "navigation_bar_height_landscape",
            )
        }

        val dimenHeight = candidates.asSequence().map { res.queryDimen(it) }.firstOrNull { it > 0 } ?: 0

        if (showNavDeclared != false && dimenHeight > 0) return dimenHeight

        // [3] Try to estimate using DisplayMetrics on pre-Android R.
        // zh-CN: 在 Android R 以下, 尝试用 DisplayMetrics 估算.

        if (!withComputedOnPreR) return 0
        val (systemUiWidthInset, systemUiHeightInset) = getSystemUiInsetsOnPreR(context) ?: return 0

        // Status bar estimated value.
        // zh-CN: 状态栏估算值.
        val statusBar = getStatusBarHeightByDimen(context)
        val bottomGuess = (systemUiHeightInset - statusBar).coerceAtLeast(0)
        val sideGuess = systemUiWidthInset.coerceAtLeast(0)

        // A navigation bar will be more likely on the side in landscape, and more likely on the bottom in portrait.
        // zh-CN: 导航栏横屏更可能在侧边, 竖屏更可能在底部.
        val computed = maxOf(bottomGuess, sideGuess)

        // Prevent abnormal values (navigation bar usually not exceeding 96dp).
        // zh-CN: 防止异常值 (导航栏通常不超过 96dp).
        val maxReasonable = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 96f, res.displayMetrics).toInt()
        val computedOk = computed in 1..maxReasonable

        // If system explicitly says "no nav bar", only accept computed when it strongly indicates otherwise.
        // zh-CN: 如果系统明确声明 "无导航栏", 则仅在计算值强烈指示存在导航栏时才接受该计算值.
        return when {
            showNavDeclared == false -> if (computedOk) computed else 0
            computedOk -> computed
            else -> 0
        }
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun Resources.queryDimen(name: String): Int = runCatching {
        val id = getIdentifier(name, "dimen", "android")
        if (id > 0) getDimensionPixelSize(id) else 0
    }.getOrDefault(0)

    @Suppress("DEPRECATION")
    private fun getSystemUiInsetsOnPreR(context: Context): Pair<Int, Int>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return null
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
        val display = wm.defaultDisplay ?: return null

        val real = DisplayMetrics().also { display.getRealMetrics(it) }
        val usable = DisplayMetrics().also { display.getMetrics(it) }

        val wInset = (real.widthPixels - usable.widthPixels).coerceAtLeast(0)
        val hInset = (real.heightPixels - usable.heightPixels).coerceAtLeast(0)

        return Pair(wInset, hInset)
    }

    private fun Window.getInsetsCompat(typeMask: Int, ignoreVisibility: Boolean): Insets {
        val decor = decorView

        val compat = ViewCompat.getRootWindowInsets(decor)
            ?: decor.rootWindowInsets?.let { WindowInsetsCompat.toWindowInsetsCompat(it, decor) }
            ?: return Insets.NONE

        return if (ignoreVisibility) {
            compat.getInsetsIgnoringVisibility(typeMask)
        } else {
            compat.getInsets(typeMask)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getInsetsFromWindowMetrics(context: Context, typeMask: Int, ignoreVisibility: Boolean): Insets {
        val wm = context.getSystemService(WindowManager::class.java) ?: return Insets.NONE
        val wi = wm.currentWindowMetrics.windowInsets
        val compat = WindowInsetsCompat.toWindowInsetsCompat(wi)
        return if (ignoreVisibility) compat.getInsetsIgnoringVisibility(typeMask) else compat.getInsets(typeMask)
    }

    private fun getWindowForContext(context: Context): Window? = when (context) {
        is Activity -> context.window
        is ContextWrapper -> getWindowForContext(context.baseContext)
        else -> null
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
        val scrim = decor.children.find { it.tag == STATUS_BAR_SCRIM_TAG }
            ?: FrameLayout(activity).also {
                it.tag = STATUS_BAR_SCRIM_TAG
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
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightNavigationBars = !isLight
    }

    fun setNavigationBarIconLightByColorLuminance(activity: Activity, @ColorInt referenceBackgroundColor: Int) {
        val shouldBeLight = isLuminanceDark(referenceBackgroundColor)
        setNavigationBarIconLight(activity, shouldBeLight)
    }

    fun setNavigationBarBackgroundColor(activity: Activity, color: Int) {
        val window = activity.window

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.navigationBarColor = color
            return
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        val scrim = (content.findViewWithTag(NAV_BAR_SCRIM_TAG) ?: run {
            View(activity).apply {
                tag = NAV_BAR_SCRIM_TAG
                // Avoid intercepting touch/accessibility focus.
                // zh-CN: 避免拦截触摸/无障碍焦点.
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

                layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT,
                    0,
                ).apply {
                    gravity = Gravity.BOTTOM
                }

                // Key: listener is attached to scrim itself, not touching decorView.
                // zh-CN: 关键: listener 挂在 scrim 自己身上, 不去动 decorView.
                ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                    val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val tap = insets.getInsets(WindowInsetsCompat.Type.tappableElement())
                    val bottom = maxOf(nav.bottom, tap.bottom)

                    v.updateLayoutParams<FrameLayout.LayoutParams> {
                        height = bottom
                        gravity = Gravity.BOTTOM
                    }
                    insets
                }
            }.also { created ->
                content.addView(created)
                created.requestApplyInsetsWhenAttached()
            }
        })

        // Update color without depending on insets callback (avoid "probabilistic ineffectiveness").
        // zh-CN: 更新颜色不依赖 insets 回调 (避免 "概率不生效").
        scrim.setBackgroundColor(color)
        scrim.bringToFront()
    }

    // If requestApplyInsets() is called when view is not attached, it will be discarded.
    // Need to wait until attached and request again to reliably trigger insets dispatch.
    // zh-CN:
    // 如果 view 未 attach 时调用 requestApplyInsets() 会被丢弃.
    // 需要等 attach 后再请求一次, 才能稳定触发 insets 分发.
    @Suppress("DEPRECATION")
    private fun View.requestApplyInsetsWhenAttached() {
        if (ViewCompat.isAttachedToWindow(this)) {
            ViewCompat.requestApplyInsets(this)
        } else {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }

    @JvmStatic
    @JvmOverloads
    fun FloatingActionButton.excludeFloatingActionButtonFromBottomNavigationBar(extraMarginBottomDp: Float = 16F) {
        val fab = this
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
    fun View.excludePaddingClippableViewFromBottomNavigationBar(extraPaddingBottomDp: Float = 0F, clipToPadding: Boolean = false) {
        val view = this
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, 0, 0, DisplayUtils.dpToPx(extraPaddingBottomDp).roundToInt() + bottomInset)
            runCatching { view.javaClass.getMethod("setClipToPadding", Boolean::class.java).invoke(view, clipToPadding) }
            insets
        }
    }

    @JvmStatic
    fun Activity.excludeContentViewFromHorizontalNavigationBar() {
        val activity = this
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

    fun Toolbar.setTitlesTextColorByThemeColorLuminance(context: Context) {
        this.setTitlesTextColorByColorLuminance(context, ThemeColorManager.colorPrimary)
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

    fun ImageView.colorFilterWithDesaturateOrNull(isOn: Boolean, alpha: Float = 0.5F) {
        if (isOn) {
            this.colorFilter = null
        } else {
            this.colorFilterWithDesaturate(alpha)
        }
    }

    fun ImageView.colorFilterWithDesaturate(alpha: Float = 0.5F) {
        // Construct the desaturation matrix.
        // zh-CN: 构造灰度矩阵.
        val desaturate = ColorMatrix().apply { setSaturation(0f) }
        // Construct the alpha scaling matrix.
        // zh-CN: 构造透明度缩放矩阵.
        val alphaMatrix = ColorMatrix().apply { setScale(1f, 1f, 1f, alpha) }
        // Concatenate: first desaturate, then apply alpha.
        // zh-CN: 叠加: 先灰度, 再透明度.
        desaturate.postConcat(alphaMatrix)
        this.colorFilter = ColorMatrixColorFilter(desaturate)
    }

    @JvmStatic
    fun Drawable.toCircular(
        context: Context,
        sizePx: Int,
        borderWidthPx: Int = 0,
        borderColor: Int = Color.TRANSPARENT,
    ): Drawable {
        val src = this
        val bmp = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bmp)

        // First stretch/center draw the source drawable to sizePx * sizePx bitmap.
        // zh-CN: 先把源 drawable 拉伸/居中绘制到 sizePx * sizePx 的位图上.
        val tmp = createBitmap(sizePx, sizePx)
        Canvas(tmp).apply {
            val w = src.intrinsicWidth.takeIf { it > 0 } ?: sizePx
            val h = src.intrinsicHeight.takeIf { it > 0 } ?: sizePx
            val scale = min(sizePx / w.toFloat(), sizePx / h.toFloat())
            val dw = (w * scale).roundToInt()
            val dh = (h * scale).roundToInt()
            val left = (sizePx - dw) / 2
            val top = (sizePx - dh) / 2
            src.setBounds(left, top, left + dw, top + dh)
            src.draw(this)
        }

        // Draw circle using BitmapShader.
        // zh-CN: 用 BitmapShader 画圆.
        val shader = BitmapShader(tmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            this.shader = shader
        }
        val radius = sizePx / 2f
        val contentRadius = radius - borderWidthPx.coerceAtLeast(0)
        canvas.drawCircle(radius, radius, contentRadius, paint)

        // Optional stroke.
        // zh-CN: 可选描边.
        if (borderWidthPx > 0) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = borderColor
                strokeWidth = borderWidthPx.toFloat()
            }
            canvas.drawCircle(radius, radius, radius - borderWidthPx / 2f, stroke)
        }

        return bmp.toDrawable(context.resources)
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
    fun showToast(context: Context, string: CharSequence?) = showToast(context, string, false)

    @JvmStatic
    fun showToast(context: Context, string: CharSequence?, isLong: Boolean) {
        string ?: return
        when {
            Looper.getMainLooper() == Looper.myLooper() -> {
                Toast.makeText(context, string, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
            else -> GlobalAppContext.post {
                Toast.makeText(context, string, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JvmStatic
    fun showSnack(view: View, stringRes: Int) = showSnack(view, stringRes, false)

    @JvmStatic
    fun showSnack(view: View, stringRes: Int, isLong: Boolean) {
        try {
            Snackbar.make(view, stringRes, if (isLong) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            ScriptRuntime.popException(e.message)
        }
    }

    @JvmStatic
    fun showSnack(view: View, string: CharSequence?) = showSnack(view, string, false)

    @JvmStatic
    fun showSnack(view: View, string: CharSequence?, isLong: Boolean) {
        string ?: return
        try {
            Snackbar.make(view, string, if (isLong) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            ScriptRuntime.popException(e.message)
        }
    }

    @JvmStatic
    fun showSnack(view: View, stringRes: Int, duration: Int) {
        try {
            Snackbar.make(view, stringRes, duration).show()
        } catch (e: IllegalStateException) {
            ScriptRuntime.popException(e.message)
        }
    }

    @JvmStatic
    fun showSnack(view: View, string: CharSequence?, duration: Int) {
        string ?: return
        try {
            Snackbar.make(view, string, duration).show()
        } catch (e: IllegalStateException) {
            ScriptRuntime.popException(e.message)
        }
    }

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

    fun PopupMenu.setForceShowIconCompat() {
        val popup = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true)
        } else {
            runCatching {
                @SuppressLint("DiscouragedPrivateApi")
                val field = PopupMenu::class.java.getDeclaredField("mPopup")
                field.isAccessible = true
                // MenuPopupHelper
                val helper = field.get(popup)
                val method = helper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                method.invoke(helper, true)
            }
        }
    }

    @JvmStatic
    fun Toolbar.setOnTitleViewClickListener(l: View.OnClickListener?) {
        this.setOnTitleViewClickListener(l, withFallback = true)
    }

    @JvmStatic
    fun Toolbar.setOnTitleViewClickListener(l: View.OnClickListener?, withFallback: Boolean) {
        this.onceGlobalLayout {
            this.titleView?.setOnClickListener(l) ?: run { if (withFallback) this.setOnClickListener(l) }
        }
    }

    @JvmStatic
    fun Toolbar.setOnSubtitleViewClickListener(l: View.OnClickListener?) {
        this.onceGlobalLayout {
            this.subtitleView?.setOnClickListener(l)
        }
    }

    @JvmStatic
    fun Toolbar.setOnTitleViewLongClickListener(l: View.OnLongClickListener?) {
        this.setOnTitleViewLongClickListener(l, withFallback = true)
    }

    @JvmStatic
    fun Toolbar.setOnTitleViewLongClickListener(l: View.OnLongClickListener?, withFallback: Boolean) {
        this.onceGlobalLayout {
            this.titleView?.setOnLongClickListener(l) ?: run { if (withFallback) this.setOnLongClickListener(l) }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun TextView.setLinesEllipsizedIndividually(
        lines: List<CharSequence>,
        lineSpacing: Float = 0f,
        where: AndroidTextUtils.TruncateAt = AndroidTextUtils.TruncateAt.MIDDLE,
    ) {
        post {
            val avail = width - paddingLeft - paddingRight
            if (avail <= 0) {
                post { setLinesEllipsizedIndividually(lines, lineSpacing, where) }
                return@post
            }

            setLineSpacing(0f, lineSpacing)

            val out = buildString {
                lines.forEachIndexed { idx, s ->
                    val e = AndroidTextUtils.ellipsize(s, paint, avail.toFloat(), where)
                    if (idx > 0) append('\n')
                    append(e)
                }
            }

            setSingleLine(false)
            ellipsize = null

            text = out
        }
    }

    /**
     * Install a ripple forwarder that makes full-row background react to touches on specific children,
     * while preserving RecyclerView-like delayed pressed behavior.
     * zh-CN:
     * 安装 Ripple 转发器, 让整行背景响应指定子视图的触摸,
     * 同时尽量模拟 RecyclerView 的延迟 pressed 行为.
     */
    fun installFullRowRippleForwarder(
        parent: View,
        vararg touchSources: View,
        pressDelayMillis: Int = ViewConfiguration.getTapTimeout(),
    ) {
        // Ensure the parent can show stateful background/foreground.
        // zh-CN: 确保父视图可以显示带状态的 background/foreground.
        parent.isClickable = true
        parent.isFocusable = true

        val vc = ViewConfiguration.get(parent.context)

        // Movement threshold to decide "scrolling" vs "tapping".
        // zh-CN: 用于区分 "滚动" 与 "点击" 的移动阈值.
        val touchSlop = vc.scaledTouchSlop

        // Duration used by framework to show a short pressed state for quick taps.
        // zh-CN: 系统用于 "快速点击" 时短暂显示 pressed 的持续时间.
        val pressedStateDuration = ViewConfiguration.getPressedStateDuration()

        // Reusable objects to avoid allocations in hot paths.
        // zh-CN: 复用对象, 避免高频路径分配.
        val parentLoc = IntArray(2)
        val childHitRect = Rect()

        touchSources.forEach { child ->
            child.setOnTouchListener(object : View.OnTouchListener {

                // Track one gesture stream.
                // zh-CN: 跟踪一次手势流.
                private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID

                // Down positions in raw (screen) coordinates.
                // zh-CN: DOWN 时的 raw (屏幕) 坐标.
                private var downRawX: Float = 0f
                private var downRawY: Float = 0f

                // Last known raw coordinates for hotspot update.
                // zh-CN: 用于更新 hotspot 的最近 raw 坐标.
                private var lastRawX: Float = 0f
                private var lastRawY: Float = 0f

                // Whether we have applied parent pressed state.
                // zh-CN: 是否已应用父视图 pressed 状态.
                private var pressedApplied: Boolean = false

                // Whether we are still waiting to apply pressed (pre-pressed).
                // zh-CN: 是否仍处于等待 pressed 的 pre-pressed 状态.
                private var prePressed: Boolean = false

                // Runnable to apply pressed after delay.
                // zh-CN: 延迟触发 pressed 的 Runnable.
                private val applyPressedRunnable = Runnable {
                    if (!prePressed) {
                        return@Runnable
                    }
                    if (!isInsideViewWithSlop(child, lastRawX, lastRawY, touchSlop, childHitRect)) {
                        prePressed = false
                        return@Runnable
                    }
                    applyPressedNow()
                }

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            activePointerId = event.getPointerId(0)

                            downRawX = event.rawX
                            downRawY = event.rawY
                            lastRawX = event.rawX
                            lastRawY = event.rawY

                            pressedApplied = false
                            prePressed = true

                            // Delay pressed to mimic scrolling container behavior.
                            // zh-CN: 延迟 pressed, 模拟可滚动容器的行为.
                            parent.removeCallbacks(applyPressedRunnable)
                            parent.postDelayed(applyPressedRunnable, pressDelayMillis.toLong())
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val idx = event.findPointerIndex(activePointerId)
                            if (idx < 0) {
                                return false
                            }

                            lastRawX = event.rawX
                            lastRawY = event.rawY

                            // If moved too much, treat as scroll and cancel.
                            // zh-CN: 如果移动过大, 视为滚动并取消.
                            val movedTooMuch =
                                abs(lastRawX - downRawX) > touchSlop || abs(lastRawY - downRawY) > touchSlop

                            if (movedTooMuch) {
                                cancelPressed()
                                return false
                            }

                            // If pressed is already applied, keep hotspot in sync.
                            // zh-CN: 若 pressed 已应用, 持续同步 hotspot.
                            if (pressedApplied) {
                                updateHotspot(lastRawX, lastRawY)
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            val idx = event.findPointerIndex(activePointerId)
                            if (idx < 0) {
                                cancelPressed()
                                return false
                            }

                            lastRawX = event.rawX
                            lastRawY = event.rawY

                            // If we never applied pressed (quick tap), apply briefly then clear.
                            // zh-CN: 如果尚未应用 pressed (快速点击), 短暂应用后清除.
                            if (prePressed && !pressedApplied) {
                                parent.removeCallbacks(applyPressedRunnable)

                                if (isInsideViewWithSlop(child, lastRawX, lastRawY, touchSlop, childHitRect)) {
                                    applyPressedNow()
                                    parent.postDelayed(
                                        { parent.isPressed = false },
                                        pressedStateDuration.toLong(),
                                    )
                                } else {
                                    cancelPressed()
                                }
                            } else {
                                // Normal end of gesture.
                                // zh-CN: 正常结束手势.
                                parent.removeCallbacks(applyPressedRunnable)
                                // Defer clearing pressed state until after this UP is dispatched to the child view.
                                // zh-CN: 将清除 pressed 的时机延后到本次 UP 分发给子视图之后.
                                parent.post {
                                    parent.isPressed = false
                                }
                                prePressed = false
                                pressedApplied = false
                            }

                            activePointerId = MotionEvent.INVALID_POINTER_ID
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            cancelPressed()
                            activePointerId = MotionEvent.INVALID_POINTER_ID
                        }
                    }

                    // Do not consume, so child's click/long-click keep working.
                    // zh-CN: 不消费事件, 保持子视图 click/long-click 正常工作.
                    return false
                }

                /**
                 * Apply pressed immediately and update hotspot.
                 * zh-CN: 立即应用 pressed, 并更新 hotspot.
                 */
                private fun applyPressedNow() {
                    pressedApplied = true
                    prePressed = false
                    parent.isPressed = true
                    updateHotspot(lastRawX, lastRawY)
                }

                /**
                 * Cancel any pending pressed state and clear current pressed state.
                 * zh-CN: 取消所有待触发的 pressed, 并清除当前 pressed 状态.
                 */
                private fun cancelPressed() {
                    parent.removeCallbacks(applyPressedRunnable)
                    parent.isPressed = false
                    prePressed = false
                    pressedApplied = false
                }

                /**
                 * Update parent's ripple hotspot based on raw coordinates.
                 * zh-CN: 基于 raw 坐标更新父视图的 Ripple hotspot.
                 */
                private fun updateHotspot(rawX: Float, rawY: Float) {
                    parent.getLocationOnScreen(parentLoc)
                    val hx = rawX - parentLoc[0]
                    val hy = rawY - parentLoc[1]

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        parent.drawableHotspotChanged(hx, hy)
                        parent.background?.setHotspot(hx, hy)
                        parent.foreground?.setHotspot(hx, hy)
                    }
                }
            })
        }
    }

    /**
     * Check whether a raw (screen) coordinate is inside the view bounds with slop tolerance.
     * zh-CN: 判断 raw (屏幕) 坐标是否在视图边界内, 并允许一定 slop 容差.
     */
    private fun isInsideViewWithSlop(
        view: View,
        rawX: Float,
        rawY: Float,
        slop: Int,
        outRect: Rect,
    ): Boolean {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)

        val xInView = rawX - loc[0]
        val yInView = rawY - loc[1]

        outRect.set(
            -slop,
            -slop,
            view.width + slop,
            view.height + slop,
        )
        return outRect.contains(xInView.toInt(), yInView.toInt())
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

    class DelayedLongPressTouchListener(context: Context, longPressDelayMs: Long) : View.OnTouchListener {

        private val longPressDelayMs: Long
        private val touchSlop: Int

        private var downX = 0f
        private var downY = 0f
        private var tracking = false
        private var longPressed = false
        private var pendingLongPress: Runnable? = null

        init {
            this.longPressDelayMs = max(0, longPressDelayMs)
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop()
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean =
            when (event.getActionMasked()) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.getX()
                    downY = event.getY()
                    tracking = true
                    longPressed = false
                    v.setPressed(true)

                    cancelPending(v)
                    pendingLongPress = Runnable {
                        if (tracking && v.isPressed()) {
                            longPressed = v.performLongClick()
                        }
                    }
                    v.postDelayed(pendingLongPress, longPressDelayMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (tracking) {
                        val dx = abs(event.getX() - downX)
                        val dy = abs(event.getY() - downY)
                        if (dx > touchSlop || dy > touchSlop) {
                            cancelGesture(v)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    cancelPending(v)
                    v.setPressed(false)

                    val shouldClick = tracking && !longPressed
                    tracking = false
                    if (shouldClick) {
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelGesture(v)
                    true
                }
                else -> false
            }

        private fun cancelPending(v: View) {
            if (pendingLongPress != null) {
                v.removeCallbacks(pendingLongPress)
                pendingLongPress = null
            }
        }

        private fun cancelGesture(v: View) {
            cancelPending(v)
            v.setPressed(false)
            tracking = false
            longPressed = false
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