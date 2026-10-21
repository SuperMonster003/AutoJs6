package org.autojs.autojs.engine

import org.json.JSONObject

/** Read-only search roots may move; actionable snapshots retain exact geometry validation. */
internal object RuntimeFlowNodeIdentity {
    fun matches(snapshot: JSONObject, current: JSONObject, movingRoot: Boolean = false): Boolean {
        val fields = listOf("windowId", "packageName", "className", "id", "text", "desc", "depth", "indexInParent", "bounds")
        fields.forEach { require(snapshot.has(it)) { "Incomplete Flow node snapshot: $it" } }
        return fields.all { key ->
            if (key == "bounds") movingRoot || listOf("left", "top", "right", "bottom").all { coordinate ->
                current.getJSONObject(key).getInt(coordinate) == snapshot.getJSONObject(key).getInt(coordinate)
            } else current.get(key).toString() == snapshot.get(key).toString()
        }
    }
}
