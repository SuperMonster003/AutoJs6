package org.autojs.plugin.offlinedocs.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/** Stable discovery and static-content contract for the Offline Documentation plugin. */
object OfflineDocsPluginContract {

    const val VERSION = 1
    const val MIN_SUPPORTED_VERSION = VERSION
    const val MAX_SUPPORTED_VERSION = VERSION

    const val ACTION = "org.autojs.plugin.OFFLINE_DOCS"
    const val CATEGORY = "offline-docs"
    const val PLUGIN_ID = "offline-docs"
    const val ENGINE = "offline-docs"

    const val DEFAULT_PACKAGE = "io.github.supermonster003.autojs6.plugin.offlinedocs"
    const val DEFAULT_PACKAGE_NAME = DEFAULT_PACKAGE
    const val REQUIRED_HOST_VERSION_CODE = 5240L

    const val ASSET_ROOT = "docs"
    const val ENTRY_POINT = "index.html"
    const val CONTENT_FORMAT = "autojs6-static-html-v1"
    const val INVENTORY_FILE = "offline-docs-inventory-v1.txt"

    const val META_DATA_CONTRACT_VERSION = "org.autojs.plugin.offlinedocs.CONTRACT_VERSION"
    const val META_DATA_CONTENT_VERSION = "org.autojs.plugin.offlinedocs.CONTENT_VERSION"
    const val META_DATA_CONTENT_FORMAT = "org.autojs.plugin.offlinedocs.CONTENT_FORMAT"
    const val META_DATA_ASSET_ROOT = "org.autojs.plugin.offlinedocs.ASSET_ROOT"
    const val META_DATA_ENTRY_POINT = "org.autojs.plugin.offlinedocs.ENTRY_POINT"
    const val META_DATA_FILE_COUNT = "org.autojs.plugin.offlinedocs.FILE_COUNT"
    const val META_DATA_TOTAL_BYTES = "org.autojs.plugin.offlinedocs.TOTAL_BYTES"
    const val META_DATA_CONTENT_SHA256 = "org.autojs.plugin.offlinedocs.CONTENT_SHA256"
    const val META_DATA_REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION
    const val META_DATA_MAX_HOST_VERSION = "org.autojs.plugin.offlinedocs.MAX_HOST_VERSION"

    const val MAX_FILE_COUNT = 512L
    const val MAX_FILE_BYTES = 16L * 1024L * 1024L
    const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L
    const val MAX_INVENTORY_BYTES = 1024L * 1024L
}
