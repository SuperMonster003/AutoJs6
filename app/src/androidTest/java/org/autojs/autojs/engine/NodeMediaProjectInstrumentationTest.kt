package org.autojs.autojs.engine

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.ui.log.LogActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Runs the plugin-owned samples supplied with -e mediaProjectRoot <directory>. */
class NodeMediaProjectInstrumentationTest {

    @Test
    fun mediaLibraryProjectCompletesWithItsActualEntryPath() = runProject("media-library")

    @Test
    fun mediaPlaybackProjectCompletesWithItsActualEntryPath() = runProject("media-playback")

    private fun runProject(name: String) {
        val sampleRoot = InstrumentationRegistry.getArguments().getString("mediaProjectRoot")
        assumeNotNull(sampleRoot)
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node media project acceptance")
        val projectDir = File(checkNotNull(sampleRoot), name)
        val entry = File(projectDir, "main.cjs")
        assertTrue("Missing sample entry: $entry", entry.isFile)
        assertEquals(entry.canonicalFile, NodeProjectRunner.resolveProjectEntryFileIfNodeProject(projectDir)?.canonicalFile)
        val launch = ScriptLaunchSourceFactory.createProjectEntryLaunch(projectDir, entry)
        assertEquals(entry.absolutePath, launch.source.elegantPath)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val service = AutoJs.instance.scriptEngineService
        val console = service.globalConsole as ConsoleImpl
        val firstLogId = console.snapshotLogEntries().lastOrNull()?.id ?: -1
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = service.execute(launch.source, listener, launch.createExecutionConfig())
        try {
            assertTrue("Timed out running $name", completed.await(90, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("$name failed", it) }
            val engine = execution.engine as NodePluginJavaScriptEngine
            val result = requireNotNull(engine.lastPluginScriptResult)
            NodePluginTestHarness.assertSucceeded(name, result)
            File(projectDir, "expected-output.txt").readLines().filter { it.isNotBlank() }.forEach { expected ->
                assertTrue("Missing '$expected'\n${result.stdout}", result.stdout.contains(expected))
            }
            // Both lifecycle messages must name the actual .cjs entry, even when launched as a project.
            val deadline = System.currentTimeMillis() + 5_000
            var lifecycleLogs: List<String>
            do {
                lifecycleLogs = console.snapshotLogEntries().filter { it.id > firstLogId }
                    .map { it.content.toString() }.filter { it.contains("[${entry.absolutePath}]") }
                if (lifecycleLogs.size >= 2) break
                Thread.sleep(50)
            } while (System.currentTimeMillis() < deadline)
            assertEquals(lifecycleLogs.joinToString("\n"), 2, lifecycleLogs.size)
        } finally {
            if (completed.count != 0L) execution.engine?.forceStop()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
