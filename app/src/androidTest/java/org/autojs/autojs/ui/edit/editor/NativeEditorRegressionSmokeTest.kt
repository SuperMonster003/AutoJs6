package org.autojs.autojs.ui.edit.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.editor.loading.EditorTextLoadPolicy
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NativeEditorRegressionSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val originalEnginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
    private val originalSelectionModePreference =
        captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
    private var activity: Activity? = null
    private val launchedEditActivities = CopyOnWriteArrayList<Activity>()

    @After
    fun tearDown() {
        finishLaunchedActivity()
        restoreEnginePreferences()
    }

    @Test
    fun nativeEditorCoreWorkflowSmoke() {
        val initialText = """
            var gate9Search = "gate9SearchTarget";
            function gate9NativeSmoke() {
                log(gate9Search);
            }
            sleep(1500);
        """.trimIndent() + "\n"
        val file = createTempScriptFile("native-gate9-core", initialText)
        try {
            val editorView = launchReadyNativeEditActivity(file, initialText)

            val appendedText = "// gate9 native edit\n"
            val editedText = initialText + appendedText
            runOnMainSync {
                editorView.editor.jumpToEnd()
                editorView.editor.insert(appendedText)
            }
            waitForEditorViewState(
                editorView = editorView,
                expectedText = editedText,
                expectedDirty = true,
                message = "Native editor should keep direct edits and dirty state",
            )

            showAndHideImeBestEffort(editorView)
            assertNativeEditor(editorView)
            assertNativeAutoCompletionAvailable(editorView)

            val replacedText = editedText.replace("gate9SearchTarget", "gate9Replaced")
            runOnMainSync {
                assertTrue(editorView.tryFind("gate9SearchTarget", usingRegex = false))
                assertTrue(editorView.tryReplace("gate9SearchTarget", "gate9Replaced", usingRegex = false))
                editorView.editor.replaceSelection()
            }
            waitForEditorViewState(
                editorView = editorView,
                expectedText = replacedText,
                expectedDirty = true,
                message = "Native search and replace should update editor text",
            )

            runOnMainSync {
                editorView.editor.jumpTo(1, 0)
                editorView.editor.addOrRemoveBreakpointAtCurrentLine()
            }
            waitUntil("Native breakpoint should be added") {
                runOnMainSync { editorView.editor.breakpointsSnapshot.any { it.line == 1 && it.enabled } }
            }
            runOnMainSync {
                editorView.editor.addOrRemoveBreakpointAtCurrentLine()
            }
            waitUntil("Native breakpoint should be removed") {
                runOnMainSync { editorView.editor.breakpointsSnapshot.none { it.line == 1 } }
            }

            val savedText = saveEditorView(editorView)
            assertEquals(replacedText, savedText)
            assertEquals(replacedText, file.readText())
            waitForEditorViewState(
                editorView = editorView,
                expectedText = replacedText,
                expectedDirty = false,
                message = "Native save should clear dirty state",
            )

            val execution = runOnMainSync { editorView.run(showMessage = false, file = file) }
            assertNotNull(execution)
            waitUntil("Native run should enter running state", timeoutMillis = RUN_TIMEOUT_MS) {
                runOnMainSync { editorView.isScriptRunning() }
            }
            runOnMainSync {
                editorView.forceStop()
            }
            waitUntil("Native run should stop after forceStop", timeoutMillis = RUN_TIMEOUT_MS) {
                runOnMainSync { !editorView.isScriptRunning() }
            }
            assertNativeEditor(editorView)
        } finally {
            finishLaunchedActivity()
            file.delete()
        }
    }

    @Test
    fun nativeEditorLargeFileSmoke() {
        val largeText = buildLargeScriptText()
        val file = createTempScriptFile("native-gate9-large", largeText)
        try {
            assertTrue(file.length() >= EditorTextLoadPolicy.LARGE_FILE_THRESHOLD_BYTES)

            val editorView = startEditActivity(file)
            waitUntil("Native large file should load completely", timeoutMillis = LARGE_FILE_TIMEOUT_MS) {
                runOnMainSync {
                    editorView.editor.engineType == EditorEngineType.NATIVE &&
                        !editorView.editor.isLoadingText &&
                        editorView.editor.textSnapshot.length == largeText.length &&
                        !editorView.isTextChanged &&
                        !editorView.saveStickyDirty
                }
            }
            assertTrue(runOnMainSync { editorView.editor.lineCountSnapshot } > 2000)
            assertEquals(largeText.take(128), runOnMainSync { editorView.editor.textSnapshot.take(128) })
            assertEquals(largeText.takeLast(128), runOnMainSync { editorView.editor.textSnapshot.takeLast(128) })
            assertEditorChromeSettled(editorView, file)
        } finally {
            finishLaunchedActivity()
            file.delete()
        }
    }

    private fun launchReadyNativeEditActivity(file: File, expectedText: String): EditorView {
        val editorView = startEditActivity(file)
        waitUntil("EditActivity should start with Native editor") {
            runOnMainSync {
                editorView.editor.engineType == EditorEngineType.NATIVE &&
                    editorView.editor.isReady
            }
        }
        waitForEditorViewState(
            editorView = editorView,
            expectedText = expectedText,
            expectedDirty = false,
            message = "Native EditActivity should load the test file cleanly",
        )
        assertEditorChromeSettled(editorView, file)
        return editorView
    }

    private fun assertEditorChromeSettled(editorView: EditorView, file: File) {
        waitUntil("Editor info bar and themed transition background should settle") {
            runOnMainSync {
                val fileName = editorView
                    .findViewById<TextView>(R.id.editor_file_name_status)
                    .text
                    .toString()
                val cursorPosition = editorView
                    .findViewById<TextView>(R.id.editor_cursor_status)
                    .text
                    .toString()
                val editorBackground = editorView.background as? ColorDrawable
                val themeBackground = editorView.getEditorTheme()?.backgroundColor
                fileName == file.name &&
                    cursorPosition == "1:1" &&
                    themeBackground != null &&
                    editorBackground?.color == themeBackground
            }
        }
    }

    private fun startEditActivity(file: File): EditorView {
        EditorEnginePreferences.set(EditorEngineType.NATIVE)
        val intent = Intent(instrumentation.targetContext, EditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EditorView.EXTRA_PATH, file.absolutePath)
            .putExtra(EditorView.EXTRA_NAME, file.nameWithoutExtension)
        val monitor = instrumentation.addMonitor(EditActivity::class.java.name, null, false)
        try {
            instrumentation.targetContext.startActivity(intent)
            val launched = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_LAUNCH_TIMEOUT_MS)
                as? EditActivity
            assertNotNull("EditActivity should launch for ${file.name}", launched)
            activity = launched
            launchedEditActivities.add(launched!!)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        instrumentation.waitForIdleSync()

        val editorView = runOnMainSync {
            activity!!.findViewById<EditorView>(R.id.editor_view)
        }
        assertNotNull(editorView)
        return editorView
    }

    private fun showAndHideImeBestEffort(editorView: EditorView) {
        runOnMainSync {
            val inputView = editorView.editor.inputView
            inputView.requestFocus()
            editorView.editor.requestEditorFocus()
            val inputMethodManager = instrumentation.targetContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            inputMethodManager.showSoftInput(inputView, 0)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(IME_SETTLE_DELAY_MS)
        runOnMainSync {
            val inputView = editorView.editor.inputView
            val inputMethodManager = instrumentation.targetContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(inputView.windowToken, 0)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(IME_SETTLE_DELAY_MS)
    }

    private fun saveEditorView(editorView: EditorView): String {
        val text = AtomicReference<String>()
        val error = AtomicReference<Throwable>()
        val latch = java.util.concurrent.CountDownLatch(1)
        runOnMainSync {
            editorView.save().subscribe({ savedText ->
                text.set(savedText)
                latch.countDown()
            }, { throwable ->
                error.set(throwable)
                latch.countDown()
            })
        }
        assertTrue("Native save should finish", latch.await(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        error.get()?.let { throw AssertionError("Native save should succeed: ${it.message}", it) }
        return text.get().orEmpty()
    }

    private fun finishLaunchedActivity() {
        val activities = (listOfNotNull(activity) + launchedEditActivities).distinct()
        if (activities.isNotEmpty()) {
            runOnMainSync {
                activities.forEach { launched ->
                    launched.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (!launched.isDestroyed) {
                        launched.finishAndRemoveTask()
                    }
                }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(ACTIVITY_FINISH_DELAY_MS)
        }
        activity = null
        launchedEditActivities.clear()
    }

    private fun assertNativeEditor(editorView: EditorView) {
        assertEquals(EditorEngineType.NATIVE, runOnMainSync { editorView.editor.engineType })
    }

    private fun assertNativeAutoCompletionAvailable(editorView: EditorView) {
        runOnMainSync {
            val autoCompletion = editorView.editor.createAutoCompletion(instrumentation.targetContext)
            assertNotNull(autoCompletion)
            autoCompletion?.shutdown()
        }
    }

    private fun waitForEditorViewState(
        editorView: EditorView,
        expectedText: String,
        expectedDirty: Boolean,
        message: String,
    ) {
        waitUntil(message) {
            runOnMainSync {
                editorView.editor.engineType == EditorEngineType.NATIVE &&
                    editorView.editor.textSnapshot == expectedText &&
                    editorView.isTextChanged == expectedDirty &&
                    editorView.saveStickyDirty == expectedDirty
            }
        }
    }

    private fun createTempScriptFile(prefix: String, text: String): File {
        return File(
            instrumentation.targetContext.cacheDir,
            "$prefix-${SystemClock.uptimeMillis()}.js",
        ).also { file ->
            file.writeText(text)
        }
    }

    private fun buildLargeScriptText(): String {
        val builder = StringBuilder(EditorTextLoadPolicy.LARGE_FILE_THRESHOLD_BYTES + 64 * 1024)
        var index = 0
        while (builder.length < EditorTextLoadPolicy.LARGE_FILE_THRESHOLD_BYTES + 32 * 1024) {
            builder.append("log(\"native gate9 large line ")
                .append(index.toString().padStart(5, '0'))
                .append("\");\n")
            index++
        }
        return builder.toString()
    }

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        intervalMillis: Long = POLL_INTERVAL_MS,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val result = try {
                condition()
            } catch (throwable: Throwable) {
                lastError = throwable
                false
            }
            if (result) {
                return
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(intervalMillis)
        }
        lastError?.let { throw AssertionError("$message: ${it.message}", it) }
        fail(message)
    }

    private fun <T> runOnMainSync(block: () -> T): T {
        val value = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try {
                value.set(block())
            } catch (throwable: Throwable) {
                error.set(throwable)
            }
        }
        error.get()?.let { throw it }
        return value.get()
    }

    private fun captureStringPreference(key: String): StoredStringPreference {
        val preferences = Pref.get()
        return StoredStringPreference(
            present = preferences.contains(key),
            value = preferences.getString(key, null),
        )
    }

    private fun restoreEnginePreferences() {
        val editor = Pref.get().edit()
        originalEnginePreference.restore(editor, EditorEnginePreferences.KEY_EDITOR_ENGINE)
        originalSelectionModePreference.restore(editor, EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
        editor.commit()
    }

    private data class StoredStringPreference(
        val present: Boolean,
        val value: String?,
    ) {
        fun restore(editor: SharedPreferences.Editor, key: String) {
            if (present) editor.putString(key, value) else editor.remove(key)
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val LARGE_FILE_TIMEOUT_MS = 30_000L
        private const val RUN_TIMEOUT_MS = 8_000L
        private const val SAVE_TIMEOUT_MS = 10_000L
        private const val ACTIVITY_LAUNCH_TIMEOUT_MS = 45_000L
        private const val ACTIVITY_FINISH_DELAY_MS = 500L
        private const val IME_SETTLE_DELAY_MS = 500L
        private const val POLL_INTERVAL_MS = 50L
    }
}
