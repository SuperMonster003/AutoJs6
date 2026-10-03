package org.autojs.autojs.core.accessibility.inspector

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken

/**
 * The recently submitted inspector queries (roadmap P2.2, D5): newest first, de-duplicated on
 * mode + trimmed text (a re-submission moves the entry to the front and takes the newer regex
 * switch), capped at [MAX_SIZE], single entries removable, clearable. Blank queries are never
 * recorded. [toJson] / [fromJson] are the persistence format; decoding is tolerant: garbage,
 * unknown modes and missing fields yield an empty history or skip the entry rather than throw.
 *
 * Free of Android types so it is unit-testable on the JVM (D13); the UI owns the preference.
 *
 * zh-CN: 最近提交过的查询 (Roadmap P2.2, D5): 最新在前, 按模式 + 去空白文本去重 (重复提交移到最前并采用较新的正则开关),
 * 上限 [MAX_SIZE], 可逐条删除与清空. 空白查询不记录. [toJson] / [fromJson] 为持久化格式, 解码宽容: 垃圾内容, 未知模式与缺失
 * 字段只会得到空历史或跳过该条, 不抛异常. 不含 Android 类型以便在 JVM 单测 (D13); 偏好项由界面层持有.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class QueryHistory private constructor(private val entries: MutableList<InspectorQuery>) {

    /** Newest first. zh-CN: 最新在前. */
    val items: List<InspectorQuery> get() = entries.toList()

    val size: Int get() = entries.size

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Records a submitted query; returns whether the history changed (a blank query or a
     * re-submission of the newest entry with the same switches does not change it).
     * zh-CN: 记录一次提交的查询; 返回历史是否发生变化 (空白查询, 或与最新一条开关相同的重复提交不改变历史).
     */
    fun record(query: InspectorQuery): Boolean {
        if (query.isBlank) return false
        val normalized = query.copy(text = query.text.trim())
        if (entries.firstOrNull() == normalized) return false
        entries.removeAll { it.sameKeyAs(normalized) }
        entries.add(0, normalized)
        while (entries.size > MAX_SIZE) entries.removeAt(entries.size - 1)
        return true
    }

    /** Removes the entry with the same mode and text; returns whether one was removed. zh-CN: 删除模式与文本相同的条目; 返回是否删除了. */
    fun remove(query: InspectorQuery): Boolean {
        val normalized = query.copy(text = query.text.trim())
        return entries.removeAll { it.sameKeyAs(normalized) }
    }

    fun clear(): Boolean {
        if (entries.isEmpty()) return false
        entries.clear()
        return true
    }

    fun toJson(): String = GSON.toJson(entries.map { Entry(it.mode.name, it.text, it.regex) })

    private fun InspectorQuery.sameKeyAs(other: InspectorQuery) = mode == other.mode && text == other.text

    /** The persisted shape of one entry; nullable so that partial JSON decodes. zh-CN: 单条的持久化形态; 可空以便解码残缺 JSON. */
    private class Entry(val mode: String?, val text: String?, val regex: Boolean?)

    companion object {

        const val MAX_SIZE = 20

        private val GSON = Gson()

        private val ENTRY_LIST = object : TypeToken<List<Entry?>>() {}.type

        fun empty(): QueryHistory = QueryHistory(ArrayList())

        /** Builds a history whose newest entry is the first argument. zh-CN: 构造历史, 第一个参数为最新一条. */
        fun of(vararg queries: InspectorQuery): QueryHistory = empty().also { history ->
            queries.reversed().forEach { history.record(it) }
        }

        /**
         * Decodes [toJson] output; anything unreadable yields an empty history, unreadable entries
         * are skipped, and the result is re-normalized (trimmed, de-duplicated, capped).
         * zh-CN: 解码 [toJson] 的输出; 不可读内容得到空历史, 不可读条目被跳过, 结果重新规范化 (去空白, 去重, 截断).
         */
        fun fromJson(json: String?): QueryHistory {
            if (json.isNullOrBlank()) return empty()
            val entries: List<Entry?> = try {
                GSON.fromJson(json, ENTRY_LIST) ?: return empty()
            } catch (e: JsonParseException) {
                return empty()
            } catch (e: IllegalStateException) {
                return empty()
            }
            val history = empty()
            entries.asReversed().forEach { entry ->
                val mode = entry?.mode?.let { name -> InspectorQuery.Mode.entries.firstOrNull { it.name == name } } ?: return@forEach
                val text = entry.text ?: return@forEach
                history.record(InspectorQuery(mode, text, entry.regex ?: false))
            }
            return history
        }
    }
}
