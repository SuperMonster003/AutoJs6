package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toBitmap
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

object PluginInstalledIconResolver {
    private val transparentIconPackages = setOf(
        "io.github.supermonster003.autojs6.plugin.threestoneai",
        // Terra and Ember retain their published package IDs after their product/namespace rename.
        "io.github.supermonster003.autojs6.plugin.audioplayer",
        "io.github.supermonster003.autojs6.plugin.videoplayer",
        "io.github.supermonster003.autojs6.plugin.three.stove.agent",
        "io.github.supermonster003.autojs6.plugin.three.adapt.a11y",
    )

    /**
     * Identity of a locally loaded icon for change detection without comparing Drawable
     * instances: it only changes when the package build or the night mode changes.
     *
     * zh-CN: 本地已加载图标的标识, 用于在不比较 Drawable 实例的情况下检测变化:
     * 仅当包构建或夜间模式变化时才会改变.
     */
    fun signatureOf(packageName: String, versionCode: Long?, lastUpdateTime: Long?): String {
        val nightKey = if (ViewUtils.isNightModeEnabled) "night" else "day"
        return "$packageName:${versionCode ?: 0L}:${lastUpdateTime ?: 0L}:$nightKey"
    }

    fun load(context: Context, appInfo: ApplicationInfo): Drawable? {
        val pm = context.packageManager
        val fallbackIcon = { runCatching { appInfo.loadIcon(pm) }.getOrNull() }

        val packageContext = runCatching {
            context.createPackageContext(appInfo.packageName, 0)
        }.getOrNull()

        // Three plugins reserve ic_launcher for their transparent in-app artwork. The application
        // icon may instead be an adaptive launcher icon with an opaque background.
        @Suppress("DiscouragedApi")
        val transparentIcon = if (appInfo.packageName in transparentIconPackages) {
            packageContext?.resources?.getIdentifier("ic_launcher", "mipmap", appInfo.packageName) ?: 0
        } else 0
        val iconResId = transparentIcon.takeIf { it != 0 }
            ?: appInfo.icon.takeIf { it != 0 }
            ?: appInfo.logo.takeIf { it != 0 }

        if (iconResId == null || packageContext == null) {
            return fallbackIcon()?.rasterizeForPluginCenter(context)
        }

        // A retained ViewModel can replay this snapshot before discovery finishes after a
        // theme change. Prepare both modes here so that first bind needs no package I/O.
        // zh-CN: 主题切换后, 保留的 ViewModel 可能在重新发现完成前重放此快照.
        // 在后台准备两种模式, 使首次绑定无需访问包资源即可使用当前主题.
        val resolvedDay = resolveThemedIcon(context, packageContext, iconResId, nightModeEnabled = false)
        val resolvedNight = resolveThemedIcon(context, packageContext, iconResId, nightModeEnabled = true)
        val daySource = resolvedDay ?: resolvedNight ?: fallbackIcon() ?: return null
        val nightSource = resolvedNight ?: daySource
        val dayIcon = daySource.rasterizeForPluginCenter(context)
        val nightIcon = if (daySource === nightSource ||
            daySource.constantState?.let { it === nightSource.constantState } == true
        ) {
            dayIcon
        } else {
            nightSource.rasterizeForPluginCenter(context)
        }
        return if (dayIcon === nightIcon) dayIcon else ThemedInstalledIcon(dayIcon, nightIcon)
    }

    /** Selects an already prepared raster without package loading or drawing on the UI thread. */
    fun forCurrentMode(icon: Drawable): Drawable = forNightMode(icon, ViewUtils.isNightModeEnabled)

    internal fun forNightMode(icon: Drawable, nightModeEnabled: Boolean): Drawable =
        (icon as? ThemedInstalledIcon)?.forNightMode(nightModeEnabled) ?: icon

    private fun resolveThemedIcon(
        context: Context,
        packageContext: Context,
        iconResId: Int,
        nightModeEnabled: Boolean,
    ): Drawable? {
        val packageConfig = Configuration(packageContext.resources.configuration)
        val hostNightMode = if (nightModeEnabled) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        packageConfig.uiMode = (packageConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or hostNightMode

        val themedPackageContext = packageContext.createConfigurationContext(packageConfig)
        val densityDpi = context.resources.displayMetrics.densityDpi
            .takeIf { it > 0 }
            ?: DisplayMetrics.DENSITY_DEFAULT

        return runCatching {
            themedPackageContext.resources.getDrawableForDensity(iconResId, densityDpi, themedPackageContext.theme)
        }.getOrNull()
            ?: runCatching { themedPackageContext.resources.getDrawable(iconResId, themedPackageContext.theme) }.getOrNull()
    }

    /**
     * Repository callers run on Dispatchers.IO. Rasterizing adaptive/vector icons here keeps
     * CircleImageView from drawing every installed package icon synchronously during row bind.
     *
     * zh-CN: 仓库调用方运行于 Dispatchers.IO. 在此栅格化自适应或矢量图标,
     * 避免 CircleImageView 在列表项绑定期间同步绘制每个已安装包图标.
     */
    private fun Drawable.rasterizeForPluginCenter(context: Context): Drawable {
        if (this is BitmapDrawable && !bitmap.isRecycled) return this
        val size = context.resources.getDimensionPixelSize(R.dimen.plugin_center_item_side_length)
        return runCatching {
            BitmapDrawable(context.resources, toBitmap(size, size))
        }.getOrDefault(this)
    }

    private class ThemedInstalledIcon(
        private val dayIcon: Drawable,
        private val nightIcon: Drawable,
    ) : DrawableWrapper(if (ViewUtils.isNightModeEnabled) nightIcon else dayIcon) {
        fun forNightMode(nightModeEnabled: Boolean): Drawable = if (nightModeEnabled) nightIcon else dayIcon
    }
}
