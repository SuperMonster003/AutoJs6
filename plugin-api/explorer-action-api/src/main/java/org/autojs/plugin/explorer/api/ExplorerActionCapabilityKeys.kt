package org.autojs.plugin.explorer.api

object ExplorerActionCapabilityKeys {
    const val PROTOCOL_VERSION = "explorerActionProtocolVersion"

    /**
     * Optional service-wide host file-information extension. Version 1 returns a bounded,
     * localized, read-only summary for a catalog action without replacing its Activity entry.
     */
    const val HOST_FILE_INFO_VERSION = "explorerActionHostFileInfoVersion"
}
