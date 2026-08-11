package org.autojs.plugin.python.runtime.api

import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PythonRuntimeCompatibilityTest {
    @Test
    fun futureMinorUnknownOptionalFieldIsSkipped() {
        val encoded = cancellationWriter(schemaMajor = 1, schemaMinor = 1)
            .string(99, "future optional value")
            .encode()

        assertEquals(
            PythonCancellationReason.REQUESTED,
            PythonRuntimeCodec.decodeCancellation(encoded).reason,
        )
    }

    @Test
    fun unknownRequiredFieldAndIncompatibleMajorAreRejected() {
        val unknownRequired = cancellationWriter(schemaMajor = 1, schemaMinor = 1)
            .string(99, "future required value", requiredForReader = true)
            .encode()
        assertThrows(TaggedWireException::class.java) {
            PythonRuntimeCodec.decodeCancellation(unknownRequired)
        }

        val incompatible = cancellationWriter(schemaMajor = 2, schemaMinor = 0).encode()
        assertThrows(TaggedWireException::class.java) {
            PythonRuntimeCodec.decodeCancellation(incompatible)
        }
    }

    @Test
    fun duplicateScalarTruncationAndUnknownEnumAreRejected() {
        val duplicate = cancellationWriter(schemaMajor = 1, schemaMinor = 0)
            .int32(2, PythonCancellationReason.REQUESTED.wireCode)
            .encode()
        assertThrows(TaggedWireException::class.java) {
            PythonRuntimeCodec.decodeCancellation(duplicate)
        }

        val valid = PythonRuntimeCodec.encodeCancellation(
            PythonExecutionCancellation(
                PythonRuntimeFixtures.requestId,
                PythonCancellationReason.REQUESTED,
                PythonFailurePhase.CANCELLATION,
                1,
            ),
        )
        assertThrows(TaggedWireException::class.java) {
            PythonRuntimeCodec.decodeCancellation(valid.copyOf(valid.size - 1))
        }

        val unknownEnum = TaggedWireWriter(PythonRuntimeContract.SCHEMA_CANCELLATION, 1, 0)
            .bytes(1, PythonRuntimeFixtures.requestId.toByteArray())
            .int32(2, 999)
            .int32(3, PythonFailurePhase.CANCELLATION.wireCode)
            .int64(4, 1)
            .encode()
        val error = assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeCodec.decodeCancellation(unknownEnum)
        }
        assertEquals(PythonRuntimeContractViolation.UNKNOWN_ENUM, error.violation)
    }

    @Test
    fun malformedUtf8IsRejectedBeforeSemanticDispatch() {
        val encoded = PythonRuntimeCodec.encodeRuntimeInfo(PythonRuntimeFixtures.runtimeInfo)
        val providerId = PythonRuntimeFixtures.runtimeInfo.providerId.toByteArray()
        val offset = encoded.indexOf(providerId)
        require(offset >= 0)
        encoded[offset] = 0x80.toByte()

        assertThrows(TaggedWireException::class.java) {
            PythonRuntimeCodec.decodeRuntimeInfo(encoded)
        }
    }

    private fun cancellationWriter(schemaMajor: Int, schemaMinor: Int) = TaggedWireWriter(
        PythonRuntimeContract.SCHEMA_CANCELLATION,
        schemaMajor,
        schemaMinor,
    )
        .bytes(1, PythonRuntimeFixtures.requestId.toByteArray())
        .int32(2, PythonCancellationReason.REQUESTED.wireCode)
        .int32(3, PythonFailurePhase.CANCELLATION.wireCode)
        .int64(4, 10)

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        for (start in 0..size - needle.size) {
            if (needle.indices.all { index -> this[start + index] == needle[index] }) return start
        }
        return -1
    }
}
