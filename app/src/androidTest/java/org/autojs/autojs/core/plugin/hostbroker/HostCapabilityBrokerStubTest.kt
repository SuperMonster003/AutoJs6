package org.autojs.autojs.core.plugin.hostbroker

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.engine.NodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeErrorCategory
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Android coverage of the actual Bundle/PFD boundary; dispatch lifetime has separate JVM tests. */
class HostCapabilityBrokerStubTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun dynamicExecutionDiscoveryRequiresAnAgentOwnerAndBothExecutionGrants() {
        for (agent in listOf(true, false)) for (permission in listOf(true, false)) for (stop in listOf(true, false)) {
            val methods = setOf("engines.execScript") + if (stop) setOf("engines.stop") else emptySet()
            val grant = HostCapabilityGrant(methods, if (permission) setOf("engines", "engines.exec") else setOf("engines"))
            val core = HostCapabilityBrokerCore(context, "dynamic-discovery", grant, engineName = if (agent) "ai-agent" else "plugin")
            try {
                assertEquals(agent && permission && stop,
                    core.getBrokerInfo().getStringArray(HostCapabilityContract.KEY_AVAILABLE_OPTIONAL_METHODS)!!.contains("engines.execScript"))
                core.destroy(null)
                assertFalse(core.getBrokerInfo().getStringArray(HostCapabilityContract.KEY_AVAILABLE_OPTIONAL_METHODS)!!.contains("engines.execScript"))
            } finally { core.destroy(null) }
        }
    }

    @Test fun optionalOcrDiscoveryTracksAvailabilityAndNeverWidensTheGrant() {
        var installed = false
        val permissions = setOf("accessibility", "screen_capture", "ocr")
        fun create(grant: HostCapabilityGrant) = HostCapabilityBrokerCore(context, "optional-test", grant,
            providerFactory = { _, _ -> object : NodeBridgeCapabilityProvider {
                override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) = error("Discovery must not dispatch")
            } }, ocrAvailable = { installed })
        for (granted in listOf(true, false)) {
            val core = create(HostCapabilityGrant(setOf("accessibility.readScreenText"), if (granted) permissions else permissions - "ocr"))
            try {
                installed = false
                assertTrue(core.getBrokerInfo().getStringArray(HostCapabilityContract.KEY_AVAILABLE_OPTIONAL_METHODS)!!.isEmpty())
                installed = true
                assertEquals(granted, core.getBrokerInfo().getStringArray(HostCapabilityContract.KEY_AVAILABLE_OPTIONAL_METHODS)!!.contains("accessibility.readScreenText"))
                assertEquals(setOf("accessibility.readScreenText"), core.getBrokerInfo().getStringArray(HostCapabilityContract.KEY_GRANT_METHODS)!!.toSet())
                core.destroy(null)
                assertTrue(core.getBrokerInfo().getStringArray(HostCapabilityContract.KEY_AVAILABLE_OPTIONAL_METHODS)!!.isEmpty())
            } finally { core.destroy(null) }
        }
    }

    @Test fun malformedDeniedAndDestroyedRequestsGetOneTerminalReply() {
        val core = core { request -> NodeBridgeResponse.success(request.id, "true") }
        val broker = HostCapabilityBrokerStub(core, Process.myUid())
        try {
            assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, reply(broker, "{").error?.category)
            assertEquals(NodeBridgeErrorCategory.CAPABILITY_DENIED,
                reply(broker, request("denied", "rhino", "run")).error?.category)
            assertTrue(reply(broker, request("normal")).ok)
            broker.destroy(Bundle())
            assertEquals(NodeBridgeErrorCategory.PROCESS_DEAD, reply(broker, request("dead")).error?.category)
        } finally {
            core.destroy(Bundle())
        }
    }

    @Test fun foreignUidCannotUseAnyBinderEntryPoint() {
        val core = core { request -> NodeBridgeResponse.success(request.id, "true") }
        val broker = HostCapabilityBrokerStub(core, Process.myUid() + 1)
        try {
            assertTrue(runCatching { broker.brokerInfo }.exceptionOrNull() is SecurityException)
            assertTrue(runCatching { broker.dispatch(Bundle(), null) }.exceptionOrNull() is SecurityException)
            assertTrue(runCatching { broker.destroy(Bundle()) }.exceptionOrNull() is SecurityException)
            withReadDescriptor { descriptor ->
                val input = Bundle().apply { putParcelable(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD, descriptor) }
                assertTrue(runCatching { broker.dispatch(input, null) }.exceptionOrNull() is SecurityException)
                assertTrue("rejected UID transfers must close incoming descriptors", runCatching { descriptor.fd }.isFailure)
            }
            assertFalse(core.isDestroyed)
        } finally {
            core.destroy(Bundle())
        }
    }

    @Test fun malformedAndCallbacklessBundlesCloseUnexpectedIncomingDescriptors() {
        val core = core { request -> NodeBridgeResponse.success(request.id, "true") }
        val broker = HostCapabilityBrokerStub(core, Process.myUid())
        try {
            val wrongType = Bundle().apply { putInt(HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON, 42) }
            assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, reply(broker, wrongType).error?.category)
            withReadDescriptor { descriptor ->
                val input = Bundle(wrongType).apply { putParcelable(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD, descriptor) }
                assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, reply(broker, input).error?.category)
                assertTrue(runCatching { descriptor.fd }.isFailure)
            }
            withReadDescriptor { descriptor ->
                val input = Bundle().apply { putParcelable(HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON, descriptor) }
                assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, reply(broker, input).error?.category)
                assertTrue("a descriptor in a JSON field is still receiver-owned", runCatching { descriptor.fd }.isFailure)
            }
            withReadDescriptor { descriptor ->
                broker.dispatch(Bundle().apply { putParcelable(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD, descriptor) }, null)
                assertTrue("calls without a callback still transfer descriptor ownership", runCatching { descriptor.fd }.isFailure)
            }
            withReadDescriptor { descriptor ->
                broker.destroy(Bundle().apply {
                    putInt(HostCapabilityContract.KEY_ERROR_MESSAGE, 123)
                    putParcelable(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD, descriptor)
                })
                assertTrue(runCatching { descriptor.fd }.isFailure)
                assertTrue(core.isDestroyed)
            }
        } finally {
            core.destroy(Bundle())
        }
    }

    @Test fun largeJsonUsesReadOnlyDescriptorWithReceiverOwnedLifetime() {
        val expected = JSONObject().put("text", "x".repeat(80 * 1024)).toString()
        val core = core { request -> NodeBridgeResponse.success(request.id, expected) }
        val broker = HostCapabilityBrokerStub(core, Process.myUid())
        val done = CountDownLatch(1)
        var descriptor: ParcelFileDescriptor? = null
        var metadata: Bundle? = null
        try {
            broker.dispatch(Bundle().apply { putString(HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON, request("payload")) },
                object : IHostCapabilityCallback.Stub() {
                    override fun onResponse(response: Bundle?) {
                        metadata = response
                        @Suppress("DEPRECATION")
                        val original = response?.getParcelable<ParcelFileDescriptor>(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD)
                        descriptor = original?.let { ParcelFileDescriptor.dup(it.fileDescriptor) }
                        done.countDown()
                    }
                })
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(metadata!!.getBoolean(HostCapabilityContract.KEY_BRIDGE_RESPONSE_OK))
            assertEquals(expected.toByteArray().size.toLong(), metadata!!.getLong(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_BYTES))
            val actual = ParcelFileDescriptor.AutoCloseInputStream(checkNotNull(descriptor)).bufferedReader().use { it.readText() }
            descriptor = null
            assertEquals(expected, actual)
        } finally {
            descriptor?.close()
            core.destroy(Bundle())
        }
    }

    private fun core(handler: (NodeBridgeRequest) -> NodeBridgeResponse) = HostCapabilityBrokerCore(
        context, "stub-test", HostCapabilityGrant(setOf("device.info"), setOf("device")),
        providerFactory = { _, _ -> object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) = callback(handler(request))
        } },
    )

    private fun request(id: String, module: String = "device", method: String = "info") =
        NodeBridgeRequest(id, module, method, "[]", 5_000L).toJson()

    private fun reply(broker: HostCapabilityBrokerStub, json: String): NodeBridgeResponse =
        reply(broker, Bundle().apply { putString(HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON, json) })

    private fun reply(broker: HostCapabilityBrokerStub, request: Bundle): NodeBridgeResponse {
        val done = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        broker.dispatch(request,
            object : IHostCapabilityCallback.Stub() {
                override fun onResponse(result: Bundle?) {
                    response = NodeBridgeResponse.fromJson(result!!.getString(HostCapabilityContract.KEY_BRIDGE_RESPONSE_JSON)!!)
                    done.countDown()
                }
            })
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return checkNotNull(response)
    }

    private fun withReadDescriptor(block: (ParcelFileDescriptor) -> Unit) {
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            block(pipe[0])
        } finally {
            pipe.forEach { runCatching { it.close() } }
        }
    }
}
