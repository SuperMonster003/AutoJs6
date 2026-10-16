package org.autojs.autojs.ui.explorer

import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "More" menu of the rename dialog under the registry snapshots of Batch Rename roadmap P1.4: no plugin, an
 * installed plugin, and a disabled plugin (which the registry leaves out, so it reads like no plugin).
 * zh-CN: Batch Rename 路线图 P1.4 三种注册表快照下重命名对话框的 "更多" 菜单: 无插件, 已安装插件, 插件未启用 (注册表不收录未启用的插件,
 * 因此与无插件相同).
 */
class ExplorerRenameDialogMenuTest {

    @Test
    fun withoutAnyRenameDialogActionTheMenuOffersTheBatchRenameGuidance() {
        assertEquals(
            listOf(ExplorerRenameDialogMenuEntry.BatchRenameGuidance, ExplorerRenameDialogMenuEntry.RenameHistory),
            ExplorerRenameDialogMenu.entries(ExplorerSurface.MAIN_FILES, emptyList<String>()),
        )
    }

    @Test
    fun installedActionsReplaceTheGuidanceInRegistryOrder() {
        assertEquals(
            listOf(
                ExplorerRenameDialogMenuEntry.PluginAction("batch-rename-single"),
                ExplorerRenameDialogMenuEntry.PluginAction("other-rename"),
                ExplorerRenameDialogMenuEntry.RenameHistory,
            ),
            ExplorerRenameDialogMenu.entries(ExplorerSurface.MAIN_FILES, listOf("batch-rename-single", "other-rename")),
        )
    }

    @Test
    fun theMenuKeepsTheTitleOptionsLimitOfMaterialDialogsWithTheRenameHistoryLast() {
        val actions = (1..12).map { "action-$it" }
        val entries = ExplorerRenameDialogMenu.entries(ExplorerSurface.MAIN_FILES, actions)
        assertEquals(10, ExplorerRenameDialogMenu.MAX_ENTRIES)
        assertEquals(actions.take(9).map { ExplorerRenameDialogMenuEntry.PluginAction(it) } + ExplorerRenameDialogMenuEntry.RenameHistory, entries)
    }

    @Test
    fun explorersWithoutPluginEntryPointsKeepThePlainDialog() {
        assertEquals(emptyList<ExplorerRenameDialogMenuEntry<String>>(), ExplorerRenameDialogMenu.entries(ExplorerSurface.UNSPECIFIED, emptyList<String>()))
        assertEquals(emptyList<ExplorerRenameDialogMenuEntry<String>>(), ExplorerRenameDialogMenu.entries(ExplorerSurface.UNSPECIFIED, listOf("action")))
    }
}
