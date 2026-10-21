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
 * reading or writing a key declared on its own (`get()` / `set()` / `isStored()` ...). A family with an empty prefix
 * ([everyName]) is every name of a named store whose names are run-time values themselves (signer fingerprints,
 * capability ids); its template is named [EVERY_NAME].
 *
 * zh-CN: 名称为 [prefix] 加运行时确定的部分 (包名, 配置档 id, 排序列等) 的一族键 (ROADMAP P3). 每个成员与 [template] 的值类型,
 * 分类, 存储与默认值相同; [template] 是注册表描述的声明 (`SettingsDomain.families`), 名称就是 [prefix], 因此守卫测试 (T5 a)
 * 以 `classification.json` 的前缀条目匹配它, 导出 / 导入 (P6 / P7) 把存储中以 [prefix] 开头的名称都视为成员 ([storedNames]).
 * 成员由 [key] 用同一工厂按需创建, 读写成员与读写单独声明的键相同. 前缀为空的键族 ([everyName]) 即某个命名存储中的全部名称
 * (名称本身就是运行时的值, 如签名指纹, 能力 id), 其 [template] 名为 [EVERY_NAME].
 */
class KeyFamily<T : Any, K : PrefKey<T>>(val prefix: String, private val factory: (name: String) -> K) {

    init {
        require(prefix.isEmpty() || prefix.isNotBlank()) { "Key family prefix is blank" }
    }

    /** The declaration the registry describes; its name is [prefix], or [EVERY_NAME] for an empty prefix. */
    val template: K = (prefix.ifEmpty { EVERY_NAME }).let { name ->
        factory(name).also {
            require(it.name == name) { "The factory of a key family must use the name it is given, got ${it.name} for $name" }
            require(prefix.isNotEmpty() || !it.store.isDefault) { "A family of every name needs a named store" }
        }
    }

    val kind: SettingKind
        get() = template.kind

    val category: SettingCategory?
        get() = template.category

    val settingsStore: SettingsStore
        get() = template.settingsStore

    /** The name of the member for [suffix]. */
    fun nameOf(suffix: String): String = prefix + suffix

    /**
     * The member for [suffix]. An empty suffix names the key [prefix] itself, which callers that build names from an
     * empty run-time part always wrote (R2); a family of every name needs a non-empty one.
     * zh-CN: [suffix] 对应的成员. 空后缀即名为 [prefix] 本身的键 (运行时部分为空的调用方原先就写这个名称, R2); 全名称键族需要非空后缀.
     */
    fun key(suffix: String): K {
        require(suffix.isNotEmpty() || prefix.isNotEmpty()) { "A member of a family of every name needs a name" }
        return factory(nameOf(suffix))
    }

    /** True when [name] is a member: a non-empty name that starts with [prefix]. */
    fun matches(name: String): Boolean = name.isNotEmpty() && name.startsWith(prefix)

    /** The part of [name] after [prefix], or null when [name] is no member. */
    fun suffixOf(name: String): String? = if (matches(name)) name.substring(prefix.length) else null

    /** Names of the members held by the store of the family. */
    fun storedNames(): List<String> = settingsStore.all().keys.filter(::matches)

    override fun toString(): String = "KeyFamily($prefix*, ${template.type.tag}, ${template.store})"

    companion object {

        /** The name of the template of a family with an empty prefix. */
        const val EVERY_NAME = "*"

        /**
         * A family of every name of the named store its factory declares (an empty prefix).
         * zh-CN: 由工厂声明的命名存储中全部名称组成的键族 (前缀为空).
         */
        @JvmStatic
        fun <T : Any, K : PrefKey<T>> everyName(factory: (name: String) -> K): KeyFamily<T, K> = KeyFamily("", factory)
    }
}
