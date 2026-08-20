package org.autojs.autojs.core.plugin.ai

/**
 * Stable identity of the official on-device AI text plugin.
 *
 * Scripts may select the local plugin route with `plugin: true` (or a partial selector); these
 * constants supply the omitted component and provider so the shorthand stays deterministic.
 */
internal object AiTextOfficialPlugin {
    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.ai.text"
    const val CLASS_NAME = "$PACKAGE_NAME.provider.AiTextProviderService"
    const val PROVIDER_ID = "autojs6.local.text"
}
