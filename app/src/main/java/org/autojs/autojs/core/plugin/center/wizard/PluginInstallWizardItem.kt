package org.autojs.autojs.core.plugin.center.wizard

import android.graphics.drawable.Drawable

/**
 * One plugin row of the wizard, merged from the built-in catalog, the official
 * plugin index and the installed package state.
 *
 * zh-CN: 向导中的一个插件行, 由内置目录, 官方插件索引及已安装状态合并而成.
 */
data class PluginInstallWizardItem(
    val packageName: String,
    val title: String,
    val category: PluginInstallWizardCategory,
    val inStandard: Boolean,
    val inMinimal: Boolean,
    val isInstalled: Boolean,
    val apkUrl: String?,
    val apkSha256: String?,
    val apkSizeBytes: Long?,
    val iconUrl: String?,
    val nightIconUrl: String?,
    /** The ABI set of the selected release does not intersect the device ABIs. */
    val isAbiMismatch: Boolean = false,
    /** Native libraries are aligned below the page size of a 16 KB device. */
    val isNativeAlignmentMismatch: Boolean = false,
    /** Icon of the installed package, prepared off the UI thread; null when not installed. */
    val installedIcon: Drawable? = null,
    val iconBackgroundColor: String? = null,
    val nightIconBackgroundColor: String? = null,
) {

    val hasInstallableApk: Boolean
        get() = !apkUrl.isNullOrBlank()

    val isSelectable: Boolean
        get() = PluginInstallWizardSelectionPolicy.isSelectable(isInstalled, hasInstallableApk)

    val isPossiblyIncompatible: Boolean
        get() = isAbiMismatch || isNativeAlignmentMismatch

    fun toCandidate() = PluginInstallWizardSelectionPolicy.Candidate(
        packageName = packageName,
        inStandard = inStandard,
        inMinimal = inMinimal,
        selectable = isSelectable,
    )

}

sealed class PluginInstallWizardRow {

    data class Header(
        val category: PluginInstallWizardCategory,
        val packageNames: List<String>,
    ) : PluginInstallWizardRow()

    data class Plugin(val item: PluginInstallWizardItem) : PluginInstallWizardRow()

    companion object {

        /** Groups [items] by category in catalog order, dropping empty categories. */
        fun build(items: List<PluginInstallWizardItem>): List<PluginInstallWizardRow> {
            val rows = ArrayList<PluginInstallWizardRow>(items.size + PluginInstallWizardCategory.entries.size)
            PluginInstallWizardCategory.entries.forEach { category ->
                val members = items.filter { it.category == category }
                if (members.isEmpty()) return@forEach
                rows += Header(category, members.map { it.packageName })
                members.forEach { rows += Plugin(it) }
            }
            return rows
        }

    }

}
