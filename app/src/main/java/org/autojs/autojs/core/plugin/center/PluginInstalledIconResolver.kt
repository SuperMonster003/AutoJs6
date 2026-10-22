package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.util.DisplayMetrics
import android.util.TypedValue
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
        val fallbackIcon = { runCatching { appInfo.loadIcon(pm) }.getOrNull() }

        val packageContext = runCatching {
            context.createPackageContext(appInfo.packageName, 0)
        }.getOrNull()

        // All standalone plugins provide dedicated transparent, neutral artwork.
        // Older official APKs use the known transparent launcher/UI resource.
        // Resolve resource names under the host theme, never the active launcher alias.
        @Suppress("DiscouragedApi")
        val transparentIcon = PluginIconResourcePolicy.candidates(appInfo.packageName).firstNotNullOfOrNull { name ->
            packageContext?.resources?.getIdentifier(name, "mipmap", appInfo.packageName)?.takeIf { it != 0 }
        }
        val iconResId = transparentIcon
            ?: appInfo.icon.takeIf { it != 0 }
            ?: appInfo.logo.takeIf { it != 0 }

        if (iconResId == null || packageContext == null) {
            return fallbackIcon()?.rasterizeForPluginCenter(context)
        }

        // A retained ViewModel can replay this snapshot before discovery finishes after a
        // theme change. Prepare both modes here so that first bind needs no package I/O.
        // zh-CN: 主题切换后, 保留的 ViewModel 可能在重新发现完成前重放此快照.
        // 在后台准备两种模式, 使首次绑定无需访问包资源即可使用当前主题.
        // A bitmap file shared by both modes is decoded only once.
        // zh-CN: 两种模式共用的位图文件只解码一次.
        val decodedFiles = HashMap<String, Drawable>(2)
        val resolvedDay = resolveThemedIcon(context, packageContext, iconResId, nightModeEnabled = false, decodedFiles)
        val resolvedNight = resolveThemedIcon(context, packageContext, iconResId, nightModeEnabled = true, decodedFiles)
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
        return ThemedInstalledIcon(
            dayIcon, nightIcon,
            resolveBackground(packageContext, false), resolveBackground(packageContext, true),
        )
    }

    /** Selects an already prepared raster without package loading or drawing on the UI thread. */
    fun forCurrentMode(icon: Drawable): Drawable = forNightMode(icon, ViewUtils.isNightModeEnabled)

    fun backgroundForCurrentMode(icon: Drawable): Int? =
        (icon as? ThemedInstalledIcon)?.background(ViewUtils.isNightModeEnabled)

    private fun resolveBackground(packageContext: Context, night: Boolean): Int? {
        val config = Configuration(packageContext.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val themed = packageContext.createConfigurationContext(config)
        return runCatching {
            @Suppress("DiscouragedApi")
            val id = themed.resources.getIdentifier("ic_plugin_center_background", "color", packageContext.packageName)
            if (id == 0) null else themed.getColor(id)
        }.getOrNull()
    }

    internal fun forNightMode(icon: Drawable, nightModeEnabled: Boolean): Drawable =
        (icon as? ThemedInstalledIcon)?.forNightMode(nightModeEnabled) ?: icon

    private fun resolveThemedIcon(
        context: Context,
        packageContext: Context,
        iconResId: Int,
        nightModeEnabled: Boolean,
        decodedFiles: MutableMap<String, Drawable>,
    ): Drawable? {
        val packageConfig = Configuration(packageContext.resources.configuration)
        val hostNightMode = if (nightModeEnabled) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        packageConfig.uiMode = (packageConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or hostNightMode
        val densityDpi = context.resources.displayMetrics.densityDpi
            .takeIf { it > 0 }
            ?: DisplayMetrics.DENSITY_DEFAULT
        packageConfig.densityDpi = densityDpi

        val themedPackageContext = packageContext.createConfigurationContext(packageConfig)

        decodeBitmapIcon(context, themedPackageContext.resources, iconResId, decodedFiles)?.let { return it }

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
        val size = context.resources.getDimensionPixelSize(R.dimen.plugin_center_item_side_length)
        if (this is BitmapDrawable && !bitmap.isRecycled) {
            // A density-scaled bitmap can take megabytes; keep a copy at the item size instead.
            // zh-CN: 按密度放大的位图可达数 MB, 改为保留列表项尺寸的副本.
            if (PluginIconRasterPolicy.fits(bitmap.width, bitmap.height, size)) return this
            return runCatching {
                BitmapDrawable(context.resources, bitmap.scaledDownTo(size))
            }.getOrDefault(this)
        }
        return runCatching {
            BitmapDrawable(context.resources, toBitmap(size, size))
        }.getOrDefault(this)
    }

    /**
     * Decodes bitmap artwork without the density scaling of Resources and keeps it at most at
     * the plugin center item size. Returns null for XML drawables, which are rasterized later.
     *
     * zh-CN: 不经 Resources 的密度缩放解码位图图片, 并保留为不超过插件中心列表项的尺寸.
     * XML 图形返回 null, 稍后再栅格化.
     */
    private fun decodeBitmapIcon(
        context: Context,
        resources: Resources,
        iconResId: Int,
        decodedFiles: MutableMap<String, Drawable>,
    ): Drawable? {
        val value = TypedValue()
        if (runCatching { resources.getValue(iconResId, value, true) }.isFailure) return null
        if (!PluginIconRasterPolicy.isBitmapFile(value.string)) return null
        val fileKey = "${value.assetCookie}:${value.string}"
        decodedFiles[fileKey]?.let { return it }

        val size = context.resources.getDimensionPixelSize(R.dimen.plugin_center_item_side_length)
        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resources.openRawResource(iconResId).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = PluginIconRasterPolicy.sampleSize(bounds.outWidth, bounds.outHeight, size)
                }
                resources.openRawResource(iconResId).use { BitmapFactory.decodeStream(it, null, options) }
            }
        }.getOrNull() ?: return null
        return BitmapDrawable(context.resources, bitmap.scaledDownTo(size)).also { decodedFiles[fileKey] = it }
    }

    private fun Bitmap.scaledDownTo(size: Int): Bitmap {
        val (scaledWidth, scaledHeight) = PluginIconRasterPolicy.scaledSize(width, height, size)
        if (scaledWidth == width && scaledHeight == height) return this
        return Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    }

    private class ThemedInstalledIcon(
        private val dayIcon: Drawable,
        private val nightIcon: Drawable,
        private val dayBackground: Int? = null,
        private val nightBackground: Int? = null,
    ) : DrawableWrapper(if (ViewUtils.isNightModeEnabled) nightIcon else dayIcon) {
        fun forNightMode(nightModeEnabled: Boolean): Drawable = if (nightModeEnabled) nightIcon else dayIcon
        fun background(night: Boolean): Int? = if (night) nightBackground else dayBackground
    }
}
