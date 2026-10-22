package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest

/**
 * One row of the category step (docs/settings/ROADMAP.md T10): a category, the number of its entries (null when the
 * step has no count for it) and whether it can be ticked ([state]).
 * zh-CN: 分类步骤的一行 (ROADMAP T10): 分类, 条目数 (该步骤没有计数时为 null), 以及能否勾选 ([state]).
 */
data class SettingsCategoryChoice @JvmOverloads constructor(val category: SettingCategory, val entries: Int?, val state: State = State.SELECTABLE) {

    enum class State {
        SELECTABLE,

        /** A data category, whose detail pages come in P10.2. zh-CN: 数据分类, 其细化页在 P10.2 提供. */
        LATER_VERSION,

        /**
         * Sealed for the key of another device (D23): this device cannot import it; P7.7 asks for the password here.
         * zh-CN: 以其他设备的密钥加密 (D23): 本机无法导入; P7.7 在此处询问口令.
         */
        LOCKED,
    }

    val isSelectable: Boolean
        get() = state == State.SELECTABLE
}

/**
 * The rows of the category step per wizard (T10): the export lists every category with the entries of the current
 * settings (P6.3), the import only the categories the file has, with the entries its manifest counts, and disables the
 * credentials this device cannot decrypt (P7.5, D23); data categories wait for P10.2. Pure logic (R3).
 *
 * zh-CN: 各向导分类步骤的行 (T10): 导出列出全部分类及当前设置的条目数 (P6.3); 导入只列出文件中有的分类及其 manifest 记录的条目数, 本机
 * 无法解密的凭据不可勾选 (P7.5, D23); 数据分类等待 P10.2. 纯逻辑 (R3).
 */
object SettingsCategoryChoices {

    /** The rows of the export wizard: [counts] are the entries per category id. zh-CN: 导出向导的行, [counts] 为各分类的条目数. */
    @JvmStatic
    @JvmOverloads
    fun export(counts: Map<String, Int>, categories: List<SettingCategory> = SettingCategory.ALL): List<SettingsCategoryChoice> = categories.map {
        if (it.isData) SettingsCategoryChoice(it, null, SettingsCategoryChoice.State.LATER_VERSION) else SettingsCategoryChoice(it, counts[it.id] ?: 0)
    }

    /**
     * The rows of the import wizard for [manifest], in page order: the known categories the file holds; [locked] are
     * the ids of the sealed categories this device cannot open (`SettingsArchiveReader.isLocked`).
     * zh-CN: 导入向导对 [manifest] 的行, 按页面顺序: 文件中有的已知分类; [locked] 为本机无法打开的加密分类 id.
     */
    @JvmStatic
    @JvmOverloads
    fun import(
        manifest: SettingsArchiveManifest,
        locked: Collection<String>,
        categories: List<SettingCategory> = SettingCategory.ALL,
    ): List<SettingsCategoryChoice> {
        val entries = manifest.categories.associate { it.id to it.entries } + manifest.data.associate { it.id to it.entries }
        return categories.filter { it.id in entries }.map {
            when {
                it.isData -> SettingsCategoryChoice(it, entries[it.id], SettingsCategoryChoice.State.LATER_VERSION)
                it.id in locked -> SettingsCategoryChoice(it, entries[it.id], SettingsCategoryChoice.State.LOCKED)
                else -> SettingsCategoryChoice(it, entries[it.id])
            }
        }
    }

    /** The categories of [choices] that can be ticked, for `SettingsCategorySelection`. zh-CN: [choices] 中可勾选的分类. */
    @JvmStatic
    fun selectable(choices: List<SettingsCategoryChoice>): List<SettingCategory> = choices.filter { it.isSelectable }.map { it.category }

    /**
     * The selection the category step of the import starts with: the rows that can be ticked and are ticked by default
     * (appendix A.1), so `credentials` and `other` are ticked by the user.
     * zh-CN: 导入的分类步骤的初始勾选: 可勾选且默认勾选 (附录 A.1) 的行, 因此 `credentials` 与 `other` 需由用户勾选.
     */
    @JvmStatic
    fun importSelection(choices: List<SettingsCategoryChoice>): List<String> =
        choices.filter { it.isSelectable && it.category.defaultSelected }.map { it.category.id }
}
