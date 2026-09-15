package org.autojs.plugin.mcp.server.fake

import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds

/**
 * Deterministic metadata and payload builders shared by the services and the JVM tests. Pure
 * Kotlin on purpose: the Android `Bundle` and `PluginInfo` assembly lives in the service layer.
 */
object FakeMcpServerFixture {

    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"

    const val NAME = "AutoJs6 MCP Server Conformance"
    const val DESCRIPTION = "Test-only MCP Server fixture with deterministic and hostile Binder behaviour"
    const val AUTHOR = "AutoJs6 conformance"
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1L
    const val VERSION_DATE = "Sep 16, 2026"
    const val SDK_VERSION = "0.0.0-fake"
    const val CLIENT_NAME = "mcp-server-conformance"

    val TOOL_GROUPS: List<String> = listOf("device", "ui", "script")
    val PROTOCOL_VERSIONS: List<String> = listOf("2025-06-18", "2025-03-26")

    /** One above the newest version the host accepts (`McpServerContract.supportsContractVersion`). */
    const val UNSUPPORTED_CONTRACT_VERSION = McpServerContract.MAX_CONTRACT_VERSION + 1

    /** Sixteen times the `statusLastError` cap; the host must cut it to the cap. */
    const val OVERSIZED_ERROR_BYTES = McpServerContract.MAX_ERROR_MESSAGE_BYTES * 16

    /**
     * Padding characters that lift one bridge request one KiB above the inline JSON cap. The cap
     * counts UTF-8 bytes while a Parcel carries UTF-16, so a three-byte character (U+4E2D) makes
     * the request exceed the cap at about 350 KiB of Parcel, well inside the 1 MiB Binder limit.
     */
    const val OVERSIZED_REQUEST_PADDING_CHARS = (McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES + 1024) / 3 + 1
    const val OVERSIZED_PADDING_CHAR = '中'
    const val BINDER_TRANSACTION_BUDGET_BYTES = 900 * 1024

    /** Delay between the last `running` callback and the self-kill of the death scenario. */
    const val DEATH_DELAY_MS = 400L

    const val PROBE_TIMEOUT_MS = 5_000L
    const val PROBE_REQUEST_ID = "fake-probe"
    const val OVERSIZED_REQUEST_ID = "fake-oversized"
    const val ERROR_CODE_FIXTURE_OVERSIZED = "fixture_oversized"
    const val EVENT_TEXT_PREFIX = "fixture running: "

    // Fixture-only status keys, so the host test can read what the fixture observed.
    const val KEY_FAKE_SCENARIO = "fakeScenario"
    const val KEY_FAKE_STATUS_CALLBACKS = "fakeStatusCallbacks"
    const val KEY_FAKE_EVENT_CALLBACKS = "fakeEventCallbacks"
    const val KEY_FAKE_BROKER_INFO_RECEIVED = "fakeBrokerInfoReceived"
    const val KEY_FAKE_BROKER_MAX_CONCURRENT_CALLS = "fakeBrokerMaxConcurrentCalls"
    const val KEY_FAKE_PROBE_RESPONSE_JSON = "fakeProbeResponseJson"
    const val KEY_FAKE_OVERSIZED_RESPONSE_JSON = "fakeOversizedResponseJson"

    fun contractVersion(scenario: FakeMcpServerScenario): Int = when (scenario) {
        FakeMcpServerScenario.WRONG_CONTRACT_VERSION -> UNSUPPORTED_CONTRACT_VERSION
        else -> McpServerContract.CONTRACT_VERSION
    }

    fun requiredHostVersion(): Long = McpServerIds.REQUIRED_HOST_VERSION_CODE

    fun endpoint(port: Int): String =
        "http://${McpServerContract.LOOPBACK_HOST}:$port${McpServerContract.ENDPOINT_PATH}"

    /** ASCII pattern of [OVERSIZED_ERROR_BYTES] bytes, so byte and character counts agree. */
    fun oversizedErrorMessage(): String = pattern(OVERSIZED_ERROR_BYTES)

    fun probeRequestJson(id: String = PROBE_REQUEST_ID): String =
        bridgeRequestJson(id, "device", "info", "[]")

    /** A valid `device.info` request whose padding puts it above the inline JSON cap in UTF-8. */
    fun oversizedRequestJson(id: String = OVERSIZED_REQUEST_ID): String {
        val padding = buildString(OVERSIZED_REQUEST_PADDING_CHARS) {
            repeat(OVERSIZED_REQUEST_PADDING_CHARS) { append(OVERSIZED_PADDING_CHAR) }
        }
        return bridgeRequestJson(id, "device", "info", "[\"$padding\"]")
    }

    private fun bridgeRequestJson(id: String, module: String, method: String, argsJson: String): String =
        "{\"id\":\"$id\",\"module\":\"$module\",\"method\":\"$method\",\"args\":$argsJson," +
                "\"timeoutMs\":$PROBE_TIMEOUT_MS,\"permissions\":[\"device\"]}"

    private fun pattern(length: Int): String {
        val digits = "0123456789abcdef"
        return buildString(length) {
            while (this.length < length) {
                append(digits, 0, minOf(digits.length, length - this.length))
            }
        }
    }
}
