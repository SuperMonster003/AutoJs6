package org.autojs.autojs.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.external.shortcut.ShortcutActivity
import org.autojs.autojs6.R

/**
 * The launcher components of the two launcher icons (adaptive / transparent background), switched without the
 * prompts of the settings page (docs/settings/ROADMAP.md T12): the switch of `LauncherIconPreference`, taken out so
 * that an import applies `key_launcher_icon` (`SettingsEffects.Entry.LAUNCHER_ICON`, P7.4). Pinned shortcuts are moved
 * to the enabled component like the page does. Before Android 8.0 only the transparent background icon exists.
 *
 * zh-CN: 两种启动图标 (自适应 / 透明背景) 的启动组件, 不带设置页的提示直接切换 (ROADMAP T12): 即 `LauncherIconPreference` 的切换逻辑,
 * 抽出以便导入应用 `key_launcher_icon` (`SettingsEffects.Entry.LAUNCHER_ICON`, P7.4). 与设置页一样把已固定的快捷方式移到启用的组件上.
 * Android 8.0 之前只有透明背景图标.
 */
object LauncherIconComponents {

    const val ALIAS_ADAPTIVE = "org.autojs.autojs.launcher.AdaptiveIconAlias"
    const val ALIAS_TRANSPARENT_BACKGROUND = "org.autojs.autojs.launcher.TransparentBackgroundIconAlias"

    /**
     * True when the transparent background component is the enabled one (or the only one, before Android 8.0). The
     * manifest declares it disabled, so its default state counts as disabled.
     * zh-CN: 透明背景组件为启用的组件时 (Android 8.0 之前只有它) 为 true. Manifest 中它默认禁用, 因此默认状态视为禁用.
     */
    @JvmStatic
    fun isTransparentBackground(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.getComponentEnabledSetting(ComponentName(context, ALIAS_TRANSPARENT_BACKGROUND)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /**
     * Enables the component [key_launcher_icon][GeneralKeys.LAUNCHER_ICON] names and, when the device cannot show it
     * (the adaptive icon before Android 8.0), stores the one in use instead, as the settings page does when it opens.
     * zh-CN: 启用 [key_launcher_icon][GeneralKeys.LAUNCHER_ICON] 所指的组件; 设备无法显示时 (Android 8.0 之前的自适应图标) 改为存储实际使用的
     * 组件, 与设置页打开时的处理相同.
     */
    @JvmStatic
    fun applyStored(context: Context) {
        val transparentKey = context.getString(R.string.key_launcher_icon_transparent_background)
        val wantTransparent = GeneralKeys.LAUNCHER_ICON.storedKeyOrDefault() == transparentKey || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        if (wantTransparent != isTransparentBackground(context)) {
            when (wantTransparent) {
                true -> switchComponent(context, ALIAS_TRANSPARENT_BACKGROUND, ALIAS_ADAPTIVE)
                else -> switchComponent(context, ALIAS_ADAPTIVE, ALIAS_TRANSPARENT_BACKGROUND)
            }
        }
        val inUse = if (isTransparentBackground(context)) transparentKey else context.getString(R.string.key_launcher_icon_adaptive)
        if (GeneralKeys.LAUNCHER_ICON.storedKey() != inUse) GeneralKeys.LAUNCHER_ICON.setStoredKey(inUse)
    }

    /** Enables [wantEnable], moves the pinned shortcuts to it and disables [wantDisable] (`LauncherIconPreference`). */
    @JvmStatic
    fun switchComponent(ctx: Context, wantEnable: String, wantDisable: String) {
        val pm = ctx.packageManager

        pm.setComponentEnabledSetting(
            ComponentName(ctx, wantEnable),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ctx.getSystemService(ShortcutManager::class.java)?.let { sm ->
                sm.pinnedShortcuts
                    .filterNot { it.isDeclaredInManifest }
                    .filterNot { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && it.isImmutable }
                    .mapNotNull { shortcutInfo ->
                        ShortcutInfo.Builder(ctx, shortcutInfo.id).apply {
                            shortcutInfo.intent?.let {
                                it.component?.let { component ->
                                    setActivity(ComponentName(component.packageName, component.className))
                                } ?: run {
                                    setActivity(ComponentName(ctx, ShortcutActivity::class.java))
                                }
                                setIntent(it)
                            } ?: run {
                                setActivity(ComponentName(ctx, ShortcutActivity::class.java))
                                setIntent(Intent(ctx, ShortcutActivity::class.java))
                            }
                            (shortcutInfo.shortLabel ?: shortcutInfo.longLabel)?.let {
                                setShortLabel(it)
                            }
                            (shortcutInfo.longLabel ?: shortcutInfo.shortLabel)?.let {
                                setLongLabel(it)
                            }
                        }.build()
                    }.takeUnless { it.isEmpty() }?.let { toUpdate ->
                        sm.updateShortcuts(toUpdate)
                    }
            }
        }

        pm.setComponentEnabledSetting(
            ComponentName(ctx, wantDisable),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
