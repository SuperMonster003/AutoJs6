package org.autojs.plugin.explorer.api

/** Bundle schema for one v20 frozen archive-input grant. */
object ExplorerArchiveInputKeys {
    const val GRANT_ID = "grantId"
    const val ARCHIVE_SESSION_ID = "archiveSessionId"
    const val OPERATION_ID = "operationId"
    const val VIRTUAL_ROOT_ID = "virtualRootId"
    const val ROOT_COUNT = "rootCount"
    const val NODE_COUNT = "nodeCount"
    const val FILE_COUNT = "fileCount"
    const val DIRECTORY_COUNT = "directoryCount"
    const val KNOWN_TOTAL_BYTES = "knownTotalBytes"

    const val ITEMS = "items"
    const val NEXT_OFFSET = "nextOffset"
    const val COMPLETE = "complete"

    const val ID = "id"
    const val PARENT_ID = "parentId"
    const val NAME = "name"
    const val KIND = "kind"
    const val SIZE = "size"
    const val LAST_MODIFIED = "lastModified"

    const val VERIFICATION_STATUS = "verificationStatus"
}
