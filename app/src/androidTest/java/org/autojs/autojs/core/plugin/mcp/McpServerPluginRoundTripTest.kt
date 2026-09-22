package org.autojs.autojs.core.plugin.mcp

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.IMcpServerCallback
import org.autojs.plugin.mcp.server.api.IMcpServerPlugin
import org.autojs.plugin.mcp.server.api.IMcpServerSession
import org.autojs.plugin.mcp.server.api.McpServerCapabilityKeys
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real Binder round trip with the installed MCP Server plugin (roadmap P1 acceptance, completed
 * with the P2.3 plugin): `openServer` through [McpServerSessionController] must reach `running`
 * with the host broker attached, `refreshStatus` must report the loopback endpoint, and
 * `stop` / `close` must bring the listener down again.
 *
 * The plugin answers the host's broker with a `device.info` probe right after attach, so a
 * passing run also proves `openServer -> dispatch -> onResponse` in the plugin's logcat
 * (`McpServerRuntime: Host probe ok`).
 *
 * Manual client evidence (Claude Code, MCP Inspector) can reuse the open session: set
 * `adb shell setprop debug.autojs6.mcp.hold <seconds>` before the run and the test keeps the
 * session open that long before stopping it.
 */
class McpServerPluginRoundTripTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun v1AndV2MetadataSelectTheCorrectTransactionAndEnvelope() {
        for (version in listOf(1, 2)) {
            val replies = CopyOnWriteArrayList<Bundle>()
            val answered = CountDownLatch(1)
            var selectedVersion = 0
            var sentConfig: Bundle? = null
            val session = object : IMcpServerSession.Stub() {
                override fun getStatus(): Bundle = Bundle()
                override fun updateConfig(config: Bundle?) = Unit
                override fun stop(reason: Bundle?) = Unit
                override fun close() = Unit
            }
            val fixture = object : IMcpServerPlugin.Stub() {
                override fun getInfo(): PluginInfo = PluginInfo().apply {
                    id = McpServerIds.PLUGIN_ID
                    engine = McpServerIds.ENGINE
                    variant = McpServerIds.VARIANT_DEFAULT
                    capabilities = getCapabilities()
                }
                override fun getCapabilities(): Bundle = Bundle().apply {
                    putInt(McpServerCapabilityKeys.CONTRACT_VERSION, version)
                    putLong(McpServerCapabilityKeys.REQUIRES_HOST_VERSION, McpServerIds.REQUIRED_HOST_VERSION_CODE)
                }
                override fun openServer(config: Bundle?, broker: IMcpHostCapabilityBroker?, callback: IMcpServerCallback?): IMcpServerSession {
                    selectedVersion = 1
                    sentConfig = config
                    assertEquals(1, broker!!.brokerInfo.getInt(McpServerContract.KEY_CONTRACT_VERSION))
                    broker.dispatch(probeBundle(), object : IMcpHostCapabilityCallback.Stub() {
                        override fun onResponse(response: Bundle?) { replies += checkNotNull(response); answered.countDown() }
                    })
                    return session
                }
                override fun openServerV2(config: Bundle?, broker: IHostCapabilityBroker?, callback: IMcpServerCallback?): IMcpServerSession {
                    selectedVersion = 2
                    sentConfig = config
                    assertEquals(1, broker!!.brokerInfo.getInt(McpServerContract.KEY_HOST_CAPABILITY_BROKER_VERSION))
                    broker.dispatch(probeBundle(), object : IHostCapabilityCallback.Stub() {
                        override fun onResponse(response: Bundle?) { replies += checkNotNull(response); answered.countDown() }
                    })
                    return session
                }
            }
            // Suppress queryLocalInterface so AIDL transaction numbers and parcel layouts are exercised.
            val wire = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
                    fixture.asBinder().transact(code, data, reply, flags)
            }
            val proxy = IMcpServerPlugin.Stub.asInterface(wire)
            val broker = McpHostCapabilityBroker(context, "roundtrip-v$version", McpCapabilityGrant.DEFAULT)
            try {
                val opened = McpServerPluginHost.openServerWithContract(proxy, McpServerConfig(), broker,
                    object : IMcpServerCallback.Stub() {
                        override fun onStatus(status: Bundle?) = Unit
                        override fun onEvent(event: Bundle?) = Unit
                    })
                assertEquals(version, selectedVersion)
                assertEquals(version, sentConfig!!.getInt(McpServerContract.KEY_CONTRACT_VERSION))
                assertTrue(answered.await(5, TimeUnit.SECONDS))
                assertEquals(1, replies.size)
                assertTrue(replies.single().getBoolean(McpServerContract.KEY_BRIDGE_RESPONSE_OK))
                opened.close()
                // Session methods retain v1 envelope compatibility for both accepted control versions.
                assertEquals(1, McpServerBundles.configToBundle(McpServerConfig()).getInt(McpServerContract.KEY_CONTRACT_VERSION))
                assertEquals(1, McpServerBundles.reasonBundle("stop").getInt(McpServerContract.KEY_CONTRACT_VERSION))
            } finally {
                broker.destroy(Bundle())
            }
        }
    }

    private fun probeBundle() = Bundle().apply {
        putString(McpServerContract.KEY_BRIDGE_REQUEST_JSON,
            """{"id":"version-probe","module":"device","method":"info","args":[],"permissions":["device"],"timeoutMs":5000}""")
    }

    @Test
    fun openServerReachesRunningWithTheHostAttachedAndStopsCleanly(): Unit = runBlocking {
        val packageName = McpServerIds.DEFAULT_PACKAGE_NAME
        assumeTrue("the MCP Server plugin is not installed", McpServerPluginHost.queryServiceCount(context) > 0)
        val wasEnabled = PluginEnableStore.isEnabled(context, packageName, defaultEnabled = false)
        PluginEnableStore.setEnabled(context, packageName, true)

        val statuses = CopyOnWriteArrayList<McpServerStatus>()
        val events = CopyOnWriteArrayList<McpServerEvent>()
        val controller = McpServerSessionController(
            context,
            object : McpServerSessionController.Listener {
                override fun onStatus(status: McpServerStatus) {
                    statuses += status
                    Log.i(TAG, "status: $status")
                }

                override fun onEvent(event: McpServerEvent) {
                    events += event
                    Log.i(TAG, "event: $event")
                }
            },
        )
        try {
            val info = McpServerPluginHost.probe(context, packageName)
            Log.i(TAG, "plugin ${info.versionName} (${info.versionCode}) capabilities: ${info.capabilities}")

            val opened = controller.open(McpServerConfig(port = PORT, hostLabel = "AutoJs6 round trip"))
            Log.i(TAG, "openServer returned $opened")
            val running = withTimeout(30_000L) { controller.status.first { it.isRunning } }
            assertEquals(listOf("http://${McpServerContract.LOOPBACK_HOST}:$PORT${McpServerContract.ENDPOINT_PATH}"), running.endpoints)
            assertEquals(PORT, running.port)
            assertEquals(McpServerContract.BIND_SCOPE_LOOPBACK, running.bindScope)
            assertEquals(McpServerContract.PROTOCOL_MODE_STATEFUL, running.protocolMode)
            assertTrue("the plugin must see the host broker", running.hostAvailable)
            assertTrue(running.startedAt > 0L)

            val refreshed = controller.refreshStatus()
            assertTrue(refreshed.isRunning)
            assertTrue(refreshed.hostAvailable)
            assertEquals(running.endpoints, refreshed.endpoints)
            Log.i(TAG, "running: $refreshed")

            val holdSeconds = holdSeconds()
            if (holdSeconds > 0) {
                Log.i(TAG, "holding the session open for $holdSeconds s for manual client evidence")
                delay(holdSeconds * 1_000L)
                Log.i(TAG, "events during the hold: ${events.map { "${it.type}${it.toolName?.let { name -> ":$name" }.orEmpty()}${it.clientName?.let { name -> "@$name" }.orEmpty()}" }}")
            }

            controller.stop()
            val stopped = withTimeout(20_000L) { controller.status.first { it.state == McpServerContract.STATE_STOPPED } }
            assertEquals(0, stopped.port)
            assertTrue(stopped.endpoints.isEmpty())
            assertTrue("the session outlives a stop", controller.isOpen)

            controller.close()
            assertFalse(controller.isOpen)
            assertEquals(McpServerContract.STATE_STOPPED, controller.status.value.state)
            Log.i(TAG, "round trip complete: ${statuses.size} status updates, ${events.size} events")
        } finally {
            runCatching { controller.close() }
            if (!wasEnabled) PluginEnableStore.setEnabled(context, packageName, false)
        }
    }

    private fun holdSeconds(): Int = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", "debug.autojs6.mcp.hold"))
        process.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull() ?: 0
    }.getOrDefault(0)

    private companion object {
        const val TAG = "McpRoundTripTest"
        const val PORT = McpServerContract.DEFAULT_PORT
    }
}
