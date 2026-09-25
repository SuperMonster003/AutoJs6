package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiJsonSyntaxValidator
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiToolResult
import org.autojs.plugin.ai.provider.api.AiToolResultBatch

/** Materialized, descriptor-independent data. Diagnostic strings never contain arguments/results. */
internal class AiPluginToolCall(val callId: String, val name: String, val argumentsJson: String) {
    init {
        AiValidation.requireUtf8(callId, 1, AiProviderLimits.MAX_TOOL_CALL_ID_BYTES, "Tool call ID")
        require(callId.none(Character::isISOControl))
        require(AiValidation.STABLE_ID.matches(name))
        AiValidation.requireUtf8(name, 1, AiProviderLimits.MAX_TOOL_NAME_BYTES, "Tool name")
        AiValidation.requireUtf8(argumentsJson, 1, AiProviderLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES.toInt(), "Tool arguments")
        AiJsonSyntaxValidator.requireValid(argumentsJson)
    }

    override fun toString() = "AiPluginToolCall(name=$name, argumentBytes=${AiValidation.utf8Size(argumentsJson)})"
}

internal class AiPluginToolResult(val callId: String, val output: String, val isError: Boolean = false) {
    fun toProtocol(): AiToolResult {
        val bytes = output.toByteArray(Charsets.UTF_8)
        return AiToolResult(callId, AiPayloadReference(
            mimeType = AiProviderMimeType.PLAIN, declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes, charset = "utf-8",
        ), isError)
    }

    override fun toString() = "AiPluginToolResult(outputBytes=${AiValidation.utf8Size(output)}, isError=$isError)"
}

internal fun List<AiPluginToolResult>.toProtocolToolResults() = AiToolResultBatch(map { it.toProtocol() })

/** Event route for Agent-owned tools, independently of text streaming. Script ask stays text-only. */
internal fun interface AiPluginToolRunner {
    fun startTools(request: AiPluginAskRequest, stream: Boolean, onEvent: (AiPluginStreamEvent) -> Unit): AiPluginAskHandle
}
