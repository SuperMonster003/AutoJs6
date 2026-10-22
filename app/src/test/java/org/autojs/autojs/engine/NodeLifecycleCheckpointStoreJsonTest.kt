package org.autojs.autojs.engine

import org.autojs.autojs.core.pref.SettingsJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checkpoint and stop request files of [NodeLifecycleCheckpointStore] as plain values of [SettingsJson]
 * (docs/settings/ROADMAP.md P5.3): a checkpoint file that Android's org.json wrote reads the same and is written back
 * in the same indent-2 layout, the status and diagnostics read it, and a checkpoint value is measured by its
 * `SettingsJson` text within the size, depth and finite-number limits.
 *
 * zh-CN: [NodeLifecycleCheckpointStore] 的检查点与停止请求文件以 [SettingsJson] 普通值表示 (ROADMAP P5.3): Android org.json
 * 写的检查点文件照常读取, 写回时保持缩进 2 格的版式, 状态与诊断照常读取; 检查点值按 `SettingsJson` 文本计量, 大小, 嵌套深度与
 * 有限数的限制不变.
 */
class NodeLifecycleCheckpointStoreJsonTest {

    // A checkpoint file as the store wrote it before P5.3: Android's `JSONObject.toString(2)` (escaped slashes, Long
    // timestamps, a nested checkpoint value with a JSON null, an empty object and an empty array).
    private val oldCheckpointFile = """
        {
          "schemaVersion": 1,
          "projectKey": "6f1d0c3b9a8e7d6c5b4a39281706f5e4",
          "packageName": "org.autojs.autojs6",
          "workingDirectory": "\/storage\/emulated\/0\/Scripts\/node-app",
          "executionId": "node-exec-42",
          "sourceName": "\/storage\/emulated\/0\/Scripts\/node-app\/main.cjs",
          "executionMode": "interactive_long_running",
          "launchSurface": "interactive_session",
          "reason": "manual",
          "checkpointBytes": 115,
          "createdAtEpochMs": 1761130000000,
          "updatedAtEpochMs": 1761134400000,
          "automaticRestart": false,
          "restartPolicy": "never",
          "checkpoint": {
            "step": 3,
            "cursor": "a\/b",
            "ratio": 0.25,
            "done": false,
            "last": null,
            "items": [
              1,
              "two",
              {
                "three": 3
              }
            ],
            "empty": {},
            "none": []
          }
        }
    """.trimIndent()

    private val scope = NodeLifecycleCheckpointStore.ProjectScope(
        packageName = "org.autojs.autojs6",
        workingDirectory = "/storage/emulated/0/Scripts/node-app",
        projectKey = "6f1d0c3b9a8e7d6c5b4a39281706f5e4",
    )

    // `pid` is passed so that the default value (android.os.Process) is not evaluated on the JVM.
    private val engineInfo = NodeBridgeEngineInfo(
        id = "node-exec-42",
        sourceName = "/storage/emulated/0/Scripts/node-app/main.cjs",
        cwd = "/storage/emulated/0/Scripts/node-app",
        executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
        launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
        pid = 1,
    )

    @Test
    fun checkpointFilesThatOrgJsonWroteStillRead() {
        val record = NodeLifecycleCheckpointStore.decode(oldCheckpointFile)
        assertEquals(
            listOf(
                "schemaVersion", "projectKey", "packageName", "workingDirectory", "executionId", "sourceName", "executionMode",
                "launchSurface", "reason", "checkpointBytes", "createdAtEpochMs", "updatedAtEpochMs", "automaticRestart",
                "restartPolicy", "checkpoint",
            ),
            record.keys.toList(),
        )
        assertEquals("/storage/emulated/0/Scripts/node-app", record["workingDirectory"])
        assertEquals("/storage/emulated/0/Scripts/node-app/main.cjs", record["sourceName"])
        assertEquals(115, record["checkpointBytes"])
        assertEquals(1761130000000L, record["createdAtEpochMs"])
        assertEquals(1761134400000L, record["updatedAtEpochMs"])
        assertEquals(false, record["automaticRestart"])

        val checkpoint = record["checkpoint"] as Map<*, *>
        assertEquals(listOf("step", "cursor", "ratio", "done", "last", "items", "empty", "none"), checkpoint.keys.toList())
        assertEquals(3, checkpoint["step"])
        assertEquals("a/b", checkpoint["cursor"])
        assertEquals(0.25, checkpoint["ratio"])
        assertTrue(checkpoint.containsKey("last"))
        assertNull(checkpoint["last"])
        assertEquals(listOf(1, "two", mapOf("three" to 3)), checkpoint["items"])
        assertEquals(emptyMap<String, Any?>(), checkpoint["empty"])
        assertEquals(emptyList<Any?>(), checkpoint["none"])
    }

    @Test
    fun checkpointFilesRoundTripInTheIndentedLayout() {
        val text = NodeLifecycleCheckpointStore.encode(NodeLifecycleCheckpointStore.decode(oldCheckpointFile))
        assertEquals(oldCheckpointFile.replace("""\/""", "/"), text)
        assertEquals(NodeLifecycleCheckpointStore.decode(oldCheckpointFile), NodeLifecycleCheckpointStore.decode(text))
    }

    @Test
    fun statusAndDiagnosticsReadOldCheckpointFiles() {
        val record = NodeLifecycleCheckpointStore.decode(oldCheckpointFile)
        val status = NodeLifecycleCheckpointStore.statusSnapshot(scope, record)
        assertEquals(
            listOf(
                "schemaVersion", "exists", "projectKey", "packageName", "workingDirectory", "maxBytes", "automaticRestart",
                "restartPolicy", "executionId", "sourceName", "reason", "checkpointBytes", "createdAtEpochMs", "updatedAtEpochMs",
            ),
            status.keys.toList(),
        )
        assertEquals(true, status["exists"])
        assertEquals(NodeLifecycleCheckpointStore.MAX_CHECKPOINT_BYTES, status["maxBytes"])
        assertEquals("node-exec-42", status["executionId"])
        assertEquals("manual", status["reason"])
        assertEquals(115, status["checkpointBytes"])
        assertEquals(1761130000000L, status["createdAtEpochMs"])
        assertEquals(1761134400000L, status["updatedAtEpochMs"])
        assertEquals(false, NodeLifecycleCheckpointStore.statusSnapshot(scope, null)["exists"])
        assertEquals(8, NodeLifecycleCheckpointStore.statusSnapshot(scope, null).size)

        assertEquals(
            mapOf(
                "embedded_script.lifecycle_checkpoint.enabled" to "true",
                "embedded_script.lifecycle_checkpoint.project_key" to "6f1d0c3b9a8e7d6c5b4a39281706f5e4",
                "embedded_script.lifecycle_checkpoint.exists" to "true",
                "embedded_script.lifecycle_checkpoint.max_bytes" to "65536",
                "embedded_script.lifecycle_checkpoint.automatic_restart" to "false",
                "embedded_script.lifecycle_checkpoint.restart_policy" to "never",
                "embedded_script.lifecycle_checkpoint.bytes" to "115",
                "embedded_script.lifecycle_checkpoint.reason" to "manual",
            ),
            NodeLifecycleCheckpointStore.nativeDiagnostics(scope, true, record),
        )
        val missing = NodeLifecycleCheckpointStore.nativeDiagnostics(scope, false, null)
        assertEquals("false", missing["embedded_script.lifecycle_checkpoint.exists"])
        assertEquals("0", missing["embedded_script.lifecycle_checkpoint.bytes"])
        assertEquals("", missing["embedded_script.lifecycle_checkpoint.reason"])
    }

    @Test
    fun checkpointRecordsKeepTheCreationTimeAndTheLayout() {
        val (value, checkpointBytes) = NodeLifecycleCheckpointStore.checkedCheckpoint(linkedMapOf("step" to 4, "cursor" to "a/b"))
        assertEquals(25, checkpointBytes)
        val previous = NodeLifecycleCheckpointStore.decode(oldCheckpointFile)
        val record = NodeLifecycleCheckpointStore.checkpointRecord(scope, engineInfo, " ", value, checkpointBytes, previous, 1761138000000L)
        val expected = """
            {
              "schemaVersion": 1,
              "projectKey": "6f1d0c3b9a8e7d6c5b4a39281706f5e4",
              "packageName": "org.autojs.autojs6",
              "workingDirectory": "/storage/emulated/0/Scripts/node-app",
              "executionId": "node-exec-42",
              "sourceName": "/storage/emulated/0/Scripts/node-app/main.cjs",
              "executionMode": "interactive_long_running",
              "launchSurface": "interactive_session",
              "reason": "manual",
              "checkpointBytes": 25,
              "createdAtEpochMs": 1761130000000,
              "updatedAtEpochMs": 1761138000000,
              "automaticRestart": false,
              "restartPolicy": "never",
              "checkpoint": {
                "step": 4,
                "cursor": "a/b"
              }
            }
        """.trimIndent()
        assertEquals(expected, NodeLifecycleCheckpointStore.encode(record))

        // Without a previous record the creation time is the write time; a missing checkpoint is a JSON null.
        val (none, noneBytes) = NodeLifecycleCheckpointStore.checkedCheckpoint(JSONObject.NULL)
        assertNull(none)
        assertEquals(4, noneBytes)
        val first = NodeLifecycleCheckpointStore.checkpointRecord(scope, engineInfo, "resume", none, noneBytes, null, 5L)
        assertEquals(5L, first["createdAtEpochMs"])
        assertEquals("resume", first["reason"])
        assertTrue(first.containsKey("checkpoint"))
        assertTrue(NodeLifecycleCheckpointStore.encode(first).endsWith("\"restartPolicy\": \"never\",\n  \"checkpoint\": null\n}"))
    }

    @Test
    fun checkpointValuesAreMeasuredBySettingsJsonText() {
        // Android's org.json wrote [1,"x\/y",null,{"k":1.5}] (25 bytes); `SettingsJson` does not escape the slash.
        val (value, checkpointBytes) = NodeLifecycleCheckpointStore.checkedCheckpoint(
            JSONArray().put(1).put("x/y").put(JSONObject.NULL).put(JSONObject().put("k", 1.5)),
        )
        assertEquals(listOf(1, "x/y", null, mapOf("k" to 1.5)), value)
        assertEquals(24, checkpointBytes)
        // The checkpoint of the old file: Android's org.json measured 115 bytes (one escaped slash).
        val oldValue = NodeLifecycleCheckpointStore.decode(oldCheckpointFile)["checkpoint"]
        assertEquals(oldValue to 114, NodeLifecycleCheckpointStore.checkedCheckpoint(oldValue))
        // An integral double is written as 2.0 (Android's org.json wrote 2).
        assertEquals(2.0 to 3, NodeLifecycleCheckpointStore.checkedCheckpoint(2.0))
        // Plain values are accepted as well.
        assertEquals(
            listOf(mapOf("a" to listOf(true, "b")), 1761130000000L) to 32,
            NodeLifecycleCheckpointStore.checkedCheckpoint(listOf(mapOf("a" to listOf(true, "b")), 1761130000000L)),
        )
    }

    @Test
    fun checkpointsOverTheSizeLimitAreRejected() {
        val max = NodeLifecycleCheckpointStore.MAX_CHECKPOINT_BYTES
        assertEquals(max, NodeLifecycleCheckpointStore.checkedCheckpoint("a".repeat(max - 2)).second)
        val error = checkpointError("a".repeat(max - 1))
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, error.code)
        assertEquals(NodeLifecycleCheckpointStore.BRIDGE_MODULE_NAME, error.module)
        assertEquals(NodeLifecycleCheckpointStore.METHOD_CHECKPOINT, error.method)
        assertEquals("AutoJs6 lifecycle checkpoint exceeded $max bytes.", error.message)
    }

    @Test
    fun deepNonFiniteOrUnsupportedCheckpointsAreRejected() {
        assertEquals(0, flatten(NodeLifecycleCheckpointStore.checkedCheckpoint(nestedLists(32)).first))
        var nestedArrays: Any = 0
        repeat(32) { nestedArrays = JSONArray().put(nestedArrays) }
        assertEquals(0, flatten(NodeLifecycleCheckpointStore.checkedCheckpoint(nestedArrays).first))

        for (checkpoint in listOf<Any>(nestedLists(33), JSONArray().put(nestedArrays))) {
            val error = checkpointError(checkpoint)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, error.code)
            assertEquals("AutoJs6 lifecycle checkpoint exceeded maximum JSON nesting depth.", error.message)
        }
        for (checkpoint in listOf(listOf(1, Double.NaN), mapOf("k" to Double.POSITIVE_INFINITY), Float.NEGATIVE_INFINITY)) {
            val error = checkpointError(checkpoint)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, error.code)
            assertEquals("AutoJs6 lifecycle checkpoint numbers must be finite.", error.message)
        }
        for (checkpoint in listOf(listOf(Any()), mapOf(1 to 2))) {
            val error = checkpointError(checkpoint)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, error.code)
            assertEquals("AutoJs6 lifecycle checkpoint must be JSON-serializable.", error.message)
        }
    }

    @Test
    fun stopRequestsKeepTheIndentedLayout() {
        val request = NodeLifecycleCheckpointStore.stopRequest("node-exec-42", "user", 1761134400000L)
        val expected = """
            {
              "schemaVersion": 1,
              "id": "stop-node-exec-42-1761134400000",
              "executionId": "node-exec-42",
              "reason": "user",
              "requestedAtEpochMs": 1761134400000
            }
        """.trimIndent()
        assertEquals(expected, NodeLifecycleCheckpointStore.encode(request))
        assertEquals(request, NodeLifecycleCheckpointStore.decode(expected))
    }

    private fun checkpointError(checkpoint: Any?): NodeBridgeError {
        try {
            NodeLifecycleCheckpointStore.checkedCheckpoint(checkpoint)
        } catch (e: NodeLifecycleCheckpointException) {
            return e.bridgeError
        }
        throw AssertionError("The checkpoint was accepted.")
    }

    private fun nestedLists(levels: Int): Any {
        var value: Any = 0
        repeat(levels) { value = listOf(value) }
        return value
    }

    private fun flatten(value: Any?): Any? = if (value is List<*>) flatten(value.single()) else value
}
