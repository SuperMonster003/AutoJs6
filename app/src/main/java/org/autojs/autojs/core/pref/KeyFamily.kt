package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.SettingsStore

/**
 * A family of keys named [prefix] followed by a part chosen at run time (docs/settings/ROADMAP.md P3): a package name,
 * a profile id, a sort column and so on. Every member has the type, category, store and default of [template], the
 * declaration the registry describes (`SettingsDomain.families`); the template is named [prefix] itself, so the guard
 * tests match it with the prefix entries of `tools/settings/classification.json` (T5 a), and export / import (P6 / P7)
 * take every stored name that starts with [prefix] as a member ([storedNames]).
 *
 * Members are made on demand by [key] with the same factory as the template, so reading or writing one is the same as
 * reading or writing a key declared on its own (`get()` / `set()` / `isStored()` ...).
 *
 * zh-CN: 名称为 [prefix] 加运行时确定的部分 (包名, 配置档 id, 排序列等) 的一族键 (ROADMAP P3). 每个成员与 [template] 的值类型,
 * 分类, 存储与默认值相同; [template] 是注册表描述的声明 (`SettingsDomain.families`), 名称就是 [prefix], 因此守卫测试 (T5 a)
 * 以 `classification.json` 的前缀条目匹配它, 导出 / 导入 (P6 / P7) 把存储中以 [prefix] 开头的名称都视为成员 ([storedNames]).
 * 成员由 [key] 用同一工厂按需创建, 读写成员与读写单独声明的键相同.
 */
class KeyFamily<T : Any, K : PrefKey<T>>(val prefix: String, private val factory: (name: String) -> K) {

    init {
        require(prefix.isNotBlank()) { "Key family prefix is blank" }
    }

    /** The declaration the registry describes; its name is [prefix]. */
    val template: K = factory(prefix).also {
        require(it.name == prefix) { "The factory of a key family must use the name it is given, got ${it.name} for $prefix" }
    }

    val kind: SettingKind
        get() = template.kind

    val category: SettingCategory?
        get() = template.category

    val settingsStore: SettingsStore
        get() = template.settingsStore

    /** The name of the member for [suffix]. */
    fun nameOf(suffix: String): String = prefix + suffix

    /** The member for [suffix] (a non-empty part after [prefix]). */
    fun key(suffix: String): K {
        require(suffix.isNotEmpty()) { "A member of $prefix needs a suffix" }
        return factory(nameOf(suffix))
    }

    /** True when [name] is a member: it starts with [prefix] and has something after it. */
    fun matches(name: String): Boolean = name.length > prefix.length && name.startsWith(prefix)

    /** The part of [name] after [prefix], or null when [name] is no member. */
    fun suffixOf(name: String): String? = if (matches(name)) name.substring(prefix.length) else null

    /** Names of the members held by the store of the family. */
    fun storedNames(): List<String> = settingsStore.all().keys.filter(::matches)

    override fun toString(): String = "KeyFamily($prefix*, ${template.type.tag}, ${template.store})"
}
