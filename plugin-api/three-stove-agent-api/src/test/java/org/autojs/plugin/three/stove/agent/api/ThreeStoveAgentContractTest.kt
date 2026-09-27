package org.autojs.plugin.three.stove.agent.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeStoveAgentContractTest {
    @Test
    fun identityAndDiscoveryMatchOfficialPluginAndProtectedAttachFlow() {
        assertEquals(PluginActions.INFO, ThreeStoveAgentActions.INFO)
        assertEquals("org.autojs.plugin.THREE_STOVE_AGENT", ThreeStoveAgentActions.SERVICE_ACTION)
        assertEquals("three-stove-agent", ThreeStoveAgentActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", ThreeStoveAgentActions.PLUGIN_PERMISSION)
        assertEquals("org.autojs.autojs6.action.THREE_STOVE_AGENT_ATTACH", ThreeStoveAgentActions.ACTION_ATTACH_REQUEST)
        assertEquals("org.autojs.autojs6.extra.THREE_STOVE_AGENT_ATTACH_IDENTITY", ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY)
        assertEquals("three-stove-agent", ThreeStoveAgentIds.PLUGIN_ID)
        assertEquals("three-stove-agent", ThreeStoveAgentIds.ENGINE)
        assertEquals("default", ThreeStoveAgentIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.three.stove.agent", ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
        assertTrue(ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE > 0L)
        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, ThreeStoveAgentCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("threeStoveAgentContractVersion", ThreeStoveAgentCapabilityKeys.CONTRACT_VERSION)
        assertEquals("threeStoveAgentToolGroups", ThreeStoveAgentCapabilityKeys.TOOL_GROUPS)
        assertEquals("threeStoveAgentFeatures", ThreeStoveAgentCapabilityKeys.FEATURES)
    }

    @Test
    fun negotiatesOnlyTheFrozenVersionTwoContract() {
        assertEquals(2, ThreeStoveAgentContract.CONTRACT_VERSION)
        assertTrue(ThreeStoveAgentContract.supportsContractVersion(ThreeStoveAgentContract.MIN_SUPPORTED_CONTRACT_VERSION))
        assertTrue(ThreeStoveAgentContract.supportsContractVersion(ThreeStoveAgentContract.MAX_SUPPORTED_CONTRACT_VERSION))
        assertFalse(ThreeStoveAgentContract.supportsContractVersion(0))
        assertFalse(ThreeStoveAgentContract.supportsContractVersion(1))
        assertFalse(ThreeStoveAgentContract.supportsContractVersion(3))
    }

    @Test
    fun keysAreUniqueTypedByRoleAndDoNotDuplicateSharedBridgeKeys() {
        val fields = ThreeStoveAgentContract::class.java.fields.filter { it.name.startsWith("KEY_") }
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
        assertEquals(setOf("detached", "attaching", "attached", "host_unavailable", "failed"), ThreeStoveAgentContract.LINK_STATES)
        assertEquals(
            setOf("queued", "running", "waiting_input", "waiting_confirmation", "cancelling",
                "completed", "partial", "failed", "blocked", "cancelled"),
            ThreeStoveAgentContract.RUN_STATES,
        )
        assertEquals(setOf("completed", "partial", "failed", "blocked", "cancelled"), ThreeStoveAgentContract.TERMINAL_RUN_STATES)
        assertTrue(ThreeStoveAgentContract.isTerminalRunState("blocked"))
        assertFalse(ThreeStoveAgentContract.isTerminalRunState("cancelling"))
        assertFalse(ThreeStoveAgentContract.isTerminalRunState(null))
        assertEquals(setOf("completed", "failed", "cancelled"), ThreeStoveAgentContract.MODEL_TERMINAL_EVENTS)
    }

    @Test
    fun errorsHaveUniqueStableCodesIncludingAllRoadmapFailures() {
        val fields = ThreeStoveAgentContract::class.java.fields.filter { it.name.startsWith("ERROR_") && it.type == String::class.java }
        val codes = fields.map { it.get(null) as String }
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(codes.toSet(), ThreeStoveAgentContract.ERROR_CODES)
        assertTrue(ThreeStoveAgentContract.ERROR_CODES.containsAll(setOf(
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
        assertEquals(8, ThreeStoveAgentContract.MAX_RUN_QUEUE)
        assertEquals(1, ThreeStoveAgentContract.MAX_CONCURRENT_RUNS_PER_LINK)
        assertEquals(200, ThreeStoveAgentContract.MAX_STEPS)
        assertEquals(300, ThreeStoveAgentContract.MAX_MODEL_CALLS)
        assertEquals(32 * 1024, ThreeStoveAgentContract.MAX_EVENT_JSON_BYTES)
        assertEquals(128 * 1024, ThreeStoveAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES)
        assertEquals(2 * 1024 * 1024, ThreeStoveAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES)
        assertEquals(30, ThreeStoveAgentContract.DEFAULT_MODEL_CALLS_PER_MINUTE)
        assertEquals(1_000_000L, ThreeStoveAgentContract.DEFAULT_MAX_TOTAL_TOKENS_PER_LINK)
        assertEquals(HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES, ThreeStoveAgentContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertEquals(HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES, ThreeStoveAgentContract.MAX_BRIDGE_PAYLOAD_BYTES)
        assertTrue(ThreeStoveAgentContract.MAX_CONCURRENT_TOOL_CALLS <= HostCapabilityContract.MAX_CONCURRENT_TOOL_CALLS)
        assertTrue(ThreeStoveAgentContract.MAX_DURATION_MS <= ThreeStoveAgentContract.MAX_DETACHED_DURATION_MS)
        assertTrue(ThreeStoveAgentContract.MAX_EVENT_JSON_BYTES < ThreeStoveAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES)
        assertTrue(ThreeStoveAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES < ThreeStoveAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES)
        assertTrue(ThreeStoveAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES < 1024 * 1024)
        assertTrue(ThreeStoveAgentContract.MAX_RESPONSE_SCHEMA_BYTES <= ThreeStoveAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES)
        assertTrue(ThreeStoveAgentContract.MAX_MODEL_OUTPUT_BYTES <= ThreeStoveAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES)
        assertTrue(ThreeStoveAgentContract.MAX_RUN_STEPS_RECORDED >= ThreeStoveAgentContract.MAX_STEPS)
        assertTrue(ThreeStoveAgentContract.MAX_SCRIPT_CATALOG_BYTES <= ThreeStoveAgentContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        ThreeStoveAgentContract::class.java.fields.filter { it.name.startsWith("MAX_") || it.name.startsWith("DEFAULT_") }
            .forEach { assertTrue(it.name, (it.get(null) as Number).toLong() > 0L) }
    }
}
