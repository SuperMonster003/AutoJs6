package org.autojs.plugin.explorer.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Freezes the transaction order of the plugin and host session interfaces: Binder transaction codes follow the
 * declaration order, so methods of a new protocol version may only be appended (v25 appended the five rename calls
 * after `openRelatedFile`). Plugins call these codes from their own builds of older AARs.
 *
 * zh-CN: 冻结插件接口与宿主会话接口的事务顺序: Binder 事务号按声明顺序分配, 新协议版本的方法只能追加 (v25 在 `openRelatedFile`
 * 之后追加了五个重命名调用). 插件会以其构建时的旧版 AAR 调用这些事务号.
 */
class ExplorerActionAidlOrderTest {

    @Test
    fun hostSessionTransactionsKeepTheirOrder() {
        assertEquals(
            listOf(
                "Bundle listChildren(String targetId, String relativePath, int offset, int limit)",
                "ParcelFileDescriptor openFile(String targetId, String relativePath)",
                "Bundle prepareOutput(String displayName, String mimeType, int conflictPolicy)",
                "ParcelFileDescriptor openOutput(String transactionId)",
                "Bundle commitOutput(String transactionId)",
                "void abortOutput(String transactionId)",
                "void close()",
                "ParcelFileDescriptor openPendingOutput(String transactionId)",
                "Bundle prepareTargetReplacement(String targetId)",
                "Bundle prepareOutputTree(String displayName, int conflictPolicy)",
                "void createOutputDirectory(String transactionId, String relativePath)",
                "ParcelFileDescriptor openOutputFile(String transactionId, String relativePath)",
                "Bundle queryOutput(String transactionId)",
                "Bundle listOutputs()",
                "void attachClient(IBinder clientToken)",
                "Bundle prepareOutputBatch(in List<String> transactionIds)",
                "Bundle commitOutputBatch(String batchId)",
                "void abortOutputBatch(String batchId)",
                "Bundle queryOutputBatch(String batchId)",
                "Bundle listOutputBatches()",
                "Bundle moveTargetsToTrash(in List<String> targetIds, in List<String> outputTransactionIds)",
                "Bundle queryTargetTrash()",
                "Bundle getPlaybackProgress(String targetId, String relativePath)",
                "void reportPlaybackProgress( String targetId, String relativePath, long positionMillis, long durationMillis, int reportState )",
                "Bundle queryTargetReplacement(String targetId)",
                "Bundle undoTargetReplacement(String targetId, String replacementHistoryId)",
                "Bundle queryTargetTrashBatch(String targetTrashBatchId)",
                "Bundle listTargetTrashBatches()",
                "Bundle undoTargetTrashBatch(String targetTrashBatchId)",
                "ParcelFileDescriptor openRelatedFile(String targetId, String suffix)",
                // v25
                "Bundle listRenameParent(int offset, int limit)",
                "Bundle renameEntries(in Bundle request)",
                "Bundle queryRenameBatch(String renameBatchId)",
                "Bundle listRenameBatches()",
                "Bundle undoRenameBatch(String renameBatchId)",
            ),
            methods("IExplorerActionHostSession.aidl"),
        )
    }

    @Test
    fun pluginTransactionsKeepTheirOrder() {
        assertEquals(
            listOf(
                "org.autojs.plugin.common.api.PluginInfo getInfo()",
                "Bundle getActionCatalog()",
                "IExplorerArchiveSession openArchive(in ParcelFileDescriptor source, in Bundle request)",
                "Bundle openArchiveV11(in ParcelFileDescriptor source, in Bundle request)",
                "Bundle getHostFileInfo(in ParcelFileDescriptor source, in Bundle request)",
            ),
            methods("IExplorerActionPlugin.aidl"),
        )
    }

    @Test
    fun protocolVersionMatchesTheLastAppendedTransactions() {
        assertEquals(25, ExplorerActionProtocol.VERSION)
        assertEquals(ExplorerActionProtocol.VERSION, ExplorerActionProtocol.MAX_SUPPORTED_VERSION)
        assertEquals(4, ExplorerActionValues.PLACEMENT_RENAME_DIALOG)
        assertEquals("targetRename", ExplorerActionCatalogKeys.TARGET_RENAME)
        assertEquals(2_000, ExplorerActionProtocol.MAX_RENAME_ENTRIES_PER_REQUEST)
        assertEquals(20, ExplorerActionProtocol.MAX_RENAME_BATCH_RESULTS)
        assertEquals(255, ExplorerActionProtocol.MAX_RENAME_NAME_BYTES)
    }

    private fun methods(fileName: String): List<String> {
        val source = locateAidlDirectory().resolve(fileName).toFile().readText(StandardCharsets.UTF_8)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
        return source.substringAfter("{").substringBeforeLast("}")
            .split(';')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun locateAidlDirectory(): Path {
        val packagePath = Paths.get("org", "autojs", "plugin", "explorer", "api")
        val candidates = listOf(
            Paths.get("src", "main", "aidl").resolve(packagePath),
            Paths.get("plugin-api", "explorer-action-api", "src", "main", "aidl").resolve(packagePath),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        return checkNotNull(candidates.firstOrNull(Files::isDirectory)) { "AIDL directory was not found in $candidates" }
    }
}
