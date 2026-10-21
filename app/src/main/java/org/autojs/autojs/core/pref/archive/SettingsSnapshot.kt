package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.OtherCategoryFallback
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingDescriptor
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * The settings of an export (docs/settings/ROADMAP.md T6, appendix B): category -> store -> key -> [TypedValue]. A
 * store is named by its [StoreId] name, `default` for the default SharedPreferences. Only stored values are in it: a
 * key without a stored value is absent, and importing the category restores its default (D8). Data categories (T19)
 * are never in it, they go to the archive through their own codecs. Pure data and logic over [SettingsStore] (R3).
 *
 * [unexportable] names the stored values (`<store>:<name>`) that [capture] left out because no type tag or no JSON
 * number fits them (for example a `Float.NaN`); the export wizard reports them (P6.4).
 *
 * zh-CN: 导出的设置 (ROADMAP T6, 附录 B): 分类 -> 存储 -> 键 -> [TypedValue]. 存储以 [StoreId] 名称表示, 默认 SharedPreferences
 * 为 `default`. 只含已存储的值: 没有存储值的键不出现, 导入该分类时恢复默认 (D8). 数据分类 (T19) 不进快照, 由各自的编解码写入归档.
 * 只依赖 [SettingsStore] 的纯数据与逻辑 (R3). [unexportable] 列出 [capture] 因没有合适的类型标签或 JSON 数字而略去的存储值
 * (`<存储>:<名称>`, 例如 `Float.NaN`), 由导出向导报告 (P6.4).
 */
data class SettingsSnapshot @JvmOverloads constructor(
    val categories: List<Category>,
    val unexportable: List<String> = emptyList(),
) {

    init {
        val duplicates = categories.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Categories listed more than once: $duplicates" }
    }

    /**
     * The settings of one category: store name -> key -> value, stores and keys in name order (`default` first).
     * zh-CN: 一个分类的设置: 存储名 -> 键 -> 值, 存储与键按名称排序 (`default` 在前).
     */
    data class Category(val id: String, val stores: Map<String, Map<String, TypedValue>>) {

        init {
            require(SettingsArchive.isCategoryId(id)) { "Invalid category id \"$id\"" }
        }

        /** The number of stored values, the `entries` of the manifest (appendix B). zh-CN: 存储值的个数, 即 manifest 的 `entries`. */
        val entries: Int
            get() = stores.values.sumOf { it.size }
    }

    /** The category [id], or null. */
    fun category(id: String): Category? = categories.firstOrNull { it.id == id }

    companion object {

        /**
         * The stored values of [categories] (the data ones are skipped), in the order of appendix A.1: every declared
         * key ([keys]) and every member of a key family ([families]) of the category whose store holds a value, and for
         * `other` also the entries of the default store that [rows] do not know, tagged by their run-time type
         * ([OtherCategoryFallback], P4.3). A value is tagged by its run-time type, so a value of another type than its
         * declaration keeps its own tag (the import counts it as a type mismatch, D9); a `json` declaration whose text
         * is no JSON document is tagged `string`. [open] gives the stores ([SettingsStores] in the app).
         *
         * zh-CN: [categories] 的存储值 (跳过数据分类), 按附录 A.1 的顺序: 分类中每个已声明的键 ([keys]) 与键族 ([families])
         * 成员中存储里有值的, `other` 另含默认存储中 [rows] 不认识的条目, 以运行时类型打标签 ([OtherCategoryFallback], P4.3).
         * 值一律按运行时类型打标签, 因此与声明类型不同的值保留自己的标签 (导入时计为类型不符, D9); 声明为 `json` 但文本不是 JSON
         * 文档的值标为 `string`. [open] 提供存储 (应用中为 [SettingsStores]).
         */
        @JvmStatic
        @JvmOverloads
        fun capture(
            categories: Collection<SettingCategory>,
            keys: List<PrefKey<*>> = SettingsRegistry.keys,
            families: List<KeyFamily<*, *>> = SettingsRegistry.families,
            rows: List<SettingDescriptor> = SettingsRegistry.describe(),
            open: (StoreId) -> SettingsStore = { SettingsStores[it] },
        ): SettingsSnapshot {
            val selected = categories.filterNot { it.isData }.toSet()
            val unexportable = ArrayList<String>()
            val result = SettingCategory.ALL.filter { it in selected }.map { category ->
                val stores = LinkedHashMap<StoreId, LinkedHashMap<String, TypedValue>>()
                fun add(store: StoreId, name: String, declaredTag: String, raw: Any?) {
                    val entries = stores.getOrPut(store) { LinkedHashMap() }
                    if (name in entries || raw == null) return
                    when (val value = tagged(declaredTag, raw)) {
                        null -> unexportable.add("${store.name}:$name")
                        else -> entries[name] = value
                    }
                }
                keys.filter { it.category == category }.forEach { key ->
                    add(key.store, key.name, key.type.tag, open(key.store)[key.name])
                }
                families.filter { it.category == category }.forEach { family ->
                    val store = family.template.store
                    val all = open(store).all()
                    all.keys.filter(family::matches).forEach { name -> add(store, name, family.template.type.tag, all[name]) }
                }
                if (category == SettingCategory.OTHER) {
                    val fallback = OtherCategoryFallback.collect(open(StoreId.DEFAULT).all(), rows, StoreId.DEFAULT)
                    fallback.entries.forEach { (name, value) -> add(StoreId.DEFAULT, name, value.type, value.value) }
                    fallback.untyped.forEach { unexportable.add("${StoreId.DEFAULT.name}:$it") }
                }
                Category(category.id, sorted(stores))
            }
            return SettingsSnapshot(result, unexportable.sorted())
        }

        private fun tagged(declaredTag: String, raw: Any): TypedValue? {
            val value = TypedValue.ofRuntimeType(raw) ?: return null
            if (value.value is Float && !value.value.isFinite()) return null
            val text = value.value as? String
            return if (declaredTag == TypedValue.JSON_TAG && text != null && isJson(text)) TypedValue(TypedValue.JSON_TAG, text) else value
        }

        private fun isJson(text: String): Boolean = try {
            SettingsJson.parse(text)
            true
        } catch (_: SettingsJson.SettingsJsonException) {
            false
        }

        private fun sorted(stores: Map<StoreId, Map<String, TypedValue>>): Map<String, Map<String, TypedValue>> =
            stores.filterValues { it.isNotEmpty() }.entries
                .sortedWith(compareBy({ !it.key.isDefault }, { it.key.name }))
                .associateTo(LinkedHashMap()) { (store, entries) -> store.name to LinkedHashMap(entries.toSortedMap()) }
    }
}
