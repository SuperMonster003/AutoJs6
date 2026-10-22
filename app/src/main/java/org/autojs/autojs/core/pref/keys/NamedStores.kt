package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * The named SharedPreferences files of the host (docs/settings/ROADMAP.md P3, T3): every file opened with
 * `getSharedPreferences` is declared here with its file name unchanged (R1) and registered in [SettingsStores], which
 * is what guard (b) of T5 checks the sources against. Keys and key families of a store declare it as their `store`
 * in the domain objects; a store without keys of its own is one of the permanent exclusions of appendix A.2
 * ([StoreId.excluded], P3.5), whose code keeps opening it as before. A name with `*` stands for the files whose names
 * are built at run time from that pattern.
 *
 * zh-CN: 宿主的命名 SharedPreferences 文件 (ROADMAP P3, T3): 每个经 `getSharedPreferences` 打开的文件都在这里以原文件名 (R1)
 * 声明并登记到 [SettingsStores], T5 守卫 (b) 以此核对源码. 存储中的键与键族在领域对象里把它写作 `store`; 没有键的存储是附录
 * A.2 的永久排除项 ([StoreId.excluded], P3.5), 其代码照旧打开文件. 名称中的 `*` 代表运行时按该模式拼出的一组文件名.
 */
object NamedStores {

    private fun store(name: String) = SettingsStores.register(StoreId(name))

    private fun excluded(name: String) = SettingsStores.register(StoreId(name, excluded = true))

    // Code editor keyboards and font dialog (P3.2, `EditorKeyboardKeys`).
    // zh-CN: 代码编辑器键盘与字体对话框 (P3.2, `EditorKeyboardKeys`).

    /** Symbol keyboard profiles (`SymbolsConfigStore`). */
    @JvmField
    val EDITOR_SYMBOLS_CONFIG = store("editor_symbols_config")

    /** The measured soft keyboard height of the functions keyboard (`FunctionsKeyboardHelper`, a cache). */
    @JvmField
    val FUNCTIONS_KEYBOARD_HELPER = store("FunctionsKeyboardHelper")

    /** Presentation preferences of the font dialog and previewer (`AceEditorFontUiPreferences`). */
    @JvmField
    val ACE_EDITOR_FONT_UI_PREFERENCES = store("ace_editor_font_ui_preferences")

    /** Usage history of the Ace editor actions (`AceActionHistoryStore`, a state). */
    @JvmField
    val ACE_ACTION_HISTORY = store("ace_action_history")

    // Profile stores (P3.3): `ApkBuildKeys`, `WizardKeys`, `PluginCenterKeys`, `UpdatesKeys`.
    // zh-CN: 配置档类存储 (P3.3).

    /** APK build profiles (`BuildConfigStore`). */
    @JvmField
    val BUILD_APK_CONFIG_PROFILES = store("build_apk_config_profiles")

    /** Profiles and options of the permission wizard (`WizardProfileStore` of `PermissionWizardDialog`). */
    @JvmField
    val PERMISSION_WIZARD = store("permission_wizard")

    /** Profiles and options of the plugin install wizard (`WizardProfileStore` of `PluginInstallWizardProfileStore`). */
    @JvmField
    val PLUGIN_INSTALL_WIZARD = store("plugin_install_wizard")

    /** The JVM source provider chosen per language (`JvmSourceProviderSelection`). */
    @JvmField
    val JVM_SOURCE_PROVIDER_SELECTION = store("jvm_source_provider_selection")

    /**
     * Ignored plugin versions (`UpdateIgnoreStore`); the file is named by the value of `R.string.key_ignored_updates`,
     * which `NamedStoresTest` checks.
     */
    @JvmField
    val IGNORED_UPDATES = store("key_\$_ignored_updates")

    // Plugin center (P3.1, `PluginCenterKeys`).
    // zh-CN: 插件中心 (P3.1, `PluginCenterKeys`).

    /** Filters of the plugin list (`PluginFilterStore`). */
    @JvmField
    val PLUGIN_CENTER_FILTER_STATE = store("plugin_center_filter_state")

    /** Sort memory of the three plugin lists: `PluginSortStore`, `PluginSortStoreOfficial` and `PluginSortStoreThirdParty` share this one file. */
    @JvmField
    val PLUGIN_CENTER_SORT_STATE = store("plugin_center_sort_state")

    /** Hidden plugins (`PluginVisibilityStore`). */
    @JvmField
    val PLUGIN_CENTER_VISIBILITY_STATE = store("plugin_center_visibility_state")

    /** Launcher icon choice per plugin (`PluginLauncherIconController`). */
    @JvmField
    val PLUGIN_LAUNCHER_ICONS = store("plugin_launcher_icons")

    /** Enable and suspension state per plugin (`PluginEnableStore`, its own category `plugin_enable_state`). */
    @JvmField
    val PLUGIN_CENTER_ENABLE_STATE = store("plugin_center_enable_state")

    /** Last install and uninstall times per plugin (`PluginRecentStore`, states). */
    @JvmField
    val PLUGIN_CENTER_RECENT = store("plugin_center_recent")

    /** Temporary pins of new or failed plugins (`PluginCenterAttentionStore`, states). */
    @JvmField
    val PLUGIN_CENTER_ATTENTION = store("plugin_center_attention")

    // Credentials and authorizations (P3.4, `CredentialKeys`): sensitive, never selected by default (D2).
    // zh-CN: 凭据与授权 (P3.4, `CredentialKeys`): 敏感项, 默认不勾选 (D2).

    /** The GitHub token (`GitHubTokenStore`). */
    @JvmField
    val GITHUB_AUTH = store("github_auth")

    /** Plugins authorized per signing fingerprint (`PluginAuthorizationStore`). */
    @JvmField
    val PLUGIN_CENTER_AUTHORIZATION = store("plugin_center_authorization")

    /** Confirmed signers of the MCP Server plugin (`McpServerPluginInspector`, `McpServerTool`). */
    @JvmField
    val MCP_SERVER_TRUST_CONFIRMATION = store("mcp_server_trust_confirmation")

    /** Confirmed signers of the 3-Stove Agent plugin (`ThreeStoveAgentPluginInspector`, `ThreeStoveAgentGuide`). */
    @JvmField
    val THREE_STOVE_AGENT_TRUST_CONFIRMATION = store("three_stove_agent_trust_confirmation")

    /** Host capabilities granted to Bun scripts (`BunHostCapabilityGrants`). */
    @JvmField
    val BUN_HOST_CAPABILITY_GRANTS = store("bun_host_capability_grants")

    // Permanent exclusions of appendix A.2 (P3.5): registered only, their readers are unchanged.
    // zh-CN: 附录 A.2 的永久排除项 (P3.5): 只登记, 读写方不变.

    /** Device identity (`DeviceIdProvider`); importing it on another device would break its uniqueness. */
    @JvmField
    val DEVICE_ID_PREFS = excluded("device_id_prefs")

    /** Data of the `storages` module of scripts (`LocalStorage`, `Storages`): one file per storage name. */
    @JvmField
    val SCRIPT_LOCAL_STORAGES = excluded("autojs.localstorage.*")

    /** Data of the storage bridge of Node.js scripts (`NodeBridgeProtocol.storagePreferencesName`, appendix C Q13 e). */
    @JvmField
    val NODE_SCRIPT_STORAGES = excluded("autojs.node.storage.*.*.*")

    /** Data of the storages of Lua scripts (`LuaHostStorage`). */
    @JvmField
    val LUA_SCRIPT_STORAGES = excluded("lua-runtime-storage-v1-*")

    /** Backups of privileged edits, pointing to files of this device (`PrivilegedBackupManager`). */
    @JvmField
    val PRIVILEGED_EDIT_BACKUPS = excluded("privileged_edit_backups")

    /** Intents of running tasks, valid for the current processes only (`RuntimeTasksBridge`). */
    @JvmField
    val RUNTIME_TASK_INTENTS = excluded("runtime_task_intents")

    /** App-installation-bound Agent consent and task ownership must not be imported onto another device or install. */
    @JvmField
    val AGENT_RUNTIME_ACCESS = excluded("agent_runtime_access")

    /** Desktop widget ids assigned by the launcher of this device (`ScriptWidgets`). */
    @JvmField
    val SCRIPT_WIDGETS = excluded("ScriptWidgets")

    /** Playback positions of media files of this device (`ExplorerPlaybackHistoryStore`). */
    @JvmField
    val EXPLORER_PLAYBACK_HISTORY = excluded("explorer_playback_history")

    /** Every declared named store, in declaration order. */
    @JvmField
    val ALL: List<StoreId> = listOf(
        EDITOR_SYMBOLS_CONFIG, FUNCTIONS_KEYBOARD_HELPER, ACE_EDITOR_FONT_UI_PREFERENCES, ACE_ACTION_HISTORY,
        BUILD_APK_CONFIG_PROFILES, PERMISSION_WIZARD, PLUGIN_INSTALL_WIZARD, JVM_SOURCE_PROVIDER_SELECTION, IGNORED_UPDATES,
        PLUGIN_CENTER_FILTER_STATE, PLUGIN_CENTER_SORT_STATE, PLUGIN_CENTER_VISIBILITY_STATE, PLUGIN_LAUNCHER_ICONS,
        PLUGIN_CENTER_ENABLE_STATE, PLUGIN_CENTER_RECENT, PLUGIN_CENTER_ATTENTION,
        GITHUB_AUTH, PLUGIN_CENTER_AUTHORIZATION, MCP_SERVER_TRUST_CONFIRMATION, THREE_STOVE_AGENT_TRUST_CONFIRMATION,
        BUN_HOST_CAPABILITY_GRANTS,
        DEVICE_ID_PREFS, SCRIPT_LOCAL_STORAGES, NODE_SCRIPT_STORAGES, LUA_SCRIPT_STORAGES, PRIVILEGED_EDIT_BACKUPS,
        RUNTIME_TASK_INTENTS, AGENT_RUNTIME_ACCESS, SCRIPT_WIDGETS, EXPLORER_PLAYBACK_HISTORY,
    )
}
