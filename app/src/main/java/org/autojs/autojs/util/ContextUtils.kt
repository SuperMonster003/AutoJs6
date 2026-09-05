package org.autojs.autojs.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import org.autojs.autojs.core.pref.Language

object ContextUtils {

    /**
     * A context whose resources follow the host app's own language and night mode instead of the
     * system's. AppCompat applies both settings to its activities only, so UI built from an
     * application or service context (floating windows, their dialogs and toasts) would otherwise
     * show the system language and dark mode. Only the two fields are overridden; everything else
     * (orientation, screen size, font scale, ...) keeps following the base context.
     *
     * zh-CN: 资源跟随宿主应用自身语言与夜间模式 (而非系统设置) 的 context. AppCompat 只对其 Activity 应用这两项设置,
     * 由 application / service context 构建的 UI (悬浮窗及其对话框, toast) 否则会显示系统语言与深色模式.
     * 仅覆盖这两个字段, 其余 (方向, 屏幕尺寸, 字体缩放等) 继续跟随 base context.
     */
    @JvmStatic
    fun Context.withHostConfiguration(): Context {
        val override = Configuration().apply {
            uiMode = resolveHostNightFlag(resources.configuration)
            Language.getPrefLanguageOrNull()?.takeUnless { it.isAuto() }?.let { setLocale(it.locale) }
        }
        return createConfigurationContext(override)
    }

    /** The night flag the host app's activities resolve to, mirroring AppCompat's default night mode. zh-CN: 宿主应用 Activity 实际采用的夜间标志, 与 AppCompat 的默认夜间模式一致. */
    private fun resolveHostNightFlag(systemConfiguration: Configuration) = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
        AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
        else -> systemConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun Context.requireActivity(): Activity {
        return this.findActivity() ?: throw IllegalArgumentException("Context cannot be cast to Activity")
    }

    @JvmStatic
    fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    @ColorInt
    fun Context.getColorInNightMode(
        @ColorRes colorRes: Int,
        nightMode: Int,
    ): Int {
        require(
            nightMode == Configuration.UI_MODE_NIGHT_YES ||
                    nightMode == Configuration.UI_MODE_NIGHT_NO
        ) {
            "nightMode must be UI_MODE_NIGHT_YES or UI_MODE_NIGHT_NO"
        }

        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }

        val themedContext = createConfigurationContext(config)
        return ContextCompat.getColor(themedContext, colorRes)
    }

    @ColorInt
    fun Context.getLightModeColor(@ColorRes colorRes: Int): Int =
        getColorInNightMode(colorRes, Configuration.UI_MODE_NIGHT_NO)

    @ColorInt
    fun Context.getDarkModeColor(@ColorRes colorRes: Int): Int =
        getColorInNightMode(colorRes, Configuration.UI_MODE_NIGHT_YES)

    @ColorInt
    fun Context.getColorForMode(
        @ColorRes colorRes: Int,
        darkMode: Boolean,
    ): Int {
        return getColorInNightMode(
            colorRes,
            if (darkMode) Configuration.UI_MODE_NIGHT_YES
            else Configuration.UI_MODE_NIGHT_NO
        )
    }

    fun Context.getColorsForMode(
        darkMode: Boolean,
        @ColorRes colorResArray: Array<Int>,
    ): Array<Int> {
        return colorResArray.map { getColorForMode(it, darkMode) }.toTypedArray()
    }
}