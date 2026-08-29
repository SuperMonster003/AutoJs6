package org.autojs.plugin.explorer.api

/** Bundle schema returned by [IExplorerArchiveSession]. */
object ExplorerArchiveSessionKeys {
    const val SESSION_ID = "sessionId"
    const val ROOT_ID = "rootId"
    const val DISPLAY_NAME = "displayName"
    /** v17 stable format identifier reported after structural archive detection. */
    const val FORMAT_ID = "formatId"
    /** v17 bounded user-facing format name, for example ZIP or TAR.GZ. */
    const val FORMAT_DISPLAY_NAME = "formatDisplayName"
    /** v17 true when the source name already declares the detected format. */
    const val DISPLAY_NAME_MATCHES_FORMAT = "displayNameMatchesFormat"
    const val SOURCE_SIZE = "sourceSize"
    const val SOURCE_LAST_MODIFIED = "sourceLastModified"
    /** False or absent for protocol v5 sessions. */
    const val CAN_OPEN_ENTRIES = "canOpenEntries"
    /** False or absent before protocol v10. */
    const val CAN_EXTRACT_ENTRIES = "canExtractEntries"
    /** False or absent before protocol v19. */
    const val CAN_DELETE_ENTRIES = "canDeleteEntries"
    /** False or absent before protocol v19. */
    const val CAN_RENAME_ENTRIES = "canRenameEntries"
    /** False or absent before protocol v20. */
    const val CAN_CREATE_DIRECTORY = "canCreateDirectory"
    /** Reserved for the v20 bounded-input slice. False or absent until implemented. */
    const val CAN_ADD_ENTRIES = "canAddEntries"
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
    /** Per-entry mutation capabilities. False or absent before protocol v19. */
    const val CAN_DELETE = "canDelete"
    const val CAN_RENAME = "canRename"
    /** Directory-only capability. False or absent before protocol v20. */
    const val CAN_CREATE_CHILDREN = "canCreateChildren"
    /** Reserved for the v20 bounded-input slice. False or absent until implemented. */
    const val CAN_ADD_CHILDREN = "canAddChildren"
}
