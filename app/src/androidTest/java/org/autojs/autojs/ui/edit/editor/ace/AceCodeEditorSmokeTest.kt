package org.autojs.autojs.ui.edit.editor.ace

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AceCodeEditorSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: Activity? = null

    @After
    fun tearDown() {
        AceEditorTestHooks.reset()
        AceEditorLspPreferences.setEnabled(false)
        EditorEnginePreferences.set(EditorEngineType.NATIVE)
        activity?.let { launched ->
            runOnMainSync {
                launched.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                launched.finish()
            }
            instrumentation.waitForIdleSync()
        }
        activity = null
    }

    @Test
    fun loadsAceEditorAndReportsDiagnostics() {
        val editor = launchReadyEditor()

        val snapshot = runOnMainSync {
            editor.createDiagnosticsSnapshot(
                fileSizeBytes = editor.textSnapshot.length.toLong(),
                largeFilePolicy = "instrumentation_smoke",
            )
        }

        assertTrue(AceCodeEditor.hasRequiredAssets(instrumentation.targetContext))
        assertEquals("ACE", snapshot.engineType)
        assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
        assertNull(snapshot.lastFailureType)
        assertNotNull(snapshot.webViewVersion)
        assertNotNull(snapshot.androidVersion)
        assertTrue((snapshot.textMirrorRevision ?: 0L) > 0L)
        assertNotNull(snapshot.lastReadyAtUptimeMillis)
        assertNotNull(snapshot.lastFirstPaintAtUptimeMillis)
        assertTrue(snapshot.runtimeEventHistory.any { it.contains("bridge_ready") })
        assertTrue(snapshot.runtimeEventHistory.any { it.contains("first_paint") })
    }

    @Test
    fun setTextAndInsertRoundTripThroughBridge() {
        val editor = launchReadyEditor()
        val initialText = "const value = 1;\nconsole.log(value);\n"
        val insertedText = "// inserted from instrumentation\n"

        runOnMainSync {
            editor.setText(initialText)
        }
        waitForEditorText(editor, initialText, "ACE setText should reach the WebView document")

        runOnMainSync {
            editor.jumpToEnd()
            editor.insert(insertedText)
        }
        val expectedText = initialText + insertedText
        waitForEditorText(editor, expectedText, "ACE insert should round-trip through the bridge")

        val snapshot = runOnMainSync { editor.createDiagnosticsSnapshot() }
        assertEquals(expectedText, runOnMainSync { editor.textSnapshot })
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
        assertNull(snapshot.lastFailureType)
    }

    @Test
    fun breakpointAndResizeEventsUpdateDiagnostics() {
        val editor = launchReadyEditor()

        runOnMainSync {
            editor.setText("line0\nline1\nline2\n")
        }
        waitForEditorText(editor, "line0\nline1\nline2\n", "ACE text should be ready before breakpoint smoke")

        runOnMainSync {
            editor.setBreakpoint(1, true)
            editor.ensureCursorVisible("instrumentation_smoke")
        }

        waitUntil("ACE breakpoint event should update Kotlin state") {
            runOnMainSync { editor.breakpointsSnapshot.contains(1) }
        }
        waitUntil("ACE resize should be scheduled with the smoke reason") {
            runOnMainSync {
                val snapshot = editor.createDiagnosticsSnapshot()
                snapshot.lastAceResizeScheduledReason == "instrumentation_smoke"
            }
        }
        waitUntil("ACE resizeDone should update diagnostics with the smoke reason") {
            runOnMainSync {
                val snapshot = editor.createDiagnosticsSnapshot()
                snapshot.lastAceResizeCompletedReason == "instrumentation_smoke"
            }
        }

        val snapshot = runOnMainSync { editor.createDiagnosticsSnapshot() }
        assertNull(snapshot.lastFailureType)
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
        assertTrue(snapshot.runtimeEventHistory.any { it.contains("resize_done") })
    }

    @Test
    fun acceptsStaticCompletionsThroughAcePopup() {
        val editor = launchReadyEditor(lspEnabled = false)

        val result = runCompletionAcceptSmoke(editor)
        assertTrue("Completion smoke should finish", result.getBoolean("ok"))
        assertCompletionText(result, "toast", "toast")
        assertCompletionText(result, "app.launch", "app.launch")
        assertCompletionText(result, "files.read", "files.read")
        assertCompletionText(result, "images.findImage", "images.findImage")
        assertCompletionText(result, "device.getBattery", "device.getBattery")

        val snapshot = runOnMainSync { editor.createDiagnosticsSnapshot() }
        assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
        assertNull(snapshot.lastFailureType)
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
    }

    @Test
    fun tooltipAndLspFallbackStayStaticAndLocal() {
        val editor = launchReadyEditor(lspEnabled = false)
        runOnMainSync {
            editor.setText("app.launch(\"org.autojs.autojs6\");\n")
        }
        waitForEditorText(
            editor,
            "app.launch(\"org.autojs.autojs6\");\n",
            "ACE text should be ready before tooltip smoke",
        )

        val disabledState = evaluateJsonObject(
            editor,
            """
                (function() {
                    var shown = window.AutoJsAce.showLspTooltip(0, 5);
                    return JSON.stringify({
                        lsp: window.AutoJsAce.getLspState(),
                        hover: window.AutoJsAce.getLspHover(0, 5),
                        shown: shown,
                        tooltip: window.AutoJsAce.getTooltipState()
                    });
                })();
            """.trimIndent(),
        )
        val disabledLsp = disabledState.getJSONObject("lsp")
        assertFalse(disabledLsp.getBoolean("enabled"))
        assertEquals("disabled", disabledLsp.getString("state"))
        assertEquals("static-fallback", disabledLsp.getString("completionProvider"))
        assertEquals("static-fallback", disabledLsp.getString("hoverProvider"))
        assertFalse(disabledLsp.getBoolean("serverReady"))
        assertTrue(disabledState.getBoolean("shown"))
        assertEquals("app.launch", disabledState.getJSONObject("hover").getString("caption"))
        val tooltip = disabledState.getJSONObject("tooltip")
        assertTrue(tooltip.getBoolean("domCreated"))
        assertTrue(tooltip.getBoolean("markdownLoaded"))
        assertEquals("safe-subset", tooltip.getString("markdownRenderer"))

        runOnMainSync {
            editor.destroy()
            activity?.finish()
        }
        instrumentation.waitForIdleSync()
        activity = null

        val lspEditor = launchReadyEditor(lspEnabled = true)
        val enabledNoServer = evaluateJsonObject(
            lspEditor,
            """
                (function() {
                    return JSON.stringify(window.AutoJsAce.getLspState());
                })();
            """.trimIndent(),
        )
        assertTrue(enabledNoServer.getBoolean("enabled"))
        assertEquals("configured-no-server", enabledNoServer.getString("state"))
        assertEquals("unavailable", enabledNoServer.getString("transport"))
        assertEquals("static-completion", enabledNoServer.getString("fallback"))
        assertEquals("static-fallback", enabledNoServer.getString("completionProvider"))
        assertEquals("static-fallback", enabledNoServer.getString("hoverProvider"))
        assertFalse(enabledNoServer.getBoolean("serverReady"))
        assertFalse(enabledNoServer.getBoolean("serverAvailable"))

        val fallbackResult = runCompletionAcceptSmoke(lspEditor)
        assertTrue("LSP no-server completion smoke should finish", fallbackResult.getBoolean("ok"))
        assertCompletionText(fallbackResult, "files.read", "files.read")
        val snapshot = runOnMainSync { lspEditor.createDiagnosticsSnapshot() }
        assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
        assertNull(snapshot.lastFailureType)
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
    }

    @Test
    fun editActivityRotationRestoreAndEngineSwitchPreserveTextAndDirtyState() {
        val file = File(
            instrumentation.targetContext.cacheDir,
            "ace-gate7-${SystemClock.uptimeMillis()}.js",
        )
        val initialText = """
            const gate = "gate7";
            console.log(gate);
        """.trimIndent() + "\n"
        val marker = "// unsaved gate7 marker ${SystemClock.uptimeMillis()}\n"
        val expectedText = initialText + marker

        file.writeText(initialText)
        try {
            val editorView = launchReadyEditActivity(file, initialText)
            val editActivity = activity as EditActivity

            runOnMainSync {
                editorView.editor.jumpToEnd()
                editorView.editor.insert(marker)
                editorView.editor.requestEditorFocus()
            }
            waitForEditorViewText(editorView, expectedText, "ACE EditActivity text should contain the unsaved marker")
            waitUntil("ACE EditActivity should be dirty after direct edit") {
                runOnMainSync { editorView.isTextChanged && editorView.saveStickyDirty }
            }

            showImeBestEffort(editorView)
            assertAceCursorVisible(editorView, "ACE cursor should be visible before Gate 7 rotation")
            rotateAndAssertEditorState(
                editActivity,
                editorView,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                Configuration.ORIENTATION_LANDSCAPE,
                expectedText,
                expectedDirty = true,
            )
            rotateAndAssertEditorState(
                editActivity,
                editorView,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                Configuration.ORIENTATION_PORTRAIT,
                expectedText,
                expectedDirty = true,
            )

            backgroundAndRestore(editActivity)
            waitForEditorViewState(
                editorView,
                expectedText,
                expectedDirty = true,
                "ACE EditActivity should preserve text and dirty state after background restore",
            )
            assertAceCursorVisible(editorView, "ACE cursor should be visible after background restore")

            val savedText = saveEditorView(editorView)
            assertEquals(expectedText, savedText)
            waitUntil("ACE EditActivity should be clean after save") {
                runOnMainSync { !editorView.isTextChanged && !editorView.saveStickyDirty }
            }
            assertEquals(expectedText, file.readText())

            switchEditorViewEngine(editorView, EditorEngineType.NATIVE, expectedText, expectedDirty = false)
            switchEditorViewEngine(editorView, EditorEngineType.ACE, expectedText, expectedDirty = false)

            val snapshot = runOnMainSync {
                (editorView.editor as AceScriptEditorEngine).createAceDiagnosticsSnapshot(
                    expectedText.length.toLong(),
                    "gate7_instrumentation",
                )
            }
            assertEquals(EditorEngineType.ACE.name, snapshot.engineType)
            assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
            assertNull(snapshot.lastFailureType)
            assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
        } finally {
            runCatching {
                (activity as? EditActivity)?.let { launched ->
                    runOnMainSync {
                        launched.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
            file.delete()
        }
    }

    private fun launchReadyEditor(lspEnabled: Boolean = false): AceCodeEditor {
        AceEditorLspPreferences.setEnabled(lspEnabled)
        val launched = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, AceEditorDebugActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        activity = launched
        instrumentation.waitForIdleSync()

        val editor = runOnMainSync {
            launched.findViewById<AceCodeEditor>(R.id.ace_editor)
        }
        assertNotNull(editor)

        waitUntil("ACE editor should report ready and first paint") {
            runOnMainSync {
                editor.isReady &&
                    editor.healthState == AceHealthState.RUNNING &&
                    editor.createDiagnosticsSnapshot().lastFirstPaintAtUptimeMillis != null
            }
        }
        return editor
    }

    private fun launchReadyEditActivity(file: File, expectedText: String): EditorView {
        EditorEnginePreferences.set(EditorEngineType.ACE)
        val launched = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, EditActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorView.EXTRA_PATH, file.absolutePath)
                .putExtra(EditorView.EXTRA_NAME, file.nameWithoutExtension),
        ) as EditActivity
        activity = launched
        instrumentation.waitForIdleSync()

        val editorView = runOnMainSync {
            launched.findViewById<EditorView>(R.id.editor_view)
        }
        assertNotNull(editorView)
        waitUntil("EditActivity should start with ACE editor") {
            runOnMainSync {
                editorView.editor.engineType == EditorEngineType.ACE &&
                    editorView.editor.isReady
            }
        }
        waitForEditorViewText(editorView, expectedText, "EditActivity should load the test file into ACE")
        waitUntil("EditActivity should start clean after file load") {
            runOnMainSync { !editorView.isTextChanged && !editorView.saveStickyDirty }
        }
        return editorView
    }

    private fun rotateAndAssertEditorState(
        activity: Activity,
        editorView: EditorView,
        requestedOrientation: Int,
        expectedOrientation: Int,
        expectedText: String,
        expectedDirty: Boolean,
    ) {
        runOnMainSync {
            activity.requestedOrientation = requestedOrientation
        }
        waitUntil("Activity should reach requested orientation $expectedOrientation") {
            runOnMainSync { activity.resources.configuration.orientation == expectedOrientation }
        }
        waitForEditorViewState(
            editorView,
            expectedText,
            expectedDirty,
            "EditActivity should preserve text and dirty state after orientation $expectedOrientation",
        )
        if (runOnMainSync { editorView.editor.engineType == EditorEngineType.ACE }) {
            assertAceCursorVisible(editorView, "ACE cursor should be visible after orientation $expectedOrientation")
        }
    }

    private fun backgroundAndRestore(activity: EditActivity) {
        val restoreIntent = Intent(activity, EditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runOnMainSync {
            activity.moveTaskToBack(true)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(BACKGROUND_RESTORE_DELAY_MS)
        instrumentation.targetContext.startActivity(restoreIntent)
        instrumentation.waitForIdleSync()
        waitUntil("EditActivity should be restored from background") {
            runOnMainSync { !activity.isFinishing && !activity.isDestroyed }
        }
    }

    private fun showImeBestEffort(editorView: EditorView) {
        runOnMainSync {
            val inputView = editorView.editor.inputView
            inputView.requestFocus()
            editorView.editor.requestEditorFocus()
            val inputMethodManager = instrumentation.targetContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            inputMethodManager.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(IME_SHOW_DELAY_MS)
    }

    private fun switchEditorViewEngine(
        editorView: EditorView,
        targetType: EditorEngineType,
        expectedText: String,
        expectedDirty: Boolean,
    ) {
        runOnMainSync {
            editorView.switchEditorEngine(targetType)
        }
        waitUntil("EditActivity should switch to $targetType") {
            runOnMainSync {
                editorView.editor.engineType == targetType && editorView.editor.isReady
            }
        }
        waitForEditorViewState(
            editorView,
            expectedText,
            expectedDirty,
            "EditActivity $targetType engine should preserve text and dirty state",
        )
        if (targetType == EditorEngineType.ACE) {
            assertAceCursorVisible(editorView, "ACE cursor should be visible after switching back to ACE")
        }
    }

    private fun assertAceCursorVisible(editorView: EditorView, message: String) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            val webView = runOnMainSync {
                if (editorView.editor.engineType == EditorEngineType.ACE) {
                    editorView.editor.inputView as? WebView
                } else {
                    null
                }
            } ?: return@waitUntil false
            val state = evaluateJsonObject(webView, ACE_CURSOR_VISIBILITY_SCRIPT)
            state.optBoolean("visible", false)
        }
    }

    private fun waitForEditorViewState(
        editorView: EditorView,
        expectedText: String,
        expectedDirty: Boolean,
        message: String,
    ) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            runOnMainSync {
                editorView.editor.textSnapshot == expectedText &&
                    editorView.isTextChanged == expectedDirty &&
                    editorView.saveStickyDirty == expectedDirty
            }
        }
    }

    private fun waitForEditorViewText(editorView: EditorView, expected: String, message: String) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            runOnMainSync { editorView.editor.textSnapshot == expected }
        }
    }

    private fun saveEditorView(editorView: EditorView): String {
        val text = AtomicReference<String>()
        val error = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        runOnMainSync {
            editorView.save().subscribe({ savedText ->
                text.set(savedText)
                latch.countDown()
            }, { throwable ->
                error.set(throwable)
                latch.countDown()
            })
        }
        assertTrue("EditActivity save should finish", latch.await(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        error.get()?.let { throw AssertionError("EditActivity save should succeed: ${it.message}", it) }
        return text.get().orEmpty()
    }

    private fun runCompletionAcceptSmoke(editor: AceCodeEditor): JSONObject {
        runOnMainSync {
            editor.webView.evaluateJavascript(COMPLETION_ACCEPT_SMOKE_SCRIPT, null)
        }
        return waitForJsonResult(editor, "window.__aceGate6CompletionResult || null")
    }

    private fun assertCompletionText(result: JSONObject, name: String, expectedPrefix: String) {
        val cases = result.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val entry = cases.getJSONObject(index)
            if (entry.getString("name") == name) {
                val text = entry.getString("text")
                assertTrue(
                    "$name completion should insert $expectedPrefix, actual=$text",
                    text.startsWith(expectedPrefix),
                )
                return
            }
        }
        fail("Missing completion result for $name")
    }

    private fun waitForJsonResult(editor: AceCodeEditor, script: String): JSONObject {
        waitUntil("ACE JavaScript smoke should finish", timeoutMillis = JS_SMOKE_TIMEOUT_MS) {
            val value = evaluateJsString(editor, script)
            value.isNotBlank() && value != "null"
        }
        return evaluateJsonObject(editor, script)
    }

    private fun evaluateJsonObject(editor: AceCodeEditor, script: String): JSONObject {
        return JSONObject(evaluateJsString(editor, script))
    }

    private fun evaluateJsonObject(webView: WebView, script: String): JSONObject {
        return JSONObject(evaluateJsString(webView, script))
    }

    private fun evaluateJsString(editor: AceCodeEditor, script: String): String {
        return evaluateJsString(editor.webView, script)
    }

    private fun evaluateJsString(webView: WebView, script: String): String {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result.set(value)
                latch.countDown()
            }
        }
        assertTrue("ACE JavaScript callback should return", latch.await(JS_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        val raw = result.get()
        if (raw.isNullOrBlank() || raw == "null") {
            return ""
        }
        return JSONArray("[$raw]").getString(0)
    }

    private fun waitForEditorText(editor: AceCodeEditor, expected: String, message: String) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            readEditorText(editor) == expected
        }
    }

    private fun readEditorText(editor: AceCodeEditor): String {
        val text = AtomicReference<String>()
        val latch = CountDownLatch(1)
        runOnMainSync {
            editor.getText { value ->
                text.set(value)
                latch.countDown()
            }
        }
        assertTrue("ACE getText callback should return", latch.await(JS_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        return text.get().orEmpty()
    }

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = READY_TIMEOUT_MS,
        intervalMillis: Long = POLL_INTERVAL_MS,
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val matched = try {
                predicate()
            } catch (error: Throwable) {
                lastError = error
                false
            }
            if (matched) {
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

    companion object {
        private const val READY_TIMEOUT_MS = 10_000L
        private const val TEXT_ROUND_TRIP_TIMEOUT_MS = 5_000L
        private const val JS_SMOKE_TIMEOUT_MS = 10_000L
        private const val JS_CALLBACK_TIMEOUT_MS = 3_000L
        private const val SAVE_TIMEOUT_MS = 10_000L
        private const val BACKGROUND_RESTORE_DELAY_MS = 500L
        private const val IME_SHOW_DELAY_MS = 500L
        private const val POLL_INTERVAL_MS = 50L
        private val ACE_CURSOR_VISIBILITY_SCRIPT = """
            (function() {
                if (!window.editor || !editor.renderer || !editor.renderer.textToScreenCoordinates) {
                    return JSON.stringify({ visible: false, reason: "missing_editor" });
                }
                var renderer = editor.renderer;
                if (renderer.scrollCursorIntoView) {
                    renderer.scrollCursorIntoView();
                }
                var position = editor.getCursorPosition();
                var coords = renderer.textToScreenCoordinates(position.row, position.column);
                var rect = editor.container.getBoundingClientRect();
                var lineHeight = renderer.lineHeight ||
                    (renderer.layerConfig && renderer.layerConfig.lineHeight) ||
                    16;
                var pageX = Number(coords && coords.pageX) || 0;
                var pageY = Number(coords && coords.pageY) || 0;
                var visible =
                    pageY >= rect.top &&
                    pageY + lineHeight <= rect.bottom &&
                    pageX >= rect.left &&
                    pageX <= rect.right + 4;
                return JSON.stringify({
                    visible: visible,
                    pageX: pageX,
                    pageY: pageY,
                    lineHeight: lineHeight,
                    top: rect.top,
                    bottom: rect.bottom,
                    left: rect.left,
                    right: rect.right
                });
            })();
        """.trimIndent()
        private val COMPLETION_ACCEPT_SMOKE_SCRIPT = """
            window.__aceGate6CompletionResult = null;
            (function() {
                var cases = [
                    { name: "toast", line: "toas", value: "toast" },
                    { name: "app.launch", line: "app.la", value: "launch" },
                    { name: "files.read", line: "files.re", value: "read" },
                    { name: "images.findImage", line: "images.find", value: "findImage" },
                    { name: "device.getBattery", line: "device.getB", value: "getBattery" }
                ];
                var results = [];
                var index = 0;

                function finish(payload) {
                    window.__aceGate6CompletionResult = JSON.stringify(payload);
                }

                function fail(test, message) {
                    finish({
                        ok: false,
                        failedCase: test && test.name,
                        message: message || "unknown completion failure",
                        cases: results
                    });
                }

                function selectedMatch(completer, expectedValue) {
                    var matches = completer &&
                        completer.completions &&
                        completer.completions.filtered || [];
                    for (var i = 0; i < matches.length; i++) {
                        var item = matches[i];
                        if ((item.value || item.caption) === expectedValue) {
                            return item;
                        }
                    }
                    return null;
                }

                function next() {
                    if (index >= cases.length) {
                        finish({ ok: true, cases: results });
                        return;
                    }
                    var test = cases[index++];
                    editor.setValue(test.line, -1);
                    editor.moveCursorTo(0, test.line.length);
                    editor.focus();
                    editor.execCommand("startAutocomplete");
                    setTimeout(function() {
                        var completer = editor.completer;
                        var match = selectedMatch(completer, test.value);
                        if (!match) {
                            fail(test, "missing completion " + test.value);
                            return;
                        }
                        completer.insertMatch(match);
                        setTimeout(function() {
                            results.push({
                                name: test.name,
                                accepted: match.value || match.caption || "",
                                text: editor.session.getValue()
                            });
                            next();
                        }, 80);
                    }, 250);
                }

                try {
                    next();
                } catch (error) {
                    fail(cases[index] || null, String(error && error.message || error));
                }
            })();
            true;
        """.trimIndent()
    }
}
