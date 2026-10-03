package org.autojs.autojs.runtime.api.augment.compose

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.engine.NodeBridgeSampleCatalog
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.open.RunIntentActivity
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiIds
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Executes the actual packaged examples, without a parallel copy or script-side test switches. */
@RunWith(AndroidJUnit4::class)
class ComposeExamplesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service get() = AutoJs.instance.scriptEngineService
    private var prepared = false
    private var previousEnabled = false
    private var previousAccessibilityFlags = 0
    private lateinit var manifest: JSONObject

    @Before fun prepare() {
        assumeTrue("Use the isolated -PcomposeUiSpike=true host", context.packageName.endsWith(".compose.spike"))
        val plugin = context.createPackageContext(ComposeUiIds.PLUGIN_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
        val hostIndex = context.assets.open("indices/compose-examples.json").use { it.readBytes() }
        assertArrayEquals("The installed APK and host have the same example index", hostIndex,
            plugin.assets.open("examples/index.json").use { it.readBytes() })
        manifest = JSONObject(String(hostIndex, Charsets.UTF_8))
        assertEquals(1, manifest.getInt("schemaVersion"))
        val entries = manifest.getJSONArray("examples")
        val expected = (0 until entries.length()).map { entries.getJSONObject(it).getString("file") }.sorted()
        assertEquals(listOf("counter.js", "floaty-hud.js", "form.js", "list.js", "theme.js"), expected)
        val catalog = NodeBridgeSampleCatalog(
            lister = { context.assets.list(it) },
            opener = { path -> runCatching { context.assets.open(path) }.getOrNull() },
        )
        assertEquals("The existing sample catalog discovers the Compose category", expected,
            catalog.list("Compose UI").entries.map { it.name })
        expected.forEach { file ->
            assertArrayEquals("The executed sample matches the installed plugin: $file",
                plugin.assets.open("examples/$file").use { it.readBytes() },
                context.assets.open("sample/Compose UI/$file").use { it.readBytes() })
        }
        previousEnabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        instrumentation.uiAutomation.serviceInfo.also { info ->
            previousAccessibilityFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            instrumentation.uiAutomation.serviceInfo = info
        }
        prepared = true
    }

    @After fun restore() {
        if (!prepared) return
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, previousEnabled)
        instrumentation.uiAutomation.serviceInfo.also { info ->
            info.flags = previousAccessibilityFlags
            instrumentation.uiAutomation.serviceInfo = info
        }
    }

    @Test fun counterAssetIncrementsAndResetsThroughItsRealButtons() {
        Running("counter").use { running ->
            running.await("Counter initial frame") { text("counter-value") == "Count: 0" }
            repeat(3) { index ->
                click("counter-increment")
                running.await("Counter frame ${index + 1}") { text("counter-value") == "Count: ${index + 1}" }
            }
            click("counter-reset")
            running.await("Counter reset frame") { text("counter-value") == "Count: 0" }
        }
    }

    @Test fun formAssetValidatesInputAndReadsNativeSwitchAndSliderChanges() {
        Running("form").use { running ->
            running.await("Form initial frame") { node("form-save") != null && text("form-result") == "Not saved" }
            click("form-save")
            running.await("The validation message stays visible beside the focused field") {
                node("form-result")?.let { it.isVisibleToUser && it.text?.toString() == "Enter your name" } == true
            }
            assertEquals("true", running.readUi("nameField.isError"))
            assertTrue("The native editor accepts text", requireNotNull(node("form-name")).performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Ada") }))
            running.await("User input clears validation") { text("form-result") == "Ready to save" }
            running.hideKeyboard()
            // IME dismissal changes the scroll viewport asynchronously. Wait for the actual
            // controls to be visible before sending their accessibility actions.
            running.await("Form controls are visible after the keyboard hides") {
                node("form-save")?.isVisibleToUser == true && node("form-level")?.isVisibleToUser == true
            }
            click("form-notifications")
            running.await("Switch event updates its handle") { node("form-notifications")?.isChecked == true }
            assertTrue("The native slider supports set-progress", requireNotNull(node("form-level")).performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 75f) }))
            running.await("Slider event updates its label") { text("form-level-label") == "Level: 75" }
            click("form-save")
            running.await("Saved values come from all three live handles", diagnostics = {
                "visibleResult=${text("form-result")}; state=" + running.readUi(
                    "JSON.stringify({name:nameField.text,checked:notifications.checked,value:level.value,result:result.text})")
            }) {
                text("form-result") == "Saved: Ada | Notifications: on | Level: 75"
            }
            assertEquals("false", running.readUi("nameField.isError"))
        }
    }

    @Test fun listAssetScrollsAcrossOneThousandItemsAndPreservesKeysWhenReordered() {
        Running("list").use { running ->
            running.await("List initial frame") { node("list-item-0") != null }
            assertEquals("1000", running.readUi("listRef.current.children.length"))
            val stableId = running.readUi("listRef.current.children.filter(function (node) { return node.key === 'item-900'; })[0].nodeId")
            click("list-jump")
            running.await("scrollTo reaches item 900") { node("list-item-900") != null }
            click("list-reverse")
            running.await("Reordering is rendered") { text("list-order") == "Order: reverse" }
            assertEquals("The same key keeps the same mounted ID", stableId,
                running.readUi("listRef.current.children.filter(function (node) { return node.key === 'item-900'; })[0].nodeId"))
            click("list-top")
            running.await("Reversed first item is visible") { node("list-item-999") != null }
            click("list-jump")
            running.await("The jump action translates the new index") { node("list-item-900") != null }
        }
    }

    @Test fun hudAssetRunsWithoutAnActivityUpdatesFromItsWorkerAndClosesTheEngine() {
        assumeTrue("Grant overlay permission to the isolated host before running this example", Settings.canDrawOverlays(context))
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        instrumentation.waitForIdleSync()
        Running("floaty-hud").use { running ->
            assertNull("The normal script does not own an Activity", running.engine.getTag("activity"))
            running.await("A worker updates the real HUD") {
                text("hud-elapsed")?.let { Regex("Elapsed: [1-9][0-9]* s").matches(it) } == true
            }
            val views = running.ownedViews()
            assertTrue("A native overlay is attached", views.isNotEmpty())
            click("hud-reset")
            running.await("Reset affects the actual HUD") { text("hud-elapsed") == "Elapsed: 0 s" }
            click("hud-close")
            running.await("Close releases the worker and script lifetime") { running.engine.isDestroyed }
            running.await("Only this example's windows are removed") {
                running.ownedWindows().isEmpty() && running.detached(views) && node("hud-elapsed") == null
            }
        }
    }

    @Test fun themeAssetChangesSeedDarkPaletteAndDynamicColorOptions() {
        Running("theme").use { running ->
            running.await("Theme initial frame") { text("theme-summary") == "Seed: #6750A4 | Dark: false | Dynamic: false" }
            val light = running.backgroundBrightness()
            click("theme-seed")
            running.await("Next seed is applied") { text("theme-summary") == "Seed: #006C4C | Dark: false | Dynamic: false" }
            click("theme-dark")
            running.await("Dark switch is rendered") { text("theme-summary") == "Seed: #006C4C | Dark: true | Dynamic: false" }
            running.await("The renderer paints the dark Surface palette") { running.backgroundBrightness() < light - 0.3 }
            assertEquals("true", running.readUi("compose.theme().dark"))
            click("theme-dynamic")
            running.await("Dynamic colors are requested, with the documented fallback below API 31") {
                text("theme-summary") == "Seed: #006C4C | Dark: true | Dynamic: true"
            }
            assertEquals("true", running.readUi("compose.theme().dynamicColor"))
            click("theme-dark")
            running.await("Light palette can be restored") { running.backgroundBrightness() > 0.6 }
        }
    }

    private inner class Running(id: String) : Closeable {
        private val marker = "compose-example-owned-${UUID.randomUUID()}"
        private val baseline = windows()
        private val started = CountDownLatch(1)
        private val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        private val failure = AtomicReference<Throwable?>()
        private val stopping = AtomicBoolean()
        private val logs = ConcurrentLinkedQueue<String>()
        private var globalListener = false
        private var execution: ScriptExecution? = null
        val engine get() = requireNotNull(engineRef.get())
        private val logListener = object : ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) { logs += logEntry.content.toString() }
            override fun onLogClear() = Unit
        }
        private val listener = object : SimpleScriptExecutionListener() {
            private fun owned(execution: ScriptExecution) = (execution.source as? StringScriptSource)?.script?.contains(marker) == true
            override fun onStart(execution: ScriptExecution) {
                if (!owned(execution)) return
                val candidate = execution.engine as? RhinoJavaScriptEngine
                if (candidate == null) failure.set(AssertionError("Example did not use Rhino")) else {
                    engineRef.set(candidate)
                    (candidate.runtime.console as? ConsoleImpl)?.addLogListener(logListener)
                }
                started.countDown()
            }
            override fun onException(execution: ScriptExecution, error: Throwable) {
                if (!owned(execution)) return
                if (!stopping.get()) failure.compareAndSet(null, error)
                started.countDown()
            }
        }

        init {
            val entries = manifest.getJSONArray("examples")
            val entry = (0 until entries.length()).map { entries.getJSONObject(it) }.single { it.getString("id") == id }
            val original = context.assets.open("${manifest.getString("hostSamplePath")}/${entry.getString("file")}")
                .bufferedReader().use { it.readText() }
            // This inert trailing comment identifies only this engine; the sample source is unmodified.
            val source = "$original\n// $marker\n"
            try {
                if (entry.getString("mode") == "ui") {
                    globalListener = true
                    service.registerGlobalScriptExecutionListener(listener)
                    instrumentation.runOnMainSync {
                        context.startActivity(Intent(context, RunIntentActivity::class.java)
                            .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, source)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    execution = service.execute(StringScriptSource(marker, source), listener, ExecutionConfig())
                }
                assertTrue("The example's owned engine starts", started.await(30, TimeUnit.SECONDS))
                assertHealthy()
                assertNotNull(engineRef.get())
            } catch (error: Throwable) { close(); throw error }
        }

        fun assertHealthy() {
            failure.get()?.let { throw AssertionError("Packaged example failed", it) }
            logs.firstOrNull { it.startsWith("Compose UI: ") }?.let { throw AssertionError(it) }
        }
        fun await(message: String, diagnostics: (() -> String)? = null, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 25000
            do {
                assertHealthy()
                if (condition()) return
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            assertHealthy()
            fail("$message; logs=${logs.joinToString(" | ")}" + diagnostics?.let { "; ${it()}" }.orEmpty())
        }
        fun readUi(expression: String): String {
            assertNotNull("Only UI scripts are inspected on main", engine.getTag("activity"))
            var result = ""
            instrumentation.runOnMainSync {
                result = RhinoUtils.withRhinoContext(engine.runtime) { cx ->
                    org.mozilla.javascript.Context.toString(cx.evaluateString(engine.scriptable, expression, "example-inspection", 1, null))
                }
            }
            return result
        }
        fun hideKeyboard() = instrumentation.runOnMainSync {
            val activity = requireNotNull(engine.getTag("activity") as? Activity)
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        fun backgroundBrightness(): Double {
            val point = IntArray(2)
            instrumentation.runOnMainSync {
                val view = requireNotNull(engine.runtime.ui.view)
                view.getLocationOnScreen(point)
                // The theme sample has 20 dp of content padding. Sample its empty side margin,
                // away from text, controls, system bars and edge-to-edge navigation scrims.
                point[0] += (8 * context.resources.displayMetrics.density).toInt()
                point[1] += view.height / 2
            }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            return try {
                val color = bitmap.getPixel(point[0].coerceIn(0, bitmap.width - 1), point[1].coerceIn(0, bitmap.height - 1))
                (Color.red(color) + Color.green(color) + Color.blue(color)) / 765.0
            } finally { bitmap.recycle() }
        }
        fun ownedWindows(): Set<FloatyWindow> = windows() - baseline
        fun ownedViews(): List<View> {
            var views = emptyList<View>()
            instrumentation.runOnMainSync { views = ownedWindows().mapNotNull { it.windowView } }
            return views
        }
        fun detached(views: List<View>): Boolean {
            var result = false
            instrumentation.runOnMainSync { result = views.all { !it.isAttachedToWindow && it.parent == null } }
            return result
        }
        override fun close() {
            try {
                stopping.set(true)
                val owned = engineRef.get() ?: execution?.engine as? RhinoJavaScriptEngine
                if (owned != null) {
                    if (!owned.isDestroyed) {
                        val activity = owned.getTag("activity") as? Activity
                        if (activity != null) instrumentation.runOnMainSync { activity.finish() } else owned.forceStop()
                        val deadline = SystemClock.uptimeMillis() + 15000
                        while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                        if (!owned.isDestroyed) owned.forceStop()
                    }
                    (owned.runtime.console as? ConsoleImpl)?.removeLogListener(logListener)
                    // This is the owned engine's registry, never the process-wide service registry.
                    owned.runtime.floaty.closeAll()
                }
            } finally {
                if (globalListener) service.unregisterGlobalScriptExecutionListener(listener)
                instrumentation.waitForIdleSync()
            }
        }
    }

    private fun windows(): Set<FloatyWindow> {
        val field = FloatyService::class.java.getDeclaredField("windows").apply { isAccessible = true }
        return (field.get(null) as Collection<*>).filterIsInstance<FloatyWindow>().toSet()
    }
    private fun text(tag: String): String? = node(tag)?.text?.toString()
    private fun click(tag: String) = assertTrue("$tag exposes a click action",
        requireNotNull(node(tag)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    private fun node(tag: String): AccessibilityNodeInfo? {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.forEach(pending::add)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue
            if (node.viewIdResourceName == tag) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::add)
        }
        return null
    }
}
