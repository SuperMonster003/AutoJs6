package org.autojs.plugin.gallery.api

/**
 * Identity of the official gallery plugin (3-Maple Image) as reported by `PluginInfo`. The engine
 * id `gallery` is shared by every implementation of this contract; the plugin id names the official
 * one. The same package also exports Explorer Action services whose `PluginInfo` differs in
 * `engine` and `capabilities`.
 */
object GalleryIds {

    const val PLUGIN_ID = "three-maple-image"

    const val ENGINE = "gallery"

    const val VARIANT_DEFAULT = "default"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.maple.image"

    /**
     * Smallest host `versionCode` the plugin may declare through
     * `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the 6.8.0 host build that ships the gallery
     * contract module (plugin roadmap M6, 10.1).
     */
    const val REQUIRED_HOST_VERSION_CODE = 5436L
}
