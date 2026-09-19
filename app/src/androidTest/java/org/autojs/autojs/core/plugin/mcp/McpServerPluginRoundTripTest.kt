package org.autojs.autojs.core.plugin.mcp

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

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
