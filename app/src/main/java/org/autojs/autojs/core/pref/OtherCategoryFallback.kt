package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.StoreId

/**
 * A stored value with its type tag (docs/settings/ROADMAP.md T6): one entry of a settings snapshot. [type] is one of
 * the tags of [SettingType] (`bool` / `int` / `long` / `float` / `string` / `stringSet` / `json`) and [value] the value
 * in the form that tag names (a `json` value is the stored text, the codec of P6.1 decides how to write it).
 *
 * zh-CN: 带类型标签的存储值 (ROADMAP T6), 设置快照的一个条目. [type] 为 [SettingType] 的标签之一, [value] 为该标签对应
 * 形式的值 (`json` 值为存储的文本, 由 P6.1 的编解码决定写法).
 */
data class TypedValue(val type: String, val value: Any) {

    init {
        val matches = when (type) {
            SettingType.Bool.tag -> value is Boolean
            SettingType.Int.tag -> value is Int
            SettingType.Long.tag -> value is Long
            SettingType.Float.tag -> value is Float
            SettingType.Str.tag, JSON_TAG -> value is String
            SettingType.StrSet.tag -> value is Set<*> && value.all { it is String }
            else -> throw IllegalArgumentException("Unknown type tag \"$type\"")
        }
        require(matches) { "A $type value cannot be ${value::class.java.name}" }
    }

    companion object {

        /** The tag of `SettingType.Json` (its instances carry a codec, so there is no object to read it from). */
        const val JSON_TAG = "json"

        /**
         * The value of a `SharedPreferences.getAll()` entry tagged by its run-time type (T6), or null for a type that
         * SharedPreferences cannot hold. A text is always `string`: whether it is JSON is known only to a declaration.
         * Sets are copied in name order, so a snapshot does not depend on the iteration order of the store.
         * zh-CN: 按运行时类型给 `SharedPreferences.getAll()` 的条目打标签 (T6), SharedPreferences 不能保存的类型返回 null.
         * 文本一律为 `string`: 是否为 JSON 只有键声明知道. 集合按名称排序复制, 快照与存储的遍历顺序无关.
         */
        @JvmStatic
        fun ofRuntimeType(value: Any?): TypedValue? = when (value) {
            is Boolean -> TypedValue(SettingType.Bool.tag, value)
            is Int -> TypedValue(SettingType.Int.tag, value)
            is Long -> TypedValue(SettingType.Long.tag, value)
            is Float -> TypedValue(SettingType.Float.tag, value)
            is String -> TypedValue(SettingType.Str.tag, value)
            is Set<*> -> when {
                value.all { it is String } -> TypedValue(SettingType.StrSet.tag, value.map { it as String }.sorted().toCollection(LinkedHashSet()))
                else -> null
            }
            else -> null
        }
    }
}

/**
 * The run-time fallback of the `other` category (docs/settings/ROADMAP.md P4.3, T6): entries of a store that no
 * registry row knows still go into `other` on export, tagged by their run-time type, so that values written by code
 * the registry does not cover yet (names built at run time, such as the stack trace keys of `NotAskAgainDialog`,
 * appendix C Q23, or keys of a newer version) are not lost. Pure logic over [SettingDescriptor] rows and plain values
 * (R3); P6.1 builds the `other` part of `SettingsSnapshot` from [collect].
 *
 * A name is known when a row of its store declares it ([SettingKind.SETTING], [SettingKind.STATE],
 * [SettingKind.CACHE] or [SettingKind.EXCLUDED]), or when it is a member of a family row of that store. Rows of
 * [SettingKind.VALUE_CONSTANT] hold no value, so a stored entry that only shares the name of a candidate value is not
 * known (the names that are both, such as `key_timed_task_backend_alarm`, have a declared key too, P4.1).
 *
 * zh-CN: "其他" 分类的运行时兜底 (ROADMAP P4.3, T6): 存储中没有任何注册表行认识的条目, 导出时仍归入 `other`, 以运行时类型
 * 打标签, 以免丢失注册表尚未覆盖的代码写入的值 (运行时拼出的名称, 如 `NotAskAgainDialog` 的调用栈键 (附录 C Q23), 或更新
 * 版本的键). 只处理 [SettingDescriptor] 行与普通值的纯逻辑 (R3); P6.1 由 [collect] 构造 `SettingsSnapshot` 的 `other` 部分.
 * 名称被认识, 指同一存储的某行声明了它 (设置, 状态, 缓存或排除项), 或它是同一存储某个键族行的成员. 设置值常量不持有值,
 * 只与候选值同名的存储条目不算被认识 (既是候选值又是存储键的名称, 如 `key_timed_task_backend_alarm`, 另有键声明, P4.1).
 */
object OtherCategoryFallback {

    /**
     * The result of [collect]: the unknown entries tagged by run-time type in name order, and the names of unknown
     * entries whose value has no type tag (SharedPreferences never returns such values; they are reported, not exported).
     * zh-CN: [collect] 的结果: 按名称排序, 以运行时类型打标签的未登记条目; 以及值无法打标签的未登记条目名称 (SharedPreferences
     * 不会返回这类值; 只报告, 不导出).
     */
    data class Result(val entries: Map<String, TypedValue>, val untyped: List<String>)

    /**
     * True when a row of [rows] for [store] declares [name] or a family row of [store] has it as a member.
     * zh-CN: [rows] 中属于 [store] 的某行声明了 [name], 或 [store] 的某个键族行以它为成员时为 true.
     */
    @JvmStatic
    @JvmOverloads
    fun isKnown(name: String, rows: List<SettingDescriptor> = SettingsRegistry.describe(), store: StoreId = StoreId.DEFAULT): Boolean =
        Index(rows, store).isKnown(name)

    /**
     * The entries of [all] (the `getAll()` of [store]) that [rows] do not know, tagged by run-time type.
     * zh-CN: [all] ([store] 的 `getAll()`) 中 [rows] 不认识的条目, 以运行时类型打标签.
     */
    @JvmStatic
    @JvmOverloads
    fun collect(all: Map<String, *>, rows: List<SettingDescriptor> = SettingsRegistry.describe(), store: StoreId = StoreId.DEFAULT): Result {
        val index = Index(rows, store)
        val entries = LinkedHashMap<String, TypedValue>()
        val untyped = ArrayList<String>()
        all.keys.filterNot(index::isKnown).sorted().forEach { name ->
            when (val typed = TypedValue.ofRuntimeType(all[name])) {
                null -> untyped.add(name)
                else -> entries[name] = typed
            }
        }
        return Result(entries, untyped)
    }

    private class Index(rows: List<SettingDescriptor>, store: StoreId) {

        private val ofStore = rows.filter { it.store == store.name && it.kind != SettingKind.VALUE_CONSTANT }

        private val names = ofStore.filterNot { it.family }.mapTo(HashSet()) { it.name }

        private val prefixes = ofStore.filter { it.family }.map { it.name }

        fun isKnown(name: String): Boolean = name in names || (name.isNotEmpty() && prefixes.any { name.startsWith(it) })
    }
}
