package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three Batch Rename catalog shapes (plugin roadmap D23) against the existing Explorer entry points (D10): the
 * selection toolbar and the folder menu need no host code beyond protocol v25, and the single-item shape only reaches
 * the rename dialog. Matching and placement run exactly as `ExplorerActionRegistry.actionsFor` combines them.
 *
 * zh-CN: Batch Rename 的三种 catalog 形态 (插件路线图 D23) 对照既有文件管理器入口 (D10): 多选工具栏与文件夹菜单在协议 v25 之外
 * 不需要宿主代码, 单项形态只出现在重命名对话框中. 匹配与放置位置的组合方式与 `ExplorerActionRegistry.actionsFor` 完全一致.
 */
class ExplorerBatchRenameEntryPointsTest {

    private val selection = shape(
        "batch-rename-selection",
        ExplorerActionValues.TARGET_MIXED,
        ExplorerActionValues.CARDINALITY_MULTIPLE,
        ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
    )
    private val directory = shape(
        "batch-rename-directory",
        ExplorerActionValues.TARGET_DIRECTORY,
        ExplorerActionValues.CARDINALITY_SINGLE,
        ExplorerActionValues.PLACEMENT_OVERFLOW,
    )
    private val single = shape(
        "batch-rename-single",
        ExplorerActionValues.TARGET_MIXED,
        ExplorerActionValues.CARDINALITY_SINGLE,
        ExplorerActionValues.PLACEMENT_RENAME_DIALOG,
    )
    private val catalog = listOf(selection, directory, single)

    private val text = ExplorerActionTarget("text/plain", "txt", ExplorerActionValues.TARGET_FILE, "notes.txt")
    private val image = ExplorerActionTarget("image/jpeg", "jpg", ExplorerActionValues.TARGET_FILE, "photo.jpg")
    private val folder = ExplorerActionTarget("inode/directory", "", ExplorerActionValues.TARGET_DIRECTORY, "photos")

    @Test
    fun theSelectionToolbarOffersOnlyTheSelectionShapeForFilesFoldersAndMixedSelections() {
        val toolbar = ExplorerActionEntryPoint.SELECTION_TOOLBAR
        assertEquals(setOf(selection.id), listed(toolbar, text, image, folder))
        assertEquals(setOf(selection.id), listed(toolbar, text, image))
        assertEquals(setOf(selection.id), listed(toolbar, folder))
    }

    @Test
    fun theFolderMenuOffersTheDirectoryShapeAndTheFileMenuOffersNothing() {
        val menu = ExplorerActionEntryPoint.OVERFLOW_MENU
        assertEquals(setOf(directory.id), listed(menu, folder))
        // Roadmap D10: a single file gets its entry in the rename dialog, not in its overflow menu.
        assertEquals(emptySet<String>(), listed(menu, text))
    }

    @Test
    fun theRenameDialogOffersTheSingleShapeForOneFileOrFolder() {
        val dialog = ExplorerActionEntryPoint.RENAME_DIALOG
        assertEquals(setOf(single.id), listed(dialog, text))
        assertEquals(setOf(single.id), listed(dialog, folder))
    }

    @Test
    fun noShapeReachesExplorersWithoutPluginEntryPoints() {
        ExplorerActionEntryPoint.entries.forEach { entryPoint ->
            catalog.forEach { action ->
                assertEquals(false, ExplorerActionPolicy.allows(ExplorerSurface.UNSPECIFIED, entryPoint, action))
            }
        }
    }

    private fun listed(entryPoint: ExplorerActionEntryPoint, vararg targets: ExplorerActionTarget): Set<String> {
        val matching = ExplorerActionMatchingPolicy.matchingActionIds(catalog, targets.toList())
        return catalog
            .filter { it.id in matching && ExplorerActionPolicy.allows(ExplorerSurface.MAIN_FILES, entryPoint, it) }
            .mapTo(linkedSetOf(), ExplorerActionDescriptor::id)
    }

    private fun shape(id: String, targetKind: Int, cardinality: Int, placement: Int): ExplorerActionDescriptor =
        requireNotNull(
            ExplorerActionCatalogPolicy.normalizeDescriptor(
                protocolVersion = 25,
                packageName = "io.github.supermonster003.autojs6.plugin.batch.rename",
                id = id,
                labelResourceName = null,
                labelFallback = id,
                activityClassName = ".BatchRenameActivity",
                priority = 0,
                targetKind = targetKind,
                cardinality = cardinality,
                accessMode = ExplorerActionValues.ACCESS_READ_ONLY,
                placement = placement,
                mimeTypes = listOf("*/*"),
                extensions = emptyList(),
                targetRename = true,
            ),
        ) { "$id must be a valid protocol 25 descriptor" }
}
