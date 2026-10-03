package org.autojs.autojs.core.accessibility.codegen.dataset

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import java.io.File

/** One intent across captured states. Future states supply labels, never candidate-search input.
 * zh-CN: 同一意图的多个捕获状态. 后续状态仅提供标签, 不作为候选搜索的输入. */
data class TaskGroup(
    val groupId: String,
    val source: String,
    val app: String,
    val screenFamily: String,
    val request: Request,
    val captureState: Int,
    val states: List<State>,
) {
    data class Request(
        val scope: CodeGenRequest.Scope,
        val attributes: Set<CodeGenRequest.Attr>,
        val action: CodeGenRequest.Action,
        val receiver: CodeGenRequest.Receiver,
    ) {
        fun forState(state: State) = CodeGenRequest(
            targets = state.targets,
            scope = scope,
            action = action,
            receiver = receiver,
            attributes = attributes,
        )
    }

    data class State(
        val stateId: String,
        val snapshot: String,
        val mutations: List<String>,
        val targetPaths: List<List<Int>>,
        val root: SnapshotNode,
        val targets: List<SnapshotNode>,
    )

    fun requireLabelable() {
        require(states.size >= 2) { "Task $groupId needs at least two states to compute survival" }
    }

    companion object {
        const val FORMAT = 1

        /** The optional loader permits bounded snapshot reuse by offline tools. */
        fun load(file: File, snapshotLoader: (File) -> SnapshotNode = SnapshotNode::load): TaskGroup {
            val json = readObject(file)
            json.keysExactly(setOf("format", "groupId", "source", "app", "screenFamily", "request", "captureState", "states"), "task")
            require(json.integer("format") == FORMAT) { "Unknown task format" }
            val groupId = json.string("groupId")
            val source = json.string("source")
            require(source in setOf("fixture", "real")) { "source must be fixture or real" }
            val app = json.string("app")
            val screenFamily = json.string("screenFamily")
            val request = parseRequest(json.obj("request"))
            val captureState = if (json.has("captureState")) json.integer("captureState") else 0
            val stateArray = json.array("states")
            require(stateArray.size() > 0) { "states must not be empty" }
            require(captureState in 0 until stateArray.size()) { "captureState is outside states" }
            val stateIds = HashSet<String>()
            val base = requireNotNull(file.canonicalFile.parentFile) { "Task file must have a containing directory" }
            val states = stateArray.mapIndexed { index, item ->
                require(item.isJsonObject) { "states[$index] must be an object" }
                val state = item.asJsonObject
                state.keysExactly(setOf("stateId", "snapshot", "mutations", "targets"), "states[$index]")
                val stateId = state.string("stateId")
                require(stateIds.add(stateId)) { "Duplicate stateId: $stateId" }
                val relative = state.string("snapshot")
                val snapshotFile = resolveSnapshot(base, relative)
                val mutations = state.array("mutations").map { it.requiredString("mutation") }
                require(mutations.distinct().size == mutations.size) { "Duplicate mutations in state $stateId" }
                val paths = state.array("targets").map { path ->
                    require(path.isJsonArray) { "Target path in state $stateId must be an array" }
                    path.asJsonArray.map { component ->
                        component.requiredInteger("target path index").also { require(it >= 0) { "Target path indices must be nonnegative" } }
                    }
                }
                require(paths.isNotEmpty()) { "State $stateId must have targets" }
                require(paths.distinct().size == paths.size) { "State $stateId has duplicate target paths" }
                if (request.scope == CodeGenRequest.Scope.SINGLE) {
                    require(paths.size == 1) { "Single scope requires exactly one target in state $stateId" }
                }
                val root = try {
                    snapshotLoader(snapshotFile)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid snapshot $relative in state $stateId", e)
                }
                val targets = paths.map { path ->
                    var node = root
                    for (childIndex in path) {
                        node = node.child(childIndex)
                            ?: throw IllegalArgumentException("Target path $path does not exist in state $stateId")
                    }
                    node
                }
                State(stateId, relative, mutations, paths, root, targets)
            }
            require(states.map { it.targets.size }.distinct().size == 1) { "Target count must be the same in every state" }
            return TaskGroup(groupId, source, app, screenFamily, request, captureState, states)
        }

        /** Reads only the identity so tools can order tasks without retaining every layout. */
        fun readGroupId(file: File): String = readObject(file).string("groupId")

        private fun resolveSnapshot(base: File, relative: String): File {
            require(relative.endsWith(".layout.json")) { "snapshot must end with .layout.json" }
            require(!relative.startsWith('/') && '\\' !in relative && ':' !in relative && '\u0000' !in relative) {
                "snapshot must use a relative forward-slash path"
            }
            require(relative.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
                "snapshot path must not contain empty, dot or parent components"
            }
            val resolved = File(base, relative).canonicalFile
            require(resolved.toPath().startsWith(base.toPath())) { "snapshot must stay inside the task directory" }
            require(resolved.isFile) { "Snapshot does not exist: $relative" }
            return resolved
        }

        private fun parseRequest(json: JsonObject): Request {
            json.keysExactly(setOf("scope", "attributes", "action", "receiver"), "request")
            val scope = when (json.string("scope")) {
                "single" -> CodeGenRequest.Scope.SINGLE
                "collection" -> CodeGenRequest.Scope.COLLECTION
                else -> throw IllegalArgumentException("Unknown request scope")
            }
            val attrs = json.array("attributes").map {
                when (it.requiredString("attribute")) {
                    "id" -> CodeGenRequest.Attr.ID
                    "text" -> CodeGenRequest.Attr.TEXT
                    "desc" -> CodeGenRequest.Attr.DESC
                    else -> throw IllegalArgumentException("Unknown request attribute")
                }
            }
            require(attrs.distinct().size == attrs.size) { "Duplicate request attributes" }
            val action = when (json.string("action")) {
                "none" -> CodeGenRequest.Action.NONE
                "click" -> CodeGenRequest.Action.CLICK
                "long-click" -> CodeGenRequest.Action.LONG_CLICK
                "set-text" -> CodeGenRequest.Action.SET_TEXT("")
                "scroll-forward" -> CodeGenRequest.Action.SCROLL_FORWARD
                "scroll-backward" -> CodeGenRequest.Action.SCROLL_BACKWARD
                else -> throw IllegalArgumentException("Unknown request action")
            }
            val receiver = when (if (json.has("receiver")) json.string("receiver") else "auto") {
                "auto" -> CodeGenRequest.Receiver.AUTO
                "self" -> CodeGenRequest.Receiver.SELF
                else -> throw IllegalArgumentException("Unknown request receiver")
            }
            return Request(scope, attrs.toSet(), action, receiver)
        }

        private fun readObject(file: File): JsonObject = try {
            file.reader(Charsets.UTF_8).use { input ->
                JsonReader(input).use { reader ->
                    val value = readValue(reader)
                    require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing task JSON content" }
                    require(value.isJsonObject) { "Task must be an object" }
                    value.asJsonObject
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid task JSON: ${file.name}", e)
        }

        // Gson's object parser accepts duplicate keys; reject them before a value can be replaced.
        // zh-CN: Gson 对象解析器接受重复键; 在值被覆盖前拒绝重复键.
        private fun readValue(reader: JsonReader, depth: Int = 0): JsonElement {
            require(depth < 32) { "Task JSON nesting is too deep" }
            return when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> JsonObject().also { result ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        require(!result.has(key)) { "Duplicate JSON key: $key" }
                        result.add(key, readValue(reader, depth + 1))
                    }
                    reader.endObject()
                }
                JsonToken.BEGIN_ARRAY -> JsonArray().also { result ->
                    reader.beginArray()
                    while (reader.hasNext()) result.add(readValue(reader, depth + 1))
                    reader.endArray()
                }
                JsonToken.STRING -> JsonPrimitive(reader.nextString())
                JsonToken.NUMBER -> JsonParser.parseString(reader.nextString())
                JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                JsonToken.NULL -> JsonNull.INSTANCE.also { reader.nextNull() }
                else -> throw IllegalArgumentException("Unexpected task JSON token")
            }
        }

        private fun JsonObject.keysExactly(allowed: Set<String>, context: String) {
            val unexpected = keySet() - allowed
            require(unexpected.isEmpty()) { "Unknown $context fields: ${unexpected.sorted().joinToString()}" }
        }

        private fun JsonObject.string(key: String) = requireNotNull(get(key)) { "Missing $key" }.requiredString(key)
        private fun JsonObject.integer(key: String) = requireNotNull(get(key)) { "Missing $key" }.requiredInteger(key)
        private fun JsonObject.obj(key: String): JsonObject {
            val item = get(key)
            require(item != null && item.isJsonObject) { "$key must be an object" }
            return item.asJsonObject
        }

        private fun JsonObject.array(key: String): JsonArray {
            val item = get(key)
            require(item != null && item.isJsonArray) { "$key must be an array" }
            return item.asJsonArray
        }

        private fun JsonElement.requiredString(field: String): String {
            require(isJsonPrimitive && asJsonPrimitive.isString) { "$field must be a string" }
            return asString.also { value ->
                require(value.isNotBlank() && value.none { it.isISOControl() }) { "$field must be a nonblank string without control characters" }
            }
        }

        private fun JsonElement.requiredInteger(field: String): Int {
            require(isJsonPrimitive && asJsonPrimitive.isNumber) { "$field must be an integer" }
            require(Regex("-?(0|[1-9][0-9]*)").matches(asString)) { "$field must be an integer" }
            return asString.toIntOrNull() ?: throw IllegalArgumentException("$field must be a 32-bit integer")
        }
    }
}
