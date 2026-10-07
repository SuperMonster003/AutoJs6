package org.autojs.autojs.theme.intelligent

import android.annotation.SuppressLint
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

/**
 * Seed colors of the current wallpaper (roadmap P4.1), without reading the wallpaper bitmap or any permission:
 * - API 31 and above: the system dynamic palette (`system_accent1/2/3`), which also follows the colors chosen in the
 *   system "Wallpaper and style" settings.
 * - API 27 to 30: the primary, secondary and tertiary colors of `WallpaperManager.getWallpaperColors`.
 * - API 24 to 26: unavailable.
 * zh-CN: 当前壁纸的种子色 (Roadmap P4.1), 不读取壁纸位图, 也不需要任何权限:
 * - API 31 及以上: 系统动态配色 (`system_accent1/2/3`), 同时跟随系统 "壁纸与个性化" 设置中选择的颜色.
 * - API 27 至 30: `WallpaperManager.getWallpaperColors` 的主色, 次色与第三色.
 * - API 24 至 26: 不可用.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object WallpaperColorSource {

    const val MIN_SDK = Build.VERSION_CODES.O_MR1

    private const val OPAQUE = 0xFF shl 24

    private var listener: Any? = null

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= MIN_SDK

    /** Distinct opaque seed colors, most prominent first; empty when unavailable. zh-CN: 去重后的不透明种子色, 最显著者在前; 不可用时为空. */
    fun seeds(context: Context): List<Int> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> systemPalette(context)
        isAvailable -> wallpaperColors(context)
        else -> emptyList()
    }.map { it or OPAQUE }.distinct()

    /** A compact identity of [seeds], to notice changes after a restart. zh-CN: [seeds] 的简短标识, 用于重启后发现变化. */
    fun fingerprint(seeds: List<Int>) = seeds.joinToString(",") { "%06X".format(it and 0xFFFFFF) }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun systemPalette(context: Context) = listOf(
        android.R.color.system_accent1_500,
        android.R.color.system_accent2_500,
        android.R.color.system_accent3_500,
    ).map { context.getColor(it) }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun wallpaperColors(context: Context): List<Int> {
        val colors = runCatching { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return emptyList()
        return listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor).map { it.toArgb() }
    }

    /**
     * Calls [onChanged] on the main thread when the wallpaper colors change while the process is alive; null stops it.
     * zh-CN: 进程存活期间壁纸颜色变化时在主线程调用 [onChanged]; 传入 null 则停止监听.
     */
    fun watch(context: Context, onChanged: (() -> Unit)?) {
        if (isAvailable) ColorsListener.watch(context.applicationContext, onChanged)
    }

    /** Keeps API 27 types out of code that runs on older systems. zh-CN: 使 API 27 的类型不出现在旧系统会执行的代码中. */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private object ColorsListener {

        fun watch(context: Context, onChanged: (() -> Unit)?) {
            val manager = WallpaperManager.getInstance(context)
            (listener as? WallpaperManager.OnColorsChangedListener)?.let { runCatching { manager.removeOnColorsChangedListener(it) } }
            listener = null
            onChanged ?: return
            val newListener = WallpaperManager.OnColorsChangedListener { _: WallpaperColors?, which: Int ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) onChanged()
            }
            runCatching { manager.addOnColorsChangedListener(newListener, Handler(Looper.getMainLooper())) }.onSuccess { listener = newListener }
        }

    }

}
