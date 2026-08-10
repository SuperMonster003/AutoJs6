package org.autojs.plugin.lua.runtime.fake

enum class FakeLuaRuntimeScenario(val metadataValue: String) {
    NORMAL("normal"),
    MALFORMED_RUNTIME_INFO("malformed-runtime-info"),
    NULL_BINDING("null-binding"),
    STALL_RUNTIME_INFO("stall-runtime-info"),
    STALL_CREATE("stall-create"),
    LATE_CREATE_RETURN_SESSION("late-create-return-session"),
    CALLBACK_BEFORE_START("callback-before-start"),
    DUPLICATE_TERMINAL("duplicate-terminal"),
    IGNORE_CANCEL("ignore-cancel"),
    ZERO_CREDIT_OUTPUT("zero-credit-output"),
    SEQUENCE_GAP("sequence-gap"),
    SEQUENCE_REPLAY("sequence-replay"),
    WRONG_REQUEST_ID("wrong-request-id"),
    BUSY_SECOND_CREATE("busy-second-create"),
    ;

    companion object {
        const val METADATA_KEY = "org.autojs.plugin.lua.runtime.fake.SCENARIO"

        fun fromMetadata(value: String): FakeLuaRuntimeScenario = values().firstOrNull {
            it.metadataValue == value
        } ?: throw IllegalArgumentException("Unknown fake Lua runtime scenario: $value")
    }
}
