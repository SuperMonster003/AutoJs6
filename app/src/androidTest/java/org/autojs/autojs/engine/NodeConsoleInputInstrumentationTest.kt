package org.autojs.autojs.engine

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs6.R
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeConsoleInputInstrumentationTest {
    @Test
    fun consoleQueueSendsReadlineInputAndRemovesPromptAfterClose() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var global: ConsoleImpl
        lateinit var console: ConsoleImpl
        lateinit var view: ConsoleView
        val activity = instrumentation.startActivitySync(
            Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        instrumentation.runOnMainSync {
            global = ConsoleImpl(UiHandler(context))
            console = ConsoleImpl(UiHandler(context), global)
            view = ConsoleView(activity).apply { setConsole(console) }
            activity.setContentView(view)
        }
        val id = "console-input-${UUID.randomUUID()}"
        val ready = Semaphore(0)
        val input = NodeRuntimeConsoleInput(console) { message: Bundle ->
            NodeJsRuntimePluginBridge.postMessage(id, message)
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<NodePluginScriptResult> {
                NodeJsRuntimePluginBridge.runScript(
                    context,
                    NodePluginScriptRequest(
                        source = """
                            (async () => {
                              const rl = require('node:readline/promises').createInterface({ input: process.stdin, output: process.stdout });
                              const lines = [];
                              for (let i = 0; i < 3; ++i) lines.push(await rl.question('Line? '));
                              console.log('console.answer=' + JSON.stringify(lines)); rl.close();
                            })();
                        """.trimIndent(),
                        sourceName = "console-input.cjs",
                        workingDirectory = context.cacheDir.absolutePath,
                        executionId = id,
                        timeoutMs = 20_000,
                    ),
                    stdinListener = { waiting ->
                        input.onState(waiting)
                        if (waiting) ready.release()
                    },
                )
            }
            for ((index, line) in listOf("控制台 🌏", "", "  space  ").withIndex()) {
                assertTrue("readline did not request line $index", ready.tryAcquire(15, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(1, console.pendingInputCount)
                    assertEquals(1, global.pendingInputCount)
                    assertEquals(View.VISIBLE, view.findViewById<View>(R.id.input_container).visibility)
                    if (index == 0) {
                        assertTrue(global.submitInput(line))
                    } else {
                        view.findViewById<EditText>(R.id.input).setText(line)
                        assertTrue(view.findViewById<View>(R.id.submit).performClick())
                    }
                }
            }
            val result = future.get(20, TimeUnit.SECONDS)
            NodePluginTestHarness.assertSucceeded("console stdin", result)
            assertTrue(result.stdout, result.stdout.contains("console.answer=[\"控制台 🌏\",\"\",\"  space  \"]"))
            input.close()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertFalse(console.hasPendingInput)
                assertFalse(global.hasPendingInput)
                assertEquals(View.GONE, view.findViewById<View>(R.id.input_container).visibility)
            }
            assertFalse(NodeJsRuntimePluginBridge.postMessage(id, Bundle()))
        } finally {
            input.close()
            NodeJsRuntimePluginBridge.cancelScript(context, id)
            executor.shutdownNow()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun closingInputCancelsOnlyItsOwnConsoleRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val global = ConsoleImpl(UiHandler(context))
        var sent = false
        val input = NodeRuntimeConsoleInput(global) { sent = true; true }
        val other = global.requestInput { }
        input.onState(true)
        assertEquals(2, global.pendingInputCount)
        input.close()
        assertEquals(1, global.pendingInputCount)
        assertFalse(other.isDone)
        other.cancel()
        assertFalse(global.hasPendingInput)
        input.onState(true)
        assertFalse(global.hasPendingInput)
        assertFalse(sent)
    }
}
