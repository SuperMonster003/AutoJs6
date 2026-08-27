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
    /** Empty or absent before protocol v13, or when this format has no filename charset override. */
    const val FILENAME_CHARSET_NAMES = "filenameCharsetNames"
    /** Effective charset selected by the backend after automatic detection or an explicit override. */
    const val FILENAME_CHARSET_NAME = "filenameCharsetName"
    /** Present only while protocol v13 uses an explicit filename charset override. */
    const val FILENAME_CHARSET_OVERRIDE = "filenameCharsetOverride"

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
    /** Per-entry preview capability. False or absent before protocol v11. */
    const val CAN_OPEN = "canOpen"
    const val CAN_EXTRACT = "canExtract"
}
