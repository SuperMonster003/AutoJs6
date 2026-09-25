package org.autojs.plugin.ai.agent.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAgentContractTest {
    @Test
    fun identityAndDiscoveryMatchOfficialPluginAndProtectedAttachFlow() {
        assertEquals(PluginActions.INFO, AiAgentActions.INFO)
        assertEquals("org.autojs.plugin.AI_AGENT", AiAgentActions.SERVICE_ACTION)
        assertEquals("ai-agent", AiAgentActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", AiAgentActions.PLUGIN_PERMISSION)
        assertEquals("org.autojs.autojs6.action.AI_AGENT_ATTACH", AiAgentActions.ACTION_ATTACH_REQUEST)
        assertEquals("org.autojs.autojs6.extra.AI_AGENT_ATTACH_IDENTITY", AiAgentActions.EXTRA_ATTACH_IDENTITY)
        assertEquals("ai-agent", AiAgentIds.PLUGIN_ID)
        assertEquals("ai-agent", AiAgentIds.ENGINE)
        assertEquals("default", AiAgentIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.ai.agent", AiAgentIds.DEFAULT_PACKAGE_NAME)
        assertTrue(AiAgentIds.REQUIRED_HOST_VERSION_CODE > 0L)
        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, AiAgentCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("aiAgentContractVersion", AiAgentCapabilityKeys.CONTRACT_VERSION)
        assertEquals("aiAgentToolGroups", AiAgentCapabilityKeys.TOOL_GROUPS)
        assertEquals("aiAgentFeatures", AiAgentCapabilityKeys.FEATURES)
    }

    @Test
    fun negotiatesOnlyTheFrozenVersionOneContract() {
        assertEquals(1, AiAgentContract.CONTRACT_VERSION)
        assertTrue(AiAgentContract.supportsContractVersion(AiAgentContract.MIN_SUPPORTED_CONTRACT_VERSION))
        assertTrue(AiAgentContract.supportsContractVersion(AiAgentContract.MAX_SUPPORTED_CONTRACT_VERSION))
        assertFalse(AiAgentContract.supportsContractVersion(0))
        assertFalse(AiAgentContract.supportsContractVersion(2))
    }

    @Test
    fun keysAreUniqueTypedByRoleAndDoNotDuplicateSharedBridgeKeys() {
        val fields = AiAgentContract::class.java.fields.filter { it.name.startsWith("KEY_") }
        val values = fields.map { it.get(null) as String }
        assertEquals(values.size, values.toSet().size)
        val expected = setOf(
            "contractVersion", "linkConfigJson", "statusJson", "runRequestJson", "runEventJson",
            "runResponseJson", "runRefJson", "modelRequestJson", "modelEventJson", "modelRefJson",
            "modelBrokerInfoJson", "payloadFd", "errorCode", "errorMessage", "modelImageFds",
        )
        assertEquals(expected, values.toSet())
        fields.forEach { field ->
            val value = field.get(null) as String
            assertTrue(value, value.matches(Regex("[a-z][a-zA-Z0-9]+")))
            if (field.name.startsWith("KEY_RUN_")) assertTrue(value.startsWith("run"))
            if (field.name.startsWith("KEY_MODEL_")) assertTrue(value.startsWith("model"))
        }
        assertFalse(fields.any { it.name.startsWith("KEY_BRIDGE_") || it.name.startsWith("KEY_GRANT_") })
    }

    @Test
    fun closedStatesDistinguishBlockedRunsFromLiveOrQueuedWork() {
        assertEquals(setOf("detached", "attaching", "attached", "host_unavailable", "failed"), AiAgentContract.LINK_STATES)
        assertEquals(
            setOf("queued", "running", "waiting_input", "waiting_confirmation", "cancelling",
                "completed", "partial", "failed", "blocked", "cancelled"),
            AiAgentContract.RUN_STATES,
        )
        assertEquals(setOf("completed", "partial", "failed", "blocked", "cancelled"), AiAgentContract.TERMINAL_RUN_STATES)
        assertTrue(AiAgentContract.isTerminalRunState("blocked"))
        assertFalse(AiAgentContract.isTerminalRunState("cancelling"))
        assertFalse(AiAgentContract.isTerminalRunState(null))
        assertEquals(setOf("completed", "failed", "cancelled"), AiAgentContract.MODEL_TERMINAL_EVENTS)
    }

    @Test
    fun errorsHaveUniqueStableCodesIncludingAllRoadmapFailures() {
        val fields = AiAgentContract::class.java.fields.filter { it.name.startsWith("ERROR_") && it.type == String::class.java }
        val codes = fields.map { it.get(null) as String }
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(codes.toSet(), AiAgentContract.ERROR_CODES)
        assertTrue(AiAgentContract.ERROR_CODES.containsAll(setOf(
            "PLUGIN_UNAVAILABLE", "LINK_DETACHED", "HOST_UNAVAILABLE", "QUEUE_FULL",
            "RUN_NOT_FOUND", "RUN_NOT_INTERACTIVE", "TOOL_UNKNOWN", "TOOL_DISABLED",
            "TOOL_ARGUMENTS_INVALID", "CAPABILITY_DENIED", "QUOTA_EXCEEDED", "RATE_LIMITED",
            "LIMIT_EXCEEDED", "TARGET_UNSUPPORTED", "TARGET_UNAVAILABLE", "MODEL_FAILED",
            "MODEL_TIMEOUT", "DECISION_UNPARSABLE", "A11Y_SERVICE_NOT_RUNNING", "NODE_REF_STALE",
            "NODE_NOT_FOUND", "SCREEN_LOCKED", "SCRIPT_NOT_REGISTERED", "SCRIPT_TIMEOUT",
            "SCRIPT_FAILED", "OCR_PLUGIN_REQUIRED", "USER_DENIED", "USER_TIMEOUT", "BUDGET_EXCEEDED", "CANCELLED",
        )))
    }

    @Test
    fun budgetsAndTransportCeilingsEnforceRoadmapBounds() {
        assertEquals(8, AiAgentContract.MAX_RUN_QUEUE)
        assertEquals(1, AiAgentContract.MAX_CONCURRENT_RUNS_PER_LINK)
        assertEquals(200, AiAgentContract.MAX_STEPS)
        assertEquals(300, AiAgentContract.MAX_MODEL_CALLS)
        assertEquals(32 * 1024, AiAgentContract.MAX_EVENT_JSON_BYTES)
        assertEquals(128 * 1024, AiAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES)
        assertEquals(2 * 1024 * 1024, AiAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES)
        assertEquals(30, AiAgentContract.DEFAULT_MODEL_CALLS_PER_MINUTE)
        assertEquals(1_000_000L, AiAgentContract.DEFAULT_MAX_TOTAL_TOKENS_PER_LINK)
        assertEquals(HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES, AiAgentContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertEquals(HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES, AiAgentContract.MAX_BRIDGE_PAYLOAD_BYTES)
        assertTrue(AiAgentContract.MAX_CONCURRENT_TOOL_CALLS <= HostCapabilityContract.MAX_CONCURRENT_TOOL_CALLS)
        assertTrue(AiAgentContract.MAX_DURATION_MS <= AiAgentContract.MAX_DETACHED_DURATION_MS)
        assertTrue(AiAgentContract.MAX_EVENT_JSON_BYTES < AiAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES)
        assertTrue(AiAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES < AiAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES)
        assertTrue(AiAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES < 1024 * 1024)
        assertTrue(AiAgentContract.MAX_RESPONSE_SCHEMA_BYTES <= AiAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES)
        assertTrue(AiAgentContract.MAX_MODEL_OUTPUT_BYTES <= AiAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES)
        assertTrue(AiAgentContract.MAX_RUN_STEPS_RECORDED >= AiAgentContract.MAX_STEPS)
        assertTrue(AiAgentContract.MAX_SCRIPT_CATALOG_BYTES <= AiAgentContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        AiAgentContract::class.java.fields.filter { it.name.startsWith("MAX_") || it.name.startsWith("DEFAULT_") }
            .forEach { assertTrue(it.name, (it.get(null) as Number).toLong() > 0L) }
    }
}
