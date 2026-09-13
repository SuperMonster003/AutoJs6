package org.autojs.autojs.permission

import android.os.Build
import android.util.Log
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.autojs.autojs.core.http.LocalNetworkDiagnostics
import org.autojs.autojs.core.http.MutableOkHttp
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs.engine.AndroidNodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeError
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.json.JSONArray
import org.json.JSONObject
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Transport cases require an explicit disposable-device fixture; UI tests operate the system dialog. */
class LocalNetworkPermissionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val host get() = arguments.getString("sdk37.lanHost") ?: "10.0.2.2"
    private val allowed get() = arguments.getString("sdk37.lanAllowed")!!.toBooleanStrict()

    private fun requireFixture() {
        assumeTrue("Explicit SDK 37 LAN fixture required", arguments.getString("sdk37.lanAllowed") != null)
        assertEquals("Permission state differs from fixture", allowed, LocalNetworkPermission.isGranted(context))
    }

    @Test fun runtimePermissionNameUsesTheExistingApiAndSkipsLegacyTargets() {
        val requests = org.autojs.autojs.core.permission.Permissions.getPermissionsNeedToRequest(context, arrayOf("access_local_network"))
        val expected = if (LocalNetworkPermission.isGranted(context)) emptyArray() else arrayOf(LocalNetworkPermission.PERMISSION)
        assertArrayEquals(expected, requests)
        assertArrayEquals(expected, Base.getPermissionsToRequest(context, arrayOf("access_local_network")))
        assertEquals(Build.VERSION.SDK_INT >= 37 && context.applicationInfo.targetSdkVersion >= 37, LocalNetworkPermission.isRequired(context))
    }

    @Test fun rawTcpAndUdpFollowTheSystemPermission() {
        requireFixture()
        checkTransport("TCP") {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, 17637), 2_000)
                socket.soTimeout = 2_000
                socket.getOutputStream().write("GET / HTTP/1.0\r\nHost: fixture\r\n\r\n".toByteArray())
                check(socket.getInputStream().read() == 'H'.code)
            }
        }
        checkTransport("UDP") {
            DatagramSocket().use { socket ->
                socket.soTimeout = 2_000
                val data = "sdk37".toByteArray()
                socket.send(DatagramPacket(data, data.size, InetAddress.getByName(host), 17638))
                val response = DatagramPacket(ByteArray(32), 32)
                socket.receive(response)
                check(String(response.data, 0, response.length) == "sdk37")
            }
        }
    }

    @Test fun inboundAcceptIsProtectedEvenWhenBindSucceeds() {
        requireFixture()
        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress("0.0.0.0", 17643))
            server.soTimeout = 3_000
            checkTransport("inbound accept") {
                server.accept().use { accepted ->
                    accepted.soTimeout = 2_000
                    check(accepted.getInputStream().read() == 'P'.code)
                }
            }
        }
    }

    @Test fun ipv4AndIpv6LoopbackRemainUsableWithoutLocalNetworkPermission() {
        requireFixture()
        for (address in listOf("127.0.0.1", "::1")) {
            val failure = AtomicReference<Throwable>()
            ServerSocket(0, 1, InetAddress.getByName(address)).use { server ->
                server.soTimeout = 3_000
                val worker = thread {
                    runCatching { server.accept().use { it.getOutputStream().write(42) } }.onFailure { failure.set(it) }
                }
                Socket().use { client ->
                    client.connect(InetSocketAddress(address, server.localPort), 2_000)
                    client.soTimeout = 2_000
                    assertEquals(42, client.getInputStream().read())
                }
                worker.join(4_000)
                assertFalse(worker.isAlive)
                failure.get()?.let { throw AssertionError(address, it) }
                Log.i(TAG, "loopback $address passed; LAN allowed=$allowed")
            }
        }
    }

    @Test fun scriptHttpSyncAndAsyncFailuresKeepTheCauseAndExplainRecovery() {
        requireFixture()
        val client = MutableOkHttp().apply {
            muteClient(OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).callTimeout(4, TimeUnit.SECONDS))
        }.client()
        val request = Request.Builder().url("http://$host:17637/").build()
        checkTransport("HTTP sync", requireHint = true) { client.newCall(request).execute().use { check(it.code == 200) } }
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { failure.set(e); done.countDown() }
            override fun onResponse(call: Call, response: Response) {
                response.use { if (it.code != 200) failure.set(AssertionError("HTTP ${it.code}")) }
                done.countDown()
            }
        })
        assertTrue(done.await(8, TimeUnit.SECONDS))
        checkOutcome("HTTP async", failure.get(), requireHint = true)
    }

    @Test fun webSocketUpgradeUsesTheSamePermissionDiagnostics() {
        requireFixture()
        val client = LocalNetworkDiagnostics.withDiagnostics(OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build())
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val socket = client.newWebSocket(Request.Builder().url("ws://$host:17637/").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { done.countDown() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failure.set(t); done.countDown() }
        })
        try {
            assertTrue(done.await(8, TimeUnit.SECONDS))
            checkOutcome("WebSocket", failure.get(), requireHint = true)
        } finally { socket.cancel() }
    }

    @Test fun rawPahoMqttFollowsTheSystemPermissionWithoutAnAutomaticPrompt() {
        requireFixture()
        val client = MqttClient("tcp://$host:17639", "sdk37-${System.nanoTime()}", MemoryPersistence())
        try {
            checkTransport("MQTT (original Paho exception)") {
                client.connect(MqttConnectOptions().apply { connectionTimeout = 2; isAutomaticReconnect = false })
                check(client.isConnected)
            }
        } finally {
            if (client.isConnected) client.disconnectForcibly(0, 1_000)
            client.close()
        }
    }

    @Test fun devServerReportsMissingPermissionBeforeAdvertisingAListener() {
        requireFixture()
        val service = DevPluginService(context)
        if (!allowed) {
            val observer = service.enableLocalServer().test().awaitDone(4, TimeUnit.SECONDS)
            observer.assertNoValues().assertError { it.message == context.getString(org.autojs.autojs6.R.string.error_local_network_permission_required) }
            assertFalse(service.isServerSocketConnected)
        }
    }

    @Test fun devServerAcceptsTheVsCodeHandshakeAfterGrant() {
        requireFixture()
        assumeTrue(allowed)
        val service = DevPluginService(context)
        val connected = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val counts = service.serverConnectionCount.subscribe { if (it > 0) connected.countDown() }
        val listener = service.enableLocalServer().subscribe({}, { failure.set(it); connected.countDown() })
        try {
            assertTrue("VS Code server received no LAN peer", connected.await(8, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("VS Code server failed", it) }
            assertTrue(service.serverConnectionCount.value!! > 0)
            Log.i(TAG, "VS Code server accepted LAN peer")
        } finally {
            service.disconnectJsonSocketServer()
            listener.dispose()
            counts.dispose()
        }
    }

    @Test fun devClientCompletesTheVsCodeHandshakeAfterGrant() {
        requireFixture()
        assumeTrue(allowed)
        val service = DevPluginService(context)
        val connected = CountDownLatch(1)
        val state = JsonSocketClient.cxnState.subscribe { if (it.isConnected()) connected.countDown() }
        val connection = service.connectToRemoteServer(context, "$host:17640", true).subscribe({}, { throw AssertionError(it) })
        try {
            assertTrue("VS Code client handshake did not complete", connected.await(8, TimeUnit.SECONDS))
            assertTrue(service.isJsonSocketClientConnected)
            Log.i(TAG, "VS Code client completed LAN handshake")
        } finally {
            service.disconnectJsonSocketClient()
            connection.dispose()
            state.dispose()
        }
    }

    @Test fun nodeControlledFetchPreservesItsErrorCodeAndAddsThePermissionHint() {
        requireFixture()
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val completed = CountDownLatch(1)
        val result = AtomicReference<NodeBridgeResponse>()
        try {
            provider.dispatch(NodeBridgeRequest(
                id = "sdk37-lan-fetch",
                module = "fetch",
                method = "request",
                argsJson = JSONArray().put(JSONObject().apply {
                    put("url", "http://$host:17637/")
                    put("method", "GET")
                    put("timeoutMs", 2_000)
                    put("maxResponseBytes", 1024)
                }).toString(),
                timeoutMs = 4_000L,
                permissions = listOf("network"),
            )) { result.set(it); completed.countDown() }
            assertTrue(completed.await(8, TimeUnit.SECONDS))
            assertEquals(allowed, result.get().ok)
            if (!allowed) {
                assertEquals(org.autojs.autojs.engine.NodeRuntimeErrorCodes.NETWORK_TIMEOUT, result.get().error?.code)
                assertTrue("Missing permission hint: ${result.get()}", result.get().error?.message.orEmpty().contains("access_local_network"))
            }
            Log.i(TAG, "Node fetch allowed=$allowed; result=${result.get()}")
        } finally { provider.destroy(NodeBridgeError.processDead("SDK 37 fixture complete")) }
    }

    @Test fun permissionRequestCanBeDeniedRetriedAndGranted() {
        requireFixture()
        assumeTrue(LocalNetworkPermission.isRequired(context) && !allowed)
        val activityClass = if (org.autojs.autojs6.BuildConfig.isInrt) org.autojs.autojs.inrt.SettingsActivity::class.java else PreferencesActivity::class.java
        val activity = instrumentation.startActivitySync(Intent(context, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as androidx.fragment.app.FragmentActivity
        try {
            val denied = CountDownLatch(1)
            instrumentation.runOnMainSync { LocalNetworkPermission(activity).request { grant -> assertFalse(grant); denied.countDown() } }
            clickResource("permission_deny_button")
            assertTrue(denied.await(5, TimeUnit.SECONDS))
            assertFalse(LocalNetworkPermission.isGranted(context))
            val granted = CountDownLatch(1)
            instrumentation.runOnMainSync { LocalNetworkPermission(activity).request { grant -> assertTrue(grant); granted.countDown() } }
            clickResource("md_buttonDefaultPositive")
            clickResource("permission_allow_button")
            assertTrue(granted.await(5, TimeUnit.SECONDS))
            assertTrue(LocalNetworkPermission.isGranted(context))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun repeatedDenialOffersSettingsWithoutOpeningItAutomatically() {
        requireFixture()
        assumeTrue(LocalNetworkPermission.isRequired(context) && !allowed)
        val activity = instrumentation.startActivitySync(Intent(context, PreferencesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PreferencesActivity
        try {
            repeat(2) { attempt ->
                val completed = CountDownLatch(1)
                instrumentation.runOnMainSync { LocalNetworkPermission(activity).request { assertFalse(it); completed.countDown() } }
                if (attempt > 0) clickResource("md_buttonDefaultPositive")
                clickResource("permission_deny_button")
                assertTrue(completed.await(5, TimeUnit.SECONDS))
            }
            instrumentation.waitForIdleSync()
            assertFalse(androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, LocalNetworkPermission.PERMISSION))
            assertEquals(context.packageName, instrumentation.uiAutomation.rootInActiveWindow.packageName.toString())
            clickResource("md_buttonDefaultNegative")
            assertFalse(LocalNetworkPermission.isGranted(context))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun existingNearbyDevicesGrantAvoidsAnotherSystemPrompt() {
        requireFixture()
        assumeTrue(LocalNetworkPermission.isRequired(context))
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT))
        val activity = instrumentation.startActivitySync(Intent(context, PreferencesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PreferencesActivity
        try {
            val completed = CountDownLatch(1)
            instrumentation.runOnMainSync { LocalNetworkPermission(activity).request { assertTrue(it); completed.countDown() } }
            assertTrue("Nearby Devices authorization required another prompt", completed.await(5, TimeUnit.SECONDS))
            assertTrue(LocalNetworkPermission.isGranted(context))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun checkTransport(name: String, requireHint: Boolean = false, block: () -> Unit) {
        checkOutcome(name, runCatching(block).exceptionOrNull(), requireHint)
    }

    private fun checkOutcome(name: String, failure: Throwable?, requireHint: Boolean) {
        Log.i(TAG, "$name; LAN allowed=$allowed; result=${failure?.stackTraceToString() ?: "success"}")
        if (allowed) {
            if (failure != null) throw AssertionError("$name failed with permission", failure)
        } else {
            assertNotNull("$name unexpectedly succeeded without permission", failure)
            assertTrue("$name failed outside the transport: $failure", failure is IOException || failure is MqttException)
            if (requireHint) {
                assertTrue("$name lacks recovery hint: $failure", failure!!.message.orEmpty().contains("access_local_network"))
                assertNotNull("$name lost original cause", failure.cause)
            }
        }
    }

    private fun clickResource(suffix: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        fun click(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val matches = node.viewIdResourceName?.endsWith(":id/$suffix") == true ||
                    (suffix == "permission_deny_button" && node.viewIdResourceName?.endsWith(":id/permission_deny_and_dont_ask_again_button") == true)
            if (matches && node.isVisibleToUser) {
                return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
            return (0 until node.childCount).any { click(node.getChild(it)) }
        }
        while (System.nanoTime() < deadline) {
            if (click(instrumentation.uiAutomation.rootInActiveWindow)) return
            Thread.sleep(100)
        }
        val visible = mutableListOf<String>()
        fun describe(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) visible.add("${node.viewIdResourceName}: ${node.text}")
            for (i in 0 until node.childCount) describe(node.getChild(i))
        }
        describe(instrumentation.uiAutomation.rootInActiveWindow)
        fail("No visible permission button: $suffix; nodes=$visible")
    }

    companion object { private const val TAG = "Sdk37LocalNetwork" }
}
