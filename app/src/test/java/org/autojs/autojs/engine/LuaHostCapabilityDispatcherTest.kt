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
import java.util.concurrent.ConcurrentHashMap

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

    @Test
    fun storageRoundTripsFixedShapesWithinOneOpaquePrincipal() {
        val storage = FakeHostStorage()
        val principal = LuaStoragePrincipal.fromStableIdentity("file:/project/main.lua")
        val dispatcher = storageDispatcher(storage, principal)
        val expected = LuaValue.MapValue(
            mapOf(
                "count" to LuaValue.Int64Value(7),
                "items" to LuaValue.ArrayValue(listOf(LuaValue.StringValue("one"))),
            ),
        )

        val put = dispatcher.dispatch(
            storageRequest(
                "storage-put",
                linkedMapOf(
                    "op" to LuaValue.StringValue("put"),
                    "key" to LuaValue.StringValue("counter.v1"),
                    "value" to expected,
                ),
            ),
            descriptorCount = 0,
        )
        assertEquals(
            LuaValue.MapValue(mapOf("stored" to LuaValue.BooleanValue(true))),
            (put as LuaHostDispatchOutcome.Completed).result.value,
        )

        val found = dispatcher.dispatch(
            storageRequest("storage-get", mapOf("op" to string("get"), "key" to string("counter.v1"))),
            descriptorCount = 0,
        )
        assertEquals(
            LuaValue.MapValue(
                linkedMapOf("found" to LuaValue.BooleanValue(true), "value" to expected),
            ),
            (found as LuaHostDispatchOutcome.Completed).result.value,
        )

        val removed = dispatcher.dispatch(
            storageRequest("storage-remove", mapOf("op" to string("remove"), "key" to string("counter.v1"))),
            descriptorCount = 0,
        )
        assertEquals(
            LuaValue.MapValue(mapOf("removed" to LuaValue.BooleanValue(true))),
            (removed as LuaHostDispatchOutcome.Completed).result.value,
        )

        val missing = dispatcher.dispatch(
            storageRequest("storage-missing", mapOf("op" to string("get"), "key" to string("counter.v1"))),
            descriptorCount = 0,
        )
        assertEquals(
            LuaValue.MapValue(mapOf("found" to LuaValue.BooleanValue(false))),
            (missing as LuaHostDispatchOutcome.Completed).result.value,
        )
        assertEquals(listOf("put", "get", "remove", "get"), storage.operations)
    }

    @Test
    fun storageDenialInvalidShapesAndMissingPrincipalNeverTouchPersistence() {
        val storage = FakeHostStorage()
        val valid = storageRequest("denied", mapOf("op" to string("clear")))
        val denied = LuaHostCapabilityDispatcher(EXECUTION_ID, emptyList(), hostStorage = storage)
            .dispatch(valid, descriptorCount = 0)
        assertEquals(LuaHostErrorCode.CAPABILITY_DENIED, (denied as LuaHostDispatchOutcome.Failed).error.code)

        val missingPrincipal = LuaHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = listOf(LuaRuntimeHostCapabilities.STORAGE_KV),
            hostStorage = storage,
        ).dispatch(storageRequest("no-principal", mapOf("op" to string("clear"))), descriptorCount = 0)
        assertEquals(
            LuaHostErrorCode.CAPABILITY_UNAVAILABLE,
            (missingPrincipal as LuaHostDispatchOutcome.Failed).error.code,
        )

        val invalidArguments = listOf(
            mapOf("op" to string("unknown")),
            mapOf("op" to string("get")),
            mapOf("op" to string("get"), "key" to string("bad/key")),
            mapOf("op" to string("clear"), "extra" to LuaValue.BooleanValue(true)),
            mapOf(
                "op" to string("put"),
                "key" to string("key"),
                "value" to LuaValue.BytesValue(byteArrayOf(1)),
            ),
        )
        val dispatcher = storageDispatcher(storage, LuaStoragePrincipal.fromStableIdentity("invalid"))
        invalidArguments.forEachIndexed { index, arguments ->
            val outcome = dispatcher.dispatch(storageRequest("invalid-$index", arguments), descriptorCount = 0)
            assertEquals(
                LuaHostErrorCode.INVALID_REQUEST,
                (outcome as LuaHostDispatchOutcome.Failed).error.code,
            )
        }
        assertTrue(storage.operations.isEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(
                storageRequest(
                    "invalid-nil",
                    mapOf("op" to string("put"), "key" to string("key"), "value" to LuaValue.Nil),
                ),
                descriptorCount = 0,
            )
        }
        assertTrue(storage.operations.isEmpty())
    }

    @Test
    fun storageOperationAndMutationQuotasAreChargedWithoutRetry() {
        val storage = FakeHostStorage()
        val dispatcher = storageDispatcher(storage, LuaStoragePrincipal.fromStableIdentity("quota"))
        repeat(LuaStorageContract.MAX_MUTATIONS_PER_EXECUTION) { index ->
            val outcome = dispatcher.dispatch(
                storageRequest("mutation-$index", mapOf("op" to string("clear"))),
                descriptorCount = 0,
            )
            assertTrue(outcome is LuaHostDispatchOutcome.Completed)
        }
        val mutationOverflow = dispatcher.dispatch(
            storageRequest("mutation-overflow", mapOf("op" to string("clear"))),
            descriptorCount = 0,
        )
        assertEquals(
            LuaHostErrorCode.QUOTA_EXCEEDED,
            (mutationOverflow as LuaHostDispatchOutcome.Failed).error.code,
        )
        assertEquals(LuaStorageContract.MAX_MUTATIONS_PER_EXECUTION, storage.operations.size)

        repeat(
            LuaStorageContract.MAX_OPERATIONS_PER_EXECUTION -
                LuaStorageContract.MAX_MUTATIONS_PER_EXECUTION - 1,
        ) { index ->
            dispatcher.dispatch(
                storageRequest("read-$index", mapOf("op" to string("get"), "key" to string("missing"))),
                descriptorCount = 0,
            )
        }
        val operationOverflow = dispatcher.dispatch(
            storageRequest("operation-overflow", mapOf("op" to string("get"), "key" to string("missing"))),
            descriptorCount = 0,
        )
        assertEquals(
            LuaHostErrorCode.QUOTA_EXCEEDED,
            (operationOverflow as LuaHostDispatchOutcome.Failed).error.code,
        )
    }

    @Test
    fun toastUsesExactShapeHostUiLaneAndIndependentFourCallQuota() {
        val messages = mutableListOf<String>()
        val dispatcher = LuaHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = listOf(LuaRuntimeHostCapabilities.UI_TOAST),
            toastSink = LuaToastSink { text -> messages += text; true },
        )
        repeat(4) { index ->
            val outcome = dispatcher.dispatch(
                toastRequest("toast-$index", "保存完成"),
                descriptorCount = 0,
            )
            assertEquals(
                LuaValue.MapValue(mapOf("accepted" to LuaValue.BooleanValue(true))),
                (outcome as LuaHostDispatchOutcome.Completed).result.value,
            )
        }
        val overflow = dispatcher.dispatch(toastRequest("toast-overflow", "fifth"), descriptorCount = 0)
        assertEquals(LuaHostErrorCode.QUOTA_EXCEEDED, (overflow as LuaHostDispatchOutcome.Failed).error.code)
        assertEquals(List(4) { "保存完成" }, messages)

        val invalid = LuaHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = listOf(LuaRuntimeHostCapabilities.UI_TOAST),
            toastSink = LuaToastSink { text -> messages += text; true },
        ).dispatch(toastRequest("toast-invalid", ""), descriptorCount = 0)
        assertEquals(LuaHostErrorCode.INVALID_REQUEST, (invalid as LuaHostDispatchOutcome.Failed).error.code)
        assertEquals(4, messages.size)
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

    private fun storageDispatcher(storage: LuaHostStorage, principal: LuaStoragePrincipal) =
        LuaHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = listOf(LuaRuntimeHostCapabilities.STORAGE_KV),
            storagePrincipal = principal,
            hostStorage = storage,
        )

    private fun storageRequest(callId: String, arguments: Map<String, LuaValue>) = LuaHostCallRequest(
        executionId = EXECUTION_ID,
        callId = callId,
        capability = LuaRuntimeHostCapabilities.STORAGE_KV,
        arguments = LuaValue.MapValue(arguments),
    )

    private fun toastRequest(callId: String, text: String) = LuaHostCallRequest(
        executionId = EXECUTION_ID,
        callId = callId,
        capability = LuaRuntimeHostCapabilities.UI_TOAST,
        arguments = LuaValue.MapValue(mapOf("text" to LuaValue.StringValue(text))),
    )

    private fun string(value: String) = LuaValue.StringValue(value)

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

    private class FakeHostStorage : LuaHostStorage {
        private val values = ConcurrentHashMap<LuaStoragePrincipal, MutableMap<String, ByteArray>>()
        val operations = mutableListOf<String>()

        override fun get(principal: LuaStoragePrincipal, key: String): ByteArray? {
            operations += "get"
            return values[principal]?.get(key)?.copyOf()
        }

        override fun put(principal: LuaStoragePrincipal, key: String, encodedValue: ByteArray) {
            operations += "put"
            values.computeIfAbsent(principal) { linkedMapOf() }[key] = encodedValue.copyOf()
        }

        override fun remove(principal: LuaStoragePrincipal, key: String): Boolean {
            operations += "remove"
            return values[principal]?.remove(key) != null
        }

        override fun clear(principal: LuaStoragePrincipal): Int {
            operations += "clear"
            val bucket = values[principal] ?: return 0
            return bucket.size.also { bucket.clear() }
        }
    }
}
