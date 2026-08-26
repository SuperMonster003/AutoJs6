package org.autojs.plugin.explorer.api

/** Bundle schema returned by [IExplorerArchiveSession]. */
object ExplorerArchiveSessionKeys {
    const val SESSION_ID = "sessionId"
    const val ROOT_ID = "rootId"
    const val DISPLAY_NAME = "displayName"
    const val SOURCE_SIZE = "sourceSize"
    const val SOURCE_LAST_MODIFIED = "sourceLastModified"
    /** False or absent for protocol v5 sessions. */
    const val CAN_OPEN_ENTRIES = "canOpenEntries"
    /** False or absent before protocol v10. */
    const val CAN_EXTRACT_ENTRIES = "canExtractEntries"

    const val ITEMS = "items"
    const val NEXT_OFFSET = "nextOffset"
    const val COMPLETE = "complete"

    const val ID = "id"
    const val PARENT_ID = "parentId"
    const val NAME = "name"
    const val KIND = "kind"
    const val SIZE = "size"
    const val COMPRESSED_SIZE = "compressedSize"
    const val LAST_MODIFIED = "lastModified"
    const val CAN_EXTRACT = "canExtract"
}
