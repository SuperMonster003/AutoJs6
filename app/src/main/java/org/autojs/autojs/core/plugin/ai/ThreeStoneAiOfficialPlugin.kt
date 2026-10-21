package org.autojs.autojs.core.plugin.ai

/**
 * Stable identity of 3-Stone AI, the official AI plugin.
 *
 * Scripts use this plugin and its declared default target when no selector is provided. An
 * explicit `plugin` selector may still override the provider or choose another exact component.
 */
internal object ThreeStoneAiOfficialPlugin {
    const val DISPLAY_NAME = "3-Stone AI"
    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.threestoneai"
    const val CLASS_NAME = "$PACKAGE_NAME.provider.ThreeStoneAiProviderService"
    const val RUNTIME_CLASS_NAME = "$PACKAGE_NAME.provider.ThreeStoneAiRuntimeService"
    const val RUNTIME_PERMISSION = "org.autojs.permission.AI_RUNTIME"
    fun acceptsPermission(component: AiProviderComponent, permission: String?): Boolean =
        permission == org.autojs.plugin.ai.provider.api.AiProviderProtocol.PLUGIN_PERMISSION ||
            component.packageName == PACKAGE_NAME && component.className == RUNTIME_CLASS_NAME && permission == RUNTIME_PERMISSION
    const val PROVIDER_ID = "autojs6.three-stone-ai"
}
