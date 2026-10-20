package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKeyNames
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingResources
import org.autojs.autojs.core.pref.SettingType

// Helpers shared by the domain objects of core/pref/keys (docs/settings/ROADMAP.md P2, T1 / T2). A computed default
// names the resource it reads ("R.bool.pref_guard_mode"), which is what descriptors show; SettingsDomainsTest checks
// that the name is the resource the default actually reads and that its value is the one docs/settings/defaults.md
// records for the key (R2).
// zh-CN: core/pref/keys 下各领域对象共用的辅助函数 (ROADMAP P2, T1 / T2). 计算型默认值写明所读的资源名 (描述中显示的即该名称);
// SettingsDomainsTest 检查该名称就是默认值实际读取的资源, 且取值与 defaults.md 记录的一致 (R2).

/** A default read from `R.bool.<name>` on every call, like `resources.getBoolean(...)`. */
internal fun boolResource(id: Int, name: String): SettingDefault<Boolean> =
    SettingDefault.computed("R.bool.$name") { SettingResources.bool(id) }

/** A default read from `R.integer.<name>` on every call, like `resources.getInteger(...)`. */
internal fun intResource(id: Int, name: String): SettingDefault<Int> =
    SettingDefault.computed("R.integer.$name") { SettingResources.int(id) }

/** A default read from `R.string.<name>` on every call, like `context.getString(...)` (locale dependent texts). */
internal fun stringResource(id: Int, name: String): SettingDefault<String> =
    SettingDefault.computed("R.string.$name") { SettingResources.string(id) }

/** A default key string read from `R.string.<name>` (a `default_key_*` alias), like `key(R.string.default_key_*)`. */
internal fun keyResource(id: Int, name: String): SettingDefault<String> =
    SettingDefault.computed("R.string.$name") { SettingKeyNames.resolve(id) }

/** An Enum type whose candidates are the key strings of [ids] (resolved on first use). */
internal fun keyChoices(vararg ids: Int): SettingType.Enum<String> = ids.copyOf().let { copy ->
    SettingType.Enum.ofKeys { copy.map(SettingKeyNames::resolve) }
}

/** Registered candidate values (T2 `ValueConstant`). */
internal fun valueConstants(vararg ids: Int): List<RegisteredName> = ids.map { RegisteredName(it, SettingKind.VALUE_CONSTANT) }

/** Registered names that are no preference keys, such as action items of a settings page (T2 `Excluded`). */
internal fun excludedNames(vararg ids: Int): List<RegisteredName> = ids.map { RegisteredName(it, SettingKind.EXCLUDED) }

/** The key string of a `key_*` resource ([SettingKeyNames]). */
internal fun keyOf(id: Int): String = SettingKeyNames.resolve(id)
