package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class NodeCapabilityBrokerV2InstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun operationDeclarationsAreExplicitAndExecutionOwned() {
        val expectedOperationCount = NodeBridgeModules.supportedMethodsByModule.values.sumOf { it.size } + 3
        assertEquals(expectedOperationCount, NodeCapabilityBrokerPolicy.operationDeclarations.size)

        val fetch = NodeCapabilityBrokerPolicy.declarationFor(NodeBridgeModules.FETCH, "request")
        assertNotNull(fetch)
        fetch!!
        assertEquals(listOf(NodeBridgePermissionManifest.NETWORK), fetch.requiredCapabilities)
        assertTrue(fetch.parameterValidated)
        assertTrue(fetch.sizeLimited)
        assertTrue(fetch.cancellable)
        assertTrue(fetch.audited)
        assertTrue(fetch.executionOwned)

        val pinyinConvert = NodeCapabilityBrokerPolicy.declarationFor(NodeBridgeModules.PINYIN, "convert")
        assertNotNull(pinyinConvert)
        assertEquals(listOf(NodeBridgePermissionManifest.PINYIN), pinyinConvert!!.requiredCapabilities)
        assertEquals(NodeCapabilityBrokerPolicy.PINYIN_CONVERT_MAX_ARGS_BYTES, pinyinConvert.maxArgsBytes)

        val pinyin4jReadings = NodeCapabilityBrokerPolicy.declarationFor(NodeBridgeModules.PINYIN4J, "readings")
        assertNotNull(pinyin4jReadings)
        assertEquals(listOf(NodeBridgePermissionManifest.PINYIN4J), pinyin4jReadings!!.requiredCapabilities)
        assertEquals(NodeCapabilityBrokerPolicy.DEFAULT_MAX_ARGS_BYTES, pinyin4jReadings.maxArgsBytes)

        val lifecycle = NodeCapabilityBrokerPolicy.declarationFor(
            NodeLifecycleCheckpointStore.BRIDGE_MODULE_NAME,
            NodeLifecycleCheckpointStore.METHOD_CHECKPOINT,
        )
        assertNotNull(lifecycle)
        assertTrue(lifecycle!!.requiredCapabilities.isEmpty())

        assertTrue(
            NodeCapabilityBrokerPolicy.operationDeclarations.all { declaration ->
                declaration.parameterValidated &&
                    declaration.sizeLimited &&
                    declaration.cancellable &&
                    declaration.audited &&
                    declaration.executionOwned
            },
        )
    }

    @Test
    fun preflightRejectsUndeclaredInvalidAndOversizedRequests() {
        val undeclared = NodeBridgeRequest(
            id = "undeclared",
            module = NodeBridgeModules.TOAST,
            method = "missing",
            argsJson = "[]",
            timeoutMs = 1000L,
        )
        val undeclaredError = NodeCapabilityBrokerPolicy.preflight(undeclared)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, undeclaredError?.code)
        assertEquals(NodeBridgeErrorCategory.UNAVAILABLE, undeclaredError?.category)

        val invalidArgs = NodeBridgeRequest(
            id = "invalid-args",
            module = NodeBridgeModules.TOAST,
            method = "showToast",
            argsJson = "{}",
            timeoutMs = 1000L,
            permissions = listOf(NodeBridgePermissionManifest.TOAST),
        )
        val invalidError = NodeCapabilityBrokerPolicy.preflight(invalidArgs)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, invalidError?.code)

        val oversized = NodeBridgeRequest(
            id = "oversized",
            module = NodeBridgeModules.TOAST,
            method = "showToast",
            argsJson = "[\"${"x".repeat(NodeCapabilityBrokerPolicy.DEFAULT_MAX_ARGS_BYTES + 1)}\"]",
            timeoutMs = 1000L,
            permissions = listOf(NodeBridgePermissionManifest.TOAST),
        )
        val oversizedError = NodeCapabilityBrokerPolicy.preflight(oversized)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, oversizedError?.code)
        assertEquals(NodeBridgeErrorCategory.RESOURCE_LIMIT, oversizedError?.category)
    }

    @Test
    fun auditLogIsExecutionOwnedQuotaBoundAndReducesToDiagnostics() {
        val now = AtomicLong(1000L)
        val audit = NodeCapabilityBrokerAuditLog(
            ownerExecutionId = "broker-test-execution",
            maxRows = 2,
            clock = { now.addAndGet(10L) },
        )

        val first = request("first")
        val second = request("second")
        val third = request("third")
        audit.record(first, NodeBridgeResponse.success(first.id, "true"))
        audit.record(second, NodeBridgeResponse.failure(second.id, NodeBridgeError.timeout(second.module, second.method)))
        audit.record(third, NodeBridgeResponse.success(third.id, "true"))

        val rows = audit.snapshot()
        assertEquals(2, rows.size)
        assertEquals(listOf("second", "third"), rows.map { it.requestId })
        assertTrue(rows.all { it.ownerExecutionId == "broker-test-execution" })
        assertEquals("failed", rows.first().outcome)
        assertEquals("succeeded", rows.last().outcome)

        val diagnostics = audit.nativeDiagnostics()
        assertEquals(NodeCapabilityBrokerPolicy.SCHEMA, diagnostics["embedded_script.capability_broker.schema"])
        assertEquals("3", diagnostics["embedded_script.capability_broker.audit_total_count"])
        assertEquals("2", diagnostics["embedded_script.capability_broker.audit_retained_count"])
        assertEquals("1", diagnostics["embedded_script.capability_broker.audit_dropped_count"])
        assertTrue(diagnostics["embedded_script.capability_broker.audit_last_json"].orEmpty().contains("third"))
    }

    @Test
    fun androidProviderPublishesBrokerAuditDiagnostics() {
        val provider = AndroidNodeBridgeCapabilityProvider(
            context = context,
            engineInfo = NodeBridgeEngineInfo.fromContext(context, id = "broker-provider-test"),
        )
        val request = NodeBridgeRequest(
            id = "provider-undeclared",
            module = NodeBridgeModules.TOAST,
            method = "missing",
            argsJson = "[]",
            timeoutMs = 1000L,
        )
        val responseRef = AtomicReference<NodeBridgeResponse>()

        provider.dispatch(request) { response -> responseRef.set(response) }

        val response = responseRef.get()
        assertNotNull(response)
        assertFalse(response.ok)
        assertEquals(NodeBridgeErrorCategory.UNAVAILABLE, response.error?.category)

        val diagnostics = provider.nativeDiagnostics()
        assertEquals("1", diagnostics["embedded_script.capability_broker.audit_total_count"])
        assertEquals("1", diagnostics["embedded_script.capability_broker.audit_rejected_count"])
        assertTrue(
            diagnostics["embedded_script.capability_broker.audit_last_json"].orEmpty(),
            diagnostics["embedded_script.capability_broker.audit_last_json"].orEmpty().contains("provider-undeclared"),
        )
        provider.destroy(NodeBridgeError.processDead("broker test cleanup"))
    }

    private fun request(id: String): NodeBridgeRequest {
        return NodeBridgeRequest(
            id = id,
            module = NodeBridgeModules.TOAST,
            method = "showToast",
            argsJson = "[\"hello\"]",
            timeoutMs = 1000L,
            permissions = listOf(NodeBridgePermissionManifest.TOAST),
        )
    }
}
