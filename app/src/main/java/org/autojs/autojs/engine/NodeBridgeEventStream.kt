package org.autojs.autojs.engine

import org.json.JSONObject
import java.util.ArrayDeque

/** Orders subscription acknowledgement before events produced during registration. */
internal class NodeBridgeEventStream(
    private val requestId: String,
    val subscriptionId: String,
    private val callback: (NodeBridgeResponse) -> Unit,
) {
    private val pending = ArrayDeque<JSONObject>()
    private var ready = false

    @Synchronized
    fun publish(event: JSONObject) {
        if (!ready) {
            if (pending.size == 128) pending.removeFirst()
            pending.addLast(event)
        } else {
            callback(NodeBridgeResponse(requestId, true, event.toString(), event = true, subscriptionId = subscriptionId))
        }
    }

    @Synchronized
    fun acknowledge(result: JSONObject) {
        callback(NodeBridgeResponse.success(requestId, result.put("subscriptionId", subscriptionId).toString()))
        ready = true
        while (pending.isNotEmpty()) publish(pending.removeFirst())
    }

    companion object {
        fun forRequest(request: NodeBridgeRequest, id: String, callback: (NodeBridgeResponse) -> Unit): NodeBridgeEventStream? =
            if (request.events) NodeBridgeEventStream(request.id, id, callback) else null
    }
}
