package org.autojs.autojs.core.accessibility.codegen.dataset

import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.features.CandidateFeatures
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CodeGenDatasetExporterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun shuffledTargetMakesTheRepeatedIdIndexFailEveryOtherState() {
        val group = TaskGroup.load(fixture("shuffle"))
        val labeled = DatasetExporter.label(group)
        val index = labeled.labels.first { label ->
            val plan = label.candidate.plan
            plan.family == LocatePlan.Family.SELF_INDEX &&
                (plan.steps.single() as PlanStep.Index).index == 1 &&
                plan.selector.conditions.singleOrNull()?.attr == "id"
        }
        assertEquals(0.0, index.survival, 0.0)
        assertEquals(listOf("s1", "s2"), index.failedStates.map { it.stateId })
        assertTrue(index.failedStates.all { it.mutations == listOf("shuffle") })
        val text = labeled.labels.first { it.candidate.plan.family == LocatePlan.Family.SELF && it.candidate.plan.selector.conditions.singleOrNull()?.attr == "text" }
        assertEquals(1.0, text.survival, 0.0)
        assertTrue(text.failedStates.isEmpty())
    }

    @Test
    fun collectionIdSurvivesRetextButFailsStrippingAndClassSurvivesBoth() {
        val group = TaskGroup.load(fixture("collection"))
        val labeled = DatasetExporter.label(group)
        val direct = labeled.labels.filter { it.candidate.plan.family == LocatePlan.Family.SELF }
        val id = direct.first { it.candidate.plan.selector.conditions.singleOrNull()?.attr == "id" }
        assertEquals(0.5, id.survival, 0.0)
        assertEquals("s2", id.failedStates.single().stateId)
        assertEquals(listOf("strip-ids"), id.failedStates.single().mutations)
        assertEquals(1.0, direct.first { it.candidate.plan.selector.conditions.singleOrNull()?.attr == "className" }.survival, 0.0)
        val rules = RuleRanker().rank(group.request.forState(group.states[0]), labeled.search.candidates)
        assertEquals(rules.first().plan.planKey, labeled.labels.single { it.ruleSelected }.candidate.plan.planKey)
    }

    @Test
    fun captureStateControlsCandidateSearchAndIsExcludedFromTheDenominator() {
        val original = TaskGroup.load(fixture("shuffle"))
        val group = original.copy(captureState = 1)
        val labeled = DatasetExporter.label(group)
        assertTrue(labeled.search.candidates.all { it.resolved.single() === group.states[1].targets.single() })
        assertTrue(labeled.labels.flatMap { it.failedStates }.all { it.stateId != "s1" })
    }

    @Test
    fun changedFutureAnnotationsCannotInfluenceCandidatesOrRuleSelection() {
        val group = TaskGroup.load(fixture("shuffle"))
        val original = DatasetExporter.label(group)
        val changed = DatasetExporter.label(group.copy(states = group.states.mapIndexed { index, state ->
            if (index == group.captureState) state else state.copy(
                targetPaths = listOf(listOf(1)), targets = listOf(requireNotNull(state.root.child(1))),
            )
        }))
        assertEquals(original.labels.map { it.candidate.plan.planKey }, changed.labels.map { it.candidate.plan.planKey })
        assertEquals(original.labels.single { it.ruleSelected }.candidate.plan.planKey, changed.labels.single { it.ruleSelected }.candidate.plan.planKey)
        assertTrue(original.labels.zip(changed.labels).any { (before, after) -> before.survival != after.survival })
        val request = group.request.forState(group.states[group.captureState])
        val beforeFeatures = CandidateFeatures.prepare(request, original.search.candidates, original.captureTree)
        val afterFeatures = CandidateFeatures.prepare(request, changed.search.candidates, changed.captureTree)
        original.labels.zip(changed.labels).forEach { (before, after) ->
            assertArrayEquals(beforeFeatures.extract(before.candidate), afterFeatures.extract(after.candidate), 0f)
        }
    }

    @Test
    fun exportIsByteIdenticalHasExactRuleSelectionsAndContainsNoAbsolutePaths() {
        val input = input()
        val first = temporary.newFolder()
        val second = temporary.newFolder()
        val summary = DatasetExporter.export(input, first, "abc1234567")
        assertEquals(2, summary.groups)
        assertTrue(summary.candidates > 0)
        assertEquals(0, summary.groupsWithoutCandidates)
        assertEquals(summary, DatasetExporter.export(input, second, "abc1234567"))
        for (name in listOf("labels.jsonl", "rules.jsonl", "features.csv", "features.json", "export-manifest.json")) {
            val text = File(first, name).readText()
            assertEquals(text, File(second, name).readText())
            assertFalse(text.contains(input.absolutePath))
        }
        val labels = File(first, "labels.jsonl").readLines().map { JsonParser.parseString(it).asJsonObject }
        assertEquals(summary.candidates, labels.size)
        val pairs = labels.map { it["groupId"].asString to it["planKey"].asString }
        assertEquals(pairs.sortedWith(compareBy({ it.first }, { it.second })), pairs)
        labels.groupBy { it["groupId"].asString }.values.forEach { group -> assertEquals(1, group.count { it["ruleSelected"].asBoolean }) }
        assertEquals((listOf("split", "groupId", "planKey", "label") + FeatureSchema.names).joinToString(","), File(first, "features.csv").useLines { it.first() })
        val schema = JsonParser.parseString(File(first, "features.json").readText()).asJsonObject
        assertEquals(FeatureSchema.featureSchemaVersion, schema["featureSchemaVersion"].asInt)
        assertEquals(FeatureSchema.namesHash, schema["namesHash"].asString)
        assertEquals(FeatureSchema.names, schema.getAsJsonArray("names").map { it.asString })
    }

    @Test
    fun noCandidateGroupIsRecordedAsCoverageFailureWithoutInventedLabels() {
        val group = TaskGroup.load(fixture("shuffle"))
        val unsupported = group.copy(request = group.request.copy(action = CodeGenRequest.Action.SCROLL_FORWARD))
        assertTrue(DatasetExporter.label(unsupported).labels.isEmpty())
        val input = input()
        val file = File(input, "shuffle/shuffle.cgtask.json")
        val json = JsonParser.parseString(file.readText()).asJsonObject
        json.getAsJsonObject("request").addProperty("action", "scroll-forward")
        file.writeText(json.toString())
        val summary = DatasetExporter.export(input)
        assertEquals(1, summary.groupsWithoutCandidates)
        val rules = File(input, "rules.jsonl").readLines().map { JsonParser.parseString(it).asJsonObject }
        val rule = rules.single { it["groupId"].asString == "manual-shuffle" }
        assertEquals(0, rule["candidateCount"].asInt)
        assertTrue(rule["rulePlanKey"].isJsonNull)
        assertTrue(rule["ruleSurvival"].isJsonNull)
        assertTrue(File(input, "labels.jsonl").readLines().none { JsonParser.parseString(it).asJsonObject["groupId"].asString == "manual-shuffle" })
    }

    @Test
    fun failedLaterGroupPreservesAllPreviousExportFilesAndRemovesStaging() {
        val input = input()
        DatasetExporter.export(input)
        val names = listOf("labels.jsonl", "rules.jsonl", "features.csv", "features.json", "export-manifest.json")
        val before = names.associateWith { File(input, it).readText() }
        val file = File(input, "shuffle/shuffle.cgtask.json")
        val json = JsonParser.parseString(file.readText()).asJsonObject
        json.getAsJsonArray("states")[2].asJsonObject.add("targets", JsonParser.parseString("[[999]]"))
        file.writeText(json.toString())
        assertThrows(IllegalArgumentException::class.java) { DatasetExporter.export(input) }
        before.forEach { (name, content) -> assertEquals(content, File(input, name).readText()) }
        assertFalse(input.listFiles()!!.any { it.name.startsWith(".codegen-export-") })
    }

    @Test
    fun duplicateGroupIdsAndSingleStateTasksDoNotProduceLabels() {
        val input = input()
        File(input, "shuffle/shuffle.cgtask.json").copyTo(File(input, "shuffle/duplicate.cgtask.json"))
        assertThrows(IllegalArgumentException::class.java) { DatasetExporter.export(input) }
        assertFalse(File(input, "labels.jsonl").exists())
        val group = TaskGroup.load(fixture("shuffle"))
        assertThrows(IllegalArgumentException::class.java) { DatasetExporter.label(group.copy(states = group.states.take(1))) }
    }

    @Test
    fun fixtureGroupsCannotEnterEitherRealApplicationSplit() {
        val input = input()
        DatasetExporter.export(input)
        val split = File(input, "split-map.json")
        val original = split.readText()
        for (destination in listOf("real-train", "real-test")) {
            val json = JsonParser.parseString(original).asJsonObject
            json.getAsJsonObject("assignment").addProperty("manual-shuffle", destination)
            split.writeText(json.toString())
            val failure = assertThrows(IllegalArgumentException::class.java) { DatasetExporter.export(input) }
            assertEquals("Fixture groups cannot enter real-train or real-test", failure.message)
        }
        split.writeText(original)
    }

    @Test
    fun missingSplitUnknownGroupsAndFamilyLeakageDoNotOverwriteExports() {
        val input = input()
        DatasetExporter.export(input)
        val before = File(input, "features.csv").readText()
        val split = File(input, "split-map.json")
        val original = split.readText()
        val json = JsonParser.parseString(original).asJsonObject
        json.getAsJsonObject("assignment").remove("manual-shuffle")
        split.writeText(json.toString())
        assertThrows(IllegalArgumentException::class.java) { DatasetExporter.export(input) }
        assertEquals(before, File(input, "features.csv").readText())
        split.writeText(original)
        for (name in listOf("shuffle", "collection")) {
            val task = File(input, "$name/$name.cgtask.json")
            val data = JsonParser.parseString(task.readText()).asJsonObject
            data.addProperty("screenFamily", "same-family")
            task.writeText(data.toString())
        }
        assertThrows(IllegalArgumentException::class.java) { DatasetExporter.export(input) }
        assertEquals(before, File(input, "features.csv").readText())
    }

    @Test
    fun csvMetadataEscapesQuotesCommasAndLineBreaks() {
        assertEquals("plain,\"a,b\",\"say \"\"yes\"\"\",\"line\nnext\",\"line\rnext\"\n",
            FeatureExport.csvRow(listOf("plain", "a,b", "say \"yes\"", "line\nnext", "line\rnext")))
    }

    @Test
    fun privateOutputsStayUnderTheConfiguredRootOutsideGit() {
        val root = temporary.newFolder()
        FeatureExport.requirePrivate(File(root, "output/features.csv"), root)
        assertThrows(IllegalArgumentException::class.java) { FeatureExport.requirePrivate(temporary.newFile(), root) }
        File(root, ".git").writeText("gitdir: elsewhere")
        assertThrows(IllegalArgumentException::class.java) { FeatureExport.requirePrivate(File(root, "output/features.csv"), root) }
    }

    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/codegen/dataset/$name/$name.cgtask.json")).toURI())

    private fun input(): File = temporary.newFolder().also { folder ->
        for (name in listOf("shuffle", "collection")) requireNotNull(fixture(name).parentFile).copyRecursively(File(folder, name))
        File(folder, "split-map.json").writeText("""{"format":1,"datasetId":"handwritten","assignment":{"manual-shuffle":"train","manual-collection":"test"}}""")
    }
}
