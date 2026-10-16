package org.autojs.autojs.ui.storage

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.autojs.autojs.core.plugin.batchrename.BatchRenameBatchState
import org.autojs.autojs.core.plugin.batchrename.BatchRenameEntryState
import org.autojs.autojs.core.plugin.batchrename.BatchRenameHistoryPrefs
import org.autojs.autojs.core.plugin.batchrename.BatchRenameHostServices
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalEntry
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalRecord
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalStore
import org.autojs.autojs.core.plugin.batchrename.BatchRenameOperation
import org.autojs.autojs.core.plugin.batchrename.BatchRenameSource
import org.autojs.autojs6.R
import org.autojs.plugin.batchrename.api.BatchRenamePluginActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The rename history page (Batch Rename plugin roadmap P6.5, D38 / D39 / D51) on a device: batches made by the real
 * executor are listed, filtered by source and state, searched by old or new name; a batch is undone and deleted from
 * its details; the menu clears the history and lowers the retention, which trims at once. The journal of the device is
 * set aside during each test and put back afterwards. With the instrumentation argument `batchRename.screenshots=true`
 * the list, the details and the retention dialog are saved as PNG files under the app's external files directory
 * (`screenshots/`) for the manual check of the layout.
 *
 * zh-CN: 在设备上验证重命名历史页面 (Batch Rename 插件路线图 P6.5, D38 / D39 / D51): 列出真实执行器产生的批次, 按来源与状态筛选, 按旧名或
 * 新名搜索; 在明细中撤销与删除批次; 菜单清空历史并调低保留批数 (立即裁剪). 每个用例期间设备上原有的日志被移开, 结束后放回. 传入
 * instrumentation 参数 `batchRename.screenshots=true` 时, 列表, 明细与保留批数对话框会以 PNG 保存到应用外部文件目录 (`screenshots/`),
 * 供人工检查布局.
 */
@RunWith(AndroidJUnit4::class)
class BatchRenameHistoryActivityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val journal: BatchRenameJournalStore = BatchRenameHostServices.journal(context)
    private val executor = BatchRenameHostServices.executor(context)
    private val journalDirectory = File(context.noBackupFilesDir, "batch-rename/journals")
    private val backup = File(context.cacheDir, "rename-history-backup-${System.nanoTime()}")
    private val root = File(context.cacheDir, "rename-history-${System.nanoTime()}")
    private var retention = BatchRenameJournalStore.MAX_BATCHES
    private var page: BatchRenameHistoryActivity? = null

    @Before
    fun setUp() {
        retention = BatchRenameHistoryPrefs.retention()
        backup.mkdirs()
        root.mkdirs()
        journalDirectory.listFiles().orEmpty().forEach { file -> check(file.renameTo(File(backup, file.name))) { "could not set ${file.name} aside" } }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { page?.finish() }
        journalDirectory.mkdirs()
        journalDirectory.listFiles().orEmpty().forEach(File::delete)
        backup.listFiles().orEmpty().forEach { file -> file.renameTo(File(journalDirectory, file.name)) }
        backup.deleteRecursively()
        root.deleteRecursively()
        BatchRenameHistoryPrefs.setRetention(retention)
    }

    @Test
    fun batchesAreListedFilteredAndSearched() {
        val script = rename("alpha-old.txt", "alpha-new.txt", BatchRenameSource.SCRIPT)
        val explorer = rename("beta-old.txt", "beta-new.txt", BatchRenameSource.EXPLORER)
        val undone = rename("gamma-old.txt", "gamma-new.txt", BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.RESTORED, executor.undo(undone.batchId)?.state)
        val activity = open()

        awaitMain { rowCount(activity) == 3 }
        awaitText("gamma-old.txt -> gamma-new.txt")
        assertEquals(batches(3), subtitle(activity))
        screenshot("rename-history-list")

        select(activity, R.id.action_rename_history_source_explorer)
        awaitMain { rowCount(activity) == 1 }
        awaitText("beta-old.txt -> beta-new.txt")
        assertEquals("1 / ${batches(3)}", subtitle(activity))

        select(activity, R.id.action_rename_history_source_all)
        select(activity, R.id.action_rename_history_state_restored)
        awaitMain { rowCount(activity) == 1 }
        awaitText("gamma-old.txt -> gamma-new.txt")
        select(activity, R.id.action_rename_history_state_committed)
        awaitMain { rowCount(activity) == 2 }
        select(activity, R.id.action_rename_history_state_all)

        // The search matches old and new names, ignoring case. zh-CN: 搜索匹配旧名与新名, 不区分大小写.
        search(activity, "ALPHA-NEW")
        awaitMain { rowCount(activity) == 1 }
        awaitText("alpha-old.txt -> alpha-new.txt")
        search(activity, "-old")
        awaitMain { rowCount(activity) == 3 }
        assertNotNull(journal.load(script.batchId))
        assertNotNull(journal.load(explorer.batchId))
    }

    @Test
    fun aBatchIsUndoneAndDeletedFromItsDetailsAndTheMenuClearsTheHistory() {
        val script = rename("alpha-old.txt", "alpha-new.txt", BatchRenameSource.SCRIPT)
        val explorer = rename("beta-old.txt", "beta-new.txt", BatchRenameSource.EXPLORER)
        val activity = open()
        awaitMain { rowCount(activity) == 2 }

        clickStableText("alpha-old.txt -> alpha-new.txt")
        awaitTextContaining("alpha-old.txt -> alpha-new.txt  (${context.getString(R.string.text_rename_history_entry_renamed)})")
        screenshot("rename-history-details")
        clickStableText(context.getString(R.string.text_rename_history_undo), ignoreCase = true)
        awaitMain { File(root, "alpha-old.txt").isFile && journal.load(script.batchId)?.state == BatchRenameBatchState.RESTORED }
        assertEquals(BatchRenameEntryState.RESTORED, journal.load(script.batchId)?.entries?.single()?.state)

        clickStableText("beta-old.txt -> beta-new.txt")
        clickStableText(context.getString(R.string.text_delete), ignoreCase = true)
        awaitText(context.getString(R.string.text_rename_history_delete_confirm))
        clickStableText(context.getString(R.string.dialog_button_confirm), ignoreCase = true)
        awaitMain { journal.load(explorer.batchId) == null && rowCount(activity) == 1 }
        // Deleting the history leaves the files alone. zh-CN: 删除历史不影响文件.
        assertTrue(File(root, "beta-new.txt").isFile)

        select(activity, R.id.action_rename_history_clear)
        awaitText(context.getString(R.string.text_rename_history_clear_confirm))
        clickStableText(context.getString(R.string.dialog_button_confirm), ignoreCase = true)
        awaitMain { journal.list().isEmpty() && rowCount(activity) == 0 }
        awaitMain { activity.findViewById<TextView>(R.id.empty_hint).visibility == View.VISIBLE }
    }

    @Test
    fun aLowerRetentionIsSavedAndTrimsAtOnce() {
        BatchRenameHistoryPrefs.setRetention(100)
        val ids = (1..55).map { index ->
            BatchRenameJournalRecord(
                batchId = journal.newBatchId(),
                source = BatchRenameSource.SCRIPT,
                pluginUid = null,
                createdAt = 1_000L + index,
                updatedAt = 1_000L + index,
                state = BatchRenameBatchState.COMMITTED,
                entries = listOf(BatchRenameJournalEntry(File(root, "a$index").path, File(root, "b$index").path, false, BatchRenameEntryState.RENAMED, renamed = true)),
            ).also(journal::save).batchId
        }
        val activity = open()
        awaitMain { rowCount(activity) == 55 }

        select(activity, R.id.action_rename_history_retention)
        awaitText(context.getString(R.string.text_rename_history_retention_hint))
        screenshot("rename-history-retention")
        clickStableText("50")
        clickStableText(context.getString(R.string.dialog_button_confirm), ignoreCase = true)
        awaitMain { BatchRenameHistoryPrefs.retention() == 50 && rowCount(activity) == 50 }
        assertEquals(ids.takeLast(50).reversed(), journal.list().map { it.batchId })
        assertNull(journal.load(ids.first()))
    }

    /**
     * Another app holding the plugin permission reaches the page through its action; the host itself holds it as well.
     * zh-CN: 持有插件权限的其他应用经该动作打开页面; 宿主自身也持有该权限.
     */
    @Test
    fun theActionOfTheContractResolvesToThePage() {
        val intent = Intent(BatchRenamePluginActions.RENAME_HISTORY).setPackage(context.packageName)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertEquals(BatchRenameHistoryActivity::class.java.name, resolved?.activityInfo?.name)
        assertEquals(BatchRenamePluginActions.PLUGIN_PERMISSION, resolved?.activityInfo?.permission)
        assertTrue(resolved?.activityInfo?.exported == true)
    }

    private fun rename(name: String, newName: String, source: BatchRenameSource): BatchRenameJournalRecord {
        val file = File(root, name).apply { writeText(name) }
        val record = executor.execute(
            listOf(BatchRenameOperation(file, newName, false, file.lastModified(), file.length(), false)),
            source,
            pluginUid = Process.myUid().takeIf { source == BatchRenameSource.EXPLORER },
            scope = root.takeIf { source == BatchRenameSource.EXPLORER },
        )
        assertEquals(BatchRenameBatchState.COMMITTED, record.state)
        return record
    }

    private fun open(): BatchRenameHistoryActivity {
        context.startActivity(Intent(context, BatchRenameHistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitMain {
            page = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<BatchRenameHistoryActivity>().firstOrNull()
            page != null
        }
        return checkNotNull(page)
    }

    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("batchRename.screenshots") != "true") return
        SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun batches(count: Int) = context.resources.getQuantityString(R.plurals.text_rename_history_batches, count, count)

    private fun rowCount(activity: BatchRenameHistoryActivity) =
        activity.findViewById<RecyclerView>(R.id.recycler_view).adapter?.itemCount ?: -1

    private fun subtitle(activity: BatchRenameHistoryActivity): String {
        var text = ""
        instrumentation.runOnMainSync { text = activity.findViewById<Toolbar>(R.id.toolbar).subtitle?.toString().orEmpty() }
        return text
    }

    /** Chooses a menu item as the overflow menu would. zh-CN: 像溢出菜单一样选择菜单项. */
    private fun select(activity: BatchRenameHistoryActivity, id: Int) {
        instrumentation.runOnMainSync {
            val item = checkNotNull(activity.findViewById<Toolbar>(R.id.toolbar).menu.findItem(id)) { "no menu item $id" }
            assertTrue(activity.onOptionsItemSelected(item))
        }
    }

    private fun search(activity: BatchRenameHistoryActivity, query: String) {
        instrumentation.runOnMainSync {
            val item = activity.findViewById<Toolbar>(R.id.toolbar).menu.findItem(R.id.action_search)
            item.expandActionView()
            (item.actionView as SearchView).setQuery(query, false)
        }
    }

    private fun awaitText(text: String): AccessibilityNodeInfo = awaitNode(text) { it.text?.toString() == text }

    private fun awaitTextContaining(text: String): AccessibilityNodeInfo = awaitNode(text) { it.text?.toString()?.contains(text) == true }

    private fun awaitNode(text: String, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull(matches)
                ?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Text did not appear: $text")
    }

    /** [ignoreCase] serves dialog buttons, which show their text in capitals. zh-CN: [ignoreCase] 用于以大写显示文字的对话框按钮. */
    private fun clickStableText(text: String, ignoreCase: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var previousBounds: Rect? = null
        var stableSince = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val now = SystemClock.elapsedRealtime()
            val node = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString().equals(text, ignoreCase) && it.isVisibleToUser && it.isEnabled }
            val bounds = node?.let { Rect().also(it::getBoundsInScreen) }
            if (node != null && bounds != null && !bounds.isEmpty) {
                if (bounds == previousBounds) {
                    // Dialog rows exist before their entrance animation settles. zh-CN: 对话框各行在入场动画结束前就已存在.
                    if (now - stableSince >= 250L) {
                        var target: AccessibilityNodeInfo = node
                        while (!target.isClickable || target.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                            target = target.parent ?: throw AssertionError("No accessible click action for: $text")
                        }
                        assertTrue("The click was rejected: $text", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        return
                    }
                } else {
                    previousBounds = bounds
                    stableSince = now
                }
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Text did not settle: $text")
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Condition was not met in time")
    }
}
