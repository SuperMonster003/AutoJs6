package org.autojs.autojs.engine

import android.os.Bundle
import android.os.SystemClock
import org.autojs.autojs.runtime.memory.MemoryDiagnostics
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException

/**
 * The stubs exported to the Node.js plugin process stay reachable until that process collects its
 * proxies. They must stop reaching the execution once it ends (docs/memory-phase2/ROADMAP.md C30).
 * zh-CN: 导出给 Node.js 插件进程的存根在该进程回收代理之前一直可达, 执行结束后不得再指向本次执行
 * (docs/memory-phase2/ROADMAP.md C30).
 */
class NodeExportedBinderReleaseInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Test
    fun destroyedBrokerStubAnswersAloneAndReleasesTheBroker() {
        val executionId = "exported-broker-${System.nanoTime()}"
        val (binder, brokerRef) = createAndDestroyBroker(executionId)
        val stub = INodeJsHostCapabilityBroker.Stub.asInterface(binder)

        val info = stub.getBrokerInfo()
        assertEquals(executionId, info.getString(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_ID))
        assertEquals(
            NodeJsRuntimeContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION,
            info.getInt(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_VERSION),
        )
        val responses = CopyOnWriteArrayList<Bundle>()
        stub.dispatch(
            Bundle().apply {
                putString(
                    NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON,
                    JSONObject().put("id", "late-1").put("module", "app").put("method", "info").toString(),
                )
            },
            object : INodeJsHostCapabilityCallback.Stub() {
                override fun onResponse(response: Bundle) {
                    responses += response
                }
            },
        )
        assertEquals(1, responses.size)
        assertFalse(responses[0].getBoolean(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_OK, true))
        val error = JSONObject(responses[0].getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON)!!).getJSONObject("error")
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD, error.getString("code"))
        assertTrue(stub.getNativeDiagnostics().isEmpty)
        stub.destroy(null)

        assertTrue("destroyed broker is still reachable from its exported stub", awaitCleared(brokerRef))
        assertNotNull(binder)
    }

    @Test
    fun relayForwardsUntilTheTerminalEventAndThenDropsTheExecution() {
        val executionId = "relay-${System.nanoTime()}"
        val events = CopyOnWriteArrayList<String>()
        val completion = CompletableFuture<Bundle>()
        val (relay, callbackRef) = createRelay(executionId, completion, events)

        relay.onEvent(event(NodeJsRuntimeContract.EVENT_STDOUT, executionId))
        relay.onEvent(
            event(NodeJsRuntimeContract.EVENT_FINISHED, executionId).apply {
                putBundle(NodeJsRuntimeContract.KEY_EVENT_RESULT, Bundle().apply { putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, 0) })
            },
        )
        relay.onEvent(event(NodeJsRuntimeContract.EVENT_STDOUT, executionId))

        assertEquals(listOf(NodeJsRuntimeContract.EVENT_STDOUT, NodeJsRuntimeContract.EVENT_FINISHED), events)
        assertEquals(0, completion.get().getInt(NodeJsRuntimeContract.KEY_EXIT_CODE, -1))
        assertTrue(relay.isCleared)
        assertTrue("finished relay still reaches the execution callback", awaitCleared(callbackRef))
    }

    @Test
    fun relayRejectsAForeignTerminalEventAndForwardsNothingOnceCleared() {
        val executionId = "relay-foreign-${System.nanoTime()}"
        val events = CopyOnWriteArrayList<String>()
        val completion = CompletableFuture<Bundle>()
        val (relay, callbackRef) = createRelay(executionId, completion, events)

        relay.onEvent(
            event(NodeJsRuntimeContract.EVENT_FINISHED, "other-$executionId").apply {
                putBundle(NodeJsRuntimeContract.KEY_EVENT_RESULT, Bundle())
            },
        )
        assertTrue(completion.isCompletedExceptionally)
        assertTrue(runCatching { completion.get() }.exceptionOrNull() is ExecutionException)

        // A timed-out or dead execution is cleared by the host without a terminal event.
        relay.clear()
        relay.onEvent(event(NodeJsRuntimeContract.EVENT_STDERR, executionId))
        assertEquals(listOf(NodeJsRuntimeContract.EVENT_FINISHED), events)
        assertTrue("cleared relay still reaches the execution callback", awaitCleared(callbackRef))
    }

    @Test
    fun blockingContractRelayStaysOpenAfterFinishedUntilCleared() {
        val executionId = "relay-blocking-${System.nanoTime()}"
        val events = CopyOnWriteArrayList<String>()
        val (relay, _) = createRelay(executionId, null, events)

        relay.onEvent(event(NodeJsRuntimeContract.EVENT_FINISHED, executionId))
        relay.onEvent(event(NodeJsRuntimeContract.EVENT_STDOUT, executionId))
        assertFalse(relay.isCleared)
        relay.clear()
        relay.onEvent(null)
        relay.onEvent(event(NodeJsRuntimeContract.EVENT_STDOUT, executionId))
        assertEquals(listOf(NodeJsRuntimeContract.EVENT_FINISHED, NodeJsRuntimeContract.EVENT_STDOUT), events)
    }

    private fun createAndDestroyBroker(executionId: String): Pair<android.os.IBinder, WeakReference<Any>> {
        val broker = NodeJsHostCapabilityBroker(
            context,
            NodePluginScriptRequest(
                source = "",
                sourceName = "<exported-broker>",
                workingDirectory = context.cacheDir.absolutePath,
                sandboxRoot = context.cacheDir.absolutePath,
                executionId = executionId,
            ),
            "instrumentation",
        )
        val binder = broker.binder
        assertEquals(executionId, INodeJsHostCapabilityBroker.Stub.asInterface(binder).getBrokerInfo()
            .getString(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_ID))
        broker.destroy(null)
        assertNull(INodeJsHostCapabilityBroker.Stub.asInterface(binder).getBrokerInfo()
            .getString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO))
        return binder to WeakReference(broker)
    }

    private fun createRelay(
        executionId: String,
        completion: CompletableFuture<Bundle>?,
        events: MutableList<String>,
    ): Pair<NodeRuntimeCallbackRelay, WeakReference<Any>> {
        val callback = object : INodeJsRuntimeCallback.Stub() {
            override fun onEvent(event: Bundle?) {
                event?.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE)?.let(events::add)
            }
        }
        return NodeRuntimeCallbackRelay(executionId, completion, callback) to WeakReference(callback)
    }

    private fun event(type: String, executionId: String) = Bundle().apply {
        putString(NodeJsRuntimeContract.KEY_EVENT_TYPE, type)
        putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId)
        putString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "x")
    }

    private fun awaitCleared(reference: WeakReference<*>): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (reference.get() != null && SystemClock.elapsedRealtime() < deadline) {
            MemoryDiagnostics.forceGc()
            SystemClock.sleep(50L)
        }
        return reference.get() == null
    }
}
