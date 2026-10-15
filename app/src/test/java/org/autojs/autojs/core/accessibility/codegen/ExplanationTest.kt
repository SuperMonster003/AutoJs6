package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.rank.Explanation
import org.autojs.autojs.core.accessibility.codegen.rank.Explanation.Key
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationTest {
    private fun spec(attr: String, value: Any) = SelectorSpec(Condition(attr, value))
    private val root = FakeUiNode.root {
        id = "app:id/root"
        child("list", clickable = true) {
            id = "app:id/list"
            repeat(10) { index ->
                child("button$index") {
                    id = "app:id/button"; className = "android.widget.Button"; text = "Buy $index"; desc = "Position $index"
                }
            }
        }
    }
    private val verifier = PlanVerifier(root)

    private fun explain(plan: LocatePlan, targetIndex: Int = 3, action: CodeGenRequest.Action = CodeGenRequest.Action.NONE): List<Explanation.Line> {
        val targets = if (plan.cardinality == LocatePlan.Cardinality.ALL) (0..9).map { root.find("button$it") }
        else listOf(root.find("button$targetIndex"))
        val request = CodeGenRequest(targets, scope = if (plan.cardinality == LocatePlan.Cardinality.ALL) CodeGenRequest.Scope.COLLECTION else CodeGenRequest.Scope.SINGLE, action = action)
        val verified = verifier.verify(request, plan)
        assertTrue("Expected verified: $verified", verified is Verified)
        return Explanation.describe(request, verified as Verified<FakeUiNode>).also { lines ->
            assertTrue(lines.size in 1..3)
            assertEquals(Key.ACTIVE_WINDOW_ONLY, lines.last().messages.single().key)
            assertTrue(lines.all { it.renderChinese().isNotBlank() })
            assertFalse(lines.any { it.renderChinese().contains('%') })
        }
    }

    private fun keys(lines: List<Explanation.Line>) = lines.flatMap { it.messages }.map { it.key }

    @Test
    fun everyFamilyHasAnAccurateSummaryAndCapturedWindowLimit() {
        val cases = listOf(
            LocatePlan(LocatePlan.Family.SELF, spec("text", "Buy 3")) to Key.SELF_UNIQUE,
            LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id", "app:id/button"), listOf(PlanStep.Index(3))) to Key.INDEXED_MATCH,
            LocatePlan(LocatePlan.Family.ANCHOR_PATH, spec("id", "app:id/list"), listOf(PlanStep.Child(3))) to Key.ANCHOR_PATH,
            LocatePlan(LocatePlan.Family.ANCHOR_SEARCH, spec("id", "app:id/list"), listOf(PlanStep.Search(spec("text", "Buy 3")))) to Key.ANCHOR_SEARCH_UNIQUE,
            LocatePlan(LocatePlan.Family.CONTAINER_ITEMS, spec("id", "app:id/list"), listOf(PlanStep.Search(spec("id", "app:id/button"))), LocatePlan.Cardinality.ALL) to Key.CONTAINER_ITEMS,
            LocatePlan(LocatePlan.Family.SELF, spec("id", "app:id/button"), cardinality = LocatePlan.Cardinality.ALL) to Key.SELF_COLLECTION,
            LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/button"), listOf(PlanStep.Sibling(1), PlanStep.Match(spec("text", "Buy 4")))) to Key.RELATIVE_UNIQUE,
            LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/button"), listOf(PlanStep.Up(1), PlanStep.Match(spec("id", "app:id/list"))), LocatePlan.Cardinality.ALL) to Key.RELATIVE_COLLECTION,
        )
        cases.forEach { (plan, key) -> assertEquals(key, explain(plan).first().messages.single().key) }
    }

    @Test
    fun relativePlansDiscloseTheirRelationAndOrderRiskOnlyWhenSeveralEntriesSurvive() {
        val unique = explain(LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/button"), listOf(PlanStep.Sibling(1), PlanStep.Match(spec("text", "Buy 4")))))
        assertEquals(Key.RELATIVE_UNIQUE, unique.first().messages.single().key)
        assertEquals(listOf("id", "10", "s>1", "text"), unique.first().messages.single().arguments)
        assertTrue(Key.FIXED_RELATION in keys(unique))
        assertFalse(Key.ORDER_DEPENDENT in keys(unique))
        val several = explain(LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/button"), listOf(PlanStep.Up(1), PlanStep.Match(spec("id", "app:id/list")))), targetIndex = 0)
        assertEquals(Key.RELATIVE_FIRST_MATCH, several.first().messages.single().key)
        assertEquals(listOf("id", "10", "p1", "id", "10"), several.first().messages.single().arguments)
        assertTrue(Key.ORDER_DEPENDENT in keys(several))
        assertTrue(unique.first().renderChinese().contains("s>1"))
    }

    @Test
    fun relativeContentRepeatedInTheCaptureDisclosesOrderRiskAlthoughOneEntrySurvives() {
        val shelf = FakeUiNode.root {
            repeat(2) { index ->
                child("card$index") {
                    child("title$index") { desc = "Game $index" }
                    child("play$index") { desc = "Play" }
                }
            }
        }
        val request = CodeGenRequest(listOf(shelf.find("play1")))
        val plan = LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("desc", "Play"), listOf(PlanStep.Up(1), PlanStep.Sibling(-1), PlanStep.Match(spec("desc", "Play"))))
        val lines = Explanation.describe(request, PlanVerifier(shelf).verify(request, plan) as Verified<FakeUiNode>)
        assertEquals(Key.RELATIVE_UNIQUE, lines.first().messages.single().key)
        assertTrue(Key.ORDER_DEPENDENT in keys(lines))
    }

    @Test
    fun firstOfManyNeverClaimsTheTargetSelectorIsUnique() {
        val self = explain(LocatePlan(LocatePlan.Family.SELF, spec("id", "app:id/button")), targetIndex = 0)
        assertEquals(Key.SELF_FIRST_MATCH, self.first().messages.single().key)
        assertFalse(self.first().renderChinese().contains("唯一"))
        assertTrue(Key.ORDER_DEPENDENT in keys(self))
        val scoped = explain(LocatePlan(LocatePlan.Family.ANCHOR_SEARCH, spec("id", "app:id/list"),
            listOf(PlanStep.Search(spec("id", "app:id/button")))), targetIndex = 0)
        assertEquals(Key.ANCHOR_SEARCH_FIRST_MATCH, scoped.first().messages.single().key)
        assertTrue(Key.ORDER_DEPENDENT in keys(scoped))
    }

    @Test
    fun positiveAndNegativeIndicesHaveHumanReadablePositionsAndOrderRisk() {
        val positive = explain(LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id", "app:id/button"), listOf(PlanStep.Index(3))))
        assertEquals(listOf("10", "4"), positive.first().messages.single().arguments)
        assertTrue(Key.ORDER_DEPENDENT in keys(positive))
        val negative = explain(LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id", "app:id/button"), listOf(PlanStep.Index(-7))))
        assertEquals(Key.INDEXED_MATCH_FROM_END, negative.first().messages.single().key)
        assertEquals(listOf("10", "7"), negative.first().messages.single().arguments)
    }

    @Test
    fun dynamicTextDescriptionAndStateDependenciesAreDisclosed() {
        val text = explain(LocatePlan(LocatePlan.Family.SELF, spec("text", "Buy 3")))
        assertTrue(Key.DYNAMIC_TEXT in keys(text))
        val desc = explain(LocatePlan(LocatePlan.Family.SELF, spec("desc", "Position 3")))
        assertTrue(Key.DYNAMIC_DESCRIPTION in keys(desc))
        val structural = explain(LocatePlan(LocatePlan.Family.SELF,
            SelectorSpec(Condition("id", "app:id/button"), Condition("indexInParent", 3))))
        assertTrue(Key.STRUCTURAL_CONDITIONS in keys(structural))
    }

    @Test
    fun fixedLongPathsAndLargeOrdinalsAreDisclosed() {
        val path = LocatePlan(LocatePlan.Family.ANCHOR_PATH, spec("text", "Buy 0"),
            listOf(PlanStep.Up(2), PlanStep.Child(0), PlanStep.Child(9)))
        val lines = explain(path, targetIndex = 9)
        assertTrue(Key.FIXED_PATH in keys(lines))
        assertTrue(Key.LONG_PATH in keys(lines))
        assertTrue(Key.LARGE_INDEX in keys(lines))
        assertEquals(listOf("4"), lines.first().messages.single().arguments)
        val indexed = explain(LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id", "app:id/button"), listOf(PlanStep.Index(9))), targetIndex = 9)
        assertTrue(Key.LARGE_INDEX in keys(indexed))
    }

    @Test
    fun ancestorReceiverIsExplainedWithoutClaimingTheLabelItselfIsClicked() {
        val plan = LocatePlan(LocatePlan.Family.SELF, spec("text", "Buy 3"), receiver = PlanReceiver.Up(1))
        val lines = explain(plan, action = CodeGenRequest.Action.CLICK)
        val receiverMessage = lines.flatMap { it.messages }.single { it.key == Key.RECEIVER_ANCESTOR }
        assertEquals(listOf("1"), receiverMessage.arguments)
    }
}
