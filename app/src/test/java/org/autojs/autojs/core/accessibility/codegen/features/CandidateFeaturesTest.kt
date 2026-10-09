package org.autojs.autojs.core.accessibility.codegen.features

import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.ln1p
import kotlin.random.Random

class CandidateFeaturesTest {
    private fun spec(vararg values: Pair<String, Any>) = SelectorSpec(values.map { Condition(it.first, it.second) })
    private fun self(vararg values: Pair<String, Any>, all: Boolean = false) = LocatePlan(
        LocatePlan.Family.SELF, spec(*values), cardinality = if (all) LocatePlan.Cardinality.ALL else LocatePlan.Cardinality.ONE,
    )
    private fun fixture(name: String) = SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/$name.layout.json")).toURI()))
    private fun <N : UiNode<N>> verified(verifier: PlanVerifier<N>, request: CodeGenRequest<N>, plan: LocatePlan): Verified<N> {
        val result = verifier.verify(request, plan)
        assertTrue("Expected verified $plan: $result", result is Verified)
        return result as Verified<N>
    }
    private fun FloatArray.feature(name: String): Float = this[FeatureSchema.names.indexOf(name).also { require(it >= 0) }]
    private fun logged(value: Double) = ln1p(value).toFloat()

    private data class Example(val request: CodeGenRequest<SnapshotNode>, val verifier: PlanVerifier<SnapshotNode>, val candidates: List<Verified<SnapshotNode>>)

    private fun example(name: String): Example {
        val root = fixture(name)
        val verifier = PlanVerifier(root)
        val request: CodeGenRequest<SnapshotNode>
        val plans: List<LocatePlan>
        when (name) {
            "login" -> {
                request = CodeGenRequest(listOf(root.child(1)!!), action = CodeGenRequest.Action.SET_TEXT("secret"))
                plans = listOf(
                    self("desc" to "密码"),
                    LocatePlan(LocatePlan.Family.SELF_INDEX, spec("className" to "EditText"), listOf(PlanStep.Index(-1))),
                    LocatePlan(LocatePlan.Family.ANCHOR_PATH, spec("className" to "LinearLayout"), listOf(PlanStep.Child(1))),
                )
            }
            "list-duplicates" -> {
                request = CodeGenRequest((0 until root.childCount()).map { root.child(it)!!.child(1)!! }, CodeGenRequest.Scope.COLLECTION, action = CodeGenRequest.Action.CLICK)
                plans = listOf(
                    self("id" to "org.autojs.test.codegen.fixture:id/delete", all = true),
                    self("text" to "删除", all = true),
                    LocatePlan(LocatePlan.Family.CONTAINER_ITEMS, spec("id" to "list"), listOf(PlanStep.Search(spec("id" to "delete"))), LocatePlan.Cardinality.ALL),
                )
            }
            "cross-package-id" -> {
                request = CodeGenRequest(listOf(root.child(1)!!))
                plans = listOf(
                    self("id" to "org.autojs.test.codegen.fixture:id/title"),
                    LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id" to "title"), listOf(PlanStep.Index(-1))),
                    self("text" to "标题", "packageName" to "org.autojs.test.codegen.fixture"),
                )
            }
            else -> error(name)
        }
        return Example(request, verifier, plans.map { verified(verifier, request, it) })
    }

    @Test
    fun versionTwoHasPinnedNamesAndHashAndVersionOneStaysProjectable() {
        assertEquals(2, FeatureSchema.featureSchemaVersion)
        assertEquals(1..2, FeatureSchema.SUPPORTED_SCHEMA)
        assertEquals(75, FeatureSchema.names.size)
        assertEquals(FeatureSchema.names.size, FeatureSchema.names.distinct().size)
        assertEquals("a2b899bcc46125ba", FeatureSchema.namesHash)
        assertEquals(FeatureSchema.names, FeatureSchema.names(2))
        assertEquals(64, FeatureSchema.names(1).size)
        assertEquals("9c6a5d8279ffca0e", FeatureSchema.namesHash(1))
        assertEquals(FeatureSchema.names(1), FeatureSchema.projection(1).map { FeatureSchema.names[it] })
        assertEquals(LocatePlan.Family.entries.toSet() - LocatePlan.Family.SELF_RELATIVE, FeatureSchema.families(1))
        assertEquals(LocatePlan.Family.entries.toSet(), FeatureSchema.families(2))
        assertThrows(IllegalArgumentException::class.java) { FeatureSchema.names(3) }
        assertThrows(IllegalArgumentException::class.java) { FeatureSchema.project(FloatArray(64), 1) }
        val snapshots = JsonParser.parseReader(requireNotNull(javaClass.getResourceAsStream("/codegen/features/v2-vectors.json")).reader()).asJsonObject
        assertEquals(FeatureSchema.names, snapshots.getAsJsonArray("names").map { it.asString })
        assertEquals(FeatureSchema.namesHash, snapshots["namesHash"].asString)
    }

    @Test
    fun threeFixtureVectorsMatchIndependentSnapshotsAndProjectOntoTheVersionOneSnapshot() {
        val snapshots = JsonParser.parseReader(requireNotNull(javaClass.getResourceAsStream("/codegen/features/v2-vectors.json")).reader()).asJsonObject
        val legacy = JsonParser.parseReader(requireNotNull(javaClass.getResourceAsStream("/codegen/features/v1-vectors.json")).reader()).asJsonObject
        assertEquals(FeatureSchema.names(1), legacy.getAsJsonArray("names").map { it.asString })
        for (name in FIXTURES) {
            val (request, verifier, candidates) = example(name)
            val vectors = CandidateFeatures.prepare(request, candidates, verifier.tree).extractAll()
            val expected = snapshots.getAsJsonObject("fixtures").getAsJsonArray(name)
            val expectedLegacy = legacy.getAsJsonObject("fixtures").getAsJsonArray(name)
            assertEquals(expected.size(), vectors.size)
            for (i in vectors.indices) {
                assertArrayEquals("$name candidate $i", expected[i].asJsonArray.map { it.asFloat }.toFloatArray(), vectors[i], 1e-6f)
                assertArrayEquals("$name candidate $i (schema 1)", expectedLegacy[i].asJsonArray.map { it.asFloat }.toFloatArray(), FeatureSchema.project(vectors[i], 1), 1e-6f)
            }
        }
    }

    @Test
    fun relativePlansReportTheirMatchSelectorAndKeptEntryCounts() {
        val root = FakeUiNode.root {
            repeat(3) { index ->
                child("row$index", clickable = true) {
                    child("icon$index") { id = "app:id/icon"; className = "android.widget.ImageView" }
                    child("title$index") { id = "app:id/title"; text = "Title $index" }
                }
            }
        }
        val request = CodeGenRequest(listOf(root.find("icon1")), action = CodeGenRequest.Action.CLICK)
        val verifier = PlanVerifier(root)
        val relative = LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id" to "app:id/icon"),
            listOf(PlanStep.Up(1), PlanStep.Match(spec("text" to "Title 1"))), receiver = PlanReceiver.Up(1))
        val sibling = LocatePlan(LocatePlan.Family.SELF_RELATIVE, spec("id" to "app:id/icon"),
            listOf(PlanStep.Sibling(1), PlanStep.Match(spec("id" to "app:id/title", "text" to "Title 1"))), receiver = PlanReceiver.Up(1))
        val index = LocatePlan(LocatePlan.Family.SELF_INDEX, spec("id" to "app:id/icon"), listOf(PlanStep.Index(1)), receiver = PlanReceiver.Up(1))
        val candidates = listOf(relative, sibling, index).map { verified(verifier, request, it) }
        val vectors = CandidateFeatures.prepare(request, candidates, verifier.tree).extractAll()
        val (byRow, bySibling, byIndex) = vectors
        assertEquals(1f, byRow.feature("family_self_relative"))
        assertEquals(0f, byRow.feature("family_self_index"))
        assertEquals(1f, byRow.feature("self_has_full_id"))
        assertEquals(-1f, byRow.feature("anchor_cond_count"))
        assertEquals(logged(1.0), byRow.feature("relative_cond_count"))
        assertEquals(1f, byRow.feature("relative_has_text"))
        assertEquals(0f, byRow.feature("relative_has_full_id"))
        assertEquals(logged(3.0), byRow.feature("selector_match_count"))
        assertEquals(logged(1.0), byRow.feature("relative_match_count"))
        assertEquals(1f / 3f, byRow.feature("relative_match_ratio"), 1e-6f)
        assertEquals(logged(1.0), byRow.feature("path_steps"))
        assertEquals(logged(1.0), byRow.feature("path_up_levels"))
        assertEquals(0f, byRow.feature("path_sibling_steps"))
        assertEquals(-1f, byRow.feature("anchor_distance"))
        assertEquals(logged(7.0), byRow.feature("text_len"))
        assertEquals(1f, byRow.feature("text_dynamic"))
        assertEquals(logged(1.0), byRow.feature("receiver_up_levels"))
        assertEquals(1f, bySibling.feature("relative_has_full_id"))
        assertEquals(logged(2.0), bySibling.feature("relative_cond_count"))
        assertEquals(logged(1.0), bySibling.feature("path_sibling_steps"))
        assertEquals(0f, bySibling.feature("path_up_levels"))
        assertEquals(logged(1.0), bySibling.feature("path_max_child_index"))
        assertEquals(0f, byIndex.feature("family_self_relative"))
        assertEquals(-1f, byIndex.feature("relative_cond_count"))
        assertEquals(-1f, byIndex.feature("relative_match_count"))
        assertEquals(-1f, byIndex.feature("relative_match_ratio"))
        assertTrue(vectors.all { it.all(Float::isFinite) && it.size == FeatureSchema.names.size })
    }

    @Test
    fun groupOrderAndTargetOrderNeverChangeFeatures() {
        val random = Random(719)
        for (name in FIXTURES) {
            val (request, verifier, candidates) = example(name)
            val expected = candidates.associate { it.plan.planKey to CandidateFeatures.extract(request, it, candidates) }
            repeat(10) {
                val reordered = candidates.shuffled(random)
                val extraction = CandidateFeatures.prepare(request.copy(targets = request.targets.shuffled(random)), reordered, verifier.tree)
                reordered.zip(extraction.extractAll()).forEach { (candidate, vector) ->
                    assertArrayEquals(expected.getValue(candidate.plan.planKey), vector, 0f)
                }
            }
        }
    }

    @Test
    fun allSearchedCandidatesAreFiniteForEveryExistingFixture() {
        for (name in FIXTURES) {
            val (request, verifier) = example(name)
            val candidates = CandidateSearch(verifier.root, verifier = verifier).search(request).candidates
            assertTrue(candidates.isNotEmpty())
            CandidateFeatures.prepare(request, candidates, verifier.tree).extractAll().forEach {
                assertEquals(FeatureSchema.names.size, it.size)
                assertTrue(it.all(Float::isFinite))
            }
        }
    }

    @Test
    fun capturedSubtreeDoesNotIncludeItsSourceParentOrOtherWindows() {
        val outside = FakeUiNode.root {
            scrollable = true
            child("target") { id = "app:id/title"; className = "Text" }
            child("outside") { id = "android:id/title"; className = "Text" }
        }
        val target = outside.find("target")
        val verifier = PlanVerifier(target)
        val request = CodeGenRequest(listOf(target))
        val candidate = verified(verifier, request, self("id" to "app:id/title"))
        val vector = CandidateFeatures.prepare(request, listOf(candidate), verifier.tree).extract(candidate)
        assertEquals(0f, vector.feature("id_cross_package_dup"))
        assertEquals(0f, vector.feature("target_depth"))
        assertEquals(0f, vector.feature("target_in_list"))
        assertEquals(0f, vector.feature("target_same_class_siblings"))
    }

    @Test
    fun collectionBooleansUseAllAndDepthUsesTheMeanBeforeLogarithm() {
        val root = FakeUiNode.root {
            id = "app:id/root"
            child("first", clickable = true) { id = "app:id/item"; text = "A"; className = "Button" }
            child("wrapper") {
                child("second") { id = "app:id/item"; className = "Button" }
            }
        }
        val request = CodeGenRequest(listOf(root.find("first"), root.find("second")), CodeGenRequest.Scope.COLLECTION)
        val verifier = PlanVerifier(root)
        val plan = LocatePlan(LocatePlan.Family.CONTAINER_ITEMS, spec("id" to "root"), listOf(PlanStep.Search(spec("id" to "item"))), LocatePlan.Cardinality.ALL)
        val candidate = verified(verifier, request, plan)
        val extraction = CandidateFeatures.prepare(request, listOf(candidate), verifier.tree)
        val vector = extraction.extract(candidate)
        assertEquals(logged(1.5), vector.feature("target_depth"))
        assertEquals(logged(1.5), vector.feature("anchor_distance"))
        assertEquals(1f, vector.feature("target_has_id"))
        assertEquals(0f, vector.feature("target_has_text"))
        assertEquals(0f, vector.feature("target_clickable"))
        assertEquals(1f, vector.feature("container_item_uniform"))
        val reversed = Verified(candidate.plan, candidate.resolved.reversed(), candidate.facts)
        assertArrayEquals(vector, CandidateFeatures.prepare(request.copy(targets = request.targets.reversed()), listOf(reversed), verifier.tree).extract(reversed), 0f)
    }

    @Test
    fun unicodeRatiosCountCodePointsAndDynamicContentDoesNotLeakText() {
        val root = FakeUiNode.root {
            child("target") { id = "app:id/b7"; text = "票𝟙12𠀀x"; desc = "第2项" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val verifier = PlanVerifier(root)
        val candidate = verified(verifier, request, self("id" to "b7", "text" to "票𝟙12𠀀x", "desc" to "第2项"))
        val vector = CandidateFeatures.extract(request, candidate, listOf(candidate))
        assertEquals(logged(6.0), vector.feature("text_len"))
        assertEquals(0.5f, vector.feature("text_digit_ratio"))
        assertEquals(1f / 3, vector.feature("text_cjk_ratio"))
        assertEquals(1f, vector.feature("text_dynamic"))
        assertEquals(logged(3.0), vector.feature("desc_len"))
        assertEquals(1f, vector.feature("desc_dynamic"))
        assertEquals(1f, vector.feature("id_looks_obfuscated"))
    }

    @Test
    fun absentSelectorsDifferFromEmptySelectorsAndAbsentValuesFromEmptyText() {
        val root = FakeUiNode.root { className = "Root" }
        val request = CodeGenRequest(listOf(root))
        val verifier = PlanVerifier(root)
        val empty = verified(verifier, request, self())
        val text = verified(verifier, request, self("text" to ""))
        val extraction = CandidateFeatures.prepare(request, listOf(empty, text), verifier.tree)
        val emptyVector = extraction.extract(empty)
        val textVector = extraction.extract(text)
        assertEquals(0f, emptyVector.feature("self_cond_count"))
        assertEquals(-1f, emptyVector.feature("anchor_cond_count"))
        assertEquals(-1f, emptyVector.feature("text_len"))
        assertEquals(-1f, emptyVector.feature("index_negative"))
        assertEquals(-1f, emptyVector.feature("container_item_uniform"))
        assertEquals(0f, textVector.feature("text_len"))
        assertEquals(0f, textVector.feature("text_digit_ratio"))
        assertEquals(0f, textVector.feature("text_dynamic"))
    }

    @Test
    fun negativeIndexUsesLiteralMagnitudeButRelativeIndexUsesResolvedPosition() {
        val root = FakeUiNode.root { repeat(20) { child("item$it") { className = "android.widget.Button" } } }
        val request = CodeGenRequest(listOf(root.find("item19")))
        val verifier = PlanVerifier(root)
        val candidates = listOf(-1, 19).map {
            verified(verifier, request, LocatePlan(LocatePlan.Family.SELF_INDEX, spec("className" to "Button"), listOf(PlanStep.Index(it))))
        }
        val vectors = CandidateFeatures.prepare(request, candidates, verifier.tree).extractAll()
        assertEquals(logged(1.0), vectors[0].feature("index_abs"))
        assertEquals(logged(19.0), vectors[1].feature("index_abs"))
        assertEquals(1f, vectors[0].feature("index_negative"))
        assertEquals(0f, vectors[1].feature("index_negative"))
        assertEquals(19f / 20, vectors[0].feature("index_rel"))
        assertEquals(vectors[0].feature("index_rel"), vectors[1].feature("index_rel"))
        assertEquals(0f, vectors[0].feature("family_rank_in_group"))
        assertEquals(logged(1.0), vectors[1].feature("family_rank_in_group"))
    }

    @Test
    fun receiverLevelsAreIncludedButQueryModeStyleAndSetTextPayloadAreNot() {
        val root = FakeUiNode.root {
            child("parent", clickable = true) { child("target") { text = "Label" } }
        }
        val request = CodeGenRequest(listOf(root.find("target")), action = CodeGenRequest.Action.CLICK)
        val verifier = PlanVerifier(root)
        val plan = LocatePlan(LocatePlan.Family.SELF, spec("text" to "Label"), receiver = PlanReceiver.Up(1))
        val candidate = verified(verifier, request, plan)
        val vector = CandidateFeatures.extract(request, candidate, listOf(candidate))
        assertEquals(logged(1.0), vector.feature("receiver_up_levels"))
        assertArrayEquals(vector, CandidateFeatures.extract(request.copy(mode = CodeGenRequest.Mode.CHECK, style = CodeGenRequest.Style.FLOW), candidate, listOf(candidate)), 0f)
        val (input, inputVerifier, inputs) = example("login")
        assertArrayEquals(CandidateFeatures.prepare(input, inputs, inputVerifier.tree).extract(inputs[0]),
            CandidateFeatures.prepare(input.copy(action = CodeGenRequest.Action.SET_TEXT("different payload")), inputs, inputVerifier.tree).extract(inputs[0]), 0f)
    }

    @Test
    fun fiveThousandDeepNodesDoNotRecurseOrBuildQuadraticSignatures() {
        var snapshot = NodeInfoSnapshot(text = "target", depth = 4999)
        for (depth in 4998 downTo 0) snapshot = NodeInfoSnapshot(depth = depth, children = listOf(snapshot))
        val root = SnapshotNode(snapshot)
        val verifier = PlanVerifier(root)
        val request = CodeGenRequest(listOf(verifier.nodes.last()))
        val candidate = verified(verifier, request, self("text" to "target"))
        val vector = CandidateFeatures.prepare(request, listOf(candidate), verifier.tree).extract(candidate)
        assertEquals(logged(4999.0), vector.feature("target_depth"))
        assertTrue(vector.all(Float::isFinite))
    }

    @Test
    fun duplicateCandidatesAndForeignCandidatesFailWithoutChangingTheGroup() {
        val (request, verifier, candidates) = example("login")
        assertThrows(IllegalArgumentException::class.java) { CandidateFeatures.prepare(request, candidates + candidates[0], verifier.tree) }
        val group = CandidateFeatures.prepare(request, candidates.take(1), verifier.tree)
        assertThrows(IllegalArgumentException::class.java) { group.extract(candidates[1]) }
        val first = group.extract(candidates[0])
        val fresh = group.extract(candidates[0])
        first.fill(-99f)
        assertArrayEquals(fresh, group.extract(candidates[0]), 0f)
    }

    companion object {
        private val FIXTURES = listOf("login", "list-duplicates", "cross-package-id")
    }
}
