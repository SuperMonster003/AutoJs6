package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics

object PluginInstalledIconResolver {

    fun load(context: Context, appInfo: ApplicationInfo): Drawable? {
        val pm = context.packageManager
        val iconResId = appInfo.icon.takeIf { it != 0 }
            ?: appInfo.logo.takeIf { it != 0 }
            ?: return runCatching { appInfo.loadIcon(pm) }.getOrNull()

        val packageContext = runCatching {
            context.createPackageContext(appInfo.packageName, 0)
        }.getOrNull() ?: return runCatching { appInfo.loadIcon(pm) }.getOrNull()

        val packageConfig = Configuration(packageContext.resources.configuration)
        val hostNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
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
