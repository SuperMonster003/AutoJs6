package org.autojs.autojs.engine

import org.autojs.autojs.timing.IntentTask
import org.json.JSONArray
import org.json.JSONObject

object NodeIntentTaskSnapshots {

    const val SCHEMA = "autojs6-node-intent-task-snapshot-v1"

    fun snapshot(task: IntentTask): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("id", task.id)
        .putNullable("scriptPath", task.scriptPath)
        .putNullable("action", task.action)
        .putNullable("category", task.category)
        .putNullable("dataType", task.dataType)
        .put("local", task.isLocal)
        .put("scope", if (task.isLocal) "app-local" else "external-broadcast")
        .put("legacyOwner", "rhino_intent_task_database")
        .put("nodeProviderOwned", false)
        .put("rawObjectPolicy", rawObjectPolicy())

    fun querySnapshots(
        tasks: Iterable<IntentTask>,
        action: String? = null,
        scriptPath: String? = null,
    ): JSONArray = JSONArray().apply {
        tasks
            .filter { task -> action == null || task.action == action }
            .filter { task -> scriptPath == null || task.scriptPath == scriptPath }
            .sortedBy { task -> task.id }
            .forEach { task -> put(snapshot(task)) }
    }

    fun getSnapshot(tasks: Iterable<IntentTask>, id: Long): JSONObject? =
        tasks.firstOrNull { task -> task.id == id }?.let(::snapshot)

    private fun rawObjectPolicy(): JSONObject = JSONObject()
        .put("rawIntentTask", "denied")
        .put("rawIntentFilter", "denied")
        .put("rawIntent", "denied")
        .put("rawBroadcastReceiver", "denied")
        .put("rawReceiverHandle", "denied")
        .put("mutableRowHandle", "denied")

    private fun JSONObject.putNullable(name: String, value: String?): JSONObject =
        put(name, value ?: JSONObject.NULL)
}
