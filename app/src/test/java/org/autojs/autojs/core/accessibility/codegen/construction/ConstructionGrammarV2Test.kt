package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Companion.GRAMMAR_V1
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Companion.GRAMMAR_V2
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Operation
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Phase
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.State
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionGrammarV2Test {
    private val budget = ConstructionBudget(maxMillis = 30_000)
    private val fruit = "Fruit\nLast played 1 week ago"
    private val mines = "Mines\nLast played 13 weeks ago"
    private val sudoku = "Sudoku\nLast played 14 weeks ago"

    /** A horizontal shelf whose last card is only partly on screen, so its Play button is not captured. */
    private fun shelf(vararg games: Pair<String, Boolean>) = FakeUiNode.root {
        games.forEachIndexed { index, (title, playable) ->
            child("card$index") {
                child("title$index") { desc = title }
                if (playable) child("play$index") { desc = "Play" }
            }
        }
    }

    private val capture = shelf(fruit to true, mines to true, sudoku to false)

    private fun grammar(version: Int, target: String = "play1") =
        ConstructionGrammar(CodeGenRequest(listOf(capture.find(target))), PlanVerifier(capture), budget, CancellationSignal(), version) {}

    private fun ConstructionGrammar<FakeUiNode>.walk(vararg operations: Operation): State =
        operations.fold(initialState()) { state, operation ->
            assertTrue("$operation is not legal in ${state.key}", operation in legal(state))
            apply(state, operation)
        }

    @Test
    fun grammarOneKeepsItsKeyFamiliesConditionsAndFeatureNames() {
        val v1 = grammar(GRAMMAR_V1, "title1")
        assertEquals("g1|FAMILY||-1||||-1|", v1.initialState().key)
        assertEquals(listOf(Family.SELF, Family.SELF_INDEX, Family.ANCHOR_PATH, Family.ANCHOR_SEARCH),
            v1.legal(v1.initialState()).map { (it as Operation.ChooseFamily).family })
        assertTrue(v1.targetConditions.none { it.attr.endsWith("StartsWith") })
        // The frozen round three/four policies were trained on exactly these 87 names.
        assertEquals("f2369debb29eaa54", ConstructionFeatures.namesHash)
        assertEquals(87, ConstructionFeatures.count)
        val v2 = grammar(GRAMMAR_V2, "title1")
        assertEquals(Family.SELF_RELATIVE, (v2.legal(v2.initialState()).last() as Operation.ChooseFamily).family)
        assertTrue(v2.initialState().key.startsWith("g2|FAMILY|"))
    }

    @Test
    fun firstLinePrefixesAreOfferedButNeverBesideTheirExactValue() {
        val v2 = grammar(GRAMMAR_V2, "title1")
        val exact = v2.targetConditions.indexOf(Condition("desc", mines))
        val prefix = v2.targetConditions.indexOf(Condition("descStartsWith", "Mines\n"))
        assertTrue(exact in 0 until prefix)
        val chosen = v2.walk(Operation.ChooseFamily(Family.SELF))
        assertTrue(v2.legal(chosen).any { it is Operation.AddCondition && it.position == prefix })
        val withExact = v2.apply(chosen, Operation.AddCondition(exact, false, "desc"))
        assertTrue(v2.legal(withExact).none { it is Operation.AddCondition && it.position == prefix })
    }

    @Test
    fun relationsAreExactlyTheRuleSearchRelations() {
        val g = grammar(GRAMMAR_V2)
        val relation = g.walk(Operation.ChooseFamily(Family.SELF_RELATIVE), Operation.EndOuter)
        assertEquals(Phase.RELATION, relation.phase)
        // Upward one or two levels, or the title beside the Play button; no end before a first step.
        assertEquals(listOf("relation-up:1", "relation-up:2", "sibling:-1"), g.legal(relation).map { it.key })
        val card = g.apply(relation, Operation.RelationUp(1))
        assertEquals(listOf("sibling:1", "sibling:-1", "end-relation"), g.legal(card).map { it.key })
        assertEquals(listOf("end-relation"), g.legal(g.apply(card, Operation.Sibling(-1))).map { it.key })
    }

    @Test
    fun contentThatAlsoMatchesATargetCannotFinishWhileDistinctiveContentCan() {
        val g = grammar(GRAMMAR_V2)
        val play = g.targetConditions.indexOf(Condition("desc", "Play"))
        val content = g.walk(Operation.ChooseFamily(Family.SELF_RELATIVE), Operation.AddCondition(play, false, "desc"),
            Operation.EndOuter, Operation.RelationUp(1), Operation.Sibling(-1), Operation.EndRelation)
        assertEquals(Phase.CONTENT, content.phase)
        val title0 = g.positions.getValue(capture.find("title0"))
        val play0 = g.positions.getValue(capture.find("play0"))
        assertEquals(listOf(title0, play0), g.legal(content).map { (it as Operation.ChooseContent).position })
        // D23: "the previous card also has a Play button" says nothing about which card this is.
        val neighbour = g.apply(g.apply(content, Operation.ChooseContent(play0)), Operation.AddMatch(0, "desc"))
        assertFalse(Operation.Finish in g.legal(neighbour))
        val titled = g.apply(content, Operation.ChooseContent(title0))
        val prefix = g.contentConditions(capture.find("title0")).indexOf(Condition("descStartsWith", "Fruit\n"))
        val done = g.apply(g.apply(titled, Operation.AddMatch(prefix, "descStartsWith")), Operation.Finish)
        val plan = g.complete(done)
        assertEquals(listOf(PlanStep.Up(1), PlanStep.Sibling(-1), PlanStep.Match(SelectorSpec(Condition("descStartsWith", "Fruit\n")))), plan.steps)
        val verified = PlanVerifier(capture).verify(CodeGenRequest(listOf(capture.find("play1"))), plan)
        assertTrue("$verified", verified is Verified)
        assertEquals(listOf(capture.find("play1")), g.relativeKept(done))
    }

    @Test
    fun grammarTwoHeuristicBuildsAPrefixRelativePlanThatSurvivesScrolling() {
        val target = capture.find("play1")
        val request = CodeGenRequest(listOf(target))
        val result = ConstructionSearch(capture, budget = budget, policy = ConstructionHeuristicPolicyV2, nanoTime = { 0L },
            grammarVersion = GRAMMAR_V2).search(request)
        val relative = result.candidates.filter { it.plan.family == Family.SELF_RELATIVE }
        assertTrue(result.candidates.toString(), relative.isNotEmpty())
        val verifier = PlanVerifier(capture)
        assertTrue(relative.none { target in verifier.match(requireNotNull(it.plan.relativeMatch)) })
        // Later the shelf scrolls, a similarly named game appears in front and the play time changes.
        val scrolled = shelf("Mines Pro\nLast played 2 weeks ago" to true, "Mines\nLast played 14 weeks ago" to true, sudoku to false)
        val later = CodeGenRequest(listOf(scrolled.find("play1")))
        val survivor = PlanStep.Match(SelectorSpec(Condition("descStartsWith", "Mines\n")))
        assertTrue(relative.map { it.plan }.toString(), relative.any { it.plan.steps.last() == survivor && PlanVerifier(scrolled).verify(later, it.plan) is Verified })
        val grammarOne = ConstructionSearch(capture, budget = budget, nanoTime = { 0L }).search(request)
        assertTrue(grammarOne.candidates.none { it.plan.family == Family.SELF_RELATIVE || it.plan.planKey.contains("StartsWith") })
    }

    @Test
    fun schemaTwoAppendsColumnsAndKeepsTheGrammarOneColumns() {
        assertEquals(ConstructionFeatures.names, ConstructionFeaturesV2.names.take(ConstructionFeatures.count))
        assertEquals(ConstructionFeaturesV2.names.size, ConstructionFeaturesV2.names.toSet().size)
        assertNotEquals(ConstructionFeatures.namesHash, ConstructionFeaturesV2.namesHash)
        val g = grammar(GRAMMAR_V2)
        val state = g.initialState()
        val operation = Operation.ChooseFamily(Family.SELF_RELATIVE)
        val next = g.apply(state, operation)
        val features = ConstructionFeaturesV2.extract(g, state, operation, next)
        assertArrayEquals(ConstructionFeatures.extract(g, state, operation, next), features.copyOf(ConstructionFeatures.count), 0f)
        assertEquals(1f, ConstructionFeaturesV2.value(features, "family_self_relative"))
        assertEquals(1f, ConstructionFeaturesV2.value(features, "relative_entry_target_present"))
    }
}
