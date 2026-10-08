package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.automator.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The index-backed matcher must reproduce the verifier's selector semantics node for node. */
class ConstructionGrammarMatchingTest {
    private fun fixture(name: String) = SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/$name.layout.json")).toURI()))

    @Test
    fun indexedMatchesEqualVerifierMatchesForEveryConditionSubsetAndSubtree() {
        var compared = 0
        for ((root, targets) in sampledTrees()) {
            compared += compareAll(root, targets)
        }
        assertTrue("Expected many compared selectors, got $compared", compared > 5_000)
    }

    @Test
    fun indexedMatchingScansEachSingleConditionOnceAndKeepsSearchResultsUnchanged() {
        val group = ConstructionBottleneckFixtures.groups().first { it.groupId.endsWith("nearby-anchor-seed3-collection") }
        val capture = group.states[group.captureState]
        val request = group.request.forState(capture)
        val budget = ConstructionBudget(maxMillis = 30_000)
        val result = ConstructionSearch(capture.root, budget = budget, nanoTime = { 0L },
            frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
        val grammar = ConstructionGrammar(request, PlanVerifier(capture.root), budget, CancellationSignal()) {}
        // Every scored action of the frozen search is replayed through a fresh grammar; its feature
        // vectors must be identical, so the trained policies keep their meaning.
        val expansions = result.expansions
        var replayed = 0
        for (expansion in expansions) {
            val state = stateFromKey(grammar, expansion.stateKey) ?: continue
            for (decision in expansion.scoredActions) {
                val operation = grammar.legal(state).firstOrNull { it.key == decision.actionKey } ?: continue
                val features = ConstructionFeatures.extract(grammar, state, operation, grammar.apply(state, operation))
                assertTrue("Feature vector changed for ${expansion.stateKey} ${decision.actionKey}", features.contentEquals(decision.features))
                replayed++
            }
        }
        assertTrue(replayed > 100)
        assertTrue(grammar.singleConditionScans in 1 until expansions.sumOf { it.scoredActions.size })
    }

    private fun sampledTrees(): List<Pair<SnapshotNode, List<SnapshotNode>>> = buildList {
        for (name in listOf("login", "list-duplicates", "cross-package-id")) {
            val root = fixture(name)
            add(root to listOf(preOrder(root).last { it.childCount() == 0 }))
        }
        val groups = ConstructionBottleneckFixtures.groups().filter { it.groupId.contains("seed1") } +
            ConstructionGeneralizationFixtures.groups().filter { it.groupId.contains("seed2-collection") } +
            ConstructionThirdSyntheticFixtures.groups().filter { it.groupId.contains("seed3-single") }
        for (group in groups) {
            val capture = group.states[group.captureState]
            add(capture.root to capture.targets)
        }
    }

    private fun <N : UiNode<N>> compareAll(root: N, targets: List<N>): Int {
        val verifier = PlanVerifier(root)
        val request = CodeGenRequest(targets, if (targets.size > 1) CodeGenRequest.Scope.COLLECTION else CodeGenRequest.Scope.SINGLE)
        val grammar = ConstructionGrammar(request, verifier, ConstructionBudget(maxMillis = 30_000), CancellationSignal()) {}
        val nodes = verifier.tree.nodes
        val conditions = LinkedHashSet<Condition>()
        for (node in nodes) {
            val state = ConstructionGrammar.State(anchor = grammar.positions.getValue(node))
            conditions += grammar.outerSource(state)
        }
        conditions += grammar.targetConditions
        val list = conditions.toList()
        val anchors = listOf(root) + targets.flatMap { target -> generateSequence(target) { verifier.tree.parent(it) }.take(4).toList() }.distinct()
        var compared = 0
        fun check(spec: SelectorSpec, within: N) {
            val expected = verifier.match(spec, within)
            val actual = grammar.matches(spec, within)
            assertEquals("Spec $spec within ${verifier.tree.nodes.indexOf(within)}", expected.map { nodes.indexOf(it) }, actual.map { nodes.indexOf(it) })
            compared++
        }
        for (within in anchors) {
            for (i in list.indices) {
                check(SelectorSpec(listOf(list[i])), within)
                for (j in i + 1 until minOf(list.size, i + 12)) {
                    check(SelectorSpec(listOf(list[i], list[j])), within)
                    if (j + 1 < list.size) check(SelectorSpec(listOf(list[i], list[j], list[j + 1])), within)
                }
            }
        }
        // Repeated queries and reversed condition order hit the cache and stay identical.
        for (within in anchors) check(SelectorSpec(list.take(2).reversed()), within)
        return compared
    }

    private fun stateFromKey(grammar: ConstructionGrammar<SnapshotNode>, key: String): ConstructionGrammar.State? {
        val parts = key.split('|')
        if (parts.size != 9 || parts[0] != "g1") return null
        fun ints(text: String) = if (text.isEmpty()) emptyList() else text.split(',').map(String::toInt)
        val path = if (parts[6].isEmpty()) emptyList() else parts[6].split(',').map { step ->
            if (step.startsWith("u")) org.autojs.autojs.core.accessibility.codegen.plan.PlanStep.Up(step.drop(1).toInt())
            else org.autojs.autojs.core.accessibility.codegen.plan.PlanStep.Child(step.drop(1).toInt())
        }
        val phase = ConstructionGrammar.Phase.valueOf(parts[1])
        val family = parts[2].takeIf { it.isNotEmpty() }?.let { org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family.valueOf(it) }
        val outer = ints(parts[4])
        val inner = ints(parts[5])
        val index = parts[8].takeIf { it.isNotEmpty() }?.toInt()
        // The key omits the prefix depth, which counts every operation applied so far.
        val anchorStep = if (parts[3].toInt() >= 0) 1 else 0
        val endOuter = if (phase in setOf(ConstructionGrammar.Phase.INDEX, ConstructionGrammar.Phase.PATH, ConstructionGrammar.Phase.INNER, ConstructionGrammar.Phase.READY)) 1 else 0
        val depth = (if (family != null) 1 else 0) + anchorStep + outer.size + endOuter + (if (index != null) 1 else 0) + path.size + inner.size
        val state = ConstructionGrammar.State(
            phase = phase, family = family, anchor = parts[3].toInt(), outer = outer, inner = inner, path = path,
            cursor = parts[7].toInt(), index = index, depth = depth,
        )
        return state.takeIf { it.key == key }.also { require(it != null) { "State key does not round-trip: $key" } }
    }

    private fun preOrder(root: SnapshotNode): List<SnapshotNode> = buildList {
        val pending = ArrayDeque<SnapshotNode>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            add(node)
            for (index in node.childCount() - 1 downTo 0) pending.addLast(requireNotNull(node.child(index)))
        }
    }
}
