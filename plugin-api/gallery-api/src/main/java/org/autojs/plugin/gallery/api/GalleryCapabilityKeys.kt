package org.autojs.plugin.gallery.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle and of [IGalleryPlugin.getCapabilities]. Every value
 * is readable without opening a session, so the plugin center can show compatibility up front.
 */
object GalleryCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /**
     * Int: the baseline contract version the plugin implements; a host accepts the plugin when this
     * value lies in its own supported range. Plugins keep it at 1 so hosts of contract version 1
     * still accept them, and advertise newer versions through [MAX_CONTRACT_VERSION].
     */
    const val CONTRACT_VERSION = "galleryContractVersion"

    /**
     * Int, optional: the newest contract version the plugin implements (absent means the value of
     * [CONTRACT_VERSION]). The host negotiates the smaller of this and its own
     * [GalleryContract.MAX_CONTRACT_VERSION] and writes it into every request.
     */
    const val MAX_CONTRACT_VERSION = "galleryMaxContractVersion"

    /** String array: the calls the plugin implements, a subset of [GalleryContract.FEATURES]. */
    const val FEATURES = "galleryFeatures"
}
