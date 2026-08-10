package org.autojs.plugin.lua.runtime.api

import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LuaRuntimeCompatibilityTest {
    @Test
    fun negotiationSelectsHighestCommonVersion() {
        val provider = LuaRuntimeFixtures.runtimeInfo(
            protocolMin = LuaProtocolVersion(1, 0),
            protocolMax = LuaProtocolVersion(1, 4),
        )

        assertEquals(
            LuaProtocolVersion(1, 2),
            LuaRuntimeNegotiation.negotiate(
                provider,
                hostMin = LuaProtocolVersion(1, 1),
                hostMax = LuaProtocolVersion(1, 2),
            ),
        )
    }

    @Test
    fun negotiationRejectsDisjointMajorVersions() {
        val provider = LuaRuntimeFixtures.runtimeInfo(
            protocolMin = LuaProtocolVersion(2, 0),
            protocolMax = LuaProtocolVersion(2, 0),
        )

        assertNull(LuaRuntimeNegotiation.negotiate(provider))
        assertThrows(LuaContractException::class.java) {
            LuaRuntimeNegotiation.requireNegotiated(provider)
        }
    }

    @Test
    fun requestValidationRejectsVersionOutsideProviderRange() {
        val provider = LuaRuntimeFixtures.runtimeInfo(
            protocolMin = LuaProtocolVersion(2, 0),
            protocolMax = LuaProtocolVersion(2, 0),
        )

        assertThrows(LuaContractException::class.java) {
            LuaRuntimeValidation.validateRequestAgainst(
                LuaRuntimeFixtures.request(),
                provider,
                LuaProtocolVersion(1, 0),
            )
        }
        assertThrows(LuaContractException::class.java) {
            LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), provider)
        }

        val futureProvider = LuaRuntimeFixtures.runtimeInfo(
            protocolMin = LuaProtocolVersion(99, 0),
            protocolMax = LuaProtocolVersion(99, 0),
        )
        val futureRequest = LuaRuntimeFixtures.request(protocolVersion = LuaProtocolVersion(99, 0))
        assertThrows(LuaContractException::class.java) {
            LuaExecutionSessionPolicy(futureRequest, futureProvider)
        }
    }

    @Test
    fun requestMustFitProviderLimitsAndCapabilities() {
        val request = LuaRuntimeFixtures.request(requiredCapabilities = listOf("console.log", "device.info"))
        val provider = LuaRuntimeFixtures.runtimeInfo(capabilities = listOf("console.log"))

        val error = assertThrows(LuaContractException::class.java) {
            LuaRuntimeValidation.validateRequestAgainst(request, provider, LuaProtocolVersion(1, 0))
        }
        assertEquals(LuaContractViolation.CAPABILITY_INCOMPATIBLE, error.violation)
    }

    @Test
    fun activeProcessAbiMustBelongToPackagedInventory() {
        val provider = LuaRuntimeFixtures.runtimeInfo(
            processAbi = "x86",
            supportedAbis = listOf("arm64-v8a", "x86_64"),
        )

        assertThrows(LuaContractException::class.java) {
            LuaRuntimeValidation.validateRuntimeInfo(provider)
        }

        val tooMany = (0..LuaRuntimeContract.MAX_SUPPORTED_ABIS).map { "abi-$it" }
        assertThrows(LuaContractException::class.java) {
            LuaRuntimeValidation.validateRuntimeInfo(
                LuaRuntimeFixtures.runtimeInfo(
                    processAbi = tooMany.first(),
                    supportedAbis = tooMany,
                ),
            )
        }
    }

    @Test
    fun runtimeVersionTextMustBeSafeForDiagnostics() {
        listOf("", "   ", "5.4.8\nforged").forEach { version ->
            assertThrows(LuaContractException::class.java) {
                LuaRuntimeValidation.validateRuntimeInfo(
                    LuaRuntimeFixtures.runtimeInfo(languageVersion = version),
                )
            }
        }
        assertThrows(LuaContractException::class.java) {
            LuaRuntimeValidation.validateRuntimeInfo(
                LuaRuntimeFixtures.runtimeInfo(providerVersionName = "1.0\tforged"),
            )
        }
    }

    @Test
    fun futureOptionalOutputFieldIsSkipped() {
        val output = TaggedWireWriter(LuaRuntimeContract.SCHEMA_OUTPUT, 1, 1)
            .bytes(1, LuaRuntimeFixtures.REQUEST_ID.toByteArray(), requiredForReader = true)
            .int64(2, 7L, requiredForReader = true)
            .int32(3, LuaOutputStream.STDOUT.wireCode, requiredForReader = true)
            .string(4, "ok", requiredForReader = true)
            .string(99, "future optional")
            .encode()

        assertEquals("ok", LuaRuntimeCodec.decodeOutput(output).text)
    }

    @Test
    fun unknownRequiredOutputFieldIsRejected() {
        val output = TaggedWireWriter(LuaRuntimeContract.SCHEMA_OUTPUT, 1, 1)
            .bytes(1, LuaRuntimeFixtures.REQUEST_ID.toByteArray(), requiredForReader = true)
            .int64(2, 7L, requiredForReader = true)
            .int32(3, LuaOutputStream.STDOUT.wireCode, requiredForReader = true)
            .string(4, "ok", requiredForReader = true)
            .string(99, "future required", requiredForReader = true)
            .encode()

        val error = assertThrows(TaggedWireException::class.java) { LuaRuntimeCodec.decodeOutput(output) }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, error.error)
    }

    @Test
    fun duplicateScalarAndUnknownEnumAreRejected() {
        val duplicate = TaggedWireWriter(LuaRuntimeContract.SCHEMA_OUTPUT, 1, 0)
            .bytes(1, LuaRuntimeFixtures.REQUEST_ID.toByteArray(), requiredForReader = true)
            .int64(2, 7L, requiredForReader = true)
            .int64(2, 8L, requiredForReader = true)
            .int32(3, LuaOutputStream.STDOUT.wireCode, requiredForReader = true)
            .string(4, "ok", requiredForReader = true)
            .encode()
        assertEquals(
            TaggedWireError.DUPLICATE_FIELD,
            assertThrows(TaggedWireException::class.java) { LuaRuntimeCodec.decodeOutput(duplicate) }.error,
        )

        val unknownEnum = TaggedWireWriter(LuaRuntimeContract.SCHEMA_OUTPUT, 1, 0)
            .bytes(1, LuaRuntimeFixtures.REQUEST_ID.toByteArray(), requiredForReader = true)
            .int64(2, 7L, requiredForReader = true)
            .int32(3, 999, requiredForReader = true)
            .string(4, "ok", requiredForReader = true)
            .encode()
        assertEquals(
            LuaContractViolation.UNKNOWN_ENUM,
            assertThrows(LuaContractException::class.java) { LuaRuntimeCodec.decodeOutput(unknownEnum) }.violation,
        )
    }

    @Test
    fun sourceNameCannotCarryPathOrUriAuthority() {
        val base = LuaRuntimeFixtures.request()
        listOf("/data/user/0/secret.lua", "C:\\secret.lua", "content://authority/script.lua", "../secret.lua")
            .forEach { sourceName ->
                val request = LuaExecutionRequest(
                    requestId = base.requestId,
                    protocolVersion = base.protocolVersion,
                    sourceName = sourceName,
                    sourceLengthBytes = base.sourceLengthBytes,
                    sourceSha256 = base.sourceSha256,
                    arguments = base.arguments,
                    requiredCapabilities = base.requiredCapabilities,
                    outputByteLimit = base.outputByteLimit,
                    memoryByteLimit = base.memoryByteLimit,
                    timeoutMillis = base.timeoutMillis,
                )
                assertThrows(LuaContractException::class.java) { LuaRuntimeValidation.validateRequest(request) }
            }
    }

    @Test
    fun v1CompletionDescriptorsFailClosed() {
        LuaRuntimeValidation.validateV1DescriptorCount(0)
        assertThrows(LuaContractException::class.java) { LuaRuntimeValidation.validateV1DescriptorCount(1) }
    }
}
