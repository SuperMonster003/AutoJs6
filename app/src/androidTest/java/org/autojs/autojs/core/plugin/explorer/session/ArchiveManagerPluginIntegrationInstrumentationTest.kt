package org.autojs.autojs.core.plugin.explorer.session

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.util.DialogUtils
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionIntentValues
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionTargetKeys
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ArchiveManagerPluginIntegrationInstrumentationTest {

    @Test
    fun pluginCreatesARealArchiveThenMovesAndRestoresTheSourceThroughHostTrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(
            "Archive Manager test APK is not installed",
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, 0)
            }.isSuccess,
        )
        val testRoot = File(context.cacheDir, "archive-manager-v16-joint-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val sourceBytes = "joint v16 source payload".encodeToByteArray()
        val source = File(testRoot, "joint-source.txt").apply { writeBytes(sourceBytes) }
        val sourcePath = source.canonicalPath
        val output = File(testRoot, "joint-source.txt.zip")
        val sourcesTrashed = CountDownLatch(1)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = PLUGIN_PACKAGE_NAME,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget(TARGET_ID, source)),
            allowCreateOutput = true,
            onSourcesTrashed = { sourcesTrashed.countDown() },
        )
        val repository = TrashRepository(context.applicationContext)
        var trashItem: TrashEntities.TrashItem? = null

        try {
            context.startActivity(compressionIntent(context, source, session).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            val moveSourcesToTrash = waitForPluginNode(
                instrumentation = instrumentation,
                resourceName = "moveSourcesToTrash",
            )
            assertTrue(moveSourcesToTrash.isEnabled)
            assertFalse(AccessibilityNodeInfoCompat.wrap(moveSourcesToTrash).isChecked)
            assertTrue(moveSourcesToTrash.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForPluginNode(instrumentation, "moveSourcesToTrash") { node -> AccessibilityNodeInfoCompat.wrap(node).isChecked }

            val createButton = waitForPluginNode(instrumentation, "createButton")
            assertTrue(createButton.isEnabled)
            assertTrue(createButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            assertTrue(
                "Archive Manager did not request source Trash within the deadline",
                sourcesTrashed.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            waitUntil("committed archive output was not published") { output.isFile }
            waitUntil("source remained visible after the Trash commit") { !source.exists() }

            trashItem = waitForTrashItem(context, sourcePath)
            assertArrayEquals(sourceBytes, readZipEntry(output, source.name))

            val restoreButton = waitForPluginNode(
                instrumentation = instrumentation,
                resourceName = "createButton",
            ) { node -> node.isEnabled }
            assertTrue(restoreButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil("source was not restored through the v21 plugin action") { source.isFile }
            assertTrue("restoring sources removed the created archive", output.isFile)
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(sourceBytes, readZipEntry(output, source.name))
            waitUntil("restored source remained in the host Trash database") {
                HistoryDatabase.getInstance(context.applicationContext)
                    .historyDao()
                    .listTrashItemsDesc()
                    .none { item -> item.originalPath == sourcePath }
            }
            trashItem = null

            val doneButton = waitForPluginNode(
                instrumentation = instrumentation,
                resourceName = "cancelButton",
            ) { node -> node.isEnabled }
            assertTrue(doneButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        } finally {
            val remainingItem = trashItem ?: HistoryDatabase.getInstance(context.applicationContext)
                .historyDao()
                .listTrashItemsDesc()
                .firstOrNull { item -> item.originalPath == sourcePath }
            if (remainingItem != null) {
                if (source.exists()) {
                    runCatching { repository.deleteTrashItem(remainingItem) }
                } else {
                    runCatching {
                        repository.restoreTrashItemToPath(
                            remainingItem,
                            source,
                            DialogUtils.OperationController(),
                        )
                    }
                }
            }
            runCatching { session.close() }
            runCatching {
                val pluginUid = context.packageManager
                    .getApplicationInfo(PLUGIN_PACKAGE_NAME, 0)
                    .uid
                val store = ExplorerActionTargetTrashBatchStore(context.applicationContext)
                store.list(pluginUid, testRoot, repository).forEach { record ->
                    store.remove(record.id)
                }
            }
            testRoot.deleteRecursively()
        }
    }

    private fun compressionIntent(
        context: android.content.Context,
        source: File,
        session: ExplorerActionHostSession,
    ): Intent {
        val sourceUri = AppFileProvider.getUriForFile(context, source)
        val parentUri = AppFileProvider.getUriForFile(context, requireNotNull(source.parentFile))
        val target = Bundle().apply {
            putString(ExplorerActionTargetKeys.ID, TARGET_ID)
            putParcelable(ExplorerActionTargetKeys.URI, sourceUri)
            putString(ExplorerActionTargetKeys.DISPLAY_NAME, source.name)
            putInt(ExplorerActionTargetKeys.KIND, ExplorerActionValues.TARGET_FILE)
            putString(ExplorerActionTargetKeys.MIME_TYPE, "text/plain")
            putLong(ExplorerActionTargetKeys.SIZE, source.length())
            putLong(ExplorerActionTargetKeys.LAST_MODIFIED, source.lastModified())
        }
        return Intent(ExplorerActionPluginActions.EXECUTE).apply {
            component = ComponentName(PLUGIN_PACKAGE_NAME, CREATE_ACTIVITY_CLASS_NAME)
            setDataAndType(sourceUri, "text/plain")
            clipData = ClipData.newRawUri(source.name, sourceUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            putExtra(ExplorerActionIntentExtras.ACTION_ID, COMPRESS_ACTION_ID)
            putExtra(ExplorerActionIntentExtras.PROTOCOL_VERSION, ExplorerActionProtocol.VERSION)
            putExtra(ExplorerActionIntentExtras.REQUEST_ID, UUID.randomUUID().toString())
            putExtra(ExplorerActionIntentExtras.PARENT_URI, parentUri)
            putExtra(ExplorerActionIntentExtras.PARENT_DISPLAY_PATH, requireNotNull(source.parentFile).path)
            putExtra(
                ExplorerActionIntentExtras.SOURCE_SURFACE,
                ExplorerActionIntentValues.SOURCE_SURFACE_MAIN,
            )
            putParcelableArrayListExtra(ExplorerActionIntentExtras.TARGETS, arrayListOf(target))
            putExtra(
                ExplorerActionIntentExtras.HOST_SESSION,
                Bundle().apply {
                    putBinder(ExplorerActionHostSessionKeys.BINDER, session)
                },
            )
        }
    }

    private fun waitForPluginNode(
        instrumentation: android.app.Instrumentation,
        resourceName: String,
        predicate: (AccessibilityNodeInfo) -> Boolean = { true },
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        val viewId = "$PLUGIN_PACKAGE_NAME:id/$resourceName"
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val node = root
                ?.findAccessibilityNodeInfosByViewId(viewId)
                .orEmpty()
                .firstOrNull(predicate)
            if (node != null) return node
            root?.let(::scrollFirstScrollableNodeForward)
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Archive Manager view did not become ready: $viewId")
    }

    private fun scrollFirstScrollableNodeForward(node: AccessibilityNodeInfo): Boolean {
        if (
            node.isScrollable &&
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        ) {
            return true
        }
        repeat(node.childCount) { index ->
            val child = node.getChild(index) ?: return@repeat
            if (scrollFirstScrollableNodeForward(child)) return true
        }
        return false
    }

    private fun waitForTrashItem(
        context: android.content.Context,
        sourcePath: String,
    ): TrashEntities.TrashItem {
        val database = HistoryDatabase.getInstance(context.applicationContext)
        var item: TrashEntities.TrashItem? = null
        waitUntil("host Trash record was not committed") {
            item = database.historyDao().listTrashItemsDesc().firstOrNull { candidate ->
                candidate.originalPath == sourcePath
            }
            item != null
        }
        return requireNotNull(item)
    }

    private fun waitUntil(failureMessage: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError(failureMessage)
    }

    private fun readZipEntry(archive: File, entryName: String): ByteArray = ZipFile(archive).use { zip ->
        val entry = requireNotNull(zip.getEntry(entryName))
        zip.getInputStream(entry).use { input -> input.readBytes() }
    }

    private companion object {
        const val PLUGIN_PACKAGE_NAME =
            "io.github.supermonster003.autojs6.plugin.archivemanager"
        const val CREATE_ACTIVITY_CLASS_NAME =
            "io.github.supermonster003.autojs6.plugin.archivemanager.CreateArchiveActivity"
        const val COMPRESS_ACTION_ID = "compress"
        const val TARGET_ID = "target-1"
        const val OPERATION_TIMEOUT_SECONDS = 30L
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
