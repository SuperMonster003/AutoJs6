package org.autojs.plugin.python.runtime.fake

import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import java.io.Closeable

class FakePythonSnapshotSet private constructor(
    payloads: Map<PythonPayloadKind, ByteArray>,
) : Closeable {
    private val values = payloads.mapValues { (_, value) -> value.copyOf() }.toMutableMap()

    @Synchronized
    fun bytes(kind: PythonPayloadKind): ByteArray =
        checkNotNull(values[kind]) { "Snapshot is unavailable" }.copyOf()

    @Synchronized
    override fun close() {
        values.values.forEach { it.fill(0) }
        values.clear()
    }

    companion object {
        internal fun publish(payloads: Map<PythonPayloadKind, ByteArray>) = FakePythonSnapshotSet(payloads)
    }
}

object FakePythonSnapshotMaterializer {
    fun materialize(
        request: PythonExecutionRequest,
        read: (PythonPayloadReference) -> FakeReadPayload,
    ): FakePythonSnapshotSet {
        val pending = linkedMapOf<PythonPayloadKind, ByteArray>()
        try {
            listOfNotNull(request.source, request.workspaceArchive, request.stdin).forEach { reference ->
                val payload = read(reference)
                require(payload.sha256.contentEquals(reference.sha256.toByteArray())) {
                    "${reference.kind} payload digest does not match"
                }
                check(pending.put(reference.kind, payload.bytes) == null) {
                    "Payload kind was materialized more than once"
                }
            }
            return FakePythonSnapshotSet.publish(pending)
        } finally {
            pending.values.forEach { it.fill(0) }
        }
    }
}
