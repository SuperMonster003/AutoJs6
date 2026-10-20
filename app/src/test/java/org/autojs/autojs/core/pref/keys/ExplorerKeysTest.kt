package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.DeploymentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExplorerKeys] (docs/settings/ROADMAP.md P2.2): the readings that `WorkingDirectoryUtils`, the explorer views and
 * menus, `ScriptOperations`, the batch rename history and the deprecated `Pref.isHiddenFilesShown` / file extension
 * getters and `BatchRenameHistoryPrefs` forward to.
 *
 * zh-CN: [ExplorerKeys] (ROADMAP P2.2): `WorkingDirectoryUtils`, 文件管理器视图与菜单, `ScriptOperations`, 批量重命名历史, 以及
 * 已弃用的 `Pref.isHiddenFilesShown` / 文件扩展名读取与 `BatchRenameHistoryPrefs` 所转发的读法.
 */
class ExplorerKeysTest : DomainKeysTestBase() {

    @Test
    fun workingDirectoryDefaultsToTheResourceAndDeploysADirectory() {
        assertEquals(6, ExplorerKeys.keys.size)
        assertEquals("/Scripts", ExplorerKeys.WORKING_DIRECTORY.get())
        assertEquals(DeploymentKind.CREATE_DIRECTORY, ExplorerKeys.WORKING_DIRECTORY.deployment)
        assertFalse(ExplorerKeys.WORKING_DIRECTORY.isStored())
        ExplorerKeys.WORKING_DIRECTORY.set("/Scripts/sub")
        assertEquals("/Scripts/sub", store.getString(key("key_working_directory"), null))
    }

    @Test
    fun hiddenFilesAreShownOnlyForTheShowKey() {
        assertFalse(ExplorerKeys.isHiddenFilesShown)
        ExplorerKeys.HIDDEN_FILES.set(key("key_hidden_files_show"))
        assertTrue(ExplorerKeys.isHiddenFilesShown)
        ExplorerKeys.HIDDEN_FILES.set(key("key_hidden_files_not_show"))
        assertFalse(ExplorerKeys.isHiddenFilesShown)
        store.put(ExplorerKeys.HIDDEN_FILES.name, "key_\$_hidden_files_removed")
        assertFalse(ExplorerKeys.isHiddenFilesShown)
    }

    @Test
    fun fileExtensionsMatchExactlyOneChoice() {
        // The default shows every extension (R.string.default_key_file_extensions).
        assertEquals(listOf(true, false, false), extensions())
        ExplorerKeys.FILE_EXTENSIONS.set(key("key_file_extensions_show_all_but_executable"))
        assertEquals(listOf(false, true, false), extensions())
        ExplorerKeys.FILE_EXTENSIONS.set(key("key_file_extensions_not_show"))
        assertEquals(listOf(false, false, true), extensions())
        // A key out of the candidates matches none, as the old string comparisons did (R2).
        store.put(ExplorerKeys.FILE_EXTENSIONS.name, "key_\$_file_extensions_removed")
        assertEquals(listOf(false, false, false), extensions())
        assertEquals("key_\$_file_extensions_removed", ExplorerKeys.FILE_EXTENSIONS.storedKeyOrDefault())
    }

    @Test
    fun playbackHistoryAndDefaultPrefix() {
        assertFalse(ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.get())
        ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.set(true)
        assertTrue(ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.get())
        assertEquals(0, ExplorerKeys.EXPLORER_FILE_DEFAULT_PREFIX.get())
        ExplorerKeys.EXPLORER_FILE_DEFAULT_PREFIX.set(2)
        assertEquals(2, store.getInt(key("key_explorer_file_default_prefix"), 0))
    }

    @Test
    fun batchRenameRetentionFallsBackToTheDefault() {
        assertEquals(20, ExplorerKeys.batchRenameHistoryRetention())
        ExplorerKeys.setBatchRenameHistoryRetention(50)
        assertEquals(50, ExplorerKeys.batchRenameHistoryRetention())
        store.put(ExplorerKeys.BATCH_RENAME_HISTORY_RETENTION.name, 33)
        assertEquals(20, ExplorerKeys.batchRenameHistoryRetention())
        assertThrows(IllegalArgumentException::class.java) { ExplorerKeys.setBatchRenameHistoryRetention(33) }
    }

    private fun extensions() = listOf(
        ExplorerKeys.isFileExtensionsShownForAll,
        ExplorerKeys.isFileExtensionsShownForAllButExecutable,
        ExplorerKeys.isFileExtensionsHidden,
    )
}
