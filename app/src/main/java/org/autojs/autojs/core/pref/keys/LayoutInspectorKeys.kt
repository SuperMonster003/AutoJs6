package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.LAYOUT_INSPECTOR
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Layout inspector (docs/settings/ROADMAP.md P2.5, appendix A.1 `layout_inspector`): the filter profiles and the
 * selected one, the copy mode of the node info dialog, the query mode and the current code generation model. All
 * missing by default; their readers fall back themselves (keyword query, "ask" copy mode, the default profile, no
 * model). The query history, the one-time introductions and the legacy color picker layout are states (P4.2, appendix
 * C Q13); the model files are not exported.
 *
 * zh-CN: 布局分析器 (ROADMAP P2.5, 附录 A.1 `layout_inspector`): 筛选配置档及选中项, 节点信息对话框的复制模式, 查询模式, 当前代码
 * 生成模型. 默认均缺省, 由读取方自行回退 (关键词查询, "询问" 复制模式, 默认配置档, 无模型). 查询历史, 一次性介绍与颜色选择旧布局为
 * 状态 (P4.2, 附录 C Q13); 模型文件不导出.
 */
object LayoutInspectorKeys : SettingsDomain {

    /** User filter profiles as JSON (`FilterProfileCatalog.toJson`); removed while only the default profile exists. */
    @JvmField
    val LAYOUT_INSPECTOR_FILTER_PROFILES = SettingKey.Str(R.string.key_layout_inspector_filter_profiles, LAYOUT_INSPECTOR, SettingDefault.None)

    /** Id of the selected filter profile; removed for the default profile. */
    @JvmField
    val LAYOUT_INSPECTOR_FILTER_PROFILE_SELECTED = SettingKey.Str(R.string.key_layout_inspector_filter_profile_selected, LAYOUT_INSPECTOR, SettingDefault.None)

    /** Name of the `NodeInfoDialog.CopyMode`. */
    @JvmField
    val LAYOUT_INSPECTOR_NODE_COPY_MODE = SettingKey.Str(R.string.key_layout_inspector_node_copy_mode, LAYOUT_INSPECTOR, SettingDefault.None)

    /** Name of the `InspectorQuery.Mode`; removed for the keyword mode. */
    @JvmField
    val LAYOUT_INSPECTOR_QUERY_MODE = SettingKey.Str(R.string.key_layout_inspector_query_mode, LAYOUT_INSPECTOR, SettingDefault.None)

    /**
     * Key of the current code generation model (`ModelStore`); debug catalogs append a profile suffix to the name
     * (`ModelCatalogConfiguration.selectionPreferenceKey`).
     * zh-CN: 当前代码生成模型的键 (`ModelStore`); 调试目录会在名称后追加配置后缀.
     */
    @JvmField
    val CODEGEN_MODEL_CURRENT = SettingKey.Str(R.string.key_codegen_model_current, LAYOUT_INSPECTOR, SettingDefault.None)

    override val keys: List<PrefKey<*>> = listOf(
        LAYOUT_INSPECTOR_FILTER_PROFILES, LAYOUT_INSPECTOR_FILTER_PROFILE_SELECTED, LAYOUT_INSPECTOR_NODE_COPY_MODE,
        LAYOUT_INSPECTOR_QUERY_MODE, CODEGEN_MODEL_CURRENT,
    )
}
