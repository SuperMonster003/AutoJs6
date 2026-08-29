package org.autojs.plugin.explorer.api

/** Bundle schema for protocol v19 archive deletion and rename. */
object ExplorerArchiveMutationKeys {
    const val OPERATION_ID = "operationId"
    const val OPERATION = "operation"
    const val ENTRY_IDS = "entryIds"
    const val ENTRY_ID = "entryId"
    const val NEW_NAME = "newName"

    const val PHASE = "phase"
    const val CURRENT_ENTRY = "currentEntry"
    const val COMPLETED_ENTRIES = "completedEntries"
    const val TOTAL_ENTRIES = "totalEntries"
    const val BYTES_WRITTEN = "bytesWritten"
    const val TOTAL_BYTES = "totalBytes"

    /** Updated [IExplorerArchiveSession.getInfo] payload after a committed mutation. */
    const val SESSION_INFO = "sessionInfo"
    const val MUTATED_ENTRIES = "mutatedEntries"

    const val ERROR_CODE = "errorCode"
    const val ERROR_MESSAGE = "errorMessage"
}
