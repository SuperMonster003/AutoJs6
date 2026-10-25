package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.render.FlowRenderer
import org.autojs.autojs.runtime.api.modules.selector.SelectorGlobals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.ast.FunctionCall
import org.mozilla.javascript.ast.FunctionNode
import org.mozilla.javascript.ast.Name
import java.io.File

class FlowRendererTest {

    private val renderer = FlowRenderer()
    private val cases = RendererTestSupport

    @Test
    fun everyFamilyModeActionAndReceiverParsesAndActsOnTheExpectedTargets() {
        var count = 0
        for (original in cases.plans) for (mode in cases.modes) for (action in cases.actions) for (receiver in cases.receivers) {
            val plan = cases.withReceiver(original, receiver)
            val source = renderer.render(cases.request(plan, mode, action, CodeGenRequest.Style.FLOW), plan)
            cases.parse(source)
            assertEquals(source, cases.expectedActions(plan, action), cases.evaluate(source))
            assertEquals(source, when (mode) {
                Mode.WAIT -> Double.POSITIVE_INFINITY
                is Mode.WAIT_TIMEOUT -> mode.millis.toDouble()
                Mode.CHECK -> 0.0
            }, (cases.evaluate(source, expression = "observedTimeout") as Number).toDouble(), 0.0)
            if (plan.cardinality == Cardinality.ALL) assertEquals("widgets", cases.evaluate(source, expression = "observedResultType"))
            count++
        }
        assertEquals(288, count)
    }

    @Test
    fun selfQueryTableSnapshots() {
        val selector = SelectorSpec(Condition("id", "a"))
        val rows = listOf(
            Triple(Mode.WAIT, Cardinality.ONE, "waitThenClick(id(\"a\"), { timeout: Infinity }).sync();" to "let w = waitAsync(id(\"a\"), { timeout: Infinity }).sync();"),
            Triple(Mode.WAIT, Cardinality.ALL, "waitThenClick(id(\"a\"), { timeout: Infinity, resultType: 'widgets' }).sync();" to "let ws = waitAsync(id(\"a\"), { timeout: Infinity, resultType: 'widgets' }).sync();"),
            Triple(Mode.WAIT_TIMEOUT(3_000), Cardinality.ONE, "flow.whenPresent(id(\"a\"), w => w.click(), { timeout: 3000 }).sync();" to "let w = null;\nflow.whenPresent(id(\"a\"), x => { w = x; }, { timeout: 3000 }).sync();"),
            Triple(Mode.WAIT_TIMEOUT(3_000), Cardinality.ALL, "flow.whenPresent(id(\"a\"), ws => ws.clickEach(), { timeout: 3000, resultType: 'widgets' }).sync();" to "let ws = null;\nflow.whenPresent(id(\"a\"), x => { ws = x; }, { timeout: 3000, resultType: 'widgets' }).sync();"),
            Triple(Mode.CHECK, Cardinality.ONE, "flow.whenPresent(id(\"a\"), w => w.click(), { timeout: 0 }).sync();" to "let w = null;\nflow.whenPresent(id(\"a\"), x => { w = x; }, { timeout: 0 }).sync();"),
            Triple(Mode.CHECK, Cardinality.ALL, "flow.whenPresent(id(\"a\"), ws => ws.clickEach(), { timeout: 0, resultType: 'widgets' }).sync();" to "let ws = null;\nflow.whenPresent(id(\"a\"), x => { ws = x; }, { timeout: 0, resultType: 'widgets' }).sync();"),
        )
        for ((mode, cardinality, expected) in rows) {
            val plan = LocatePlan(Family.SELF, selector, cardinality = cardinality)
            assertEquals(expected.first, renderer.render(cases.request(plan, mode, Action.CLICK), plan))
            assertEquals(expected.second, renderer.render(cases.request(plan, mode), plan))
        }
    }

    @Test
    fun everyGeneratedGlobalCallHasAnActualPublicRegistration() {
        // Read the registration declaration rather than copying a second whitelist that can drift.
        // zh-CN: 读取实际注册声明, 避免复制一份会与实现脱节的白名单.
        val relative = "src/main/java/org/autojs/autojs/runtime/api/modules/flow/Flow.kt"
        val flowFile = listOf(File(relative), File("app/$relative")).first { it.isFile }
        val declarations = flowFile.readText().substringAfter("override val globalAssignmentFunctions = listOf(").substringBefore("\n    )")
        val globals = Regex("::(\\w+)\\.name").findAll(declarations).map { it.groupValues[1] }.toSet()
        assertTrue(globals.containsAll(listOf("waitAsync", "waitThenClick")))
        assertFalse(globals.contains("waitThenLongClick"))
        val allowed = globals + SelectorGlobals.names + "selector"
        for (original in cases.plans) for (mode in cases.modes) for (action in cases.actions) for (receiver in cases.receivers) {
            val plan = cases.withReceiver(original, receiver)
            val source = renderer.render(cases.request(plan, mode, action), plan)
            val ast = cases.parse(source)
            val locals = mutableSetOf<String>()
            ast.visit { node -> if (node is FunctionNode && node.name.isNotEmpty()) locals += node.name; true }
            ast.visit { node ->
                if (node is FunctionCall && node.target is Name) {
                    val name = (node.target as Name).identifier
                    assertTrue("Unregistered global $name in:\n$source", name in allowed || name in locals)
                }
                true
            }
        }
    }

    @Test
    fun functionConditionsShareTheClassicSingleProbeAndRejectEmptyCollections() {
        for (plan in cases.plans.filter { it.family != Family.SELF }) {
            val source = renderer.render(cases.request(plan, Mode.WAIT, Action.LONG_CLICK), plan)
            assertTrue(source, source.contains("waitAsync(locate, { timeout: Infinity"))
            assertTrue(source, source.endsWith(".longClick().sync();"))
            for (mode in listOf(Mode.CHECK, Mode.WAIT_TIMEOUT(100))) {
                val missing = renderer.render(cases.request(plan, mode, Action.CLICK), plan)
                assertEquals(missing, "", cases.evaluate(missing, "hiddenTargets = true; row2.children = [];"))
            }
        }
    }

    @Test
    fun missingTargetsSkipHandlersWithoutThrowingOrPerformingActions() {
        for (plan in cases.plans) for (mode in listOf(Mode.CHECK, Mode.WAIT_TIMEOUT(100))) for (action in cases.actions) {
            val source = renderer.render(cases.request(plan, mode, action), plan)
            assertEquals(source, "", cases.evaluate(source, "hiddenTargets = true; row2.children = [];"))
            if (action == Action.NONE) assertEquals(source, "null", cases.evaluate(source, "hiddenTargets = true; row2.children = [];", cases.RESULT))
        }
    }

    @Test
    fun ancestorReceiverCompassIsOnlyUsedForSingleSelectorConditions() {
        for (original in cases.plans) for (mode in cases.modes) {
            val plan = cases.withReceiver(original, PlanReceiver.Up(1))
            val source = renderer.render(cases.request(plan, mode, Action.CLICK), plan)
            val usesCompass = plan.family == Family.SELF && plan.cardinality == Cardinality.ONE
            assertEquals(source, usesCompass, source.contains("compass: 'p1'"))
            if (!usesCompass || mode != Mode.WAIT) assertEquals(source, "", cases.evaluate(source, "missingReceiver = true;"))
            if (!usesCompass) assertTrue(source, source.contains(".parent(1)"))
            if (plan.cardinality == Cardinality.ALL) assertTrue(source, source.contains("ws.each(w =>"))
        }
    }

    @Test
    fun collectionsAttemptEveryTargetAndTextArgumentsRoundTrip() {
        for (plan in cases.plans) for (mode in cases.modes) {
            val click = renderer.render(cases.request(plan, mode, Action.CLICK), plan)
            assertEquals(click, cases.expectedActions(plan, Action.CLICK), cases.evaluate(click, "failActions = true;"))
            for (value in listOf("", cases.textArgument)) {
                val source = renderer.render(cases.request(plan, mode, Action.SET_TEXT(value)), plan)
                assertEquals(value, cases.evaluate(source, expression = "calls[0][2]"))
            }
        }
    }

    @Test
    fun noActionReturnsTargetsAndDoesNotNavigateToAnActionReceiver() {
        for (original in cases.plans) for (mode in cases.modes) {
            val plan = cases.withReceiver(original, PlanReceiver.Up(1))
            val source = renderer.render(cases.request(plan, mode), plan)
            assertFalse(source, source.contains("compass: 'p1'"))
            assertFalse(source, source.contains(".parent("))
            assertEquals(source, if (plan.cardinality == Cardinality.ALL) "collection:other,target" else "node:target", cases.evaluate(source, expression = cases.RESULT))
        }
    }
}
