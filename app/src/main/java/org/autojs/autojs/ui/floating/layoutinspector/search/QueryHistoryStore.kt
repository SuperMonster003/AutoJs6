package org.autojs.autojs.ui.floating.layoutinspector.search

import org.autojs.autojs.core.accessibility.inspector.QueryHistory
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/**
 * Persists the inspector [QueryHistory] in the app preferences (roadmap P2.2, D5). Loading is
 * tolerant of an absent or damaged value; saving writes the compact JSON of [QueryHistory.toJson].
 * zh-CN: 把布局分析的 [QueryHistory] 持久化到应用偏好 (Roadmap P2.2, D5). 读取时容忍缺失或损坏的值; 保存时写入
 * [QueryHistory.toJson] 的紧凑 JSON.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
object QueryHistoryStore {

    fun load(): QueryHistory = QueryHistory.fromJson(Pref.getStringOrNull(R.string.key_layout_inspector_query_history))

    fun save(history: QueryHistory) {
        Pref.putString(R.string.key_layout_inspector_query_history, if (history.isEmpty) null else history.toJson())
    }

}
