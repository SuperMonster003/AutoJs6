package org.autojs.autojs.runtime.api.augment.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.open.RunIntentActivity
import org.autojs.autojs.script.StringScriptSource
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

/** Public UI scripts enter through RunIntentActivity; the host test has no Compose dependency. */
@RunWith(AndroidJUnit4::class)
class ComposeScriptUiDeviceTest {
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
        instrumentation.uiAutomation.serviceInfo.also { info ->
            previousAccessibilityFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
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

    @Test fun counterRunsAsAUiScriptAndStateChangesReachTheInstalledRenderer() {
        launch("""
            var count = compose.state(0);
            var session = compose.mount(function () {
                return compose.Column({padding: 16},
                    compose.Text({key: 'label', testTag: 'compose-p3-counter', text: 'Count: ' + count.value}),
                    compose.Button({key: 'button', testTag: 'compose-p3-increment', onClick: function () {
                        count.value++;
                        report({count: count.value});
                    }}, 'Increment'));
            });
        """).use { running ->
            running.await("Counter initial frame") { node("counter")?.text?.toString() == "Count: 0" }
            for (expected in 1..3) {
                click("increment")
                running.await("Counter render $expected") { node("counter")?.text?.toString() == "Count: $expected" }
                assertEquals(expected, running.report().getInt("count"))
            }
            running.assertHealthy()
        }
    }

    @Test fun aRenderExceptionKeepsTheOldViewAndTheSameButtonCanRecoverOnTheMainThread() {
        launch("""
            var count = compose.state(0);
            compose.mount(function renderCounter() {
                if (count.value === 1) throw new Error('Expected render failure');
                return compose.Column({padding: 16},
                    compose.Text({key: 'value', testTag: 'compose-p3-recovery-count', text: 'Count: ' + count.value}),
                    compose.Button({key: 'button', testTag: 'compose-p3-recover', onClick: function () {
                        count.value++;
                        report({count: count.value});
                    }}, 'Advance'));
            });
        """, expectedRenderFailures = 1).use { running ->
            running.await("Initial recovery frame") { node("recovery-count")?.text?.toString() == "Count: 0" }
            val retainedButton = requireNotNull(node("recover"))
            assertTrue(retainedButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            running.await("Render failure is reported without terminating the engine") { running.renderFailures().size == 1 }
            assertEquals(1, running.report().getInt("count"))
            assertEquals("Count: 0", node("recovery-count")?.text?.toString())
            assertFalse("A caught render error must not destroy the main-thread script engine", running.engine.isDestroyed)
            assertTrue("Failed render keeps the previous button attached", retainedButton.refresh())
            assertTrue("The retained callback can trigger recovery", retainedButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            running.await("A repaired state publishes the next frame") { node("recovery-count")?.text?.toString() == "Count: 2" }
            assertEquals(2, running.report().getInt("count"))
            assertEquals("Exactly one typed render failure", 1, running.renderFailures().size)
            assertTrue("The error report retains the named script render stack", running.logs.any { it.contains("renderCounter") })
            running.assertHealthy()
        }
    }

    @Test fun nativeFieldSwitchAndButtonUsePublicNodeHandlesAndCallbacks() {
        launch("""
            var field = compose.OutlinedTextField({testTag: 'compose-p3-name', text: '', singleLine: true,
                onValueChange: function (text) { runtime.putProperty('compose.p3.field', String(text)); }});
            var toggle = compose.Switch({testTag: 'compose-p3-enabled', checked: false,
                onCheckedChange: function (checked) { toggle.checked = checked; }});
            var summary = compose.Text({testTag: 'compose-p3-summary', text: 'Not submitted'});
            var session = compose.mount(compose.Column({padding: 16}, field, toggle,
                compose.Button({testTag: 'compose-p3-submit', onClick: function () {
                    summary.text = field.text + ':' + toggle.checked;
                    report({text: field.text, checked: toggle.checked});
                }}, 'Submit'),
                compose.Button({testTag: 'compose-p3-reinsert', onClick: function () {
                    var parent = field.parent, previousId = field.nodeId;
                    parent.remove(field);
                    check(field.nodeId === -1, 'Detached public ID is absent');
                    session.post(function () {
                        parent.insert(0, field);
                        check(field.nodeId === previousId, 'Reinsert retains internal identity');
                        session.post(function () { field.edit({text: 'Reinserted'}); });
                    });
                }}, 'Reinsert'), summary));
        """).use { running ->
            running.await("Native input field") { node("name")?.isEditable == true }
            val entered = "Compose \uD83D\uDE03"
            assertTrue("Native accessibility text editing", requireNotNull(node("name")).performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, entered)
                },
            ))
            running.await("Text event updates the script handle") { running.engine.runtime.getProperty("compose.p3.field")?.toString() == entered }
            click("enabled")
            running.await("Host accepts Switch proposal") { node("enabled")?.isChecked == true }
            click("submit")
            running.await("Button reads current native text and controlled switch") { node("summary")?.text?.toString() == "$entered:true" }
            assertEquals(entered, running.report().getString("text"))
            assertTrue(running.report().getBoolean("checked"))
            click("reinsert")
            running.await("Reinserted editor accepts a command in its new native lifetime") {
                running.engine.runtime.getProperty("compose.p3.field")?.toString() == "Reinserted"
            }
        }
    }

    @Test fun thousandKeyedRowsScrollAndRetainHandleIdentityAfterReorder() {
        launch("""
            var reversed = compose.state(false);
            var list = compose.ref();
            var savedId = -1;
            function row900() {
                return list.current.children.filter(function (node) { return node.key === 'row-900'; })[0];
            }
            var session = compose.mount(function () {
                var items = [];
                for (var index = 0; index < 1000; index++) {
                    var value = reversed.value ? 999 - index : index;
                    items.push(compose.Text({key: 'row-' + value, testTag: 'compose-p3-row-' + value,
                        text: 'Row ' + value, modifier: compose.modifier().height(32).fillMaxWidth()}));
                }
                return compose.Column({modifier: compose.modifier().fillMaxSize()},
                    compose.Text({key: 'order', testTag: 'compose-p3-order', text: reversed.value ? 'Reverse' : 'Forward'}),
                    compose.Row({key: 'controls'},
                        compose.Button({key: 'scroll', testTag: 'compose-p3-scroll', onClick: function () {
                            savedId = row900().nodeId;
                            list.current.scrollTo(900);
                        }}, 'Scroll'),
                        compose.Button({key: 'reverse', testTag: 'compose-p3-reverse', onClick: function () { reversed.value = true; }}, 'Reverse'),
                        compose.Button({key: 'inspect', testTag: 'compose-p3-inspect', onClick: function () {
                            report({sameId: row900().nodeId === savedId, children: list.current.children.length, reversed: reversed.value});
                        }}, 'Inspect')),
                    compose.LazyColumn({key: 'list', ref: list, testTag: 'compose-p3-list',
                        modifier: compose.modifier().weight(1).fillMaxWidth()}, items));
            });
        """).use { running ->
            running.await("Thousand-item list initial viewport") { node("row-0") != null && node("scroll") != null }
            click("scroll")
            running.await("Lazy scroll command reaches row 900") { node("row-900") != null }
            click("reverse")
            running.await("Reordered list frame") { node("order")?.text?.toString() == "Reverse" }
            click("inspect")
            running.await("Reordered list reports retained node identity") { running.reportOrNull()?.optBoolean("reversed") == true }
            assertTrue(running.report().getBoolean("sameId"))
            assertEquals(1000, running.report().getInt("children"))
        }
    }

    @Test fun memoizedKeyedRowsSkipUnrelatedRendersAndRebuildOnlyTheEditedRow() {
        launch("""
            var initial = [];
            for (var index = 0; index < 300; index++) initial.push({id: 'm' + index, title: 'Memo ' + index});
            var items = compose.state(initial), tick = compose.state(0), list = compose.ref();
            var builds = 0, firstHandle = null;
            var session = compose.mount(function () {
                var rows = items.value.map(function (item) {
                    return compose.memo(function () {
                        builds++;
                        return compose.Text({key: item.id, testTag: 'compose-p3-' + item.id, text: item.title,
                            modifier: compose.modifier().height(32).fillMaxWidth()});
                    }, [item.id, item.title], {key: item.id});
                });
                return compose.Column({modifier: compose.modifier().fillMaxSize()},
                    compose.Text({key: 'tick', testTag: 'compose-p3-tick', text: 'Tick ' + tick.value}),
                    compose.Row({key: 'controls'},
                        compose.Button({key: 'bump', testTag: 'compose-p3-bump', onClick: function () { tick.value++; }}, 'Tick'),
                        compose.Button({key: 'edit', testTag: 'compose-p3-edit', onClick: function () {
                            var next = items.value.slice().reverse();
                            next[0] = {id: next[0].id, title: 'Edited ' + next[0].id};
                            items.value = next;
                        }}, 'Edit'),
                        compose.Button({key: 'inspect', testTag: 'compose-p3-inspect', onClick: function () {
                            var children = list.current.children;
                            if (firstHandle === null) firstHandle = children[0];
                            report({builds: builds, tick: tick.value, count: children.length, first: children[0].key,
                                firstText: children[0].text, originalLast: children[children.length - 1] === firstHandle});
                        }}, 'Inspect')),
                    compose.LazyColumn({key: 'list', ref: list, testTag: 'compose-p3-memo-list',
                        modifier: compose.modifier().weight(1).fillMaxWidth()}, rows));
            });
        """).use { running ->
            running.await("Memoized rows render") { node("m0")?.text?.toString() == "Memo 0" && node("inspect") != null }
            click("inspect")
            running.await("Initial memo report") { running.reportOrNull()?.optInt("builds") == 300 }
            click("bump")
            running.await("Unrelated state reaches the real renderer") { node("tick")?.text?.toString() == "Tick 1" }
            click("inspect")
            running.await("Report after the unrelated render") { running.reportOrNull()?.optInt("tick") == 1 }
            assertEquals("An unrelated render reuses every row", 300, running.report().getInt("builds"))
            click("edit")
            running.await("Reordered and edited row is visible") { node("m299")?.text?.toString() == "Edited m299" }
            assertEquals("Memo 298", node("m298")?.text?.toString())
            click("inspect")
            running.await("Report after the edit") { running.reportOrNull()?.optString("first") == "m299" }
            val report = running.report()
            assertEquals("Only the edited row is rebuilt", 301, report.getInt("builds"))
            assertEquals(300, report.getInt("count"))
            assertEquals("Edited m299", report.getString("firstText"))
            assertTrue("A moved row keeps its handle", report.getBoolean("originalLast"))
        }
    }

    @Test fun twentyRealMountCloseCyclesReleaseViewsRefsAndCloseCallbacks() {
        launch("""
            var cycles = 0;
            var closed = 0;
            function cycle() {
                var ref = compose.ref();
                var node = compose.Text({ref: ref, testTag: 'compose-p3-cycle', text: 'Cycle ' + cycles});
                var session = compose.mount(node);
                session.on('close', function () { closed++; });
                session.post(function () {
                    check(ref.current === node, 'Accepted frame publishes the ref');
                    var view = ui.view;
                    session.close();
                    check(session.isClosed() && session.root === null, 'Closed session releases its root');
                    check(ref.current === null, 'Close clears the ref');
                    check(view.getParent() === null && view.getChildCount() === 0, 'Close detaches and disposes the renderer view');
                    cycles++;
                    if (cycles < 20) compose.post(cycle);
                    else report({cycles: cycles, closed: closed, sessions: compose.sessions.length});
                }, 30);
            }
            cycle();
        """).use { running ->
            running.await("Twenty mount/close cycles complete", 45000) { running.reportOrNull()?.optInt("cycles") == 20 }
            assertEquals(20, running.report().getInt("closed"))
            assertEquals(0, running.report().getInt("sessions"))
            assertNull(node("cycle"))
            running.assertHealthy()
        }
    }

    @Test fun replacingComposeWithUiLayoutClosesTheSessionAndWarns() {
        launch("""
            var closed = 0;
            runtime.putProperty('compose.p3.afterClose', 0);
            var replace = compose.Button({testTag: 'compose-p3-replace', onClick: function () {
                ui.layout('<vertical><text id="composeLegacy" text="Legacy content"/></vertical>');
                report({closed: session.isClosed(), notifications: closed, sessions: compose.sessions.length});
            }}, 'Replace with XML');
            replace.on('click', function () { runtime.putProperty('compose.p3.afterClose', 1); });
            var session = compose.mount(replace);
            session.on('close', function () { closed++; });
        """).use { running ->
            running.await("Compose replacement control") { node("replace") != null }
            click("replace")
            running.await("Legacy layout replaces Compose") { findNode { it.text?.toString() == "Legacy content" } != null }
            assertTrue(running.report().getBoolean("closed"))
            assertEquals(1, running.report().getInt("notifications"))
            assertEquals(0, running.report().getInt("sessions"))
            assertEquals(0, (running.engine.runtime.getProperty("compose.p3.afterClose") as Number).toInt())
            assertTrue("Content replacement warns through the script console", running.logs.any {
                it.contains("Compose UI session closed") && it.contains("ui content was replaced")
            })
            assertNull(node("replace"))
        }
    }

    @Test fun stoppingTheOwnedUiEngineDisposesItsViewAndCancelsPostedCallbacks() {
        launch("""
            var ticks = runtime.getProperty('compose.p3.ticks');
            compose.mount(compose.Text({testTag: 'compose-p3-stopping', text: 'Running'}));
            function tick() { ticks.incrementAndGet(); compose.post(tick, 40); }
            compose.post(tick, 40);
            compose.post(function () { runtime.getProperty('compose.p3.late').incrementAndGet(); }, 5000);
        """).use { running ->
            running.await("Posted callbacks are running") { running.ticks.get() >= 2 && node("stopping") != null }
            lateinit var view: View
            instrumentation.runOnMainSync { view = requireNotNull(running.engine.runtime.ui.view) }
            running.stop()
            val stoppedAt = running.ticks.get()
            // This crosses the delayed callback's deadline as well as several normal post intervals.
            SystemClock.sleep(5100)
            assertEquals(stoppedAt, running.ticks.get())
            assertEquals(0, running.late.get())
            instrumentation.runOnMainSync {
                assertNull(view.parent)
                assertEquals(0, (view as ViewGroup).childCount)
            }
            assertNull(node("stopping"))
        }
    }

    private fun launch(source: String, expectedRenderFailures: Int = 0): Running = Running(source, expectedRenderFailures)

    private inner class Running(source: String, private val expectedRenderFailures: Int) : Closeable {
        private val marker = "compose-p3-owned-${UUID.randomUUID()}"
        private val started = CountDownLatch(1)
        private val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        private val failure = AtomicReference<Throwable?>()
        private val stopping = AtomicBoolean()
        val ticks = AtomicInteger()
        val late = AtomicInteger()
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
                val engine = execution.engine as? RhinoJavaScriptEngine
                if (engine == null) failure.set(AssertionError("UI script did not use Rhino"))
                else {
                    engineRef.set(engine)
                    engine.runtime.putProperty("compose.p3.ticks", ticks)
                    engine.runtime.putProperty("compose.p3.late", late)
                    (engine.runtime.console as? ConsoleImpl)?.addLogListener(consoleListener)
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
            val script = "\"ui\";\n// $marker\n" + """
                function check(value, message) { if (!value) throw new Error(message); }
                function report(value) { runtime.putProperty('compose.p3.report', JSON.stringify(value)); }
            """.trimIndent() + "\n" + source.trimIndent()
            service.registerGlobalScriptExecutionListener(listener)
            try {
                instrumentation.runOnMainSync {
                    // The path route applies the host's external-storage permission gate even to
                    // private files. Inline source exercises the same public UI launch without it.
                    context.startActivity(Intent(context, RunIntentActivity::class.java)
                        .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, script)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                assertTrue("RunIntentActivity starts the owned UI script", started.await(30, TimeUnit.SECONDS))
                assertHealthy()
                assertNotNull(engineRef.get())
            } catch (error: Throwable) { close(); throw error }
        }

        fun reportOrNull(): JSONObject? = engine.runtime.getProperty("compose.p3.report")?.toString()?.let(::JSONObject)
        fun report(): JSONObject = requireNotNull(reportOrNull()) { "Script did not report a result" }
        fun renderFailures(): List<String> = logs.filter { it.lineSequence().firstOrNull() == "Compose UI: RENDER_FAILED" }
        fun assertHealthy() {
            failure.get()?.let { throw AssertionError("Owned UI script failed", it) }
            val composeErrors = logs.filter { it.startsWith("Compose UI: ") }
            composeErrors.firstOrNull { it.lineSequence().firstOrNull() != "Compose UI: RENDER_FAILED" }?.let { throw AssertionError(it) }
            if (composeErrors.size > expectedRenderFailures) throw AssertionError(composeErrors.joinToString("\n"))
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
        fun stop() {
            val owned = engineRef.get() ?: return
            stopping.set(true)
            if (!owned.isDestroyed) owned.forceStop()
            val deadline = SystemClock.uptimeMillis() + 15000
            while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertTrue("Only the owned UI engine is stopped", owned.isDestroyed)
            instrumentation.waitForIdleSync()
        }

        override fun close() {
            try {
                stopping.set(true)
                engineRef.get()?.let { owned ->
                    if (!owned.isDestroyed) {
                        val activity = owned.getTag("activity") as? Activity
                        if (activity != null) instrumentation.runOnMainSync { activity.finish() } else owned.forceStop()
                        val deadline = SystemClock.uptimeMillis() + 15000
                        while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                        if (!owned.isDestroyed) owned.forceStop()
                    }
                    (owned.runtime.console as? ConsoleImpl)?.removeLogListener(consoleListener)
                }
            } finally {
                service.unregisterGlobalScriptExecutionListener(listener)
            }
        }
    }

    private fun click(key: String) = assertTrue("$key exposes a click action", requireNotNull(node(key)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    private fun node(key: String): AccessibilityNodeInfo? = findNode {
        it.viewIdResourceName == "compose-p3-$key" || it.viewIdResourceName == "${context.packageName}:id/compose-p3-$key"
    }
    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.forEach(nodes::add)
        while (nodes.isNotEmpty()) {
            val node = nodes.removeFirst()
            if (!node.refresh()) continue
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(nodes::add)
        }
        return null
    }
}
