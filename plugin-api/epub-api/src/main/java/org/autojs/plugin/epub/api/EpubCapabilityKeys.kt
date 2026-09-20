package org.autojs.plugin.epub.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle and of [IEpubPlugin.getCapabilities]. Every value
 * is readable without opening a book, so the plugin center can show compatibility up front.
 */
object EpubCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /** Int: [EpubContract.CONTRACT_VERSION] implemented by the plugin. */
    const val CONTRACT_VERSION = "epubContractVersion"

    /** String array: optional features, a subset of [EpubContract.FEATURES]. */
    const val FEATURES = "epubFeatures"

    /** String: version of the Readium toolkit bundled in the plugin, for diagnostics only. */
    const val READIUM_VERSION = "epubReadiumVersion"
}
