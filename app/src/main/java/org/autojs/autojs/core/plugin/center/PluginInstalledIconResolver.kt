package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import org.autojs.autojs.util.ViewUtils

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
            ?: return runCatching { appInfo.loadIcon(pm) }.getOrNull()

        val packageContext = runCatching {
            context.createPackageContext(appInfo.packageName, 0)
        }.getOrNull() ?: return runCatching { appInfo.loadIcon(pm) }.getOrNull()

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
            ?: runCatching { appInfo.loadIcon(pm) }.getOrNull()
    }
}
