package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorText
import org.autojs.autojs.core.accessibility.inspector.SelectorCallParser
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.selector.SelectorArgType
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser

class LocatePlanTest {

    private val spec = SelectorSpec(Condition("id", "example:id/item"))
    private fun selector() = UiSelector(null as AccessibilityBridge?)

    @Test
    fun equivalentPlansHaveStableKeysAndValueEquality() {
        val first = LocatePlan(Family.SELF, spec)
        val second = LocatePlan(Family.SELF, SelectorSpec(Condition("id", "example:id/item")))
        assertEquals(first.planKey, second.planKey)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertTrue(first.planKey.startsWith("v1|"))
    }

    @Test
    fun everyPartOfThePlanContributesToItsKey() {
        val plans = listOf(
            LocatePlan(Family.SELF, spec),
            LocatePlan(Family.SELF, spec, cardinality = Cardinality.ALL),
            LocatePlan(Family.SELF, spec, receiver = PlanReceiver.Up(1)),
            LocatePlan(Family.SELF, spec, receiver = PlanReceiver.Up(2)),
            LocatePlan(Family.SELF_INDEX, spec, listOf(PlanStep.Index(0))),
            LocatePlan(Family.SELF_INDEX, spec, listOf(PlanStep.Index(-1))),
            LocatePlan(Family.ANCHOR_PATH, spec, listOf(PlanStep.Up(1), PlanStep.Child(-1))),
            LocatePlan(Family.ANCHOR_PATH, spec, listOf(PlanStep.Child(-1), PlanStep.Up(1))),
            LocatePlan(Family.ANCHOR_SEARCH, spec, listOf(PlanStep.Search(SelectorSpec(Condition("text", "x"))))),
            LocatePlan(Family.ANCHOR_SEARCH, spec, listOf(PlanStep.Search(SelectorSpec(Condition("text", "y"))))),
            LocatePlan(Family.CONTAINER_ITEMS, spec, listOf(PlanStep.Search(SelectorSpec(Condition("text", "x")))), Cardinality.ALL),
            LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(1), PlanStep.Match(SelectorSpec(Condition("text", "x"))))),
            LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(1), PlanStep.Match(SelectorSpec(Condition("text", "y"))))),
            LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Sibling(1), PlanStep.Match(SelectorSpec(Condition("text", "x"))))),
            LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Sibling(-1), PlanStep.Match(SelectorSpec(Condition("text", "x"))))),
            LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(1), PlanStep.Child(0), PlanStep.Match(SelectorSpec(Condition("text", "x"))))),
            LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(1), PlanStep.Match(SelectorSpec(Condition("text", "x")))), Cardinality.ALL),
        )
        assertEquals(plans.size, plans.map { it.planKey }.toSet().size)
    }

    @Test
    fun relativePlansNeedRelationStepsAndANonEmptyMatch() {
        val match = PlanStep.Match(SelectorSpec(Condition("text", "x")))
        val plan = LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(2), PlanStep.Sibling(-1), match))
        assertEquals(listOf(PlanStep.Up(2), PlanStep.Sibling(-1)), plan.relation)
        assertEquals(match.selector, plan.relativeMatch)
        assertTrue(LocatePlan(Family.SELF, spec).relation.isEmpty())
        assertEquals(null, LocatePlan(Family.SELF, spec).relativeMatch)
        assertThrows(IllegalArgumentException::class.java) { PlanStep.Sibling(0) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.SELF_RELATIVE, spec, listOf(match)) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(1))) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(1), PlanStep.Match(SelectorSpec()))) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Up(1), PlanStep.Search(spec))) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.SELF_RELATIVE, spec, listOf(PlanStep.Index(0), match)) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.SELF_RELATIVE, spec, List(5) { PlanStep.Up(1) } + match) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.ANCHOR_PATH, spec, listOf(PlanStep.Sibling(1))) }
    }

    @Test
    fun conditionsKeepTheirOrderAndCannotCollideWithDelimiters() {
        val a = Condition("text", "x|4:self:search(\"y\")\n😀")
        val b = Condition("clickable", true)
        assertNotEquals(LocatePlan(Family.SELF, SelectorSpec(a, b)).planKey, LocatePlan(Family.SELF, SelectorSpec(b, a)).planKey)
        assertNotEquals(LocatePlan(Family.SELF, SelectorSpec(a)).planKey, LocatePlan(Family.SELF, SelectorSpec(Condition("text", "x"), b)).planKey)
    }

    @Test
    fun inputListsCannotMutateAnExistingPlan() {
        val conditions = mutableListOf(Condition("text", "original"))
        val steps = mutableListOf<PlanStep>(PlanStep.Child(0))
        val plan = LocatePlan(Family.ANCHOR_PATH, SelectorSpec(conditions), steps)
        val key = plan.planKey
        conditions.clear()
        steps.clear()
        assertEquals(key, plan.planKey)
        assertEquals(1, plan.selector.conditions.size)
        assertEquals(listOf(PlanStep.Child(0)), plan.steps)
        assertThrows(UnsupportedOperationException::class.java) { (plan.steps as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (plan.selector.conditions as MutableList).clear() }
    }

    @Test
    fun invalidNamesTypesAndPlanShapesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Condition("text);evil", "x") }
        assertThrows(IllegalArgumentException::class.java) { Condition("text", 1) }
        assertThrows(IllegalArgumentException::class.java) { Condition("depth", "2") }
        assertThrows(IllegalArgumentException::class.java) { Condition("depth", Any()) }
        assertThrows(IllegalArgumentException::class.java) { Condition("algorithm", "BFS") }
        assertThrows(IllegalArgumentException::class.java) { Condition("currentApp", "example") }
        assertThrows(IllegalArgumentException::class.java) { PlanStep.Up(0) }
        assertThrows(IllegalArgumentException::class.java) { PlanReceiver.Up(-1) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.SELF_INDEX, spec) }
        assertThrows(IllegalArgumentException::class.java) { LocatePlan(Family.CONTAINER_ITEMS, spec, listOf(PlanStep.Search(spec))) }
    }

    @Test
    fun everyRegisteredSingleLiteralConditionRoundTrips() {
        val parser = SelectorCallParser()
        val supported = setOf(SelectorArgType.STRING, SelectorArgType.BOOLEAN, SelectorArgType.INT, SelectorArgType.DOUBLE)
        for (entry in SelectorRegistry.entries) {
            if (entry.name == "algorithm" || entry.name == "currentApp") continue
            val overload = entry.overloads.firstOrNull { it.params.size == 1 && it.params.single().type in supported } ?: continue
            val value: Any = when (overload.params.single().type) {
                SelectorArgType.STRING -> "a\"b\\n\u2028😀"
                SelectorArgType.BOOLEAN -> false
                else -> 1
            }
            val expression = SelectorText.render(SelectorSpec(Condition(entry.name, value)))
            val call = parser.calls(expression).single()
            assertEquals(entry.name, call.name)
            assertEquals(listOf(value), call.args)
            parser.parse(expression, selector())
            Parser(CompilerEnvirons().apply { languageVersion = Context.VERSION_ES6 }).parse(expression + ";", "plan", 1)
        }
        assertEquals("selector()", SelectorText.render(SelectorSpec()))
    }

    @Test
    fun parsedSelectorAndRegistryApplicationSelectTheSameNodes() {
        fun screen() = FakeUiNode.root {
            child("wanted") {
                id = "example:id/item"
                packageName = "example"
                className = "android.widget.Button"
                text = "a\"b\\c\n😀"
                desc = "description\u2029"
                clickable = true
            }
            child("other") { text = "other" }
        }
        val conditions = listOf(
            Condition("id", "example:id/item"),
            Condition("packageName", "example"),
            Condition("className", "android.widget.Button"),
            Condition("text", "a\"b\\c\n😀"),
            Condition("desc", "description\u2029"),
            Condition("clickable", true),
            Condition("depth", 1),
        )
        val expected = selector()
        for (condition in conditions) assertEquals(SelectorRegistry.Outcome.APPLIED, SelectorRegistry.apply(expected, condition.attr, listOf(condition.value)))
        val parsed = SelectorCallParser().parse(SelectorText.render(SelectorSpec(conditions)), selector())
        val root = screen()
        assertEquals(listOf(root.find("wanted")), parsed.searchIn(root))
        assertEquals(expected.searchIn(screen()).map { it.name }, parsed.searchIn(screen()).map { it.name })
    }
}
