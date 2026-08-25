package org.autojs.autojs.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.runner.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Roadmap M10.2: localhost-only, explicit, debug-build Node inspector proof. */
@RunWith(AndroidJUnit4::class)
class NodeInspectorInstrumentationTest {

    private lateinit var workingDirectory: File

    @Before
    fun setUp() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node inspector instrumentation")
        workingDirectory = File(
            NodePluginTestHarness.targetContext.cacheDir,
            "node-inspector-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        if (::workingDirectory.isInitialized) {
            workingDirectory.deleteRecursively()
        }
    }

    @Test
    fun inspectorIsDeniedWithoutTheExplicitRequest() {
        val result = NodePluginTestHarness.runPluginScript(
            source = "require('inspector'); console.log('unexpected');",
            sourceName = "inspector-denied.cjs",
            workingDirectory = workingDirectory,
            inspectorEnabled = false,
        )

        assertFalse(NodePluginTestHarness.describeResult("inspector denied", result), result.succeeded)
        assertEquals("ERR_AUTOJS6_BUILTIN_DISABLED", result.errorCode)
    }

    @Test
    fun localhostCdpSessionHitsAndResumesABreakpoint() {
        val urlFile = File(workingDirectory, "inspector-url.txt")
        val readyFile = File(workingDirectory, "debugger-ready.txt")
        val sourceName = "inspector-breakpoint.cjs"
        val executor = Executors.newSingleThreadExecutor()
        var execution: Future<NodePluginScriptResult>? = null
        var webSocket: WebSocket? = null
        try {
            execution = executor.submit<NodePluginScriptResult> {
                NodePluginTestHarness.runPluginScript(
                    source = inspectorBreakpointSource(workingDirectory),
                    sourceName = sourceName,
                    workingDirectory = workingDirectory,
                    timeoutMs = 25_000L,
                    inspectorEnabled = true,
                )
            }

            waitForFile(urlFile, execution, 10_000L)
            val inspectorUrl = urlFile.readText().trim()
            assertTrue("Inspector must bind IPv4 loopback only: $inspectorUrl", inspectorUrl.startsWith("ws://127.0.0.1:"))

            val debuggerEnabled = CountDownLatch(1)
            val paused = CountDownLatch(1)
            val resumed = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val listener = object : WebSocketListener() {
                override fun onOpen(socket: WebSocket, response: Response) {
                    socket.send("{\"id\":1,\"method\":\"Runtime.enable\"}")
                    socket.send("{\"id\":2,\"method\":\"Debugger.enable\"}")
                }

                override fun onMessage(socket: WebSocket, text: String) {
                    runCatching {
                        val message = JSONObject(text)
                        when {
                            message.optInt("id") == 2 -> debuggerEnabled.countDown()
                            message.optInt("id") == 3 -> resumed.countDown()
                            message.optString("method") == "Debugger.paused" -> {
                                val frames = message
                                    .getJSONObject("params")
                                    .getJSONArray("callFrames")
                                val sourceSeen = (0 until frames.length()).any { index ->
                                    val frame = frames.getJSONObject(index)
                                    frame.optString("url").contains(sourceName) ||
                                        frame.optString("functionName") == "breakpointTarget"
                                }
                                check(sourceSeen) { "Paused event did not contain $sourceName: $message" }
                                socket.send("{\"id\":3,\"method\":\"Debugger.resume\"}")
                                paused.countDown()
                            }
                        }
                    }.onFailure { failure.compareAndSet(null, it) }
                }

                override fun onFailure(socket: WebSocket, error: Throwable, response: Response?) {
                    failure.compareAndSet(null, error)
                    debuggerEnabled.countDown()
                    paused.countDown()
                    resumed.countDown()
                }
            }
            val client = OkHttpClient.Builder()
                .readTimeout(0L, TimeUnit.MILLISECONDS)
                .build()
            webSocket = client.newWebSocket(Request.Builder().url(inspectorUrl).build(), listener)

            assertTrue("Debugger.enable did not complete", debuggerEnabled.await(8L, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("CDP setup failed", it) }
            readyFile.writeText("ready")
            assertTrue("Debugger.paused was not observed", paused.await(10L, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("CDP pause failed", it) }
            assertTrue("Debugger.resume was not acknowledged", resumed.await(5L, TimeUnit.SECONDS))

            val result = execution.get(15L, TimeUnit.SECONDS)
            NodePluginTestHarness.assertSucceeded("inspector breakpoint", result)
            assertTrue(result.stdout.contains("inspector.non-loopback-denied=true"))
            assertTrue(result.stdout.contains("inspector.wait-denied=true"))
            assertTrue(result.stdout.contains("inspector.breakpoint.resumed=true"))
            assertTrue(result.stdout.contains("inspector.debug-only=true"))
        } finally {
            webSocket?.close(1000, "test complete")
            execution?.cancel(true)
            executor.shutdownNow()
        }
    }

    private fun waitForFile(
        file: File,
        execution: Future<NodePluginScriptResult>,
        timeoutMs: Long,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (file.isFile && file.length() > 0L) return
            if (execution.isDone) {
                val result = execution.get()
                throw AssertionError(NodePluginTestHarness.describeResult("inspector endpoint startup", result))
            }
            Thread.sleep(25L)
        }
        throw AssertionError("Timed out waiting for ${file.absolutePath}")
    }

    private fun inspectorBreakpointSource(workingDirectory: File): String {
        val urlPath = JSONObject.quote(File(workingDirectory, "inspector-url.txt").absolutePath)
        val readyPath = JSONObject.quote(File(workingDirectory, "debugger-ready.txt").absolutePath)
        return """
        "nodejs";
        const fs = require("fs");
        const inspector = require("inspector");
        const profile = require("autojs6:profile");
        let nonLoopbackDenied = false;
        let waitDenied = false;
        try { inspector.open(0, "0.0.0.0", false); } catch (error) {
          nonLoopbackDenied = error && error.code === "ERR_INVALID_ARG_VALUE";
        }
        try { inspector.open(0, "127.0.0.1", true); } catch (error) {
          waitDenied = error && error.code === "ERR_INVALID_ARG_VALUE";
        }
        console.log("inspector.non-loopback-denied=" + nonLoopbackDenied);
        console.log("inspector.wait-denied=" + waitDenied);
        console.log("inspector.debug-only=" + (
          profile.featureFlags.inspector.enabled === true &&
          profile.featureFlags.inspector.debugOnly === true &&
          profile.featureFlags.inspector.releaseForbidden === true
        ));
        inspector.open(0, "localhost", false);
        const url = inspector.url();
        if (!url || url.indexOf("ws://127.0.0.1:") !== 0) throw new Error("bad inspector URL: " + url);
        fs.writeFileSync($urlPath, url, "utf8");
        const readyFile = $readyPath;
        const deadline = Date.now() + 10000;
        (function awaitDebugger() {
          if (fs.existsSync(readyFile)) {
            (function breakpointTarget() { debugger; })();
            console.log("inspector.breakpoint.resumed=true");
            setTimeout(function() { inspector.close(); }, 50);
            return;
          }
          if (Date.now() >= deadline) throw new Error("Debugger client did not become ready");
          setTimeout(awaitDebugger, 25);
        })();
    """.trimIndent()
    }
}
