package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.SettingsJson.SettingsJsonException
import org.autojs.autojs.core.pref.TypedValue

/**
 * The codec of the category files of an archive (docs/settings/ROADMAP.md T6, appendix B), on [SettingsJson] only:
 * `{"id": <category>, "stores": {<store>: {<key>: {"t": <type tag>, "v": <value>}}}}`. A `stringSet` value is an array,
 * a `json` value is the decoded JSON document (not its text again), and the other values are the JSON scalars of their
 * tags. Category files are written with an indent of 2, so that an archive reads well in an editor.
 *
 * Decoding is lenient per entry (D9): an entry with an unknown tag (of a newer format) or with a value that does not
 * fit its tag is left out and listed in [Decoded.rejected], the others are kept; only a file that is not a category file
 * at all is rejected as a whole. A `long` that fits an `Int` reads back as a `Long`, a `float` as the nearest `Float`
 * (the text of a `Float` reads back exactly), and a `json` value as its compact text.
 *
 * zh-CN: 归档中分类文件的编解码 (ROADMAP T6, 附录 B), 只依赖 [SettingsJson]: `{"id": 分类, "stores": {存储: {键: {"t": 类型标签,
 * "v": 值}}}}`. `stringSet` 值为数组, `json` 值为解码后的 JSON 文档 (不是再次字符串化的文本), 其余为标签对应的 JSON 标量.
 * 分类文件以缩进 2 写出, 便于在编辑器中查看. 解码按条目宽松 (D9): 标签未知 (更新的格式) 或值与标签不符的条目被略去并列入
 * [Decoded.rejected], 其余保留; 只有根本不是分类文件时才整体拒绝. 能放进 Int 的 `long` 读回仍为 Long, `float` 读回最接近的
 * Float (Float 的文本可精确读回), `json` 值读回为紧凑文本.
 */
object SettingsSnapshotCodec {

    const val TYPE = "t"
    const val VALUE = "v"

    /**
     * A decoded category file: the category and the entries left out (`<store>:<key> (<reason>)`).
     * zh-CN: 解码后的分类文件: 分类与被略去的条目 (`<存储>:<键> (<原因>)`).
     */
    data class Decoded(val category: SettingsSnapshot.Category, val rejected: List<String>)

    /** The JSON value of one entry: `{"t": <tag>, "v": <value>}`. zh-CN: 一个条目的 JSON 值. */
    @JvmStatic
    fun encodeValue(value: TypedValue): Map<String, Any?> = linkedMapOf(
        TYPE to value.type,
        VALUE to when (value.type) {
            SettingType.StrSet.tag -> (value.value as Set<*>).toList()
            TypedValue.JSON_TAG -> SettingsJson.parse(value.value as String)
            else -> value.value
        },
    )

    /**
     * The entry of the JSON value [json]; throws [SettingsJsonException] for an unknown tag or a value that does not
     * fit its tag.
     * zh-CN: JSON 值 [json] 对应的条目; 标签未知或值与标签不符时抛 [SettingsJsonException].
     */
    @JvmStatic
    fun decodeValue(json: Any?): TypedValue {
        val entry = json as? Map<*, *> ?: throw SettingsJsonException("not an entry object")
        val tag = entry[TYPE] as? String ?: throw SettingsJsonException("no type tag")
        val v = entry[VALUE]
        val value: Any? = when (tag) {
            SettingType.Bool.tag -> v as? Boolean
            SettingType.Int.tag -> SettingsJson.toInt(v)
            SettingType.Long.tag -> SettingsJson.toLong(v)
            SettingType.Float.tag -> SettingsJson.toFloat(v)
            SettingType.Str.tag -> v as? String
            SettingType.StrSet.tag -> (v as? List<*>)?.takeIf { list -> list.all { it is String } }?.mapTo(LinkedHashSet()) { it as String }
            TypedValue.JSON_TAG -> if (entry.containsKey(VALUE)) SettingsJson.stringify(v) else null
            else -> throw SettingsJsonException("unknown type tag \"$tag\"")
        }
        return TypedValue(tag, value ?: throw SettingsJsonException("not a $tag value"))
    }

    /** The JSON document of a category file. zh-CN: 分类文件的 JSON 文档. */
    @JvmStatic
    fun encodeCategory(category: SettingsSnapshot.Category): Map<String, Any?> = linkedMapOf(
        "id" to category.id,
        "stores" to category.stores.mapValuesTo(LinkedHashMap()) { (_, entries) ->
            entries.mapValuesTo(LinkedHashMap()) { (_, value) -> encodeValue(value) }
        },
    )

    /** The text of a category file. zh-CN: 分类文件的文本. */
    @JvmStatic
    fun categoryText(category: SettingsSnapshot.Category): String = SettingsJson.stringify(encodeCategory(category), indent = 2)

    /**
     * The category file [json]; throws [SettingsJsonException] when it is no category file (no object, no valid id, no
     * stores object).
     * zh-CN: 分类文件 [json]; 不是分类文件 (不是对象, id 无效, 没有 stores 对象) 时抛 [SettingsJsonException].
     */
    @JvmStatic
    fun decodeCategory(json: Any?): Decoded {
        val root = json as? Map<*, *> ?: throw SettingsJsonException("A category file is not a JSON object")
        val id = (root["id"] as? String)?.takeIf(SettingsArchive::isCategoryId) ?: throw SettingsJsonException("A category file has no valid id")
        val stores = root["stores"] as? Map<*, *> ?: throw SettingsJsonException("$id: the category file has no stores object")
        val rejected = ArrayList<String>()
        val decoded = LinkedHashMap<String, Map<String, TypedValue>>()
        stores.forEach { (store, entries) ->
            val storeName = store as String
            val map = entries as? Map<*, *> ?: run {
                rejected.add("$storeName (not an object of entries)")
                return@forEach
            }
            val values = LinkedHashMap<String, TypedValue>()
            map.forEach { (name, entry) ->
                try {
                    values[name as String] = decodeValue(entry)
                } catch (e: SettingsJsonException) {
                    rejected.add("$storeName:$name (${e.message})")
                }
            }
            decoded[storeName] = values
        }
        return Decoded(SettingsSnapshot.Category(id, decoded), rejected)
    }

    /** [decodeCategory] of the text of a category file. zh-CN: 由分类文件的文本解码. */
    @JvmStatic
    fun decodeCategoryText(text: String): Decoded = decodeCategory(SettingsJson.parse(text))
}
