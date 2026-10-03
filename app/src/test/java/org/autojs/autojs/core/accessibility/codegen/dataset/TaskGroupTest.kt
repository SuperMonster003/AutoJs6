package org.autojs.autojs.core.accessibility.codegen.dataset

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TaskGroupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun loadsSingleAndCollectionTargetsWithCanonicalNodeIdentity() {
        val single = TaskGroup.load(fixture("shuffle"))
        assertEquals("manual-shuffle", single.groupId)
        assertEquals(0, single.captureState)
        assertEquals(CodeGenRequest.Receiver.AUTO, single.request.receiver)
        assertEquals(listOf("Beta", "Beta", "Beta"), single.states.map { it.targets.single().text() })
        assertSame(single.states[0].root.child(1), single.states[0].targets.single())
        val collection = TaskGroup.load(fixture("collection"))
        assertEquals(CodeGenRequest.Scope.COLLECTION, collection.request.scope)
        assertEquals(CodeGenRequest.Receiver.SELF, collection.request.receiver)
        assertTrue(collection.states.all { it.targets.size == 2 })
        assertEquals(collection.states[1].targets, collection.request.forState(collection.states[1]).targets)
        collection.requireLabelable()
    }

    @Test
    fun rejectsOutOfBoundsNegativeFractionalAndDuplicatePaths() {
        for (targets in listOf("[[8]]", "[[-1]]", "[[0.5]]", "[[2147483648]]", "[[1],[1]]", "[]")) {
            val file = altered("shuffle") { it.getAsJsonArray("states")[0].asJsonObject.add("targets", JsonParser.parseString(targets)) }
            assertThrows(IllegalArgumentException::class.java) { TaskGroup.load(file) }
        }
    }

    @Test
    fun rejectsTargetCountChangesAndSingleScopeWithMultipleTargets() {
        val changed = altered("collection") { it.getAsJsonArray("states")[1].asJsonObject.add("targets", JsonParser.parseString("[[0]]")) }
        assertThrows(IllegalArgumentException::class.java) { TaskGroup.load(changed) }
        val multiple = altered("collection") { it.getAsJsonObject("request").addProperty("scope", "single") }
        assertThrows(IllegalArgumentException::class.java) { TaskGroup.load(multiple) }
    }

    @Test
    fun rejectsUnsupportedFormatUnknownOptionsWrongTypesAndDuplicateKeys() {
        val edits: List<(JsonObject) -> Unit> = listOf(
            { it.addProperty("format", 2) },
            { it.addProperty("format", "1") },
            { it.addProperty("source", "generated") },
            { it.addProperty("captureState", 3) },
            { it.addProperty("captureState", -1) },
            { it.getAsJsonObject("request").addProperty("style", "flow") },
            { it.getAsJsonObject("request").addProperty("action", "tap") },
            { it.getAsJsonObject("request").addProperty("receiver", "parent") },
            { it.getAsJsonObject("request").add("attributes", JsonParser.parseString("[\"text\",\"text\"]")) },
            { it.getAsJsonArray("states")[1].asJsonObject.addProperty("stateId", "s0") },
        )
        for (edit in edits) assertThrows(IllegalArgumentException::class.java) { TaskGroup.load(altered("shuffle", edit)) }
        val file = copied("shuffle")
        file.writeText(file.readText().replace("\"format\": 1", "\"format\": 1, \"format\": 1"))
        assertThrows(IllegalArgumentException::class.java) { TaskGroup.load(file) }
    }

    @Test
    fun rejectsMissingAbsoluteAndParentSnapshotPathsBeforeLoading() {
        val invalid = listOf("missing.layout.json", "../s0.layout.json", "a/../../s0.layout.json", "/s0.layout.json", "C:/s0.layout.json", "a\\s0.layout.json", "./s0.layout.json", "s0.json", "a//s0.layout.json")
        for (path in invalid) {
            var loaded = false
            val file = altered("shuffle") { it.getAsJsonArray("states")[0].asJsonObject.addProperty("snapshot", path) }
            assertThrows(IllegalArgumentException::class.java) {
                TaskGroup.load(file) { loaded = true; SnapshotNode.load(it) }
            }
            assertTrue("Invalid paths must be rejected before snapshot access", !loaded)
        }
    }

    @Test
    fun captureStateCanBeNonzeroAndRootIsAnEmptyTargetPath() {
        val file = altered("shuffle") { json ->
            json.addProperty("captureState", 2)
            json.getAsJsonObject("request").addProperty("action", "none")
            json.getAsJsonArray("states").forEach { it.asJsonObject.add("targets", JsonParser.parseString("[[]]")) }
        }
        val group = TaskGroup.load(file)
        assertEquals(2, group.captureState)
        group.states.forEach { assertSame(it.root, it.targets.single()) }
    }

    @Test
    fun singleStateIsReadableForTheQueueButCannotProduceTrainingLabels() {
        val file = altered("shuffle") { json ->
            json.add("states", JsonArray().apply { add(json.getAsJsonArray("states")[0]) })
        }
        val group = TaskGroup.load(file)
        assertThrows(IllegalArgumentException::class.java) { group.requireLabelable() }
    }

    @Test
    fun actionCarriesCapabilitiesWithoutRuntimeTextOrTiming() {
        val file = altered("shuffle") { it.getAsJsonObject("request").addProperty("action", "set-text") }
        val group = TaskGroup.load(file)
        val request = group.request.forState(group.states.first())
        assertEquals(CodeGenRequest.Action.SET_TEXT(""), request.action)
        assertEquals(CodeGenRequest.Mode.WAIT, request.mode)
        assertEquals(CodeGenRequest.Style.CLASSIC, request.style)
    }

    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/codegen/dataset/$name/$name.cgtask.json")).toURI())

    private fun copied(name: String): File {
        val directory = temporary.newFolder()
        requireNotNull(fixture(name).parentFile).copyRecursively(directory, overwrite = true)
        return File(directory, "$name.cgtask.json")
    }

    private fun altered(name: String, edit: (JsonObject) -> Unit): File = copied(name).also { file ->
        val json = JsonParser.parseString(file.readText()).asJsonObject
        edit(json)
        file.writeText(json.toString())
    }
}
