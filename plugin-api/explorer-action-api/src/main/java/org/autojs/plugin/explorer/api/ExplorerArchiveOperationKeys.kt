package org.autojs.plugin.explorer.api

/** Bundle schema for protocol v10 archive extraction and v11 recoverable interactions. */
object ExplorerArchiveOperationKeys {
    const val OPERATION_ID = "operationId"
    const val ENTRY_IDS = "entryIds"
    /** Transient v11 request value. Receivers must copy, bound, and clear it immediately. */
    const val PASSWORD = "password"
    const val SKIP_UNSAFE_PATHS = "skipUnsafePaths"
    const val ALLOW_RESOURCE_BUDGET_OVERRIDE = "allowResourceBudgetOverride"

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
    const val INTERACTION_KIND = "interactionKind"
}
