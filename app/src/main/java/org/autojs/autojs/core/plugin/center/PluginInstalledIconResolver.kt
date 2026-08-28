package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toBitmap
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

object PluginInstalledIconResolver {

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
        val iconResId = appInfo.icon.takeIf { it != 0 }
            ?: appInfo.logo.takeIf { it != 0 }
        val fallbackIcon = { runCatching { appInfo.loadIcon(pm) }.getOrNull() }

        val packageContext = runCatching {
            context.createPackageContext(appInfo.packageName, 0)
        }.getOrNull()

        val resolved = if (iconResId == null || packageContext == null) {
            fallbackIcon()
        } else {
            resolveThemedIcon(context, packageContext, iconResId) ?: fallbackIcon()
        }
        return resolved?.rasterizeForPluginCenter(context)
    }

    private fun resolveThemedIcon(
        context: Context,
        packageContext: Context,
        iconResId: Int,
    ): Drawable? {
        val packageConfig = Configuration(packageContext.resources.configuration)
        val hostNightMode = if (ViewUtils.isNightModeEnabled) {
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
}
