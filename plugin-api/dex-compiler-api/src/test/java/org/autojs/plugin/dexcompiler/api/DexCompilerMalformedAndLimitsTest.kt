package org.autojs.plugin.dexcompiler.api

import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DexCompilerMalformedAndLimitsTest {

    @Test
    fun rejectsTruncatedWireDocument() {
        val encoded = DexCompilerCodec.encodeRequest(DexCompilerFixtures.request())

        assertThrows(TaggedWireException::class.java) {
            DexCompilerCodec.decodeRequest(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test
    fun rejectsDuplicateSingularField() {
        val writer = rawRequest()
            .string(6, DexCompilerMode.RELEASE.wireName, requiredForReader = true)

        assertThrows(TaggedWireException::class.java) {
            DexCompilerCodec.decodeRequest(writer.encode())
        }
    }

    @Test
    fun rejectsMalformedFixedLengthHashes() {
        val encoded = rawRequest(programSha256 = ByteArray(DexSha256.BYTE_COUNT - 1)).encode()

        assertEquals(
            DexCompilerContractViolation.INVALID_VALUE,
            assertThrows(DexCompilerContractException::class.java) {
                DexCompilerCodec.decodeRequest(encoded)
            }.violation,
        )
    }

    @Test
    fun rejectsUnknownRequestEnum() {
        val encoded = rawRequest(mode = "optimize-and-shrink").encode()

        assertEquals(
            DexCompilerContractViolation.UNKNOWN_ENUM,
            assertThrows(DexCompilerContractException::class.java) {
                DexCompilerCodec.decodeRequest(encoded)
            }.violation,
        )
    }

    @Test
    fun rejectsRequestAboveProtocolOutputAndTimeoutLimits() {
        assertEquals(
            DexCompilerContractViolation.INVALID_VALUE,
            assertThrows(DexCompilerContractException::class.java) {
                DexCompilerValidation.validateRequest(
                    DexCompilerFixtures.request(maxOutputBytes = DexCompilerContract.MAX_OUTPUT_BYTES + 1L)
                )
            }.violation,
        )

        val request = DexCompilerFixtures.request().let { value ->
            value.copy(timeoutMillis = DexCompilerContract.MAX_TIMEOUT_MILLIS + 1L)
        }
        assertThrows(DexCompilerContractException::class.java) {
            DexCompilerCodec.encodeRequest(request)
        }
    }

    @Test
    fun rejectsRuntimeLibraryFingerprintMismatch() {
        val wrongFingerprint = DexCompilerFixtures.sha256("wrong")

        assertEquals(
            DexCompilerContractViolation.INVALID_VALUE,
            assertThrows(DexCompilerContractException::class.java) {
                DexCompilerCodec.encodeCapabilities(
                    DexCompilerFixtures.capabilities(runtimeFingerprintOverride = wrongFingerprint)
                )
            }.violation,
        )
    }

    @Test
    fun rejectsPartialOrOutOfRangeProgress() {
        val partial = DexCompileProgress(
            DexCompilerFixtures.requestId,
            sequence = 1L,
            stage = DexCompilerProgressStage.COMPILING,
            current = 1L,
            total = null,
        )
        assertThrows(DexCompilerContractException::class.java) {
            DexCompilerCodec.encodeProgress(partial)
        }

        val outOfRange = partial.copy(current = 11L, total = 10L)
        assertThrows(DexCompilerContractException::class.java) {
            DexCompilerCodec.encodeProgress(outOfRange)
        }
    }

    @Test
    fun rejectsOversizedErrorAndDiagnosticText() {
        val oversizedError = DexCompileError(
            requestId = DexCompilerFixtures.requestId,
            code = DexCompilerErrorCode.INTERNAL,
            phase = DexCompilerFailurePhase.CLEANUP,
            message = "x".repeat(DexCompilerContract.MAX_ERROR_MESSAGE_CODE_POINTS + 1),
            retryable = false,
        )
        assertThrows(DexCompilerContractException::class.java) {
            DexCompilerCodec.encodeError(oversizedError)
        }

        val oversizedDiagnostic = DexCompilerDiagnostic(
            DexCompilerDiagnosticSeverity.ERROR,
            "D8_ERROR",
            "x".repeat(DexCompilerContract.MAX_DIAGNOSTIC_MESSAGE_CODE_POINTS + 1),
        )
        assertThrows(DexCompilerContractException::class.java) {
            DexCompilerValidation.validateDiagnostics(listOf(oversizedDiagnostic))
        }
    }

    @Test
    fun rejectsMetadataAboveBinderEnvelopeLimit() {
        assertThrows(TaggedWireException::class.java) {
            DexCompilerCodec.decodeInfo(ByteArray(DexCompilerContract.MAX_METADATA_BYTES + 1))
        }
    }

    private fun rawRequest(
        mode: String = DexCompilerMode.RELEASE.wireName,
        programSha256: ByteArray = DexCompilerFixtures.programSha256.toByteArray(),
    ): TaggedWireWriter = TaggedWireWriter(
        DexCompilerContract.SCHEMA_COMPILE_REQUEST,
        DexCompilerContract.SCHEMA_MAJOR,
        DexCompilerContract.SCHEMA_MINOR,
    )
        .bytes(1, DexCompilerFixtures.requestId.toByteArray(), requiredForReader = true)
        .int32(2, 1, requiredForReader = true)
        .int32(3, 0, requiredForReader = true)
        .string(4, DexCompilerInputFormat.JAR.wireName, requiredForReader = true)
        .string(5, DexCompilerOutputFormat.DEX_ZIP.wireName, requiredForReader = true)
        .string(6, mode, requiredForReader = true)
        .int32(7, 24, requiredForReader = true)
        .int64(8, 4_096L, requiredForReader = true)
        .bytes(9, programSha256, requiredForReader = true)
        .int64(10, 16L * 1024L * 1024L, requiredForReader = true)
        .int32(11, DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1.wireCode, requiredForReader = true)
        .bytes(12, DexCompilerFixtures.runtimeFingerprint.toByteArray(), requiredForReader = true)
        .int32(13, DexCompilerContract.CANONICALIZATION_POLICY_VERSION, requiredForReader = true)
        .int32(14, DexCompilerContract.MAX_DIAGNOSTIC_BYTES, requiredForReader = true)
        .int64(15, DexCompilerContract.DEFAULT_TIMEOUT_MILLIS, requiredForReader = true)
}
