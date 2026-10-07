package org.autojs.plugin.explorer.api

/** Keys used by every v4 target Bundle in [ExplorerActionIntentExtras.TARGETS]. */
object ExplorerActionTargetKeys {
    const val ID = "id"
    const val URI = "uri"
    const val DISPLAY_NAME = "displayName"
    const val KIND = "kind"
    const val MIME_TYPE = "mimeType"
    const val SIZE = "size"
    const val LAST_MODIFIED = "lastModified"

    /**
     * v23. The canonical absolute path of a directory target, present only when the accepted
     * catalog entry declares [ExplorerActionCatalogKeys.FILESYSTEM_PATH]; at most
     * [ExplorerActionProtocol.MAX_FILESYSTEM_PATH_LENGTH] characters.
     */
    const val FILESYSTEM_PATH = "filesystemPath"
}
