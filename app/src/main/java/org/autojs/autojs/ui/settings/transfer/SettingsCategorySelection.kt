package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingCategory

/**
 * The selection rules of the category step (docs/settings/ROADMAP.md T10, D2 / D6, appendix C Q28). Selections are
 * category ids in page order (appendix A.1) and only hold selectable categories: the data categories wait for their
 * codecs and detail pages (P10). Pure logic (R3).
 *
 * zh-CN: 分类步骤的勾选规则 (ROADMAP T10, D2 / D6, 附录 C Q28). 勾选为按页面顺序 (附录 A.1) 排列的分类 id, 只含可选分类: 数据分类
 * 等待其编解码与细化页 (P10). 纯逻辑 (R3).
 */
object SettingsCategorySelection {

    /** The categories that can be ticked now. zh-CN: 当前可勾选的分类. */
    @JvmStatic
    @JvmOverloads
    fun selectable(categories: List<SettingCategory> = SettingCategory.ALL): List<SettingCategory> = categories.filterNot { it.isData }

    /** [ids] in page order, without unknown or unselectable ones and duplicates. zh-CN: 按页面顺序整理, 去掉未知, 不可选与重复的 id. */
    @JvmStatic
    @JvmOverloads
    fun normalize(ids: Collection<String>, categories: List<SettingCategory> = SettingCategory.ALL): List<String> =
        selectable(categories).map { it.id }.filter { it in ids }

    /**
     * The selection the export wizard starts with: the [remembered] one (P6.3) without `credentials`, which is never
     * ticked without its warning (D2, Q28), or the default selection of appendix A.1 when nothing is remembered.
     * zh-CN: 导出向导的初始勾选: 记住的勾选 (P6.3) 去掉 `credentials` (不经警告不勾选, D2, Q28); 没有记住的勾选时为附录 A.1 的默认勾选.
     */
    @JvmStatic
    @JvmOverloads
    fun initial(remembered: List<String>?, categories: List<SettingCategory> = SettingCategory.ALL): List<String> =
        if (remembered == null) selectable(categories).filter { it.defaultSelected }.map { it.id }
        else toRemember(normalize(remembered, categories))

    @JvmStatic
    @JvmOverloads
    fun all(categories: List<SettingCategory> = SettingCategory.ALL): List<String> = selectable(categories).map { it.id }

    @JvmStatic
    @JvmOverloads
    fun inverted(current: Collection<String>, categories: List<SettingCategory> = SettingCategory.ALL): List<String> =
        selectable(categories).map { it.id }.filterNot { it in current }

    /** [id] added to or removed from [current]. zh-CN: 在 [current] 中加入或去掉 [id]. */
    @JvmStatic
    @JvmOverloads
    fun toggled(current: Collection<String>, id: String, categories: List<SettingCategory> = SettingCategory.ALL): List<String> =
        normalize(if (id in current) current - id else current + id, categories)

    /** What is remembered after "Next": the selection without `credentials` (Q28). zh-CN: 点击 "下一步" 后记住的勾选: 不含 `credentials` (Q28). */
    @JvmStatic
    fun toRemember(selected: List<String>): List<String> = selected - SettingCategory.CREDENTIALS.id

    /** True when [next] ticks `credentials` and [current] does not (D2). zh-CN: [next] 勾选了 `credentials` 而 [current] 没有时为 true (D2). */
    @JvmStatic
    fun addsCredentials(current: Collection<String>, next: Collection<String>): Boolean =
        SettingCategory.CREDENTIALS.id in next && SettingCategory.CREDENTIALS.id !in current
}
