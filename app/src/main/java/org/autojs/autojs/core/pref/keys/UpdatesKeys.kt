package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.UPDATES
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs6.R

/**
 * Settings > Updates (docs/settings/ROADMAP.md P2.4, appendix A.1 `updates`): checking for updates automatically and
 * the ignored versions of the host (a JSON string list, written byte for byte as Gson did, P1.4). The check
 * timestamps are states (P4.2); the ignored versions of plugins are the named store `UpdateIgnoreStore` (P3.3).
 *
 * zh-CN: 设置 > 更新 (ROADMAP P2.4, 附录 A.1 `updates`): 自动检查更新, 以及宿主被忽略的版本 (JSON 字符串列表, 写出的文本与 Gson
 * 逐字节一致, P1.4). 检查时间戳为状态 (P4.2); 插件被忽略的版本是命名存储 `UpdateIgnoreStore` (P3.3).
 */
object UpdatesKeys : SettingsDomain {

    @JvmField
    val AUTO_CHECK_FOR_UPDATES = SettingKey.Bool(
        R.string.key_auto_check_for_updates, UPDATES,
        boolResource(R.bool.pref_auto_check_for_updates, "pref_auto_check_for_updates"),
    )

    /** Summaries of the ignored host versions in insertion order (`Pref.getLinkedHashSet` / `putLinkedHashSet`). */
    @JvmField
    val IGNORED_UPDATES = SettingKey.Json(
        R.string.key_ignored_updates, UPDATES, SettingType.Json(SettingsJson.STRING_LIST, htmlSafe = true), SettingDefault.of(emptyList()),
    )

    /**
     * Ignored versions per plugin package name in the named store named like [IGNORED_UPDATES] (`UpdateIgnoreStore`,
     * P3.3): "<versionCode>|<versionName>" or "<versionCode>".
     * zh-CN: 每个插件包名被忽略的版本, 在与 [IGNORED_UPDATES] 同名的命名存储中 (P3.3).
     */
    @JvmField
    val IGNORED_PLUGIN_VERSIONS = KeyFamily("key_\$_ignored_plugin_\$_") { name ->
        SettingKey.StrSet(name = name, category = UPDATES, default = SettingDefault.of(emptySet()), store = NamedStores.IGNORED_UPDATES)
    }

    override val keys: List<PrefKey<*>> = listOf(AUTO_CHECK_FOR_UPDATES, IGNORED_UPDATES)

    override val families: List<KeyFamily<*, *>> = listOf(IGNORED_PLUGIN_VERSIONS)

    override val names: List<RegisteredName> = excludedNames(
        // Entries of the updates sections that hold no value.
        R.string.key_check_for_updates,
        R.string.key_check_for_updates_while_ignoring_the_local_version,
        R.string.key_manage_ignored_updates,
        R.string.key_release_history,
        R.string.key_updates_checked_states_cleared,
    )

    /**
     * The ignored versions as `Pref.getLinkedHashSet` read them: empty when the key is missing, otherwise the stored
     * JSON text decoded strictly (malformed text throws, as before, R2).
     * zh-CN: 读法同 `Pref.getLinkedHashSet`: 键不存在时为空, 否则严格解码存储的 JSON 文本 (格式错误时与原先一样抛出, R2).
     */
    @JvmStatic
    fun ignoredUpdates(): LinkedHashSet<String> {
        val text = IGNORED_UPDATES.settingsStore.getString(IGNORED_UPDATES.name, null) ?: return linkedSetOf()
        return LinkedHashSet(SettingsJson.decodeStringList(text))
    }

    @JvmStatic
    fun setIgnoredUpdates(values: Collection<String>) = IGNORED_UPDATES.set(values.toList())
}
