package org.autojs.plugin.jvmsource.api

import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class JvmSourceCompatibilityTest {

    @Test
    fun negotiatesHighestProtocolInIntersection() {
        val provider = JvmSourceFixtures.providerInfo(
            min = JvmProtocolVersion(1, 0),
            max = JvmProtocolVersion(1, 4),
        )

        assertEquals(
            JvmProtocolVersion(1, 2),
            JvmSourceNegotiation.negotiate(
                provider,
                hostVersionCode = JvmSourceFixtures.HOST_VERSION_CODE,
                hostMin = JvmProtocolVersion(1, 1),
                hostMax = JvmProtocolVersion(1, 2),
            ),
        )
    }

    @Test
    fun rejectsDisjointProtocolRanges() {
        val provider = JvmSourceFixtures.providerInfo(
            min = JvmProtocolVersion(2, 0),
            max = JvmProtocolVersion(2, 1),
        )

        assertNull(JvmSourceNegotiation.negotiate(provider, JvmSourceFixtures.HOST_VERSION_CODE))
        assertEquals(
            JvmSourceContractViolation.PROTOCOL_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceNegotiation.requireNegotiated(provider, JvmSourceFixtures.HOST_VERSION_CODE)
            }.violation,
        )
    }

    @Test
    fun requiresPositiveCompatibleHostVersionCode() {
        val provider = JvmSourceFixtures.providerInfo().copy(
            minHostVersionCode = 5_300L,
            maxHostVersionCode = 5_400L,
        )

        assertEquals(
            JvmProtocolVersion(1, 0),
            JvmSourceNegotiation.negotiate(provider, 5_300L),
        )
        assertEquals(
            JvmProtocolVersion(1, 0),
            JvmSourceNegotiation.negotiate(provider, 5_400L),
        )
        assertNull(JvmSourceNegotiation.negotiate(provider, 5_299L))
        assertNull(JvmSourceNegotiation.negotiate(provider, 5_401L))
        assertEquals(
            JvmSourceContractViolation.INVALID_VALUE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceNegotiation.negotiate(provider, 0L)
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.PROTOCOL_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceNegotiation.requireNegotiated(provider, 5_299L)
            }.violation,
        )
    }

    @Test
    fun acceptsUnknownOptionalFieldFromNewerMinor() {
        val encoded = rawProviderInfo(schemaMinor = 9)
            .string(99, "future optional value")
            .encode()

        assertEquals("ecj-java", JvmSourceCodec.decodeProviderInfo(encoded).providerId)
    }

    @Test
    fun rejectsUnknownRequiredField() {
        val encoded = rawProviderInfo()
            .string(99, "future required value", requiredForReader = true)
            .encode()

        val failure = assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeProviderInfo(encoded)
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, failure.error)
    }

    @Test
    fun resultAcceptsFutureOptionalFieldAfterRequiredLoaderTelemetry() {
        val encoded = JvmSourceCodec.encodeResult(JvmSourceFixtures.result())
        val futureResult = TaggedWireWriter(
            JvmSourceContract.SCHEMA_RESULT,
            JvmSourceContract.SCHEMA_MAJOR,
            JvmSourceContract.SCHEMA_MINOR + 1,
        )
            .bytes(1, JvmSourceFixtures.requestId.toByteArray(), requiredForReader = true)
            .string(2, "null", requiredForReader = true)
            .int64(3, 1L, requiredForReader = true)
            .int64(4, 1L, requiredForReader = true)
            .int64(5, 2L, requiredForReader = true)
            .bytes(6, JvmSourceFixtures.capabilities().toolchainFingerprint.toByteArray(), requiredForReader = true)
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
            .string(22, JvmSourceFixtures.DEX_VERSION, requiredForReader = true)
            .int32(23, JvmSourceFixtures.loaderKind.wireCode, requiredForReader = true)
            .string(99, "future optional telemetry")
            .encode()

        assertEquals(JvmSourceCodec.decodeResult(encoded), JvmSourceCodec.decodeResult(futureResult))
    }

    @Test
    fun rejectsDifferentSchemaMajor() {
        val encoded = rawProviderInfo(schemaMajor = 2).encode()

        val failure = assertThrows(TaggedWireException::class.java) {
            JvmSourceCodec.decodeProviderInfo(encoded)
        }
        assertEquals(TaggedWireError.SCHEMA_MISMATCH, failure.error)
    }

    @Test
    fun validatesRequestAgainstNegotiatedCapabilities() {
        val javaOnly = JvmSourceFixtures.capabilities()
        JvmSourceValidation.validateRequestAgainst(
            JvmSourceFixtures.request(),
            javaOnly,
            JvmProtocolVersion(1, 0),
        )

        val failure = assertThrows(JvmSourceContractException::class.java) {
            JvmSourceValidation.validateRequestAgainst(
                JvmSourceFixtures.request(
                    language = JvmSourceLanguage.KOTLIN,
                    sourceFileName = "Main.kt",
                ),
                javaOnly,
                JvmProtocolVersion(1, 0),
            )
        }
        assertEquals(JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE, failure.violation)
    }

    @Test
    fun bindsRequestStartedAndResultToOneToolchainFingerprint() {
        val provider = JvmSourceFixtures.providerInfo()
        val capabilities = JvmSourceFixtures.capabilities()
        val request = JvmSourceFixtures.request()
        val started = JvmSourceStarted(
            requestId = request.requestId,
            language = request.language,
            providerId = provider.providerId,
            toolchainFingerprint = capabilities.toolchainFingerprint,
            compilerPid = JvmSourceFixtures.COMPILER_PID,
            compilerUid = JvmSourceFixtures.COMPILER_UID,
            compilerProcessName = JvmSourceFixtures.COMPILER_PROCESS_NAME,
        )
        val result = JvmSourceResult(
            requestId = request.requestId,
            toolchainFingerprint = capabilities.toolchainFingerprint,
            classArtifactSizeBytes = JvmSourceFixtures.classArtifactSizeBytes,
            classArtifactSha256 = JvmSourceFixtures.classArtifactSha256,
            dexArtifactSizeBytes = JvmSourceFixtures.dexArtifactSizeBytes,
            dexArtifactSha256 = JvmSourceFixtures.dexArtifactSha256,
            dexVersion = JvmSourceFixtures.DEX_VERSION,
            loaderKind = JvmSourceFixtures.loaderKind,
            resultJson = "null",
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
            compilationElapsedMillis = 1L,
            executionElapsedMillis = 1L,
            elapsedMillis = 2L,
        )

        JvmSourceValidation.validateRequestAgainst(request, capabilities, JvmProtocolVersion(1, 0))
        JvmSourceValidation.validateStartedAgainst(started, request, provider, capabilities)
        JvmSourceValidation.validateResultAgainst(result, request)

        val otherFingerprint = JvmSha256.digest(byteArrayOf(7))
        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateRequestAgainst(
                    request.copy(expectedToolchainFingerprint = otherFingerprint),
                    capabilities,
                    JvmProtocolVersion(1, 0),
                )
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateResultAgainst(
                    result.copy(stdoutSizeBytes = 1L),
                    request.copy(maxStdoutBytes = 0L),
                )
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateResultAgainst(
                    result.copy(stderrSizeBytes = 1L),
                    request.copy(maxStderrBytes = 0L),
                )
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateResultAgainst(
                    result.copy(toolchainFingerprint = otherFingerprint),
                    request,
                )
            }.violation,
        )
    }

    @Test
    fun freezesTheProtocolV1CombinedToolchainFingerprint() {
        val capabilities = JvmSourceFixtures.capabilities()
        assertEquals(
            "b222da2a0bab4a088243555f0a5aff4549c5dc6c196510a47800d05112064259",
            capabilities.toolchainFingerprint.toHexString(),
        )
        assertNotEquals(
            capabilities.toolchainFingerprint,
            JvmToolchainFingerprint.compute(
                language = JvmSourceLanguage.JAVA,
                sourceCompilerFamily = JvmSourceCompilerFamily.ECJ,
                sourceCompilerVersion = "3.42.1",
                d8Version = JvmSourceFixtures.D8_VERSION,
                runtimeLibraryFingerprint = JvmSourceFixtures.runtimeLibraryFingerprint,
            ),
        )
        assertNotEquals(
            capabilities.toolchainFingerprint,
            JvmToolchainFingerprint.compute(
                language = JvmSourceLanguage.JAVA,
                sourceCompilerFamily = JvmSourceCompilerFamily.ECJ,
                sourceCompilerVersion = JvmSourceFixtures.JAVA_COMPILER_VERSION,
                d8Version = "8.13.18",
                runtimeLibraryFingerprint = JvmSourceFixtures.runtimeLibraryFingerprint,
            ),
        )
        assertNotEquals(
            capabilities.toolchainFingerprint,
            JvmToolchainFingerprint.compute(
                language = JvmSourceLanguage.JAVA,
                sourceCompilerFamily = JvmSourceCompilerFamily.ECJ,
                sourceCompilerVersion = JvmSourceFixtures.JAVA_COMPILER_VERSION,
                d8Version = JvmSourceFixtures.D8_VERSION,
                runtimeLibraryFingerprint = JvmSha256.digest(byteArrayOf(1)),
            ),
        )
    }

    @Test
    fun freezesDexVersionAndLoaderCompatibilityProfiles() {
        assertEquals(setOf("035", "037"), JvmDexRuntimeProfile.admittedVersions(24))
        assertEquals(setOf("035", "037"), JvmDexRuntimeProfile.admittedVersions(25))
        assertEquals(setOf("035", "037", "038"), JvmDexRuntimeProfile.admittedVersions(26))
        assertEquals(setOf("035", "037", "038", "039"), JvmDexRuntimeProfile.admittedVersions(28))
        assertEquals(setOf("035", "037", "038", "039", "040"), JvmDexRuntimeProfile.admittedVersions(29))
        assertEquals(JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER, JvmDexRuntimeProfile.loaderKindForApi(24))
        assertEquals(JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER, JvmDexRuntimeProfile.loaderKindForApi(25))
        assertEquals(JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER, JvmDexRuntimeProfile.loaderKindForApi(26))
        assertNull(JvmDexRuntimeProfile.loaderKindForApi(23))
        assertEquals(1, JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER.wireCode)
        assertEquals("PRIVATE_DEX_CLASS_LOADER", JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER.wireName)
        assertEquals(2, JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER.wireCode)
        assertEquals("IN_MEMORY_DEX_CLASS_LOADER", JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER.wireName)

        val exposed = JvmDexRuntimeProfile.admittedVersions(29)
        assertThrows(UnsupportedOperationException::class.java) {
            (exposed as MutableSet<String>).add("041")
        }
        assertEquals(setOf("035", "037", "038", "039", "040"), JvmDexRuntimeProfile.admittedVersions(29))
    }

    @Test
    fun resultDexTelemetryMustMatchRequestAndActualHostRuntime() {
        val request24 = JvmSourceFixtures.request()
        val private037 = JvmSourceFixtures.result()
        JvmSourceValidation.validateResultAgainstRuntime(private037, request24, runtimeApi = 24)

        val inMemory037 = private037.copy(loaderKind = JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER)
        JvmSourceValidation.validateResultAgainstRuntime(inMemory037, request24, runtimeApi = 26)

        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateResultAgainst(
                    private037.copy(dexVersion = "038"),
                    request24,
                )
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateResultAgainstRuntime(private037, request24, runtimeApi = 26)
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.INVALID_VALUE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateResult(private037.copy(dexVersion = "041"))
            }.violation,
        )
    }

    private fun rawProviderInfo(
        schemaMajor: Int = JvmSourceContract.SCHEMA_MAJOR,
        schemaMinor: Int = JvmSourceContract.SCHEMA_MINOR,
    ): TaggedWireWriter = TaggedWireWriter(
        JvmSourceContract.SCHEMA_PROVIDER_INFO,
        schemaMajor,
        schemaMinor,
    )
        .int32(1, 1, requiredForReader = true)
        .int32(2, 0, requiredForReader = true)
        .int32(3, 1, requiredForReader = true)
        .int32(4, 0, requiredForReader = true)
        .string(5, "ecj-java", requiredForReader = true)
        .string(6, "0.1.0", requiredForReader = true)
        .int64(7, 1L, requiredForReader = true)
        .int32(8, JvmSourceContract.ENTRY_API_VERSION, requiredForReader = true)
}
