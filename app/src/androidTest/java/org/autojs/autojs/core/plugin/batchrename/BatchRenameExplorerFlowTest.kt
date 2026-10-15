package org.autojs.autojs.core.plugin.batchrename

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerActionLauncher
import org.autojs.autojs.core.plugin.explorer.ExplorerActionRegistry
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * The file manager path end to end (Batch Rename roadmap P4.4): the host's Explorer Action registry offers the
 * installed plugin's three rename actions, the launcher opens the plugin screen with a real v25 session, the test types
 * into the plugin screen through [UiAutomation] (the plugin exposes its test tags as resource IDs), and the files on
 * disk are renamed by the host executor and restored by the plugin's Undo. Skipped without the plugin.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.core.plugin.batchrename.BatchRenameExplorerFlowTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: 文件管理器路径的端到端测试 (Batch Rename 路线图 P4.4): 宿主的 Explorer Action 注册表提供已安装插件的三个改名动作, 启动器以真实
 * v25 会话打开插件界面, 测试经 [UiAutomation] 在插件界面中输入 (插件把测试标签作为资源 ID 暴露), 磁盘上的文件由宿主执行器改名, 再由
 * 插件的撤销恢复. 未安装插件时跳过.
 */
@RunWith(AndroidJUnit4::class)
class BatchRenameExplorerFlowTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val root by lazy { File(context.cacheDir, "batch-rename-flow-${UUID.randomUUID()}").apply { assertTrue(mkdirs()) } }
    private val journal by lazy { File(context.noBackupFilesDir, "batch-rename/journals") }
    private lateinit var journalBefore: Set<String>
    private lateinit var automation: UiAutomation

    @Before
    fun setUp() {
        assumeTrue("No Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
        journalBefore = journal.list().orEmpty().toSet()
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        runBlocking { ExplorerActionRegistry.refresh(context) }
    }

    @After
    fun cleanUp() {
        if (!::journalBefore.isInitialized) return
        runCatching { closePluginScreen() }
        journal.listFiles().orEmpty().filter { it.name !in journalBefore }.forEach(File::delete)
        root.deleteRecursively()
    }

    @Test
    fun theSelectionToolbarRenamesFilesAndFolderContentsAndUndoRestoresThem() {
        val file = File(root, "IMG_1.jpg").apply { writeText("one") }
        val album = File(root, "album").apply { mkdirs() }
        File(album, "IMG_2.jpg").writeText("two")
        launch("batch-rename-selection", ExplorerActionEntryPoint.SELECTION_TOOLBAR, listOf(ExplorerFileItem(file, null), ExplorerDirPage(album, null)))
        renameInPlugin(search = "IMG_", replace = "Photo_", expect = "Photo_1.jpg")
        waitUntil("the host renamed both images") { File(root, "Photo_1.jpg").isFile && File(album, "Photo_2.jpg").isFile }
        undoInPlugin()
        waitUntil("undo restored both images") { file.isFile && File(album, "IMG_2.jpg").isFile }
        assertEquals("two", File(album, "IMG_2.jpg").readText())
    }

    @Test
    fun theFolderMenuRenamesWhatIsInsideIncludingSubfolders() {
        val album = File(root, "album").apply { mkdirs() }
        File(album, "IMG_3.jpg").writeText("three")
        File(album, "sub").mkdirs()
        File(album, "sub/IMG_4.jpg").writeText("four")
        launch("batch-rename-directory", ExplorerActionEntryPoint.OVERFLOW_MENU, listOf(ExplorerDirPage(album, null)))
        renameInPlugin(search = "IMG", replace = "Pic", expect = "Pic_3.jpg")
        waitUntil("the host renamed the folder contents") { File(album, "Pic_3.jpg").isFile && File(album, "sub/Pic_4.jpg").isFile }
        assertTrue("the folder itself keeps its name", album.isDirectory)
        undoInPlugin()
        waitUntil("undo restored the folder contents") { File(album, "IMG_3.jpg").isFile && File(album, "sub/IMG_4.jpg").isFile }
    }

    @Test
    fun theRenameDialogRenamesOneItem() {
        val notes = File(root, "notes.txt").apply { writeText("notes") }
        File(root, "other.txt").writeText("other")
        launch("batch-rename-single", ExplorerActionEntryPoint.RENAME_DIALOG, listOf(ExplorerFileItem(notes, null)))
        renameInPlugin(search = "notes", replace = "memo", expect = "memo.txt")
        waitUntil("the host renamed the item") { File(root, "memo.txt").isFile && !notes.exists() }
        assertTrue(File(root, "other.txt").isFile)
        undoInPlugin()
        waitUntil("undo restored the item") { notes.isFile }
    }

    private fun launch(actionId: String, entryPoint: ExplorerActionEntryPoint, items: List<ExplorerItem>) {
        val action = ExplorerActionRegistry.actionsFor(ExplorerSurface.MAIN_FILES, entryPoint, items)
            .singleOrNull { it.descriptor.id == actionId }
            ?: throw AssertionError("the registry offers no $actionId for $entryPoint")
        assertTrue(action.descriptor.targetRename)
        ExplorerActionLauncher.launch(context, ExplorerSurface.MAIN_FILES, items, action).getOrThrow()
        waitFor(SEARCH)
    }

    /** Types the texts, waits until the preview shows [expect] and taps Rename. */
    private fun renameInPlugin(search: String, replace: String, expect: String) {
        setText(SEARCH, search)
        setText(REPLACE, replace)
        try {
            waitUntil("the preview never showed $expect") { previewShows(expect) }
        } catch (error: AssertionError) {
            throw AssertionError("${error.message}; visible: ${visibleIds()}", error)
        }
        val apply = try {
            waitFor(APPLY) { it.isEnabled }
        } catch (error: AssertionError) {
            throw AssertionError("${error.message}; search=${find(SEARCH)?.text}, replace=${find(REPLACE)?.text}, summary=${find(SUMMARY)?.text}", error)
        }
        assertTrue(apply.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun undoInPlugin() {
        val undo = waitFor(UNDO) { it.isEnabled }
        assertTrue(undo.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun closePluginScreen() {
        if (find(SEARCH) == null) return
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        waitUntil("the plugin screen closed") { find(SEARCH) == null }
    }

    private fun setText(id: String, text: String) {
        val node = waitFor(id)
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        assertTrue("could not type into $id", node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
    }

    private fun waitFor(id: String, predicate: (AccessibilityNodeInfo) -> Boolean = { true }): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        try {
            waitUntil("$id did not appear") { find(id)?.takeIf(predicate)?.also { found = it } != null }
        } catch (error: AssertionError) {
            throw AssertionError("${error.message}; visible: ${visibleIds()}", error)
        }
        return found!!
    }

    private fun previewShows(name: String): Boolean = pluginNodes().any { it.text?.toString() == name }

    /** Resource IDs and texts of the plugin's visible nodes, for failure messages. */
    private fun visibleIds(): String = pluginNodes()
        .filter { it.viewIdResourceName != null || !it.text.isNullOrEmpty() }
        .joinToString { "${it.viewIdResourceName ?: ""}[${it.text ?: ""}${if (it.isEnabled) "" else ", disabled"}]" }

    /** The current node of the plugin window whose resource ID is [id] (a Compose test tag). */
    private fun find(id: String): AccessibilityNodeInfo? = pluginNodes().firstOrNull { it.viewIdResourceName == id }

    /**
     * Every node of the plugin's windows, each refreshed: the accessibility cache misses some Compose changes, and
     * refresh() reads the node from its window again.
     * zh-CN: 插件窗口中的全部节点, 逐个刷新: 无障碍缓存会漏掉部分 Compose 变化, refresh() 会从窗口重新读取节点.
     */
    private fun pluginNodes(): List<AccessibilityNodeInfo> {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val roots = automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)
        roots.filter { it.packageName == PLUGIN_PACKAGE }.distinctBy { it.windowId }.forEach { root ->
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (!node.refresh()) continue
                nodes += node
                (0 until node.childCount).mapNotNullTo(queue) { node.getChild(it) }
            }
        }
        return nodes
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            SystemClock.sleep(200)
        }
        throw AssertionError("Timed out: $message")
    }

    private companion object {
        const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.batch.rename"
        const val SEARCH = "batchRename.search"
        const val REPLACE = "batchRename.replace"
        const val APPLY = "batchRename.apply"
        const val UNDO = "batchRename.undo"
        const val SUMMARY = "batchRename.summary"
        const val ROW_PREFIX = "batchRename.row:"
        const val TIMEOUT_MILLIS = 60_000L
    }
}
