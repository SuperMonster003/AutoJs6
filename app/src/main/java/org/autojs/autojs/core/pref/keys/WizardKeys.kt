package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.WIZARDS
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs.core.pref.store.StoreId

/**
 * Setup wizard profiles (docs/settings/ROADMAP.md P3.3, appendix A.1 `wizards`): the named stores `permission_wizard`
 * and `plugin_install_wizard` of `WizardProfileStore`. The custom profiles and the "show done entries" option are
 * settings, the last chosen profile id is a state (`other`); the plugin install wizard also keeps whether new plugins
 * show their launcher icons. Names are unchanged (R1); the custom profiles stay the JSON text `WizardProfileCodec`
 * writes (R2).
 *
 * zh-CN: 设置向导配置档 (ROADMAP P3.3, 附录 A.1 `wizards`): `WizardProfileStore` 的两份命名存储. 自定义配置档与 "显示已完成条目"
 * 为设置, 上次选择的配置档 id 为状态 (`other`); 插件安装向导另存新插件是否显示启动图标. 名称不变 (R1); 自定义配置档仍是
 * `WizardProfileCodec` 写出的 JSON 文本 (R2).
 */
object WizardKeys : SettingsDomain {

    /** The keys of one wizard in its own named store. zh-CN: 一个向导在其命名存储中的键. */
    class WizardProfileKeys internal constructor(
        @JvmField val store: StoreId,
        customProfilesName: String,
        lastProfileIdName: String,
        showDoneName: String,
    ) {

        /** The custom profiles as a JSON array text (`WizardProfileCodec`). */
        @JvmField
        val customProfiles = SettingKey.Json(
            name = customProfilesName, category = WIZARDS, jsonType = SettingType.Json(SettingsJson.ARRAY), default = SettingDefault.None, store = store,
        )

        /** The id of the profile chosen last; removed when none is. */
        @JvmField
        val lastProfileId = StateKey.str(name = lastProfileIdName, default = SettingDefault.None, store = store)

        /** "Show done entries"; a missing one is the default of the wizard (`WizardProfileStore.showDoneByDefault`). */
        @JvmField
        val showDone = SettingKey.Bool(name = showDoneName, category = WIZARDS, default = SettingDefault.of(true), store = store)

        val keys: List<PrefKey<*>>
            get() = listOf(customProfiles, lastProfileId, showDone)
    }

    /** The permission wizard (`PermissionWizardDialog`). */
    @JvmField
    val PERMISSION_WIZARD = WizardProfileKeys(
        NamedStores.PERMISSION_WIZARD,
        "key_\$_permission_wizard_custom_profiles",
        "key_\$_permission_wizard_last_profile_id",
        "key_\$_permission_wizard_show_done",
    )

    /** The plugin install wizard (`PluginInstallWizardProfileStore`). */
    @JvmField
    val PLUGIN_INSTALL_WIZARD = WizardProfileKeys(
        NamedStores.PLUGIN_INSTALL_WIZARD,
        "key_\$_plugin_install_wizard_custom_profiles",
        "key_\$_plugin_install_wizard_last_profile_id",
        "key_\$_plugin_install_wizard_show_done",
    )

    /** Plugins installed by the wizard show their launcher icons (`PluginInstallWizardProfileStore.showLauncherIcons`). */
    @JvmField
    val PLUGIN_INSTALL_WIZARD_SHOW_LAUNCHER_ICONS = SettingKey.Bool(
        name = "show_launcher_icons_for_new_plugins", category = WIZARDS, default = SettingDefault.of(true), store = NamedStores.PLUGIN_INSTALL_WIZARD,
    )

    override val keys: List<PrefKey<*>> = PERMISSION_WIZARD.keys + PLUGIN_INSTALL_WIZARD.keys + PLUGIN_INSTALL_WIZARD_SHOW_LAUNCHER_ICONS

    // The first run hint of the plugin install wizard, no longer read or written since 2d7c72b33 but still present in
    // older installations; registered as excluded like the removed terminal keys (appendix C Q13 / Q20).
    // zh-CN: 插件安装向导的首次运行提示, 2d7c72b33 起不再读写, 旧安装中仍有残留; 与已移除终端的遗留键一样登记为排除项 (附录 C Q13 / Q20).
    override val names: List<RegisteredName> = listOf(
        RegisteredName(kind = SettingKind.EXCLUDED, literalName = "key_\$_plugin_install_wizard_first_run_shown", store = NamedStores.PLUGIN_INSTALL_WIZARD),
    )
}
