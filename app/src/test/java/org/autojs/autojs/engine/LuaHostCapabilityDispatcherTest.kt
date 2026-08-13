package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.lua.LuaRuntimeHostCapabilities
import org.autojs.autojs.script.LuaFileSource
import org.autojs.plugin.lua.runtime.api.LuaHostCallRequest
import org.autojs.plugin.lua.runtime.api.LuaHostErrorCode
import org.autojs.plugin.lua.runtime.api.LuaRequestId
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaSha256
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LuaHostCapabilityDispatcherTest {

    @Test
    fun grantedDeviceInfoReturnsBoundedWireMap() {
        val expected = basicInfo()
        val dispatcher = dispatcher(source = { expected })

        val outcome = dispatcher.dispatch(request("call-1"), descriptorCount = 0)

        assertTrue(outcome is LuaHostDispatchOutcome.Completed)
        assertEquals(expected, (outcome as LuaHostDispatchOutcome.Completed).result.value)
        assertEquals(EXECUTION_ID, outcome.result.executionId)
        assertEquals("call-1", outcome.result.callId)
    }

    @Test
    fun ungrantedCapabilityIsExplicitlyDenied() {
        var sourceCalled = false
        val dispatcher = LuaHostCapabilityDispatcher(EXECUTION_ID, emptyList()) {
            sourceCalled = true
            basicInfo()
        }

        val outcome = dispatcher.dispatch(request("call-denied"), descriptorCount = 0)

        assertEquals(
            LuaHostErrorCode.CAPABILITY_DENIED,
            (outcome as LuaHostDispatchOutcome.Failed).error.code,
        )
        assertFalse(sourceCalled)
    }

    @Test
    fun argumentsDescriptorsAndExecutionIdentityFailClosed() {
        val invalidArguments = dispatcher().dispatch(
            request("call-args", LuaValue.MapValue(mapOf("extra" to LuaValue.BooleanValue(true)))),
            descriptorCount = 0,
        )
        assertEquals(
            LuaHostErrorCode.INVALID_REQUEST,
            (invalidArguments as LuaHostDispatchOutcome.Failed).error.code,
        )

        assertThrows(IllegalArgumentException::class.java) {
            dispatcher().dispatch(request("call-fd"), descriptorCount = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher().dispatch(
                LuaHostCallRequest(
                    executionId = OTHER_EXECUTION_ID,
                    callId = "call-other",
                    capability = LuaRuntimeHostCapabilities.DEVICE_INFO,
                    arguments = EMPTY_ARGUMENTS,
                ),
                descriptorCount = 0,
            )
        }
    }

    @Test
    fun replayAndLifetimeQuotaAreRejectedForGrantedAndDeniedCalls() {
        val granted = dispatcher()
        granted.dispatch(request("repeat"), descriptorCount = 0)
        assertThrows(IllegalStateException::class.java) {
            granted.dispatch(request("repeat"), descriptorCount = 0)
        }

        val denied = LuaHostCapabilityDispatcher(EXECUTION_ID, emptyList()) { basicInfo() }
        repeat(LuaRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION) { index ->
            denied.dispatch(request("denied-$index"), descriptorCount = 0)
        }
        assertThrows(IllegalStateException::class.java) {
            denied.dispatch(request("denied-overflow"), descriptorCount = 0)
        }
    }

    @Test
    fun sourceFailureIsSanitizedAndDispatcherCloseIsIdempotent() {
        val failing = dispatcher(source = { error("secret device failure") })

        val outcome = failing.dispatch(request("call-failure"), descriptorCount = 0)

        val error = (outcome as LuaHostDispatchOutcome.Failed).error
        assertEquals(LuaHostErrorCode.INTERNAL, error.code)
        assertEquals("Host capability failed", error.message)
        assertTrue(failing.close())
        assertTrue(!failing.close())
        assertThrows(IllegalStateException::class.java) {
            failing.dispatch(request("after-close"), descriptorCount = 0)
        }
    }

    @Test
    fun grantedModuleSnapshotReturnsFrozenBytesAndExplicitMissingResult() {
        val directory = Files.createTempDirectory("lua-host-module").toFile()
        try {
            val entry = directory.resolve("main.lua").apply { writeText("return require('helper')") }
            val expected = "return 'frozen'".toByteArray()
            directory.resolve("main.modules").apply { mkdir() }.resolve("helper.lua").writeBytes(expected)
            val snapshot = LuaModuleSnapshot.create(LuaFileSource(entry))
            val dispatcher = LuaHostCapabilityDispatcher(
                executionId = EXECUTION_ID,
                grantedCapabilities = listOf(LuaRuntimeHostCapabilities.MODULE_SNAPSHOT),
                moduleSnapshot = snapshot,
            )

            val found = dispatcher.dispatch(moduleRequest("module-found", "helper"), descriptorCount = 0)
            val foundValue = (found as LuaHostDispatchOutcome.Completed).result.value as LuaValue.MapValue
            assertEquals(LuaValue.BooleanValue(true), foundValue.values["found"])
            assertArrayEquals(
                expected,
                (foundValue.values["source"] as LuaValue.BytesValue).toByteArray(),
            )
            assertArrayEquals(
                LuaSha256.digest(expected).toByteArray(),
                (foundValue.values["sha256"] as LuaValue.BytesValue).toByteArray(),
            )

            val missing = dispatcher.dispatch(moduleRequest("module-missing", "absent"), descriptorCount = 0)
            assertEquals(
                LuaValue.MapValue(mapOf("found" to LuaValue.BooleanValue(false))),
                (missing as LuaHostDispatchOutcome.Completed).result.value,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun moduleSnapshotRequiresGrantAndExactNameShape() {
        val denied = LuaHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = listOf(LuaRuntimeHostCapabilities.DEVICE_INFO),
        ).dispatch(moduleRequest("module-denied", "helper"), descriptorCount = 0)
        assertEquals(
            LuaHostErrorCode.CAPABILITY_DENIED,
            (denied as LuaHostDispatchOutcome.Failed).error.code,
        )

        listOf(
            EMPTY_ARGUMENTS,
            LuaValue.MapValue(mapOf("name" to LuaValue.BooleanValue(true))),
            LuaValue.MapValue(
                mapOf(
                    "name" to LuaValue.StringValue("helper"),
                    "extra" to LuaValue.BooleanValue(true),
                ),
            ),
            LuaValue.MapValue(mapOf("name" to LuaValue.StringValue("nested.helper"))),
            LuaValue.MapValue(mapOf("name" to LuaValue.StringValue("autojs"))),
        ).forEachIndexed { index, arguments ->
            val dispatcher = LuaHostCapabilityDispatcher(
                executionId = EXECUTION_ID,
                grantedCapabilities = listOf(LuaRuntimeHostCapabilities.MODULE_SNAPSHOT),
            )
            val outcome = dispatcher.dispatch(
                request = LuaHostCallRequest(
                    executionId = EXECUTION_ID,
                    callId = "invalid-module-$index",
                    capability = LuaRuntimeHostCapabilities.MODULE_SNAPSHOT,
                    arguments = arguments,
                ),
                descriptorCount = 0,
            )
            assertEquals(
                LuaHostErrorCode.INVALID_REQUEST,
                (outcome as LuaHostDispatchOutcome.Failed).error.code,
            )
        }
    }

    private fun dispatcher(
        source: LuaDeviceInfoSource = LuaDeviceInfoSource { basicInfo() },
    ) = LuaHostCapabilityDispatcher(
        executionId = EXECUTION_ID,
        grantedCapabilities = LuaRuntimeHostCapabilities.ENABLED,
        deviceInfoSource = source,
    )

    private fun request(
        callId: String,
        arguments: LuaValue = EMPTY_ARGUMENTS,
    ) = LuaHostCallRequest(
        executionId = EXECUTION_ID,
        callId = callId,
        capability = LuaRuntimeHostCapabilities.DEVICE_INFO,
        arguments = arguments,
    )

    private fun moduleRequest(callId: String, name: String) = LuaHostCallRequest(
        executionId = EXECUTION_ID,
        callId = callId,
        capability = LuaRuntimeHostCapabilities.MODULE_SNAPSHOT,
        arguments = LuaValue.MapValue(mapOf("name" to LuaValue.StringValue(name))),
    )

    private fun basicInfo() = LuaValue.MapValue(
        linkedMapOf(
            "brand" to LuaValue.StringValue("AutoJs"),
            "manufacturer" to LuaValue.StringValue("AutoJs"),
            "model" to LuaValue.StringValue("M3"),
            "device" to LuaValue.StringValue("lua"),
            "product" to LuaValue.StringValue("runtime"),
            "sdkInt" to LuaValue.Int64Value(36L),
        ),
    )

    private companion object {
        val EXECUTION_ID = LuaRequestId.fromBytes(ByteArray(16) { 1 })
        val OTHER_EXECUTION_ID = LuaRequestId.fromBytes(ByteArray(16) { 2 })
        val EMPTY_ARGUMENTS = LuaValue.MapValue(emptyMap())
    }
}
