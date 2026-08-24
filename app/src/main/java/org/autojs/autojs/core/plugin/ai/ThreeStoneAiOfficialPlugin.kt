package org.autojs.autojs.core.plugin.ai

/**
 * Stable identity of 3-Stone AI, the official AI plugin.
 *
 * Scripts may select the local plugin route with `plugin: true` (or a partial selector); these
 * constants supply the omitted component and provider so the shorthand stays deterministic.
 */
internal object ThreeStoneAiOfficialPlugin {
    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.threestoneai"
    const val CLASS_NAME = "$PACKAGE_NAME.provider.ThreeStoneAiProviderService"
    const val PROVIDER_ID = "autojs6.three-stone-ai"
}
