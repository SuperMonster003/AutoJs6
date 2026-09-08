package org.autojs.autojs.engine

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeAsyncExecutionInstrumentationTest {
    @Test fun threeHostScriptsUseAsyncAdmissionAndCommitWorkspacesOnlyAfterCompletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        NodeJsRuntimePluginHost.invalidateForTest(context)
        val info = runBlocking { NodeJsRuntimePluginHost.probe(context) }
        assertTrue(info.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES).orEmpty()
            .contains(NodeJsRuntimeContract.CAPABILITY_ASYNC_SCRIPT_EXECUTION))
        val root = File(context.cacheDir, "node-async-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val ids = List(3) { "host-async-${UUID.randomUUID()}" }
        val directories = List(3) { File(root, "$it").apply {
            check(mkdirs())
            File(this, "project.json").writeText("{}") // Declares the full project tree as execution input.
        } }
        val outputs = List(3) { CountDownLatch(1) }
        val executor = Executors.newFixedThreadPool(3)
        try {
            val futures = (0..2).map { index ->
                val future = executor.submit<NodePluginScriptResult> {
                    NodeJsRuntimePluginBridge.runScript(context, NodePluginScriptRequest(
                        source = """require('fs').writeFileSync('answer.txt', 'answer=$index'); console.log('ready'); setInterval(() => {}, 1000);""",
                        sourceName = "<node-async-$index.js>",
                        workingDirectory = directories[index].absolutePath,
                        sandboxRoot = directories[index].absolutePath,
                        executionId = ids[index],
                        timeoutMs = 180_000L,
                    ), outputListener = { type, text ->
                        if (type == NodeJsRuntimeContract.EVENT_STDOUT && text.contains("ready")) outputs[index].countDown()
                    })
                }
                if (index < 2) assertTrue("host script $index did not start", outputs[index].await(25, TimeUnit.SECONDS))
                future
            }
            val deadline = SystemClock.elapsedRealtime() + 20_000
            var state: Bundle
            do {
                state = runBlocking { NodeJsRuntimePluginHost.probe(context) }
                if (state.getInt("queuedExecutions") == 1) break
                SystemClock.sleep(20)
            } while (SystemClock.elapsedRealtime() < deadline)
            assertEquals(1, state.getInt("queuedExecutions"))
            assertEquals(1L, outputs[2].count)
            directories.forEach { assertFalse("archive committed while script is still running", File(it, "answer.txt").exists()) }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "\nm16.asyncHost=READY dispatcherPid=${state.getInt("dispatcherPid")} queued=1\n")
            })
            // Optional bounded window for an external JDWP thread snapshot; no runtime test hook.
            val holdMs = InstrumentationRegistry.getArguments().getString("asyncHoldMs")?.toLongOrNull()?.coerceIn(0, 90_000) ?: 0
            if (holdMs > 0) SystemClock.sleep(holdMs)
            assertTrue(NodeJsRuntimePluginBridge.cancelScript(context, ids[2]))
            val queued = futures[2].get(20, TimeUnit.SECONDS)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED, queued.errorCode)
            for (index in 0..1) {
                assertTrue(NodeJsRuntimePluginBridge.cancelScript(context, ids[index]))
                val result = futures[index].get(20, TimeUnit.SECONDS)
                assertEquals(result.errorMessage, NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED, result.errorCode)
                assertEquals("answer=$index", File(directories[index], "answer.txt").readText())
            }
            assertFalse(File(directories[2], "answer.txt").exists())
            val quick = NodeJsRuntimePluginBridge.runScript(context, NodePluginScriptRequest(
                source = "console.log(require('fs').readFileSync('answer.txt', 'utf8'))",
                sourceName = "<node-async-readback.js>", workingDirectory = directories[0].absolutePath,
                sandboxRoot = directories[0].absolutePath, timeoutMs = 20_000,
            ))
            assertTrue(quick.errorMessage, quick.succeeded)
            assertTrue(quick.stdout, quick.stdout.contains("answer=0"))
        } finally {
            ids.forEach { NodeJsRuntimePluginBridge.cancelScript(context, it) }
            executor.shutdownNow()
            directories.forEach { dir -> dir.listFiles()?.forEach { it.delete() }; dir.delete() }
            root.delete()
        }
    }
}
