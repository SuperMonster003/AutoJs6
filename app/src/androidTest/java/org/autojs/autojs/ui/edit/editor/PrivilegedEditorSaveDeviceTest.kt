package org.autojs.autojs.ui.edit.editor

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedBackupManager
import org.autojs.autojs.model.explorer.PrivilegedEditSessions
import org.autojs.autojs.model.explorer.PrivilegedFileOperationException
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.filechooser.FileChooseListView
import org.autojs.autojs6.R
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Uses only already-authorized Shizuku and its own 0700 remote directory and private sessions. */
@RunWith(AndroidJUnit4::class)
class PrivilegedEditorSaveDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val mode = PrivilegedAccessMode.SHIZUKU
    private val sessionUris = mutableListOf<Uri>()
    private val activities = mutableListOf<EditActivity>()
    private val preferenceKeys = listOf(
        EditorEnginePreferences.KEY_EDITOR_ENGINE,
        EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE,
    )
    private val previousPreferences = preferenceKeys.associateWith { key ->
        Pref.get().contains(key) to Pref.get().getString(key, null)
    }
    private lateinit var service: IUserService
    private lateinit var remoteRoot: String
    private lateinit var localRoot: File
    private var activity: EditActivity? = null

    @Before
    fun setUp() {
        assumeTrue("Window inspection requires API 29", Build.VERSION.SDK_INT >= 29)
        assumeTrue("Shizuku must already be running and authorized", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        service = checkNotNull(WrappedShizuku.getServiceOrNull())
        remoteRoot = "/data/local/tmp/autojs6-privileged-editor-ui-${UUID.randomUUID()}"
        shell("mkdir -m 700 -- ${quote(remoteRoot)}")
        localRoot = File(context.cacheDir, "privileged-editor-ui-${UUID.randomUUID()}").apply { check(mkdir()) }
        EditorEnginePreferences.set(EditorEngineType.NATIVE)
    }

    @After
    fun tearDown() {
        onMain {
            activities.distinct().forEach { if (!it.isDestroyed) it.finishAndRemoveTask() }
        }
        waitUntil("Editor test activities should close") { onMain { activities.all(Activity::isDestroyed) } }
        PrivilegedBackupManager.awaitIdle()
        sessionUris.distinct().forEach { uri ->
            val token = uri.pathSegments.single()
            check(token.matches(Regex("[a-f0-9-]{36}")))
            val directory = File(File(context.filesDir, "privileged-edit-sessions"), token)
            check(directory.canonicalFile.parentFile == File(context.filesDir, "privileged-edit-sessions").canonicalFile)
            if (directory.exists()) check(directory.deleteRecursively())
        }
        if (::remoteRoot.isInitialized) {
            check(remoteRoot.matches(Regex("/data/local/tmp/autojs6-privileged-editor-ui-[a-f0-9-]{36}")))
            File(context.filesDir, "privileged-edit-backups").listFiles().orEmpty()
                .filter { it.name.matches(Regex("[a-f0-9-]{36}\\.json")) }
                .forEach { file ->
                    val record = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@forEach
                    if (record.optString("source").startsWith("$remoteRoot/") || record.optString("requestedSource").startsWith("$remoteRoot/")) {
                        AtomicFile(file).delete()
                    }
                }
            shell("chmod -R u+rwX -- ${quote(remoteRoot)}")
            shell("rm -rf -- ${quote(remoteRoot)}")
        }
        if (::localRoot.isInitialized) {
            check(localRoot.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            check(localRoot.deleteRecursively())
        }
        Pref.get().edit().apply {
            previousPreferences.forEach { (key, previous) ->
                if (previous.first) putString(key, previous.second) else remove(key)
            }
        }.commit()
    }

    @Test
    fun editorWritesBackToAnUnreadableOriginalAndCanSaveAgain() {
        val source = source("editable ' 中文.js", "initial\n")
        assertFalse("The original must require privileged access", File(source).canRead())
        val uri = newSession(source)
        val editor = launch(uri, "initial\n")
        assertFalse(onMain { editor.isReadOnly() })
        assertEquals(View.GONE, onMain { editor.findViewById<View>(R.id.run).visibility })
        assertEquals(File(source).name, onMain { editor.findViewById<TextView>(R.id.editor_file_name_status).text.toString() })

        append(editor, "first edit\n")
        assertEquals("initial\nfirst edit\n", save(editor).getOrThrow())
        awaitDirty(editor, false)
        assertEquals("initial\nfirst edit\n", remoteRead(source).toString(Charsets.UTF_8))
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, uri))

        append(editor, "second edit\n")
        save(editor).getOrThrow()
        awaitDirty(editor, false)
        assertEquals("initial\nfirst edit\nsecond edit\n", remoteRead(source).toString(Charsets.UTF_8))
    }

    @Test
    fun externalModificationRefusesSaveAndKeepsEditorAndDurableDraft() {
        val source = source("conflict.txt", "original\n")
        val uri = newSession(source)
        val editor = launch(uri, "original\n")
        append(editor, "my edit\n")
        remoteWrite(source, "external replacement\n".toByteArray())

        val error = save(editor).exceptionOrNull()
        assertTrue(error.toString(), error is PrivilegedFileOperationException)
        assertEquals("SOURCE_CHANGED", (error as PrivilegedFileOperationException).code)
        awaitDirty(editor, true)
        assertEquals("original\nmy edit\n", onMain { editor.editor.textSnapshot })
        assertEquals("external replacement\n", remoteRead(source).toString(Charsets.UTF_8))
        assertTrue(PrivilegedEditSessions.hasPendingDraft(context, uri))
        assertEquals("original\nmy edit\n", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
    }

    @Test
    fun deniedDirectoryWriteKeepsTheDraftAndAnExplicitRetrySucceedsAfterPermissionIsRestored() {
        assumeTrue("This check requires a non-root Shizuku service", shell("id -u").trim() != "0")
        val directory = "$remoteRoot/permission"
        shell("mkdir -m 700 -- ${quote(directory)}")
        val source = "$directory/retry.txt"
        remoteWrite(source, "initial\n".toByteArray())
        val uri = newSession(source)
        val editor = launch(uri, "initial\n")
        append(editor, "preserved edit\n")
        shell("chmod 500 -- ${quote(directory)}")
        try {
            val failure = save(editor).exceptionOrNull()
            assertTrue(failure.toString(), failure is PrivilegedFileOperationException)
            assertFalse(failure?.message.isNullOrBlank())
            awaitDirty(editor, true)
            assertTrue(PrivilegedEditSessions.hasPendingDraft(context, uri))
            assertEquals("initial\n", remoteRead(source).toString(Charsets.UTF_8))
        } finally {
            shell("chmod 700 -- ${quote(directory)}")
        }

        assertEquals("initial\npreserved edit\n", save(editor).getOrThrow())
        awaitDirty(editor, false)
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, uri))
        assertEquals("initial\npreserved edit\n", remoteRead(source).toString(Charsets.UTF_8))
    }

    @Test
    fun failedSaveAndExitKeepsTheActivityOpenWithRecoveryActions() {
        val source = source("exit-conflict.txt", "original\n")
        val uri = newSession(source)
        val editor = launch(uri, "original\n")
        append(editor, "unsaved\n")
        remoteWrite(source, "other program\n".toByteArray())

        onMain { activity!!.finish() }
        clickText(R.string.text_save_and_exit)
        waitUntil("A failed exit-save should offer Continue editing") { hasText(R.string.text_privileged_edit_keep_editing) }
        assertFalse(onMain { activity!!.isFinishing })
        assertTrue(hasText(R.string.dialog_button_save_as))
        assertTrue(hasText(R.string.text_explorer_access_mode))
        clickText(R.string.text_privileged_edit_keep_editing)
        awaitDirty(editor, true)
        assertEquals("original\nunsaved\n", onMain { editor.editor.textSnapshot })
        assertEquals("other program\n", remoteRead(source).toString(Charsets.UTF_8))
    }

    @Test
    fun persistedUtf16DraftReopensDirtyAndSurvivesActivityRecreation() {
        val source = source("utf16.txt", "baseline")
        val uri = newSession(source)
        remoteWrite(source, "external content".toByteArray())
        val text = "草稿仍然保留\nnot yet saved\n"
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val failure = runCatching { PrivilegedEditSessions.save(context, uri, bytes, Charsets.UTF_16LE.name(), true) }.exceptionOrNull()
        assertTrue(failure.toString(), failure is PrivilegedFileOperationException)
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })

        val editor = launch(uri, text, pending = true)
        assertTrue(onMain { editor.shouldSaveDraft() })
        val oldActivity = activity!!
        onMain { oldActivity.recreate() }
        waitUntil("The recreated activity should keep the same private session") {
            onMain {
                val current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<EditActivity>().firstOrNull { it !== oldActivity && it.intent.data == uri }
                if (current != null) {
                    activity = current
                    if (current !in activities) activities += current
                }
                current != null
            }
        }
        val restored = onMain { activity!!.findViewById<EditorView>(R.id.editor_view) }
        awaitLoaded(restored, text, pending = true)
        assertEquals(Charsets.UTF_16LE, PrivilegedEditSessions.pendingCharset(context, uri))
        assertEquals("external content", remoteRead(source).toString(Charsets.UTF_8))
    }

    @Test
    fun saveAsFromConflictKeepsTheExtensionAndDetachesSubsequentSavesFromOriginal() {
        val source = source("save-copy.js", "baseline\n")
        val uri = newSession(source)
        val editor = launch(uri, "baseline\n")
        append(editor, "my copy\n")
        remoteWrite(source, "external owner\n".toByteArray())
        val error = checkNotNull(save(editor).exceptionOrNull())
        onMain { assertTrue(editor.showPrivilegedSaveError(error)) }
        clickText(R.string.dialog_button_save_as)
        waitUntil("Save As should open the directory chooser") { onMain { windowViews().any { it is FileChooseListView } } }
        val destination = File(localRoot, "saved-copy").apply { check(mkdir()) }
        onMain {
            val chooser = windowViews().filterIsInstance<FileChooseListView>().last()
            chooser.setExplorer(Explorers.external(), ExplorerDirPage.createRoot(destination))
        }
        waitUntil("The test destination should be selected") {
            onMain {
                windowViews().filterIsInstance<FileChooseListView>().lastOrNull()?.let {
                    !it.isRefreshing && it.currentDirectory.canonicalPath == destination.canonicalPath
                } == true
            }
        }
        clickText(R.string.dialog_button_confirm)
        val saved = File(destination, "save-copy.js")
        waitUntil("Save As should establish the local document") {
            onMain { editor.uri == Uri.fromFile(saved) && !editor.saveStickyDirty }
        }
        assertEquals("baseline\nmy copy\n", saved.readText())
        assertEquals(Uri.fromFile(saved), onMain { activity!!.intent.data })
        assertEquals(saved.path, onMain { activity!!.intent.getStringExtra(EditorView.EXTRA_PATH) })
        assertEquals(View.VISIBLE, onMain { editor.findViewById<View>(R.id.run).visibility })
        assertTrue(onMain { activity!!.intent.getBooleanExtra(EditorView.EXTRA_RUN_ENABLED, true) })
        waitUntil("A successfully saved copy should resolve its old recovery draft") { !PrivilegedEditSessions.hasPendingDraft(context, uri) }

        append(editor, "local only\n")
        save(editor).getOrThrow()
        assertEquals("baseline\nmy copy\nlocal only\n", saved.readText())
        assertEquals("external owner\n", remoteRead(source).toString(Charsets.UTF_8))
        assertNotEquals(uri, editor.uri)
    }

    @Test
    fun explicitDiscardDoesNotRestoreTheSameFailedDraftOnNextOpen() {
        val source = source("discard.txt", "baseline\n")
        val uri = newSession(source)
        val editor = launch(uri, "baseline\n")
        append(editor, "discard this\n")
        remoteWrite(source, "external owner\n".toByteArray())
        assertTrue(save(editor).isFailure)
        onMain { activity!!.finish() }
        clickText(R.string.text_exit_directly)
        waitUntil("Explicit discard should finish the editor") { onMain { activity!!.isDestroyed } }
        assertFalse(PrivilegedEditSessions.hasPendingDraft(context, uri))
        val next = newSession(source)
        assertNotEquals(uri, next)
        launch(next, "external owner\n")
    }

    private fun source(name: String, text: String): String = "$remoteRoot/$name".also { remoteWrite(it, text.toByteArray()) }

    private fun newSession(source: String): Uri = PrivilegedEditSessions.create(context, mode, source).also { sessionUris += it }

    private fun launch(uri: Uri, expected: String, pending: Boolean = false): EditorView {
        val monitor = instrumentation.addMonitor(EditActivity::class.java.name, null, false)
        try {
            onMain { assertTrue(EditActivity.editPrivilegedDocument(context, uri, File(checkNotNull(PrivilegedEditSessions.originalPath(context, uri))).name)) }
            val launched = instrumentation.waitForMonitorWithTimeout(monitor, 45_000L) as? EditActivity
            assertNotNull("The privileged document should open in the editor", launched)
            activity = launched!!
            activities += launched
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        val editor = onMain { activity!!.findViewById<EditorView>(R.id.editor_view) }
        awaitLoaded(editor, expected, pending)
        return editor
    }

    private fun awaitLoaded(editor: EditorView, text: String, pending: Boolean) = waitUntil("The complete document and its dirty state should load") {
        onMain {
            editor.editor.isReady && !editor.editor.isLoadingText && editor.editor.textSnapshot == text &&
                editor.saveStickyDirty == pending
        }
    }

    private fun append(editor: EditorView, text: String) {
        onMain {
            editor.editor.jumpToEnd()
            editor.editor.insert(text)
        }
        awaitDirty(editor, true)
    }

    private fun awaitDirty(editor: EditorView, expected: Boolean) = waitUntil("The dirty state should be $expected") {
        onMain { editor.saveStickyDirty == expected && editor.shouldSaveDraft() == expected }
    }

    private fun save(editor: EditorView): Result<String> {
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<String>>()
        onMain {
            editor.save().subscribe({ result.set(Result.success(it)); completed.countDown() }, {
                result.set(Result.failure(it)); completed.countDown()
            })
        }
        assertTrue("Privileged save should finish", completed.await(60, TimeUnit.SECONDS))
        return result.get()
    }

    private fun clickText(resource: Int) = waitUntil("Missing button ${context.getString(resource)}") {
        onMain {
            windowViews().filterIsInstance<TextView>()
                .lastOrNull { it.isShown && it.isEnabled && it.isClickable && it.text.toString() == context.getString(resource) }
                ?.performClick() == true
        }
    }

    private fun hasText(resource: Int): Boolean = onMain {
        windowViews().filterIsInstance<TextView>().any { it.isShown && it.text.toString() == context.getString(resource) }
    }

    private fun windowViews(): List<View> = WindowInspector.getGlobalWindowViews().flatMap(::descendants)

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    private fun remoteWrite(path: String, bytes: ByteArray) {
        val payload = Base64.encodeToString(bytes, Base64.NO_WRAP)
        shell("printf %s ${quote(payload)} | base64 -d > ${quote(path)}")
    }

    private fun remoteRead(path: String): ByteArray = Base64.decode(shell("base64 < ${quote(path)}"), Base64.DEFAULT)

    private fun shell(command: String): String {
        val result = JSONObject(service.execCommand(command))
        assertEquals(result.toString(), 0, result.getInt("code"))
        return result.optString("result")
    }

    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"

    private fun waitUntil(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    private fun <T> onMain(action: () -> T): T {
        val value = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try { value.set(action()) } catch (caught: Throwable) { error.set(caught) }
        }
        error.get()?.let { throw it }
        return value.get()
    }
}
