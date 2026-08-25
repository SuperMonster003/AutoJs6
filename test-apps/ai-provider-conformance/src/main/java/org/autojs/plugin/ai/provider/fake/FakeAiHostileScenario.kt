package org.autojs.plugin.ai.provider.fake

enum class FakeAiHostileScenario(val targetId: String) {
    CHUNK_BEFORE_START("local:hostile.chunk-before-start"),
    SEQUENCE_GAP("local:hostile.sequence-gap"),
    NO_CREDIT("local:hostile.no-credit"),
    DUPLICATE_TERMINAL("local:hostile.duplicate-terminal"),
    INVALID_FD_REFERENCE("local:hostile.invalid-fd-ref"),
    COMPLETION_FD("local:hostile.completion-fd"),
    TOOL_FD("local:hostile.tool-fd"),
    WRONG_UID("local:hostile.wrong-uid"),
    PROCESS_DEATH("local:hostile.process-death"),
    STALL("local:hostile.stall"),
    ;

    companion object {
        fun fromTargetId(targetId: String): FakeAiHostileScenario? = values().firstOrNull { it.targetId == targetId }
    }
}
