package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.flow.Flow
import org.autojs.autojs.runtime.api.augment.selector.Selector
import org.autojs.autojs.runtime.api.augment.selector.SelectorGlobals
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject.READONLY
import java.util.concurrent.ConcurrentHashMap

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
        val MODULES = listOf("automator", "flow", "selector")
        val JAVA_OBJECT_MEMBERS = listOf("equals", "hashCode", "toString")
        val AUTOMATOR_NAMES = listOf("click", "longClick", "scrollDown", "scrollUp", "setText")
        val MODULE_GLOBALS = listOf("selector", "\$selector", "pickup", "detect", "existsAll", "existsOne")
        val PATHS = listOf("selector")
        val GLOBALS = SelectorGlobals.names + MODULE_GLOBALS
    }

}
