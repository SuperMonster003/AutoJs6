package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.render.ClassicRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicRendererTest {

    private val renderer = ClassicRenderer()
    private val cases = RendererTestSupport

    @Test
    fun everyFamilyModeActionAndReceiverParsesAndActsOnTheExpectedTargets() {
        var count = 0
        for (original in cases.plans) for (mode in cases.modes) for (action in cases.actions) for (receiver in cases.receivers) {
            val plan = cases.withReceiver(original, receiver)
            val source = renderer.render(cases.request(plan, mode, action), plan)
            cases.parse(source)
            assertEquals(source, cases.expectedActions(plan, action), cases.evaluate(source))
            count++
        }
        assertEquals(288, count)
    }

    @Test
    fun selfQueryTableSnapshots() {
        val selector = SelectorSpec(Condition("id", "a"))
        val rows = listOf(
            Triple(Mode.WAIT, Cardinality.ONE, "id(\"a\").untilFindOne().click();" to "let w = id(\"a\").untilFindOne();"),
            Triple(Mode.WAIT, Cardinality.ALL, "id(\"a\").untilFind().clickEach();" to "let ws = id(\"a\").untilFind();"),
            Triple(Mode.WAIT_TIMEOUT(3_000), Cardinality.ONE, "let w = id(\"a\").findOne(3000);\nif (w) {\n    w.click();\n}" to "let w = id(\"a\").findOne(3000);"),
            Triple(Mode.WAIT_TIMEOUT(3_000), Cardinality.ALL, "if (id(\"a\").findOne(3000)) {\n    id(\"a\").find().clickEach();\n}" to "let ws = id(\"a\").findOne(3000) ? id(\"a\").find() : null;"),
            Triple(Mode.CHECK, Cardinality.ONE, "let w = id(\"a\").findOnce();\nif (w) {\n    w.click();\n}" to "let found = id(\"a\").exists();"),
            Triple(Mode.CHECK, Cardinality.ALL, "let ws = id(\"a\").find();\nif (ws.nonEmpty()) {\n    ws.clickEach();\n}" to "let ws = id(\"a\").find();"),
        )
        for ((mode, cardinality, expected) in rows) {
            val plan = LocatePlan(Family.SELF, selector, cardinality = cardinality)
            assertEquals(expected.first, renderer.render(cases.request(plan, mode, Action.CLICK), plan))
            assertEquals(expected.second, renderer.render(cases.request(plan, mode), plan))
        }
    }

    @Test
    fun functionQueryTableSnapshots() {
        val plan = LocatePlan(Family.SELF_INDEX, SelectorSpec(Condition("id", "a")), listOf(PlanStep.Index(-2)))
        val prefix = "function locate() {\n    return id(\"a\").findOnce(-2);\n}\n\n"
        val rows = listOf(
            Mode.WAIT to ("wait(locate, Infinity, 200, { then: w => w.click() });" to "let w = wait(locate, Infinity, 200, { then: w => w });"),
            Mode.WAIT_TIMEOUT(3_000) to ("wait(locate, 3000, 200, { then: w => w.click() });" to "let w = wait(locate, 3000, 200, { then: w => w });"),
            Mode.CHECK to ("let w = locate();\nif (w) {\n    w.click();\n}" to "let w = locate();"),
        )
        for ((mode, expected) in rows) {
            assertEquals(prefix + expected.first, renderer.render(cases.request(plan, mode, Action.CLICK), plan))
            assertEquals(prefix + expected.second, renderer.render(cases.request(plan, mode), plan))
        }
    }

    @Test
    fun relationFamilySnapshotsUseOneNullSafeCompassOrSubtreeQuery() {
        val path = cases.plans.single { it.family == Family.ANCHOR_PATH }
        assertEquals("""
            function locate() {
                let a = id("example:id/label").findOnce();
                return a && a.compass("p1c-1");
            }

            let w = locate();
        """.trimIndent(), renderer.render(cases.request(path, Mode.CHECK), path))

        val search = cases.plans.single { it.family == Family.ANCHOR_SEARCH }
        assertEquals("""
            function locate() {
                let a = id("example:id/row2").findOnce();
                return a && a.findOne(id("example:id/item"));
            }

            let w = locate();
        """.trimIndent(), renderer.render(cases.request(search, Mode.CHECK), search))

        val collection = cases.plans.single { it.family == Family.CONTAINER_ITEMS }
        assertEquals("""
            function locate() {
                let a = id("example:id/root").findOnce();
                let ws = a && a.find(id("example:id/item"));
                return ws && ws.nonEmpty() ? ws : null;
            }

            let ws = wait(locate, Infinity, 200, { then: ws => ws });
        """.trimIndent(), renderer.render(cases.request(collection, Mode.WAIT), collection))

        val relative = cases.plans.single { it.family == Family.SELF_RELATIVE && it.cardinality == Cardinality.ONE }
        assertEquals("""
            function locate() {
                return id("example:id/item").filter(w => {
                    let r = w.compass("s<1");
                    return r !== null && r.findOne(id("example:id/label")) !== null;
                }).findOnce();
            }

            let w = locate();
        """.trimIndent(), renderer.render(cases.request(relative, Mode.CHECK), relative))

        val relativeCollection = cases.plans.single { it.family == Family.SELF_RELATIVE && it.cardinality == Cardinality.ALL }
        assertEquals("""
            function locate() {
                let ws = id("example:id/item").filter(w => {
                    let r = w.compass("p1");
                    return r !== null && r.findOne(id("example:id/item")) !== null;
                }).find();
                return ws.nonEmpty() ? ws : null;
            }

            let ws = wait(locate, Infinity, 200, { then: ws => ws });
        """.trimIndent(), renderer.render(cases.request(relativeCollection, Mode.WAIT), relativeCollection))
    }

    @Test
    fun missingTargetsSkipActionsAndKeepTheDocumentedNoActionResult() {
        for (plan in cases.plans) for (mode in listOf(Mode.CHECK, Mode.WAIT_TIMEOUT(100))) for (action in cases.actions) {
            val source = renderer.render(cases.request(plan, mode, action), plan)
            assertEquals(source, "", cases.evaluate(source, "hiddenTargets = true; row2.children = [];"))
            if (action == Action.NONE) {
                val expected = when {
                    plan.family == Family.SELF && mode == Mode.CHECK && plan.cardinality == Cardinality.ONE -> "false"
                    plan.family == Family.SELF && mode == Mode.CHECK -> "collection:"
                    plan.family != Family.SELF && mode is Mode.WAIT_TIMEOUT -> "false"
                    else -> "null"
                }
                assertEquals(source, expected, cases.evaluate(source, "hiddenTargets = true; row2.children = [];", cases.RESULT))
            }
        }
    }

    @Test
    fun missingAnchorsAndUnreachablePathsAreNullSafe() {
        for (plan in cases.plans.filter { it.family in listOf(Family.ANCHOR_PATH, Family.ANCHOR_SEARCH, Family.CONTAINER_ITEMS) }) {
            for (mode in listOf(Mode.CHECK, Mode.WAIT_TIMEOUT(100))) {
                val source = renderer.render(cases.request(plan, mode, Action.CLICK), plan)
                assertEquals("", cases.evaluate(source, "hiddenAnchors = true;"))
            }
        }
        val path = LocatePlan(Family.ANCHOR_PATH, SelectorSpec(Condition("id", "example:id/label")), listOf(PlanStep.Up(20), PlanStep.Child(0)))
        val source = renderer.render(cases.request(path, Mode.CHECK, Action.CLICK), path)
        assertEquals("", cases.evaluate(source))
        assertEquals(1, Regex("\\.compass\\(").findAll(source).count())
    }

    @Test
    fun ancestorReceiversAreCheckedAndCollectionsAttemptEveryAction() {
        for (original in cases.plans) for (mode in cases.modes) {
            val plan = cases.withReceiver(original, PlanReceiver.Up(1))
            val source = renderer.render(cases.request(plan, mode, Action.CLICK), plan)
            assertEquals(source, "", cases.evaluate(source, "missingReceiver = true;"))
            assertEquals(source, cases.expectedActions(plan, Action.CLICK), cases.evaluate(source, "failActions = true;"))
        }
    }

    @Test
    fun setTextPreservesTheEntireArgumentIncludingEmptyAndEscapedText() {
        for (plan in cases.plans) for (value in listOf("", cases.textArgument)) {
            val source = renderer.render(cases.request(plan, Mode.CHECK, Action.SET_TEXT(value)), plan)
            cases.parse(source)
            assertEquals(source, value, cases.evaluate(source, expression = "calls[0][2]"))
            assertFalse(source, source.contains("setText()"))
        }
    }

    @Test
    fun noActionKeepsTheTargetInsteadOfTheAncestorReceiver() {
        for (original in cases.plans) for (mode in cases.modes) {
            val plan = cases.withReceiver(original, PlanReceiver.Up(1))
            val source = renderer.render(cases.request(plan, mode), plan)
            val expected = when {
                mode == Mode.CHECK && plan.family == Family.SELF && plan.cardinality == Cardinality.ONE -> "true"
                plan.cardinality == Cardinality.ALL -> "collection:other,target"
                else -> "node:target"
            }
            assertEquals(source, expected, cases.evaluate(source, expression = cases.RESULT))
        }
    }

    @Test
    fun invalidRequestAndCardinalityMismatchCannotProduceMisleadingCode() {
        val plan = cases.plans.first()
        val request = cases.request(plan, Mode.WAIT)
        assertThrows(IllegalArgumentException::class.java) { renderer.render(request.copy(mode = Mode.WAIT_TIMEOUT(99)), plan) }
        assertThrows(IllegalArgumentException::class.java) { renderer.render(request.copy(scope = CodeGenRequest.Scope.COLLECTION), plan) }
        assertThrows(IllegalArgumentException::class.java) { renderer.render(request.copy(receiver = CodeGenRequest.Receiver.SELF), cases.withReceiver(plan, PlanReceiver.Up(1))) }
        assertTrue(renderer.render(request.copy(mode = Mode.WAIT_TIMEOUT(600_000)), plan).contains("findOne(600000)"))
    }
}
