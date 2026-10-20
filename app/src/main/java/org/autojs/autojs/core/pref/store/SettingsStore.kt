package org.autojs.autojs.core.pref.store

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingType

/**
 * One settings store (docs/settings/ROADMAP.md T3): the default SharedPreferences, a named SharedPreferences file or a
 * JSON file, behind the same members (get / put / remove / contains / all / edit {} / listen).
 *
 * Values have the SharedPreferences types (`Boolean`, `Int`, `Long`, `Float`, `String`, `Set<String>`) and follow its
 * rules: one [edit] block is one write in which `clear()` runs before the puts, a null value removes the key, putting
 * an equal value is no change, and a typed getter throws [ClassCastException] when the stored value has another type.
 * [edit] returns the keys whose value changed; listeners get each changed key once and a null key after a clear.
 * `commit = false` is `apply()` for SharedPreferences (the write reaches the disk later, as `Pref.putXxx` does) and
 * `commit = true` is `commit()` (as `Pref.putXxxSync` does).
 *
 * Typed access through a declared key ([get], [getOrNull], [set], [storedOrNull]) is added by extension functions
 * below; callers usually go through the members of the key itself (`PrefKey.get()` / `set()`, P2).
 *
 * zh-CN: 一个设置存储 (ROADMAP T3): 默认 SharedPreferences, 命名 SharedPreferences 文件或 JSON 文件, 成员统一. 值类型与规则
 * 同 SharedPreferences: 一个 [edit] 块为一次写入, 块内 `clear()` 先于 put 执行, null 值即删除, 写入相等的值不算变化, 类型不符
 * 时类型化读取抛 [ClassCastException]. [edit] 返回值发生变化的键; 监听器对每个变化的键收到一次通知, clear 之后收到 null.
 * `commit = false` 对应 `apply()` (同 `Pref.putXxx`), `commit = true` 对应 `commit()` (同 `Pref.putXxxSync`). 经键声明的
 * 类型化读写由下方扩展函数提供, 调用方通常经键自身的成员 (`PrefKey.get()` / `set()`, P2) 读写.
 */
interface SettingsStore {

    val id: StoreId

    /** The stored value of [name] (one of the SharedPreferences types), or null when absent. */
    operator fun get(name: String): Any?

    fun contains(name: String): Boolean

    /** A detached copy of every entry. */
    fun all(): Map<String, Any>

    /**
     * Applies [block] as one write and returns the keys whose value changed (removed keys included).
     * zh-CN: 把 [block] 作为一次写入应用, 返回值发生变化的键 (含被删除的键).
     */
    fun edit(commit: Boolean = false, block: Editor.() -> Unit): Set<String>

    /**
     * Registers [listener] for changed keys (null after a clear); closing the result unregisters it. The thread the
     * listener runs on is the store's (the main thread for SharedPreferences).
     * zh-CN: 注册键变化监听 (clear 之后收到 null), 关闭返回值即注销; 回调线程由存储决定 (SharedPreferences 为主线程).
     */
    fun listen(listener: (key: String?) -> Unit): AutoCloseable

    fun put(name: String, value: Any?, commit: Boolean = false): Set<String> = edit(commit) { put(name, value) }

    fun remove(name: String, commit: Boolean = false): Set<String> = edit(commit) { remove(name) }

    fun clear(commit: Boolean = false): Set<String> = edit(commit) { clear() }

    fun getBoolean(name: String, defValue: Boolean): Boolean = typed(name, defValue)

    fun getInt(name: String, defValue: Int): Int = typed(name, defValue)

    fun getLong(name: String, defValue: Long): Long = typed(name, defValue)

    fun getFloat(name: String, defValue: Float): Float = typed(name, defValue)

    fun getString(name: String, defValue: String?): String? = typedOrNull(name, defValue, String::class.java)

    fun getStringSet(name: String, defValue: Set<String>?): Set<String>? {
        val value = get(name) ?: return defValue
        if (value !is Set<*>) throw ClassCastException("$id: \"$name\" holds ${value.javaClass.name}, not a string set")
        @Suppress("UNCHECKED_CAST")
        return value as Set<String>
    }

    interface Editor {

        /** A null [value] removes the key, like `putString(key, null)`. */
        fun put(name: String, value: Any?)

        fun remove(name: String)

        fun clear()

        fun putBoolean(name: String, value: Boolean) = put(name, value)

        fun putInt(name: String, value: Int) = put(name, value)

        fun putLong(name: String, value: Long) = put(name, value)

        fun putFloat(name: String, value: Float) = put(name, value)

        fun putString(name: String, value: String?) = put(name, value)

        fun putStringSet(name: String, value: Set<String>?) = put(name, value)
    }

    companion object {

        /**
         * Checks that [value] is one of the SharedPreferences types and copies a set; shared by the implementations.
         * zh-CN: 检查 [value] 属于 SharedPreferences 的类型并复制集合; 供各实现共用.
         */
        @JvmStatic
        fun checkedValue(id: StoreId, name: String, value: Any): Any = when (value) {
            is Boolean, is Int, is Long, is Float, is String -> value
            is Set<*> -> {
                require(value.all { it is String }) { "$id: \"$name\" set elements must all be strings" }
                @Suppress("UNCHECKED_CAST")
                LinkedHashSet(value as Set<String>)
            }
            else -> throw IllegalArgumentException("$id: \"$name\" has unsupported type ${value.javaClass.name}")
        }
    }
}

private inline fun <reified T : Any> SettingsStore.typed(name: String, defValue: T): T = typedOrNull(name, defValue, T::class.javaObjectType)!!

private fun <T : Any> SettingsStore.typedOrNull(name: String, defValue: T?, type: Class<T>): T? {
    val value = get(name) ?: return defValue
    if (!type.isInstance(value)) throw ClassCastException("$id: \"$name\" holds ${value.javaClass.name}, not ${type.simpleName}")
    return type.cast(value)
}

/**
 * The stored value of [key] read with the typed getters, or null when absent or invalid ([SettingType]); a stored
 * value of another SharedPreferences type throws [ClassCastException] like the typed getters do.
 * zh-CN: 用类型化读取取 [key] 的存储值, 不存在或无效时为 null; 存储值为其他 SharedPreferences 类型时与类型化读取一样抛
 * [ClassCastException].
 */
fun <T : Any> SettingsStore.storedOrNull(key: PrefKey<T>): T? {
    checkStore(key)
    val name = key.name
    if (!contains(name)) return null
    @Suppress("UNCHECKED_CAST")
    val raw: Any? = when (key.type) {
        SettingType.Bool -> getBoolean(name, false)
        SettingType.Int -> getInt(name, 0)
        SettingType.Long -> getLong(name, 0L)
        SettingType.Float -> getFloat(name, 0f)
        SettingType.StrSet -> getStringSet(name, null)
        is SettingType.Str, is SettingType.Enum<*>, is SettingType.Json<*> -> getString(name, null)
    }
    return raw?.let(key::decode)
}

/** The stored value of [key], else its default; null only for a key without default ([org.autojs.autojs.core.pref.SettingDefault.None]). */
fun <T : Any> SettingsStore.getOrNull(key: PrefKey<T>): T? = storedOrNull(key) ?: key.defaultOrNull()

/** The stored value of [key], else its default; throws [IllegalStateException] for a key without default and value. */
operator fun <T : Any> SettingsStore.get(key: PrefKey<T>): T =
    getOrNull(key) ?: throw IllegalStateException("${key.name} has neither a stored value nor a default")

/** Writes [value] for [key] (null removes it). */
operator fun <T : Any> SettingsStore.set(key: PrefKey<T>, value: T?) {
    put(key, value, commit = false)
}

/**
 * Writes [value] for [key] (null removes it) as one write and returns the changed keys; `commit = true` is
 * `commit()` like `Pref.putXxxSync`, `false` is `apply()` like `Pref.putXxx`.
 * zh-CN: 把 [key] 的值写为 [value] (null 即删除), 作为一次写入并返回变化的键; `commit = true` 对应 `commit()` (同
 * `Pref.putXxxSync`), `false` 对应 `apply()` (同 `Pref.putXxx`).
 */
fun <T : Any> SettingsStore.put(key: PrefKey<T>, value: T?, commit: Boolean): Set<String> {
    checkStore(key)
    return put(key.name, value?.let(key::encode), commit)
}

/**
 * Puts [value] for [key] within one [SettingsStore.edit] block (null removes it); the key must belong to the store
 * being edited.
 * zh-CN: 在一个 [SettingsStore.edit] 块中写入 [key] 的值 (null 即删除); 键须属于正在编辑的存储.
 */
operator fun <T : Any> SettingsStore.Editor.set(key: PrefKey<T>, value: T?) {
    put(key.name, value?.let(key::encode))
}

private fun SettingsStore.checkStore(key: PrefKey<*>) {
    require(key.store == id) { "${key.name} belongs to ${key.store}, not $id" }
}
