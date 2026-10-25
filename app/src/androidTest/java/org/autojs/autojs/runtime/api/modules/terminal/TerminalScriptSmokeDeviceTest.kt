package org.autojs.autojs.runtime.api.modules.terminal

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.terminal.TerminalPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real host UID -> Rhino -> plugin pipes. Only sessions created by these tests are closed. */
class TerminalScriptSmokeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences("plugin_center_enable_state", 0)
    private var preferenceSnapshot: Map<String, *>? = null
    private var existing = emptySet<String>()
    private var available = false

    @Before fun prepare() {
        assumeTrue("install 3-Shell Terminal to run this integration suite", runCatching { context.packageManager.getPackageInfo(PLUGIN, 0) }.isSuccess)
        preferenceSnapshot = preferences.all.toMap()
        PluginEnableStore.setEnabled(context, PLUGIN, true)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(PLUGIN, "android.permission.POST_NOTIFICATIONS")
        }
        available = runBlocking { TerminalPluginHost.isAvailable(context) }
        assertTrue("installed terminal plugin must answer", available)
        existing = runBlocking { TerminalPluginHost.listSessions(context).map { it.id }.toSet() }
    }

    @After fun restore() {
        if (available) runBlocking {
            TerminalPluginHost.listSessions(context).filter { it.id !in existing }.forEach { TerminalPluginHost.closeSession(context, it.id) }
        }
        preferenceSnapshot?.let { before ->
            // Restore only the preference keys this plugin's enable operation touched.
            val editor = preferences.edit()
            (preferences.all.keys + before.keys).filter { it.contains(PLUGIN) }.forEach { key ->
                val value = before[key]
                if (value is Boolean) editor.putBoolean(key, value) else if (value == null) editor.remove(key)
            }
            editor.commit()
        }
    }

    @Test fun linesWaitAndExitAreOrderedOnTheScriptThread() {
        val report = runScript("""
            var lines = [], order = [], exited = null;
            var origin = java.lang.Thread.currentThread().getId();
            var s = terminal.exec('sleep 1; for i in 1 2 3; do echo line${'$'}i; sleep 1; done; exit 3', {cwd: testCwd, show: false, keepOpen: false});
            s.on('output', function(line) {
                check(java.lang.Thread.currentThread().getId() == origin, 'foreign callback thread');
                if (/^line[123]${'$'}/.test(line)) { lines.push(line); order.push(line); }
            });
            s.once('exit', function(code) { exited = code; order.push('exit'); });
            check(s.waitFor(/line2/, 15000) == 'line2', 'regex wait');
            var barrier = terminal.exec('sleep 4; exit 7', {cwd: testCwd, show: false, keepOpen: false, wait: true});
            check(barrier.exitCode == 7, 'wait exit code');
            check(exited == 3 && s.exitCode == 3, 'event exit code');
            check(lines.join(',') == 'line1,line2,line3', 'output lines: ' + lines);
            check(order.join(',') == 'line1,line2,line3,exit', 'exit must follow output');
            report({lines: lines, exitCode: exited, cwd: barrier.cwd});
        """)
        assertEquals(3, report.getInt("exitCode"))
        assertTrue(report.getString("cwd").endsWith("/files/terminal/home"))
    }

    @Test fun promptsCanBeAnsweredWhileWaitingAndArgumentsAreGuarded() {
        runScript("""
            var s = terminal.exec('sleep 1; printf "name? "; read name; echo "hello_${'$'}name"; sleep 3', {cwd: testCwd, show: false, keepOpen: false});
            s.on('output', function(line) { if (line.indexOf('name?') >= 0) s.write('terminal\n'); });
            check(s.waitFor('hello_terminal', 15000).indexOf('hello_terminal') >= 0, 'interactive wait');
            check(s.transcript(1024).indexOf('hello_terminal') >= 0, 'transcript');
            ['bad', -1, 1048577].forEach(function(limit) {
                try { s.transcript(limit); throw Error('missing argument error'); }
                catch (e) { check(e instanceof terminal.TerminalError && e.code == 'INVALID_ARGUMENT', 'argument error'); }
            });
            try { s.waitFor('never-present', 50); throw Error('missing timeout'); }
            catch (e) { check(e.code == 'TIMEOUT', 'timeout code'); }
            s.removeAllListeners(); s.close(); report({ok: true});
        """)
    }

    @Test fun exitOnlyAndOnceListenersReleaseSubscriptionsAndAllowResubscription() {
        runScript("""
            var only = terminal.open({cwd: testCwd, show: false});
            var exitSeen = null;
            only.once('exit', function(code) { exitSeen = code; });
            only.write('exit 9\n');
            terminal.exec('sleep 2', {cwd: testCwd, show: false, keepOpen: false, wait: true});
            check(exitSeen == 9, 'exit-only callback');
            var s = terminal.exec('sleep 1; echo first; sleep 5', {cwd: testCwd, show: false});
            var once = 0;
            s.once('output', function() { once++; });
            terminal.exec('sleep 2', {cwd: testCwd, show: false, keepOpen: false, wait: true});
            check(once == 1 && service.subscriptionCount == 0, 'once subscription leaked');
            var listener = function() {};
            for (var i = 0; i < 6; i++) {
                s.on('output', listener); s.off('output', listener);
                terminal.exec('sleep 0.1', {cwd: testCwd, show: false, keepOpen: false, wait: true});
            }
            check(service.subscriptionCount == 0, 'off subscription leaked');
            s.close(); report({ok: true});
        """)
    }

    @Test fun fastExitsAndAsyncWaitsKeepTheirExitCodes() {
        runScript("""
            for (var i = 0; i < 8; i++) {
                var s = terminal.exec('exit ' + i, {cwd: testCwd, show: false, keepOpen: false, wait: true, timeout: 10000});
                check(s.exitCode == i && s.state == 'exited', 'fast exit ' + i);
            }
            terminal.execAsync('sleep 1; exit 11', {cwd: testCwd, show: false, keepOpen: false, wait: true}).then(function(s) {
                check(s.exitCode == 11, 'async exit'); report({ok: true});
            });
        """)
    }

    @Test fun highThroughputReportsOverflowBeforeExitWithoutBlockingThePlugin() {
        var probes = 0
        var longestReadMillis = 0L
        // Keep the byte-pressure case bounded on emulators: one-character yes output creates
        // 26 million callbacks. Short-line pressure is exercised separately below.
        val report = runScript("""
            var dropped = 0, lines = 0, exitSeen = false;
            var s = terminal.exec('sleep 1; yes terminal-pressure-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJKLMNOPQRSTUVWXYZ | head -c 50m; exit 3', {cwd: testCwd, title: 'P4 throughput', show: true, keepOpen: false});
            s.on('output', function() { check(!exitSeen, 'output after exit'); lines++; });
            s.on('overflow', function(bytes) { check(!exitSeen, 'overflow after exit'); dropped += bytes; });
            s.on('exit', function(code) {
                exitSeen = true;
                check(code == 3 && dropped > 0 && lines > 0, 'throughput outcome');
                report({dropped: dropped, lines: lines, code: code});
            });
            // Deliberately stop draining the script queue. The plugin must continue processing.
            sleep(4000);
            terminal.sessions();
        """, 300000, observe = {
            runBlocking {
                val session = TerminalPluginHost.listSessions(context).firstOrNull { it.title == "P4 throughput" }
                if (session != null) {
                    val start = SystemClock.elapsedRealtime()
                    // readTranscript crosses into the plugin's UI thread during the output flood.
                    if (runCatching { TerminalPluginHost.readTranscript(context, session.id, 64) }.isSuccess) {
                        longestReadMillis = maxOf(longestReadMillis, SystemClock.elapsedRealtime() - start)
                        probes++
                    }
                }
            }
        })
        assertTrue(report.getLong("dropped") > 0)
        assertTrue("no main-thread responsiveness probes", probes > 0)
        assertTrue("plugin main thread stalled for $longestReadMillis ms", longestReadMillis < 5000)
        android.util.Log.i("TerminalScriptSmoke", "throughput main-thread probes=$probes longestReadMillis=$longestReadMillis")
    }

    @Test fun shortLinePressureStillDrainsAndDeliversExit() {
        runScript("""
            var lines = 0, exitSeen = false;
            var s = terminal.exec('sleep 1; yes | head -c 1m; exit 5', {cwd: testCwd, show: false, keepOpen: false});
            s.on('output', function() { check(!exitSeen, 'output after exit'); lines++; });
            s.on('exit', function(code) {
                exitSeen = true;
                check(code == 5 && lines > 0, 'short-line outcome');
                report({lines: lines, code: code});
            });
        """, 180000)
    }

    @Test fun stoppingScriptReleasesPipesButPreservesItsUserSession() {
        val running = startScript("""
            var s = terminal.open({cwd: testCwd, show: false});
            s.on('output', function() {});
            runtime.putProperty('terminal.smoke.id', String(s.id));
        """)
        try {
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (running.engine.runtime.getProperty("terminal.smoke.id") == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            val id = running.engine.runtime.getProperty("terminal.smoke.id") as? String ?: throw AssertionError("no session")
            running.engine.forceStop()
            assertTrue(running.finished.await(15, TimeUnit.SECONDS))
            running.engine.thread.join(10000)
            assertFalse("script cleanup did not finish", running.engine.thread.isAlive)
            val field = running.engine.runtime.javaClass.getDeclaredField("terminal").apply { isAccessible = true }
            val service = field.get(running.engine.runtime) as org.autojs.autojs.runtime.api.terminal.TerminalService
            assertEquals(0, service.subscriptionCount)
            assertTrue(runBlocking { TerminalPluginHost.listSessions(context).any { it.id == id && it.alive } })
        } finally {
            if (running.engine.thread.isAlive) running.engine.forceStop()
            running.engine.thread.join(10000)
        }
    }

    private data class Running(val engine: RhinoJavaScriptEngine, val finished: CountDownLatch, val failure: AtomicReference<Throwable?>)

    private fun startScript(source: String, workingDirectory: String = ""): Running {
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) { started.countDown() }
            override fun onSuccess(execution: ScriptExecution, result: Any?) { finished.countDown() }
            override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error); started.countDown(); finished.countDown() }
        }
        val prepared = """
            function check(ok, message) { if (!ok) throw Error(message); }
            function report(value) { runtime.putProperty('terminal.smoke.report', JSON.stringify(value)); }
            var field = runtime.getClass().getDeclaredField('terminal'); field.setAccessible(true);
            var service = field.get(runtime);
            var testCwd = terminal.isAvailable() ? terminal.env().home : '';
        """.trimIndent() + "\n" + source.trimIndent()
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("terminal-smoke", prepared), listener, ExecutionConfig(workingDirectory = workingDirectory))
        assertTrue("script started", started.await(15, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("script startup failed", it) }
        return Running(execution.engine as RhinoJavaScriptEngine, finished, failure)
    }

    internal fun runScript(source: String, timeoutMillis: Long = 45000, workingDirectory: String = "", observe: (() -> Unit)? = null): JSONObject {
        val running = startScript(source, workingDirectory)
        try {
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis
            var nextDiagnostic = SystemClock.elapsedRealtime() + 15000
            while (!running.finished.await(200, TimeUnit.MILLISECONDS)) {
                if (SystemClock.elapsedRealtime() >= nextDiagnostic) {
                    android.util.Log.i("TerminalScriptSmoke", "pending report=${running.engine.runtime.getProperty("terminal.smoke.report")} stack=${running.engine.thread.stackTrace.take(6).joinToString("; ")}")
                    nextDiagnostic += 30000
                }
                assertTrue("script did not finish; report=${running.engine.runtime.getProperty("terminal.smoke.report")}", SystemClock.elapsedRealtime() < deadline)
                observe?.invoke()
            }
            running.engine.thread.join(10000)
            assertFalse("script cleanup did not finish", running.engine.thread.isAlive)
            running.failure.get()?.let { throw AssertionError("script failed", it) }
            val report = running.engine.runtime.getProperty("terminal.smoke.report") as? String ?: throw AssertionError("no script report")
            android.util.Log.i("TerminalScriptSmoke", report)
            return JSONObject(report)
        } finally {
            if (running.engine.thread.isAlive) running.engine.forceStop()
            running.engine.thread.join(10000)
        }
    }

    private companion object {
        const val PLUGIN = "io.github.supermonster003.autojs6.plugin.three.shell.terminal"
    }
}
