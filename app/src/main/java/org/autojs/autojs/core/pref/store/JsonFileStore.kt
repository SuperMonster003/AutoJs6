package org.autojs.autojs.core.pref.store

import org.autojs.autojs.core.pref.SettingsJson
import java.io.File
import java.io.IOException

/**
 * A [SettingsStore] kept in one JSON file (docs/settings/ROADMAP.md T3, R3): a JSON object of entries, read on first
 * access and written back after every [edit] that changes something.
 *
 * Each entry is `{"t": <type tag>, "v": <value>}` with the tags of T6 (bool / int / long / float / string /
 * stringSet), so a value reads back with its SharedPreferences type (an `Int` stays an `Int`, a `Float` a `Float`).
 * A missing file is an empty store. A file that cannot be parsed is treated as empty and reported by [loadError]; it
 * is replaced by the next write. Writes go to a sibling temporary file that is then renamed over [file], so a crash
 * leaves the old or the new content. `commit` makes no difference: every write is synchronous on the calling thread,
 * and so are the listeners.
 *
 * zh-CN: 存放在一个 JSON 文件中的 [SettingsStore] (ROADMAP T3, R3): 内容为条目对象, 首次访问时读取, 每次有变化的 [edit]
 * 之后写回. 每个条目为 `{"t": 类型标签, "v": 值}` (T6 的标签), 读回时保持 SharedPreferences 类型. 文件不存在即空存储; 无法
 * 解析的文件按空存储处理并记入 [loadError], 下次写入时被替换. 写入先写同目录的临时文件再改名覆盖 [file], 崩溃时保留旧内容或
 * 新内容. `commit` 没有区别: 每次写入都在调用线程同步完成, 监听器也在调用线程回调.
 */
class JsonFileStore @JvmOverloads constructor(
    val file: File,
    override val id: StoreId = StoreId(file.name, StoreId.Backend.JSON_FILE),
) : SettingsStore {

    private val lock = Any()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String?) -> Unit>()
    private var cache: LinkedHashMap<String, Any>? = null

    /** Why the file could not be read the last time it was loaded, or null. */
    @Volatile
    var loadError: String? = null
        private set

    override fun get(name: String): Any? = synchronized(lock) { values()[name]?.let(::copyOf) }

    override fun contains(name: String): Boolean = synchronized(lock) { name in values() }

    override fun all(): Map<String, Any> = synchronized(lock) {
        values().entries.associateTo(LinkedHashMap()) { it.key to copyOf(it.value) }
    }

    override fun edit(commit: Boolean, block: SettingsStore.Editor.() -> Unit): Set<String> {
        val editor = PendingEditor(id).apply(block)
        var cleared = false
        val changed = LinkedHashSet<String>()
        val notified = LinkedHashSet<String>()
        synchronized(lock) {
            val values = values()
            val next = LinkedHashMap(values)
            if (editor.clearRequested && next.isNotEmpty()) {
                changed.addAll(next.keys)
                next.clear()
                cleared = true
            }
            editor.pending.forEach { (key, value) ->
                if (value == null) {
                    if (next.remove(key) != null) notified.add(key)
                } else if (next[key] != value) {
                    next[key] = value
                    notified.add(key)
                }
            }
            changed.addAll(notified)
            if (changed.isEmpty()) return changed
            write(next)
            cache = next
        }
        if (cleared) listeners.forEach { it(null) }
        notified.forEach { key -> listeners.forEach { it(key) } }
        return changed
    }

    override fun listen(listener: (key: String?) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    /** Drops the cached content so that the next access reads [file] again. */
    fun reload() = synchronized(lock) { cache = null }

    private fun values(): LinkedHashMap<String, Any> = cache ?: load().also { cache = it }

    private fun load(): LinkedHashMap<String, Any> {
        loadError = null
        if (!file.isFile) return LinkedHashMap()
        return try {
            decode(SettingsJson.parse(file.readText(Charsets.UTF_8)))
        } catch (e: SettingsJson.SettingsJsonException) {
            loadError = e.message
            LinkedHashMap()
        } catch (e: IOException) {
            loadError = e.message ?: e.javaClass.name
            LinkedHashMap()
        }
    }

    private fun decode(json: Any?): LinkedHashMap<String, Any> {
        val root = json as? Map<*, *> ?: throw SettingsJson.SettingsJsonException("$id: the file is not a JSON object")
        val result = LinkedHashMap<String, Any>()
        root.forEach { (key, entry) ->
            val name = key as String
            val map = entry as? Map<*, *> ?: throw SettingsJson.SettingsJsonException("$id: \"$name\" is not an entry")
            val value = when (val tag = map["t"]) {
                "bool" -> map["v"] as? Boolean
                "int" -> SettingsJson.toInt(map["v"])
                "long" -> SettingsJson.toLong(map["v"])
                "float" -> SettingsJson.toFloat(map["v"])
                "string" -> map["v"] as? String
                "stringSet" -> (map["v"] as? List<*>)?.takeIf { list -> list.all { it is String } }?.mapTo(LinkedHashSet()) { it as String }
                else -> throw SettingsJson.SettingsJsonException("$id: \"$name\" has an unknown type tag $tag")
            } ?: throw SettingsJson.SettingsJsonException("$id: \"$name\" does not hold a ${map["t"]} value")
            result[name] = value
        }
        return result
    }

    private fun write(values: Map<String, Any>) {
        val json = LinkedHashMap<String, Any>()
        values.forEach { (name, value) ->
            json[name] = linkedMapOf("t" to tagOf(value), "v" to if (value is Set<*>) value.toList() else value)
        }
        val text = SettingsJson.stringify(json)
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        // On Android (and other POSIX systems) rename replaces the target atomically; elsewhere delete it first.
        if (!tmp.renameTo(file) && !(file.delete() && tmp.renameTo(file))) {
            tmp.delete()
            throw IOException("$id: cannot replace ${file.path}")
        }
    }

    private class PendingEditor(private val id: StoreId) : SettingsStore.Editor {

        val pending = LinkedHashMap<String, Any?>()
        var clearRequested = false
            private set

        override fun put(name: String, value: Any?) {
            pending[name] = value?.let { SettingsStore.checkedValue(id, name, it) }
        }

        override fun remove(name: String) {
            pending[name] = null
        }

        override fun clear() {
            clearRequested = true
        }
    }

    private companion object {

        fun tagOf(value: Any): String = when (value) {
            is Boolean -> "bool"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            is String -> "string"
            is Set<*> -> "stringSet"
            else -> throw IllegalArgumentException("Unsupported type ${value.javaClass.name}")
        }

        fun copyOf(value: Any): Any = if (value is Set<*>) LinkedHashSet(value) else value
    }
}
