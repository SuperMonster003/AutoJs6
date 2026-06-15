package org.autojs.autojs.ui.edit.editor.ace

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AceCodeEditorSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: Activity? = null

    @After
    fun tearDown() {
        AceEditorTestHooks.reset()
        activity?.let { launched ->
            runOnMainSync {
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

    private fun launchReadyEditor(): AceCodeEditor {
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
        private const val JS_CALLBACK_TIMEOUT_MS = 3_000L
        private const val POLL_INTERVAL_MS = 50L
    }
}
