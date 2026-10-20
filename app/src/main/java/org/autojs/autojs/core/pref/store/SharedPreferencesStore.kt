package org.autojs.autojs.core.pref.store

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * A [SettingsStore] over one SharedPreferences file (docs/settings/ROADMAP.md T3): the default preferences
 * ([StoreId.DEFAULT], `PreferenceManager.getDefaultSharedPreferences`) or a named file (`getSharedPreferences(name,
 * MODE_PRIVATE)`), so file names do not change (R1) and the old readers of the same file keep working.
 *
 * Reads go to the typed getters of [preferences] and [edit] is one `SharedPreferences.Editor` with `apply()` or
 * `commit()`, so values, types, exceptions and the time a write reaches the disk stay those of the direct calls (R2).
 * Before writing, [edit] compares the pending values with the current ones to return the changed keys; that only
 * reads values already in memory. Listeners are registered with `registerOnSharedPreferenceChangeListener` (the
 * store keeps them strongly referenced, since SharedPreferences only keeps weak references); SharedPreferences calls
 * them on the main thread, and with a null key after `clear()` only on Android 11 and later.
 *
 * zh-CN: 一份 SharedPreferences 文件上的 [SettingsStore] (ROADMAP T3): 默认偏好或命名文件, 文件名不变 (R1), 同一文件的旧读法
 * 照常工作. 读取走 [preferences] 的类型化读取, [edit] 为一次 `SharedPreferences.Editor` 的 `apply()` 或 `commit()`, 值, 类型,
 * 异常与写入磁盘的时机都与直接调用相同 (R2). 写入前 [edit] 比较待写值与当前值以返回变化的键, 只读取已在内存中的值. 监听器经
 * `registerOnSharedPreferenceChangeListener` 注册 (存储持有强引用, 因为 SharedPreferences 只持有弱引用); 回调在主线程,
 * `clear()` 之后的 null 键只在 Android 11 及以上出现.
 */
class SharedPreferencesStore(
    override val id: StoreId,
    val preferences: SharedPreferences,
) : SettingsStore {

    init {
        require(id.backend == StoreId.Backend.SHARED_PREFERENCES) { "$id is not a SharedPreferences store" }
    }

    private val registrations = ConcurrentHashMap<AutoCloseable, OnSharedPreferenceChangeListener>()

    /** The stored value of [name]; this copies the whole map (`getAll()`), so typed reads prefer the typed getters. */
    override fun get(name: String): Any? = preferences.all[name]

    override fun contains(name: String): Boolean = preferences.contains(name)

    @Suppress("UNCHECKED_CAST")
    override fun all(): Map<String, Any> = preferences.all.filterValues { it != null } as Map<String, Any>

    override fun getBoolean(name: String, defValue: Boolean): Boolean = preferences.getBoolean(name, defValue)

    override fun getInt(name: String, defValue: Int): Int = preferences.getInt(name, defValue)

    override fun getLong(name: String, defValue: Long): Long = preferences.getLong(name, defValue)

    override fun getFloat(name: String, defValue: Float): Float = preferences.getFloat(name, defValue)

    override fun getString(name: String, defValue: String?): String? = preferences.getString(name, defValue)

    override fun getStringSet(name: String, defValue: Set<String>?): Set<String>? = preferences.getStringSet(name, defValue)

    override fun edit(commit: Boolean, block: SettingsStore.Editor.() -> Unit): Set<String> {
        val pending = PendingEditor(id).apply(block)
        val changed = changedKeys(pending)
        preferences.edit(commit) {
            if (pending.clearRequested) clear()
            pending.values.forEach { (name, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    null -> remove(name)
                    is Boolean -> putBoolean(name, value)
                    is Int -> putInt(name, value)
                    is Long -> putLong(name, value)
                    is Float -> putFloat(name, value)
                    is String -> putString(name, value)
                    is Set<*> -> putStringSet(name, value as Set<String>)
                }
            }
        }
        return changed
    }

    override fun listen(listener: (key: String?) -> Unit): AutoCloseable {
        val wrapper = OnSharedPreferenceChangeListener { _, key -> listener(key) }
        lateinit var registration: AutoCloseable
        registration = AutoCloseable {
            registrations.remove(registration)?.let(preferences::unregisterOnSharedPreferenceChangeListener)
        }
        registrations[registration] = wrapper
        preferences.registerOnSharedPreferenceChangeListener(wrapper)
        return registration
    }

    private fun changedKeys(pending: PendingEditor): Set<String> {
        val changed = LinkedHashSet<String>()
        if (pending.clearRequested) {
            changed.addAll(preferences.all.keys)
            pending.values.forEach { (name, value) -> if (value != null) changed.add(name) }
            return changed
        }
        pending.values.forEach { (name, value) ->
            if (!preferences.contains(name)) {
                if (value != null) changed.add(name)
            } else if (value == null || currentValueOrMismatch(name, value) != value) {
                changed.add(name)
            }
        }
        return changed
    }

    private fun currentValueOrMismatch(name: String, sample: Any): Any? = try {
        when (sample) {
            is Boolean -> preferences.getBoolean(name, false)
            is Int -> preferences.getInt(name, 0)
            is Long -> preferences.getLong(name, 0L)
            is Float -> preferences.getFloat(name, 0f)
            is String -> preferences.getString(name, null)
            is Set<*> -> preferences.getStringSet(name, null)
            else -> MISMATCH
        }
    } catch (_: ClassCastException) {
        MISMATCH
    }

    private class PendingEditor(private val id: StoreId) : SettingsStore.Editor {

        val values = LinkedHashMap<String, Any?>()
        var clearRequested = false
            private set

        override fun put(name: String, value: Any?) {
            values[name] = value?.let { SettingsStore.checkedValue(id, name, it) }
        }

        override fun remove(name: String) {
            values[name] = null
        }

        override fun clear() {
            clearRequested = true
        }
    }

    companion object {

        private val MISMATCH = Any()

        /**
         * Opens the store of [id]: the default preferences for [StoreId.DEFAULT], else `getSharedPreferences(id.name,
         * MODE_PRIVATE)`.
         * zh-CN: 打开 [id] 对应的存储: [StoreId.DEFAULT] 为默认偏好, 否则为 `getSharedPreferences(id.name, MODE_PRIVATE)`.
         */
        @JvmStatic
        fun open(context: Context, id: StoreId): SharedPreferencesStore = SharedPreferencesStore(
            id,
            when {
                id.isDefault -> PreferenceManager.getDefaultSharedPreferences(context)
                else -> context.getSharedPreferences(id.name, Context.MODE_PRIVATE)
            },
        )
    }
}
