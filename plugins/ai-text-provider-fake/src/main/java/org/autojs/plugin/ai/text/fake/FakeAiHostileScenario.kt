package org.autojs.plugin.ai.text.fake

enum class FakeAiHostileScenario(val modelId: String) {
    CHUNK_BEFORE_START("fake.hostile.chunk-before-start"),
    SEQUENCE_GAP("fake.hostile.sequence-gap"),
    NO_CREDIT("fake.hostile.no-credit"),
    DUPLICATE_TERMINAL("fake.hostile.duplicate-terminal"),
    INVALID_FD_REFERENCE("fake.hostile.invalid-fd-ref"),
    STALL("fake.hostile.stall"),
    ;

    companion object {
        fun fromModelId(modelId: String): FakeAiHostileScenario? = values().firstOrNull { it.modelId == modelId }
    }
}
