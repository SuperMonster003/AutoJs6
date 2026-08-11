package org.autojs.plugin.python.runtime.consumer

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireProtocol
import org.autojs.plugin.python.runtime.api.PythonCancellationReason
import org.autojs.plugin.python.runtime.api.PythonExecutionCancellation
import org.autojs.plugin.python.runtime.api.PythonFailurePhase
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract

/**
 * Compile and round-trip smoke for the two separately distributed R1 AARs.
 *
 * Keeping this code in the main source set proves that an Android consumer can
 * resolve both the shared tagged-wire API and the Python Runtime API without a
 * source-project dependency.
 */
object PythonRuntimeApiDistributionSmoke {

    fun verify(): Result {
        check(TaggedWireProtocol.MAGIC == 0x414A3657)
        check(PythonRuntimeContract.ENGINE_ID == "python")
        check(PythonRuntimeContract.PROTOCOL_MAJOR == 1)

        val expected = PythonExecutionCancellation(
            requestId = PythonRequestId.fromBytes(ByteArray(PythonRequestId.BYTE_COUNT) { index ->
                (index + 1).toByte()
            }),
            reason = PythonCancellationReason.REQUESTED,
            phase = PythonFailurePhase.CANCELLATION,
            elapsedMillis = 17L,
        )
        val encoded = PythonRuntimeCodec.encodeCancellation(expected)
        val wireDocument = TaggedWireDocument.decode(encoded)
        val decoded = PythonRuntimeCodec.decodeCancellation(encoded)

        check(wireDocument.schemaId == PythonRuntimeContract.SCHEMA_CANCELLATION)
        check(wireDocument.schemaMajor == PythonRuntimeContract.SCHEMA_MAJOR)
        check(wireDocument.schemaMinor == PythonRuntimeContract.SCHEMA_MINOR)
        check(decoded == expected)

        return Result(
            schemaId = wireDocument.schemaId,
            encodedBytes = encoded.size,
            cancellation = decoded,
        )
    }

    data class Result(
        val schemaId: Int,
        val encodedBytes: Int,
        val cancellation: PythonExecutionCancellation,
    )
}
