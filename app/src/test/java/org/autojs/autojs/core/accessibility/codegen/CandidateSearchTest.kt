package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.search.SearchBudget
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CandidateSearchTest {
    private fun fixture(name: String) = SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/" + name + ".layout.json")).toURI()))

    @Test
    fun singleTargetSearchProducesAllFiveApplicableFamilies() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!), action = Action.CLICK)
        val result = CandidateSearch(root).search(request)
        assertEquals(setOf(Family.SELF, Family.SELF_INDEX, Family.ANCHOR_PATH, Family.ANCHOR_SEARCH, Family.SELF_RELATIVE), result.candidates.map { it.plan.family }.toSet())
        assertTrue(result.attemptedCount <= 2_000)
        assertTrue(result.candidates.size <= 200)
        assertTrue(result.candidates.all { it.resolved == request.targets })
    }

    @Test
    fun collectionSearchRetainsOnlyExactSetsIncludingContainerFamily() {
        val root = fixture("list-duplicates")
        val targets = (0 until 20).map { root.child(it)!!.child(1)!! }
        val request = CodeGenRequest(targets, Scope.COLLECTION, action = Action.CLICK)
        val result = CandidateSearch(root).search(request)
        assertEquals(setOf(Family.SELF, Family.CONTAINER_ITEMS, Family.SELF_RELATIVE), result.candidates.map { it.plan.family }.toSet())
        assertTrue(result.candidates.all { it.resolved.toSet() == targets.toSet() })
    }

    @Test
    fun repeatedMiddleRowUsesAnIndexOrARelationshipWithoutChangingTheTarget() {
        val root = fixture("list-duplicates")
        val target = root.child(3)!!.child(1)!!
        val result = CandidateSearch(root).search(CodeGenRequest(listOf(target), action = Action.CLICK))
        assertTrue(result.candidates.isNotEmpty())
        assertTrue(result.candidates.any { it.plan.family == Family.SELF_INDEX && (it.plan.steps.single() as PlanStep.Index).index == 3 })
        assertTrue(result.candidates.all { it.resolved.single() === target })
    }

    @Test
    fun nearTheEndAlsoOffersANegativeIndex() {
        val root = fixture("list-duplicates")
        val result = CandidateSearch(root).search(CodeGenRequest(listOf(root.child(19)!!.child(1)!!)))
        assertTrue(result.candidates.any { it.plan.family == Family.SELF_INDEX && (it.plan.steps.single() as PlanStep.Index).index < 0 })
    }

    @Test
    fun crossPackageIdCollisionNeverUsesTheBareEntry() {
        val root = fixture("cross-package-id")
        val result = CandidateSearch(root).search(CodeGenRequest(listOf(root.child(1)!!)))
        val idConditions = result.candidates.flatMap { candidate ->
            candidate.plan.selector.conditions + candidate.plan.steps.filterIsInstance<PlanStep.Search>().flatMap { it.selector.conditions }
        }.filter { it.attr == "id" }
        assertTrue(idConditions.isNotEmpty())
        assertTrue(idConditions.all { ":id/" in it.value as String })
    }

    @Test
    fun disabledAttributesAreRespectedOnAnchorsAndNestedSelectors() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!), attributes = emptySet())
        val result = CandidateSearch(root).search(request)
        assertTrue(result.candidates.isNotEmpty())
        for (candidate in result.candidates) {
            val conditions = candidate.plan.selector.conditions + candidate.plan.steps.filterIsInstance<PlanStep.Search>().flatMap { it.selector.conditions }
            assertTrue(conditions.none { it.attr in setOf("id", "text", "desc") })
        }
    }

    @Test
    fun validCombinationIsSimplifiedAndDuplicatePlansAreRemoved() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!))
        val result = CandidateSearch(root).search(request)
        val verifier = PlanVerifier(root)
        assertEquals(result.candidates.size, result.candidates.map { it.plan.planKey }.toSet().size)
        // A single-condition direct selector must survive; broadening to selector() picks the root.
        val direct = result.candidates.first { it.plan.family == Family.SELF && it.plan.selector.conditions.size == 1 }
        assertFalse(verifier.verify(request, LocatePlan(Family.SELF, SelectorSpec())) is Verified)
        assertEquals(request.targets, direct.resolved)
    }

    @Test
    fun budgetTruncationIsDeterministicAndIncludesSimplificationAttempts() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!))
        val first = CandidateSearch(root, budget = SearchBudget(12, 3)).search(request)
        val second = CandidateSearch(root, budget = SearchBudget(12, 3)).search(request)
        assertTrue(first.budgetExhausted)
        assertTrue(first.attemptedCount <= 12)
        assertTrue(first.candidates.size <= 3)
        assertEquals(first.attemptedCount, second.attemptedCount)
        assertEquals(first.candidates.map { it.plan.planKey }, second.candidates.map { it.plan.planKey })
        val zero = CandidateSearch(root, budget = SearchBudget(0)).search(request)
        assertTrue(zero.budgetExhausted)
        assertEquals(0, zero.attemptedCount)
        assertTrue(zero.candidates.isEmpty())
    }

    @Test
    fun cancellationInterruptsBeforeSearching() {
        val root = fixture("login")
        val signal = CancellationSignal()
        val search = CandidateSearch(root, signal)
        signal.cancel()
        assertThrows(CodeGenCancelledException::class.java) { search.search(CodeGenRequest(listOf(root))) }
    }

    @Test
    fun automaticActionUsesTheNearestSupportingAncestor() {
        val root = FakeUiNode.root {
            child("button", clickable = true) {
                className = "android.widget.Button"
                child("label") { text = "Tap"; className = "android.widget.TextView" }
            }
        }
        val request = CodeGenRequest(listOf(root.find("label")), action = Action.CLICK)
        val result = CandidateSearch(root).search(request)
        assertTrue(result.candidates.isNotEmpty())
        assertTrue(result.candidates.all { it.plan.receiver == PlanReceiver.Up(1) })
        assertFalse(root.find("label").isRecycled)
    }

    @Test
    fun failedActionResolutionProvidesRejectionEvidence() {
        val root = FakeUiNode.root { child("label") { text = "No action" } }
        val result = CandidateSearch(root).search(CodeGenRequest(listOf(root.find("label")), action = Action.CLICK))
        assertTrue(result.candidates.isEmpty())
        assertNotNull(result.closestRejection)
        assertEquals(RejectionReason.UNSUPPORTED_ACTION, result.closestRejection!!.reason)
    }

    @Test
    fun repeatedRowsWithIdenticalTargetsAreDistinguishedByANearbyControl() {
        val root = FakeUiNode.root {
            id = "app:id/list"
            scrollable = true
            repeat(3) { index ->
                child("row$index", clickable = true) {
                    child("icon$index") { id = "app:id/icon"; className = "android.widget.ImageView" }
                    child("title$index") { id = "app:id/title"; text = "Title $index"; className = "android.widget.TextView" }
                }
            }
        }
        val target = root.find("icon1")
        val result = CandidateSearch(root).search(CodeGenRequest(listOf(target), action = Action.CLICK))
        val relative = result.candidates.filter { it.plan.family == Family.SELF_RELATIVE }
        assertTrue(relative.isNotEmpty())
        assertTrue(relative.all { it.resolved.single() === target && it.plan.receiver == PlanReceiver.Up(1) })
        // The row title is the nearest content, reachable one level up or one sibling forward.
        val steps = relative.map { it.plan.steps }
        val title = PlanStep.Match(SelectorSpec(Condition("text", "Title 1")))
        assertTrue(steps.toString(), listOf(PlanStep.Up(1), title) in steps)
        assertTrue(steps.toString(), listOf(PlanStep.Sibling(1), title) in steps)
        // Simplification never collapses the relative match to selector(), and the entry selector stays specific.
        assertTrue(relative.all { (it.plan.steps.last() as PlanStep.Match).selector.conditions.isNotEmpty() })
        assertTrue(relative.any { it.plan.selector.conditions == listOf(Condition("id", "icon")) })
        assertEquals(result.candidates.size, result.candidates.map { it.plan.planKey }.toSet().size)
    }

    @Test
    fun relativeContentThatAlsoMatchesTheTargetIsNeverUsedAndTheTitleSurvivesScrolling() {
        // A horizontal shelf whose last card is only partly on screen, so its Play button is not captured.
        fun shelf(vararg games: Pair<String, Boolean>) = FakeUiNode.root {
            games.forEachIndexed { index, (title, playable) ->
                child("card$index") {
                    child("title$index") { desc = title }
                    if (playable) child("play$index") { desc = "Play" }
                }
            }
        }
        val fruit = "Fruit\nLast played 1 week ago"
        val mines = "Mines\nLast played 13 weeks ago"
        val sudoku = "Sudoku\nLast played 14 weeks ago"
        val capture = shelf(fruit to true, mines to true, sudoku to false)
        val target = capture.find("play1")
        val request = CodeGenRequest(listOf(target))
        val result = CandidateSearch(capture).search(request)
        val verifier = PlanVerifier(capture)
        val relative = result.candidates.filter { it.plan.family == Family.SELF_RELATIVE }
        assertTrue(relative.isNotEmpty())
        // A nested "Play" would make the upward relation always true and the sibling one merely positional.
        assertTrue(relative.toString(), relative.none { target in verifier.match(requireNotNull(it.plan.relativeMatch)) })
        val best = RuleRanker().rank(request, result.candidates).first().plan
        assertEquals(PlanStep.Match(SelectorSpec(Condition("desc", mines))), best.steps.last())
        // After scrolling one card, the same game is first and the next card's Play button becomes visible.
        val scrolled = shelf(mines to true, sudoku to true, fruit to false)
        val later = CodeGenRequest(listOf(scrolled.find("play0")))
        assertTrue("$best", PlanVerifier(scrolled).verify(later, best) is Verified)
    }

    @Test
    fun collectionWithDifferentNearestReceiversReportsTheUnrepresentableStrategy() {
        val root = FakeUiNode.root {
            child("one", clickable = true) { child("first") { text = "target" } }
            child("two", clickable = true) { child("wrapper") { child("second") { text = "target" } } }
        }
        val request = CodeGenRequest(listOf(root.find("first"), root.find("second")), Scope.COLLECTION, action = Action.CLICK)
        val result = CandidateSearch(root).search(request)
        assertTrue(result.candidates.isEmpty())
        assertEquals(RejectionReason.RECEIVER_STRATEGY_MISMATCH, result.closestRejection!!.reason)
    }
}
