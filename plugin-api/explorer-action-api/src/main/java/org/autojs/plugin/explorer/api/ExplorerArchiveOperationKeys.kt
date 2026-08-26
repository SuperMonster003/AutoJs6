package org.autojs.plugin.explorer.api

/** Bundle schema for protocol v10 archive extraction requests and callbacks. */
object ExplorerArchiveOperationKeys {
    const val OPERATION_ID = "operationId"
    const val ENTRY_IDS = "entryIds"

    const val PHASE = "phase"
    const val CURRENT_ENTRY = "currentEntry"
    const val COMPLETED_ENTRIES = "completedEntries"
    const val TOTAL_ENTRIES = "totalEntries"
    const val BYTES_WRITTEN = "bytesWritten"
    const val TOTAL_BYTES = "totalBytes"

    const val OUTPUT_DISPLAY_NAME = "outputDisplayName"
    const val OUTPUT_DISPLAY_PATH = "outputDisplayPath"
    const val FILES_EXTRACTED = "filesExtracted"
    const val DIRECTORIES_CREATED = "directoriesCreated"
    const val ENTRIES_SKIPPED = "entriesSkipped"

    const val ERROR_CODE = "errorCode"
    const val ERROR_MESSAGE = "errorMessage"
}
