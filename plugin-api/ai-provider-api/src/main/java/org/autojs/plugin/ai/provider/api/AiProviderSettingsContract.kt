package org.autojs.plugin.ai.provider.api

/**
 * Parameterless entry point for an AI provider plugin's own settings UI.
 *
 * The contract intentionally defines no extras, URI, clip data, result payload, or provider
 * configuration fields. Credentials remain private to the plugin and must never cross this
 * settings boundary.
 */
object AiProviderSettingsContract {
    const val ACTION_OPEN_SETTINGS = "org.autojs.plugin.AI_PROVIDER_SETTINGS"
    const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
    const val PLUGIN_PERMISSION = AiProviderProtocol.PLUGIN_PERMISSION
}
