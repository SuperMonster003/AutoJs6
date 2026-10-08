package org.autojs.autojs.core.plugin.typescript

import android.Manifest
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.AppOps
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.console.StackFrameParser
import org.autojs.autojs.core.image.capture.ScreenCapturerForegroundService
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.NodePluginJavaScriptEngine
import org.autojs.autojs.engine.NodePluginScriptExecutionException
import org.autojs.autojs.engine.NodeRuntimeErrorCodes
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.receiver.BaseBroadcastReceiver
import org.autojs.autojs.external.shortcut.ScriptShortcutIntentFactory
import org.autojs.autojs.runtime.api.Engines
import org.autojs.autojs.runtime.exception.ScriptCompilationDiagnosticSeverity
import org.autojs.autojs.runtime.exception.ScriptCompilationException
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.SequenceScriptSource
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.autojs.script.TypeScriptStringSource
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.autojs.autojs.ui.log.StackFrameTargetResolver
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs6.R
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.RhinoException

@RunWith(AndroidJUnit4::class)
class TypeScriptCompilerPluginClientDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun reusesUnchangedCompilationAcrossClientInstances() {
        val cacheRoot = File(context.cacheDir, TYPESCRIPT_CACHE_DIRECTORY)
        cacheRoot.listFiles().orEmpty().forEach { child ->
            child.walkBottomUp().forEach(File::delete)
        }
        val performance = mutableListOf<TypeScriptCompilationPerformanceSnapshot>()
        val reporter = TypeScriptCompilationPerformanceReporter { snapshot ->
            performance += snapshot
            Log.i(TEST_LOG_TAG, snapshot.toLogLine())
        }
        withSource(
            name = "cache-${System.nanoTime()}.ts",
            source = "const cached: number = 42; toast(String(cached));",
        ) { source ->
            val firstStarted = SystemClock.elapsedRealtime()
            val first = runBlocking {
                TypeScriptCompilerPluginClient(
                    context,
                    performanceReporter = reporter,
                ).compileSingleFile(source)
            }
            val firstElapsed = SystemClock.elapsedRealtime() - firstStarted
            assertTrue(first is TypeScriptCompilationOutput.Emitted)
            val firstGeneration = cacheRoot.listFiles().orEmpty().map(File::getName).sorted()
            assertEquals(2, firstGeneration.size)
            assertTrue(firstGeneration.any { it.endsWith(".entry") })
            assertTrue(firstGeneration.any { it.endsWith(".output.zip") })

            val secondStarted = SystemClock.elapsedRealtime()
            val second = runBlocking {
                TypeScriptCompilerPluginClient(
                    context,
                    performanceReporter = reporter,
                ).compileSingleFile(source)
            }
            val secondElapsed = SystemClock.elapsedRealtime() - secondStarted
            assertTrue(second is TypeScriptCompilationOutput.Emitted)
            assertEquals(
                (first as TypeScriptCompilationOutput.Emitted).javaScript,
                (second as TypeScriptCompilationOutput.Emitted).javaScript,
            )
            assertEquals(
                "A cache hit must retain the committed generation instead of recompiling.",
                firstGeneration,
                cacheRoot.listFiles().orEmpty().map(File::getName).sorted(),
            )
            Log.i(
                TEST_LOG_TAG,
                "typescript-cache-smoke firstMs=$firstElapsed secondMs=$secondElapsed " +
                    "generation=${firstGeneration.first()}",
            )
            assertEquals(2, performance.size)
            assertEquals(
                TypeScriptCompilationPerformanceSnapshot.CACHE_MISS,
                performance[0].cacheStatus,
            )
            assertEquals(
                TypeScriptCompilationPerformanceSnapshot.CACHE_HIT,
                performance[1].cacheStatus,
            )
            assertTrue(performance[0].bindAndPrewarmElapsedMs != null)
            assertTrue(performance[0].compilerElapsedMs != null)
            assertTrue(performance[0].compilerBeforeIngressElapsedMs != null)
            assertTrue(performance[0].compilerIngressElapsedMs != null)
            assertTrue(performance[0].compilerInterPhaseElapsedMs != null)
            assertTrue(performance[0].compilerParseElapsedMs != null)
            assertTrue(performance[0].compilerSemanticElapsedMs != null)
            assertTrue(performance[0].compilerEmitElapsedMs != null)
            assertTrue(performance[0].compilerAfterEmitElapsedMs != null)
            assertNull(performance[1].bindAndPrewarmElapsedMs)
            assertNull(performance[1].compilerElapsedMs)
            assertNull(performance[1].compilerBeforeIngressElapsedMs)
            assertNull(performance[1].compilerIngressElapsedMs)
            assertNull(performance[1].compilerInterPhaseElapsedMs)
            assertNull(performance[1].compilerParseElapsedMs)
            assertNull(performance[1].compilerSemanticElapsedMs)
            assertNull(performance[1].compilerEmitElapsedMs)
            assertNull(performance[1].compilerAfterEmitElapsedMs)
        }
    }

    @Test
    fun startupPrewarmMovesCompilerBootstrapOffFirstRunAndKeepsRevisionOneSession() {
        TypeScriptCompilerStartupPrewarm.release("device-test-setup")
        try {
            val prewarm = runBlocking {
                TypeScriptCompilerStartupPrewarm.prewarm(context, "device-test")
            }
            assertTrue("The compiler prewarm binding was not retained", prewarm.retained)
            assertEquals(
                TypeScriptR2Contract.PROJECT_SESSION_REVISION,
                prewarm.projectSessionRevision,
            )

            withProject { root ->
                File(root, "project.json").writeText(
                    """{"type":"rhino","main":"main.ts"}""",
                )
                File(root, "tsconfig.json").writeText(
                    """{"compilerOptions":{"strict":true}}""",
                )
                val moduleCount = 9
                for (index in 0 until moduleCount - 1) {
                    File(root, "module$index.ts").writeText(
                        "import { value as nextValue, marker } from './module${index + 1}'\n" +
                            "export { marker }\n" +
                            "export const value: number = nextValue + 1\n",
                    )
                }
                val leaf = File(root, "module${moduleCount - 1}.ts")
                fun writeLeaf(revision: String, value: Int) {
                    leaf.writeText(
                        "export const value: number = $value\n" +
                            "export const marker: string = '$revision'\n",
                    )
                }
                writeLeaf("revision-cold", 1)
                val entry = File(root, "main.ts").apply {
                    writeText(
                        "import { value, marker } from './module0'\n" +
                            "export const result: string = marker + ':' + value\n",
                    )
                }
                val performance = mutableListOf<TypeScriptCompilationPerformanceSnapshot>()
                val client = TypeScriptCompilerPluginClient(
                    context,
                    performanceReporter = TypeScriptCompilationPerformanceReporter { snapshot ->
                        performance += snapshot
                        Log.i(TEST_LOG_TAG, snapshot.toLogLine())
                    },
                )
                fun compile(): TypeScriptCompilationOutput.Emitted = runBlocking {
                    client.compileProject(
                        projectRoot = root,
                        entryFile = entry,
                        targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
                        usePersistentCache = false,
                    ) as TypeScriptCompilationOutput.Emitted
                }

                val cold = compile()
                assertEquals(moduleCount + 1, cold.modules.size)
                val revision = "revision-warm"
                writeLeaf(revision, 2)
                val warm = compile()
                assertEquals(moduleCount + 1, warm.modules.size)
                assertTrue(
                    "Warm output did not include $revision",
                    warm.modules.any { module -> revision in module.javaScript },
                )

                assertEquals(2, performance.size)
                assertTrue(performance.all { snapshot ->
                    snapshot.cacheStatus == TypeScriptCompilationPerformanceSnapshot.CACHE_DISABLED
                })
                val firstRunBindPrewarmMs =
                    requireNotNull(performance.first().bindAndPrewarmElapsedMs)
                val coldCoreCompilerMs = performance.first().coreCompilerElapsedMs()
                val warmCoreCompilerMs = performance.last().coreCompilerElapsedMs()
                Log.i(
                    TEST_LOG_TAG,
                    "typescript-startup-prewarm-acceptance schema=1 files=${moduleCount + 1} " +
                        "prewarmMs=${prewarm.elapsedMs} firstRunBindPrewarmMs=$firstRunBindPrewarmMs " +
                        "coldCoreCompilerMs=$coldCoreCompilerMs " +
                        "warmCoreCompilerMs=$warmCoreCompilerMs",
                )
                assertTrue(
                    "Startup prewarm must remove at least half of bind/bootstrap latency from " +
                        "the first user run: prewarm=${prewarm.elapsedMs}ms " +
                        "firstRunBindPrewarm=${firstRunBindPrewarmMs}ms",
                    firstRunBindPrewarmMs * 2L <= prewarm.elapsedMs,
                )
                assertTrue(
                    "The second edit must reuse revision-1 compiler core state: " +
                        "cold=${coldCoreCompilerMs}ms warm=${warmCoreCompilerMs}ms",
                    warmCoreCompilerMs < coldCoreCompilerMs,
                )
            }
        } finally {
            TypeScriptCompilerStartupPrewarm.release("device-test-finished")
        }
    }

    private fun TypeScriptCompilationPerformanceSnapshot.coreCompilerElapsedMs(): Long =
        requireNotNull(compilerParseElapsedMs) +
            requireNotNull(compilerSemanticElapsedMs) +
            requireNotNull(compilerEmitElapsedMs)

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
    fun compilesNativeTsxWithSourceMapThroughRealPluginBinder() {
        withSource(
            name = "component.tsx",
            source = "\"ui\"; ui.layout(<text text=\"TypeScript TSX\" />)",
        ) { source ->
            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(source)
            }

            assertTrue(output is TypeScriptCompilationOutput.Emitted)
            output as TypeScriptCompilationOutput.Emitted
            assertTrue(output.javaScript.contains("__autojs6Tsx("))
            assertTrue(output.sourceMap.contains("\"version\":3"))
            assertTrue(output.sourceMap.contains("component.tsx"))
            assertTrue(output.entryModulePath.endsWith("component.js"))
            assertFalse(output.diagnostics.any(TypeScriptDiagnostic::isError))
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
    fun executesNonForegroundEntryAdaptersThroughSharedTypeScriptPreparation() {
        val selectorKey = "autojs6.typescript.background.selector.${System.nanoTime()}"
        val preExecuteKey = "autojs6.typescript.background.pre.${System.nanoTime()}"
        val markerKeys = listOf("tasker", "timed", "intent", "shortcut").associateWith { entry ->
            "autojs6.typescript.background.$entry.${System.nanoTime()}"
        }
        (markerKeys.values + selectorKey + preExecuteKey).forEach(System::clearProperty)
        try {
            withSource(
                name = "background-entry.ts",
                source = """
                    declare const java: any
                    const markerKey: string = String(java.lang.System.getProperty("$selectorKey"))
                    java.lang.System.setProperty(markerKey, "executed")
                """.trimIndent(),
            ) { source ->
                val taskerIntent = Intent()
                    .putExtra(ScriptIntents.EXTRA_KEY_PATH, source.path)
                    .putExtra(
                        ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT,
                        "java.lang.System.setProperty('$preExecuteKey', 'executed');",
                    )
                val taskerLaunch = requireNotNull(
                    ScriptIntents.prepareLaunch(context, taskerIntent),
                )
                assertTrue(taskerLaunch.launch.source is SequenceScriptSource)
                assertTrue(
                    TypeScriptExecutionCompiler.requiresPreparation(taskerLaunch.launch.source),
                )
                System.setProperty(selectorKey, markerKeys.getValue("tasker"))
                ScriptIntents.handleIntent(context, taskerIntent)
                awaitSystemProperty(preExecuteKey, "executed")
                awaitSystemProperty(markerKeys.getValue("tasker"), "executed")

                val timedTask = TimedTask(
                    System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1),
                    0L,
                    source.path,
                    ExecutionConfig(),
                )
                val timedIntent = timedTask.createIntent()
                val timedLaunch = requireNotNull(
                    ScriptIntents.prepareLaunch(context, timedIntent),
                )
                assertTrue(
                    TypeScriptExecutionCompiler.requiresPreparation(timedLaunch.launch.source),
                )
                System.setProperty(selectorKey, markerKeys.getValue("timed"))
                ScriptIntents.handleIntent(context, timedIntent)
                awaitSystemProperty(markerKeys.getValue("timed"), "executed")

                val intentTask = IntentTask().apply {
                    setScriptPathAndCaptureContext(source.path)
                    action = "autojs6.typescript.background.${System.nanoTime()}"
                }
                System.setProperty(selectorKey, markerKeys.getValue("intent"))
                BaseBroadcastReceiver.runTask(
                    context,
                    Intent(intentTask.action),
                    intentTask,
                )
                awaitSystemProperty(markerKeys.getValue("intent"), "executed")

                val shortcutIntent = ScriptShortcutIntentFactory.create(source.path)
                val shortcutLaunch = requireNotNull(
                    ScriptShortcutIntentFactory.prepareDirectPathLaunch(
                        context,
                        shortcutIntent,
                    ),
                )
                assertTrue(
                    TypeScriptExecutionCompiler.requiresPreparation(shortcutLaunch.launch.source),
                )
                System.setProperty(selectorKey, markerKeys.getValue("shortcut"))
                val shortcutTerminal = executeAndAwait(
                    shortcutLaunch.launch.source,
                    shortcutLaunch.config.workingDirectory,
                )
                shortcutTerminal.failure.get()?.let { error ->
                    throw AssertionError("TypeScript shortcut execution failed", error)
                }
                assertTrue(shortcutTerminal.succeeded.get())
                assertEquals("executed", System.getProperty(markerKeys.getValue("shortcut")))
            }
        } finally {
            (markerKeys.values + selectorKey + preExecuteKey).forEach(System::clearProperty)
        }
    }

    @Test
    fun executesTaskerPrefixBeforeRhinoTypeScriptProjectEntry() {
        val preExecuteKey = "autojs6.typescript.project.pre.${System.nanoTime()}"
        val markerKey = "autojs6.typescript.project.body.${System.nanoTime()}"
        System.clearProperty(preExecuteKey)
        System.clearProperty(markerKey)
        try {
            withProject { root ->
                File(root, "project.json").writeText("""{"main":"main.ts"}""")
                File(root, "value.ts").writeText("export const value: number = 42\n")
                val entry = File(root, "main.ts").apply {
                    writeText(
                        """
                        declare const java: any
                        import { value } from './value'
                        java.lang.System.setProperty('$markerKey', String(value))
                        """.trimIndent(),
                    )
                }
                val launch = ScriptLaunchSourceFactory.createPathLaunch(
                    path = entry.path,
                    preExecuteScript =
                        "java.lang.System.setProperty('$preExecuteKey', 'executed');",
                    workingDirectory = root.path,
                    defaultWorkingDirectory = root.path,
                )

                assertTrue(launch.source is SequenceScriptSource)
                assertTrue(TypeScriptExecutionCompiler.requiresPreparation(launch.source))
                val terminal = executeAndAwait(launch.source, launch.workingDirectory)
                terminal.failure.get()?.let { error ->
                    throw AssertionError("Prefixed Rhino TypeScript project failed", error)
                }
                assertTrue(terminal.succeeded.get())
                assertEquals("executed", System.getProperty(preExecuteKey))
                assertEquals("42", System.getProperty(markerKey))
            }
        } finally {
            System.clearProperty(preExecuteKey)
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun executesTaskerPrefixBeforeNodeTypeScriptFile() {
        val marker = "typescript-node-tasker-prefix-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withSource(
                name = "tasker-prefix.mts",
                source = """
                    const value: number = Number(
                        (globalThis as { taskerValue?: unknown }).taskerValue,
                    ) + 1
                    console.log('$marker:' + value)
                """.trimIndent(),
            ) { source ->
                val launch = ScriptLaunchSourceFactory.createPathLaunch(
                    path = source.path,
                    preExecuteScript = "globalThis.taskerValue = 41;",
                    workingDirectory = source.parentFile?.path,
                    defaultWorkingDirectory = context.cacheDir.path,
                )

                assertTrue(launch.source is SequenceScriptSource)
                assertTrue(TypeScriptExecutionCompiler.requiresPreparation(launch.source))
                val terminal = executeAndAwait(launch.source, launch.workingDirectory)
                terminal.failure.get()?.let { error ->
                    throw AssertionError("Prefixed Node TypeScript execution failed", error)
                }
                assertTrue(terminal.succeeded.get())
                val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                assertTrue(requireNotNull(engine.lastPluginScriptResult).succeeded)
                val consoleMessage = awaitConsoleMessage(marker)
                assertTrue(
                    "Expected $marker:42 in Node console, got: $consoleMessage",
                    consoleMessage.contains("$marker:42"),
                )
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun executesRhinoProjectWithImportsCycleJsonAndMoreThanTenModules() {
        val markerKey = "autojs6.typescript.rhino-project.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withProject { root ->
                File(root, "project.json").writeText("""{"main":"main.ts"}""")
                File(root, "tsconfig.json").writeText(
                    """{"compilerOptions":{"strict":true}}""",
                )
                val chain = File(root, "chain").apply { mkdirs() }
                repeat(11) { index ->
                    File(chain, "m$index.ts").writeText(
                        if (index == 10) {
                            "export const value: number = 30;\n"
                        } else {
                            "import { value as next } from './m${index + 1}'; " +
                                "export const value: number = next + 1;\n"
                        },
                    )
                }
                val cycle = File(root, "cycle").apply { mkdirs() }
                File(cycle, "a.ts").writeText(
                    "import { fromB } from './b'; " +
                        "export const base: number = 40; " +
                        "export function cycleValue(): number { return fromB() + 1; }\n",
                )
                File(cycle, "b.ts").writeText(
                    "import { base } from './a'; " +
                        "export function fromB(): number { return base; }\n",
                )
                File(root, "data.json").writeText("""{"bonus":2}""")
                val entry = File(root, "main.ts").apply {
                    writeText(
                        """
                        declare const java: any
                        declare function require(path: string): any
                        const payload = require('./data.json') as { bonus: number }
                        import { value } from './chain/m0'
                        import { cycleValue } from './cycle/a'
                        const total: number = value + cycleValue() + payload.bonus
                        java.lang.System.setProperty('$markerKey', String(total))
                        """.trimIndent(),
                    )
                }

                val launch = ScriptLaunchSourceFactory.createFileLaunch(entry)
                assertTrue(launch.source is TypeScriptFileSource)
                val terminal = executeAndAwait(launch.source, root.path)
                terminal.failure.get()?.let { error ->
                    throw AssertionError("Rhino TypeScript project execution failed", error)
                }
                assertTrue(terminal.succeeded.get())
                assertEquals("83", System.getProperty(markerKey))
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun returnsProjectDiagnosticFromImportedTypeScriptModule() {
        withProject { root ->
            File(root, "project.json").writeText("""{"main":"main.ts"}""")
            File(root, "main.ts").writeText("import { value } from './lib/bad'; toast(value);\n")
            val library = File(root, "lib").apply { mkdirs() }
            File(library, "bad.ts").writeText("export const value: number = 'wrong';\n")

            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileProject(
                    projectRoot = root,
                    entryFile = File(root, "main.ts"),
                    targetProfile = org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_RHINO,
                )
            }
            assertTrue(output is TypeScriptCompilationOutput.Diagnostics)
            output as TypeScriptCompilationOutput.Diagnostics
            assertTrue(output.diagnostics.any { diagnostic ->
                diagnostic.isError && diagnostic.uri?.endsWith("/lib/bad.ts") == true
            })
        }
    }

    @Test
    fun dependencyLayerWithThreeDirectPackagesCompilesThroughRealPluginBinder() {
        withProject { root ->
            File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
            File(root, "package.json").writeText(
                """{"type":"module","dependencies":{"dayjs":"1.11.13","lodash":"4.17.21","zod":"4.1.5"}}""",
            )
            File(root, "package-lock.json").writeText(
                """{"name":"t2-device-project","lockfileVersion":3,"packages":{}}""",
            )
            val dependencies = File(root, "node_modules").apply { mkdirs() }
            listOf("dayjs", "lodash", "zod").forEachIndexed { index, packageName ->
                val packageRoot = File(dependencies, packageName).apply { mkdirs() }
                File(packageRoot, "package.json").writeText(
                    """{"name":"$packageName","main":"index.js","types":"index.d.ts","scripts":{"test":"must-not-run"}}""",
                )
                File(packageRoot, "index.js").writeText("export const value = $index;\n")
                File(packageRoot, "index.d.ts").writeText(
                    "export declare const value: number;\n",
                )
                File(packageRoot, "README.md").writeText("excluded documentation")
                File(packageRoot, "tests").apply { mkdirs() }
                    .resolve("must-not-run.js")
                    .writeText("throw new Error('excluded dependency test');\n")
            }
            val entry = File(root, "main.mts").apply {
                writeText("export const dependencyLayerAdmission: number = 42;\n")
            }
            val performance = mutableListOf<TypeScriptCompilationPerformanceSnapshot>()
            val client = TypeScriptCompilerPluginClient(
                context,
                performanceReporter = TypeScriptCompilationPerformanceReporter { snapshot ->
                    performance += snapshot
                },
            )

            fun compile(): TypeScriptCompilationOutput = runBlocking {
                client.compileProject(
                    projectRoot = root,
                    entryFile = entry,
                    targetProfile =
                        org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_NODE,
                )
            }

            val first = compile() as TypeScriptCompilationOutput.Emitted
            File(dependencies, "dayjs/index.js").writeText("export const value = 42;\n")
            val dependencyChanged = compile() as TypeScriptCompilationOutput.Emitted
            assertEquals(1, first.modules.size)
            assertEquals(first.javaScript, dependencyChanged.javaScript)
            assertTrue(first.javaScript.contains("dependencyLayerAdmission"))
            assertEquals(
                TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION,
                first.authority.dependencyLayerSchemaRevision,
            )
            assertEquals(
                TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION,
                first.authority.dependencyLayerPolicyRevision,
            )
            assertEquals(
                DEPENDENCY_LAYER_POLICY_FINGERPRINT,
                first.authority.dependencyLayerPolicyFingerprint,
            )
            assertEquals(2, performance.size)
            assertTrue(performance.all { snapshot ->
                snapshot.cacheStatus == TypeScriptCompilationPerformanceSnapshot.CACHE_MISS &&
                    snapshot.compilerElapsedMs != null
            })
        }
    }

    @Test
    fun nativeAndInstallLifecycleDependenciesFailBeforePluginBinderCompilation() {
        withProject { root ->
            File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
            val entry = File(root, "main.mts").apply {
                writeText("import bcrypt from \"bcrypt\"; export const hash = bcrypt;\n")
            }
            val dependency = File(root, "node_modules/bcrypt").apply { mkdirs() }
            File(dependency, "package.json").writeText(
                """{"name":"bcrypt","version":"6.0.0","scripts":{"install":"node-gyp-build"}}""",
            )
            File(dependency, "index.js").writeText("module.exports = {};\n")
            val binding = File(dependency, "binding.gyp").apply { writeText("{}\n") }
            val native = File(dependency, "prebuilds/darwin-arm64").apply { mkdirs() }
                .resolve("bcrypt.node")
                .apply { writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)) }

            fun compileFailure(): Pair<TypeScriptCompilerException, TypeScriptDependencyBoundaryException> {
                val error = runCatching {
                    runBlocking {
                        TypeScriptCompilerPluginClient(context).compileProject(
                            projectRoot = root,
                            entryFile = entry,
                            targetProfile =
                                org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_NODE,
                        )
                    }
                }.exceptionOrNull()
                assertTrue(
                    "Expected TypeScriptCompilerException, got " +
                        "${error?.javaClass?.name}: ${error?.message}",
                    error is TypeScriptCompilerException,
                )
                error as TypeScriptCompilerException
                assertTrue(
                    "Expected TypeScriptDependencyBoundaryException cause, got " +
                        "${error.cause?.javaClass?.name}: ${error.cause?.message}",
                    error.cause is TypeScriptDependencyBoundaryException,
                )
                return error to (error.cause as TypeScriptDependencyBoundaryException)
            }

            val (nativeFailure, nativeBoundary) = compileFailure()
            assertEquals(TypeScriptR2Contract.ERROR_NATIVE_DEPENDENCY_UNSUPPORTED, nativeFailure.code)
            assertEquals("native-binary", nativeBoundary.signal)
            assertEquals("bcrypt", nativeBoundary.packageName)

            assertTrue(native.delete())
            assertTrue(binding.delete())
            val (lifecycleFailure, lifecycleBoundary) = compileFailure()
            assertEquals(
                TypeScriptR2Contract.ERROR_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED,
                lifecycleFailure.code,
            )
            assertEquals("scripts.install", lifecycleBoundary.signal)
            assertEquals("bcrypt", lifecycleBoundary.packageName)
        }
    }

    @Test
    fun bareDayjsAndZodExportsCompileThroughRealPluginBinder() {
        withProject { root ->
            File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
            File(root, "package.json").writeText(
                """{"type":"module","dependencies":{"dayjs":"1.11.13","zod":"4.1.5"}}""",
            )
            File(root, "package-lock.json").writeText(
                """{"name":"t2-2-device-project","lockfileVersion":3,"packages":{}}""",
            )
            val dependencies = File(root, "node_modules").apply { mkdirs() }
            File(dependencies, "dayjs").apply {
                mkdirs()
                resolve("package.json").writeText(
                    """{"name":"dayjs","main":"dayjs.min.js","types":"index.d.ts"}""",
                )
                resolve("index.d.ts").writeText(
                    "declare function dayjs(): { format(): string };\n" +
                        "export = dayjs;\n",
                )
                resolve("dayjs.min.js").writeText("module.exports = function dayjs() {};\n")
            }
            File(dependencies, "zod").apply {
                mkdirs()
                resolve("package.json").writeText(
                    """
                    {
                      "name":"zod",
                      "type":"module",
                      "exports":{
                        ".":{"types":"./index.d.ts","import":"./index.js","require":"./index.cjs"},
                        "./mini":{"types":"./mini/index.d.ts","import":"./mini/index.js","require":"./mini/index.cjs"}
                      }
                    }
                    """.trimIndent(),
                )
                resolve("index.d.ts").writeText(
                    "export declare const z: { string(): { parse(value: unknown): string } };\n",
                )
                resolve("index.js").writeText("export const z = {};\n")
                resolve("index.cjs").writeText("exports.z = {};\n")
                resolve("mini").apply {
                    mkdirs()
                    resolve("index.d.ts").writeText(
                        "export declare const mini: { number(): { parse(value: unknown): number } };\n",
                    )
                    resolve("index.js").writeText("export const mini = {};\n")
                    resolve("index.cjs").writeText("exports.mini = {};\n")
                }
            }
            val entry = File(root, "main.mts").apply {
                writeText(
                    """
                    import dayjs from "dayjs"
                    import { z } from "zod"
                    import { mini } from "zod/mini"
                    const formatted: string = dayjs().format()
                    const parsed: string = z.string().parse("ok")
                    const count: number = mini.number().parse(42)
                    export const t2BareSpecifierAcceptance = formatted + parsed + count
                    """.trimIndent(),
                )
            }

            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileProject(
                    projectRoot = root,
                    entryFile = entry,
                    targetProfile =
                        org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_NODE,
                )
            }
            assertTrue(
                "Expected emitted T2-2 output, got: $output",
                output is TypeScriptCompilationOutput.Emitted,
            )
            output as TypeScriptCompilationOutput.Emitted
            assertEquals(1, output.modules.size)
            assertTrue(output.javaScript.contains("dayjs"))
            assertTrue(output.javaScript.contains("zod/mini"))
            assertEquals(
                TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
                output.authority.dependencyResolverPolicyRevision,
            )
            assertEquals(
                TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
                output.authority.dependencyResolverPolicyFingerprint,
            )
        }
    }

    @Test
    fun packageTypesVersionsAndAtTypesMatchAcrossCompilerProfiles() {
        val diagnosticCodesByProfile = listOf(
            org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_RHINO,
            org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_NODE,
        ).associateWith { profile ->
            withProject { root ->
                val node = profile ==
                    org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_NODE
                val entryName = if (node) "main.mts" else "main.ts"
                File(root, "project.json").writeText(
                    if (node) {
                        """{"type":"node","main":"$entryName"}"""
                    } else {
                        """{"type":"rhino","main":"$entryName"}"""
                    },
                )
                File(root, "tsconfig.json").writeText(
                    """{"compilerOptions":{"strict":true}}""",
                )
                File(root, "package.json").writeText(
                    """{"type":"${if (node) "module" else "commonjs"}","dependencies":{"dayjs":"1.11.13"},"devDependencies":{"@types/ambient":"1.0.0"}}""",
                )
                File(root, "package-lock.json").writeText(
                    """{"name":"t2-4-device-project","lockfileVersion":3,"packages":{}}""",
                )
                val dependencies = File(root, "node_modules").apply { mkdirs() }
                File(dependencies, "dayjs").apply {
                    mkdirs()
                    resolve("package.json").writeText(
                        """
                        {
                          "name":"dayjs",
                          "main":"dayjs.min.js",
                          "types":"legacy/index.d.ts",
                          "typesVersions":{">=6.0":{"*":["ts6/*"]}}
                        }
                        """.trimIndent(),
                    )
                    resolve("dayjs.min.js").writeText(
                        "module.exports = function dayjs() {};\n",
                    )
                    resolve("legacy").apply {
                        mkdirs()
                        resolve("index.d.ts").writeText(
                            "interface LegacyDayjs { legacyOnly(): never }\n" +
                                "declare function dayjs(): LegacyDayjs\n" +
                                "export = dayjs\n",
                        )
                    }
                    resolve("ts6/legacy").apply {
                        mkdirs()
                        resolve("index.d.ts").writeText(
                            "import { Dayjs } from './internal'\n" +
                                "declare function dayjs(): Dayjs\n" +
                                "export = dayjs\n",
                        )
                        resolve("internal.d.ts").writeText(
                            "export interface Dayjs { format(): string; unix(): number }\n",
                        )
                    }
                }
                File(dependencies, "@types/ambient").apply {
                    mkdirs()
                    resolve("package.json").writeText(
                        """{"name":"@types/ambient","types":"index.d.ts"}""",
                    )
                    resolve("index.d.ts").writeText("declare const ambientAnswer: 42\n")
                }
                val entry = File(root, entryName)
                fun writeEntry(wrongType: String) {
                    entry.writeText(
                        """
                        import dayjs from "dayjs"
                        const value = dayjs()
                        const formatted: string = value.format()
                        const unix: number = value.unix()
                        const ambient: 42 = ambientAnswer
                        const wrong: $wrongType = value.format()
                        export const t2DependencyTypes = formatted + unix + ambient + wrong
                        """.trimIndent(),
                    )
                }
                fun compile(): TypeScriptCompilationOutput = runBlocking {
                    TypeScriptCompilerPluginClient(context).compileProject(
                        projectRoot = root,
                        entryFile = entry,
                        targetProfile = profile,
                    )
                }

                writeEntry("string")
                val emitted = compile()
                assertTrue("Expected emitted T2-4 $profile output, got: $emitted", emitted is TypeScriptCompilationOutput.Emitted)
                emitted as TypeScriptCompilationOutput.Emitted
                assertTrue(emitted.javaScript.contains("t2DependencyTypes"))
                assertEquals(
                    TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
                    emitted.authority.dependencyResolverPolicyRevision,
                )
                assertEquals(
                    TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
                    emitted.authority.dependencyResolverPolicyFingerprint,
                )

                writeEntry("number")
                val diagnostics = compile()
                assertTrue(
                    "Expected T2-4 $profile diagnostics, got: $diagnostics",
                    diagnostics is TypeScriptCompilationOutput.Diagnostics,
                )
                (diagnostics as TypeScriptCompilationOutput.Diagnostics)
                    .diagnostics
                    .map(TypeScriptDiagnostic::code)
                    .sorted()
            }
        }

        assertEquals(listOf(2322), diagnosticCodesByProfile.getValue("rhino"))
        assertEquals(
            diagnosticCodesByProfile.getValue("rhino"),
            diagnosticCodesByProfile.getValue("node"),
        )
    }

    @Test
    fun executesSameDualPackageSnapshotThroughRhinoAndNodeProfiles() {
        val rhinoMarker = "autojs6.typescript.t2_3.rhino.${System.nanoTime()}"
        val nodeMarker = "typescript-t2-3-node-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        System.clearProperty(rhinoMarker)
        console.clear()
        try {
            withProject { root ->
                File(root, "tsconfig.json").writeText("{}")
                File(root, "package.json").writeText(
                    """{"dependencies":{"dual-runtime":"1.0.0"}}""",
                )
                val dependency = File(root, "node_modules/dual-runtime").apply { mkdirs() }
                File(dependency, "package.json").writeText(
                    """
                    {
                      "name": "dual-runtime",
                      "version": "1.0.0",
                      "type": "module",
                      "exports": {
                        ".": {
                          "types": "./index.d.ts",
                          "import": "./index.mjs",
                          "require": "./index.cjs"
                        }
                      }
                    }
                    """.trimIndent(),
                )
                File(dependency, "index.d.ts").writeText(
                    "export declare const flavor: string\n" +
                        "export declare const value: number\n",
                )
                File(dependency, "index.cjs").writeText(
                    "exports.flavor = 'cjs'; exports.value = 42;\n",
                )
                File(dependency, "index.mjs").writeText(
                    "export const flavor = 'esm'; export const value = 42;\n",
                )

                val rhinoEntry = File(root, "rhino-main.ts").apply {
                    writeText(
                        """
                        declare const java: any
                        import { flavor, value } from "dual-runtime"
                        java.lang.System.setProperty("$rhinoMarker", flavor + ":" + value)
                        """.trimIndent(),
                    )
                }
                val rhinoLaunch = ScriptLaunchSourceFactory.createFileLaunch(rhinoEntry)
                assertTrue(rhinoLaunch.source is TypeScriptFileSource)
                val rhinoTerminal = executeAndAwait(rhinoLaunch.source, root.path)
                rhinoTerminal.failure.get()?.let { error ->
                    val details = (error as? ScriptCompilationException)?.consoleMessage
                    throw AssertionError(
                        "Rhino dual-package execution failed" +
                            if (details.isNullOrBlank()) "" else "\n$details",
                        error,
                    )
                }
                assertTrue(rhinoTerminal.succeeded.get())
                assertTrue(rhinoTerminal.execution.engine is LoopBasedJavaScriptEngine)
                assertEquals("cjs:42", System.getProperty(rhinoMarker))

                val nodeEntry = File(root, "node-main.mts").apply {
                    writeText(
                        """
                        import { flavor, value } from "dual-runtime"
                        console.log("$nodeMarker:" + flavor + ":" + value)
                        """.trimIndent(),
                    )
                }
                val nodeLaunch = ScriptLaunchSourceFactory.createFileLaunch(nodeEntry)
                assertEquals(
                    JavaScriptSource.ENGINE_NODEJS,
                    (nodeLaunch.source as JavaScriptSource).engineName,
                )
                val nodeTerminal = executeAndAwait(nodeLaunch.source, root.path)
                nodeTerminal.failure.get()?.let { error ->
                    val details = (error as? ScriptCompilationException)?.consoleMessage
                    throw AssertionError(
                        "Node dual-package execution failed" +
                            if (details.isNullOrBlank()) "" else "\n$details",
                        error,
                    )
                }
                assertTrue(nodeTerminal.succeeded.get())
                val engine = nodeTerminal.execution.engine as NodePluginJavaScriptEngine
                assertTrue(requireNotNull(engine.lastPluginScriptResult).succeeded)
                assertTrue(
                    awaitConsoleMessage(nodeMarker).contains("$nodeMarker:esm:42"),
                )
            }
        } finally {
            System.clearProperty(rhinoMarker)
            console.clear()
        }
    }

    @Test
    fun executesCommonJsNodeTsProjectWithCycleJsonAndMoreThanTenModules() =
        assertNodeTsProjectExecution(packageType = "commonjs", includeJson = true, includeCycle = true)

    @Test
    fun executesEsmNodeTsProjectWithMoreThanTenModules() =
        assertNodeTsProjectExecution(packageType = "module", includeJson = false, includeCycle = false)

    @Test
    fun executesEsmAndCommonJsWorkspacePackagesInOneNodeProject() {
        val marker = "typescript-node-workspace-boundaries-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withProject { root ->
                File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
                File(root, "package.json").writeText("{}")
                val packages = File(root, "packages").apply { mkdirs() }
                val esmPackage = File(packages, "esm-package").apply { mkdirs() }
                File(esmPackage, "package.json").writeText("""{"type":"module"}""")
                File(esmPackage, "value.ts").writeText(
                    "export const esmValue: number = 20\n",
                )
                val commonJsPackage = File(packages, "commonjs-package").apply { mkdirs() }
                File(commonJsPackage, "package.json").writeText("""{"type":"commonjs"}""")
                File(commonJsPackage, "value.ts").writeText(
                    "export const commonJsValue: number = 22\n",
                )
                val entry = File(root, "main.mts").apply {
                    writeText(
                        """
                        import { esmValue } from "./packages/esm-package/value.js"
                        import { commonJsValue } from "./packages/commonjs-package/value.js"
                        const total: number = esmValue + commonJsValue
                        console.log("$marker:" + total)
                        """.trimIndent(),
                    )
                }

                val launch = ScriptLaunchSourceFactory.createFileLaunch(entry)
                assertTrue(launch.source is NodeProjectLaunchSource)
                val terminal = executeAndAwait(launch.source, root.path)
                terminal.failure.get()?.let { error ->
                    val details = (error as? ScriptCompilationException)?.consoleMessage
                    throw AssertionError(
                        "Mixed ESM/CommonJS workspace package execution failed" +
                            if (details.isNullOrBlank()) "" else "\n$details",
                        error,
                    )
                }
                assertTrue(terminal.succeeded.get())
                val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                assertTrue(requireNotNull(engine.lastPluginScriptResult).succeeded)
                assertTrue(awaitConsoleMessage(marker).contains("$marker:42"))
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun executesEsmTypeScriptCycleWithLiveBindings() {
        val marker = "typescript-node-esm-live-binding-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withProject { root ->
                File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
                File(root, "package.json").writeText("""{"type":"module"}""")
                File(root, "main.mts").writeText(
                    """
                    import { snapshot, setValue } from "./state.mjs"
                    console.log("$marker:initial=" + snapshot())
                    setValue(42)
                    console.log("$marker:updated=" + snapshot())
                    """.trimIndent(),
                )
                File(root, "state.mts").writeText(
                    """
                    import { readValue } from "./reader.mjs"
                    export let value: number = 1
                    export function setValue(next: number): void { value = next }
                    export function snapshot(): number { return readValue() }
                    """.trimIndent(),
                )
                File(root, "reader.mts").writeText(
                    """
                    import { value } from "./barrel.mjs"
                    export function readValue(): number { return value }
                    """.trimIndent(),
                )
                File(root, "barrel.mts").writeText(
                    """export { value } from "./state.mjs"""",
                )

                val launch = ScriptLaunchSourceFactory.createFileLaunch(File(root, "main.mts"))
                assertTrue(launch.source is NodeProjectLaunchSource)
                val terminal = executeAndAwait(launch.source, root.path)
                terminal.failure.get()?.let { error ->
                    val details = (error as? ScriptCompilationException)?.consoleMessage
                    throw AssertionError(
                        "ESM TypeScript live-binding cycle execution failed" +
                            if (details.isNullOrBlank()) "" else "\n$details",
                        error,
                    )
                }
                assertTrue(terminal.succeeded.get())
                val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                val result = requireNotNull(engine.lastPluginScriptResult)
                assertTrue(result.succeeded)
                assertEquals(
                    "vm_source_text_module",
                    result.nativeValues["embedded_script.esm_linker"],
                )
                assertEquals(
                    "true",
                    result.nativeValues["embedded_script.esm_live_bindings"],
                )
                assertTrue(
                    awaitConsoleMessage("$marker:initial=").contains("$marker:initial=1"),
                )
                assertTrue(
                    awaitConsoleMessage("$marker:updated=").contains("$marker:updated=42"),
                )
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun mapsImportedNodeProjectRuntimeFailureBackToTypeScriptSources() {
        val marker = "typescript-node-project-map-${System.nanoTime()}"
        withProject { root ->
            File(root, "project.json").writeText("""{"type":"node","main":"main.ts"}""")
            File(root, "package.json").writeText("""{"type":"commonjs"}""")
            File(root, "main.ts").writeText(
                "import { fail } from './lib/fail.js';\nfail();\n",
            )
            val library = File(root, "lib").apply { mkdirs() }
            File(library, "fail.ts").writeText(
                """
                export function fail(): never {
                    const message: string = '$marker'
                    throw new Error(message)
                }
                """.trimIndent(),
            )

            val launch = ScriptLaunchSourceFactory.createFileLaunch(File(root, "main.ts"))
            assertTrue(launch.source is NodeProjectLaunchSource)
            val terminal = executeAndAwait(launch.source, root.path)
            assertFalse(terminal.succeeded.get())
            assertTrue(terminal.failure.get() is NodePluginScriptExecutionException)
            val engine = terminal.execution.engine as NodePluginJavaScriptEngine
            val mappedStack = requireNotNull(engine.lastPluginScriptResult?.errorStack)
            assertTrue(
                "Expected imported TypeScript frame at fail.ts:3, got:\n$mappedStack",
                mappedStack.contains("/lib/fail.ts:3:"),
            )
            assertTrue(
                "Expected entry TypeScript frame at main.ts:2, got:\n$mappedStack",
                mappedStack.contains("/main.ts:2:"),
            )
            assertFalse("Generated JavaScript frame leaked:\n$mappedStack", mappedStack.contains("lib/fail.js"))
        }
    }

    private fun assertNodeTsProjectExecution(
        packageType: String,
        includeJson: Boolean,
        includeCycle: Boolean,
    ) {
        val marker = "typescript-node-project-$packageType-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withProject { root ->
                File(root, "project.json").writeText("""{"type":"node","main":"main.ts"}""")
                File(root, "package.json").writeText("""{"type":"$packageType"}""")
                val chain = File(root, "chain").apply { mkdirs() }
                repeat(11) { index ->
                    File(chain, "m$index.ts").writeText(
                        if (index == 10) {
                            "export const value: number = 30;\n"
                        } else {
                            "import { value as next } from './m${index + 1}.js'; " +
                                "export const value: number = next + 1;\n"
                        },
                    )
                }
                val dependency = File(root, "dependency").apply { mkdirs() }
                File(dependency, "a.ts").writeText(
                    if (includeCycle) {
                        "import { fromB } from './b.js'; " +
                            "export const base: number = 40; " +
                            "export function dependencyValue(): number { return fromB() + 1; }\n"
                    } else {
                        "import { base } from './b.js'; " +
                            "export function dependencyValue(): number { return base + 1; }\n"
                    },
                )
                File(dependency, "b.ts").writeText(
                    if (includeCycle) {
                        "import { base } from './a.js'; " +
                            "export function fromB(): number { return base; }\n"
                    } else {
                        "export const base: number = 40;\n"
                    },
                )
                if (includeJson) File(root, "data.json").writeText("""{"bonus":2}""")
                val jsonExpression = if (includeJson) {
                    "declare function require(path: string): any; " +
                        "const payload = require('./data.json') as { bonus: number }; " +
                        "const bonus: number = payload.bonus;"
                } else {
                    "const bonus: number = 2;"
                }
                val entry = File(root, "main.ts").apply {
                    writeText(
                        "import { value } from './chain/m0.js'; " +
                            "import { dependencyValue } from './dependency/a.js'; " +
                            "$jsonExpression " +
                            "const total: number = value + dependencyValue() + bonus; " +
                            "console.log('$marker:' + total);\n",
                    )
                }

                val launch = ScriptLaunchSourceFactory.createFileLaunch(entry)
                assertTrue(launch.source is NodeProjectLaunchSource)
                val terminal = executeAndAwait(launch.source, root.path)
                terminal.failure.get()?.let { error ->
                    val details = (error as? ScriptCompilationException)?.consoleMessage
                    throw AssertionError(
                        buildString {
                            append("Node TypeScript $packageType project execution failed")
                            if (details != null) {
                                append('\n')
                                append(details)
                            }
                        },
                        error,
                    )
                }
                assertTrue(terminal.succeeded.get())
                val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                assertTrue(requireNotNull(engine.lastPluginScriptResult).succeeded)
                val consoleMessage = awaitConsoleMessage(marker)
                assertTrue(
                    "Expected $marker:83 in Node console, got: $consoleMessage",
                    consoleMessage.contains("$marker:83"),
                )
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun compilesRuntimeCreatedTypeScriptAndKeepsSnapshotErrorsStable() {
        val marker = "typescript-node-dynamic-snapshot-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withProject { root ->
                File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
                File(root, "package.json").writeText("""{"type":"module"}""")
                File(root, "value.ts").writeText("export const value: number = 10\n")
                File(root, "value.mts").writeText("export const value: number = 20\n")
                File(root, "value.cts").writeText("export const value: number = 12\n")
                val entry = File(root, "main.mts").apply {
                    writeText(
                        """
                        import { value } from "./value.mjs"

                        function errorCode(error: unknown): string {
                            return String((error as { code?: unknown }).code ?? "")
                        }

                        const extensions: readonly string[] = ["ts", "mts", "cts"]
                        const loaded: Array<{ value: number }> = await Promise.all(
                            extensions.map(async (extension: string) =>
                                await import("./value." + extension) as { value: number },
                            ),
                        )
                        const total: number = loaded.reduce(
                            (sum: number, item: { value: number }): number => sum + item.value,
                            0,
                        )
                        if (value !== 20) throw new Error("Static ESM control import failed")

                        let ambiguous: string = ""
                        const extensionlessTarget: string = "./value"
                        try {
                            await import(extensionlessTarget)
                        } catch (error: unknown) {
                            ambiguous = errorCode(error)
                        }

                        const fsSpecifier: string = "node:" + "fs"
                        const runtimeFs = await import(fsSpecifier) as {
                            writeFileSync(path: string, data: string): void
                        }
                        runtimeFs.writeFileSync("./late.mts", "export const value: number = 99\n")
                        const lateTarget: string = "./late." + "mts"
                        const late = await import(lateTarget) as { value: number }

                        let escaping: string = ""
                        const escapingTarget: string = "../escape." + "mts"
                        try {
                            await import(escapingTarget)
                        } catch (error: unknown) {
                            escaping = errorCode(error)
                        }

                        console.log("$marker:" + [total, ambiguous, late.value, escaping].join("|"))
                        """.trimIndent(),
                    )
                }

                val launch = ScriptLaunchSourceFactory.createFileLaunch(entry)
                assertTrue(launch.source is NodeProjectLaunchSource)
                val terminal = executeAndAwait(launch.source, root.path)
                terminal.failure.get()?.let { error ->
                    val details = (error as? ScriptCompilationException)?.consoleMessage
                    throw AssertionError(
                        "Closed-snapshot dynamic TypeScript execution failed" +
                            if (details.isNullOrBlank()) "" else "\n$details",
                        error,
                    )
                }
                assertTrue(terminal.succeeded.get())
                val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                val result = requireNotNull(engine.lastPluginScriptResult)
                assertTrue(result.succeeded)
                val expected = listOf(
                    "42",
                    NodeRuntimeErrorCodes.TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS,
                    "99",
                    // Native module loading follows Android fs access. This path has
                    // no compiler output, so it is a snapshot miss rather than an fs denial.
                    NodeRuntimeErrorCodes.TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND,
                ).joinToString("|")
                val consoleMessage = awaitConsoleMessage(marker)
                assertTrue(
                    "Expected $marker:$expected in Node console, got: $consoleMessage",
                    consoleMessage.contains("$marker:$expected"),
                )
                assertEquals(
                    "true",
                    result.nativeValues[
                        "embedded_script.typescript.dynamic_specifier.snapshot_enabled"
                    ],
                )
                assertEquals(
                    "5",
                    result.nativeValues[
                        "embedded_script.typescript.dynamic_specifier.source_count"
                    ],
                )
                assertEquals(
                    "4",
                    result.nativeValues[
                        "embedded_script.typescript.dynamic_specifier.mapped_count"
                    ],
                )
                assertEquals(
                    "2",
                    result.nativeValues[
                        "embedded_script.typescript.dynamic_specifier.rejected_count"
                    ],
                )
                assertEquals(
                    "1",
                    result.nativeValues[
                        "embedded_script.typescript.dynamic_specifier.ambiguous_count"
                    ],
                )
                assertEquals(
                    NodeRuntimeErrorCodes.TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND,
                    result.nativeValues[
                        "embedded_script.typescript.dynamic_specifier.last_error_code"
                    ],
                )
                assertEquals(
                    "0",
                    result.nativeValues[
                        "embedded_script.module_provider.missing_candidate_request_count"
                    ],
                )
                assertEquals(
                    "1",
                    result.nativeValues[
                        "embedded_script.typescript.on_demand_compile.count"
                    ],
                )
                assertTrue(
                    result.nativeValues[
                        "embedded_script.typescript.on_demand_compile.source_bytes"
                    ].orEmpty().toLong() > 0L,
                )
                assertEquals(
                    "1",
                    result.nativeValues[
                        "embedded_script.module_provider.typescript_compile_count"
                    ],
                )
                assertEquals(
                    "0",
                    result.nativeValues[
                        "embedded_script.module_provider.typescript_compile_failure_count"
                    ],
                )
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun compilesAndExecutesInMemoryTypeScriptWithItsFinalValue() {
        val output = runBlocking {
            TypeScriptCompilerPluginClient(context).compileSingleSource(
                source = "const answer: number = 40; answer + 2",
                sourceName = "inline-result.ts",
            )
        }

        assertTrue(output is TypeScriptCompilationOutput.Emitted)
        output as TypeScriptCompilationOutput.Emitted
        assertTrue(output.javaScript.contains("answer + 2"))
        assertTrue(output.sourceMap.contains("inline-result.ts"))

        val source = Engines.createExecScriptSource(
            "inline-result.ts",
            "const answer: number = 40; answer + 2",
        )
        assertTrue(source is TypeScriptStringSource)
        assertTrue(TypeScriptExecutionCompiler.requiresPreparation(source))
        val terminal = executeAndAwait(source, context.cacheDir.path)

        terminal.failure.get()?.let { error ->
            throw AssertionError("In-memory TypeScript execution failed", error)
        }
        assertTrue(terminal.succeeded.get())
        assertEquals(42.0, terminal.result.get())
        assertTrue(terminal.execution.engine is LoopBasedJavaScriptEngine)
    }

    @Test
    fun compilesAndExecutesInMemoryNodeTypeScript() {
        val marker = "inline-node-typescript-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            val source = Engines.createExecScriptSource(
                "inline-node.mts",
                "const answer: number = 40 + 2; console.log('$marker:' + answer)",
            )

            assertTrue(source is TypeScriptStringSource)
            assertEquals(JavaScriptSource.ENGINE_NODEJS, source.engineName)
            assertTrue(TypeScriptExecutionCompiler.requiresPreparation(source))
            val terminal = executeAndAwait(source, context.cacheDir.path)

            terminal.failure.get()?.let { error ->
                throw AssertionError("In-memory Node TypeScript execution failed", error)
            }
            assertTrue(terminal.succeeded.get())
            val engine = terminal.execution.engine as NodePluginJavaScriptEngine
            assertTrue(requireNotNull(engine.lastPluginScriptResult).succeeded)
            assertTrue(awaitConsoleMessage(marker).contains("$marker:42"))
        } finally {
            console.clear()
        }
    }

    @Test
    fun reportsDiagnosticsForInMemoryTypeScriptWithoutExecutingIt() {
        val source = Engines.createExecScriptSource(
            "inline-diagnostic.ts",
            "const count: number = \"not-a-number\"; count",
        )
        val terminal = executeAndAwait(source, context.cacheDir.path)
        val failure = terminal.failure.get()

        assertFalse(terminal.succeeded.get())
        assertTrue(failure is ScriptCompilationException)
        failure as ScriptCompilationException
        assertTrue(
            failure.diagnostics.any { diagnostic ->
                diagnostic.sourceName == "inline-diagnostic.ts" &&
                    diagnostic.code == "TS2322"
            },
        )
        assertEquals(null, terminal.execution.engine)
    }

    @Test
    fun mapsInMemoryRuntimeErrorsBackToTheTypeScriptName() {
        val marker = "inline-typescript-runtime-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            val source = Engines.createExecScriptSource(
                "inline-runtime.ts",
                """
                    function fail(): never {
                        const message: string = "$marker"
                        throw new Error(message)
                    }
                    fail()
                """.trimIndent(),
            )
            val terminal = executeAndAwait(source, context.cacheDir.path)

            assertFalse(terminal.succeeded.get())
            assertTrue(terminal.failure.get() is RhinoException)
            val consoleMessage = awaitConsoleMessage(marker)
            assertTrue(consoleMessage.contains("inline-runtime.ts:3"))
            assertFalse(consoleMessage.contains(".generated.js"))
        } finally {
            console.clear()
        }
    }

    @Test
    fun runtimeCreatedTypeScriptTypeErrorsReturnReadableDiagnostics() {
        val marker = "typescript-node-on-demand-diagnostic-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withProject { root ->
                File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
                File(root, "package.json").writeText("""{"type":"module"}""")
                val entry = File(root, "main.mts").apply {
                    writeText(
                        """
                        export {}

                        const fsSpecifier: string = "node:" + "fs"
                        const runtimeFs = await import(fsSpecifier) as {
                            writeFileSync(path: string, data: string): void
                        }
                        runtimeFs.writeFileSync(
                            "./late-error.mts",
                            'export const value: number = "not-a-number"\n',
                        )
                        const lateTarget: string = "./late-error." + "mts"
                        let code: string = ""
                        let message: string = ""
                        try {
                            await import(lateTarget)
                        } catch (error: unknown) {
                            const typed = error as { code?: unknown; message?: unknown }
                            code = String(typed.code ?? "")
                            message = String(typed.message ?? "")
                        }
                        console.log("$marker:" + code + "|" + message.replace(/\s+/g, " "))
                        """.trimIndent(),
                    )
                }

                val launch = ScriptLaunchSourceFactory.createFileLaunch(entry)
                assertTrue(launch.source is NodeProjectLaunchSource)
                val terminal = executeAndAwait(launch.source, root.path)
                terminal.failure.get()?.let { error ->
                    val details = (error as? ScriptCompilationException)?.consoleMessage
                    throw AssertionError(
                        "On-demand TypeScript diagnostic execution failed" +
                            if (details.isNullOrBlank()) "" else "\n$details",
                        error,
                    )
                }
                assertTrue(terminal.succeeded.get())
                val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                val result = requireNotNull(engine.lastPluginScriptResult)
                assertTrue(result.succeeded)
                val consoleMessage = awaitConsoleMessage(marker)
                assertTrue(
                    "Expected a stable on-demand compilation error code, got: $consoleMessage",
                    consoleMessage.contains(
                        NodeRuntimeErrorCodes.TYPESCRIPT_ON_DEMAND_COMPILATION_FAILED,
                    ),
                )
                assertTrue(
                    "Expected late-error.mts in the compiler diagnostic, got: $consoleMessage",
                    consoleMessage.contains("late-error.mts"),
                )
                assertTrue(
                    "Expected TS2322 in the compiler diagnostic, got: $consoleMessage",
                    consoleMessage.contains("TS2322"),
                )
                assertEquals(
                    "0",
                    result.nativeValues[
                        "embedded_script.typescript.on_demand_compile.count"
                    ],
                )
                assertEquals(
                    "1",
                    result.nativeValues[
                        "embedded_script.module_provider.typescript_compile_failure_count"
                    ],
                )
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun mapsRuntimeCreatedTypeScriptFailureBackToDynamicSource() {
        val marker = "typescript-node-on-demand-map-${System.nanoTime()}"
        withProject { root ->
            File(root, "project.json").writeText("""{"type":"node","main":"main.mts"}""")
            File(root, "package.json").writeText("""{"type":"module"}""")
            val entry = File(root, "main.mts").apply {
                writeText(
                    """
                    export {}

                    const fsSpecifier: string = "node:" + "fs"
                    const runtimeFs = await import(fsSpecifier) as {
                        writeFileSync(path: string, data: string): void
                    }
                    const dynamicSource: string = [
                        "export function fail(): never {",
                        "  const message: string = '$marker'",
                        "  throw new Error(message)",
                        "}",
                        "fail()",
                        "",
                    ].join("\n")
                    runtimeFs.writeFileSync("./late-stack.mts", dynamicSource)
                    const lateTarget: string = "./late-stack." + "mts"
                    await import(lateTarget)
                    """.trimIndent(),
                )
            }

            val launch = ScriptLaunchSourceFactory.createFileLaunch(entry)
            assertTrue(launch.source is NodeProjectLaunchSource)
            val terminal = executeAndAwait(launch.source, root.path)
            assertFalse(terminal.succeeded.get())
            assertTrue(
                "Expected NodePluginScriptExecutionException, got " +
                    (terminal.failure.get()?.javaClass?.name ?: "no failure"),
                terminal.failure.get() is NodePluginScriptExecutionException,
            )
            val engine = terminal.execution.engine as NodePluginJavaScriptEngine
            val result = requireNotNull(engine.lastPluginScriptResult)
            assertFalse(result.succeeded)
            val mappedStack = requireNotNull(result.errorStack)
            assertTrue(
                "Expected dynamic TypeScript error marker, got:\n$mappedStack",
                mappedStack.contains(marker),
            )
            assertTrue(
                "Expected dynamic TypeScript frame at late-stack.mts:3, got:\n$mappedStack",
                mappedStack.contains("/late-stack.mts:3:"),
            )
            assertFalse(
                "Generated dynamic JavaScript frame leaked:\n$mappedStack",
                mappedStack.contains("late-stack.mjs"),
            )
            assertEquals(
                "1",
                result.nativeValues[
                    "embedded_script.typescript.on_demand_compile.count"
                ],
            )
            assertEquals(
                "0",
                result.nativeValues[
                    "embedded_script.module_provider.typescript_compile_failure_count"
                ],
            )
        }
    }

    @Test
    fun routesMtsAndCtsThroughCompilerBeforeNodePlugin() {
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            listOf(
                Triple("节点.mts", ".mjs", "esm"),
                Triple("node-entry.cts", ".cjs", "commonjs"),
            ).forEach { (sourceName, generatedExtension, moduleKind) ->
                val marker = "typescript-node-$moduleKind-${System.nanoTime()}"
                withSource(
                    name = sourceName,
                    source = """
                        const values: readonly number[] = [20, 22]
                        const total: number = values.reduce(
                            (sum: number, value: number): number => sum + value,
                            0,
                        )
                        console.log("$marker:" + total)
                    """.trimIndent(),
                ) { source ->
                    val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                    assertEquals(JavaScriptSource.ENGINE_NODEJS, launch.engineName)
                    val terminal = executeAndAwait(launch.source, source.parent.orEmpty())

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Compiled Node TypeScript execution failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                    val result = requireNotNull(engine.lastPluginScriptResult)
                    assertTrue(result.succeeded)
                    assertTrue(result.sourceName.endsWith(generatedExtension))
                    assertFalse(
                        "Generated JavaScript must not carry raw TypeScript policy diagnostics",
                        "embedded_script.typescript.error" in result.nativeValues,
                    )
                    assertTrue(awaitConsoleMessage(marker).contains("$marker:42"))
                }
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun executesNodeTopLevelAwaitForMtsAndTypeModuleTs() {
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            listOf(
                Triple("main.mts", ".mjs", "mts"),
                Triple("main.ts", ".js", "type-module-ts"),
            ).forEach { (sourceName, generatedExtension, format) ->
                val marker = "typescript-node-top-level-await-$format-${System.nanoTime()}"
                withProject { root ->
                    File(root, "project.json").writeText(
                        """{"type":"node","main":"$sourceName"}""",
                    )
                    File(root, "package.json").writeText("""{"type":"module"}""")
                    File(root, "tsconfig.json").writeText(
                        """{"compilerOptions":{"lib":["ES2022"],"target":"ES2022"},"files":["$sourceName"]}""",
                    )
                    val entry = File(root, sourceName).apply {
                        writeText(
                            """
                            const sleep = (milliseconds: number): Promise<void> =>
                              new Promise((resolve): void => {
                                setTimeout((): void => resolve(), milliseconds)
                              })
                            await sleep(25)
                            export const answer: number = 40 + 2
                            console.log("$marker:" + answer)
                            """.trimIndent(),
                        )
                    }

                    val launch = ScriptLaunchSourceFactory.createFileLaunch(entry)
                    assertTrue(launch.source is NodeProjectLaunchSource)
                    val terminal = executeAndAwait(launch.source, root.path)
                    terminal.failure.get()?.let { error ->
                        val details = (error as? ScriptCompilationException)?.consoleMessage
                        throw AssertionError(
                            "Node top-level await $format execution failed" +
                                if (details.isNullOrBlank()) "" else "\n$details",
                            error,
                        )
                    }
                    assertTrue(terminal.succeeded.get())
                    val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                    val result = requireNotNull(engine.lastPluginScriptResult)
                    assertTrue(result.succeeded)
                    assertTrue(result.sourceName.endsWith(generatedExtension))
                    assertEquals(
                        "precompiled_only",
                        result.nativeValues["embedded_script.typescript.preparation_mode"],
                    )
                    assertTrue(awaitConsoleMessage(marker).contains("$marker:42"))
                }
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun mapsNodeRuntimeErrorsBackToTypeScriptLineAndColumn() =
        assertNodeRuntimeErrorMapsBackToTypeScript(extension = "mts")

    @Test
    fun mapsCommonJsNodeRuntimeErrorsBackToTypeScriptLineAndColumn() =
        assertNodeRuntimeErrorMapsBackToTypeScript(extension = "cts")

    private fun assertNodeRuntimeErrorMapsBackToTypeScript(extension: String) {
        val marker = "typescript-node-$extension-runtime-map-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withSource(
                name = "node runtime map.$extension",
                source = """
                    const heading: string = "source-map"
                    function failAtTypeScriptLine(): never {
                        const message: string = "$marker"
                        throw new Error(message)
                    }
                    void heading
                    failAtTypeScriptLine()
                """.trimIndent(),
            ) { source ->
                val expectedLineNumber = 4
                val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                val terminal = executeAndAwait(launch.source, source.parent.orEmpty())
                val failure = terminal.failure.get()

                assertFalse(terminal.succeeded.get())
                assertTrue(
                    "Expected NodePluginScriptExecutionException, got " +
                        (failure?.javaClass?.name ?: "no failure"),
                    failure is NodePluginScriptExecutionException,
                )
                val engine = terminal.execution.engine as NodePluginJavaScriptEngine
                val result = requireNotNull(engine.lastPluginScriptResult)
                val mappedStack = requireNotNull(result.errorStack)
                assertTrue(
                    "Mapped Node stack did not identify the TypeScript throw location: $mappedStack",
                    mappedStack.contains("${source.name}:$expectedLineNumber:11"),
                )
                assertFalse(mappedStack.contains("autojs-typescript-entry.generated."))
                assertTrue(awaitConsoleMessage(marker).contains(marker))

                val consoleFrame = awaitConsoleMessage("${source.name}:$expectedLineNumber:")
                assertTrue(consoleFrame.contains("${source.name}:$expectedLineNumber:11"))
                val links = StackFrameParser.parse(consoleFrame)
                assertTrue(
                    "Mapped Node console frame was not linkable: <$consoleFrame>; links=$links",
                    links.any { candidate ->
                        candidate.sourceName == source.name &&
                            candidate.lineIndex == expectedLineNumber - 1
                    },
                )
                val link = links.single { candidate ->
                    candidate.sourceName == source.name &&
                        candidate.lineIndex == expectedLineNumber - 1
                }
                assertEquals(10, link.columnNumber)
                assertTrue(
                    StackFrameTargetResolver.matchesCurrentFile(
                        link.sourceName,
                        source.absolutePath,
                    ),
                )
            }
        } finally {
            console.clear()
        }
    }

    @Test
    fun nodeDirectiveTsFailsClosedUntilPackageMetadataIsAvailable() {
        val markerKey = "autojs6.typescript.node-ts-closed.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withSource(
                name = "node-directive.ts",
                source = """
                    "nodejs";
                    declare const java: any
                    java.lang.System.setProperty("$markerKey", "must-not-run")
                """.trimIndent(),
            ) { source ->
                val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                assertEquals(JavaScriptSource.ENGINE_NODEJS, launch.engineName)
                val terminal = executeAndAwait(launch.source, source.parent.orEmpty())
                val failure = terminal.failure.get()

                assertFalse(terminal.succeeded.get())
                assertTrue(failure is IllegalArgumentException)
                assertTrue(
                    failure?.message.orEmpty().contains(
                        "Single-file Node .ts compilation requires project package metadata",
                    ),
                )
                assertEquals(null, terminal.execution.engine)
                assertEquals(null, System.getProperty(markerKey))
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun exposesDeviceAndGestureApisToStrictTypeScript() {
        val markerKey = "autojs6.typescript.device-gesture.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withSource(
                name = "device-and-gesture.ts",
                source = """
                    declare const java: any
                    const snapshotWidth: number = device.width
                    const snapshotHeight: number = device.height
                    const snapshotSdk: number = device.sdkInt
                    const snapshotModel: string = device.model
                    const clickFunction: (x: number, y: number) => boolean = click
                    const swipeFunction: (
                        x1: number,
                        y1: number,
                        x2: number,
                        y2: number,
                        duration: number,
                    ) => boolean = swipe
                    const automatorClick: (x: number, y: number) => boolean = automator.click
                    const automatorSwipe: (
                        x1: number,
                        y1: number,
                        x2: number,
                        y2: number,
                        duration: number,
                    ) => boolean = automator.swipe
                    if (
                        snapshotWidth <= 0 ||
                        snapshotHeight <= 0 ||
                        snapshotSdk <= 0 ||
                        snapshotModel.length === 0
                    ) {
                        throw new Error("Invalid device snapshot")
                    }
                    if (
                        typeof clickFunction !== "function" ||
                        typeof swipeFunction !== "function" ||
                        typeof automatorClick !== "function" ||
                        typeof automatorSwipe !== "function"
                    ) {
                        throw new Error("Gesture API is unavailable")
                    }
                    java.lang.System.setProperty("$markerKey", String(snapshotSdk))
                """.trimIndent(),
            ) { source ->
                val terminal = executeAndAwait(source)

                terminal.failure.get()?.let { error ->
                    throw AssertionError("Device and gesture TypeScript smoke failed", error)
                }
                assertTrue(terminal.succeeded.get())
                assertTrue(System.getProperty(markerKey)?.toIntOrNull()?.let { it > 0 } == true)
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun dispatchesRealClickAndSwipeFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.real-gesture.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withGestureTarget { target ->
                withSource(
                    name = "real-gesture.ts",
                    source = """
                        declare const java: any
                        if (!click(${target.clickX}, ${target.clickY})) {
                            throw new Error("Coordinate click was not dispatched")
                        }
                        if (!swipe(
                            ${target.swipeStartX},
                            ${target.swipeStartY},
                            ${target.swipeEndX},
                            ${target.swipeEndY},
                            500,
                        )) {
                            throw new Error("Coordinate swipe was not dispatched")
                        }
                        java.lang.System.setProperty("$markerKey", "dispatched")
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Real TypeScript gesture smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals("dispatched", System.getProperty(markerKey))
                    assertTrue(
                        "The controlled button did not receive the TypeScript click",
                        target.clicked.await(GESTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                    assertTrue(
                        "The controlled surface did not receive the TypeScript swipe",
                        target.swiped.await(GESTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun performsLocalHttpRequestFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.http.${System.nanoTime()}"
        val expectedBody = "typescript-http-ok"
        System.clearProperty(markerKey)
        try {
            withSingleRequestHttpServer(expectedBody) { url ->
                withSource(
                    name = "http.ts",
                    source = """
                        declare const java: any
                        const response: Http.WrappedResponse = http.get(
                            ${JSONObject.quote(url)},
                            { timeout: 5000 },
                        )
                        if (response.statusCode !== 200) {
                            throw new Error("Unexpected HTTP status: " + response.statusCode)
                        }
                        const body: string = response.body.string()
                        if (body !== ${JSONObject.quote(expectedBody)}) {
                            throw new Error("Unexpected HTTP body: " + body)
                        }
                        java.lang.System.setProperty("$markerKey", body)
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("HTTP TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals(expectedBody, System.getProperty(markerKey))
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun readsAndInspectsImageFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.images.${System.nanoTime()}"
        val expectedColor = Color.rgb(18, 52, 86)
        System.clearProperty(markerKey)
        try {
            withPng(expectedColor) { imageFile ->
                withSource(
                    name = "images.ts",
                    source = """
                        declare const java: any
                        const image: ImageWrapper | null = images.read(
                            ${JSONObject.quote(imageFile.absolutePath)},
                            true,
                        )
                        if (image === null) {
                            throw new Error("Image could not be read")
                        }
                        try {
                            if (image.width !== 2 || image.height !== 2) {
                                throw new Error("Unexpected image dimensions")
                            }
                            const pixel: number = images.pixel(image, 0, 0)
                            if (pixel !== $expectedColor) {
                                throw new Error("Unexpected image pixel: " + pixel)
                            }
                            java.lang.System.setProperty("$markerKey", String(pixel))
                        } finally {
                            if (!images.recycle(image)) {
                                throw new Error("Image recycle failed")
                            }
                        }
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Images TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals(expectedColor, System.getProperty(markerKey)?.toIntOrNull())
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun returnsFalseWhenScreenCaptureConsentIsDenied() {
        val markerKey = "autojs6.typescript.capture-denied.${System.nanoTime()}"
        System.clearProperty(markerKey)
        assertFalse("PROJECT_MEDIA must not bypass the denial smoke", AppOps.isProjectMediaAccessGranted(context))
        try {
            withMediaProjectionConsent(MediaProjectionConsent.DENY) {
                withSource(
                    name = "capture-denied.ts",
                    source = """
                        declare const java: any
                        const granted: boolean = requestScreenCapture()
                        if (granted) {
                            throw new Error("Screen capture consent unexpectedly succeeded")
                        }
                        java.lang.System.setProperty("$markerKey", "denied")
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Denied screen-capture TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals("denied", System.getProperty(markerKey))
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun capturesAndInspectsScreenAfterAutomatedConsent() {
        val markerKey = "autojs6.typescript.capture.${System.nanoTime()}"
        System.clearProperty(markerKey)
        assertFalse("PROJECT_MEDIA must not bypass the consent smoke", AppOps.isProjectMediaAccessGranted(context))
        try {
            withMediaProjectionConsent(MediaProjectionConsent.ALLOW) {
                withSource(
                    name = "capture-screen.ts",
                    source = """
                        declare const java: any
                        if (!requestScreenCapture()) {
                            throw new Error("Screen capture consent was not granted")
                        }
                        const captured: ImageWrapper = captureScreen()
                        try {
                            const captureWidth: number = captured.width
                            const captureHeight: number = captured.height
                            if (captureWidth <= 0 || captureHeight <= 0) {
                                throw new Error("Captured frame has invalid dimensions")
                            }
                            const centerPixel: number = images.pixel(
                                captured,
                                Math.floor(captureWidth / 2),
                                Math.floor(captureHeight / 2),
                            )
                            java.lang.System.setProperty(
                                "$markerKey",
                                String(captureWidth) + "x" + String(captureHeight) + ":" + String(centerPixel),
                            )
                        } finally {
                            if (!images.recycle(captured)) {
                                throw new Error("Captured frame recycle failed")
                            }
                        }
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Real screen-capture TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    val marker = System.getProperty(markerKey).orEmpty()
                    assertTrue("Captured frame marker is malformed: $marker", CAPTURE_MARKER.matches(marker))
                }
            }
            assertTrue(
                "Screen-capture foreground service remained active after script teardown",
                waitUntil(SERVICE_TEARDOWN_TIMEOUT_MILLIS) {
                    !ScreenCapturerForegroundService.isForegroundStarted()
                },
            )
        } finally {
            context.stopService(Intent(context, ScreenCapturerForegroundService::class.java))
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun runsStringXmlUiFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.ui.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withInstrumentationNodeClick(UI_BUTTON_TEXT) {
                withSource(
                    name = "string-xml-ui.ts",
                    source = """
                        "ui";
                        declare const java: any
                        ui.layout(
                            '<vertical gravity="center" padding="24">' +
                                '<text id="status" text="TypeScript UI ready" textSize="20sp" />' +
                                '<button id="action" text="$UI_BUTTON_TEXT" />' +
                            '</vertical>',
                        )
                        const tsUiStatusView: UI.View | null = ui.findView("status")
                        const tsUiActionView: UI.View | null = ui.findView("action")
                        if (tsUiStatusView === null || tsUiActionView === null) {
                            throw new Error("String XML views were not inflated")
                        }
                        tsUiActionView.click(() => {
                            tsUiStatusView.setText("TypeScript UI clicked")
                            java.lang.System.setProperty("$markerKey", "clicked")
                            ui.finish()
                        })
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("String XML TypeScript UI smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals("clicked", System.getProperty(markerKey))
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun runsNativeTsxUiFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.tsx-ui.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withInstrumentationNodeClick(TSX_UI_BUTTON_TEXT) {
                withSource(
                    name = "native-ui.tsx",
                    source = """
                        "ui";
                        declare const java: any
                        ui.layout(
                            <vertical gravity="center" padding={24}>
                                <>
                                    <text id="status" textSize="20sp">TypeScript TSX ready</text>
                                    <button onClick={() => {
                                        const status: UI.View | null = ui.findView("status")
                                        if (status === null) {
                                            throw new Error("Native TSX status view was not inflated")
                                        }
                                        status.setText("TypeScript TSX clicked")
                                        java.lang.System.setProperty("$markerKey", "clicked")
                                        ui.finish()
                                    }}>$TSX_UI_BUTTON_TEXT</button>
                                </>
                            </vertical>,
                        )
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Native TSX TypeScript UI smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals("clicked", System.getProperty(markerKey))
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun typeErrorsNeverFallBackToRawRhinoExecution() {
        val markerKey = "autojs6.typescript.rejected.${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        System.clearProperty(markerKey)
        console.clear()
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
                assertTrue(
                    "Expected ScriptCompilationException, got " +
                        (failure?.javaClass?.name ?: "no failure"),
                    failure is ScriptCompilationException,
                )
                failure as ScriptCompilationException
                assertEquals(
                    context.getString(R.string.error_typescript_compilation_failed),
                    failure.message,
                )
                val diagnostic = failure.diagnostics.single { it.code == "TS2322" }
                assertEquals(source.name, diagnostic.sourceName)
                assertEquals(ScriptCompilationDiagnosticSeverity.ERROR, diagnostic.severity)
                assertTrue(diagnostic.lineNumber != null && diagnostic.lineNumber > 0)
                assertTrue(diagnostic.columnNumber != null && diagnostic.columnNumber > 0)

                val consoleMessage = awaitConsoleMessage("TS2322")
                assertTrue(consoleMessage.endsWith(failure.consoleMessage))
                assertFalse(consoleMessage.contains(TypeScriptDiagnosticsException::class.java.name))
                val link = StackFrameParser.parse(consoleMessage).single()
                assertEquals(source.name, link.sourceName)
                assertEquals(diagnostic.lineNumber!! - 1, link.lineIndex)
                assertEquals(diagnostic.columnNumber!! - 1, link.columnNumber)
                assertTrue(
                    StackFrameTargetResolver.matchesCurrentFile(
                        link.sourceName,
                        source.absolutePath,
                    ),
                )
                assertEquals(null, System.getProperty(markerKey))
                assertEquals(null, terminal.execution.engine)
            }
        } finally {
            console.clear()
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun runtimeErrorsMapBackToClickableTypeScriptSource() {
        val marker = "typescript-runtime-map-marker-${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        console.clear()
        try {
            withSource(
                name = "runtime map.ts",
                source = """
                    function failAtTypeScriptLine(): never {
                        const message: string = "$marker"
                        throw new Error(message)
                    }
                    failAtTypeScriptLine()
                """.trimIndent(),
            ) { source ->
                val expectedLineNumber = 3
                val terminal = executeAndAwait(source)
                val failure = terminal.failure.get()

                assertFalse(terminal.succeeded.get())
                assertTrue(
                    "Expected RhinoException, got " + (failure?.javaClass?.name ?: "no failure"),
                    failure is RhinoException,
                )
                assertTrue(terminal.execution.engine is LoopBasedJavaScriptEngine)

                val consoleMessage = awaitConsoleMessage(marker)
                assertTrue(consoleMessage.contains("${source.name}:$expectedLineNumber"))
                assertFalse(consoleMessage.contains(".generated.js"))
                val link = StackFrameParser.parse(consoleMessage).single { candidate ->
                    candidate.sourceName == source.name &&
                        candidate.lineIndex == expectedLineNumber - 1
                }
                assertEquals(0, link.columnNumber)
                assertTrue(
                    StackFrameTargetResolver.matchesCurrentFile(
                        link.sourceName,
                        source.absolutePath,
                    ),
                )
            }
        } finally {
            console.clear()
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

    private fun <T> withProject(block: (File) -> T): T {
        val root = File(context.cacheDir, "typescript-project-${System.nanoTime()}")
        check(root.mkdirs())
        try {
            return block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withGestureTarget(block: (GestureTarget) -> Unit) {
        ensureAccessibilityServiceStarted("real gesture target")
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        instrumentation.waitForIdleSync()
        assertAccessibilityServiceOperational("real gesture target")

        val clicked = CountDownLatch(1)
        val swiped = CountDownLatch(1)
        lateinit var overlay: FrameLayout
        lateinit var clickTarget: Button
        lateinit var swipeTarget: View
        instrumentation.runOnMainSync {
            fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

            overlay = FrameLayout(activity).apply {
                background = ColorDrawable(Color.WHITE)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            clickTarget = Button(activity).apply {
                text = "TypeScript click target"
                setOnClickListener { clicked.countDown() }
            }
            overlay.addView(
                clickTarget,
                FrameLayout.LayoutParams(dp(280), dp(72), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = dp(96)
                },
            )

            var downY = Float.NaN
            swipeTarget = View(activity).apply {
                background = ColorDrawable(Color.rgb(32, 96, 192))
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> downY = event.rawY
                        MotionEvent.ACTION_UP -> {
                            if (!downY.isNaN() && kotlin.math.abs(event.rawY - downY) >= dp(80)) {
                                swiped.countDown()
                            }
                            view.performClick()
                        }
                    }
                    true
                }
            }
            overlay.addView(
                swipeTarget,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360), Gravity.BOTTOM).apply {
                    leftMargin = dp(48)
                    rightMargin = dp(48)
                    bottomMargin = dp(96)
                },
            )
            (activity.window.decorView as ViewGroup).addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250L)

        try {
            val target = IntArray(6)
            instrumentation.runOnMainSync {
                val clickLocation = IntArray(2)
                clickTarget.getLocationOnScreen(clickLocation)
                target[0] = clickLocation[0] + clickTarget.width / 2
                target[1] = clickLocation[1] + clickTarget.height / 2

                val swipeLocation = IntArray(2)
                swipeTarget.getLocationOnScreen(swipeLocation)
                target[2] = swipeLocation[0] + swipeTarget.width / 2
                target[3] = swipeLocation[1] + swipeTarget.height * 3 / 4
                target[4] = swipeLocation[0] + swipeTarget.width / 2
                target[5] = swipeLocation[1] + swipeTarget.height / 4
            }
            assertTrue("Click target was not laid out", target[0] > 0 && target[1] > 0)
            assertTrue("Swipe target was not laid out", target[2] > 0 && target[3] > target[5])
            block(
                GestureTarget(
                    clickX = target[0],
                    clickY = target[1],
                    swipeStartX = target[2],
                    swipeStartY = target[3],
                    swipeEndX = target[4],
                    swipeEndY = target[5],
                    clicked = clicked,
                    swiped = swiped,
                ),
            )
        } finally {
            instrumentation.runOnMainSync {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }

    private fun withMediaProjectionConsent(
        consent: MediaProjectionConsent,
        block: () -> Unit,
    ) {
        withAccessibilityAutomation(
            description = "media-projection ${consent.name.lowercase()}",
            action = {
                val initialButtonIds = when (consent) {
                    MediaProjectionConsent.ALLOW -> setOf(POSITIVE_BUTTON_ID)
                    MediaProjectionConsent.DENY -> NEGATIVE_BUTTON_IDS
                }
                waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { node ->
                    node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                        node.viewIdResourceName in initialButtonIds
                }

                if (consent == MediaProjectionConsent.ALLOW) {
                    findAccessibilityNode { node ->
                        node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                            node.viewIdResourceName in SCREEN_SHARE_MODE_IDS
                    }?.let { spinner ->
                        assertTrue("Could not open the screen-share mode selector", clickNodeOrAncestor(spinner))
                        val entireScreen = waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { node ->
                            node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                                node.viewIdResourceName == SPINNER_ITEM_ID &&
                                node.text?.toString().isEntireScreenLabel()
                        }
                        assertTrue("Could not select entire-screen projection", clickNodeOrAncestor(entireScreen))
                    }
                }

                val terminalButton = waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { node ->
                    node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                        node.viewIdResourceName in initialButtonIds
                }
                assertTrue("Could not click the media-projection consent button", clickNodeOrAncestor(terminalButton))
            },
            block = block,
        )
    }

    private fun withAccessibilityNodeClick(text: String, block: () -> Unit) {
        withAccessibilityAutomation(
            description = "node text '$text'",
            action = {
                val node = waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { candidate ->
                    candidate.packageName?.toString() == context.packageName &&
                        candidate.text?.toString()?.equals(text, ignoreCase = true) == true
                }
                assertTrue("Could not click accessibility node '$text'", clickNodeOrAncestor(node))
            },
            block = block,
        )
    }

    private fun withInstrumentationNodeClick(text: String, block: () -> Unit) {
        val automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val thread = Thread({
            try {
                val node = waitForInstrumentationNode(
                    automation = automation,
                    timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS,
                ) { candidate ->
                    candidate.packageName?.toString() == context.packageName &&
                        candidate.text?.toString()?.equals(text, ignoreCase = true) == true
                }
                val clicked = clickNodeOrAncestor(node)
                Log.i(TEST_LOG_TAG, "Instrumentation node '$text' click result: $clicked")
                assertTrue("Could not click instrumentation node '$text'", clicked)
            } catch (error: Throwable) {
                Log.e(TEST_LOG_TAG, "Instrumentation node '$text' action failed", error)
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }, "typescript-ui-automation-${System.nanoTime()}").apply {
            isDaemon = true
            start()
        }
        try {
            block()
            assertTrue(
                "Instrumentation node click did not complete: '$text'",
                completed.await(ACCESSIBILITY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            )
            failure.get()?.let { error ->
                throw AssertionError("Instrumentation node click failed: '$text'", error)
            }
        } finally {
            thread.interrupt()
            thread.join(2_000L)
        }
    }

    private fun waitForInstrumentationNode(
        automation: UiAutomation,
        timeoutMillis: Long,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            val roots = automation.windows.mapNotNull { window -> window.root }.ifEmpty {
                listOfNotNull(automation.rootInActiveWindow)
            }
            val compatibilityDialogOk = findAccessibilityNode(roots) { candidate ->
                candidate.packageName?.toString() == SYSTEM_FRAMEWORK_PACKAGE &&
                    candidate.className?.toString() == BUTTON_CLASS_NAME &&
                    candidate.text?.toString()?.equals("OK", ignoreCase = true) == true
            }
            if (
                compatibilityDialogOk != null &&
                clickNodeOrAncestor(compatibilityDialogOk)
            ) {
                Log.i(TEST_LOG_TAG, "Dismissed Android app compatibility dialog")
                SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
                continue
            }
            findAccessibilityNode(roots, predicate)?.let { return it }
            SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
        }
        throw AssertionError("Instrumentation node was not found within ${timeoutMillis}ms")
    }

    private fun withAccessibilityAutomation(
        description: String,
        action: () -> Unit,
        block: () -> Unit,
    ) {
        warmUpAccessibilityService(description)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val thread = Thread({
            try {
                action()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }, "typescript-accessibility-${System.nanoTime()}").apply {
            isDaemon = true
            start()
        }
        try {
            block()
            assertTrue(
                "Accessibility automation did not complete: $description",
                completed.await(ACCESSIBILITY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            )
            failure.get()?.let { error ->
                throw AssertionError("Accessibility automation failed: $description", error)
            }
        } finally {
            thread.interrupt()
            thread.join(2_000L)
        }
    }

    private fun warmUpAccessibilityService(description: String) {
        ensureAccessibilityServiceStarted(description)
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        instrumentation.waitForIdleSync()
        try {
            assertAccessibilityServiceOperational(description)
        } finally {
            instrumentation.runOnMainSync(activity::finish)
            instrumentation.waitForIdleSync()
        }
    }

    private fun assertAccessibilityServiceOperational(description: String) {
        assertTrue(
            "AutoJs6 accessibility service did not start for $description",
            AccessibilityService.waitForStarted(ACCESSIBILITY_TIMEOUT_MILLIS),
        )
        assertTrue(
            "AutoJs6 accessibility service did not become operational for $description",
            AccessibilityService.waitForOperational(ACCESSIBILITY_TIMEOUT_MILLIS),
        )
    }

    private fun ensureAccessibilityServiceStarted(description: String) {
        if (AccessibilityService.hasInstance()) return

        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val enabledServices = services.split(':').filter(String::isNotBlank)
        assertTrue(
            "AutoJs6 accessibility service is not enabled for $description",
            AUTOJS_ACCESSIBILITY_SERVICE in enabledServices,
        )
        val servicesWithoutAutoJs = enabledServices
            .filterNot(AUTOJS_ACCESSIBILITY_SERVICE::equals)
            .joinToString(":")
        val automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        var detached = false
        try {
            assertTrue(
                "Could not refresh the AutoJs6 accessibility service for $description",
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    servicesWithoutAutoJs,
                ),
            )
            detached = true
            SystemClock.sleep(ACCESSIBILITY_BINDING_REFRESH_MILLIS)
        } finally {
            if (detached) {
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    services,
                )
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1,
                )
            }
            automation.dropShellPermissionIdentity()
        }
        assertTrue(
            "AutoJs6 accessibility service did not start for $description",
            AccessibilityService.waitForStarted(ACCESSIBILITY_TIMEOUT_MILLIS),
        )
    }

    private fun waitForAccessibilityNode(
        timeoutMillis: Long,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            findAccessibilityNode(predicate)?.let { return it }
            SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
        }
        throw AssertionError("Accessibility node was not found within ${timeoutMillis}ms")
    }

    private fun findAccessibilityNode(
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val service = AccessibilityService.instance ?: return null
        val roots = service.windows.mapNotNull { window -> window.root }.ifEmpty {
            listOfNotNull(service.rootInActiveWindow)
        }
        return findAccessibilityNode(roots, predicate)
    }

    private fun findAccessibilityNode(
        roots: List<AccessibilityNodeInfo>,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.forEach(queue::addLast)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            visited += 1
            if (predicate(node)) {
                return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        repeat(MAX_ACCESSIBILITY_ANCESTORS) {
            val current = candidate ?: return false
            if (current.isEnabled && current.isClickable &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            candidate = current.parent
        }
        return false
    }

    private fun String?.isEntireScreenLabel(): Boolean {
        val label = this?.trim().orEmpty()
        return label.equals("整个屏幕", ignoreCase = true) ||
            label.contains("entire screen", ignoreCase = true)
    }

    private fun waitUntil(timeoutMillis: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
        }
        return predicate()
    }

    private fun withPng(color: Int, block: (File) -> Unit) {
        val imageFile = File(context.cacheDir, "typescript-image-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        try {
            FileOutputStream(imageFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            block(imageFile)
        } finally {
            bitmap.recycle()
            imageFile.delete()
        }
    }

    private fun withSingleRequestHttpServer(responseBody: String, block: (String) -> Unit) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val served = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    socket.soTimeout = HTTP_TIMEOUT_MILLIS
                    val input = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val bodyBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                    val headers = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/plain; charset=utf-8\r\n")
                        append("Content-Length: ${bodyBytes.size}\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }.toByteArray(StandardCharsets.US_ASCII)
                    socket.getOutputStream().use { output ->
                        output.write(headers)
                        output.write(bodyBytes)
                        output.flush()
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                served.countDown()
            }
        }, "typescript-http-smoke").apply {
            isDaemon = true
            start()
        }
        try {
            block("http://127.0.0.1:${server.localPort}/typescript-smoke")
            assertTrue(
                "Local HTTP smoke server did not handle the request",
                served.await(HTTP_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS),
            )
            failure.get()?.let { error ->
                throw AssertionError("Local HTTP smoke server failed", error)
            }
        } finally {
            server.close()
            serverThread.join(HTTP_TIMEOUT_MILLIS.toLong())
        }
    }

    private fun executeAndAwait(source: File): ExecutionTerminal {
        val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
        assertTrue(launch.source is TypeScriptFileSource)
        return executeAndAwait(launch.source, source.parent.orEmpty())
    }

    private fun executeAndAwait(
        source: org.autojs.autojs.script.ScriptSource,
        workingDirectory: String,
    ): ExecutionTerminal {
        val completed = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        val failure = AtomicReference<Throwable>()
        val terminalResult = AtomicReference<Any?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                terminalResult.set(result)
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
        return ExecutionTerminal(execution, succeeded, failure, terminalResult)
    }

    private fun awaitConsoleMessage(fragment: String): String {
        val console = AutoJs.instance.globalConsole
        val deadline = SystemClock.uptimeMillis() + CONSOLE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            synchronized(console.logEntries) {
                console.logEntries.lastOrNull { entry -> fragment in entry.content }?.let { entry ->
                    return entry.content.toString()
                }
            }
            SystemClock.sleep(50)
        }
        fail("Console did not contain $fragment")
        error("Unreachable")
    }

    private fun awaitSystemProperty(key: String, expected: String) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(EXECUTION_TIMEOUT_SECONDS)
        while (SystemClock.uptimeMillis() < deadline) {
            if (System.getProperty(key) == expected) return
            SystemClock.sleep(50)
        }
        fail("System property $key did not become $expected")
    }

    private data class ExecutionTerminal(
        val execution: ScriptExecution,
        val succeeded: AtomicBoolean,
        val failure: AtomicReference<Throwable>,
        val result: AtomicReference<Any?>,
    )

    private data class GestureTarget(
        val clickX: Int,
        val clickY: Int,
        val swipeStartX: Int,
        val swipeStartY: Int,
        val swipeEndX: Int,
        val swipeEndY: Int,
        val clicked: CountDownLatch,
        val swiped: CountDownLatch,
    )

    private enum class MediaProjectionConsent {
        ALLOW,
        DENY,
    }

    private companion object {
        const val TEST_LOG_TAG = "TypeScriptDeviceTest"
        const val TYPESCRIPT_CACHE_DIRECTORY = "typescript-compiler-cache-v1"
        const val DEPENDENCY_LAYER_POLICY_FINGERPRINT =
            "c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a"
        const val EXECUTION_TIMEOUT_SECONDS = 90L
        const val CONSOLE_TIMEOUT_MILLIS = 5_000L
        const val HTTP_TIMEOUT_MILLIS = 10_000
        const val ACCESSIBILITY_TIMEOUT_MILLIS = 30_000L
        const val ACCESSIBILITY_POLL_MILLIS = 100L
        const val ACCESSIBILITY_BINDING_REFRESH_MILLIS = 250L
        const val GESTURE_TIMEOUT_SECONDS = 5L
        const val SERVICE_TEARDOWN_TIMEOUT_MILLIS = 10_000L
        const val MAX_ACCESSIBILITY_NODES = 512
        const val MAX_ACCESSIBILITY_ANCESTORS = 4
        const val SYSTEM_FRAMEWORK_PACKAGE = "android"
        const val BUTTON_CLASS_NAME = "android.widget.Button"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val AUTOJS_ACCESSIBILITY_SERVICE =
            "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        const val POSITIVE_BUTTON_ID = "android:id/button1"
        val NEGATIVE_BUTTON_IDS = setOf(
            "android:id/button2",
            "android:id/button3",
        )
        const val SPINNER_ITEM_ID = "android:id/text1"
        const val UI_BUTTON_TEXT = "Finish TypeScript UI"
        const val TSX_UI_BUTTON_TEXT = "Finish TypeScript TSX"
        val SCREEN_SHARE_MODE_IDS = setOf(
            "com.android.systemui:id/screen_share_mode_spinner",
            "com.android.systemui:id/screen_share_mode_options",
        )
        val CAPTURE_MARKER = Regex("[1-9][0-9]*x[1-9][0-9]*:-?[0-9]+")
    }
}
