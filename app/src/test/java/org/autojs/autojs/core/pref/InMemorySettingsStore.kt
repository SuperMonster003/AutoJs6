package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.StoreId

/**
 * JVM test fixture standing in for one SharedPreferences file (docs/settings/ROADMAP.md P0.5, T3, R3).
 *
 * It holds the same value types as SharedPreferences (Boolean, Int, Long, Float, String, Set of String), applies an
 * [edit] block as one commit with the same rules (`clear()` runs before the puts of the same block, a null value
 * removes the key, putting an equal value is not a change) and notifies listeners once per changed key, with a null
 * key after a clear (Android 11+ behavior). Typed getters throw [ClassCastException] on a type mismatch like
 * SharedPreferences does. [commitCount] lets applier tests check "one commit per store" (T8).
 *
 * It implements [SettingsStore] (P1.2); `commit` makes no difference in memory.
 *
 * zh-CN: 一份 SharedPreferences 文件的 JVM 测试替身 (ROADMAP P0.5, T3, R3). 值类型与 SharedPreferences 相同, 一次 [edit] 等于
 * 一次提交且规则相同 (同一块内 `clear()` 先于 put 执行, null 值即删除, 写入相等的值不算变化), 每个变化的键通知一次, clear 之后
 * 通知一次 null 键 (Android 11 起的行为). 类型不符时类型化读取抛 [ClassCastException]. [commitCount] 供导入应用测试核对 "每个
 * 存储一次提交" (T8). 实现 [SettingsStore] (P1.2); 在内存中 `commit` 没有区别.
 */
class InMemorySettingsStore(
    val name: String = DEFAULT_NAME,
    initial: Map<String, Any?> = emptyMap(),
) : SettingsStore {

    override val id: StoreId = StoreId(name)

    private val values = LinkedHashMap<String, Any>()
    private val listeners = mutableListOf<(String?) -> Unit>()

    /** Number of [edit] blocks applied, including blocks that changed nothing. */
    var commitCount = 0
        private set

    init {
        initial.forEach { (key, value) -> if (value != null) values[key] = checkedValue(key, value) }
    }

    override fun get(name: String): Any? = values[name]?.let(::copyOf)

    override fun contains(name: String): Boolean = name in values

    /** A detached copy of every entry, in insertion order. */
    override fun all(): Map<String, Any> = values.entries.associateTo(LinkedHashMap()) { it.key to copyOf(it.value) }

    override fun getStringSet(name: String, defValue: Set<String>?): Set<String>? {
        val value = values[name] ?: return defValue
        if (value !is Set<*>) throw mismatch(name, value, "stringSet")
        @Suppress("UNCHECKED_CAST")
        return LinkedHashSet(value as Set<String>)
    }

    override fun getBoolean(name: String, defValue: Boolean): Boolean = typed(name, defValue, Boolean::class.javaObjectType)

    override fun getInt(name: String, defValue: Int): Int = typed(name, defValue, Int::class.javaObjectType)

    override fun getLong(name: String, defValue: Long): Long = typed(name, defValue, Long::class.javaObjectType)

    override fun getFloat(name: String, defValue: Float): Float = typed(name, defValue, Float::class.javaObjectType)

    override fun getString(name: String, defValue: String?): String? = typedOrNull(name, defValue, String::class.java)

    /**
     * Applies [block] as one commit and returns the keys whose value changed (removed keys included).
     * zh-CN: 把 [block] 作为一次提交应用, 返回值发生变化的键 (含被删除的键).
     */
    override fun edit(commit: Boolean, block: SettingsStore.Editor.() -> Unit): Set<String> {
        val editor = Editor(this).apply(block)
        commitCount += 1
        val changed = LinkedHashSet<String>()
        var cleared = false
        if (editor.clearRequested && values.isNotEmpty()) {
            changed.addAll(values.keys)
            values.clear()
            cleared = true
        }
        val reallyChanged = LinkedHashSet<String>()
        editor.pending.forEach { (key, value) ->
            val before = values[key]
            if (value == null) {
                if (values.remove(key) != null) reallyChanged.add(key)
            } else if (before != value) {
                values[key] = value
                reallyChanged.add(key)
            }
        }
        changed.addAll(reallyChanged)
        val snapshot = listeners.toList()
        if (cleared) snapshot.forEach { it(null) }
        reallyChanged.forEach { key -> snapshot.forEach { it(key) } }
        return changed
    }

    /**
     * Registers [listener] for changed keys (null after a clear); closing the result unregisters it.
     * zh-CN: 注册键变化监听 (clear 之后收到 null), 关闭返回值即注销.
     */
    override fun listen(listener: (key: String?) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    class Editor internal constructor(private val store: InMemorySettingsStore) : SettingsStore.Editor {

        internal val pending = LinkedHashMap<String, Any?>()
        internal var clearRequested = false
            private set

        /** A null [value] removes the key, like `putString(key, null)`. */
        override fun put(name: String, value: Any?) {
            pending[name] = value?.let { store.checkedValue(name, it) }
        }

        override fun remove(name: String) {
            pending[name] = null
        }

        override fun clear() {
            clearRequested = true
        }
    }

    private fun <T : Any> typed(key: String, defValue: T, type: Class<T>): T = typedOrNull(key, defValue, type)!!

    private fun <T : Any> typedOrNull(key: String, defValue: T?, type: Class<T>): T? {
        val value = values[key] ?: return defValue
        if (!type.isInstance(value)) throw mismatch(key, value, typeTagOfClass(type))
        return type.cast(value)
    }

    private fun mismatch(key: String, value: Any, expected: String) =
        ClassCastException("$name: \"$key\" holds ${typeTag(value)}, not $expected")

    internal fun checkedValue(key: String, value: Any): Any = SettingsStore.checkedValue(id, key, value)

    companion object {

        /** Store name of the default SharedPreferences (`StoreId` "default", appendix B). */
        const val DEFAULT_NAME = StoreId.DEFAULT_NAME

        /**
         * Type tag of a stored value as written to the archive (T6: bool / int / long / float / string / stringSet).
         * zh-CN: 归档中的类型标签 (T6).
         */
        @JvmStatic
        fun typeTag(value: Any): String = when (value) {
            is Boolean -> "bool"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            is String -> "string"
            is Set<*> -> "stringSet"
            else -> throw IllegalArgumentException("Unsupported type ${value::class.java.name}")
        }

        private fun typeTagOfClass(type: Class<*>): String = when (type) {
            Boolean::class.javaObjectType -> "bool"
            Int::class.javaObjectType -> "int"
            Long::class.javaObjectType -> "long"
            Float::class.javaObjectType -> "float"
            else -> "string"
        }

        private fun copyOf(value: Any): Any = if (value is Set<*>) LinkedHashSet(value) else value
    }
}
