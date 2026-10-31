package org.autojs.autojs.engine

import android.app.ActivityManager
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The class syntax of the Rhino fork in the production script engine: classes, fields and private names, subclasses of
 * built-in constructors, and classes that extend Java classes, whose adapter classes go through the D8 of the host. The
 * cases are in `assets/rhino/class-syntax-cases.js` and `class-syntax-ui-case.js`, which also run in an inrt APK
 * (`tools/rhino/device_rhino_check.py --inrt`). Run it on API 24 as well, where the host dexes with the legacy path.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.engine.RhinoClassSyntaxDeviceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: 生产脚本引擎中 Rhino 分支的 class 语法: class, 字段与私有名, 内建构造器的子类, 以及继承 Java 类的 class (其适配器类经宿主的
 * D8 转换). 用例在 `assets/rhino/class-syntax-cases.js` 与 `class-syntax-ui-case.js`, 它们也在 inrt APK 中运行
 * (`tools/rhino/device_rhino_check.py --inrt`). 也要在 API 24 上运行, 宿主在那里走旧的转换路径.
 */
class RhinoClassSyntaxDeviceTest {

    @Test
    fun classCore() = assertCase("classCore")

    @Test
    fun fieldsAndPrivateNames() = assertCase("fieldsAndPrivateNames")

    @Test
    fun builtinSubclasses() = assertCase("builtinSubclasses")

    @Test
    fun extendsThread() = assertCase("extendsThread")

    @Test
    fun extendsHandler() = assertCase("extendsHandler")

    @Test
    fun implementsRunnable() = assertCase("implementsRunnable")

    @Test
    fun severalInterfaces() = assertCase("severalInterfaces")

    @Test
    fun abstractJavaClass() = assertCase("abstractJavaClass")

    @Test
    fun javaCollectionSubclass() = assertCase("javaCollectionSubclass")

    @Test
    fun hostCollections() = assertCase("hostCollections")

    @Test
    fun extendsClassFromLoadDex() = assertCase("extendsClassFromLoadDex")

    @Test
    fun extendsClassFromLoadJar() = assertCase("extendsClassFromLoadJar")

    @Test
    fun viewSubclassInUiMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "rhino-class-ui-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        try {
            val report = File(directory, "report.txt")
            val script = File(directory, "class-ui.js")
            script.writeText(buildString {
                appendLine("\"ui rhino\";")
                appendLine(testAsset("rhino/class-syntax-ui-case.js"))
                appendLine("rhinoClassUiCase(function (report) { files.write(${JSONObject.quote(report.absolutePath)}, report); ui.finish(); });")
            })
            execute(JavaScriptFileSource(script), ExecutionConfig(workingDirectory = directory.absolutePath))
            assertEquals("PASS", report.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    /**
     * What the first construction of a class that extends a Java class costs in every run of a script: the adapter class
     * is generated, dexed and loaded once for each signature and each top-level scope. The rounds warm the process up
     * (a debug build needs about 100 scripts); the status lines carry the result, read by device_rhino_check.py.
     * zh-CN: 继承 Java 类的 class 每次脚本运行的首次构造开销: 每个签名在每个顶层作用域生成, 转 dex 并加载一次适配器类. 这些轮次同时暖机
     * (debug 包约需 100 次脚本), 结果由状态行带出, 由 device_rhino_check.py 读取.
     */
    @Test
    fun firstConstructionCost() {
        val rounds = (1..ROUNDS).map { round ->
            val result = runScript(
                "rhino-class-cost-$round",
                """
                function cost(make) { var from = java.lang.System.nanoTime(); make(); return (java.lang.System.nanoTime() - from) / 1e6; }
                class Worker extends java.lang.Thread { run() {} }
                class Tick extends java.util.TimerTask { run() {} }
                result = [
                    cost(function () { return new Worker(); }),
                    cost(function () { return new Worker(); }),
                    cost(function () { return new Tick(); }),
                    cost(function () { return new JavaAdapter(java.lang.Thread, { run: function () {} }); }),
                ].join();
                """,
            )
            result.toString().split(',').map(String::toDouble)
        }
        val warm = rounds.drop(ROUNDS - ROUNDS / 4)
        val names = listOf("first construction", "second construction", "first of another signature", "first new JavaAdapter")
        val group = runCatching { File("/proc/self/cpuset").readText().trim() }.getOrDefault("?")
        val policy = runCatching {
            val ps = ProcessBuilder("ps", "-o", "PCY", "-p", Process.myPid().toString()).redirectErrorStream(true).start()
            ps.inputStream.bufferedReader().readLines().lastOrNull()?.trim()?.takeIf { it.length <= 3 }
        }.getOrNull() ?: "?"
        val importance = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance
        metric("rounds $ROUNDS, warm rounds ${warm.size}, PCY $policy, cpuset $group, importance $importance")
        names.forEachIndexed { index, name ->
            val sorted = warm.map { it[index] }.sorted()
            metric(String.format(Locale.ROOT, "%s: round 1 %.2f ms, warm median %.2f ms, min %.2f ms", name, rounds[0][index], sorted[sorted.size / 2], sorted[0]))
        }
    }

    private fun assertCase(name: String) {
        assertEquals("PASS", runScript(name, testAsset(CASES) + "\nresult = rhinoClassCases.run('$name');"))
    }

    private fun testAsset(name: String): String {
        return InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use { it.readText() }
    }

    private fun metric(line: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(METRIC_STATUS, Bundle().apply { putString("rhino_metric", line) })
    }

    /** Runs [body], which assigns the global `result`, and returns that value as a string. */
    private fun runScript(name: String, body: String): Any? {
        val script = "var result;\n$body\nruntime.putProperty('rhino.result', String(result));"
        val execution = execute(StringScriptSource(name, script), ExecutionConfig())
        return (execution.engine as RhinoJavaScriptEngine).runtime.getProperty("rhino.result")
    }

    private fun execute(source: ScriptSource, config: ExecutionConfig): ScriptExecution {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) = completed.countDown()
            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(source, listener, config)
        assertTrue("the script did not finish", completed.await(90, TimeUnit.SECONDS))
        assertNull("the script failed: ${failure.get()}", failure.get())
        return execution
    }

    private companion object {
        const val CASES = "rhino/class-syntax-cases.js"
        const val ROUNDS = 120
        const val METRIC_STATUS = 100
    }
}
