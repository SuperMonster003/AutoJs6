package org.autojs.autojs.runtime.api.augment.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.UiAutomation
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.typescript.TypeScriptCompilationOutput
import org.autojs.autojs.core.plugin.typescript.TypeScriptCompilerPluginClient
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.plugin.compose.api.ComposeUiIds
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Real .tsx files use the installed compiler and its declarations, then the normal launch path. */
@RunWith(AndroidJUnit4::class)
class ComposeTsxDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val service get() = AutoJs.instance.scriptEngineService
    private val previousEnabled = linkedMapOf<String, Boolean>()
    private var previousAccessibilityFlags: Int? = null

    @Before fun prepare() {
        assumeTrue("Use the isolated -PcomposeUiSpike=true host", context.packageName.endsWith(".compose.spike"))
        for (packageName in listOf(ComposeUiIds.PLUGIN_PACKAGE_NAME, COMPILER_PACKAGE)) {
            context.packageManager.getApplicationInfo(packageName, 0)
            previousEnabled[packageName] = PluginEnableStore.isEnabled(context, packageName)
            PluginEnableStore.setEnabled(context, packageName, true)
        }
        automation.serviceInfo.also { info ->
            previousAccessibilityFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            automation.serviceInfo = info
        }
        assertTrue("The real TypeScript Engine must be available and authorized", runBlocking {
            TypeScriptCompilerPluginClient(context).isAvailable()
        })
    }

    @After fun restore() {
        previousEnabled.forEach { (packageName, enabled) -> PluginEnableStore.setEnabled(context, packageName, enabled) }
        previousAccessibilityFlags?.let { flags ->
            automation.serviceInfo.also { info -> info.flags = flags; automation.serviceInfo = info }
        }
    }

    @Test fun realCompilerCreatesAnIndependentDialogSessionAndPreservesCallbacks() {
        Running("dialog", ui = true).use { running ->
            assertTrue(running.javaScript.contains("compose.dialog"))
            assertTrue(running.javaScript.contains("__autojs6Tsx(compose.Column"))
            running.await("A real compiled TSX render opens the Material sheet") {
                text("tsx-dialog-count") == "TSX dialog count: 0"
            }
            clickTag("tsx-dialog-increment")
            running.await("The dialog uses the usual reactive session and callback path") {
                text("tsx-dialog-count") == "TSX dialog count: 1" && running.property("count") == "1"
            }
            clickTag("tsx-dialog-close")
            running.await("Normal session.close and its receiver work from compiled TSX") {
                running.property("closed") == "true" && tag("tsx-dialog-count") == null
            }
        }
    }

    @Test fun realCompilerBridgesXmlComposeContainersAndInflatedTsxAndroidViews() {
        Running("interop", ui = true).use { running ->
            assertTrue(running.javaScript.contains("__autojs6Tsx(compose.AndroidView"))
            assertTrue(running.javaScript.contains("\"compose\""))
            running.await("Compiled XML compose container and AndroidView are visible") {
                text("tsx-interop-count") == "F2 TSX count: 0" && node { it.text?.toString() == "F2 inflated TSX native button" } != null
            }
            assertEquals("1", running.property("factories"))
            repeat(2) { index ->
                clickNode(requireNotNull(node { it.text?.toString() == "F2 inflated TSX native button" }))
                running.await("Native TSX click survives mixed-domain rejection and updates Compose") {
                    text("tsx-interop-count") == "F2 TSX count: ${index + 1}" && running.property("clicks") == "${index + 1}"
                }
            }
            assertEquals("Saving the node preserves its native View across render", "1", running.property("factories"))
        }
    }

    @Test fun realCompilerPreservesFactoriesNamespaceSlotsStateCallbacksAndRefs() {
        Running("counter", ui = true).use { running ->
            assertTrue(running.javaScript.contains("__autojs6Tsx(compose.Text"))
            assertTrue(running.javaScript.contains("__autojs6Tsx(Increment"))
            assertTrue(running.javaScript.contains("\"compose:Column\""))
            assertTrue(running.javaScript.contains("__autojs6TsxFragment"))
            running.await("Member, namespace and nested slot TSX render") {
                text("tsx-count") == "Count: 0" && text("tsx-namespace") == "Qualified TSX" &&
                    node { it.text?.toString() == "TSX slot title" } != null
            }
            var stableId: String? = null
            for (count in 1..3) {
                clickTag("tsx-increment")
                running.await("The compiled native click updates state and publishes frame $count") {
                    text("tsx-count") == "Count: $count" && running.property("count") == "$count"
                }
                assertEquals("The callback receiver is the published ref", "true", running.property("ref"))
                if (stableId == null) stableId = running.property("node")
                assertEquals("A keyed button keeps its identity across TSX renders", stableId, running.property("node"))
            }
        }
    }

    @Test fun mixedTsxRenderRejectsBeforeReplacingComposeAndRetainedCallbackRecovers() {
        Running("mixed-render", ui = true).use { running ->
            running.await("The initial Compose page renders") { text("tsx-retained-count") == "Phase: 0" }
            val retained = requireNotNull(tag("tsx-advance"))
            assertTrue(retained.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            running.await("A mixed render reports the typed boundary error") { running.property("mixed") == "INVALID_ARGUMENT" }
            assertEquals("Phase: 0", text("tsx-retained-count"))
            assertFalse(running.engine.isDestroyed)
            assertTrue("The original native callback remains attached", retained.refresh())
            assertTrue(retained.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            running.await("The same callback recovers with the next valid TSX frame") {
                text("tsx-retained-count") == "Phase: 2" && running.property("phase") == "2"
            }
        }
    }

    @Test fun legacyXmlFragmentsAndClickSurviveRejectedComposeChildren() {
        Running("legacy-xml", ui = true).use { running ->
            assertTrue(running.javaScript.contains("__autojs6Tsx(\"vertical\""))
            running.await("The legacy XML TSX page renders") { node { it.text?.toString() == "Legacy TSX ready" } != null }
            for (count in 1..2) {
                clickNode(requireNotNull(node { it.text?.toString()?.equals("Legacy TSX click", ignoreCase = true) == true }))
                running.await("The real legacy click survives mixed-tree rejection $count") {
                    running.property("clicks") == "$count" && node { it.text?.toString() == "Legacy clicks: $count" } != null
                }
                assertEquals("INVALID_ARGUMENT", running.property("mixed"))
                assertFalse("Rejected mixed XML preserves its live UI engine", running.engine.isDestroyed)
            }
        }
    }

    @Test fun aCompiledTsxMountWithoutUiModeReportsThePublicTypedError() {
        Running("non-ui", ui = false).use { running ->
            running.await("The real non-UI TSX source reaches its typed error") { running.property("error") == "UI_MODE_REQUIRED" }
            running.awaitSuccess()
            assertNull("No UI Activity was created for the source", running.engine.getTag("activity"))
        }
    }

    @Test fun fragmentsRespectSingleRootAndSingleSlotAndNeverInvokeUnknownFunctions() {
        Running("fragment-boundaries", ui = true).use { running ->
            running.await("Single-root and zero/one-slot fragments render and boundary checks finish") {
                running.property("ready") == "true" && text("tsx-fragment-page") == "Fragment page retained" &&
                    node { it.text?.toString() == "Single slot fragment" } != null
            }
            for (name in listOf("roots", "slots", "function")) assertEquals(name, "INVALID_ARGUMENT", running.property(name))
            assertEquals("An unregistered function component must never be called", "0", running.property("calls"))
            assertFalse(running.engine.isDestroyed)
        }
    }

    @Test fun compiledTsxFloatyRunsWithoutActivityUpdatesAndClosesItsOwnedWindow() {
        assertTrue("Grant overlay permission to this owned test host before running TSX", Settings.canDrawOverlays(context))
        Running("floaty", ui = false).use { running ->
            assertNull("A TSX floating script does not require an Activity", running.engine.getTag("activity"))
            running.await("The compiled TSX floating window renders") { text("tsx-floaty-count") == "Floaty: 0" }
            clickTag("tsx-floaty-increment")
            running.await("The non-UI event dispatcher commits its next TSX frame") {
                running.property("count") == "1" && text("tsx-floaty-count") == "Floaty: 1"
            }
            clickTag("tsx-floaty-close")
            running.await("Close releases the owned TSX window and script lifetime") {
                running.property("closed") == "true" && running.engine.isDestroyed && tag("tsx-floaty-count") == null
            }
            running.awaitSuccess()
        }
    }

    private inner class Running(asset: String, ui: Boolean) : Closeable {
        private val marker = "compose-tsx-${UUID.randomUUID()}"
        private val root = File(context.cacheDir, marker)
        private val started = CountDownLatch(1)
        private val completed = CountDownLatch(1)
        private val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        private val failure = AtomicReference<Throwable?>()
        private val succeeded = AtomicBoolean()
        private val stopping = AtomicBoolean()
        private var execution: ScriptExecution? = null
        private val logs = ConcurrentLinkedQueue<String>()
        val engine get() = requireNotNull(engineRef.get())
        val javaScript: String
        private val logListener = object : ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) { logs += logEntry.content.toString() }
            override fun onLogClear() = Unit
        }
        private val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                val owned = execution.engine as? RhinoJavaScriptEngine
                if (owned == null) failure.compareAndSet(null, AssertionError("The compiled TSX did not use Rhino"))
                else {
                    engineRef.set(owned)
                    (owned.runtime.console as? ConsoleImpl)?.addLogListener(logListener)
                }
                started.countDown()
            }
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                succeeded.set(true)
                completed.countDown()
            }
            override fun onException(execution: ScriptExecution, error: Throwable) {
                if (!stopping.get()) failure.compareAndSet(null, error)
                started.countDown()
                completed.countDown()
            }
        }

        init {
            check(root.mkdirs())
            try {
                val source = File(root, "$asset.tsx")
                val sourceText = instrumentation.context.assets.open("compose-tsx/$asset.tsx")
                    .bufferedReader().use { it.readText() }.replace("__TSX_MARKER__", marker)
                source.writeText(sourceText, Charsets.UTF_8)
                // This preflight has no cache and no declaration injection. The script below
                // is still the original .tsx and the normal launch path compiles it itself.
                val output = runBlocking {
                    TypeScriptCompilerPluginClient(context).compileSingleFile(source, usePersistentCache = false)
                }
                assertTrue("The installed compiler must emit strict TSX against its real declaration layer: $output",
                    output is TypeScriptCompilationOutput.Emitted)
                val emitted = output as TypeScriptCompilationOutput.Emitted
                assertTrue(emitted.diagnostics.none { it.isError })
                javaScript = emitted.javaScript
                assertTrue("The controlled compiler factory appears in the real output", javaScript.contains("__autojs6Tsx("))
                if (ui) assertTrue("Compilation retains the UI directive", javaScript.contains("\"ui\";"))
                val sources = JSONObject(emitted.sourceMap).getJSONArray("sources")
                assertTrue("The real source map identifies the .tsx input", (0 until sources.length()).any {
                    sources.getString(it).endsWith("/$asset.tsx") || sources.getString(it) == "$asset.tsx"
                })
                val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                assertTrue("The executable input remains TypeScript, not the preflight JavaScript", launch.source is TypeScriptFileSource)
                execution = service.execute(launch.source, listener, ExecutionConfig(workingDirectory = root.path))
                assertTrue("The owned TSX engine starts", started.await(90, TimeUnit.SECONDS))
                assertHealthy()
                assertNotNull(engineRef.get())
                if (ui) assertNotNull("The source UI directive creates the normal script Activity", engine.getTag("activity"))
            } catch (error: Throwable) { close(); throw error }
        }

        fun property(name: String): String? = System.getProperty("$marker.$name")
        fun assertHealthy() { failure.get()?.let { throw AssertionError("Real TSX execution failed; logs=${logs.joinToString(" | ")}", it) } }
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 30000
            do {
                assertHealthy()
                if (condition()) return
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            assertHealthy()
            fail("$message; logs=${logs.joinToString(" | ")}")
        }
        fun awaitSuccess() {
            assertTrue("The owned TSX execution terminates", completed.await(30, TimeUnit.SECONDS))
            assertHealthy()
            assertTrue("The real .tsx source completes normally", succeeded.get())
        }
        override fun close() {
            stopping.set(true)
            try {
                val owned = engineRef.get() ?: execution?.engine as? RhinoJavaScriptEngine
                if (owned != null) {
                    if (!owned.isDestroyed) {
                        val activity = owned.getTag("activity") as? Activity
                        if (activity == null) owned.forceStop() else instrumentation.runOnMainSync { activity.finish() }
                        val deadline = SystemClock.uptimeMillis() + 15000
                        while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                        if (!owned.isDestroyed) owned.forceStop()
                    }
                    (owned.runtime.console as? ConsoleImpl)?.removeLogListener(logListener)
                    owned.runtime.floaty.closeAll()
                }
            } finally {
                instrumentation.waitForIdleSync()
                System.getProperties().stringPropertyNames().filter { it.startsWith("$marker.") }.forEach(System::clearProperty)
                root.deleteRecursively()
            }
        }
    }

    private fun tag(name: String) = node { it.viewIdResourceName == name }
    private fun text(name: String) = tag(name)?.text?.toString()
    private fun clickTag(name: String) = clickNode(requireNotNull(tag(name)) { "Missing TSX tag $name" })
    private fun clickNode(node: AccessibilityNodeInfo) {
        var candidate: AccessibilityNodeInfo? = node
        repeat(5) {
            val current = candidate ?: return@repeat
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            candidate = current.parent
        }
        fail("The real TSX node has no working click action: ${node.text}")
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        automation.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == context.packageName }.forEach(pending::add)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 1024) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::add)
        }
        return null
    }

    private companion object {
        const val COMPILER_PACKAGE = "io.github.supermonster003.autojs6.plugin.typescript"
    }
}
