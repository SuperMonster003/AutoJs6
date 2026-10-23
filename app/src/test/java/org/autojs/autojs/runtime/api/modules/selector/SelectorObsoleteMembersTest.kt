package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.flow.Flow
import org.autojs.autojs.runtime.api.augment.selector.Selector
import org.autojs.autojs.runtime.api.augment.selector.SelectorGlobals
import org.autojs.autojs.runtime.api.modules.CurrentModuleResults
import org.autojs.autojs.runtime.api.modules.ObsoleteMembers
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject.READONLY
import java.util.concurrent.ConcurrentHashMap

/**
 * Roadmap P3.6: every member of the obsolete `__selector__.js` exists on the new layer (`automator`, `flow` and
 * `selector` mounted in the host order, as in [SelectorModulesTest]) with the same type. The module object is compared
 * by [SurfaceDiff]; the globals the module added (`pickup`, `detect`, `existsAll`, `existsOne` and a wrapper for every
 * member a `UiSelector` enumerated that the global scope did not have yet) are globals of the same type here. The
 * obsolete module loads with a stub `selector()` that returns a `UiSelector` without an accessibility bridge, before
 * any other module, so it also wraps `click`, `longClick`, `scrollDown`, `scrollUp` and `setText`, which `automator` had
 * taken on a device; here they are the functions of `automator`, of the same type.
 *
 * The obsolete module wrapped every enumerated Java member; the current modules export the registry properties and the
 * listed query and action methods ([SelectorGlobals]), which drops what [SelectorGlobals.excluded] names
 * (`toStringReadable`) and the mangled names of `internal` members (with a `$`), as its documentation records. Only those
 * may be missing, and two the obsolete module wrapped by mistake: `progress` and `selection` are the bean properties Rhino
 * makes of the setter-only `setProgress(float)` and `setSelection(int, int)` of UiObjectActions, which read as undefined,
 * so their wrappers always threw a TypeError (checked here). Their actions stay `setProgress` and `setSelection`.
 *
 * zh-CN: P3.6: 旧 __selector__.js 的每个成员都以相同类型存在于新层 (按宿主顺序挂载 automator, flow 与 selector, 同 SelectorModulesTest). 模块对象用
 * SurfaceDiff 比较; 旧模块添加的全局 (pickup, detect, existsAll, existsOne, 以及 UiSelector 枚举出且全局尚未拥有的每个成员的包装) 在此为同类型的全局. 旧模块以
 * stub selector() 加载 (返回不带无障碍桥的 UiSelector), 且先于其他模块, 所以它也包装了真机上已由 automator 占用的 click, longClick, scrollDown, scrollUp 与
 * setText; 在此它们是 automator 的函数, 类型相同. 旧模块包装每个枚举出的 Java 成员; 现有模块导出注册表属性与列出的查询和动作方法 (SelectorGlobals), 因此去掉了
 * SelectorGlobals.excluded 列出的名称 (toStringReadable) 与 internal 成员的混淆名 (含 $), 这在其文档中有记录. 只有这些可以缺失, 另有两个是旧模块误包装的:
 * progress 与 selection 是 Rhino 由 UiObjectActions 只有 setter 的 setProgress(float) 与 setSelection(int, int) 生成的 bean 属性, 读取为 undefined, 所以其包装
 * 总是抛 TypeError (在此检查). 对应动作仍是 setProgress 与 setSelection.
 */
class SelectorObsoleteMembersTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide()
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModules() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        host.setRuntimeProperty("selector", rhino.evalJs("(function () { return new Packages.org.autojs.autojs.core.accessibility.UiSelector(null); })"))
        val before = host.global.allIds.toSet()
        val selector = host.load("selector")
        val report = SurfaceDiff.compare(SurfaceDiff.surface(selector, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt("selector"), rhino.ktScope.globalThis))
        assertTrue("selector: " + SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())

        val added = host.global.allIds.filterIsInstance<String>().filterNot { it in before }
        assertTrue(added.toString(), added.containsAll(MODULE_GLOBALS) && added.containsAll(AUTOMATOR_NAMES))

        val dead = (added - MODULE_GLOBALS).filter { '$' !in it }
            .filter { rhino.evalJs("typeof new Packages.org.autojs.autojs.core.accessibility.UiSelector(null)['$it']") != "function" }
        assertEquals(DEAD_WRAPPERS, dead.sorted())
        host.setRuntimeProperty("console", rhino.evalJs("({ warn: function () {} })"))
        dead.forEach { assertEquals(it, "TypeError", rhino.evalJs("(function () { try { global['$it'](1, 2); return 'returned'; } catch (e) { return e.name; } })()")) }

        val mismatches = added.filterNot { it in SelectorGlobals.excluded || '$' in it || it in dead }
            .map { Triple(it, ObsoleteMembers.typeOf(host.global, it), ObsoleteMembers.typeOf(rhino.ktScope.globalThis, it)) }
            .filter { (_, js, kt) -> js != kt }
        assertTrue(mismatches.joinToString("\n") { (name, js, kt) -> "$name: js $js, kt $kt" }, mismatches.isEmpty())
    }

    /**
     * Roadmap P3.6, evidence A: the argument and selector form cases of [SelectorModulesTest] (`selectorArguments`,
     * `selectorForms` but its regex engine case, repeated here since that test is not changed) give the results of the
     * current modules, with error names compared loosely ([CurrentModuleResults]); the differences that remain are
     * registered in appendix E (P3.3) and listed in [KNOWN]. Both sides are built in the test body, which the timeout runs
     * on a thread of its own.
     *
     * zh-CN: P3.6 A 类证据: SelectorModulesTest 的参数与选择器形式用例 (selectorArguments, 以及 selectorForms 中除正则引擎那条以外的用例; 该测试不改动,
     * 故在此重复列出) 都得到现有模块的结果, 错误名宽松比较 (CurrentModuleResults); 余下的差异登记在附录 E (P3.3) 并列入 KNOWN. 两侧都在测试体内构造,
     * 因为超时设置让测试体在独立线程运行.
     */
    @Test(timeout = 600_000)
    fun resultsAgreeWithTheCurrentModules() {
        val summary = newSide(newLayer = true).use { new ->
            newSide(newLayer = false).use { current -> CurrentModuleResults.assertAgree(new, current, EXPRESSIONS, KNOWN) }
        }
        println("P3.6 A selector: $summary")
    }

    private fun newSide(newLayer: Boolean = true): DifferentialRhino = DifferentialRhino().also { side ->
        val runtime = side.ktRuntime
        RuntimeModulesFixture.prepareHost(runtime)
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "scale", Scale(runtime))
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "mProperties", ConcurrentHashMap<String, Any>())
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "closeableManager", RuntimeResourceScope("selector-obsolete-members-test"))
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
        val MODULES = listOf("automator", "flow", "selector")
        val MODULE_GLOBALS = listOf("pickup", "detect", "existsAll", "existsOne")
        val AUTOMATOR_NAMES = listOf("click", "longClick", "scrollDown", "scrollUp", "setText")

        /** Bean properties of the setter-only `setProgress(float)` and `setSelection(int, int)` of UiObjectActions. */
        val DEAD_WRAPPERS = listOf("progress", "selection")

        /** The expressions of `selectorArguments` and `selectorForms` in [SelectorModulesTest]. */
        val EXPRESSIONS = listOf(
            "selector(1)", "pickup(1, 2, 3, 4, 5, 6)", "pickup(1, 'a', '>', 'w', null)", "pickup('a', 1, 'w', null)",
            "pickup('a', '>', 'w', 1)", "detect()", "detect(null)", "detect(1, 'p')", "detect(null, 1, 'w')", "detect(null, 'p', 'w', 1)",
            "detect(null, 'p', 'w', null, 1)", "text(1, 2, 3)", "findOne('x')",
            "pickup(undefined)", "pickup(true)", "pickup({ nope: 1 })", "pickup({ clickable: 'x' })", "pickup(['aj6-none', { nope: 1 }])",
            "pickup([[true]])", "pickup(selector().text)", "pickup({ depth: NaN })", "pickup({ textMatches: '[' })",
            "pickup('aj6-none', 'nope')", "pickup('aj6-none', ['click', 1])", "pickup('aj6-none', ['child', NaN])",
            "pickup('aj6-none', ['child', '0'])", "pickup('aj6-none', 'p', '@')", "pickup('aj6-none', 'c')", "pickup('aj6-none', 'x2', 'w')",
            "detect(null, 'widgets')", "detect(null, ['#'])", "detect(null, 'c', 'w')", "existsAll({ nope: 1 })", "depth(NaN)",
            "textMatches('[')", "pickup({ depth: [NaN] })", "pickup({ textMatch: '(' })", "pickup({ textMatches: '(?i)(' })",
            "pickup('aj6-none', ['setTextSelection', 1, NaN])", "pickup('aj6-none', 'c2147483648')", "detect(null, 's', 'w')",
            "textMatch('a|*')", "indexInParent(NaN)",
        )

        /**
         * Differences from the current modules registered in appendix E of docs/augment/ROADMAP.md (P3.3): expression to
         * the new result and the line of the row. On the JVM the current module fails on its console warnings (the fixture
         * has no console, NullPointerException) or finds no node and returns null.
         *
         * zh-CN: 已在 docs/augment/ROADMAP.md 附录 E (P3.3) 登记的与现有模块的差异: 表达式到新层结果与该行行号. JVM 上现有模块在打印控制台警告时失败
         * (夹具没有控制台, NullPointerException), 或找不到节点而返回 null.
         */
        val KNOWN = mapOf(
            // docs/augment/ROADMAP.md L1131: no condition or overload matches (two console warnings first).
            "text(1, 2, 3)" to ("throws TypeError" to "E L1131"),
            "findOne('x')" to ("throws TypeError" to "E L1131"),
            // docs/augment/ROADMAP.md L1147 (Q68): values a condition refuses (two console warnings first).
            "depth(NaN)" to ("throws RangeError" to "E L1147"),
            "textMatches('[')" to ("throws RangeError" to "E L1147"),
            "textMatch('a|*')" to ("throws RangeError" to "E L1147"),
            "indexInParent(NaN)" to ("throws RangeError" to "E L1147"),
            // docs/augment/ROADMAP.md L1144 (Q66 (2)): result types, refused only once a node is found.
            "detect(null, ['#'])" to ("throws RangeError" to "E L1144"),
            // docs/augment/ROADMAP.md L1146 (Q67): the syntax of a compass, parsed only once a node is found.
            "detect(null, 'c', 'w')" to ("throws RangeError" to "E L1146"),
            "detect(null, 's', 'w')" to ("throws RangeError" to "E L1146"),
        )
    }

}
