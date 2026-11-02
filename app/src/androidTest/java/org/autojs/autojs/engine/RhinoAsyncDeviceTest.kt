package org.autojs.autojs.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.project.ScriptConfig
import org.autojs.autojs.rhino.debug.DebugCallback
import org.autojs.autojs.rhino.debug.Dim
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.edit.debug.DebuggerSingleton
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Async functions of the Rhino fork in the production script engine, on a real looper: the order of microtasks and timers,
 * Promises of the host, the threads of a script, coroutines, the end of a script and the debugger. The cases that need no
 * more than a result are in `assets/rhino/async-cases.js`, which also runs in an inrt APK
 * (`tools/rhino/device_rhino_check.py --inrt`). What the console shows is read from the console of the script and from
 * logcat (tag GlobalConsole).
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.engine.RhinoAsyncDeviceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: 生产脚本引擎中 Rhino 分支的 async 函数, 运行在真实 Looper 上: 微任务与计时器的先后, 宿主的 Promise, 脚本的线程, 协程, 脚本结束与调试器.
 * 只看结果的用例在 `assets/rhino/async-cases.js`, 它也在 inrt APK 中运行 (`tools/rhino/device_rhino_check.py --inrt`). 控制台内容从脚本的控制台与
 * logcat (标签 GlobalConsole) 读取.
 */
class RhinoAsyncDeviceTest {

    @Test
    fun microtasksBeforeTimers() = assertCase("microtasksBeforeTimers")

    @Test
    fun awaitTimer() = assertCase("awaitTimer")

    @Test
    fun hostPromiseOrder() = assertCase("hostPromiseOrder")

    @Test
    fun awaitHostPromise() = assertCase("awaitHostPromise")

    @Test
    fun awaitJavaFuture() = assertCase("awaitJavaFuture")

    @Test
    fun settledByOtherThreads() = assertCase("settledByOtherThreads")

    @Test
    fun threadsAsyncFunction() = assertCase("threadsAsyncFunction")

    @Test
    fun forAwait() = assertCase("forAwait")

    @Test
    fun asyncGenerator() = assertCase("asyncGenerator")

    @Test
    fun classAsyncMethods() = assertCase("classAsyncMethods")

    @Test
    fun optionalCallOfMissingMethod() = assertCase("optionalCallOfMissingMethod")

    @Test
    fun switchCaseFunctionOnEntry() = assertCase("switchCaseFunctionOnEntry")

    @Test
    fun blockFunctionFromOutside() = assertCase("blockFunctionFromOutside")

    /** D15: the two ways to wait work together, and a coroutine waits for an async function that waits for the host. */
    @Test
    fun aCoroutineAwaitsAnAsyncFunction() {
        val script = """
            var log = [];
            async function plusOne(value) {
                await new Promise(function (resolve) { setTimeout(resolve, 5); });
                return value + 1;
            }
            async function fails() {
                await null;
                throw new Error('no');
            }
            log.push(continuation.await(plusOne(1)));
            try {
                continuation.await(fails());
            } catch (e) {
                log.push('caught ' + e.message);
            }
            log.push(plusOne(2).await());
            log.push(continuation.await((async function () { return await flow.of(6).toPromise(); })()));
            var cont = continuation.create();
            (async function () {
                log.push('function ' + await new Promise(function (resolve) { setTimeout(function () { resolve('settled'); }, 5); }));
                cont.resume('resumed');
            })();
            log.push(cont.await());
            runtime.putProperty('rhino.result', log.join('|'));
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-coroutine", script), coroutines())
        run.finish()
        assertEquals("2|caught no|3|6|function settled|resumed", run.result)
    }

    /**
     * A module of the host settles through a [ScriptAsyncDispatcher], which runs the microtasks after each delivery: the coroutine
     * resumes there, and suspends there again. Its last statement returns null, which is also the value of the last resume.
     * zh-CN: 宿主模块经 [ScriptAsyncDispatcher] 落定, 它在每次投递后运行微任务: 协程在那里恢复, 也在那里再次挂起. 脚本最后一条语句的值是 null, 这也是最后一次恢复的返回值.
     */
    @Test
    fun aCoroutineAwaitsOneAfterTheOtherThroughADispatcher() {
        val script = """
            var log = [];
            function settled(value) {
                var dispatcher = new org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher(runtime, true);
                var adapter = new org.autojs.autojs.runtime.api.ScriptPromiseAdapter();
                var promise = ResultAdapter.promise(adapter);
                new java.lang.Thread(new java.lang.Runnable({
                    run: function () { dispatcher.resolve(adapter, value, function () {}); },
                })).start();
                return promise;
            }
            log.push(settled(1).await());
            log.push(settled(2).await());
            log.push(continuation.await(settled(3)));
            runtime.putProperty('rhino.result', log.join('|'));
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-dispatcher", script), coroutines())
        run.finish()
        assertEquals("1|2|3", run.result)
    }

    /** A coroutine cannot suspend inside an async function: the error can be read and caught, and nothing hangs. */
    @Test
    fun aCoroutineInsideAnAsyncFunctionIsAReadableError() {
        val script = """
            var log = [];
            (async function () {
                try {
                    continuation.await(Promise.resolve(1));
                    log.push('no');
                } catch (e) {
                    log.push(e.name, e.message);
                }
                await null;
                log.push('after');
            })();
            (async function () { Promise.resolve(2).await(); log.push('no'); })().catch(function (e) { log.push(e.name); });
            setTimeout(function () { runtime.putProperty('rhino.result', log.join('|')); }, 20);
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-coroutine-error", script), coroutines())
        run.finish()
        val log = run.result.toString().split('|')
        assertEquals(run.result.toString(), 4, log.size)
        assertEquals("TypeError", log[0])
        assertTrue(log[1], "await" in log[1])
        assertEquals(listOf("TypeError", "after"), log.drop(2).sorted())
        assertEquals(emptyList<String>(), run.console(Log.ERROR))
    }

    /** Appendix C Q6: an error on the console once, with the stack, and the script goes on. */
    @Test
    fun anUncaughtRejectionIsReportedOnce() {
        val marker = "lost-${UUID.randomUUID()}"
        val script = """
            function boom() {
                throw new RangeError('$marker');
            }
            Promise.resolve().then(boom);
            setTimeout(function () { runtime.putProperty('rhino.result', 'still running'); }, 20);
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-rejection", script), ExecutionConfig())
        run.finish()
        assertEquals("still running", run.result)
        val errors = run.console(Log.ERROR)
        assertEquals(errors.toString(), 1, errors.size)
        assertTrue(errors[0], marker in errors[0])
        assertTrue(errors[0], "rhino-async-rejection" in errors[0] && ":2" in errors[0])
        val logcat = logcat().dropWhile { marker !in it }
        assertTrue("no $marker in logcat", logcat.isNotEmpty())
        assertTrue(logcat.joinToString("\n"), logcat.any { "rhino-async-rejection" in it })
    }

    @Test
    fun aHandledRejectionIsNotReported() {
        val script = """
            (async function () {
                try {
                    await Promise.reject(new Error('no'));
                } catch (e) {
                    runtime.putProperty('rhino.result', e.message);
                }
            })();
            Promise.reject(new Error('late')).catch(function () {});
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-handled", script), ExecutionConfig())
        run.finish()
        assertEquals("no", run.result)
        assertEquals(emptyList<String>(), run.console(Log.ERROR))
    }

    @Test
    fun aSettledScriptEndsWithoutAWarning() {
        val script = """
            (async function () {
                await new Promise(function (resolve) { setTimeout(resolve, 5); });
                runtime.putProperty('rhino.result', 'settled');
            })();
        """.trimIndent()
        // The timer of the function keeps the script running.
        val run = start(StringScriptSource("rhino-async-settled", script), ExecutionConfig())
        run.finish()
        assertEquals("settled", run.result)
        assertEquals(emptyList<String>(), run.console(Log.WARN))
    }

    /** Appendix C Q7: a warning once with the number and the place of the first one, and a normal end of the script. */
    @Test
    fun pendingAwaitsAtTheEndAreWarnedAbout() {
        val name = "rhino-async-pending"
        val script = """
            async function waits() {
                await new Promise(function () {});
            }
            waits();
            waits();
        """.trimIndent()
        val run = start(StringScriptSource(name, script), ExecutionConfig())
        run.finish()
        val warnings = run.console(Log.WARN)
        assertEquals(warnings.toString(), 1, warnings.size)
        assertTrue(warnings[0], "2" in warnings[0] && "$name.js:2" in warnings[0])
    }

    @Test
    fun pendingAwaitsOfAThreadAreWarnedAboutOnce() {
        val name = "rhino-async-pending-thread"
        val script = """
            threads.start(function () {
                (async function () {
                    await new Promise(function () {});
                })();
            });
            setTimeout(function () {}, 200);
        """.trimIndent()
        val run = start(StringScriptSource(name, script), ExecutionConfig())
        run.finish()
        val warnings = run.console(Log.WARN)
        assertEquals(warnings.toString(), 1, warnings.size)
        assertTrue(warnings[0], "$name.js:3" in warnings[0])
    }

    /** Appendix C Q7: a task of the host keeps the script running, though the Promise of it does not. */
    @Test
    fun aRequestOfTheHostKeepsTheScriptRunning() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread {
            server.use {
                it.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    Thread.sleep(200)
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
        }
        val script = """
            (async function () {
                var response = await http.getAsync('http://127.0.0.1:${server.localPort}/');
                runtime.putProperty('rhino.result', String(response.statusCode));
            })();
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-request", script), ExecutionConfig())
        run.finish()
        assertEquals("200", run.result)
        assertEquals(emptyList<String>(), run.console(Log.WARN))
    }

    @Test
    fun exitDoesNotWarn() {
        val script = """
            (async function () {
                await new Promise(function () {});
            })();
            exit();
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-exit", script), ExecutionConfig())
        run.finish(interrupted = true)
        assertEquals(emptyList<String>(), run.console(Log.WARN))
    }

    @Test
    fun stoppingAScriptDoesNotWarn() {
        val script = """
            (async function () {
                await new Promise(function () {});
            })();
            runtime.putProperty('rhino.result', 'started');
            setInterval(function () {}, 20);
        """.trimIndent()
        val run = start(StringScriptSource("rhino-async-stop", script), ExecutionConfig())
        val deadline = System.currentTimeMillis() + 20_000
        while (run.engineOrNull?.runtime?.getProperty("rhino.result") == null) {
            assertTrue("the script did not start", System.currentTimeMillis() < deadline)
            Thread.sleep(10)
        }
        run.engineOrNull!!.forceStop()
        run.finish(interrupted = true)
        assertEquals(emptyList<String>(), run.console(Log.WARN))
    }

    @Test
    fun asyncFunctionInUiMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "rhino-async-ui-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        try {
            val report = File(directory, "report.txt")
            val script = File(directory, "async-ui.js")
            script.writeText(
                """
                "ui rhino";
                ui.layout(<text id="label"/>);
                function onUiThread() { return android.os.Looper.myLooper() === android.os.Looper.getMainLooper(); }
                (async function () {
                    var log = [onUiThread()];
                    await null;
                    log.push(onUiThread());
                    await new Promise(function (resolve) { setTimeout(resolve, 5); });
                    log.push(onUiThread());
                    await new Promise(function (resolve) { threads.start(function () { resolve(); }); });
                    log.push(onUiThread());
                    await new Promise(function (resolve) {
                        new java.lang.Thread(new java.lang.Runnable({ run: function () { resolve(); } })).start();
                    });
                    log.push(onUiThread());
                    ui.label.setText('done');
                    log.push(String(ui.label.getText()));
                    files.write(${JSONObject.quote(report.absolutePath)}, log.join('|'));
                    ui.finish();
                })();
                """.trimIndent(),
            )
            start(JavaScriptFileSource(script), ExecutionConfig(workingDirectory = directory.absolutePath)).finish()
            assertEquals("true|true|true|true|true|done", report.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    /**
     * Appendix C Q60: the debugger generates debug information, which the transforms of for-of, of a class that extends another
     * and of for await go through. The script stops on every line it reaches, as in the debug mode of the editor.
     * zh-CN: 附录 C Q60: 调试器打开调试信息生成, for-of, 继承其他类的 class 与 for await 的变换都要经过它. 脚本像编辑器的调试模式一样在每一行停下.
     */
    @Test
    fun debugMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "rhino-async-debug-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val debugger = DebuggerSingleton.get()
        try {
            val file = File(directory, "debugged.js")
            file.writeText(DEBUGGED_SCRIPT)
            val source = HeldFileSource(file)
            val lines = ConcurrentSkipListSet<Int>()
            val stops = AtomicInteger()
            val handler = Handler(Looper.getMainLooper())
            debugger.setDebugCallback(object : DebugCallback {
                override fun updateSourceText(sourceInfo: Dim.SourceInfo) = Unit
                override fun enterInterrupt(lastFrame: Dim.StackFrame, threadTitle: String, alertMessage: String?) {
                    if (lastFrame.url == file.path) lines.add(lastFrame.lineNumber)
                    handler.post { if (stops.incrementAndGet() < DEBUG_STEPS) debugger.stepInto() else debugger.resume() }
                }
            })
            val run = start(source, ExecutionConfig(workingDirectory = directory.absolutePath))
            debugger.attach(run.execution)
            source.attached.countDown()
            run.finish()
            assertEquals("1|2|1|2|ab|no", run.result)
            val reached = DEBUGGED_SCRIPT.lines().withIndex().filter { "// reached" in it.value }.map { it.index + 1 }
            assertTrue("stopped on $lines", lines.containsAll(reached))
        } finally {
            debugger.detach()
            debugger.setDebugCallback(null)
            directory.deleteRecursively()
        }
    }

    private fun assertCase(name: String) {
        val script = testAsset(CASES) + "\nrhinoAsyncCases.run('$name', function (verdict) { runtime.putProperty('rhino.result', verdict); });"
        val run = start(StringScriptSource(name, script), ExecutionConfig())
        run.finish()
        assertEquals("PASS", run.result)
    }

    private fun coroutines() = ExecutionConfig(scriptConfig = ScriptConfig(features = listOf(ScriptConfig.FEATURE_CONTINUATION)))

    private fun testAsset(name: String): String {
        return InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use { it.readText() }
    }

    private fun logcat(): List<String> {
        val process = ProcessBuilder("logcat", "-d", "-v", "raw", "-s", "GlobalConsole:*").redirectErrorStream(true).start()
        return process.inputStream.bufferedReader().use { it.readLines() }
    }

    private fun start(source: ScriptSource, config: ExecutionConfig): Run {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) = completed.countDown()
            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        return Run(AutoJs.instance.scriptEngineService.execute(source, listener, config), completed, failure)
    }

    private class Run(val execution: ScriptExecution, val completed: CountDownLatch, val failure: AtomicReference<Throwable>) {

        val engineOrNull get() = execution.engine as RhinoJavaScriptEngine?

        val result: Any? get() = engineOrNull!!.runtime.getProperty("rhino.result")

        fun finish(interrupted: Boolean = false) {
            assertTrue("the script did not finish", completed.await(90, TimeUnit.SECONDS))
            val error = failure.get()
            if (interrupted) {
                assertTrue("the script failed: $error", error == null || ScriptInterruptedException.causedByInterrupt(error))
            } else {
                assertNull("the script failed: $error", error)
            }
        }

        /** What the script printed at [level]. */
        fun console(level: Int) = engineOrNull!!.runtime.console.snapshotLogEntries().filter { it.level == level }.map { it.content.toString() }
    }

    /**
     * A file whose engine is created after [attached]: the debugger needs the execution, and only takes engines that come after it.
     * zh-CN: [attached] 之后才创建引擎的文件: 调试器需要 execution 对象, 且只接管之后创建的引擎.
     */
    private class HeldFileSource(file: File) : JavaScriptFileSource(file) {

        val attached = CountDownLatch(1)

        private val starter = Thread.currentThread()

        override val engineName: String?
            get() {
                if (Thread.currentThread() !== starter) attached.await(10, TimeUnit.SECONDS)
                return super.engineName
            }
    }

    private companion object {
        const val CASES = "rhino/async-cases.js"
        const val DEBUG_STEPS = 400
        val DEBUGGED_SCRIPT = """
            var log = [];
            for (var number of [1, 2]) {
                log.push(number); // reached
            }
            class A {
                async value() {
                    await null;
                    return 'a'; // reached
                }
            }
            class B extends A {
                async value() {
                    return (await super.value()) + 'b'; // reached
                }
            }
            async function* items() {
                yield 1;
                yield Promise.resolve(2); // reached
            }
            async function main() {
                for await (var item of items()) {
                    log.push(item); // reached
                }
                log.push(await new B().value());
                try {
                    await Promise.reject(new Error('no'));
                } catch (e) {
                    log.push(e.message); // reached
                }
                runtime.putProperty('rhino.result', log.join('|'));
            }
            main();
        """.trimIndent()
    }
}
