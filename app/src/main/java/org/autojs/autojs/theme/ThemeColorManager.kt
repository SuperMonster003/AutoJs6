package org.autojs.autojs.theme

import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.view.View
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.color.ThemeColorDerivation
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import java.lang.ref.WeakReference

/**
 * Created by Stardust on May 10, 2016.
 */
object ThemeColorManager {

    @JvmField
    var defaultThemeColor = when {
        // R.color.md_blue_gray_900
        isInrt -> "#263238"
        // R.color.theme_color_default
        else -> "#FFDEAD"
    }.let { ThemeColor(ColorUtils.parseColor(it)) }

    @JvmStatic
    lateinit var currentThemeColor: ThemeColor

    @JvmStatic
    val colorPrimary: Int
        get() = currentThemeColor.colorPrimary

    private const val KEY_DERIVED_COLORS = "THEME_COLOR_DERIVED_ACCENT_AND_DARK"

    /**
     * Whether the accent and dark colors are derived from the primary color (D2, roadmap P4.6). Off (the default) keeps
     * them equal to the primary color, exactly as before.
     * zh-CN: 强调色与深色是否由主色派生 (D2, Roadmap P4.6). 关闭 (默认) 时二者与主色相同, 与此前完全一致.
     */
    @JvmStatic
    var isDerivationEnabled: Boolean
        get() = Pref.getBoolean(KEY_DERIVED_COLORS, false)
        set(value) = Pref.putBoolean(KEY_DERIVED_COLORS, value)

    /**
     * The theme color for [primary]: explicit [accent] / [dark] win; otherwise they are derived when
     * [isDerivationEnabled], or equal to [primary].
     * zh-CN: [primary] 对应的主题色: 显式给出的 [accent] / [dark] 优先; 否则在 [isDerivationEnabled] 时派生, 或与 [primary] 相同.
     */
    @JvmStatic
    @JvmOverloads
    fun resolve(primary: Int, accent: Int? = null, dark: Int? = null): ThemeColor {
        val (resolvedAccent, resolvedDark) = ThemeColorDerivation.accentAndDark(primary, accent, dark, isDerivationEnabled)
        return ThemeColor(primary, resolvedDark, resolvedAccent)
    }

    fun init() {
        ThemeColor.fromPreferences()?.let { currentThemeColor = it } ?: setThemeColor(defaultThemeColor)
    }

    @JvmStatic
    fun add(colorMutable: ThemeColorMutable) = ThemeColorMutableManager.add(colorMutable)

    @JvmStatic
    fun addViewBackground(titleBarView: View) = BackgroundColorManager.add(titleBarView)

    fun addPaint(paint: Paint) = PaintManager.add(paint)

    fun addActivityStatusBar(activity: Activity) = StatusBarManager.add(activity)

    fun addActivityNavigationBar(activity: Activity) {
        ThemeColorWidgetReferenceManager.add(object : ThemeColorMutableReference {
            val weakReference = WeakReference(activity)
            override fun setThemeColor(color: ThemeColor) {
                ViewUtils.setNavigationBarBackgroundColor(activity, color.colorPrimary)
            }

            override fun isNull() = weakReference.get() == null
        })
    }

    fun setThemeColor(themeColor: ThemeColor) {
        currentThemeColor = themeColor
        saveThemeColorIfNeeded()
        val color = themeColor.colorPrimary
        BackgroundColorManager.setColor(color)
        // The status bar takes the dark color, which equals the primary color unless derived (D2).
        // zh-CN: 状态栏使用深色; 未启用派生时深色与主色相同 (D2).
        StatusBarManager.setColor(themeColor.colorPrimaryDark)
        PaintManager.setColor(color)
        ThemeColorMutableManager.setColor(themeColor)
        ThemeColorWidgetReferenceManager.setColor(themeColor)
    }

    fun setThemeColor(color: Int) = setThemeColor(resolve(color))

    private fun saveThemeColorIfNeeded() {
        currentThemeColor.saveIn()
    }

    @JvmStatic
    @JvmOverloads
    fun isLuminanceLight(backgroundColorMatters: Boolean = true): Boolean {
        return ViewUtils.isLuminanceLight(colorPrimary, backgroundColorMatters)
    }

    @JvmStatic
    @JvmOverloads
    fun isLuminanceDark(backgroundColorMatters: Boolean = true): Boolean {
        return ViewUtils.isLuminanceDark(colorPrimary, backgroundColorMatters)
    }

    @JvmStatic
    fun getDayOrNightColorByLuminance(context: Context): Int {
        return ViewUtils.getDayOrNightColorByLuminance(context, colorPrimary)
    }

    @JvmStatic
    fun getDayOrNightColorResByLuminance(): Int {
        return ViewUtils.getDayOrNightColorResByLuminance(colorPrimary)
    }

    @JvmStatic
    fun setStatusBarBackgroundColor(activity: Activity) {
        ViewUtils.setStatusBarBackgroundColor(activity, currentThemeColor.colorPrimaryDark)
    }

    @JvmStatic
    fun setStatusBarIconLight(activity: Activity) {
        ViewUtils.setStatusBarIconLight(activity, isLuminanceDark())
    }

    /**
     * For pages whose status bar is colored by [setStatusBarBackgroundColor], i.e. with the dark color (D2).
     * zh-CN: 用于状态栏由 [setStatusBarBackgroundColor] 着色 (即使用深色) 的页面 (D2).
     */
    @JvmStatic
    fun setStatusBarIconLightByDarkColor(activity: Activity) {
        ViewUtils.setStatusBarIconLight(activity, ViewUtils.isLuminanceDark(currentThemeColor.colorPrimaryDark))
    }

    private object BackgroundColorManager {

        private val views = ThemeColorTargetRegistry<WeakReference<View>> { it.get() != null }

        fun add(view: View) {
            views.add(WeakReference(view))
            view.setBackgroundColor(currentThemeColor.colorPrimary)
        }

        fun setColor(color: Int) = views.forEach {
            it.get()?.setBackgroundColor(color)
        }
    }

    private object StatusBarManager {

        private val activityRefs = ThemeColorTargetRegistry<WeakReference<Activity>> { it.get() != null }

        fun add(activity: Activity) {
            activityRefs.add(WeakReference(activity))
            setStatusBarBackgroundColor(activity)
        }

        fun setColor(color: Int) {
            activityRefs.forEach { reference ->
                reference.get()?.also { activity ->
                    ViewUtils.setStatusBarBackgroundColor(activity, color)
                }
            }
        }
    }

    private object PaintManager {

        private val paints = ThemeColorTargetRegistry<WeakReference<Paint>> { it.get() != null }

        fun add(paint: Paint) {
            paint.color = currentThemeColor.colorPrimary
            paints.add(WeakReference(paint))
        }

        fun setColor(color: Int) {
            paints.forEach {
                it.get()?.color = color
            }
        }
    }

    private object ThemeColorWidgetReferenceManager {

        private val widgets = ThemeColorTargetRegistry<ThemeColorMutableReference> { !it.isNull }

        fun add(widget: ThemeColorMutableReference) {
            widget.setThemeColor(currentThemeColor)
            widgets.add(widget)
        }

        fun setColor(color: ThemeColor?) {
            widgets.forEach {
                if (!it.isNull) it.setThemeColor(color)
            }
        }
    }

    private object ThemeColorMutableManager {

        private val widgets = ThemeColorTargetRegistry<WeakReference<ThemeColorMutable>> { it.get() != null }

        fun add(widget: ThemeColorMutable) {
            widget.setThemeColor(currentThemeColor)
            widgets.add(WeakReference(widget))
        }

        fun setColor(color: ThemeColor?) {
            widgets.forEach {
                it.get()?.setThemeColor(color)
            }
        }
    }

}
