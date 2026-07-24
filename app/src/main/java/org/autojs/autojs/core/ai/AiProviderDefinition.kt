package org.autojs.autojs.core.ai

/**
 * Built-in provider protocol kinds.
 *
 * Model names deliberately remain profile data because providers change them independently
 * of AutoJs6 releases.
 */
enum class AiProviderDefinition(
    val id: String,
    val defaultBaseUrl: String?,
) {
    OPENAI(
        id = "openai",
        defaultBaseUrl = "https://api.openai.com/v1",
    ),
    ANTHROPIC(
        id = "anthropic",
        defaultBaseUrl = "https://api.anthropic.com/v1",
    ),
    GEMINI(
        id = "gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
    ),
    DEEPSEEK(
        id = "deepseek",
        defaultBaseUrl = "https://api.deepseek.com",
    ),
    OPENROUTER(
        id = "openrouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
    ),
    OPENAI_COMPATIBLE(
        id = "openai-compatible",
        defaultBaseUrl = null,
    ),
    ;

    companion object {

        @JvmStatic
        fun fromId(id: String): AiProviderDefinition? {
            val normalized = id.trim().lowercase()
            return entries.firstOrNull { it.id == normalized }
        }

        @JvmStatic
        fun requireById(id: String): AiProviderDefinition =
            fromId(id) ?: throw AiProviderValidationException(
                field = AiProviderValidationField.PROVIDER,
                code = AiProviderValidationCode.UNSUPPORTED,
                message = "Unsupported AI provider: $id",
            )
    }
}
