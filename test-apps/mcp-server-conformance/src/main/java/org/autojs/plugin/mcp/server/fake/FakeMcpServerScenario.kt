package org.autojs.plugin.mcp.server.fake

/**
 * One manifest component per scenario (the DEX compiler fixture pattern): the service verifies
 * on creation that its compiled scenario matches the `SCENARIO` meta-data of its component.
 */
enum class FakeMcpServerScenario(val metadataValue: String) {
    /** Deterministic plugin: valid metadata, one status callback per transition, a real broker probe. */
    NORMAL("normal"),

    /** `mcpServerContractVersion` one above the newest version the host understands. */
    WRONG_CONTRACT_VERSION("wrong-contract-version"),

    /** `statusLastError` far beyond its 4 KiB cap and one broker request beyond the inline JSON cap. */
    OVERSIZED_BUNDLE("oversized-bundle"),

    /** Every status and event callback twice, plus one more status callback after `close()`. */
    DUPLICATE_CALLBACK("duplicate-callback"),

    /** Reports `running`, then kills its own process while the host still holds the session. */
    DEATH_CALLBACK("death-callback"),
    ;

    companion object {
        const val METADATA_KEY = "org.autojs.plugin.mcp.server.fake.SCENARIO"

        fun fromMetadata(value: String): FakeMcpServerScenario = values().firstOrNull {
            it.metadataValue == value
        } ?: throw IllegalArgumentException("Unknown fake MCP Server scenario: $value")
    }
}
