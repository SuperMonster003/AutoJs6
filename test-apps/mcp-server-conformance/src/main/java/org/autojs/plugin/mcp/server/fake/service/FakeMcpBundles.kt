package org.autojs.plugin.mcp.server.fake.service

import android.os.Bundle
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.mcp.server.api.McpServerCapabilityKeys
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.autojs.plugin.mcp.server.fake.FakeMcpCallbackPlan
import org.autojs.plugin.mcp.server.fake.FakeMcpServerFixture
import org.autojs.plugin.mcp.server.fake.FakeMcpServerScenario
import org.autojs.plugin.mcp.server.fake.FakeMcpSessionMachine

/** Android-side assembly of the contract parcelables from the pure fixture values. */
internal object FakeMcpBundles {

    fun pluginInfo(scenario: FakeMcpServerScenario): PluginInfo = PluginInfo().apply {
        name = FakeMcpServerFixture.NAME
        description = FakeMcpServerFixture.DESCRIPTION
        instruction = null
        author = FakeMcpServerFixture.AUTHOR
        collaborators = null
        versionName = FakeMcpServerFixture.VERSION_NAME
        versionCode = FakeMcpServerFixture.VERSION_CODE
        versionDate = FakeMcpServerFixture.VERSION_DATE
        id = McpServerIds.PLUGIN_ID
        engine = McpServerIds.ENGINE
        variant = McpServerIds.VARIANT_DEFAULT
        supportedAbis = emptyArray()
        capabilities = capabilities(scenario)
    }

    fun capabilities(scenario: FakeMcpServerScenario): Bundle = Bundle().apply {
        putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, FakeMcpServerFixture.requiredHostVersion())
        putInt(McpServerCapabilityKeys.CONTRACT_VERSION, FakeMcpServerFixture.contractVersion(scenario))
        putStringArray(McpServerCapabilityKeys.TOOL_GROUPS, FakeMcpServerFixture.TOOL_GROUPS.toTypedArray())
        putStringArray(McpServerCapabilityKeys.PROTOCOL_VERSIONS, FakeMcpServerFixture.PROTOCOL_VERSIONS.toTypedArray())
        putString(McpServerCapabilityKeys.SDK_VERSION, FakeMcpServerFixture.SDK_VERSION)
        putInt(McpServerCapabilityKeys.SETTINGS_VERSION, McpServerContract.SETTINGS_VERSION)
        putBoolean(McpServerCapabilityKeys.USER_STOPPED, false)
    }

    /** Validates the host's config bundle and returns the requested port. */
    fun validatedPort(config: Bundle?): Int {
        val bundle = config ?: throw IllegalArgumentException("${McpServerContract.ERROR_INVALID_CONFIG}: config is required")
        val contractVersion = bundle.getInt(McpServerContract.KEY_CONTRACT_VERSION, 0)
        if (!McpServerContract.supportsContractVersion(contractVersion)) {
            throw IllegalArgumentException("${McpServerContract.ERROR_UNSUPPORTED_CONTRACT}: config contract version $contractVersion")
        }
        val port = bundle.getInt(McpServerContract.KEY_SERVER_CONFIG_PORT, McpServerContract.DEFAULT_PORT)
        if (port !in McpServerContract.MIN_PORT..McpServerContract.MAX_PORT) {
            throw IllegalArgumentException("${McpServerContract.ERROR_INVALID_CONFIG}: port $port")
        }
        return port
    }

    fun bindScope(config: Bundle?): String =
        config?.getString(McpServerContract.KEY_SERVER_CONFIG_BIND_SCOPE) ?: McpServerContract.BIND_SCOPE_LOOPBACK

    fun protocolMode(config: Bundle?): String =
        config?.getString(McpServerContract.KEY_SERVER_CONFIG_PROTOCOL_MODE) ?: McpServerContract.PROTOCOL_MODE_STATEFUL

    fun bridgeRequest(json: String): Bundle = Bundle().apply {
        putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.CONTRACT_VERSION)
        putString(McpServerContract.KEY_BRIDGE_REQUEST_JSON, json)
        putString(McpServerContract.KEY_BRIDGE_CLIENT_NAME, FakeMcpServerFixture.CLIENT_NAME)
    }

    fun event(type: String, text: String): Bundle = Bundle().apply {
        putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.CONTRACT_VERSION)
        putString(McpServerContract.KEY_EVENT_TYPE, type)
        putString(McpServerContract.KEY_EVENT_TEXT, text)
        putLong(McpServerContract.KEY_EVENT_TIMESTAMP, System.currentTimeMillis())
    }

    fun status(
        scenario: FakeMcpServerScenario,
        machine: FakeMcpSessionMachine,
        plan: FakeMcpCallbackPlan,
        bindScope: String,
        protocolMode: String,
        observed: Observed,
    ): Bundle = Bundle().apply {
        putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.CONTRACT_VERSION)
        putString(McpServerContract.KEY_STATUS_STATE, machine.contractState)
        putStringArray(McpServerContract.KEY_STATUS_ENDPOINTS, machine.endpoints.toTypedArray())
        putInt(McpServerContract.KEY_STATUS_PORT, machine.boundPort)
        putString(McpServerContract.KEY_STATUS_BIND_SCOPE, bindScope)
        putString(McpServerContract.KEY_STATUS_PROTOCOL_MODE, protocolMode)
        putInt(McpServerContract.KEY_STATUS_PAIRED_CLIENT_COUNT, 0)
        putInt(McpServerContract.KEY_STATUS_ACTIVE_SESSION_COUNT, 0)
        putLong(McpServerContract.KEY_STATUS_STARTED_AT, machine.startedAt)
        putBoolean(McpServerContract.KEY_STATUS_HOST_AVAILABLE, observed.brokerInfoReceived)
        if (plan.oversizedStatus) {
            putString(McpServerContract.KEY_STATUS_LAST_ERROR_CODE, FakeMcpServerFixture.ERROR_CODE_FIXTURE_OVERSIZED)
            putString(McpServerContract.KEY_STATUS_LAST_ERROR, FakeMcpServerFixture.oversizedErrorMessage())
        } else {
            machine.lastReasonCode?.let { putString(McpServerContract.KEY_STATUS_LAST_ERROR_CODE, it) }
        }
        putString(FakeMcpServerFixture.KEY_FAKE_SCENARIO, scenario.metadataValue)
        putInt(FakeMcpServerFixture.KEY_FAKE_STATUS_CALLBACKS, observed.statusCallbacks)
        putInt(FakeMcpServerFixture.KEY_FAKE_EVENT_CALLBACKS, observed.eventCallbacks)
        putBoolean(FakeMcpServerFixture.KEY_FAKE_BROKER_INFO_RECEIVED, observed.brokerInfoReceived)
        putInt(FakeMcpServerFixture.KEY_FAKE_BROKER_MAX_CONCURRENT_CALLS, observed.brokerMaxConcurrentCalls)
        observed.probeResponseJson?.let { putString(FakeMcpServerFixture.KEY_FAKE_PROBE_RESPONSE_JSON, it) }
        observed.oversizedResponseJson?.let { putString(FakeMcpServerFixture.KEY_FAKE_OVERSIZED_RESPONSE_JSON, it) }
    }

    /** What the session saw from the host so far; copied under the session lock. */
    data class Observed(
        val statusCallbacks: Int,
        val eventCallbacks: Int,
        val brokerInfoReceived: Boolean,
        val brokerMaxConcurrentCalls: Int,
        val probeResponseJson: String?,
        val oversizedResponseJson: String?,
    )
}
