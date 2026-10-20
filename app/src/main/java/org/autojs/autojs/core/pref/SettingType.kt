package org.autojs.autojs.core.pref

/**
 * Value type of a key (docs/settings/ROADMAP.md T1 / T6): how the value is stored in a SharedPreferences-like store
 * and how it is written to an archive.
 *
 * - Stored form ([fromStored] / [toStored]): the SharedPreferences types, `Boolean`, `Int`, `Long`, `Float`,
 *   `String` and `Set<String>`; [Enum] stores the key string of the value, [Json] the JSON text of [SettingsJson].
 * - Archive form ([toJson] / [fromJson]): a plain JSON value of [SettingsJson] with the type tag [tag] (T6: bool / int
 *   / long / float / string / stringSet / json); a [Json] value is the decoded JSON value, not its text (appendix B).
 *
 * [fromStored] and [fromJson] return null when the value has another type or is not valid (an [Enum] value that is
 * not a candidate, malformed [Json] text); what a reader does then is the caller's decision.
 *
 * zh-CN: 键的值类型 (ROADMAP T1 / T6): 值在类 SharedPreferences 存储中的形式, 以及写入归档的形式. 存储形式为
 * SharedPreferences 的六种类型 ([Enum] 存候选项的键字符串, [Json] 存 [SettingsJson] 的文本); 归档形式为带类型标签 [tag]
 * 的普通 JSON 值 ([Json] 为解码后的 JSON 值而非文本, 附录 B). 值的类型不符或无效时 (不在候选项中的 [Enum] 值, 格式错误的
 * [Json] 文本) [fromStored] 与 [fromJson] 返回 null, 由调用方决定如何处理.
 */
sealed class SettingType<T : Any>(val tag: String) {

    abstract fun fromStored(raw: Any): T?

    abstract fun toStored(value: T): Any

    abstract fun toJson(value: T): Any?

    abstract fun fromJson(json: Any?): T?

    /** Text of [value] for descriptors (T4 `defaultText`). */
    open fun describe(value: T): String = value.toString()

    override fun toString(): String = tag

    object Bool : SettingType<Boolean>("bool") {
        override fun fromStored(raw: Any) = raw as? Boolean
        override fun toStored(value: Boolean): Any = value
        override fun toJson(value: Boolean): Any = value
        override fun fromJson(json: Any?) = json as? Boolean
    }

    object Int : SettingType<kotlin.Int>("int") {
        override fun fromStored(raw: Any) = raw as? kotlin.Int
        override fun toStored(value: kotlin.Int): Any = value
        override fun toJson(value: kotlin.Int): Any = value
        override fun fromJson(json: Any?) = SettingsJson.toInt(json)
    }

    object Long : SettingType<kotlin.Long>("long") {
        override fun fromStored(raw: Any) = raw as? kotlin.Long
        override fun toStored(value: kotlin.Long): Any = value
        override fun toJson(value: kotlin.Long): Any = value
        override fun fromJson(json: Any?) = SettingsJson.toLong(json)
    }

    object Float : SettingType<kotlin.Float>("float") {
        override fun fromStored(raw: Any) = raw as? kotlin.Float
        override fun toStored(value: kotlin.Float): Any = value
        override fun toJson(value: kotlin.Float): Any = value
        override fun fromJson(json: Any?) = SettingsJson.toFloat(json)
    }

    object Str : SettingType<String>("string") {
        override fun fromStored(raw: Any) = raw as? String
        override fun toStored(value: String): Any = value
        override fun toJson(value: String): Any = value
        override fun fromJson(json: Any?) = json as? String
    }

    /** Archives write the elements sorted, since SharedPreferences keeps no order for a string set. */
    object StrSet : SettingType<Set<String>>("stringSet") {
        override fun fromStored(raw: Any): Set<String>? = (raw as? Set<*>)?.let(::stringsOrNull)
        override fun toStored(value: Set<String>): Any = LinkedHashSet(value)
        override fun toJson(value: Set<String>): Any = value.sorted()
        override fun fromJson(json: Any?): Set<String>? = (json as? List<*>)?.let(::stringsOrNull)
        override fun describe(value: Set<String>): String = SettingsJson.stringify(value.sorted())

        private fun stringsOrNull(items: Collection<*>): Set<String>? =
            if (items.all { it is String }) items.mapTo(LinkedHashSet()) { it as String } else null
    }

    /**
     * A value stored as the key string of one of [candidates] (`key_night_mode_follow_system` and so on). The
     * candidates are read on first use, so a declaration may resolve resource key names lazily.
     * zh-CN: 以候选项的键字符串存储的值; 候选项在首次使用时读取, 因此声明处可以惰性解析资源键名.
     */
    class Enum<E : Any>(candidates: () -> List<E>, private val keyOf: (E) -> String) : SettingType<E>("string") {

        val candidates: List<E> by lazy {
            candidates().also { list ->
                require(list.isNotEmpty()) { "An Enum type needs at least one candidate" }
                val keys = list.map(keyOf)
                require(keys.toSet().size == keys.size) { "Duplicate candidate keys: $keys" }
            }
        }

        val candidateKeys: List<String>
            get() = candidates.map(keyOf)

        fun keyOf(value: E): String = keyOf.invoke(value)

        fun ofKeyOrNull(key: String): E? = candidates.firstOrNull { keyOf(it) == key }

        fun isCandidate(value: E): Boolean = ofKeyOrNull(keyOf(value)) != null

        override fun fromStored(raw: Any) = (raw as? String)?.let(::ofKeyOrNull)
        override fun toStored(value: E): Any = checkedKey(value)
        override fun toJson(value: E): Any = checkedKey(value)
        override fun fromJson(json: Any?) = (json as? String)?.let(::ofKeyOrNull)
        override fun describe(value: E): String = keyOf(value)

        private fun checkedKey(value: E): String = keyOf(value).also { key ->
            require(ofKeyOrNull(key) != null) { "\"$key\" is not one of $candidateKeys" }
        }

        companion object {

            /** An Enum whose values are the key strings themselves. */
            @JvmStatic
            fun ofKeys(candidates: () -> List<String>): Enum<String> = Enum(candidates) { it }
        }
    }

    /**
     * A value stored as JSON text through [codec]. [htmlSafe] writes the text the way Gson does (see [SettingsJson]),
     * for keys whose stored text Gson used to write (R1).
     * zh-CN: 经 [codec] 以 JSON 文本存储的值. [htmlSafe] 按 Gson 的方式写文本, 用于以前由 Gson 写入的键 (R1).
     */
    class Json<V : Any> @JvmOverloads constructor(
        val codec: SettingsJson.Codec<V>,
        val htmlSafe: Boolean = false,
    ) : SettingType<V>("json") {

        override fun fromStored(raw: Any): V? = (raw as? String)?.let { text ->
            try {
                codec.decode(SettingsJson.parse(text))
            } catch (_: SettingsJson.SettingsJsonException) {
                null
            }
        }

        override fun toStored(value: V): Any = SettingsJson.stringify(codec.encode(value), htmlSafe)

        override fun toJson(value: V): Any? = SettingsJson.normalize(codec.encode(value))

        override fun fromJson(json: Any?): V? = try {
            codec.decode(json)
        } catch (_: SettingsJson.SettingsJsonException) {
            null
        }

        override fun describe(value: V): String = SettingsJson.stringify(codec.encode(value))
    }
}
