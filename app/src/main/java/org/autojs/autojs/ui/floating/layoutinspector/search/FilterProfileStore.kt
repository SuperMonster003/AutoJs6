package org.autojs.autojs.ui.floating.layoutinspector.search

import org.autojs.autojs.core.accessibility.inspector.FilterProfile
import org.autojs.autojs.core.accessibility.inspector.FilterProfileCatalog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/**
 * Persists the inspector [FilterProfileCatalog] (roadmap P3.3, D5): the user profiles as JSON and
 * the selected profile id, in two preferences. Loading tolerates absent or damaged values.
 * zh-CN: 持久化布局分析的 [FilterProfileCatalog] (Roadmap P3.3, D5): 用户配置的 JSON 与已选配置 id, 分两个偏好项. 读取时容忍缺失或
 * 损坏的值.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
object FilterProfileStore {

    fun load(): FilterProfileCatalog = FilterProfileCatalog.fromJson(
        Pref.getStringOrNull(R.string.key_layout_inspector_filter_profiles),
        Pref.getStringOrNull(R.string.key_layout_inspector_filter_profile_selected),
    )

    fun save(catalog: FilterProfileCatalog) {
        Pref.putString(R.string.key_layout_inspector_filter_profiles, if (catalog.profiles.size <= 1) null else catalog.toJson())
        saveSelection(catalog)
    }

    fun saveSelection(catalog: FilterProfileCatalog) {
        Pref.putString(R.string.key_layout_inspector_filter_profile_selected, catalog.selectedId.takeIf { it != FilterProfile.DEFAULT_ID })
    }

}
