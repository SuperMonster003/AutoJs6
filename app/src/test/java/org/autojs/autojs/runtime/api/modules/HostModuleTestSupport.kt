package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.differential.SurfaceDiff
import org.autojs.autojs.script.JavaScriptSource
import org.junit.Assert.assertTrue
import java.io.File
import org.autojs.autojs.runtime.api.Engines as ApiEngines
import org.autojs.autojs.runtime.api.Files as ApiFiles

/**
 * Test support of the host modules migrated in roadmap P3.5 (zip, sqlite, crypto, http, web, WebSocket, media, sensors,
 * recorder, tts): a working directory for the JVM test runtime, the comparison of a new module with its current Kotlin
 * module in the same JVM (surface and results), and the export of device cases for `tools/augment/device_eval.py`.
 *
 * zh-CN: P3.5 迁移的宿主模块的测试设施: 给 JVM 测试运行时设置工作目录, 在同一 JVM 中把新模块与现有 Kotlin 模块比较 (表面与结果), 以及为
 * tools/augment/device_eval.py 导出设备用例.
 */
internal object HostModuleTestSupport {

    /** Gives [runtime] `files` and `engines` of a script whose working directory is [cwd] (paths resolve against it). */
    fun useWorkingDirectory(runtime: ScriptRuntime, cwd: File) {
        val engines = ApiEngines(runtime).apply { setCurrentEngine(WorkingDirectoryEngine(cwd.path)) }
        setField(runtime, "engines", engines)
        setField(runtime, "files", ApiFiles(runtime))
    }

    fun setField(runtime: ScriptRuntime, name: String, value: Any?) {
        ScriptRuntime::class.java.getDeclaredField(name).apply { isAccessible = true }.set(runtime, value)
    }

    /**
     * The surface of [expression] (a module object) on the new layer against the current module's, recorded before
     * P5.2 removed it ([ScriptModuleExpectations]): names, kinds and attributes, the prototype chain included; `length`
     * is allowed to differ (appendix C Q9).
     */
    fun assertSameSurface(rhino: DifferentialRhino, expression: String) {
        val actual = SurfaceDiff.surface(rhino.evalKt(expression), rhino.ktScope.globalThis)
        val expected = ScriptModuleExpectations.obsoleteSurface("surface: $expression")
        val report = SurfaceDiff.compare(expected, actual)
        assertTrue("$expression:\n" + SurfaceDiff.describe(report, "current", "new"), report.namesMatch && report.attributeMismatches.isEmpty())
    }

    /**
     * Evaluates [expressions] on the new layer ([rhino], already mounted) against the current module's results, recorded
     * before P5.2 removed it ([currentResults]); the results must be equal except for [known] (expression to the expected
     * new value with a reason), whose current value is checked to differ. A Java exception of the current module (rendered from its printed stack
     * as `throws <Class>`) equals the same class mapped by the new layer (`throws JavaException <Class>`, T5); results
     * the current module cannot produce on the JVM (`unavailable(jvm)`, Android classes behind its helpers) are skipped
     * and left to the device cases. Returns the number of expressions compared.
     */
    fun assertSameResults(rhino: DifferentialRhino, expressions: List<String>, known: Map<String, Pair<String, String>>): Int {
        val actual = expressions.associateWith { rhino.render(DifferentialRhino.Side.KT, it) }
        val expected = expressions.zip(currentResults(expressions)).toMap()
        var compared = 0
        val failures = expressions.mapNotNull { expression ->
            val now = expected.getValue(expression)
            val new = actual.getValue(expression)
            val difference = known[expression]
            if (now.key.startsWith("unavailable(jvm)") && difference == null) return@mapNotNull null
            compared++
            val sameJavaException = now.key.startsWith("throws ") && new.key == "throws JavaException " + now.key.removePrefix("throws ")
            when {
                difference == null && now.key != new.key && !sameJavaException -> "$expression\n    current $now\n    new     $new"
                difference != null && new.key != difference.first -> "$expression (known)\n    expected ${difference.first}\n    new      $new"
                difference != null && now.key == new.key -> "$expression (known difference no longer differs)\n    both $new"
                else -> null
            }
        }
        assertTrue("Results differ from the current module:\n" + failures.joinToString("\n"), failures.isEmpty())
        return compared
    }

    /** The results of [expressions] on the current module, recorded before P5.2 removed it ([ScriptModuleExpectations]). */
    fun currentResults(expressions: List<String>): List<DifferentialRhino.Rendered> =
        expressions.map { expression -> ScriptModuleExpectations.obsoleteRendered("current: $expression") }

    /** Checks [cases] (expression to expected rendering) on the new layer. */
    fun check(rhino: DifferentialRhino, cases: List<Pair<String, String>>) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression)
            if (actual.key == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Device cases of one test class, written to `[directory]/[name].json` (by default `build/script-modules-device`; the
     * cases that need the local server of `tools/augment/device_net.py` go to `build/script-modules-device-net`; the format of
     * `DifferentialCases`: expected values in "kt") for `device_eval.py --new-modules on --strict-errors`; cases added
     * later replace earlier ones with the same expression, the file keeps the order of first addition.
     */
    class DeviceCases(private val name: String, private val reason: String, private val directory: String = "build/script-modules-device") {

        private val cases = LinkedHashMap<String, String>()

        @Synchronized
        fun add(entries: List<Pair<String, String>>) {
            entries.forEach { (expression, expected) -> cases[expression] = expected }
            val dir = File(directory).apply { mkdirs() }
            // An INFO case has no expected value: device_eval.py only prints the device result.
            val body = cases.entries.joinToString(",\n") { (expression, expected) ->
                val kt = if (expected == INFO) "null" else json(expected)
                "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": $kt, \"reason\": ${json(reason)}}"
            }
            File(dir, "$name.json").writeText("[\n$body\n]\n", Charsets.UTF_8)
        }

        companion object {
            /** The expected value of a case whose result depends on the device (its TTS engine, its sensors): printed, not compared. */
            const val INFO = "<info>"
        }

    }

    /**
     * A device expression that settles the Promise [promise] on a worker thread (`threads.start`, whose timer runs the
     * Promise callbacks; blocking the script thread with `wait()` would keep them from running) and returns [value] of the
     * result `v`, "rejected" or "timeout" after 10 seconds.
     */
    fun settled(promise: String, value: String = "v"): String =
        "(function () { var latch = new java.util.concurrent.CountDownLatch(1); var out = 'timeout'; " +
            "threads.start(function () { ($promise).then(function (v) { out = $value; latch.countDown(); }, function (e) { out = 'rejected'; latch.countDown(); }); }); " +
            "latch.await(10, java.util.concurrent.TimeUnit.SECONDS); return out; })()"

    /**
     * A device expression that waits for the Promise [promise] with `wait()` on the script thread itself and returns
     * [value] of the result `v`, or "rejected". The wait runs the callbacks of the timer of the thread, where the Promise
     * callbacks may be queued (docs/augment/ROADMAP.md Q26: they kept `wait()` blocked for good before).
     */
    fun waited(promise: String, value: String = "v"): String =
        "(function () { var v; try { v = ($promise).wait(); } catch (e) { return 'rejected'; } return $value; })()"

    fun json(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' || c == '\\' -> append('\\').append(c)
                c == '\n' -> append("\\n")
                c.code < 0x20 -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    /** An engine that only knows its working directory, the one thing `Files.cwd()` reads. */
    private class WorkingDirectoryEngine(cwd: String) : JavaScriptEngine() {

        init {
            setTag(ScriptEngine.TAG_WORKING_DIRECTORY, cwd)
        }

        override fun doExecution(scriptSource: JavaScriptSource?): Any? = null

        override fun put(name: String?, value: Any?) = Unit

        override fun forceStop() = Unit

        override fun init() = Unit

    }

}
