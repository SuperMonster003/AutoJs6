package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class RuleRankerTest {
    private val ranker = RuleRanker()
    private fun spec(attr: String, value: Any) = SelectorSpec(Condition(attr, value))
    private fun self(attr: String, value: Any) = LocatePlan(LocatePlan.Family.SELF, spec(attr, value))
    private fun <N : UiNode<N>> verified(verifier: PlanVerifier<N>, request: CodeGenRequest<N>, plan: LocatePlan): Verified<N> {
        val result = verifier.verify(request, plan)
        assertTrue("Expected verified: $result", result is Verified)
        return result as Verified<N>
    }

    @Test
    fun loginFixturePrefersDescriptionForInputAndTextForButton() {
        val root = SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/login.layout.json")).toURI()))
        val verifier = PlanVerifier(root)
        val input = CodeGenRequest(listOf(root.child(1)!!))
        val byDescription = self("desc", "密码")
        val byIndex = LocatePlan(LocatePlan.Family.SELF_INDEX, spec("className", "android.widget.EditText"), listOf(PlanStep.Index(1)))
        assertEquals(byDescription, ranker.rank(input, listOf(byIndex, byDescription).map { verified(verifier, input, it) }).first().plan)
        val button = CodeGenRequest(listOf(root.child(2)!!))
        val byText = self("text", "登录")
        assertEquals(byText, ranker.rank(button, listOf(self("className", "android.widget.Button"), byText).map { verified(verifier, button, it) }).first().plan)
    }

    @Test
    fun stableExactConditionsFollowDocumentedCostOrder() {
        val root = FakeUiNode.root {
            child("target", clickable = true) {
                id = "app:id/target"; desc = "Target"; text = "Target"; className = "android.widget.Button"
            }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val verifier = PlanVerifier(root)
        val plans = listOf(self("id", "app:id/target"), self("id", "target"), self("desc", "Target"),
            self("text", "Target"), self("className", "Button"), self("clickable", true))
        val input = plans.reversed().map { verified(verifier, request, it) }
        assertEquals(plans, ranker.rank(request, input).map { it.plan })
    }

    @Test
    fun equalComplexityFamiliesFollowBaselinePreference() {
        val root = FakeUiNode.root {
            id = "app:id/root"
            child("target") {
                id = "app:id/target"
                child("label") { id = "app:id/label" }
            }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val verifier = PlanVerifier(root)
        val direct = self("id", "app:id/target")
        val scoped = LocatePlan(LocatePlan.Family.ANCHOR_SEARCH, spec("id", "app:id/root"), listOf(PlanStep.Search(spec("id", "app:id/target"))))
        val relative = LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/target"), listOf(PlanStep.Child(0), PlanStep.Match(spec("id", "app:id/label"))))
        val path = LocatePlan(LocatePlan.Family.ANCHOR_PATH, spec("id", "app:id/label"), listOf(PlanStep.Up(1)))
        val index = LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id", "app:id/target"), listOf(PlanStep.Index(0)))
        val plans = listOf(direct, scoped, relative, path, index)
        val ranked = ranker.rank(request, plans.reversed().map { verified(verifier, request, it) })
        assertEquals(plans, ranked.map { it.plan })
        val collectionRequest = request.copy(scope = CodeGenRequest.Scope.COLLECTION)
        val collection = LocatePlan(LocatePlan.Family.CONTAINER_ITEMS, spec("id", "app:id/root"),
            listOf(PlanStep.Search(spec("id", "app:id/target"))), LocatePlan.Cardinality.ALL)
        val collectionScore = ranker.rank(collectionRequest, listOf(verified(verifier, collectionRequest, collection))).single().score
        assertTrue(ranked[3].score > collectionScore)
        assertTrue(collectionScore > ranked[4].score)
    }

    @Test
    fun relativePlansAreJudgedByKeptMatchesAndSiblingStepsCostSlightlyMore() {
        val root = FakeUiNode.root {
            repeat(3) { index ->
                child("row$index") {
                    child("icon$index") { id = "app:id/icon" }
                    child("title$index") { id = "app:id/title"; text = "Title $index" }
                }
            }
        }
        val request = CodeGenRequest(listOf(root.find("icon1")))
        val verifier = PlanVerifier(root)
        val byRow = LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/icon"), listOf(PlanStep.Up(1), PlanStep.Match(spec("text", "Title 1"))))
        val bySibling = LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/icon"), listOf(PlanStep.Sibling(1), PlanStep.Match(spec("text", "Title 1"))))
        val index = LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id", "app:id/icon"), listOf(PlanStep.Index(1)))
        val ranked = ranker.rank(request, listOf(index, bySibling, byRow).map { verified(verifier, request, it) })
        assertEquals(listOf(byRow, bySibling, index), ranked.map { it.plan })
        // The broad entry selector matches three icons, but only one survives the relation, so no first-of-many penalty applies.
        val unique = verified(verifier, request, byRow)
        assertEquals(3, unique.facts.selectorMatchCount)
        assertEquals(1, unique.facts.relativeMatchCount)
        val first = CodeGenRequest(listOf(root.find("icon0")))
        val broad = verified(verifier, first, LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id", "app:id/icon"), listOf(PlanStep.Up(1), PlanStep.Match(spec("id", "app:id/title")))))
        assertEquals(3, broad.facts.relativeMatchCount)
        // unique: family 60 + full id 1 + text 6 + dynamic text 32 + one up level 2; broad: 60 + 1 + 1 + 2 + first-of-three 66.
        assertEquals(101L, ranker.cost(unique))
        assertEquals(130L, ranker.cost(broad))
        assertEquals(102L, ranker.cost(verified(verifier, request, bySibling)))
    }

    @Test
    fun relativeContentRepeatedInTheCaptureCostsLikeFirstOfMany() {
        // "The Play button whose previous card also has a Play button" keeps only the target, yet only says "the second card".
        val root = FakeUiNode.root {
            child("card0") {
                child("title0") { desc = "Fruit\nLast played 1 week ago" }
                child("play0") { desc = "Play" }
            }
            child("card1") {
                child("title1") { desc = "Mines\nLast played 13 weeks ago" }
                child("play1") { desc = "Play" }
            }
        }
        val request = CodeGenRequest(listOf(root.find("play1")))
        val verifier = PlanVerifier(root)
        val byNeighbour = verified(verifier, request, LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("desc", "Play"),
            listOf(PlanStep.Up(1), PlanStep.Sibling(-1), PlanStep.Match(spec("desc", "Play")))))
        val byTitle = verified(verifier, request, LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("desc", "Play"),
            listOf(PlanStep.Up(1), PlanStep.Match(spec("desc", "Mines\nLast played 13 weeks ago")))))
        assertEquals(1, byNeighbour.facts.relativeMatchCount)
        assertEquals(2, byNeighbour.facts.relativeContentMatchCount)
        assertEquals(1, byTitle.facts.relativeContentMatchCount)
        // byNeighbour: family 60 + two descriptions 8 + one up level and one sibling 5 + one of two contents 65;
        // byTitle: 60 + 8 + dynamic description 32 + one up level 2.
        assertEquals(138L, ranker.cost(byNeighbour))
        assertEquals(102L, ranker.cost(byTitle))
        assertEquals(listOf(byTitle, byNeighbour), ranker.rank(request, listOf(byNeighbour, byTitle)).map { it.verified })
    }

    @Test
    fun firstLinePrefixesCostSlightlyMoreThanExactValuesButLessThanChangingOnes() {
        val root = FakeUiNode.root {
            child("stable") { desc = "Settings\nNetwork and internet"; text = "Settings\nNetwork and internet" }
            child("changing") { desc = "Google\n29 MB"; text = "Google\n29 MB" }
        }
        val verifier = PlanVerifier(root)
        val stable = CodeGenRequest(listOf(root.find("stable")))
        val stablePlans = listOf(self("desc", "Settings\nNetwork and internet"), self("descStartsWith", "Settings\n"),
            self("text", "Settings\nNetwork and internet"), self("textStartsWith", "Settings\n"))
        assertEquals(stablePlans, ranker.rank(stable, stablePlans.reversed().map { verified(verifier, stable, it) }).map { it.plan })
        val changing = CodeGenRequest(listOf(root.find("changing")))
        val changingPlans = listOf(self("descStartsWith", "Google\n"), self("textStartsWith", "Google\n"), self("desc", "Google\n29 MB"), self("text", "Google\n29 MB"))
        assertEquals(changingPlans, ranker.rank(changing, changingPlans.reversed().map { verified(verifier, changing, it) }).map { it.plan })
        assertTrue(changingPlans.flatMap { it.selector.conditions }.none(RuleRanker::isStructural))
    }

    @Test
    fun changingTextAndDescriptionLoseToStableAlternatives() {
        val root = FakeUiNode.root {
            child("target") { text = "Pay 19"; desc = "Pay 19"; className = "android.widget.Button" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val verifier = PlanVerifier(root)
        val plans = listOf(self("text", "Pay 19"), self("desc", "Pay 19"), self("className", "Button"))
        val ranked = ranker.rank(request, plans.map { verified(verifier, request, it) })
        assertEquals("className", ranked.first().plan.selector.conditions.single().attr)
    }

    @Test
    fun firstOfManyIsPenalizedAlthoughItPassesVerification() {
        val root = FakeUiNode.root {
            child("target") { className = "android.widget.Button"; text = "Pay 19" }
            child("other") { className = "android.widget.Button" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val verifier = PlanVerifier(root)
        val broad = verified(verifier, request, self("className", "Button"))
        val precise = verified(verifier, request, LocatePlan(LocatePlan.Family.SELF,
            SelectorSpec(Condition("className", "Button"), Condition("indexInParent", 0))))
        assertEquals(2, broad.facts.selectorMatchCount)
        assertSame(precise, ranker.rank(request, listOf(broad, precise)).first().verified)
        val exactText = verified(verifier, request, self("text", "Pay 19"))
        assertSame(exactText, ranker.rank(request, listOf(broad, exactText)).first().verified)
    }

    @Test
    fun shorterPathsAndSmallerEdgeRelativeIndicesWin() {
        val root = FakeUiNode.root {
            id = "app:id/root"
            child("one") {
                id = "app:id/one"
                child("two") {
                    id = "app:id/two"
                    child("target") { id = "app:id/target" }
                }
            }
        }
        val verifier = PlanVerifier(root)
        val request = CodeGenRequest(listOf(root.find("target")))
        val long = LocatePlan(LocatePlan.Family.ANCHOR_PATH, spec("id", "app:id/root"), List(3) { PlanStep.Child(0) })
        val short = LocatePlan(LocatePlan.Family.ANCHOR_PATH, spec("id", "app:id/two"), listOf(PlanStep.Child(0)))
        assertEquals(short, ranker.rank(request, listOf(long, short).map { verified(verifier, request, it) }).first().plan)

        val list = FakeUiNode.root { repeat(20) { child("item$it") { className = "android.widget.Button" } } }
        val listVerifier = PlanVerifier(list)
        val listRequest = CodeGenRequest(listOf(list.find("item19")))
        val end = LocatePlan(LocatePlan.Family.SELF_INDEX, spec("className", "Button"), listOf(PlanStep.Index(-1)))
        val start = LocatePlan(LocatePlan.Family.SELF_INDEX, spec("className", "Button"), listOf(PlanStep.Index(19)))
        assertEquals(end, ranker.rank(listRequest, listOf(start, end).map { verified(listVerifier, listRequest, it) }).first().plan)
    }

    @Test
    fun tiedScoresHaveStablePlanKeyOrderAndPreserveAllCandidates() {
        val root = FakeUiNode.root {
            child("target") { id = "app:id/target"; text = "Target"; desc = "Target" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val verifier = PlanVerifier(root)
        val plans = listOf(self("text", "Target"), LocatePlan(LocatePlan.Family.SELF,
            SelectorSpec(Condition("desc", "Target"), Condition("id", "target"))))
        val candidates = plans.map { verified(verifier, request, it) }
        val expected = plans.sortedBy { it.planKey }
        repeat(20) { seed ->
            val ranked = ranker.rank(request, candidates.shuffled(Random(seed)))
            assertEquals(1, ranked.map { it.score }.distinct().size)
            assertEquals(expected, ranked.map { it.plan })
            assertEquals(candidates.toSet(), ranked.map { it.verified }.toSet())
            assertThrows(UnsupportedOperationException::class.java) { (ranked as MutableList<*>).clear() }
        }
        assertTrue(ranker.rank(request, emptyList()).isEmpty())
    }
}
