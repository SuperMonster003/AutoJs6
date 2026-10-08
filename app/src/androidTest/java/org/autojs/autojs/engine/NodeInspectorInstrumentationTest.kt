package org.autojs.autojs.engine

import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.R
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Roadmap M10.2: localhost-only Inspector in Debug and signed Release hosts. */
@RunWith(AndroidJUnit4::class)
class NodeInspectorInstrumentationTest {

    private lateinit var workingDirectory: File
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(NodePluginTestHarness.targetContext)
    private val inspectorKey get() = NodePluginTestHarness.targetContext.getString(R.string.key_nodejs_inspector_enabled)
    private var originalInspectorSetting: Boolean? = null

    @Before
    fun setUp() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node inspector instrumentation")
        originalInspectorSetting = if (preferences.contains(inspectorKey)) preferences.getBoolean(inspectorKey, false) else null
        check(preferences.edit().putBoolean(inspectorKey, false).commit())
        workingDirectory = File(
            NodePluginTestHarness.targetContext.cacheDir,
            "node-inspector-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        if (::workingDirectory.isInitialized) {
            workingDirectory.deleteRecursively()
            val editor = preferences.edit()
            originalInspectorSetting?.let { editor.putBoolean(inspectorKey, it) } ?: editor.remove(inspectorKey)
            check(editor.commit())
        }
    }

    @Test
    fun inspectorStaysInactiveWithoutPermission() {
        val result = NodePluginTestHarness.runPluginScript(
            source = """
                const assert=require('node:assert/strict'), inspector=require('node:inspector');
                assert.equal(inspector.url(), undefined);
                assert.throws(()=>inspector.open(0), e=>e.autojs6Code==='ERR_AUTOJS6_BUILTIN_DISABLED');
                console.log('inspector.inactive=true');
            """.trimIndent(),
            sourceName = "inspector-inactive.cjs",
            workingDirectory = workingDirectory,
            inspectorEnabled = false,
        )
        NodePluginTestHarness.assertSucceeded("inspector inactive", result)
        assertTrue(result.stdout.contains("inspector.inactive=true"))
    }

    @Test
    fun developerPreferenceAppliesToOrdinaryRequestsAndExplicitFalseOverridesIt() {
        val request = NodePluginScriptRequest(
            source = "console.log('inspector.enabled='+require('autojs6:profile').inspector);",
            sourceName = File(workingDirectory, "preference.cjs").absolutePath,
            workingDirectory = workingDirectory.absolutePath,
        )
        for (enabled in listOf(false, true)) {
            check(preferences.edit().putBoolean(inspectorKey, enabled).commit())
            val result = NodeJsRuntimePluginBridge.runScript(NodePluginTestHarness.targetContext, request)
            NodePluginTestHarness.assertSucceeded("inspector preference=$enabled", result)
            assertTrue(result.stdout.contains("inspector.enabled=$enabled"))
        }
        val result = NodeJsRuntimePluginBridge.runScript(NodePluginTestHarness.targetContext, request.copy(inspectorEnabled=false))
        NodePluginTestHarness.assertSucceeded("explicit Inspector opt-out", result)
        assertTrue(result.stdout.contains("inspector.enabled=false"))
    }

    @Test
    fun localhostCdpSessionHitsAndResumesABreakpoint() {
        check(preferences.edit().putBoolean(inspectorKey, true).commit())
        val externalClient = InstrumentationRegistry.getArguments().getString("nodeInspectorExternalClient") == "true"
        val timeoutMs = if (externalClient) 150_000L else 25_000L
        val endpointReady = CountDownLatch(1)
        val endpoint = AtomicReference<String>()
        val streamed = StringBuilder()
        val sourceName = "inspector-breakpoint.cjs"
        val executor = Executors.newSingleThreadExecutor()
        var execution: Future<NodePluginScriptResult>? = null
        var webSocket: WebSocket? = null
        try {
            execution = executor.submit<NodePluginScriptResult> {
                NodeJsRuntimePluginBridge.runScript(
                    NodePluginTestHarness.targetContext,
                    NodePluginScriptRequest(
                        source = inspectorBreakpointSource(timeoutMs),
                        sourceName = File(workingDirectory, sourceName).absolutePath,
                        workingDirectory = workingDirectory.absolutePath,
                        timeoutMs = timeoutMs,
                    ),
                    outputListener = { _, text ->
                        synchronized(streamed) {
                            streamed.append(text)
                            Regex("inspector.endpoint=(ws://127\\.0\\.0\\.1:[0-9]+/[^\\s]+)")
                                .find(streamed)?.groupValues?.get(1)?.let {
                                    endpoint.set(it)
                                    endpointReady.countDown()
                                }
                        }
                    },
                )
            }
            assertTrue("Inspector URL was not streamed: $streamed", endpointReady.await(15, TimeUnit.SECONDS))
            val inspectorUrl = endpoint.get()
            assertTrue("Inspector must bind IPv4 loopback only: $inspectorUrl", inspectorUrl.startsWith("ws://127.0.0.1:"))
            if (externalClient) {
                Log.i("NodeInspectorTest", "inspector.endpoint=$inspectorUrl")
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("inspectorUrl", inspectorUrl)
                })
            } else {
                val debuggerEnabled = CountDownLatch(1)
                val paused = CountDownLatch(1)
                val resumed = CountDownLatch(1)
                val failure = AtomicReference<Throwable?>()
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("{\"id\":1,\"method\":\"Runtime.enable\"}")
                        webSocket.send("{\"id\":2,\"method\":\"Debugger.enable\"}")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching {
                            val message = JSONObject(text)
                            when {
                                message.optInt("id") == 2 -> {
                                    webSocket.send("""{"id":4,"method":"Runtime.evaluate","params":{"expression":"globalThis.__autojsInspectorReady=true"}}""")
                                    debuggerEnabled.countDown()
                                }
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
                                    webSocket.send("{\"id\":3,\"method\":\"Debugger.resume\"}")
                                    paused.countDown()
                                }
                            }
                        }.onFailure { failure.compareAndSet(null, it) }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        failure.compareAndSet(null, t)
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
                assertTrue("Debugger.paused was not observed", paused.await(10L, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("CDP pause failed", it) }
                assertTrue("Debugger.resume was not acknowledged", resumed.await(5L, TimeUnit.SECONDS))
                // Node close() waits for clients to detach before the script can finish.
                webSocket.close(1000, "breakpoint resumed")
            }
            val result = execution.get(timeoutMs, TimeUnit.MILLISECONDS)
            NodePluginTestHarness.assertSucceeded("inspector breakpoint", result)
            assertTrue(result.stdout.contains("inspector.non-loopback-denied=true"))
            assertTrue(result.stdout.contains("inspector.wait-denied=true"))
            assertTrue(result.stdout.contains("inspector.breakpoint.resumed=true"))
            assertTrue(result.stdout.contains("inspector.release-allowed=true"))
        } finally {
            webSocket?.close(1000, "test complete")
            execution?.cancel(true)
            executor.shutdownNow()
        }
    }

    private fun inspectorBreakpointSource(timeoutMs: Long): String = """
        "nodejs";
        const inspector = require("inspector");
        const profile = require("autojs6:profile");
        let nonLoopbackDenied = false, waitDenied = false;
        try { inspector.open(0, "0.0.0.0", false); } catch (error) {
          nonLoopbackDenied = error && error.code === "ERR_INVALID_ARG_VALUE";
        }
        try { inspector.open(0, "127.0.0.1", true); } catch (error) {
          waitDenied = error && error.code === "ERR_INVALID_ARG_VALUE";
        }
        console.log("inspector.non-loopback-denied=" + nonLoopbackDenied);
        console.log("inspector.wait-denied=" + waitDenied);
        console.log("inspector.release-allowed=" + (
          profile.featureFlags.inspector.enabled === true &&
          profile.featureFlags.inspector.debugOnly === false &&
          profile.featureFlags.inspector.releaseForbidden === false
        ));
        inspector.open(0, "localhost", false);
        console.log("inspector.endpoint=" + inspector.url());
        const deadline = Date.now() + $timeoutMs - 2000;
        (function awaitDebugger() {
          if (globalThis.__autojsInspectorReady) {
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
