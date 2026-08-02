package org.autojs.plugin.explorer.api

object ExplorerActionIntentExtras {
    private const val PREFIX = "org.autojs.plugin.explorer.extra."

    const val ACTION_ID = "${PREFIX}ACTION_ID"
    const val PROTOCOL_VERSION = "${PREFIX}PROTOCOL_VERSION"
    const val DISPLAY_NAME = "${PREFIX}DISPLAY_NAME"
    const val SIZE = "${PREFIX}SIZE"
    const val PARENT_URI = "${PREFIX}PARENT_URI"
    const val HOST_PACKAGE_NAME = "${PREFIX}HOST_PACKAGE_NAME"
    const val HOST_VERSION_NAME = "${PREFIX}HOST_VERSION_NAME"
    const val HOST_VERSION_CODE = "${PREFIX}HOST_VERSION_CODE"
    const val SOURCE_SURFACE = "${PREFIX}SOURCE_SURFACE"
}
