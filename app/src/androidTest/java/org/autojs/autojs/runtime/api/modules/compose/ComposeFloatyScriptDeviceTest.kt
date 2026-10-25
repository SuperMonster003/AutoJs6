package org.autojs.autojs.runtime.api.modules.compose

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.floaty.RawWindow
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs6.R
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
import kotlin.math.roundToInt

/** Real non-UI engines and public floaty facade, restricted to the isolated Compose spike package. */
@RunWith(AndroidJUnit4::class)
class ComposeFloatyScriptDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var prepared = false
    private var previousEnabled = false
    private var previousAccessibilityFlags = 0

    @Before fun prepare() {
        assumeTrue("Use the isolated -PcomposeUiSpike=true host", context.packageName.endsWith(".compose.spike"))
        context.packageManager.getApplicationInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0)
        assumeTrue("Grant overlay permission to the isolated test host before running this matrix", Settings.canDrawOverlays(context))
        previousEnabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        instrumentation.uiAutomation.serviceInfo.also { info ->
            previousAccessibilityFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            instrumentation.uiAutomation.serviceInfo = info
        }
        prepared = true
        // HyperOS accepts this overlay scenario on the desktop. Do not launch an Activity or
        // alter any launcher data; other devices use the same reproducible hosting surface.
        assertTrue("Reach the desktop for the overlay acceptance scenario", instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        instrumentation.waitForIdleSync()
    }

    @After fun restore() {
        if (!prepared) return
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, previousEnabled)
        instrumentation.uiAutomation.serviceInfo.also { info ->
            info.flags = previousAccessibilityFlags
            instrumentation.uiAutomation.serviceInfo = info
        }
    }

    @Test fun rawHudAcceptsWorkerPostsOnTheScriptThreadAndClosesFromItsComposeButton() {
        Running("""
            var status = compose.Text({testTag:'compose-floaty-status', text:'Starting'});
            var win = compose.floaty(compose.Column({padding:12, bg:'#FFF4F4F4'}, status,
                compose.Button({testTag:'compose-floaty-close', onClick:function () { win.close(); }}, 'Close')),
                {raw:true, x:24, y:100, width:TEST.width, height:TEST.height, touchable:true, focusable:false});
            win.on('close', function () {
                check(onOwner(), 'Close callback uses the script dispatcher');
                runtime.getProperty('compose.floaty.closed').incrementAndGet();
                report({phase:'closed', closed:win.session.isClosed(), sessions:compose.sessions.length});
            });
            threads.start(function () {
                var workerId = threadId();
                for (var index = 1; index <= 2; index++) {
                    sleep(1000);
                    (function (value) {
                        compose.post(function () {
                            check(onOwner() && workerId !== ownerId, 'Worker posts return to the owning script thread');
                            status.text = 'Tick ' + value;
                            report({phase:'tick', value:value, owner:ownerId, callback:threadId(), worker:workerId,
                                x:win.getX(), y:win.getY(), width:win.getWidth(), height:win.getHeight()});
                        });
                    })(index);
                }
            });
        """).use { running ->
            running.await("Worker updates the HUD twice") { node("status")?.text?.toString() == "Tick 2" }
            val report = running.report()
            assertEquals(report.getString("owner"), report.getString("callback"))
            assertNotEquals(report.getString("owner"), report.getString("worker"))
            assertEquals(24, report.getInt("x")); assertEquals(100, report.getInt("y"))
            assertEquals(running.width, report.getInt("width")); assertEquals(running.height, report.getInt("height"))
            val windows = running.ownedWindows()
            assertEquals(1, windows.size); assertTrue(windows.single() is RawWindow)
            val views = running.views()
            click("close")
            running.await("Close button releases the overlay and session") {
                running.closed.get() == 1 && running.reportOrNull()?.optBoolean("closed") == true && node("status") == null
            }
            assertEquals(0, running.report().getInt("sessions"))
            running.awaitReleased(views)
            assertEquals(1, running.closed.get())
        }
    }

    @Test fun resizableWindowAppliesPixelGeometryTouchAndFocusOptionsAndNativeChromeClose() {
        Running("""
            var win = compose.floaty(compose.Text({testTag:'compose-floaty-resizable', text:'Resizable'}),
                {raw:false, x:32, y:120, width:TEST.width, height:TEST.height, touchable:false, focusable:false});
            win.on('close', function () {
                check(onOwner(), 'Native close returns to the script dispatcher');
                runtime.getProperty('compose.floaty.closed').incrementAndGet();
                report({phase:'closed', closed:win.session.isClosed()});
            });
            var phase = 0;
            setInterval(function () {
                var action = Number(runtime.getProperty('compose.floaty.action'));
                if (phase === 0 && action === 1) {
                    phase = 1;
                    win.setPosition(51, 151); win.setSize(TEST.width - 20, TEST.height + 20);
                    win.setTouchable(true); win.requestFocus(); win.setAdjustEnabled(true);
                } else if (phase === 1 && action === 2) {
                    phase = 2; win.disableFocus(); report({phase:'unfocused'});
                }
                // Native layout is asynchronous. Keep reporting actual facade readback until
                // the observer verifies the requested geometry, instead of freezing a 100 ms sample.
                if (phase === 1) report({phase:'geometry', x:win.getX(), y:win.getY(), width:win.getWidth(), height:win.getHeight()});
            }, 50);
        """).use { running ->
            running.await("Resizable overlay attaches with the supplied flags") {
                running.ownedWindows().singleOrNull()?.let { window ->
                    attached(window) && flags(window) and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0 &&
                        flags(window) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
                } == true
            }
            val window = running.ownedWindows().single()
            assertTrue(window is BaseResizableFloatyWindow)
            running.action(1)
            running.await("Window commands reach the native resizable bridge") {
                running.reportOrNull()?.let { result -> result.optString("phase") == "geometry" &&
                    result.optInt("x") == 51 && result.optInt("y") == 151 &&
                    result.optInt("width") == running.width - 20 && result.optInt("height") == running.height + 20 } == true &&
                    flags(window) and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0 &&
                    flags(window) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
            }
            val result = running.report()
            assertEquals(51, result.getInt("x")); assertEquals(151, result.getInt("y"))
            assertEquals(running.width - 20, result.getInt("width")); assertEquals(running.height + 20, result.getInt("height"))
            running.action(2)
            running.await("disableFocus restores the native flag") { flags(window) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0 }
            val views = running.views()
            instrumentation.runOnMainSync {
                val close = requireNotNull(window.windowView?.findViewById<View>(R.id.close))
                assertEquals(View.VISIBLE, close.visibility)
                assertTrue("The native resizable close control is connected", close.performClick())
            }
            running.await("Native chrome close also closes the Compose session") {
                running.closed.get() == 1 && running.reportOrNull()?.optBoolean("closed") == true
            }
            running.awaitReleased(views)
        }
    }

    @Test fun focusedFloatingTextFieldShowsTheImeAndUsesItsNativeInputConnection() {
        Running("""
            var field = compose.OutlinedTextField({testTag:'compose-floaty-input', text:'', singleLine:true,
                onFocusChange:function (focused) { runtime.putProperty('compose.floaty.focus', focused); },
                onValueChange:function (text) {
                    check(onOwner(), 'Input callbacks use the script dispatcher');
                    runtime.putProperty('compose.floaty.input', String(text));
                }});
            var win = compose.floaty(compose.Column({padding:12, bg:'#FFFFFFFF'}, field),
                {raw:true, x:24, y:100, width:TEST.width, height:TEST.height, focusable:false});
            var focused = false;
            setInterval(function () {
                if (!focused && Number(runtime.getProperty('compose.floaty.action')) === 1) {
                    focused = true;
                    win.requestFocus();
                    win.post(function () { field.focus(); }, 100);
                }
            }, 50);
        """).use { running ->
            running.await("Floating native field is visible") { node("input")?.isEditable == true }
            val window = running.ownedWindows().single()
            assertTrue(flags(window) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            running.action(1)
            running.await("requestFocus and field.focus activate the editor") {
                running.engine.runtime.getProperty("compose.floaty.focus") == true && node("input")?.isFocused == true
            }
            running.await("The floating window and input method have accepted editor focus") {
                var ready = false
                instrumentation.runOnMainSync {
                    val editor = running.editorView()
                    val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    ready = editor != null && editor.isAttachedToWindow && editor.hasWindowFocus() && ime.isActive(editor)
                }
                ready
            }
            lateinit var connection: InputConnection
            instrumentation.runOnMainSync {
                val editor = requireNotNull(running.editorView())
                val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val accepted = ime.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                android.util.Log.i("ComposeFloatyIme", "show=$accepted, attached=${editor.isAttachedToWindow}, " +
                    "windowFocus=${editor.hasWindowFocus()}, viewFocus=${editor.hasFocus()}, active=${ime.isActive(editor)}, " +
                    "keyboard=${context.resources.configuration.keyboard}, hardKeyboardHidden=${context.resources.configuration.hardKeyboardHidden}")
                assertTrue("The ready native editor accepts the IME show request", accepted)
            }
            running.await("The actual IME is visible above the desktop") {
                instrumentation.uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            }
            instrumentation.runOnMainSync {
                connection = requireNotNull(requireNotNull(running.editorView()).onCreateInputConnection(EditorInfo()))
                assertTrue(connection.beginBatchEdit())
                assertTrue(connection.setComposingText("Com", 1))
                assertTrue(connection.finishComposingText())
                assertTrue(connection.commitText("pose \u6D6E\u7A97", 1))
                connection.endBatchEdit()
            }
            val expected = "Compose \u6D6E\u7A97"
            running.await("Native IME edits update the script handle") {
                running.engine.runtime.getProperty("compose.floaty.input")?.toString() == expected && node("input")?.text?.toString() == expected
            }
            instrumentation.runOnMainSync { connection.closeConnection() }
            running.assertHealthy()
        }
    }

    @Test fun legacyFloatyCloseAllClosesBothComposeWindowKindsAndTheirSessions() {
        Running("""
            var a = compose.floaty(compose.Text({testTag:'compose-floaty-all-raw', text:'Raw'}),
                {raw:true, x:20, y:90, width:TEST.width, height:TEST.height});
            var b = compose.floaty(compose.Text({testTag:'compose-floaty-all-resizable', text:'Resizable'}),
                {raw:false, x:40, y:130, width:TEST.width, height:TEST.height});
            function closed() { check(onOwner(), 'closeAll callback is on the script thread'); runtime.getProperty('compose.floaty.closed').incrementAndGet(); }
            a.on('close', closed); b.on('close', closed);
            var requested = false;
            setInterval(function () {
                if (!requested && Number(runtime.getProperty('compose.floaty.action')) === 1) { requested = true; floaty.closeAll(); }
                if (requested && a.session.isClosed() && b.session.isClosed()) {
                    report({closedA:a.isClosed(), closedB:b.isClosed(), sessions:compose.sessions.length});
                }
            }, 50);
        """).use { running ->
            running.await("Raw and resizable windows are both attached") { running.ownedWindows().size == 2 && running.ownedWindows().all(::attached) }
            assertTrue(running.ownedWindows().any { it is RawWindow })
            assertTrue(running.ownedWindows().any { it is BaseResizableFloatyWindow })
            val views = running.views()
            running.action(1)
            running.await("Existing floaty.closeAll closes both Compose sessions") {
                running.closed.get() == 2 && running.reportOrNull()?.optInt("sessions", -1) == 0
            }
            assertTrue(running.report().getBoolean("closedA")); assertTrue(running.report().getBoolean("closedB"))
            running.awaitReleased(views)
            assertNull(node("all-raw")); assertNull(node("all-resizable"))
        }
    }

    @Test fun immediateCloseAllCancelsTheProvisionalWindowBeforeDeferredNativeAttachment() {
        Running("""
            var ref = compose.ref();
            var win = compose.floaty(compose.Text({ref:ref, testTag:'compose-floaty-immediate', text:'Must not remain'}),
                {raw:true, x:24, y:100, width:TEST.width, height:TEST.height});
            win.on('close', function () {
                check(onOwner(), 'Immediate close callback uses the script dispatcher');
                runtime.getProperty('compose.floaty.closed').incrementAndGet();
                check(win.session.isClosed() && ref.current === null, 'Closed provisional window releases its ref');
                report({closed:win.session.isClosed(), refEmpty:ref.current === null, sessions:compose.sessions.length});
            });
            // Deliberately remain in the same initial script turn: no post, timer or UI wait.
            floaty.closeAll();
            check(win.session.isClosed(), 'closeAll immediately closes the provisional Compose session');
            check(ref.current === null, 'No frame is accepted for a canceled initial mount');
            runtime.putProperty('compose.floaty.immediate', true);
        """).use { running ->
            running.await("Immediate closeAll finishes the session exactly once") {
                running.engine.runtime.getProperty("compose.floaty.immediate") == true && running.closed.get() == 1 &&
                    running.reportOrNull()?.optBoolean("closed") == true
            }
            assertTrue(running.report().getBoolean("refEmpty"))
            assertEquals(0, running.report().getInt("sessions"))
            // Cross several UI/service dispatch turns. A canceled asynchronous service startup
            // must never attach the previously registered provisional window afterward.
            val deadline = SystemClock.uptimeMillis() + 2000
            do {
                running.assertHealthy()
                assertTrue("No late native window is registered", running.ownedWindows().isEmpty())
                assertNull(node("immediate"))
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            assertEquals(1, running.closed.get())
        }
    }

    @Test fun repeatedSameTurnCreationReleasesProvisionalWindowsWhenControlSlotsAreFull() {
        Running("""
            // Keep the engine alive so automatic engine-exit closeAll cannot hide a leaked proxy.
            setInterval(function () {}, 1000);
            var windows = [], failure = null;
            try {
                for (var round = 0; round < 3; round++) {
                    for (var index = 0; index < 8; index++) {
                        var win = compose.floaty(compose.Text({text:'Provisional'}), {raw:true});
                        win.on('close', function () {
                            check(onOwner(), 'Rejected-startup cleanup uses the script owner');
                            runtime.getProperty('compose.floaty.closed').incrementAndGet();
                        });
                        windows.push(win);
                    }
                    // Remain in this initial script turn so old control slots await their drain.
                    floaty.closeAll();
                }
            } catch (error) {
                check(error instanceof ComposeError, 'Control reservation rejection remains typed');
                failure = error.code;
            }
            check(failure === 'LIMIT_EXCEEDED', 'Pending native cleanup has a bounded reservation budget');
            check(windows.length === 16, 'Two closed batches fit before control reservation is rejected');
            // Do not call closeAll again: it would hide a leaked failed-startup registry entry.
            compose.post(function () {
                report({code:failure, count:windows.length, closed:windows.every(function (window) {return window.isClosed();}),
                    sessions:compose.sessions.length});
            });
        """).use { running ->
            running.await("Rejected startup cleans its own provisional registration and prior sessions finish") {
                running.reportOrNull()?.optInt("count") == 16 && running.closed.get() == 16
            }
            assertEquals("LIMIT_EXCEEDED", running.report().getString("code"))
            assertTrue(running.report().getBoolean("closed"))
            assertEquals(0, running.report().getInt("sessions"))
            running.await("No failed-startup window remains registered") { running.ownedWindows().isEmpty() }
            val registry = org.autojs.autojs.runtime.api.Floaty::class.java.getDeclaredField("mWindows").apply { isAccessible = true }
            running.await("The still-running engine has no provisional window proxy") {
                (registry.get(running.engine.runtime.floaty) as Collection<*>).isEmpty()
            }
            assertFalse(running.engine.isDestroyed)
        }
    }

    @Test fun stoppingOnlyTheOwnedNonUiEngineRemovesItsWindowAndCancelsPostedWork() {
        Running("""
            var ticks = runtime.getProperty('compose.floaty.ticks');
            var label = compose.Text({testTag:'compose-floaty-stop', text:'Running'});
            var win = compose.floaty(label, {raw:true, x:24, y:100, width:TEST.width, height:TEST.height});
            function tick() { check(onOwner(), 'Tick uses the script dispatcher'); ticks.incrementAndGet(); compose.post(tick, 40); }
            compose.post(tick, 40);
            compose.post(function () { runtime.getProperty('compose.floaty.late').incrementAndGet(); }, 3000);
        """).use { running ->
            running.await("The non-UI loop is processing posted work") { running.ticks.get() >= 2 && node("stop") != null }
            val views = running.views()
            running.stop()
            running.awaitReleased(views)
            val stoppedAt = running.ticks.get()
            SystemClock.sleep(3100)
            assertEquals(stoppedAt, running.ticks.get()); assertEquals(0, running.late.get())
            assertNull(node("stop"))
        }
    }

    @Test fun saturatedUserPostsDoNotBlockNativeEventsOrNativeWindowClosure() {
        Running("""
            var clicks = runtime.getProperty('compose.floaty.ticks');
            var win = compose.floaty(compose.Button({testTag:'compose-floaty-saturated-event', onClick:function () {
                check(onOwner(), 'Native event uses the blocked script owner after it resumes');
                clicks.incrementAndGet();
            }}, 'Deliver event'), {raw:false, x:24, y:100, width:TEST.width, height:TEST.height});
            win.setAdjustEnabled(true);
            win.on('close', function () {
                check(onOwner(), 'Native close cleanup stays on the script owner');
                runtime.getProperty('compose.floaty.closed').incrementAndGet();
                report({closed:win.isClosed(), sessions:compose.sessions.length});
            });
            var phase = 0;
            setInterval(function () {
                var requested = Number(runtime.getProperty('compose.floaty.action'));
                if (requested <= phase) return;
                phase = requested;
                var count = 0, rejected = false;
                try {
                    while (count < 4096) { compose.post(function () {}); count++; }
                } catch (error) {
                    check(error instanceof ComposeError, 'User queue overflow remains a typed script error');
                    check(error.code === 'LIMIT_EXCEEDED', 'User queue overflow keeps the documented limit code');
                    runtime.putProperty('compose.floaty.rejection', String(error.code));
                    rejected = true;
                }
                check(rejected && count > 0 && count < 4096, 'Ordinary script post queue remains bounded');
                runtime.putProperty('compose.floaty.queued', count);
                runtime.getProperty('compose.floaty.saturation').set(phase);
                var gate = runtime.getProperty(phase === 1 ? 'compose.floaty.gate1' : 'compose.floaty.gate2');
                check(gate.await(20, java.util.concurrent.TimeUnit.SECONDS), 'Test releases only the script-thread gate');
            }, 50);
        """).use { running ->
            try {
                running.await("Resizable saturation test window is ready") {
                    node("saturated-event") != null && running.ownedWindows().singleOrNull()?.let { native ->
                        var ready = false
                        instrumentation.runOnMainSync { ready = native.windowView?.findViewById<View>(R.id.close)?.visibility == View.VISIBLE }
                        ready
                    } == true
                }
                running.action(1)
                running.await("First ordinary post queue is full and its script owner is paused") { running.saturation.get() == 1 }
                click("saturated-event")
                assertEquals("No script callback runs on Android main", 0, running.ticks.get())
                running.gate1.countDown()
                running.await("The pending native event survives ordinary queue saturation") { running.ticks.get() == 1 }
                click("saturated-event")
                running.await("Event draining remains armed after recovery") { running.ticks.get() == 2 }

                running.action(2)
                running.await("Second ordinary post queue is full and its script owner is paused") { running.saturation.get() == 2 }
                val native = running.ownedWindows().single()
                val views = running.views()
                instrumentation.runOnMainSync {
                    assertTrue(requireNotNull(native.windowView?.findViewById<View>(R.id.close)).performClick())
                }
                running.awaitReleased(views)
                assertEquals("Native disposal does not execute the blocked script's close listener", 0, running.closed.get())
                assertFalse("Window close leaves the executing script thread intact", running.engine.isDestroyed)
                running.gate2.countDown()
                running.await("The critical close receipt runs exactly once after the script resumes") {
                    running.closed.get() == 1 && running.reportOrNull()?.optInt("sessions", -1) == 0
                }
                assertTrue(running.report().getBoolean("closed"))
                assertEquals(2, running.ticks.get())
                running.assertHealthy()
            } finally {
                running.gate1.countDown()
                running.gate2.countDown()
            }
        }
    }

    private inner class Running(source: String) : Closeable {
        private val baseline = windows()
        private val started = CountDownLatch(1)
        private val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        private val failure = AtomicReference<Throwable?>()
        private val stopping = AtomicBoolean()
        private var execution: ScriptExecution? = null
        val closed = AtomicInteger()
        val ticks = AtomicInteger()
        val late = AtomicInteger()
        val saturation = AtomicInteger()
        val gate1 = CountDownLatch(1)
        val gate2 = CountDownLatch(1)
        val logs = ConcurrentLinkedQueue<String>()
        val engine get() = requireNotNull(engineRef.get())
        val width = (260 * context.resources.displayMetrics.density).roundToInt()
            .coerceAtMost(context.resources.displayMetrics.widthPixels - 60)
        val height = (180 * context.resources.displayMetrics.density).roundToInt()
        private val consoleListener = object : ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) { logs += logEntry.content.toString() }
            override fun onLogClear() = Unit
        }
        private val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                val engine = execution.engine as? RhinoJavaScriptEngine
                if (engine == null) failure.set(AssertionError("Non-UI script did not use Rhino")) else {
                    engineRef.set(engine)
                    engine.runtime.putProperty("compose.floaty.closed", closed)
                    engine.runtime.putProperty("compose.floaty.ticks", ticks)
                    engine.runtime.putProperty("compose.floaty.late", late)
                    engine.runtime.putProperty("compose.floaty.action", 0)
                    engine.runtime.putProperty("compose.floaty.saturation", saturation)
                    engine.runtime.putProperty("compose.floaty.gate1", gate1)
                    engine.runtime.putProperty("compose.floaty.gate2", gate2)
                    (engine.runtime.console as? ConsoleImpl)?.addLogListener(consoleListener)
                }
                started.countDown()
            }
            override fun onException(execution: ScriptExecution, error: Throwable) {
                if (!stopping.get()) failure.compareAndSet(null, error)
                started.countDown()
            }
        }

        init {
            val prefix = """
                function check(value, message) { if (!value) throw new Error(message); }
                function report(value) { runtime.putProperty('compose.floaty.report', JSON.stringify(value)); }
                function threadId() { return String(java.lang.Thread.currentThread().getId()); }
                var ownerId = threadId();
                function onOwner() { return threadId() === ownerId; }
                check(ownerId !== String(android.os.Looper.getMainLooper().getThread().getId()), 'Floaty script uses a non-UI engine');
                var TEST = {width:$width, height:$height};
                setInterval(function () {}, 1000);
            """.trimIndent()
            try {
                execution = AutoJs.instance.scriptEngineService.execute(
                    StringScriptSource("compose-floaty-owned-${UUID.randomUUID()}", prefix + "\n" + source.trimIndent()), listener, ExecutionConfig())
                assertTrue("The owned non-UI engine starts", started.await(30, TimeUnit.SECONDS))
                assertHealthy(); assertNotNull(engineRef.get())
                assertNull("A floaty script does not own a ScriptExecuteActivity", engine.getTag("activity"))
            } catch (error: Throwable) { close(); throw error }
        }

        fun action(value: Int) { engine.runtime.putProperty("compose.floaty.action", value) }
        fun reportOrNull(): JSONObject? = engine.runtime.getProperty("compose.floaty.report")?.toString()?.let(::JSONObject)
        fun report(): JSONObject = requireNotNull(reportOrNull()) { "Script did not report a result" }
        fun ownedWindows(): Set<FloatyWindow> = windows() - baseline
        fun views(): List<View> { var result = emptyList<View>(); instrumentation.runOnMainSync { result = ownedWindows().mapNotNull { it.windowView } }; return result }
        fun editorView(): View? {
            fun find(view: View): View? {
                if (view.onCheckIsTextEditor()) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            return ownedWindows().mapNotNull { it.windowView }.firstNotNullOfOrNull(::find)
        }
        fun assertHealthy() {
            failure.get()?.let { throw AssertionError("Owned non-UI floaty script failed", it) }
            logs.firstOrNull { it.startsWith("3-Block Compose: ") }?.let { throw AssertionError(it) }
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
        fun awaitReleased(views: List<View>) = await("Only the owned overlay views are detached") {
            var detached = false
            instrumentation.runOnMainSync { detached = views.all { !it.isAttachedToWindow && it.parent == null } }
            detached && ownedWindows().isEmpty()
        }
        fun stop() {
            val owned = engineRef.get() ?: return
            stopping.set(true)
            if (!owned.isDestroyed) owned.forceStop()
            val deadline = SystemClock.uptimeMillis() + 15000
            while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertTrue("Only the owned non-UI engine is stopped", owned.isDestroyed)
            instrumentation.waitForIdleSync()
        }
        override fun close() {
            try {
                stopping.set(true)
                gate1.countDown(); gate2.countDown()
                val owned = engineRef.get() ?: execution?.engine as? RhinoJavaScriptEngine
                if (owned != null) {
                    if (!owned.isDestroyed) owned.forceStop()
                    val deadline = SystemClock.uptimeMillis() + 15000
                    while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                    (owned.runtime.console as? ConsoleImpl)?.removeLogListener(consoleListener)
                    // Failure cleanup only touches this engine's existing native window registry.
                    owned.runtime.floaty.closeAll()
                }
            } finally { instrumentation.waitForIdleSync() }
        }
    }

    /** Read only our host registry, never platform-private WindowManager APIs or other packages. */
    private fun windows(): Set<FloatyWindow> {
        val field = FloatyService::class.java.getDeclaredField("windows").apply { isAccessible = true }
        return (field.get(null) as Collection<*>).filterIsInstance<FloatyWindow>().toSet()
    }
    private fun attached(window: FloatyWindow): Boolean {
        var value = false; instrumentation.runOnMainSync { value = window.windowView?.isAttachedToWindow == true }; return value
    }
    private fun flags(window: FloatyWindow): Int {
        var value = 0; instrumentation.runOnMainSync { value = window.windowLayoutParams.flags }; return value
    }
    private fun click(key: String) = assertTrue("$key exposes a click action", requireNotNull(node(key)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    private fun node(key: String): AccessibilityNodeInfo? {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.forEach(pending::add)
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            if (!item.refresh()) continue
            if (item.viewIdResourceName == "compose-floaty-$key" || item.viewIdResourceName == "${context.packageName}:id/compose-floaty-$key") return item
            for (index in 0 until item.childCount) item.getChild(index)?.let(pending::add)
        }
        return null
    }
}
