package org.autojs.plugin.mail.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle and of [IMailPlugin.getCapabilities]. Every value
 * is readable without opening a session, so the plugin center can show compatibility up front.
 */
object MailCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /** Int: [MailContract.CONTRACT_VERSION] implemented by the plugin. */
    const val CONTRACT_VERSION = "mailContractVersion"

    /** String array: protocols the plugin speaks, a subset of [MailContract.PROTOCOLS]. */
    const val PROTOCOLS = "mailProtocols"

    /** String array: authentication mechanisms, a subset of [MailContract.AUTH_MECHANISMS]. */
    const val AUTH_MECHANISMS = "mailAuthMechanisms"

    /** String array: optional features, a subset of [MailContract.FEATURES]. */
    const val FEATURES = "mailFeatures"

    /** Int: version of the bundled provider preset table returned by [IMailPlugin.listProviders]. */
    const val PROVIDERS_VERSION = "mailProvidersVersion"

    /** Int: optional settings extension; version 1 supports OPEN_SETTINGS and saved accounts. */
    const val SETTINGS_VERSION = "mailSettingsVersion"

    /** String: version of the mail library bundled in the plugin, for diagnostics only. */
    const val LIBRARY_VERSION = "mailLibraryVersion"
}
