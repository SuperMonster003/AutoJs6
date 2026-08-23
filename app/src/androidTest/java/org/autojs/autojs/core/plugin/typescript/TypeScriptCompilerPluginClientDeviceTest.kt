package org.autojs.autojs.core.plugin.typescript

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeScriptCompilerPluginClientDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compilesUtf8SingleFileThroughRealPluginBinder() {
        withSource(
            name = "你好.ts",
            source = """
                const message: string = "host-client-ok"
                toast(message)
            """.trimIndent(),
        ) { source ->
            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(source)
            }

            assertTrue(output is TypeScriptCompilationOutput.Emitted)
            output as TypeScriptCompilationOutput.Emitted
            assertTrue(output.javaScript.contains("host-client-ok"))
            assertTrue(output.sourceMap.contains("\"version\":3"))
            assertTrue(output.entryModulePath.endsWith("你好.js"))
            assertFalse(output.diagnostics.any(TypeScriptDiagnostic::isError))
        }
    }

    @Test
    fun returnsBoundedDiagnosticsWithoutExecutableOutput() {
        withSource(
            name = "diagnostic.ts",
            source = "const count: number = \"not-a-number\"",
        ) { source ->
            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(source)
            }

            assertTrue(output is TypeScriptCompilationOutput.Diagnostics)
            output as TypeScriptCompilationOutput.Diagnostics
            assertEquals(1, output.diagnostics.count(TypeScriptDiagnostic::isError))
            assertTrue(
                output.diagnostics.any { diagnostic ->
                    diagnostic.category == TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR &&
                        diagnostic.uri?.endsWith("/diagnostic.ts") == true &&
                        diagnostic.range != null
                },
            )
        }
    }

    @Test
    fun routesCompiledTypeScriptIntoRhinoExecution() {
        val markerKey = "autojs6.typescript.execution.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withSource(
                name = "hello.ts",
                source = """
                    declare const java: any
                    const message: string = "TypeScript says hello"
                    toast(message)
                    java.lang.System.setProperty("$markerKey", "executed")
                """.trimIndent(),
            ) { source ->
                val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                assertTrue(launch.source is TypeScriptFileSource)
                val terminal = executeAndAwait(launch.source, source.parent.orEmpty())

                terminal.failure.get()?.let { error ->
                    throw AssertionError("Compiled TypeScript execution failed", error)
                }
                assertTrue(terminal.succeeded.get())
                assertTrue(terminal.execution.engine is LoopBasedJavaScriptEngine)
                assertEquals("executed", System.getProperty(markerKey))
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun typeErrorsNeverFallBackToRawRhinoExecution() {
        val markerKey = "autojs6.typescript.rejected.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withSource(
                name = "rejected.ts",
                source = """
                    declare const java: any
                    const count: number = "not-a-number"
                    java.lang.System.setProperty("$markerKey", "must-not-run")
                """.trimIndent(),
            ) { source ->
                val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                val terminal = executeAndAwait(launch.source, source.parent.orEmpty())
                val failure = terminal.failure.get()

                assertFalse(terminal.succeeded.get())
                assertTrue(failure is TypeScriptDiagnosticsException)
                assertTrue(failure?.message?.contains("TS2322") == true)
                assertEquals(null, System.getProperty(markerKey))
                assertEquals(null, terminal.execution.engine)
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    private fun withSource(name: String, source: String, block: (File) -> Unit) {
        val root = File(context.cacheDir, "typescript-client-${System.nanoTime()}")
        check(root.mkdirs())
        try {
            val sourceFile = File(root, name)
            sourceFile.writeText(source, Charsets.UTF_8)
            block(sourceFile)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun executeAndAwait(
        source: org.autojs.autojs.script.ScriptSource,
        workingDirectory: String,
    ): ExecutionTerminal {
        val completed = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                succeeded.set(true)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            source,
            listener,
            ExecutionConfig(workingDirectory = workingDirectory),
        )
        assertTrue(
            "TypeScript execution did not reach a terminal state",
            completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        return ExecutionTerminal(execution, succeeded, failure)
    }

    private data class ExecutionTerminal(
        val execution: ScriptExecution,
        val succeeded: AtomicBoolean,
        val failure: AtomicReference<Throwable>,
    )

    private companion object {
        const val EXECUTION_TIMEOUT_SECONDS = 90L
    }
}
