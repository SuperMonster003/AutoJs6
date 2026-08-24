package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure

/**
 * Actionable, non-sensitive hints appended to plugin-route error messages so a script author can
 * resolve "plugin not installed / disabled / no model" states without reading host sources.
 */
internal object AiPluginFailureHints {

    fun forFailure(failure: AiPluginAskFailure): String = when (failure) {
        AiPluginAskFailure.PROVIDER_NOT_FOUND ->
            "; the AI plugin is not installed, install it from Plugin Center"
        AiPluginAskFailure.PROVIDER_DISABLED ->
            "; the AI plugin is disabled, enable it in Plugin Center"
        AiPluginAskFailure.MODEL_NOT_FOUND ->
            "; no matching model is imported, import one on the plugin's model manager page"
        AiPluginAskFailure.MODEL_AMBIGUOUS ->
            "; multiple models are imported, pass an explicit modelId (see ai.models())"
        else -> ""
    }
}
