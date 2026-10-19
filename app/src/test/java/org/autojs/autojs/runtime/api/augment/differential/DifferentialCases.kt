package org.autojs.autojs.runtime.api.augment.differential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

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
     */
    fun run(
        rhino: DifferentialRhino,
        expressions: List<String>,
        known: List<KnownDifference> = emptyList(),
        tolerateErrorTypes: Boolean = false,
    ) {
        val knownByExpression = known.associateBy { it.expression }
        val unexpected = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val report = StringBuilder()
        expressions.forEach { expression ->
            val js = rhino.render(DifferentialRhino.Side.JS, expression)
            val kt = rhino.render(DifferentialRhino.Side.KT, expression)
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
        val undocumented = knownByExpression.keys - expressions.toSet()
        assertTrue("Known differences must be listed in the expressions: $undocumented", undocumented.isEmpty())
        assertTrue("Undocumented differences between the obsolete JS module and the Kotlin module:\n" + unexpected.joinToString("\n"), unexpected.isEmpty())
    }

}
