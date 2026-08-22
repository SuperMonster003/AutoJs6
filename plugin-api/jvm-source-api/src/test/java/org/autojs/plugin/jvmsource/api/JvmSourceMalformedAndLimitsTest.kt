package org.autojs.plugin.jvmsource.api

import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceMalformedAndLimitsTest {

    @Test
    fun rejectsTruncatedWireDocument() {
        val encoded = JvmSourceCodec.encodeRequest(JvmSourceFixtures.request())

        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeRequest(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test
    fun rejectsDuplicateSingularField() {
        val encoded = rawRequest()
            .string(5, "Duplicate.java", requiredForReader = true)
            .encode()

        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeRequest(encoded)
        }
    }

    @Test
    fun rejectsMalformedSourceHash() {
        val encoded = rawRequest(
            sourceSha256 = ByteArray(JvmSha256.BYTE_COUNT - 1),
        ).encode()

        assertEquals(
            JvmSourceContractViolation.INVALID_VALUE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceCodec.decodeRequest(encoded)
            }.violation,
        )
    }

    @Test
    fun requiresToolchainFingerprintOnRequestStartedAndResult() {
        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeRequest(rawRequest(includeToolchainFingerprint = false).encode())
        }

        val startedWithoutFingerprint = TaggedWireWriter(
            JvmSourceContract.SCHEMA_STARTED,
            JvmSourceContract.SCHEMA_MAJOR,
            JvmSourceContract.SCHEMA_MINOR,
        )
            .bytes(1, JvmSourceFixtures.requestId.toByteArray(), requiredForReader = true)
            .string(2, JvmSourceLanguage.JAVA.wireName, requiredForReader = true)
            .string(3, "ecj-java", requiredForReader = true)
            .encode()
        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeStarted(startedWithoutFingerprint)
        }

        val resultWithoutFingerprint = TaggedWireWriter(
            JvmSourceContract.SCHEMA_RESULT,
            JvmSourceContract.SCHEMA_MAJOR,
            JvmSourceContract.SCHEMA_MINOR,
        )
            .bytes(1, JvmSourceFixtures.requestId.toByteArray(), requiredForReader = true)
            .string(2, "null", requiredForReader = true)
            .int64(3, 0L, requiredForReader = true)
            .int64(4, 0L, requiredForReader = true)
            .int64(5, 0L, requiredForReader = true)
            .encode()
        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeResult(resultWithoutFingerprint)
        }
    }

    @Test
    fun requiresDexVersionAndLoaderKindOnEverySuccessfulResult() {
        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeResult(rawResult(includeDexVersion = false).encode())
        }
        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeResult(rawResult(includeLoaderKind = false).encode())
        }
    }

    @Test
    fun rejectsUnknownLoaderKindAndUnsupportedDexContainerVersion() {
        assertEquals(
            JvmSourceContractViolation.UNKNOWN_ENUM,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceCodec.decodeResult(rawResult(loaderKindWireCode = 99).encode())
            }.violation,
        )
        listOf("034", "036", "041", "40", "040 ").forEach { version ->
            assertEquals(
                JvmSourceContractViolation.INVALID_VALUE,
                assertThrows(JvmSourceContractException::class.java) {
                    JvmSourceCodec.encodeResult(JvmSourceFixtures.result(dexVersion = version))
                }.violation,
            )
        }
    }

    @Test
    fun rejectsSourceTimeoutAndResultAboveProtocolLimits() {
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeRequest(
                JvmSourceFixtures.request(
                    sourceSizeBytes = JvmSourceContract.MAX_SOURCE_BYTES + 1L,
                ),
            )
        }
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeRequest(
                JvmSourceFixtures.request(
                    timeoutMillis = JvmSourceContract.MAX_TIMEOUT_MILLIS + 1L,
                ),
            )
        }
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeResult(
                JvmSourceResult(
                    JvmSourceFixtures.requestId,
                    toolchainFingerprint = JvmSourceFixtures.capabilities().toolchainFingerprint,
                    classArtifactSizeBytes = JvmSourceFixtures.classArtifactSizeBytes,
                    classArtifactSha256 = JvmSourceFixtures.classArtifactSha256,
                    dexArtifactSizeBytes = JvmSourceFixtures.dexArtifactSizeBytes,
                    dexArtifactSha256 = JvmSourceFixtures.dexArtifactSha256,
                    dexVersion = JvmSourceFixtures.DEX_VERSION,
                    loaderKind = JvmSourceFixtures.loaderKind,
                    resultJson = "x".repeat(JvmSourceContract.MAX_RESULT_JSON_BYTES + 1),
                    stdoutSizeBytes = 0L,
                    stdoutSha256 = JvmSourceFixtures.emptyOutputSha256,
                    stderrSizeBytes = 0L,
                    stderrSha256 = JvmSourceFixtures.emptyOutputSha256,
                    workerPid = JvmSourceFixtures.WORKER_PID,
                    workerUid = JvmSourceFixtures.WORKER_UID,
                    workerProcessName = JvmSourceFixtures.WORKER_PROCESS_NAME,
                    workerGeneration = JvmSourceFixtures.WORKER_GENERATION,
                    internalBridgeCallerPid = JvmSourceFixtures.WORKER_PID,
                    internalBridgeCallerUid = JvmSourceFixtures.WORKER_UID,
                    internalBridgeCallCount = 1,
                    compilationElapsedMillis = 0L,
                    executionElapsedMillis = 0L,
                    elapsedMillis = 0L,
                ),
            )
        }
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeResult(
                result("null").copy(stdoutSizeBytes = JvmSourceContract.MAX_STDOUT_BYTES + 1L),
            )
        }
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeResult(
                result("null").copy(
                    dexArtifactSizeBytes = JvmSourceContract.MAX_DEX_ARTIFACT_BYTES + 1L,
                ),
            )
        }
    }

    @Test
    fun rejectsMetadataAboveBinderEnvelopeLimit() {
        assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeProviderInfo(
                ByteArray(JvmSourceContract.MAX_METADATA_BYTES + 1),
            )
        }
    }

    @Test
    fun kotlinScriptExtensionIsNotSupported() {
        assertNull(JvmSourceLanguage.fromExtension("kts"))
        assertNull(JvmSourceLanguage.fromFileName("Main.kts"))

        assertEquals(
            JvmSourceContractViolation.INVALID_VALUE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceCodec.encodeRequest(
                    JvmSourceFixtures.request(
                        language = JvmSourceLanguage.KOTLIN,
                        sourceFileName = "Main.kts",
                    ),
                )
            }.violation,
        )
    }

    @Test
    fun requiresDisposableWorkerIsolationAndSingleSession() {
        val withoutHardKill = JvmSourceFixtures.capabilities().copy(
            isolationCapabilities = listOf(
                JvmIsolationCapability.COMPILER_AND_WORKER_SEPARATED,
                JvmIsolationCapability.FRESH_WORKER_PER_EXECUTION,
            ),
        )
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeCapabilities(withoutHardKill)
        }

        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeCapabilities(
                JvmSourceFixtures.capabilities().copy(maxConcurrentSessions = 2),
            )
        }
    }

    @Test
    fun rejectsMalformedOrExcessivelyNestedJsonValues() {
        val malformedValues = listOf(
            "",
            "{",
            "[1,]",
            "{\"key\" 1}",
            "01",
            "1.",
            "true false",
            "\"\\x\"",
            "\"\\uD800\"",
        )
        malformedValues.forEach { malformed ->
            assertEquals(
                JvmSourceContractViolation.INVALID_VALUE,
                assertThrows(JvmSourceContractException::class.java) {
                    JvmSourceCodec.encodeResult(result(resultJson = malformed))
                }.violation,
            )
        }

        val atDepthLimit = "[".repeat(JvmSourceContract.MAX_JSON_DEPTH) +
            "0" + "]".repeat(JvmSourceContract.MAX_JSON_DEPTH)
        assertEquals(
            atDepthLimit,
            JvmSourceCodec.decodeResult(
                JvmSourceCodec.encodeResult(result(resultJson = atDepthLimit)),
            ).resultJson,
        )

        val tooDeep = "[".repeat(JvmSourceContract.MAX_JSON_DEPTH + 1) +
            "0" + "]".repeat(JvmSourceContract.MAX_JSON_DEPTH + 1)
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeResult(result(resultJson = tooDeep))
        }
    }

    @Test
    fun validatesEveryJsonBearingMessage() {
        val valid = " {\"emoji\":\"\\uD83D\\uDE00\",\"number\":-1.2e+3,\"values\":[true,false,null]} "
        assertEquals(valid, JvmSourceCodec.decodeResult(JvmSourceCodec.encodeResult(result(valid))).resultJson)

        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeHostCall(
                JvmHostCall(
                    JvmSourceFixtures.requestId,
                    callId = 1L,
                    method = "app.launch",
                    payloadJson = "{]",
                    timeoutMillis = 1_000L,
                ),
            )
        }
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeHostResponse(
                JvmHostResponse(
                    JvmSourceFixtures.requestId,
                    callId = 1L,
                    succeeded = true,
                    payloadJson = "[1,]",
                ),
            )
        }
    }

    @Test
    fun requiresOneLanguageAndAConsistentToolchainIdentity() {
        val capabilities = JvmSourceFixtures.capabilities()
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeCapabilities(
                capabilities.copy(
                    languages = listOf(JvmSourceLanguage.JAVA, JvmSourceLanguage.KOTLIN),
                ),
            )
        }
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeCapabilities(
                capabilities.copy(sourceCompilerFamily = JvmSourceCompilerFamily.KOTLIN_JVM),
            )
        }
        assertThrows(JvmSourceContractException::class.java) {
            JvmSourceCodec.encodeCapabilities(
                capabilities.copy(toolchainFingerprint = JvmSha256.digest(byteArrayOf(9))),
            )
        }
    }

    @Test
    fun defensivelyCopiesProtocolCollections() {
        val languages = mutableListOf(JvmSourceLanguage.JAVA)
        val isolation = JvmIsolationCapability.entries.toMutableList()
        val capabilities = JvmSourceFixtures.capabilities().copy(
            languages = languages,
            isolationCapabilities = isolation,
        )
        languages.clear()
        isolation.clear()
        assertEquals(listOf(JvmSourceLanguage.JAVA), capabilities.languages)
        assertEquals(JvmIsolationCapability.entries, capabilities.isolationCapabilities)
        assertThrows(UnsupportedOperationException::class.java) {
            (capabilities.languages as MutableList<JvmSourceLanguage>).clear()
        }

        val hostCalls = mutableListOf("app.launch")
        val request = JvmSourceFixtures.request().copy(allowedHostCalls = hostCalls)
        hostCalls.clear()
        assertEquals(listOf("app.launch"), request.allowedHostCalls)
        assertThrows(UnsupportedOperationException::class.java) {
            (request.allowedHostCalls as MutableList<String>).clear()
        }
        assertTrue(request.allowedHostCalls.isNotEmpty())
    }

    private fun rawRequest(
        sourceSha256: ByteArray = JvmSourceFixtures.sourceSha256.toByteArray(),
        includeToolchainFingerprint: Boolean = true,
    ): TaggedWireWriter {
        val writer = TaggedWireWriter(
            JvmSourceContract.SCHEMA_REQUEST,
            JvmSourceContract.SCHEMA_MAJOR,
            JvmSourceContract.SCHEMA_MINOR,
        )
            .bytes(1, JvmSourceFixtures.requestId.toByteArray(), requiredForReader = true)
            .int32(2, 1, requiredForReader = true)
            .int32(3, 0, requiredForReader = true)
            .string(4, JvmSourceLanguage.JAVA.wireName, requiredForReader = true)
            .string(5, "Main.java", requiredForReader = true)
            .int64(6, 4_096L, requiredForReader = true)
            .bytes(7, sourceSha256, requiredForReader = true)
            .string(8, "Main", requiredForReader = true)
            .int32(9, JvmSourceContract.MIN_ANDROID_API, requiredForReader = true)
            .int64(10, JvmSourceContract.DEFAULT_TIMEOUT_MILLIS, requiredForReader = true)
            .int64(11, JvmSourceContract.MAX_STDOUT_BYTES, requiredForReader = true)
            .int64(12, JvmSourceContract.MAX_STDERR_BYTES, requiredForReader = true)
            .int32(13, JvmSourceContract.MAX_DIAGNOSTIC_BYTES, requiredForReader = true)
            .string(14, "app.launch")
        if (includeToolchainFingerprint) {
            writer.bytes(
                15,
                JvmSourceFixtures.capabilities().toolchainFingerprint.toByteArray(),
                requiredForReader = true,
            )
        }
        return writer
    }

    private fun rawResult(
        includeDexVersion: Boolean = true,
        includeLoaderKind: Boolean = true,
        loaderKindWireCode: Int = JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER.wireCode,
    ): TaggedWireWriter {
        val writer = TaggedWireWriter(
            JvmSourceContract.SCHEMA_RESULT,
            JvmSourceContract.SCHEMA_MAJOR,
            JvmSourceContract.SCHEMA_MINOR,
        )
            .bytes(1, JvmSourceFixtures.requestId.toByteArray(), requiredForReader = true)
            .string(2, "null", requiredForReader = true)
            .int64(3, 1L, requiredForReader = true)
            .int64(4, 1L, requiredForReader = true)
            .int64(5, 2L, requiredForReader = true)
            .bytes(
                6,
                JvmSourceFixtures.capabilities().toolchainFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .bytes(7, JvmSourceFixtures.classArtifactSha256.toByteArray(), requiredForReader = true)
            .bytes(8, JvmSourceFixtures.dexArtifactSha256.toByteArray(), requiredForReader = true)
            .int64(9, 0L, requiredForReader = true)
            .bytes(10, JvmSourceFixtures.emptyOutputSha256.toByteArray(), requiredForReader = true)
            .int64(11, 0L, requiredForReader = true)
            .bytes(12, JvmSourceFixtures.emptyOutputSha256.toByteArray(), requiredForReader = true)
            .int64(13, JvmSourceFixtures.classArtifactSizeBytes, requiredForReader = true)
            .int64(14, JvmSourceFixtures.dexArtifactSizeBytes, requiredForReader = true)
            .int32(15, JvmSourceFixtures.WORKER_PID, requiredForReader = true)
            .int32(16, JvmSourceFixtures.WORKER_UID, requiredForReader = true)
            .string(17, JvmSourceFixtures.WORKER_PROCESS_NAME, requiredForReader = true)
            .int64(18, JvmSourceFixtures.WORKER_GENERATION, requiredForReader = true)
            .int32(19, JvmSourceFixtures.WORKER_PID, requiredForReader = true)
            .int32(20, JvmSourceFixtures.WORKER_UID, requiredForReader = true)
            .int32(21, 1, requiredForReader = true)
        if (includeDexVersion) writer.string(22, JvmSourceFixtures.DEX_VERSION, requiredForReader = true)
        if (includeLoaderKind) writer.int32(23, loaderKindWireCode, requiredForReader = true)
        return writer
    }

    private fun result(resultJson: String) = JvmSourceResult(
        requestId = JvmSourceFixtures.requestId,
        toolchainFingerprint = JvmSourceFixtures.capabilities().toolchainFingerprint,
        classArtifactSizeBytes = JvmSourceFixtures.classArtifactSizeBytes,
        classArtifactSha256 = JvmSourceFixtures.classArtifactSha256,
        dexArtifactSizeBytes = JvmSourceFixtures.dexArtifactSizeBytes,
        dexArtifactSha256 = JvmSourceFixtures.dexArtifactSha256,
        dexVersion = JvmSourceFixtures.DEX_VERSION,
        loaderKind = JvmSourceFixtures.loaderKind,
        resultJson = resultJson,
        stdoutSizeBytes = 0L,
        stdoutSha256 = JvmSourceFixtures.emptyOutputSha256,
        stderrSizeBytes = 0L,
        stderrSha256 = JvmSourceFixtures.emptyOutputSha256,
        workerPid = JvmSourceFixtures.WORKER_PID,
        workerUid = JvmSourceFixtures.WORKER_UID,
        workerProcessName = JvmSourceFixtures.WORKER_PROCESS_NAME,
        workerGeneration = JvmSourceFixtures.WORKER_GENERATION,
        internalBridgeCallerPid = JvmSourceFixtures.WORKER_PID,
        internalBridgeCallerUid = JvmSourceFixtures.WORKER_UID,
        internalBridgeCallCount = 1,
        compilationElapsedMillis = 0L,
        executionElapsedMillis = 0L,
        elapsedMillis = 0L,
    )
}
