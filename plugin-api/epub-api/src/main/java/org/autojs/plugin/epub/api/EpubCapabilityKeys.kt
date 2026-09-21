package org.autojs.plugin.epub.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle and of [IEpubPlugin.getCapabilities]. Every value
 * is readable without opening a book, so the plugin center can show compatibility up front.
 */
object EpubCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /**
     * Int: the baseline contract version the plugin implements; a host accepts the plugin when this
     * value lies in its own supported range. Plugins keep it at 1 so hosts of contract version 1
     * still accept them, and advertise newer versions through [MAX_CONTRACT_VERSION].
     */
    const val CONTRACT_VERSION = "epubContractVersion"

    /**
     * Int, optional: the newest contract version the plugin implements (absent means the value of
     * [CONTRACT_VERSION]). The host negotiates the smaller of this and its own
     * [EpubContract.MAX_CONTRACT_VERSION] and writes it into every request of the books and
     * sessions it opens.
     */
    const val MAX_CONTRACT_VERSION = "epubMaxContractVersion"

    /** String array: optional features, a subset of [EpubContract.FEATURES]. */
    const val FEATURES = "epubFeatures"

    /** String: version of the Readium toolkit bundled in the plugin, for diagnostics only. */
    const val READIUM_VERSION = "epubReadiumVersion"
}
