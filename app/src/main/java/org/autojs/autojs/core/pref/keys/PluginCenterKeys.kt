package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort
import org.autojs.autojs.core.plugin.center.PluginFilterStore
import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.PLUGIN_CENTER
import org.autojs.autojs.core.pref.SettingCategory.Companion.PLUGIN_ENABLE_STATE
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs6.R

/**
 * Plugin center (docs/settings/ROADMAP.md P2.6 / P3.1, appendix A.1 `plugin_center` and `plugin_enable_state`).
 *
 * - Settings page (P2.6): the online sources shown and the automatic update check.
 * - Named stores (P3.1, [NamedStores]): the filters, the sort memory of the three plugin lists (one file shared by
 *   `PluginSortStore`, `PluginSortStoreOfficial` and `PluginSortStoreThirdParty`), the hidden plugins and the launcher
 *   icon choices; the enable and suspension state per plugin is its own category `plugin_enable_state` (an import
 *   only writes it, the plugin center wakes and rebinds on its next refresh, appendix C Q11); the last install /
 *   uninstall times and the temporary pins are states.
 * - Default preferences (P3.1): the automatic mounts, the "check for updates" switch per plugin (a setting, appendix C
 *   Q13 c) and the variant priority per engine.
 *
 * Names are unchanged (R1). Keys named by a package name, an engine or a sort column are key families; their readers
 * keep their own fallbacks where the old code passed one (R2). Values the old code wrote as org.json text stay text
 * written by the same code ([PrefKey.storedText]).
 *
 * zh-CN: 插件中心 (ROADMAP P2.6 / P3.1, 附录 A.1 `plugin_center` 与 `plugin_enable_state`). 设置页 (P2.6): 显示的在线来源与自动检查
 * 更新. 命名存储 (P3.1): 筛选, 三个插件列表的排序记忆 (三个类共用一个文件), 隐藏的插件, 启动图标; 每个插件的启用与挂起状态单独
 * 分类 `plugin_enable_state` (导入只写偏好, 唤醒 / 重绑由插件中心下次刷新处理, 附录 C Q11); 最近安装 / 卸载时间与临时置顶为状态.
 * 默认偏好 (P3.1): 自动挂载, 每个插件的 "检查更新" 开关 (设置, 附录 C Q13 c), 每个引擎的变体优先级. 名称不变 (R1); 以包名, 引擎或
 * 排序列命名的键为键族, 原先自带回退值的读取方保留其回退值 (R2); 原先以 org.json 文本写入的值仍由原代码读写文本.
 */
object PluginCenterKeys : SettingsDomain {

    // ------------------------------------------------------------------------------------- settings page (P2.6)

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

    // --------------------------------------------------------------------- plugin_center_filter_state (P3.1)

    /** The single filter of old versions, read only while none of the filters below is stored (`PluginFilterStore`). */
    @JvmField
    val FILTER_LEGACY_ORDINAL = filterInt("key_\$_plugin_center_filter_state", SettingDefault.of(0))

    @JvmField
    val FILTER_INSTALL_STATE = filterInt(
        "key_\$_plugin_center_filter_install_state",
        SettingDefault.computed("InstallState.ALL.ordinal") { PluginFilterStore.InstallState.ALL.ordinal },
    )

    @JvmField
    val FILTER_ENABLED_STATE = filterInt(
        "key_\$_plugin_center_filter_enabled_state",
        SettingDefault.computed("EnabledState.ALL.ordinal") { PluginFilterStore.EnabledState.ALL.ordinal },
    )

    @JvmField
    val FILTER_MECHANISM_STATE = filterInt(
        "key_\$_plugin_center_filter_mechanism_state",
        SettingDefault.computed("MechanismState.ALL.ordinal") { PluginFilterStore.MechanismState.ALL.ordinal },
    )

    @JvmField
    val FILTER_SOURCE_STATE = filterInt(
        "key_\$_plugin_center_filter_source_state",
        SettingDefault.computed("SourceState.ALL.ordinal") { PluginFilterStore.SourceState.ALL.ordinal },
    )

    @JvmField
    val FILTER_UPDATABLE_ONLY = SettingKey.Bool(
        name = "key_\$_plugin_center_filter_updatable_only", category = PLUGIN_CENTER, default = SettingDefault.of(false),
        store = NamedStores.PLUGIN_CENTER_FILTER_STATE,
    )

    // ----------------------------------------------------------------------- plugin_center_sort_state (P3.1)

    /**
     * The sort memory of one plugin list in the shared file `plugin_center_sort_state`: the primary sort ordinal, the
     * direction per sort column, the secondary sort per primary one, and the secondary sorts of the mechanism and
     * source columns, which keep the names of older versions.
     * zh-CN: 共用文件 `plugin_center_sort_state` 中一个插件列表的排序记忆: 主排序序号, 各排序列的方向, 各主排序的次排序, 以及沿用旧版
     * 名称的机制列与来源列的次排序.
     */
    class PluginSortKeys internal constructor(
        sortName: String,
        ascendingPrefix: String,
        secondaryPrefix: String,
        mechanismSecondaryName: String,
        sourceSecondaryName: String,
    ) {

        /** Ordinal of the primary [Sort]; an unknown ordinal falls back to the default sort. */
        @JvmField
        val sort = SettingKey.Int(
            name = sortName, category = PLUGIN_CENTER, store = NamedStores.PLUGIN_CENTER_SORT_STATE,
            default = SettingDefault.computed("Sort.defaultPrimarySort().ordinal") { Sort.defaultPrimarySort().ordinal },
        )

        /** Ascending per sort column (`Sort.name`); a missing one is `Sort.defaultAscending` of its column. */
        @JvmField
        val ascending = KeyFamily(ascendingPrefix) { name ->
            SettingKey.Bool(name = name, category = PLUGIN_CENTER, default = SettingDefault.None, store = NamedStores.PLUGIN_CENTER_SORT_STATE)
        }

        /** Secondary sort ordinal per primary sort (`Sort.name`); a missing one is `defaultSecondarySort()` of the primary. */
        @JvmField
        val secondary = KeyFamily(secondaryPrefix) { name ->
            SettingKey.Int(name = name, category = PLUGIN_CENTER, default = SettingDefault.None, store = NamedStores.PLUGIN_CENTER_SORT_STATE)
        }

        @JvmField
        val mechanismSecondary = SettingKey.Int(
            name = mechanismSecondaryName, category = PLUGIN_CENTER, store = NamedStores.PLUGIN_CENTER_SORT_STATE,
            default = SettingDefault.computed("Sort.MECHANISM.defaultSecondarySort().ordinal") { Sort.MECHANISM.defaultSecondarySort().ordinal },
        )

        @JvmField
        val sourceSecondary = SettingKey.Int(
            name = sourceSecondaryName, category = PLUGIN_CENTER, store = NamedStores.PLUGIN_CENTER_SORT_STATE,
            default = SettingDefault.computed("Sort.SOURCE.defaultSecondarySort().ordinal") { Sort.SOURCE.defaultSecondarySort().ordinal },
        )

        val keys: List<PrefKey<*>>
            get() = listOf(sort, mechanismSecondary, sourceSecondary)

        val families: List<KeyFamily<*, *>>
            get() = listOf(ascending, secondary)
    }

    /** The list of all plugins (`PluginSortStore`). */
    @JvmField
    val SORT_ALL = PluginSortKeys(
        "key_\$_plugin_center_sort_state",
        "key_\$_plugin_center_sort_state_ascending_",
        "key_\$_plugin_center_sort_state_secondary_",
        "key_\$_plugin_center_sort_state_mechanism_secondary",
        "key_\$_plugin_center_sort_state_source_secondary",
    )

    /** The list of official plugins (`PluginSortStoreOfficial`). */
    @JvmField
    val SORT_OFFICIAL = PluginSortKeys(
        "key_\$_plugin_center_sort_state_official",
        "key_\$_plugin_center_sort_state_official_ascending_",
        "key_\$_plugin_center_sort_state_official_secondary_",
        "key_\$_plugin_center_sort_state_official_mechanism_secondary",
        "key_\$_plugin_center_sort_state_official_source_secondary",
    )

    /** The list of third-party plugins (`PluginSortStoreThirdParty`). */
    @JvmField
    val SORT_THIRD_PARTY = PluginSortKeys(
        "key_\$_plugin_center_sort_state_third_party",
        "key_\$_plugin_center_sort_state_third_party_ascending_",
        "key_\$_plugin_center_sort_state_third_party_secondary_",
        "key_\$_plugin_center_sort_state_third_party_mechanism_secondary",
        "key_\$_plugin_center_sort_state_third_party_source_secondary",
    )

    // ----------------------------------------------------------------- plugin_center_visibility_state (P3.1)

    /** Package names of the hidden plugins (`PluginVisibilityStore`). */
    @JvmField
    val HIDDEN_PACKAGES = SettingKey.StrSet(
        name = "key_\$_plugin_center_hidden_packages", category = PLUGIN_CENTER, default = SettingDefault.of(emptySet()),
        store = NamedStores.PLUGIN_CENTER_VISIBILITY_STATE,
    )

    /** Title of a hidden plugin, per package name, shown by the hidden plugin management page. */
    @JvmField
    val HIDDEN_TITLE = KeyFamily("key_\$_plugin_center_hidden_title_") { name ->
        SettingKey.Str(name = name, category = PLUGIN_CENTER, default = SettingDefault.None, store = NamedStores.PLUGIN_CENTER_VISIBILITY_STATE)
    }

    /** Index source (`PluginIndexSource.name`) of a hidden plugin, per package name. */
    @JvmField
    val HIDDEN_SOURCE = KeyFamily("key_\$_plugin_center_hidden_source_") { name ->
        SettingKey.Str(name = name, category = PLUGIN_CENTER, default = SettingDefault.None, store = NamedStores.PLUGIN_CENTER_VISIBILITY_STATE)
    }

    // -------------------------------------------------------------------------- plugin_launcher_icons (P3.1)

    /**
     * The launcher icon choice per package name: a JSON object with "show", the component "states" and the install
     * identity ("uid", "firstInstallTime"), read and written as org.json text by `PluginLauncherIconController`.
     * zh-CN: 每个包名的启动图标选择: 含 "show", 组件 "states" 与安装身份的 JSON 对象, 由 `PluginLauncherIconController` 以 org.json
     * 文本读写.
     */
    @JvmField
    val LAUNCHER_ICON = KeyFamily.everyName { name ->
        SettingKey.Json(
            name = name, category = PLUGIN_CENTER, jsonType = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None,
            store = NamedStores.PLUGIN_LAUNCHER_ICONS,
        )
    }

    // -------------------------------------------------------------------- jvm_source_provider_selection (P3.3)

    /** The exact JVM source provider component chosen for Java (`JvmSourceProviderSelection`; the original key is Java only). */
    @JvmField
    val JVM_SOURCE_EXACT_COMPONENT_JAVA = SettingKey.Str(
        name = "exact_component", category = PLUGIN_CENTER, default = SettingDefault.None, store = NamedStores.JVM_SOURCE_PROVIDER_SELECTION,
    )

    /** The exact JVM source provider component chosen for Kotlin, which never inherits the Java choice. */
    @JvmField
    val JVM_SOURCE_EXACT_COMPONENT_KOTLIN = SettingKey.Str(
        name = "exact_component_kotlin", category = PLUGIN_CENTER, default = SettingDefault.None, store = NamedStores.JVM_SOURCE_PROVIDER_SELECTION,
    )

    // ---------------------------------------------------------------------- default preferences (P3.1)

    /** Package names that have an automatic mount configuration (`PluginMountStore`). */
    @JvmField
    val MOUNT_PACKAGES = SettingKey.StrSet(name = "key_\$_plugin_mount_packages", category = PLUGIN_CENTER, default = SettingDefault.of(emptySet()))

    /** The automatic mount switch per package name. */
    @JvmField
    val MOUNT_ENABLED = KeyFamily("key_\$_plugin_mount_enabled_\$_") { name ->
        SettingKey.Bool(name = name, category = PLUGIN_CENTER, default = SettingDefault.of(false))
    }

    /** The global object name a plugin is mounted as, per package name; missing means the derived default. */
    @JvmField
    val MOUNT_OBJECT_NAME = KeyFamily("key_\$_plugin_mount_object_name_\$_") { name ->
        SettingKey.Str(name = name, category = PLUGIN_CENTER, default = SettingDefault.None)
    }

    /** The mount point per package name; missing means the global scope. */
    @JvmField
    val MOUNT_POINT = KeyFamily("key_\$_plugin_mount_point_\$_") { name ->
        SettingKey.Str(name = name, category = PLUGIN_CENTER, default = SettingDefault.None)
    }

    /** The "check for updates" switch per package name (`PluginUpdateCheckStore`, a setting, appendix C Q13 c). */
    @JvmField
    val UPDATE_CHECK = KeyFamily("key_\$_plugin_update_check_\$_") { name ->
        SettingKey.Bool(name = name, category = PLUGIN_CENTER, default = SettingDefault.of(true))
    }

    /**
     * The variant priority per normalized engine key: a JSON array of package names, read and written as org.json
     * text by `PluginVariantPriorityStore`.
     * zh-CN: 每个规范化引擎键的变体优先级: 包名组成的 JSON 数组, 由 `PluginVariantPriorityStore` 以 org.json 文本读写.
     */
    @JvmField
    val VARIANT_PRIORITY = KeyFamily("key_\$_plugin_variant_priority_\$_") { name ->
        SettingKey.Json(name = name, category = PLUGIN_CENTER, jsonType = SettingType.Json(SettingsJson.STRING_LIST), default = SettingDefault.of(emptyList()))
    }

    // ---------------------------------------------------- plugin_center_enable_state (P3.1, plugin_enable_state)

    /** Enabled per package name (`PluginEnableStore`); a missing one is the default the caller passes (true unless a policy says otherwise). */
    @JvmField
    val PLUGIN_ENABLED = KeyFamily("key_\$_enabled_plugin_\$_") { name ->
        SettingKey.Bool(name = name, category = PLUGIN_ENABLE_STATE, default = SettingDefault.of(true), store = NamedStores.PLUGIN_CENTER_ENABLE_STATE)
    }

    /** Switched off because the plugin could not be reached, not by the user (`PluginEnableSuspension`); removed otherwise. */
    @JvmField
    val PLUGIN_SUSPENDED = KeyFamily("key_\$_suspended_plugin_\$_") { name ->
        SettingKey.Bool(name = name, category = PLUGIN_ENABLE_STATE, default = SettingDefault.of(false), store = NamedStores.PLUGIN_CENTER_ENABLE_STATE)
    }

    // ------------------------------------------------------------------------------ states (P3.1, `other`)

    /** Last install time per package name (`PluginRecentStore`); 0 or missing means unknown. */
    @JvmField
    val LAST_INSTALL_TIME = KeyFamily("key_\$_last_install_time_\$_") { name ->
        StateKey.long(name = name, default = SettingDefault.of(0L), store = NamedStores.PLUGIN_CENTER_RECENT)
    }

    /** Last uninstall time per package name (`PluginRecentStore`); 0 or missing means unknown. */
    @JvmField
    val LAST_UNINSTALL_TIME = KeyFamily("key_\$_last_uninstall_time_\$_") { name ->
        StateKey.long(name = name, default = SettingDefault.of(0L), store = NamedStores.PLUGIN_CENTER_RECENT)
    }

    /** First install times of the packages seen, as an org.json object text (`PluginCenterAttentionStore`). */
    @JvmField
    val ATTENTION_KNOWN_FIRST_INSTALL_TIMES = StateKey.json(
        name = "known_first_install_times", type = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None,
        store = NamedStores.PLUGIN_CENTER_ATTENTION,
    )

    /** Index sources already compared once (`PluginCenterAttentionStore`). */
    @JvmField
    val ATTENTION_INITIALIZED_SOURCES = StateKey.strSet(name = "initialized_sources", default = SettingDefault.None, store = NamedStores.PLUGIN_CENTER_ATTENTION)

    /** The current pins as an org.json array text (`PluginCenterAttentionStore`). */
    @JvmField
    val ATTENTION_PINS = StateKey.json(
        name = "pins", type = SettingType.Json(SettingsJson.ARRAY), default = SettingDefault.None, store = NamedStores.PLUGIN_CENTER_ATTENTION,
    )

    override val keys: List<PrefKey<*>> = listOf(
        PLUGIN_CENTER_SHOW_OFFICIAL_PLUGINS, PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS, PLUGIN_CENTER_AUTO_CHECK_UPDATES,
        FILTER_LEGACY_ORDINAL, FILTER_INSTALL_STATE, FILTER_ENABLED_STATE, FILTER_MECHANISM_STATE, FILTER_SOURCE_STATE, FILTER_UPDATABLE_ONLY,
    ) + SORT_ALL.keys + SORT_OFFICIAL.keys + SORT_THIRD_PARTY.keys + listOf(
        HIDDEN_PACKAGES, MOUNT_PACKAGES, JVM_SOURCE_EXACT_COMPONENT_JAVA, JVM_SOURCE_EXACT_COMPONENT_KOTLIN,
        ATTENTION_KNOWN_FIRST_INSTALL_TIMES, ATTENTION_INITIALIZED_SOURCES, ATTENTION_PINS,
    )

    override val families: List<KeyFamily<*, *>> = SORT_ALL.families + SORT_OFFICIAL.families + SORT_THIRD_PARTY.families + listOf(
        HIDDEN_TITLE, HIDDEN_SOURCE, LAUNCHER_ICON,
        MOUNT_ENABLED, MOUNT_OBJECT_NAME, MOUNT_POINT, UPDATE_CHECK, VARIANT_PRIORITY,
        PLUGIN_ENABLED, PLUGIN_SUSPENDED,
        LAST_INSTALL_TIME, LAST_UNINSTALL_TIME,
    )

    private fun filterInt(name: String, default: SettingDefault<Int>) =
        SettingKey.Int(name = name, category = PLUGIN_CENTER, default = default, store = NamedStores.PLUGIN_CENTER_FILTER_STATE)
}
