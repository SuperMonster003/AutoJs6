package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure

/**
 * Actionable, non-sensitive hints appended to plugin-route error messages so a script author can
 * resolve plugin availability and target-selection states without reading host sources.
 */
internal object AiPluginFailureHints {

    fun forFailure(failure: AiPluginAskFailure): String = when (failure) {
        AiPluginAskFailure.AI_PROVIDER_UNAVAILABLE ->
            "; the AI plugin is not installed, install it from Plugin Center"
        AiPluginAskFailure.AI_PROVIDER_DISABLED ->
            "; the AI plugin is disabled, enable it in Plugin Center"
        AiPluginAskFailure.TARGET_NOT_FOUND ->
            "; the requested target does not exist; inspect ai.catalog() and pass its exact id"
        AiPluginAskFailure.TARGET_NOT_CONFIGURED ->
            "; configure the target in the plugin, or select another target from ai.catalog()"
        AiPluginAskFailure.TARGET_UNAVAILABLE ->
            "; the selected target is temporarily unavailable; inspect its catalog availability"
        AiPluginAskFailure.BACKEND_UNAVAILABLE ->
            "; the selected backend is unavailable; inspect the target's backendProfiles"
        else -> ""
    }
}
