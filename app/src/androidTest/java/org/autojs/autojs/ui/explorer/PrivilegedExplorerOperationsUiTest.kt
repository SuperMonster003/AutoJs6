package org.autojs.autojs.ui.explorer

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedExplorerItem
import org.autojs.autojs.model.explorer.PrivilegedFileOperationException
import org.autojs.autojs.model.explorer.PrivilegedFileOperations
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Real row listeners, popup menus, dialogs, and selection state; never changes authorization. */
@RunWith(AndroidJUnit4::class)
class PrivilegedExplorerOperationsUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var activity: ExplorerPathMenuProbeActivity
    private lateinit var explorer: ExplorerView
    private lateinit var directory: File
    private var previousAccessibilityFlags: Int? = null

    @Before
    fun setUp() {
        assumeTrue("Shizuku is not already running and authorized", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        checkNotNull(WrappedShizuku.getServiceOrNull())
        directory = createReadableFixture()
        instrumentation.uiAutomation.serviceInfo.let { info ->
            previousAccessibilityFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            instrumentation.uiAutomation.serviceInfo = info
        }
        activity = instrumentation.startActivitySync(Intent(context, ExplorerPathMenuProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ExplorerPathMenuProbeActivity
    }

    @After
    fun tearDown() {
        if (::activity.isInitialized) onMain { activity.finish() }
        previousAccessibilityFlags?.let { flags ->
            instrumentation.uiAutomation.serviceInfo.let { info ->
                info.flags = flags
                instrumentation.uiAutomation.serviceInfo = info
            }
        }
        if (::directory.isInitialized) {
            check(directory.name.matches(Regex("privileged-explorer-ui-[a-f0-9-]{36}")))
            check(directory.canonicalFile.parentFile in fixtureParents().map(File::getCanonicalFile))
            check(directory.deleteRecursively())
        }
    }

    @Test
    fun realRowMenuRenamesTheFileAndRefreshesItsItemIdentity() {
        val source = File(directory, "before.txt").apply { writeText("rename through the row") }
        val renamed = File(directory, "renamed ' 中文.txt")
        installExplorer()
        val originalItem = onMain { items().single() }
        openRowMenu(source, R.string.text_rename)
        withNode({ it.isEditable }) { input ->
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, renamed.name) }
            assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        }
        clickDialogButton("md_buttonDefaultPositive")
        awaitMain("Rename should update the file and the privileged listing") {
            !source.exists() && renamed.isFile && !explorer.isRefreshing && items().map { it.path } == listOf(renamed.path)
        }
        assertEquals("rename through the row", renamed.readText())
        assertNotEquals(originalItem, onMain { items().single() })
        assertTrue(onMain { items().single() is PrivilegedExplorerItem })
    }

    @Test
    fun realRowDeleteMenuCancelsSafelyAndDeletesOnlyAfterConfirmation() {
        val source = File(directory, "delete.txt").apply { writeText("keep until confirmed") }
        installExplorer()
        openRowMenu(source, R.string.text_delete)
        withNode({ it.text?.contains(source.path) == true }) { assertTrue(it.isVisibleToUser) }
        clickDialogButton("md_buttonDefaultNegative")
        awaitMain("Cancel should return to the file list") { explorer.hasWindowFocus() }
        assertEquals("keep until confirmed", source.readText())
        assertEquals(listOf(source.path), onMain { items().map { it.path } })

        openRowMenu(source, R.string.text_delete)
        clickDialogButton("md_buttonDefaultPositive")
        awaitMain("Confirmed deletion should refresh the list") { !source.exists() && !explorer.isRefreshing && items().isEmpty() }
        assertTrue(directory.isDirectory)
    }

    @Test
    fun longPressOnARealPrivilegedRowOpensTheSelectionToolbar() {
        val source = File(directory, "first.txt").apply { writeText("first") }
        File(directory, "second.txt").writeText("second")
        installExplorer()
        performRowAction(source.path, R.id.item, longClick = true)
        awaitMain("Privileged rows should enter selection mode") { explorer.findViewById<View>(R.id.selection_toolbar).isShown }
        assertTrue(source.exists())
        assertEquals(2, onMain { items().size })
    }

    private fun installExplorer() {
        onMain {
            explorer = ExplorerView(ContextThemeWrapper(activity, R.style.AppTheme)).apply {
                // Avoid presenting or consuming unrelated interrupted-operation diagnostics.
                setPluginActionProvider(ExplorerSurface.UNSPECIFIED, null)
                setExplorer(Explorers.workspace(), ExplorerDirPage.createRoot(directory))
                configurePrivilegedAccess(PrivilegedAccessMode.SHIZUKU)
            }
            activity.showExplorer(explorer)
        }
        awaitMain("The fixture should load as privileged rows") {
            explorer.isAttachedToWindow && !explorer.isRefreshing && explorer.currentPage is PrivilegedExplorerItem &&
                items().size == directory.listFiles()!!.size && items().all { it is PrivilegedExplorerItem }
        }
    }

    private fun items(): List<ExplorerItem> = explorer.explorerItemManager.let { manager ->
        (0 until manager.itemCount()).map(manager::getFileItem)
    }

    private fun openRowMenu(file: File, title: Int) {
        performRowAction(file.path, R.id.more)
        val label = onMain { explorer.context.getString(title) }
        withNode({ it.text?.toString() == label }) { assertTrue("Menu item should accept clicks", clickNode(it)) }
    }

    private fun performRowAction(path: String, viewId: Int, longClick: Boolean = false) {
        awaitMain("Missing visible row control for $path") {
            val index = items().indexOfFirst { it.path == path }
            val list = explorer.explorerItemListView
            if (index < 0 || list == null) return@awaitMain false
            val position = (list.adapter as ExplorerAdapter).getItemPosition(items()[index], index)
            val control = list.findViewHolderForAdapterPosition(position)?.itemView?.findViewById<View>(viewId)
            if (control?.isShown != true) return@awaitMain false
            assertTrue(if (longClick) control.performLongClick() else control.performClick())
            true
        }
    }

    private fun clickDialogButton(id: String) = withNode({ it.viewIdResourceName?.endsWith("/$id") == true && it.isEnabled }) {
        assertTrue("Dialog button should accept clicks", clickNode(it))
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val parent = node.parent ?: return false
        return try { clickNode(parent) } finally { release(parent) }
    }

    private fun withNode(predicate: (AccessibilityNodeInfo) -> Boolean, action: (AccessibilityNodeInfo) -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val found = root?.let { try { findNode(it, predicate) } finally { release(it) } }
            if (found != null) {
                try { action(found) } finally { release(found) }
                return
            }
            SystemClock.sleep(40)
        }
        throw AssertionError("Expected visible popup/dialog control was not found")
    }

    @Suppress("DEPRECATION")
    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && predicate(node)) return AccessibilityNodeInfo.obtain(node)
        repeat(node.childCount) { index ->
            val child = node.getChild(index) ?: return@repeat
            val found = try { findNode(child, predicate) } finally { release(child) }
            if (found != null) return found
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo) = node.recycle()

    private fun awaitMain(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(40)
        }
        throw AssertionError(message)
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun fixtureParents() = listOfNotNull(context.cacheDir, context.getExternalFilesDir(null)).distinct()

    private fun createReadableFixture(): File {
        for (parent in fixtureParents()) {
            val candidate = File(parent, "privileged-explorer-ui-${UUID.randomUUID()}").apply { check(mkdir()) }
            try {
                PrivilegedFileOperations.stat(context, PrivilegedAccessMode.SHIZUKU, candidate.path)
                return candidate
            } catch (error: PrivilegedFileOperationException) {
                check(candidate.delete())
                if (error.code != "PERMISSION_DENIED") throw error
            }
        }
        assumeTrue("The authorized Shizuku identity cannot access either isolated app fixture location", false)
        throw AssertionError("Unreachable")
    }
}
