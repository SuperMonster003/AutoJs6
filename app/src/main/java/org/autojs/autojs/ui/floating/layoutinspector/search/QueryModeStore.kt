package org.autojs.autojs.ui.floating.layoutinspector.search

import org.autojs.autojs.core.accessibility.inspector.InspectorQuery
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.keys.LayoutInspectorKeys
import org.autojs.autojs6.R

/**
 * Persists the inspector query mode across sessions (roadmap P4.1, D2) and the one-time field /
 * selector mode introduction flags. The keyword mode is stored as an absent value.
 * zh-CN: 跨会话持久化布局分析的查询模式 (Roadmap P4.1, D2) 以及字段模式的一次性介绍标记. 关键词模式存为缺省值.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
object QueryModeStore {

    fun load(): InspectorQuery.Mode {
        val name = LayoutInspectorKeys.LAYOUT_INSPECTOR_QUERY_MODE.storedOrNull() ?: return InspectorQuery.Mode.KEYWORD
        return InspectorQuery.Mode.entries.firstOrNull { it.name == name } ?: InspectorQuery.Mode.KEYWORD
    }

    fun save(mode: InspectorQuery.Mode) {
        LayoutInspectorKeys.LAYOUT_INSPECTOR_QUERY_MODE.set(mode.takeIf { it != InspectorQuery.Mode.KEYWORD }?.name)
    }

    /** Whether the field mode syntax toast has been shown once (roadmap P4.1). zh-CN: 字段模式语法提示是否已展示过一次 (Roadmap P4.1). */
    val isFieldModeIntroduced: Boolean
        get() = Pref.getBoolean(R.string.key_layout_inspector_field_mode_introduced, false)

    fun markFieldModeIntroduced() = Pref.putBoolean(R.string.key_layout_inspector_field_mode_introduced, true)

    /** Whether the selector mode syntax toast has been shown once (roadmap P5.2). zh-CN: 选择器模式语法提示是否已展示过一次 (Roadmap P5.2). */
    val isSelectorModeIntroduced: Boolean
        get() = Pref.getBoolean(R.string.key_layout_inspector_selector_mode_introduced, false)

    fun markSelectorModeIntroduced() = Pref.putBoolean(R.string.key_layout_inspector_selector_mode_introduced, true)

}
