package org.autojs.autojs.core.plugin.mcp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.engine.NodeBridgeErrorCategory
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.IMcpServerCallback
import org.autojs.plugin.mcp.server.api.IMcpServerPlugin
import org.autojs.plugin.mcp.server.api.IMcpServerSession
import org.autojs.plugin.mcp.server.api.McpServerActions
import org.autojs.plugin.mcp.server.api.McpServerCapabilityKeys
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in, cross-APK conformance of the MCP Server Binder contract against the deterministic
 * fixture in `test-apps/mcp-server-conformance` (roadmap P6, host conformance test app).
 *
 * [McpServerPluginHost] pins the production package name on purpose, so the fixture is driven
 * through an exact-component binding here and the host pieces under test are the ones that do
 * not depend on that pin: `validatePluginInfo`, the [McpServerBundles] codec caps, and the real
 * [McpHostCapabilityBroker] with the default [McpCapabilityGrant]. The rebind schedule of
 * [McpServerSessionController] stays covered by the lifecycle matrix with the production plugin.
 *
 * The fixture must be installed with the same dedicated debug/test signer as the host. The suite
 * stays skipped during ordinary connected tests unless [ARG_ENABLED] is explicitly true.
 */
class McpServerAndroidConformanceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fixtureServicesDeclareTheContractShapeAndStayInvisibleToTheStrictHost() {
        requireConformanceEnvironment()
        val error = expectThrows<IllegalArgumentException> {
            runBlocking { McpServerPluginHost.probe(context, FAKE_PACKAGE) }
        }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Unsupported MCP Server package"))
    }

    @Test
    fun normalFixturePassesHostValidationAndRoundTrips() {
        requireConformanceEnvironment()
        FixtureBinding.bind(context, NORMAL_SERVICE).use { binding ->
            val plugin = binding.plugin
            val info = plugin.getInfo()
            McpServerPluginHost.validatePluginInfo(info)
            assertEquals(McpServerIds.PLUGIN_ID, info.id)
            assertEquals(McpServerIds.ENGINE, info.engine)
            val capabilities = plugin.getCapabilities()
            assertEquals(McpServerContract.CONTRACT_VERSION, capabilities.getInt(McpServerCapabilityKeys.CONTRACT_VERSION))
            assertTrue(capabilities.getLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION) >= McpServerIds.REQUIRED_HOST_VERSION_CODE)
            assertEquals(McpServerContract.SETTINGS_VERSION, capabilities.getInt(McpServerCapabilityKeys.SETTINGS_VERSION))

            val broker = McpHostCapabilityBroker(context, "conformance-normal", McpCapabilityGrant.DEFAULT)
            val callback = CountingCallback()
            val session = checkNotNull(plugin.openServer(configBundle(), broker, callback))
            try {
                val running = callback.awaitStatus(McpServerContract.STATE_RUNNING, OPEN_TIMEOUT_MILLIS)
                val parsed = McpServerBundles.statusFromBundle(running)
                assertTrue(parsed.isRunning)
                assertEquals(listOf("http://${McpServerContract.LOOPBACK_HOST}:$PORT${McpServerContract.ENDPOINT_PATH}"), parsed.endpoints)
                assertEquals(PORT, parsed.port)
                assertEquals(McpServerContract.BIND_SCOPE_LOOPBACK, parsed.bindScope)
                assertEquals(McpServerContract.PROTOCOL_MODE_STATEFUL, parsed.protocolMode)
                assertTrue("the fixture must have read the broker info", parsed.hostAvailable)
                assertTrue(parsed.startedAt > 0L)
                assertEquals(McpCapabilityGrant.DEFAULT.maxConcurrentCalls, running.getInt(KEY_FAKE_BROKER_MAX_CONCURRENT_CALLS))
                val probe = JSONObject(checkNotNull(running.getString(KEY_FAKE_PROBE_RESPONSE_JSON)))
                assertEquals(PROBE_REQUEST_ID, probe.getString("id"))
                assertTrue("device.info through the host broker must succeed: $probe", probe.getBoolean("ok"))

                val pulled = McpServerBundles.statusFromBundle(session.getStatus())
                assertTrue(pulled.isRunning)
                assertEquals(parsed.endpoints, pulled.endpoints)

                session.stop(McpServerBundles.reasonBundle(McpServerContract.REASON_USER_REQUEST))
                val stopped = McpServerBundles.statusFromBundle(callback.awaitStatus(McpServerContract.STATE_STOPPED, STOP_TIMEOUT_MILLIS))
                assertEquals(0, stopped.port)
                assertTrue(stopped.endpoints.isEmpty())
                assertEquals(McpServerContract.REASON_USER_REQUEST, stopped.lastErrorCode)

                session.close()
                Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
                assertEquals("one running callback", 1, callback.count(McpServerContract.STATE_RUNNING))
                assertEquals("one stopped callback", 1, callback.count(McpServerContract.STATE_STOPPED))
                assertEquals(1, callback.events.size)
                assertEquals(McpServerContract.EVENT_WARNING, McpServerBundles.eventFromBundle(callback.events.single())?.type)
            } finally {
                runCatching { session.close() }
                broker.destroy(McpServerBundles.reasonBundle(McpServerContract.REASON_USER_REQUEST))
            }
        }
    }

    @Test
    fun wrongContractVersionIsRejectedByHostValidationBeforeOpenServer() {
        requireConformanceEnvironment()
        FixtureBinding.bind(context, WRONG_CONTRACT_SERVICE).use { binding ->
            val plugin = binding.plugin
            val info = plugin.getInfo()
            val rejection = expectThrows<IllegalArgumentException> { McpServerPluginHost.validatePluginInfo(info) }
            assertTrue(rejection.message.orEmpty(), rejection.message.orEmpty().contains("Unsupported MCP Server contract version"))
            val declared = plugin.getCapabilities().getInt(McpServerCapabilityKeys.CONTRACT_VERSION)
            assertEquals(McpServerContract.MAX_CONTRACT_VERSION + 1, declared)
            assertFalse(McpServerContract.supportsContractVersion(declared))

            // The fixture refuses to open on that contract, so a host that skipped validation fails loudly.
            val broker = McpHostCapabilityBroker(context, "conformance-wrong-contract", McpCapabilityGrant.DEFAULT)
            try {
                val refusal = expectThrows<IllegalStateException> {
                    plugin.openServer(configBundle(), broker, CountingCallback())
                }
                assertTrue(refusal.message.orEmpty(), refusal.message.orEmpty().contains(McpServerContract.ERROR_UNSUPPORTED_CONTRACT))
            } finally {
                broker.destroy(McpServerBundles.reasonBundle(McpServerContract.ERROR_UNSUPPORTED_CONTRACT))
            }
        }
    }

    @Test
    fun oversizedStatusAndBridgeRequestsAreCappedByTheHost() {
        requireConformanceEnvironment()
        FixtureBinding.bind(context, OVERSIZED_SERVICE).use { binding ->
            val plugin = binding.plugin
            McpServerPluginHost.validatePluginInfo(plugin.getInfo())
            val broker = McpHostCapabilityBroker(context, "conformance-oversized", McpCapabilityGrant.DEFAULT)
            val callback = CountingCallback()
            val session = checkNotNull(plugin.openServer(configBundle(), broker, callback))
            try {
                val running = callback.awaitStatus(McpServerContract.STATE_RUNNING, OPEN_TIMEOUT_MILLIS)
                assertEquals(OVERSIZED_ERROR_BYTES, running.getString(McpServerContract.KEY_STATUS_LAST_ERROR)?.length)
                val parsed = McpServerBundles.statusFromBundle(running)
                assertTrue(parsed.isRunning)
                assertEquals("the codec must cut statusLastError to the cap", McpServerContract.MAX_ERROR_MESSAGE_BYTES, parsed.lastError?.length)
                assertEquals(FIXTURE_OVERSIZED_ERROR_CODE, parsed.lastErrorCode)

                val pulled = session.getStatus()
                assertEquals(OVERSIZED_ERROR_BYTES, pulled.getString(McpServerContract.KEY_STATUS_LAST_ERROR)?.length)
                assertEquals(McpServerContract.MAX_ERROR_MESSAGE_BYTES, McpServerBundles.statusFromBundle(pulled).lastError?.length)

                val probe = JSONObject(checkNotNull(running.getString(KEY_FAKE_PROBE_RESPONSE_JSON)))
                assertTrue("the small probe must still succeed: $probe", probe.getBoolean("ok"))
                val oversized = JSONObject(checkNotNull(running.getString(KEY_FAKE_OVERSIZED_RESPONSE_JSON)))
                assertEquals(OVERSIZED_REQUEST_ID, oversized.getString("id"))
                assertFalse("the broker must refuse an inline request above the cap: $oversized", oversized.getBoolean("ok"))
                assertEquals(NodeBridgeErrorCategory.RESOURCE_LIMIT, oversized.getString("category"))

                session.stop(McpServerBundles.reasonBundle(McpServerContract.REASON_USER_REQUEST))
                callback.awaitStatus(McpServerContract.STATE_STOPPED, STOP_TIMEOUT_MILLIS)
                session.close()
                Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
                assertEquals(1, callback.count(McpServerContract.STATE_RUNNING))
                assertEquals(1, callback.count(McpServerContract.STATE_STOPPED))
            } finally {
                runCatching { session.close() }
                broker.destroy(McpServerBundles.reasonBundle(McpServerContract.REASON_USER_REQUEST))
            }
        }
    }

    @Test
    fun duplicateCallbacksArriveInOrderAndParseWithoutFailure() {
        requireConformanceEnvironment()
        FixtureBinding.bind(context, DUPLICATE_SERVICE).use { binding ->
            val plugin = binding.plugin
            McpServerPluginHost.validatePluginInfo(plugin.getInfo())
            val broker = McpHostCapabilityBroker(context, "conformance-duplicate", McpCapabilityGrant.DEFAULT)
            val callback = CountingCallback()
            val session = checkNotNull(plugin.openServer(configBundle(), broker, callback))
            try {
                callback.awaitCount(McpServerContract.STATE_RUNNING, 2, OPEN_TIMEOUT_MILLIS)
                Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
                assertEquals(2, callback.count(McpServerContract.STATE_RUNNING))
                assertEquals(2, callback.events.size)

                session.stop(McpServerBundles.reasonBundle(McpServerContract.REASON_USER_REQUEST))
                callback.awaitCount(McpServerContract.STATE_STOPPED, 2, STOP_TIMEOUT_MILLIS)
                session.close()
                Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
                assertEquals("one more status callback after close is the scenario", 3, callback.count(McpServerContract.STATE_STOPPED))
                assertEquals(
                    listOf(
                        McpServerContract.STATE_RUNNING, McpServerContract.STATE_RUNNING,
                        McpServerContract.STATE_STOPPED, McpServerContract.STATE_STOPPED, McpServerContract.STATE_STOPPED,
                    ),
                    callback.statuses.map { it.getString(McpServerContract.KEY_STATUS_STATE) },
                )
                callback.statuses.forEach { status ->
                    assertTrue(McpServerContract.isKnownState(McpServerBundles.statusFromBundle(status).state))
                }
                callback.events.forEach { event ->
                    assertEquals(McpServerContract.EVENT_WARNING, McpServerBundles.eventFromBundle(event)?.type)
                }
            } finally {
                runCatching { session.close() }
                broker.destroy(McpServerBundles.reasonBundle(McpServerContract.REASON_USER_REQUEST))
            }
        }
    }

    @Test
    fun sessionDeathIsObservedAndTheHostBrokerSurvives() {
        requireConformanceEnvironment()
        FixtureBinding.bind(context, DEATH_SERVICE).use { binding ->
            val plugin = binding.plugin
            McpServerPluginHost.validatePluginInfo(plugin.getInfo())
            val broker = McpHostCapabilityBroker(context, "conformance-death", McpCapabilityGrant.DEFAULT)
            val callback = CountingCallback()
            val session = checkNotNull(plugin.openServer(configBundle(), broker, callback))
            val death = CountDownLatch(1)
            session.asBinder().linkToDeath({ death.countDown() }, 0)
            try {
                val running = McpServerBundles.statusFromBundle(callback.awaitStatus(McpServerContract.STATE_RUNNING, OPEN_TIMEOUT_MILLIS))
                assertTrue(running.isRunning)
                assertTrue("the fixture process must die after reporting running", death.await(DEATH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertFalse(session.asBinder().isBinderAlive)
                expectThrows<DeadObjectException> { session.getStatus() }

                // The host broker outlives the plugin: destroy is clean and a late dispatch answers process-dead.
                broker.destroy(McpServerBundles.reasonBundle(McpServerContract.REASON_PLUGIN_DIED))
                val answered = CountDownLatch(1)
                val response = AtomicReference<Bundle?>(null)
                val request = Bundle().apply {
                    putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.CONTRACT_VERSION)
                    putString(McpServerContract.KEY_BRIDGE_REQUEST_JSON, LATE_REQUEST_JSON)
                }
                broker.dispatch(request, object : IMcpHostCapabilityCallback.Stub() {
                    override fun onResponse(bundle: Bundle?) {
                        response.set(bundle)
                        answered.countDown()
                    }
                })
                assertTrue(answered.await(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                val late = JSONObject(checkNotNull(response.get()?.getString(McpServerContract.KEY_BRIDGE_RESPONSE_JSON)))
                assertFalse(late.getBoolean("ok"))
                assertEquals(NodeBridgeErrorCategory.PROCESS_DEAD, late.getJSONObject("error").getString("category"))

                Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
                assertEquals(1, callback.count(McpServerContract.STATE_RUNNING))
                assertEquals("a dead plugin sends no stop callback", 0, callback.count(McpServerContract.STATE_STOPPED))
            } finally {
                runCatching { broker.destroy(McpServerBundles.reasonBundle(McpServerContract.REASON_PLUGIN_DIED)) }
            }
        }
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "MCP Server Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("MCP Server conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertTrue("MCP Server fixture package is not installed", isPackageInstalled(FAKE_PACKAGE))
        assertEquals(
            "MCP Server fixture and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )

        val expected = ALL_SERVICES.map { ComponentName(FAKE_PACKAGE, it) }.toSet()
        val discovered = queryFixtureServices(McpServerActions.SERVICE_ACTION)
        assertEquals("MCP Server fixture services must resolve through the contract action and category", expected, discovered.keys)
        discovered.forEach { (component, service) ->
            assertTrue("MCP Server fixture is not exported: $component", service.exported)
            assertTrue("MCP Server fixture service is disabled: $component", service.enabled)
            assertEquals("MCP Server fixture must require the plugin permission: $component", McpServerActions.PLUGIN_PERMISSION, service.permission)
        }
        val info = queryFixtureServices(PluginActions.INFO)
        assertEquals(setOf(ComponentName(FAKE_PACKAGE, INFO_SERVICE)), info.keys)
    }

    private fun queryFixtureServices(action: String): Map<ComponentName, ServiceInfo> {
        val intent = Intent(action).apply {
            addCategory(McpServerActions.SERVICE_CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(FAKE_PACKAGE)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { item -> item.serviceInfo }
            .associateBy { service -> ComponentName(service.packageName, service.name) }
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun configBundle(): Bundle =
        McpServerBundles.configToBundle(McpServerConfig(port = PORT, hostLabel = "AutoJs6 conformance"))

    /** JUnit 4.12 on the device has no `assertThrows`; this keeps the failure message useful. */
    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("expected ${T::class.java.simpleName} but got ${error.javaClass.name}: ${error.message}", error)
        }
        throw AssertionError("expected ${T::class.java.simpleName} but nothing was thrown")
    }

    private class CountingCallback : IMcpServerCallback.Stub() {

        val statuses = CopyOnWriteArrayList<Bundle>()
        val events = CopyOnWriteArrayList<Bundle>()

        override fun onStatus(status: Bundle?) {
            status?.let(statuses::add)
        }

        override fun onEvent(event: Bundle?) {
            event?.let(events::add)
        }

        fun count(state: String): Int = statuses.count { it.getString(McpServerContract.KEY_STATUS_STATE) == state }

        fun awaitStatus(state: String, timeoutMillis: Long): Bundle = awaitCount(state, 1, timeoutMillis)

        fun awaitCount(state: String, count: Int, timeoutMillis: Long): Bundle {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (count(state) < count) {
                if (System.currentTimeMillis() > deadline) {
                    error("timed out waiting for $count x $state; statuses so far: ${statuses.map { it.getString(McpServerContract.KEY_STATUS_STATE) }}")
                }
                Thread.sleep(POLL_MILLIS)
            }
            return statuses.last { it.getString(McpServerContract.KEY_STATUS_STATE) == state }
        }
    }

    /** Exact-component binding to one fixture service; the strict host wrapper cannot reach the fixture. */
    private class FixtureBinding private constructor(
        private val context: Context,
        private val connection: ServiceConnection,
        val plugin: IMcpServerPlugin,
    ) : Closeable {

        override fun close() {
            runCatching { context.unbindService(connection) }
        }

        companion object {
            fun bind(context: Context, serviceClassName: String): FixtureBinding {
                val appContext = context.applicationContext
                val connected = CountDownLatch(1)
                val binderRef = AtomicReference<IBinder?>(null)
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        binderRef.set(service)
                        connected.countDown()
                    }

                    override fun onServiceDisconnected(name: ComponentName?) = Unit

                    override fun onNullBinding(name: ComponentName?) {
                        connected.countDown()
                    }
                }
                val intent = Intent(McpServerActions.SERVICE_ACTION).apply {
                    addCategory(McpServerActions.SERVICE_CATEGORY)
                    component = ComponentName(FAKE_PACKAGE, serviceClassName)
                }
                check(appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    "bindService returned false for $serviceClassName"
                }
                if (!connected.await(BIND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    runCatching { appContext.unbindService(connection) }
                    error("MCP Server fixture did not connect within $BIND_TIMEOUT_MILLIS ms: $serviceClassName")
                }
                val binder = binderRef.get() ?: run {
                    runCatching { appContext.unbindService(connection) }
                    error("MCP Server fixture returned a null binding: $serviceClassName")
                }
                return FixtureBinding(appContext, connection, IMcpServerPlugin.Stub.asInterface(binder))
            }
        }
    }

    private companion object {
        const val ARG_ENABLED = "autojs.mcpServer.conformance.enabled"
        const val FAKE_PACKAGE = "org.autojs.plugin.mcp.server.fake"
        const val SERVICE_PACKAGE = "$FAKE_PACKAGE.service"
        const val INFO_SERVICE = "$SERVICE_PACKAGE.FakeMcpServerInfoService"
        const val NORMAL_SERVICE = "$SERVICE_PACKAGE.NormalMcpServerService"
        const val WRONG_CONTRACT_SERVICE = "$SERVICE_PACKAGE.WrongContractVersionMcpServerService"
        const val OVERSIZED_SERVICE = "$SERVICE_PACKAGE.OversizedBundleMcpServerService"
        const val DUPLICATE_SERVICE = "$SERVICE_PACKAGE.DuplicateCallbackMcpServerService"
        const val DEATH_SERVICE = "$SERVICE_PACKAGE.DeathCallbackMcpServerService"
        val ALL_SERVICES = setOf(NORMAL_SERVICE, WRONG_CONTRACT_SERVICE, OVERSIZED_SERVICE, DUPLICATE_SERVICE, DEATH_SERVICE)

        const val PORT = 9655

        // Fixture-only status keys and values, documented in test-apps/mcp-server-conformance/README.md.
        const val KEY_FAKE_BROKER_MAX_CONCURRENT_CALLS = "fakeBrokerMaxConcurrentCalls"
        const val KEY_FAKE_PROBE_RESPONSE_JSON = "fakeProbeResponseJson"
        const val KEY_FAKE_OVERSIZED_RESPONSE_JSON = "fakeOversizedResponseJson"
        const val PROBE_REQUEST_ID = "fake-probe"
        const val OVERSIZED_REQUEST_ID = "fake-oversized"
        const val FIXTURE_OVERSIZED_ERROR_CODE = "fixture_oversized"
        const val OVERSIZED_ERROR_BYTES = McpServerContract.MAX_ERROR_MESSAGE_BYTES * 16
        const val LATE_REQUEST_JSON = "{\"id\":\"late\",\"module\":\"device\",\"method\":\"info\",\"args\":[],\"timeoutMs\":5000,\"permissions\":[\"device\"]}"

        const val BIND_TIMEOUT_MILLIS = 10_000L
        const val OPEN_TIMEOUT_MILLIS = 20_000L
        const val STOP_TIMEOUT_MILLIS = 10_000L
        const val DEATH_TIMEOUT_MILLIS = 10_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 1_500L
        const val POLL_MILLIS = 50L
    }
}
