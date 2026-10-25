package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.PatternPrecheck
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.core.automator.pick.PickVerdict
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.flow.Flow
import org.autojs.autojs.runtime.api.augment.selector.Selector
import org.autojs.autojs.runtime.api.augment.selector.SelectorGlobals
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject.READONLY
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Roadmap P3.3, third group: `selector` on the new script module layer, with `automator` and `flow` mounted before it
 * as in the host order, so that both sides leave the same names (`click`, `setText`, ...) to `automator`. The surface of
 * the module, of `pickup` / `detect` / `existsAll` / `existsOne` and of every global selector function is compared with
 * the current augment calls in the same JVM ([RuntimeModulesFixture]); the argument rules run here, and the cases that
 * neither reach the accessibility service nor act on a node go to `build/script-modules-device/SelectorModulesTest.json`.
 *
 * zh-CN: P3.3 第三组: 新层上的 selector, 按宿主顺序在它之前挂载 automator 与 flow, 两侧都把同样的名称 (click, setText 等) 留给 automator. 模块,
 * pickup / detect / existsAll / existsOne 与每个全局选择器函数的表面在同一 JVM 中与现有 augment 调用比较; 参数规则在此检查, 既不到达无障碍服务也不操作节点的
 * 用例写入设备用例.
 */
class SelectorModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide(newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, PATHS)
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, GLOBALS, PATHS) }
        assertEquals(expected, actual)
    }

    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        val own = SelectorGlobals.names.filter { it !in AUTOMATOR_NAMES && it !in SelectorGlobals.excluded && it !in JAVA_OBJECT_MEMBERS }
        assertTrue(own.isNotEmpty())
        (own + MODULE_GLOBALS).forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    @Test
    fun selectorArguments() = RuntimeModulesFixture.check(
        rhino, listOf(
            "selector(1)" to "throws TypeError",
            "pickup(1, 2, 3, 4, 5, 6)" to "throws TypeError",
            "pickup(1, 'a', '>', 'w', null)" to "throws TypeError",
            "pickup('a', 1, 'w', null)" to "throws TypeError",
            "pickup('a', '>', 'w', 1)" to "throws TypeError",
            "detect()" to "throws TypeError",
            "detect(null)" to "throws TypeError",
            "detect(1, 'p')" to "throws TypeError",
            "detect(null, 1, 'w')" to "throws TypeError",
            "detect(null, 'p', 'w', 1)" to "throws TypeError",
            "detect(null, 'p', 'w', null, 1)" to "throws TypeError",
            "text(1, 2, 3)" to "throws TypeError",
            "findOne('x')" to "throws TypeError",
        )
    )

    /**
     * The selector forms, compasses and result types the current readers refuse (appendix C Q66 (2), Q67) and the values a
     * condition refuses (Q68), run on the device with the switch on only: the current module refuses them as well, but
     * only once a node is found, or after a console warning. Most are told by [PickArguments] without an exception
     * (Q69 (2), selector P1.4); the last case has the regex engine of the device (ICU) refuse every pattern the check refuses.
     */
    @Test
    fun selectorForms() {
        val cases = listOf(
            "pickup(undefined)" to "throws TypeError",
            "pickup(true)" to "throws TypeError",
            "pickup({ nope: 1 })" to "throws TypeError",
            "pickup({ clickable: 'x' })" to "throws TypeError",
            "pickup(['aj6-none', { nope: 1 }])" to "throws TypeError",
            "pickup([[true]])" to "throws TypeError",
            "pickup(selector().text)" to "throws TypeError",
            "pickup({ depth: NaN })" to "throws RangeError",
            "pickup({ textMatches: '[' })" to "throws RangeError",
            "pickup('aj6-none', 'nope')" to "throws RangeError",
            "pickup('aj6-none', ['click', 1])" to "throws RangeError",
            "pickup('aj6-none', ['child', NaN])" to "throws RangeError",
            "pickup('aj6-none', ['child', '0'])" to "throws RangeError",
            "pickup('aj6-none', 'p', '@')" to "throws TypeError",
            "pickup('aj6-none', 'c')" to "throws RangeError",
            "pickup('aj6-none', 'x2', 'w')" to "throws RangeError",
            "detect(null, 'widgets')" to "throws RangeError",
            "detect(null, ['#'])" to "throws RangeError",
            "detect(null, 'c', 'w')" to "throws RangeError",
            "existsAll({ nope: 1 })" to "throws TypeError",
            "depth(NaN)" to "throws RangeError",
            "textMatches('[')" to "throws RangeError",
            "pickup({ depth: [NaN] })" to "throws RangeError",
            "pickup({ textMatch: '(' })" to "throws RangeError",
            "pickup({ textMatches: '(?i)(' })" to "throws RangeError",
            "pickup('aj6-none', ['setTextSelection', 1, NaN])" to "throws RangeError",
            "pickup('aj6-none', 'c2147483648')" to "throws RangeError",
            "detect(null, 's', 'w')" to "throws RangeError",
            "textMatch('a|*')" to "throws RangeError",
            "indexInParent(NaN)" to "throws RangeError",
            // Q70 (2): refused by the full syntax check of PatternPrecheck. zh-CN: Q70 (2): 由 PatternPrecheck 的完整语法检查拒绝.
            "pickup({ textMatches: '[z-a]' })" to "throws RangeError",
            "textMatches('a{2,1}')" to "throws RangeError",
            "pickup({ textMatch: '(?<1a>x)' })" to "throws RangeError",
            "textMatches('(?<a>x)\\\\k<b>')" to "throws RangeError",
            "[${refusedPatterns().joinToString { "'${it.replace("\\", "\\\\")}'" }}].filter(function (p) {" +
                    " try { java.util.regex.Pattern.compile(p); return true; } catch (e) { return false; } }).join(' ')" to "\"\"",
        )
        RuntimeModulesFixture.check(rhino, cases)
        // The message of an unknown algorithm is a string resource, which the JVM has not: on the device only.
        RuntimeModulesFixture.exportForDevice(EXPORT_FORMS, cases + listOf("pickup({ algorithm: 'x' })" to "throws RangeError", "algorithm('x')" to "throws RangeError"))
    }

    /**
     * Patterns [PatternPrecheck.refusal] refuses, up to [REFUSED_PER_KIND] of each description, from random strings
     * of the characters the check looks at (no `/`, so no literal form).
     */
    private fun refusedPatterns(): List<String> {
        val random = Random(69)
        val byKind = LinkedHashMap<String, MutableSet<String>>()
        repeat(50_000) {
            val s = buildString { repeat(1 + random.nextInt(8)) { append(PATTERN_ALPHABET[random.nextInt(PATTERN_ALPHABET.length)]) } }
            val refusal = PatternPrecheck.refusal(s) ?: return@repeat
            byKind.getOrPut(refusal.substringBefore(" near index")) { LinkedHashSet() }.takeIf { it.size < REFUSED_PER_KIND }?.add(s)
        }
        // The kinds of refusal random strings of these characters reach (seven before Q70 (2)).
        assertEquals(19, byKind.size)
        return byKind.values.flatten()
    }

    /**
     * JVM only (they search on a device): what the current readers accept passes the checks of [selectorForms]. The compat
     * setters (`setTextSelection` ...) are no result types since the registry of D11 (docs/selector/ROADMAP.md appendix C Q10).
     */
    @Test
    fun acceptedSelectorForms() {
        val pick = PickArguments.uiObject
        listOf(
            "null", "'a'", "'a' + 'b'", "/a/", "1", "1.5", "selector().text('a')", "[]", "['a', null, /b/, [1.5, ['c']]]",
            "({ text: 'a', textMatches: /a/, depth: 2, clickable: true, filter: function () { return true; }, action: null })",
            "[{ desc: 'b', boundsInside: [0, 0, '100', 100] }, 'c']",
            "({ textMatch: 'a(b)c', textMatches: '(?i)a', depth: [2], algorithm: 'dfs', indexInParent: Infinity })",
        ).forEach { assertNull(it, pick.selectorError("pickup", "selector", unwrapped(it))) }
        listOf(
            "'w'", "''", "'widgets'", "'?'", "'@'", "'text'", "'bounds'", "'click'", "'parent'", "1", "[]", "[1]",
            "['child', 0]", "['setText', 'a']", "['compass', 'p']", "['scrollTo', 1, 2]", "['#']", "['txt']", "['setSelection', 1, Infinity]",
        ).forEach { assertNull(it, pick.resultTypeError("pickup", "resultType", unwrapped(it), null, single = false)) }
        listOf("'w'", "'text'", "['child', 0]", "'@'").forEach { assertNull(it, pick.resultTypeError("detect", "resultType", unwrapped(it), "%", single = true)) }
        listOf("'?'", "'w'", "['child', 0]", "'widgets'").forEach { assertNull(it, pick.resultTypeError("pickup", "resultType", unwrapped(it), "p2", single = false)) }
        listOf(
            "'%'", "'p'", "'p2'", "'pp'", "'c0'", "'c-1'", "'c0>1'", "'s1'", "'s<1'", "'s>-1'", "'k'", "'k2'", "'p2c1'",
            "''", "'p0'", "'k0'", "'c2147483647'", "'c-2147483648'",
        ).forEach { assertNull(it, pick.compassError("pickup", "compass", unwrapped(it) as CharSequence)) }
        listOf("null", "undefined").forEach { assertTrue(it, pick.pickup(arrayOf("a", unwrapped(it), "w"), BINDING) is PickVerdict.Accepted) }
    }

    private fun unwrapped(source: String): Any? = RhinoUtils.unwrap(rhino.evalKt(source))

    /** Cases that hold in both states of the switch on a device (the current module throws for the same arguments). */
    @Test
    fun exportsDeviceCases() = listOf(
            "typeof selector" to "\"function\"",
            "\$selector === selector" to "true",
            "typeof pickup" to "\"function\"",
            "typeof \$pickup" to "\"undefined\"",
            "typeof selector.pickup" to "\"undefined\"",
            "existsAll()" to "true",
            "existsOne()" to "false",
            "typeof text" to "\"function\"",
            "typeof scrollForward" to "\"function\"",
            "click === automator.click" to "true",
            "text('a') !== text('a')" to "true",
            "pickup(1, 2, 3, 4, 5, 6)" to "throws TypeError",
            "pickup(1, 'a', '>', 'w', null)" to "throws TypeError",
            "pickup('a', 1, 'w', null)" to "throws TypeError",
            "pickup('a', '>', 'w', 1)" to "throws TypeError",
            "detect()" to "throws TypeError",
            "detect(null)" to "throws TypeError",
            "detect(1, 'p')" to "throws TypeError",
            "detect(null, 1, 'w')" to "throws TypeError",
            "detect(null, 'p', 'w', 1)" to "throws TypeError",
        ).let { cases ->
            RuntimeModulesFixture.check(rhino, cases)
            RuntimeModulesFixture.exportForDevice(EXPORT, cases)
        }

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        val runtime = side.ktRuntime
        RuntimeModulesFixture.prepareHost(runtime)
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "scale", Scale(runtime))
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "mProperties", ConcurrentHashMap<String, Any>())
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "closeableManager", RuntimeResourceScope("selector-modules-test"))
        RuntimeModulesFixture.mount(side, MODULES, newLayer) { name, global, rt ->
            when (name) {
                "automator" -> Automator(rt).augment(global)
                "flow" -> Flow(rt).augment(global)
                "selector" -> Selector(rt).augment(global, additionalAttributes = READONLY)
                else -> throw AssertionError(name)
            }
        }
    }

    private companion object {
        const val EXPORT = "SelectorModulesTest"
        const val EXPORT_FORMS = "SelectorFormsTest"
        const val PATTERN_ALPHABET = "[]()*+?|\\a{}^-:=!<>.QEcpdx"
        const val REFUSED_PER_KIND = 40
        val MODULES = listOf("automator", "flow", "selector")
        val JAVA_OBJECT_MEMBERS = listOf("equals", "hashCode", "toString")
        val AUTOMATOR_NAMES = listOf("click", "longClick", "scrollDown", "scrollUp", "setText")
        val MODULE_GLOBALS = listOf("selector", "\$selector", "pickup", "detect", "existsAll", "existsOne")
        val PATHS = listOf("selector")
        val GLOBALS = SelectorGlobals.names + MODULE_GLOBALS

        /** Selectors without a bridge: [acceptedSelectorForms] only normalizes, never searches. */
        val BINDING = PickArguments.Binding({ UiSelector() }, { BooleanFilter.BooleanSupplier { true } })
    }

}
