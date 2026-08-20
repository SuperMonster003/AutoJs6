package org.autojs.autojs.core.plugin.ai

/**
 * Stable identity of the official on-device on-device AI plugin.
 *
 * Scripts may select the local plugin route with `plugin: true` (or a partial selector); these
 * constants supply the omitted component and provider so the shorthand stays deterministic.
 */
internal object OnDeviceAiOfficialPlugin {
    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.ondeviceai"
    const val CLASS_NAME = "$PACKAGE_NAME.provider.OnDeviceAiProviderService"
    const val PROVIDER_ID = "autojs6.on-device-ai"
}
