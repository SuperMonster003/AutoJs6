package org.autojs.plugin.dexcompiler.fake

enum class FakeDexCompilerScenario(val metadataValue: String) {
    NORMAL("normal"),
    PROGRESS_BEFORE_START("progress-before-start"),
    DUPLICATE_TERMINAL("duplicate-terminal"),
    INVALID_OUTPUT("invalid-output"),
    UNNEGOTIATED_DEX_VERSION("unnegotiated-dex-version"),
    STALL("stall"),
    ;

    companion object {
        const val METADATA_KEY = "org.autojs.plugin.dexcompiler.fake.SCENARIO"

        fun fromMetadata(value: String): FakeDexCompilerScenario = values().firstOrNull {
            it.metadataValue == value
        } ?: throw IllegalArgumentException("Unknown fake DEX compiler scenario: $value")
    }
}
