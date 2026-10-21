package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.PLUGIN_CENTER
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Plugin center settings page (docs/settings/ROADMAP.md P2.6, appendix A.1 `plugin_center`): the online sources shown
 * and the automatic update check. The named stores of the plugin center (filter, sort, visibility, mount, variant
 * priority, update check per plugin) join this category in P3.1; the time of the last online check is a state (P4.2).
 *
 * zh-CN: 插件中心设置页 (ROADMAP P2.6, 附录 A.1 `plugin_center`): 显示的在线来源与自动检查更新. 插件中心的命名存储 (筛选, 排序,
 * 可见性, 挂载, 变体优先级, 每个插件的检查更新) 在 P3.1 并入本分类; 上次在线检查的时间为状态 (P4.2).
 */
object PluginCenterKeys : SettingsDomain {

    @JvmField
    val PLUGIN_CENTER_SHOW_OFFICIAL_PLUGINS = SettingKey.Bool(
        R.string.key_plugin_center_show_official_plugins, PLUGIN_CENTER,
        boolResource(R.bool.pref_plugin_center_show_official_plugins, "pref_plugin_center_show_official_plugins"),
    )

    @JvmField
    val PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS = SettingKey.Bool(
        R.string.key_plugin_center_show_third_party_plugins, PLUGIN_CENTER,
        boolResource(R.bool.pref_plugin_center_show_third_party_plugins, "pref_plugin_center_show_third_party_plugins"),
    )

    @JvmField
    val PLUGIN_CENTER_AUTO_CHECK_UPDATES = SettingKey.Bool(
        R.string.key_plugin_center_auto_check_updates, PLUGIN_CENTER,
        boolResource(R.bool.pref_plugin_center_auto_check_updates, "pref_plugin_center_auto_check_updates"),
    )

    override val keys: List<PrefKey<*>> = listOf(
        PLUGIN_CENTER_SHOW_OFFICIAL_PLUGINS, PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS, PLUGIN_CENTER_AUTO_CHECK_UPDATES,
    )
}
