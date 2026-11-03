package org.autojs.autojs.core.plugin.mcp

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.autojs.engine.NodeBridgeErrorCategory
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class McpRemoteServerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val service get() = AutoJs.instance.devPluginService

    @Test fun aPeerAcceptedDuringShutdownCannotReopenTheListener() = withStoppedServer {
        val peer = JsonSocketServer(service, DevPluginService.Port.AJ_SERVER, openListener = false)
        try {
            assertTrue(JsonSocketServer.serverSocket == null || JsonSocketServer.serverSocket!!.isClosed)
            assertFalse(service.isServerSocketConnected)
        } finally { peer.close() }
    }

    @Test fun startsOnceAndTheListenerSurvivesItsMcpSession() = withStoppedServer {
        val broker = broker()
        try {
            val first = call(broker)
            assertTrue(first.toString(), first.ok)
            val status = JSONObject(first.resultJson!!)
            assertTrue(status.getBoolean("enabled"))
            assertFalse(status.getBoolean("alreadyRunning"))
            assertEquals(7347, status.getInt("port"))
            val startedAt = service.serverStartedAt
            assertFalse(service.isServerSocketNormallyClosed)
            val second = call(broker)
            assertTrue(second.toString(), second.ok)
            assertTrue(JSONObject(second.resultJson!!).getBoolean("alreadyRunning"))
            assertEquals(startedAt, service.serverStartedAt)
            broker.destroy(null)
            assertTrue(service.isServerSocketConnected)
            Socket("127.0.0.1", DevPluginService.Port.AJ_SERVER).use { assertTrue(it.isConnected) }
        } finally { broker.destroy(null) }
    }

    @Test fun occupiedPortFailsAndCanBeRetriedAfterItIsReleased() = withStoppedServer {
        val broker = broker()
        try {
            ServerSocket(DevPluginService.Port.AJ_SERVER).use {
                val failed = call(broker)
                assertFalse(failed.toString(), failed.ok)
                assertEquals(NodeBridgeErrorCategory.PROVIDER_FAILED, failed.error?.category)
                assertFalse(service.isServerSocketConnected)
            }
            assertTrue(call(broker).ok)
        } finally { broker.destroy(null) }
    }

    @Test fun genericAppPermissionsCannotStartTheListenerEvenWhenTheRequestOmitsItsToken() {
        val broker = broker(HostCapabilityGrant(setOf("app.startRemoteServer"), setOf("app", "app.activity")))
        val before = service.isServerSocketConnected
        try {
            val denied = call(broker, permissions = emptyList())
            assertFalse(denied.toString(), denied.ok)
            assertEquals(NodeBridgeErrorCategory.CAPABILITY_DENIED, denied.error?.category)
            assertEquals(before, service.isServerSocketConnected)
        } finally { broker.destroy(null) }
    }

    @Test fun unexpectedArgumentsFailBeforeStarting() {
        val broker = broker()
        val before = service.isServerSocketConnected
        try {
            val invalid = call(broker, args = "[7347]")
            assertFalse(invalid.toString(), invalid.ok)
            assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, invalid.error?.category)
            assertEquals(before, service.isServerSocketConnected)
        } finally { broker.destroy(null) }
    }

    private fun broker(grant: HostCapabilityGrant = HostCapabilityGrant.DEFAULT) =
        HostCapabilityBrokerCore(context, "remote-server-test", grant, engineName = "mcp")

    private fun call(broker: HostCapabilityBrokerCore, args: String = "[]", permissions: List<String> = listOf("app.remote_server")): NodeBridgeResponse {
        val done = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        val request = NodeBridgeRequest("remote-server", "app", "startRemoteServer", args, 12_000L, permissions)
        broker.dispatch(Bundle().apply { putString(HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON, request.toJson()) }) {
            response = NodeBridgeResponse.fromJson(it.getString(HostCapabilityContract.KEY_BRIDGE_RESPONSE_JSON)!!)
            done.countDown()
        }
        assertTrue("Host reply timed out", done.await(15, TimeUnit.SECONDS))
        return checkNotNull(response)
    }

    private fun withStoppedServer(block: () -> Unit) {
        assumeFalse("Leave an existing user listener alone", service.isServerSocketConnected)
        assumeTrue("Grant AutoJs6 local network access before the device test", LocalNetworkPermission.isGranted(context))
        val normallyClosed = service.isServerSocketNormallyClosed
        try { block() } finally {
            service.disconnectJsonSocketServer()
            Thread.sleep(150) // Let the application-owned accept loop observe the closed socket.
            assertFalse("Stopping server mode must not reopen its listener", service.isServerSocketConnected)
            service.isServerSocketNormallyClosed = normallyClosed
        }
    }
}
