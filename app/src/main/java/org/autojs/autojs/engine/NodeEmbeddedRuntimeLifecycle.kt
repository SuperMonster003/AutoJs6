package org.autojs.autojs.engine

import android.util.Log

internal class NodeEmbeddedRuntimeLifecycle(
    private val tag: String,
    val executionId: String,
    private val processName: String,
    private val pid: Int,
    private val sourceName: String,
) {

    private val transitions = mutableListOf<String>()

    @Volatile
    var state: NodeEmbeddedRuntimeState = NodeEmbeddedRuntimeState.CREATED
        private set

    @Synchronized
    fun transition(
        to: NodeEmbeddedRuntimeState,
        reason: String,
    ) {
        val from = state
        if (from == to) {
            return
        }
        state = to
        transitions += "${from.name}->${to.name}"
        Log.i(
            tag,
            "node.runtime.state executionId=$executionId ${from.name} -> ${to.name} " +
                    "reason=$reason processName=$processName pid=$pid sourceName=$sourceName",
        )
    }

    @Synchronized
    fun nativeValues(): Map<String, String> {
        return linkedMapOf(
            "embedded_script.execution_id" to executionId,
            "embedded_script.runtime_state" to state.name,
            "embedded_script.lifecycle_transitions" to transitions.joinToString("|"),
            "embedded_script.lifecycle_transition_count" to transitions.size.toString(),
        )
    }
}
