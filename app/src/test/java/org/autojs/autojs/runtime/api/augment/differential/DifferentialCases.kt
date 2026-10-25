package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Runs the same expressions on the JS side and the Kotlin side of a [DifferentialRhino] and checks that the
 * rendered results agree, except for the differences a test documents explicitly in [KnownDifference]s. A
 * documented difference is itself asserted, so a silent change on either side still fails the test.
 *
 * zh-CN: 在 [DifferentialRhino] 的 JS 侧与 Kotlin 侧求值同一组表达式并比对渲染结果; 测试用 [KnownDifference] 明确记录的
 * 既有偏差会按记录的两侧结果断言, 任何一侧悄悄变化都会失败.
 */
internal object DifferentialCases {

    data class KnownDifference(val expression: String, val js: String, val kt: String, val reason: String)

    /**
     * @param tolerateErrorTypes when true, two sides that both throw count as agreeing even if the error
     * names differ. The current Kotlin augmentables wrap every failure into an `InternalError`
     * (`WrappedRuntimeException`), which roadmap decision D10 replaces; until then the error type is not a
     * useful signal and would otherwise have to be documented for every failing call.
     * @param exportDir where [exportForDevice] writes the device cases: `build/augment-differential` for the "old JS
     * against the current Kotlin" tests (P0), `build/script-modules-device` for the "old JS against the new layer" tests
     * (P2 on, evaluated with `device_eval.py --new-modules on --strict-errors`).
     * @param exportAll export every expression with its Kotlin side value, not only the documented differences and the
     * skipped expressions (P2 on: the device run then confirms the whole set on the new layer).
     */
    fun run(
        rhino: DifferentialRhino,
        expressions: List<String>,
        known: List<KnownDifference> = emptyList(),
        tolerateErrorTypes: Boolean = false,
        exportDir: String = "build/augment-differential",
        exportAll: Boolean = false,
    ) {
        val knownByExpression = known.associateBy { it.expression }
        val unexpected = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val ktValues = mutableMapOf<String, String>()
        val report = StringBuilder()
        expressions.forEach { expression ->
            val js = DifferentialRhino.Rendered(ScriptModuleExpectations.obsolete("js: $expression") { rhino.render(DifferentialRhino.Side.JS, expression).key }, null)
            val kt = rhino.render(DifferentialRhino.Side.KT, expression)
            ktValues[expression] = kt.key
            val documented = knownByExpression[expression]
            val bothThrow = js.key.startsWith("throws") && kt.key.startsWith("throws")
            when {
                documented != null -> {
                    report.appendLine("known   $expression\n        js=$js\n        kt=$kt\n        reason: ${documented.reason}")
                    assertEquals("JS result changed for documented difference: $expression", documented.js, js.key)
                    assertEquals("Kotlin result changed for documented difference: $expression", documented.kt, kt.key)
                }
                js.key == kt.key -> report.appendLine("same    $expression -> $js")
                js.key.startsWith("unavailable") || kt.key.startsWith("unavailable") -> {
                    report.appendLine("skip    $expression (needs Android at runtime)\n        js=$js\n        kt=$kt")
                    skipped += expression
                }
                tolerateErrorTypes && bothThrow -> report.appendLine("throws  $expression\n        js=$js\n        kt=$kt")
                else -> {
                    report.appendLine("DIFF    $expression\n        js=$js\n        kt=$kt")
                    unexpected += "$expression\n    js=$js\n    kt=$kt"
                }
            }
        }
        if (skipped.isNotEmpty()) report.appendLine("skipped ${skipped.size} expression(s) that need Android at runtime; cover them with tools/augment on a device")
        println(report)
        val exported = if (exportAll) expressions.filter { it !in skipped } else expressions.filter { it in knownByExpression || it in skipped }
        exportForDevice(exported, knownByExpression, if (exportAll) ktValues else emptyMap(), exportDir)
        val undocumented = knownByExpression.keys - expressions.toSet()
        assertTrue("Known differences must be listed in the expressions: $undocumented", undocumented.isEmpty())
        assertTrue("Undocumented differences between the obsolete JS module and the Kotlin module:\n" + unexpected.joinToString("\n"), unexpected.isEmpty())
    }

    /**
     * Writes the documented differences and the skipped expressions of the calling test class to
     * `build/augment-differential/<TestClass>.json`, where `tools/augment/device_eval.py` picks them up to evaluate the
     * same expressions with the installed host: the Kotlin values recorded here come from the JVM (fakes, stubs), the
     * device run confirms them. Skipped expressions have no expected value.
     * zh-CN: 把调用方测试类已记录的偏差与跳过的表达式写到 build/augment-differential/<测试类>.json, 供 tools/augment/device_eval.py
     * 用已安装的宿主求值同一批表达式, 以真机确认 JVM 上记录的 Kotlin 值.
     */
    private fun exportForDevice(expressions: List<String>, known: Map<String, KnownDifference>, ktValues: Map<String, String>, exportDir: String) {
        val testClass = Thread.currentThread().stackTrace.map { it.className }.firstOrNull { it.endsWith("DifferentialTest") } ?: return
        val dir = File(exportDir).apply { mkdirs() }
        val entries = expressions.joinToString(",\n") { expression ->
            val documented = known[expression]
            val kt = documented?.kt ?: ktValues[expression]
            "  {\"expression\": ${json(expression)}, \"js\": ${json(documented?.js)}, \"kt\": ${json(kt)}, \"reason\": ${json(documented?.reason)}}"
        }
        File(dir, testClass.substringAfterLast('.') + ".json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
    }

    private fun json(value: String?): String = when (value) {
        null -> "null"
        else -> buildString {
            append('"')
            value.forEach { c ->
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c < ' ' || c.code > 0x7e -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }

}
