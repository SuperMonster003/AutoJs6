package org.autojs.autojs.runtime.api.augment.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.compose.ComposeUiPluginHost
import org.autojs.autojs.core.plugin.compose.assertGraphicsPathNativeLibraryInitialization
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.open.RunIntentActivity
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real engine/renderer lifetimes. Package mutation is opt-in and performed by the owned AVD runner. */
@RunWith(AndroidJUnit4::class)
class ComposeRobustnessDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service get() = AutoJs.instance.scriptEngineService
    private var prepared = false
    private var previousEnabled = false
    private var previousAccessibilityFlags = 0

    @Before fun prepare() {
        assumeTrue("Use the isolated -PcomposeUiSpike=true host", context.packageName.endsWith(".compose.spike"))
        context.packageManager.getApplicationInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0)
        previousEnabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        instrumentation.uiAutomation.serviceInfo.also {
            previousAccessibilityFlags = it.flags
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            instrumentation.uiAutomation.serviceInfo = it
        }
        prepared = true
    }

    @After fun restore() {
        if (!prepared) return
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, previousEnabled)
        instrumentation.uiAutomation.serviceInfo.also {
            it.flags = previousAccessibilityFlags
            instrumentation.uiAutomation.serviceInfo = it
        }
    }

    @Test fun oneHundredMountCloseCyclesReleaseAcceptedRefsViewsAndCallbacks() {
        Running("""
            var cycles = 0, closed = 0;
            function cycle() {
                var ref = compose.ref();
                var node = compose.Text({ref: ref, testTag: 'compose-robustness-cycle', text: 'Cycle ' + cycles});
                var session = compose.mount(node);
                session.on('close', function () { closed++; });
                session.post(function () {
                    check(ref.current === node, 'The accepted frame publishes its ref');
                    var view = ui.view;
                    session.close();
                    check(session.isClosed() && session.root === null && ref.current === null, 'Close releases handles');
                    check(view.getParent() === null && view.getChildCount() === 0, 'Close disposes the native view');
                    cycles++;
                    if (cycles < 100) compose.post(cycle);
                    else report({cycles: cycles, closed: closed, sessions: compose.sessions.length});
                }, 30);
            }
            cycle();
        """).use { running ->
            running.await("One hundred native mount/close cycles", 60000) { running.reportOrNull()?.optInt("cycles") == 100 }
            assertEquals(100, running.report().getInt("closed"))
            assertEquals(0, running.report().getInt("sessions"))
            assertNull(node("cycle"))
        }
    }

    @Test fun closingInTheFirstEventHandlerCancelsRemainingHandlersAndReleasesItsView() {
        Running("""
            var ref = compose.ref(), first = 0, forbidden = 0, closed = 0;
            var button = compose.Button({ref: ref, testTag: 'compose-robustness-close', onClick: function () {
                first++;
                session.close();
                report({first: first, forbidden: forbidden, closed: closed, released: ref.current === null});
            }}, 'Close');
            button.on('click', function () { forbidden++; report({forbidden: forbidden}); });
            var session = compose.mount(button);
            session.on('close', function () { closed++; });
        """).use { running ->
            running.await("Close control is attached") { node("close") != null }
            val view = running.uiView()
            click("close")
            running.await("Callback closes its owner") { running.reportOrNull()?.optInt("closed") == 1 }
            assertEquals(1, running.report().getInt("first"))
            assertEquals(0, running.report().getInt("forbidden"))
            assertTrue(running.report().getBoolean("released"))
            assertDisposed(view)
            assertNull(node("close"))
        }
    }

    @Test fun aWorkerMustPostStateChangesBackToTheOwningUiEngine() {
        Running("""
            var state = compose.state(0);
            compose.mount(function () {
                return compose.Text({testTag: 'compose-robustness-worker', text: String(state.value)});
            });
            threads.start(function () {
                var rejected;
                try { state.value = 99; } catch (error) { rejected = error.code; }
                compose.post(function () {
                    check(rejected === 'INVALID_ARGUMENT' && state.value === 0, 'Direct worker mutation is rejected without changing state');
                    state.value = 1;
                    report({code: rejected});
                });
            });
        """).use { running ->
            running.await("Worker-posted state reaches the owner") { node("worker")?.text?.toString() == "1" }
            assertEquals("INVALID_ARGUMENT", running.report().getString("code"))
        }
    }

    @Test fun stoppingDuringANonUiRenderReleasesTheOverlayWithoutPublishingItsLateTree() {
        assumeTrue("Grant overlay permission to the isolated host", Settings.canDrawOverlays(context))
        Running("""
            var state = compose.state(0);
            var window = compose.floaty(function renderUntilStopped() {
                if (state.value === 1) {
                    runtime.getProperty('compose.robustness.entered').countDown();
                    try { runtime.getProperty('compose.robustness.gate').await(); }
                    finally { runtime.getProperty('compose.robustness.exited').incrementAndGet(); }
                }
                return compose.Text({testTag: 'compose-robustness-stopping', text: 'Render ' + state.value});
            }, {raw: true, x: 40, y: 100, width: 240, height: 120});
            setInterval(function () {
                if (String(runtime.getProperty('compose.robustness.action')) === 'render') {
                    runtime.putProperty('compose.robustness.action', 'entered');
                    state.value = 1;
                }
            }, 20);
        """, ui = false).use { running ->
            running.await("The initial overlay is attached") { node("stopping")?.text?.toString() == "Render 0" }
            val views = running.overlayViews()
            assertTrue(views.isNotEmpty())
            running.action("render")
            assertTrue("The script thread enters render before force-stop", running.entered.await(20, TimeUnit.SECONDS))
            running.stop()
            assertEquals(1, running.exited.get())
            running.await("Stopped render releases all owned native overlays") {
                var detached = false
                instrumentation.runOnMainSync { detached = views.all { !it.isAttachedToWindow && it.parent == null } }
                detached && running.ownedWindows().isEmpty()
            }
            assertNull(node("stopping"))
        }
    }

    @Test fun rotationPreservesTheExistingUiEngineAndActivityRecreationDestroysItsOwner() {
        Running(counterScript("rotation")).use { running ->
            running.await("Rotation counter is attached") { node("rotation")?.text?.toString() == "Count 0" }
            val activity = running.activity()
            val previous = activity.requestedOrientation
            val orientation = activity.resources.configuration.orientation
            val target = if (orientation == Configuration.ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            try {
                instrumentation.runOnMainSync {
                    activity.requestedOrientation = if (target == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                running.await("The host receives the real orientation configuration change") { activity.resources.configuration.orientation == target }
                assertFalse("Manifest configChanges preserves the existing script engine", running.engine.isDestroyed)
                click("increment")
                running.await("State still updates after rotation") { node("rotation")?.text?.toString() == "Count 1" }
            } finally { instrumentation.runOnMainSync { activity.requestedOrientation = previous } }
            val view = running.uiView()
            running.expectStopping()
            instrumentation.runOnMainSync { activity.recreate() }
            running.await("An actual Activity onDestroy destroys the original engine") { running.engine.isDestroyed }
            instrumentation.waitForIdleSync()
            assertDisposed(view)
        }
    }

    @Suppress("DEPRECATION")
    @Test fun aCriticalTrimMemoryNotificationLeavesTheActiveTreeAbleToUpdate() {
        Running(counterScript("trim")).use { running ->
            running.await("Trim-memory counter is attached") { node("trim")?.text?.toString() == "Count 0" }
            val delivered = CountDownLatch(1)
            val receivedLevels = ConcurrentLinkedQueue<Int>()
            val observer = object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    receivedLevels += level
                    if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) delivered.countDown()
                }
                override fun onConfigurationChanged(configuration: Configuration) = Unit
                override fun onLowMemory() = Unit
            }
            val application = context.applicationContext
            instrumentation.runOnMainSync { application.registerComponentCallbacks(observer) }
            try {
                val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                    "am send-trim-memory ${context.packageName} RUNNING_CRITICAL",
                )).bufferedReader().use { it.readText() }
                assertTrue("System trim-memory command was rejected: $output", output.isBlank())
                assertTrue("The system must deliver RUNNING_CRITICAL; received=$receivedLevels; output=$output",
                    delivered.await(15, TimeUnit.SECONDS))
                click("increment")
                running.await("Existing callbacks and renderer survive system memory notification") { node("trim")?.text?.toString() == "Count 1" }
                assertFalse(running.engine.isDestroyed)
            } finally {
                instrumentation.runOnMainSync { application.unregisterComponentCallbacks(observer) }
            }
        }
    }

    @Test fun disablingThePluginClosesTheActiveSessionWithItsSpecificGateError() {
        Running(packageScript(), expectedError = "PLUGIN_DISABLED").use { running ->
            running.await("The watched page is attached") { node("package")?.text?.toString() == "Original" }
            val view = running.uiView()
            PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, false)
            running.await("Center disable invalidates the live session") { running.reportOrNull()?.optInt("closed") == 1 }
            assertEquals(1, running.expectedErrors().size)
            assertTrue(running.report().getBoolean("released"))
            assertFalse(running.report().getBoolean("available"))
            assertDisposed(view)
            PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
            running.action("remount")
            running.await("The same engine can mount after the gate is restored") { node("package")?.text?.toString() == "Updated" }
            assertEquals(1, running.report().getInt("closed"))
        }
    }

    /**
     * Run only on an owned AVD, passing composeRobustnessPackageChange=update|uninstall.
     * The external runner waits for the READY status before replacing/removing the installed APK.
     * No package mutation or broad process cleanup is performed by this test.
     */
    @Test fun packageMutationClosesActiveSessionAndUpdateCanRemount() {
        val mutation = InstrumentationRegistry.getArguments().getString("composeRobustnessPackageChange")
        assumeTrue("Opt in to owned-AVD package mutation", mutation == "update" || mutation == "uninstall")
        ComposeUiPluginHost(context).use { host ->
            val before = host.load()
            assertGraphicsPathNativeLibraryInitialization(before)
            Running(packageScript(), expectedError = "PLUGIN_UNAVAILABLE").use { running ->
                running.await("The old APK's active page is attached") { node("package")?.text?.toString() == "Original" }
                val view = running.uiView()
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("compose.robustness.phase", if (mutation == "update") "READY_FOR_PLUGIN_UPDATE" else "READY_FOR_PLUGIN_UNINSTALL")
                    putString("compose.robustness.package", ComposeUiIds.PLUGIN_PACKAGE_NAME)
                    putString("compose.robustness.previousApk", before.identity.apk)
                })
                running.await("APK mutation closes the old session", 90000) { running.reportOrNull()?.optInt("closed") == 1 }
                assertEquals("Only one terminal unavailable report", 1, running.expectedErrors().size)
                assertTrue(running.report().getBoolean("released"))
                assertFalse("Package changes do not terminate the whole script", running.engine.isDestroyed)
                assertDisposed(view)
                if (mutation == "update") {
                    running.action("remount")
                    running.await("The same engine mounts the newly installed APK", 30000) { node("package")?.text?.toString() == "Updated" }
                    val after = host.load()
                    assertNotEquals("Replacement changes the package identity epoch", before.identity, after.identity)
                    assertNotSame("Replacement creates a new class loader", before.loader, after.loader)
                    assertGraphicsPathNativeLibraryInitialization(after)
                    assertEquals(1, running.report().getInt("closed"))
                } else {
                    assertFalse(host.isAvailable())
                    assertFalse(running.report().getBoolean("available"))
                }
                instrumentation.sendStatus(0, Bundle().apply { putString("compose.robustness.phase", "PACKAGE_CHANGE_VERIFIED") })
            }
        }
    }

    private fun counterScript(tag: String) = """
        var count = compose.state(0);
        compose.mount(function () {
            return compose.Column({},
                compose.Text({key: 'label', testTag: 'compose-robustness-$tag', text: 'Count ' + count.value}),
                compose.Button({key: 'button', testTag: 'compose-robustness-increment', onClick: function () { count.value++; }}, 'Increment'));
        });
    """

    private fun packageScript() = """
        var ref = compose.ref(), closed = 0;
        var page = compose.mount(compose.Text({ref: ref, testTag: 'compose-robustness-package', text: 'Original'}));
        page.on('close', function () {
            closed++;
            report({closed: closed, released: ref.current === null && page.isClosed() && page.root === null, available: compose.isAvailable()});
        });
        setInterval(function () {
            if (String(runtime.getProperty('compose.robustness.action')) === 'remount') {
                runtime.putProperty('compose.robustness.action', 'done');
                var fresh = compose.mount(compose.Text({testTag: 'compose-robustness-package', text: 'Updated'}));
                report({closed: closed, mounted: !fresh.isClosed()});
            }
        }, 30);
    """

    private inner class Running(source: String, private val ui: Boolean = true, private val expectedError: String? = null) : Closeable {
        private val marker = "compose-robustness-owned-${UUID.randomUUID()}"
        private val started = CountDownLatch(1)
        private val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        private val failure = AtomicReference<Throwable?>()
        private val stopping = AtomicBoolean()
        private val baseline = windows()
        private var execution: ScriptExecution? = null
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val exited = AtomicInteger()
        val logs = ConcurrentLinkedQueue<String>()
        val engine get() = requireNotNull(engineRef.get())
        private val consoleListener = object : ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) { logs += logEntry.content.toString() }
            override fun onLogClear() = Unit
        }
        private val listener = object : SimpleScriptExecutionListener() {
            private fun owned(execution: ScriptExecution) = (execution.source as? StringScriptSource)?.script?.contains(marker) == true
            override fun onStart(execution: ScriptExecution) {
                if (!owned(execution)) return
                val current = execution.engine as? RhinoJavaScriptEngine
                if (current == null) failure.set(AssertionError("Owned script did not use Rhino")) else {
                    engineRef.set(current)
                    current.runtime.putProperty("compose.robustness.entered", entered)
                    current.runtime.putProperty("compose.robustness.gate", gate)
                    current.runtime.putProperty("compose.robustness.exited", exited)
                    current.runtime.putProperty("compose.robustness.action", "idle")
                    (current.runtime.console as? ConsoleImpl)?.addLogListener(consoleListener)
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
            val script = (if (ui) "\"ui\";\n" else "") + "// $marker\n" + """
                function check(value, message) { if (!value) throw new Error(message); }
                function report(value) { runtime.putProperty('compose.robustness.report', JSON.stringify(value)); }
            """.trimIndent() + "\n" + source.trimIndent()
            if (ui) service.registerGlobalScriptExecutionListener(listener)
            try {
                if (ui) instrumentation.runOnMainSync {
                    context.startActivity(Intent(context, RunIntentActivity::class.java)
                        .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, script)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else execution = service.execute(StringScriptSource(marker, script), listener, ExecutionConfig())
                assertTrue("The owned script engine starts", started.await(30, TimeUnit.SECONDS))
                assertHealthy()
                assertNotNull(engineRef.get())
            } catch (error: Throwable) { close(); throw error }
        }

        fun action(value: String) { engine.runtime.putProperty("compose.robustness.action", value) }
        fun activity() = requireNotNull(engine.getTag("activity") as? Activity)
        fun uiView(): View { lateinit var result: View; instrumentation.runOnMainSync { result = requireNotNull(engine.runtime.ui.view) }; return result }
        fun ownedWindows() = windows() - baseline
        fun overlayViews(): List<View> { var result = emptyList<View>(); instrumentation.runOnMainSync { result = ownedWindows().mapNotNull { it.windowView } }; return result }
        fun reportOrNull(): JSONObject? = engine.runtime.getProperty("compose.robustness.report")?.toString()?.let(::JSONObject)
        fun report() = requireNotNull(reportOrNull()) { "Script did not report a result" }
        fun expectedErrors() = logs.filter { it.lineSequence().firstOrNull() == "3-Block Compose: $expectedError" }
        fun assertHealthy() {
            failure.get()?.let { throw AssertionError("Owned robustness script failed", it) }
            if (stopping.get()) return
            val errors = logs.filter { it.startsWith("3-Block Compose: ") }
            errors.firstOrNull { expectedError == null || it.lineSequence().firstOrNull() != "3-Block Compose: $expectedError" }?.let { throw AssertionError(it) }
            if (errors.size > 1) throw AssertionError(errors.joinToString("\n"))
        }
        fun await(message: String, timeout: Long = 20000, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + timeout
            do {
                assertHealthy()
                if (condition()) return
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            assertHealthy()
            fail("$message; report=${reportOrNull()}; logs=${logs.joinToString(" | ")}")
        }
        fun expectStopping() { stopping.set(true) }
        fun stop() {
            expectStopping()
            if (!engine.isDestroyed) engine.forceStop()
            await("Only the owned engine is stopped", 15000) { engine.isDestroyed }
            instrumentation.waitForIdleSync()
        }
        override fun close() {
            try {
                stopping.set(true)
                gate.countDown()
                val owned = engineRef.get() ?: execution?.engine as? RhinoJavaScriptEngine
                if (owned != null) {
                    if (!owned.isDestroyed) {
                        val activity = owned.getTag("activity") as? Activity
                        if (activity != null) instrumentation.runOnMainSync { activity.finish() } else owned.forceStop()
                        val deadline = SystemClock.uptimeMillis() + 15000
                        while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                        if (!owned.isDestroyed) owned.forceStop()
                    }
                    (owned.runtime.console as? ConsoleImpl)?.removeLogListener(consoleListener)
                }
            } finally { if (ui) service.unregisterGlobalScriptExecutionListener(listener) }
        }
    }

    private fun assertDisposed(view: View) = instrumentation.runOnMainSync {
        assertNull(view.parent)
        assertEquals(0, (view as ViewGroup).childCount)
    }

    private fun windows(): Set<FloatyWindow> {
        val field = FloatyService::class.java.getDeclaredField("windows").apply { isAccessible = true }
        return (field.get(null) as Collection<*>).filterIsInstance<FloatyWindow>().toSet()
    }

    private fun click(key: String) = assertTrue("$key exposes a click action", requireNotNull(node(key)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    private fun node(key: String): AccessibilityNodeInfo? {
        val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.forEach(nodes::add)
        while (nodes.isNotEmpty()) {
            val current = nodes.removeFirst()
            if (!current.refresh()) continue
            if (current.viewIdResourceName == "compose-robustness-$key" || current.viewIdResourceName == "${context.packageName}:id/compose-robustness-$key") return current
            for (index in 0 until current.childCount) current.getChild(index)?.let(nodes::add)
        }
        return null
    }
}
