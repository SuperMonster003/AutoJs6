package org.autojs.plugin.epub.api

/**
 * Identity of the official EPUB plugin (3-Folio EPUB) as reported by `PluginInfo`. The
 * engine id `epub` is shared by every implementation of this contract; the plugin id names the
 * official one. The same package also exports an Explorer Action service whose `PluginInfo`
 * differs only in `engine` and `capabilities` (roadmap D10).
 */
object EpubIds {

    const val PLUGIN_ID = "three-folio-epub"

    const val ENGINE = "epub"

    const val VARIANT_DEFAULT = "default"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.folio.epub"

    /**
     * Smallest host `versionCode` the plugin may declare through
     * `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the 6.8.0 host build that ships the EPUB
     * contract module and the host client (plugin roadmap P5.1).
     */
    const val REQUIRED_HOST_VERSION_CODE = 5282L
}
