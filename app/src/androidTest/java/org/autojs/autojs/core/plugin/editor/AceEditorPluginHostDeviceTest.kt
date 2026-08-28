package org.autojs.autojs.core.plugin.editor

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorProjectSnapshotProvider
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.editor.api.EditorPluginCallback
import org.autojs.plugin.editor.api.EditorPluginContract
import org.autojs.plugin.editor.api.EditorPluginFailure
import org.autojs.plugin.editor.api.EditorPluginSessionConfig
import org.json.JSONArray
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Cross-APK smoke test. It is skipped when the Ace editor plugin is not installed. */
class AceEditorPluginHostDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun installedAceEditorPluginLoadsAndRunsThroughHostAbi() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())

        val ready = CountDownLatch(1)
        val fatalFailure = AtomicReference<EditorPluginFailure>()
        val loaded = AtomicReference<AceEditorPluginHost.LoadedSession>()
        val activity = launchHostActivity()
        try {
            runOnMainSync {
                loaded.set(
                    AceEditorPluginHost.createSession(
                        context = activity,
                        config = EditorPluginSessionConfig(
                            hostPackageName = context.packageName,
                            hostVersionName = BuildConfig.VERSION_NAME,
                            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                            storageDirectoryPath = File(context.cacheDir, "ace-editor-plugin-smoke-fonts").absolutePath,
                        ),
                        callback = object : EditorPluginCallback {
                            override fun onReady(state: org.autojs.plugin.editor.api.EditorPluginState) {
                                ready.countDown()
                            }

                            override fun onFailure(failure: EditorPluginFailure) {
                                if (failure.fatal) {
                                    fatalFailure.compareAndSet(null, failure)
                                    ready.countDown()
                                }
                            }
                        },
                    ),
                )
                assertFalse(loaded.get().session.view.isAttachedToWindow)
                activity.setContentView(loaded.get().session.view)
            }

            assertTrue("Ace editor plugin did not become ready", ready.await(20, TimeUnit.SECONDS))
            fatalFailure.get()?.let { failure ->
                fail("Ace editor plugin failed: ${failure.type}: ${failure.message}")
            }

            val loadedSession = loaded.get()
            runOnMainSync {
                assertEquals(EditorPluginContract.DEFAULT_PACKAGE_NAME, loadedSession.plugin.candidate.serviceInfo.packageName)
                assertEquals(EditorPluginContract.VERSION, loadedSession.plugin.entrypoint.contractVersion)
                assertNotSame(EditorPluginCallback::class.java.classLoader, loadedSession.plugin.classLoader)
                assertEquals(loadedSession.plugin.classLoader, loadedSession.plugin.entrypoint.javaClass.classLoader)
                assertTrue(loadedSession.session.isReady)
                loadedSession.session.setInitialText(SMOKE_TEXT)
            }
            waitUntil("Ace editor plugin did not mirror host text") {
                runOnMainSync { loadedSession.session.textSnapshot == SMOKE_TEXT }
            }
        } finally {
            loaded.get()?.let { loadedSession ->
                runOnMainSync {
                    loadedSession.destroy()
                    loadedSession.destroy()
                }
            }
            finishHostActivity(activity)
        }
    }

    @Test
    fun installedAceEditorConsumesHostSnapshotForLiveProjectDiagnostics() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())

        val project = File(context.cacheDir, "ace-editor-host-project-diagnostics").apply {
            deleteRecursively()
            mkdirs()
        }
        File(project, "tsconfig.json").writeText("{}")
        val sourceText =
            "import { shared } from './shared'; import { absent } from './absent'; " +
                "const answer: 42 = shared; void absent; void answer;"
        val document = File(project, "main.ts").apply { writeText(sourceText) }
        File(project, "shared.ts").writeText("export const shared = 42 as const;")
        val documentPath = document.canonicalPath
        val providerSnapshot = requireNotNull(
            TypeScriptEditorProjectSnapshotProvider.captureRequired(documentPath),
        )
        assertEquals(2, providerSnapshot.sourceFileCount)

        val ready = CountDownLatch(1)
        val fatalFailure = AtomicReference<EditorPluginFailure>()
        val loaded = AtomicReference<AceEditorPluginHost.LoadedSession>()
        val activity = launchHostActivity()
        try {
            runOnMainSync {
                loaded.set(
                    AceEditorPluginHost.createSession(
                        context = activity,
                        config = EditorPluginSessionConfig(
                            hostPackageName = context.packageName,
                            hostVersionName = BuildConfig.VERSION_NAME,
                            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                            storageDirectoryPath =
                                File(context.cacheDir, "ace-editor-host-project-fonts").absolutePath,
                            documentPath = documentPath,
                            projectSnapshotProvider = TypeScriptEditorProjectSnapshotProvider,
                        ),
                        callback = object : EditorPluginCallback {
                            override fun onReady(state: org.autojs.plugin.editor.api.EditorPluginState) {
                                ready.countDown()
                            }

                            override fun onFailure(failure: EditorPluginFailure) {
                                if (failure.fatal) {
                                    fatalFailure.compareAndSet(null, failure)
                                    ready.countDown()
                                }
                            }
                        },
                    ),
                )
                activity.setContentView(loaded.get().session.view)
            }

            assertTrue(
                "Ace project diagnostic session did not become ready",
                ready.await(20, TimeUnit.SECONDS),
            )
            fatalFailure.get()?.let { failure ->
                fail("Ace editor plugin failed: ${failure.type}: ${failure.message}")
            }
            runOnMainSync {
                assertEquals(2, loaded.get().plugin.entrypoint.contractVersion)
                loaded.get().session.setInitialText(sourceText)
            }

            var diagnosticCodes = emptyList<String>()
            val deadline = SystemClock.uptimeMillis() + 30_000L
            while (SystemClock.uptimeMillis() < deadline && "2307" !in diagnosticCodes) {
                val raw = evaluateJavascript(
                    loaded.get().session.inputView as WebView,
                    "JSON.stringify(window.AutoJsAce && window.AutoJsAce.validateLsp " +
                        "? window.AutoJsAce.validateLsp() : [])",
                )
                val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
                    .getOrNull().orEmpty()
                val diagnostics = runCatching { JSONArray(decoded) }.getOrElse { JSONArray() }
                diagnosticCodes = List(diagnostics.length()) { index ->
                    diagnostics.getJSONObject(index).optString("code")
                }
                if ("2307" !in diagnosticCodes) SystemClock.sleep(100L)
            }

            assertEquals(
                "Host-backed project diagnostic golden changed",
                listOf("2307"),
                diagnosticCodes.sorted(),
            )
        } finally {
            loaded.get()?.let { loadedSession ->
                runOnMainSync { loadedSession.destroy() }
            }
            finishHostActivity(activity)
            project.deleteRecursively()
        }
    }

    private fun launchHostActivity(): Activity {
        val intent = Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return instrumentation.startActivitySync(intent).also {
            instrumentation.waitForIdleSync()
        }
    }

    private fun finishHostActivity(activity: Activity) {
        runOnMainSync {
            if (!activity.isDestroyed) {
                activity.finishAndRemoveTask()
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Suppress("DEPRECATION")
    private fun isPluginInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(EditorPluginContract.DEFAULT_PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = 10_000L,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) {
                return
            }
            SystemClock.sleep(50L)
        }
        fail(message)
    }

    private fun evaluateJavascript(webView: WebView, script: String): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        runOnMainSync {
            webView.evaluateJavascript(script) { raw ->
                result.set(raw)
                completed.countDown()
            }
        }
        assertTrue("Ace JavaScript evaluation timed out", completed.await(5, TimeUnit.SECONDS))
        return result.get().orEmpty()
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
        private const val SMOKE_TEXT = "const aceEditorPluginSmoke = true;\n"
    }
}
