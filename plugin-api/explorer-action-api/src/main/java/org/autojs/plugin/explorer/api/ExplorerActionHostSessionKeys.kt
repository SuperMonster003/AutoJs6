package org.autojs.plugin.explorer.api

/** Keys shared by the v4 host file session and its result Bundles. */
object ExplorerActionHostSessionKeys {
    const val BINDER = "binder"

    const val ITEMS = "items"
    const val NEXT_OFFSET = "nextOffset"
    const val COMPLETE = "complete"

    const val RELATIVE_PATH = "relativePath"
    const val DISPLAY_NAME = "displayName"
    const val KIND = "kind"
    const val MIME_TYPE = "mimeType"
    const val SIZE = "size"
    const val LAST_MODIFIED = "lastModified"
    const val READABLE = "readable"
    const val SYMBOLIC_LINK = "symbolicLink"

    const val OUTPUT_TRANSACTION_ID = "outputTransactionId"
    const val OUTPUT_DISPLAY_NAME = "outputDisplayName"
    const val OUTPUT_DISPLAY_PATH = "outputDisplayPath"
    const val OUTPUT_KIND = "outputKind"
    const val OUTPUT_STATE = "outputState"
    const val OUTPUTS = "outputs"
    const val OUTPUT_FILE_COUNT = "outputFileCount"
    const val OUTPUT_DIRECTORY_COUNT = "outputDirectoryCount"
    const val OUTPUT_BYTES = "outputBytes"

    const val OUTPUT_BATCH_ID = "outputBatchId"
    const val OUTPUT_BATCH_STATE = "outputBatchState"
    const val OUTPUT_BATCHES = "outputBatches"
    const val OUTPUT_TRANSACTION_IDS = "outputTransactionIds"
    const val OUTPUT_BATCH_PUBLISHED_COUNT = "outputBatchPublishedCount"

    const val TARGET_TRASH_STATE = "targetTrashState"
    const val TARGET_TRASH_ITEM_IDS = "targetTrashItemIds"
    const val TARGET_TRASH_MOVED_COUNT = "targetTrashMovedCount"
    const val TARGET_TRASH_RECOVERY_COUNT = "targetTrashRecoveryCount"
    const val TARGET_TRASH_RESTORED_COUNT = "targetTrashRestoredCount"

    const val TARGET_TRASH_BATCH_ID = "targetTrashBatchId"
    const val TARGET_TRASH_BATCH_STATE = "targetTrashBatchState"
    const val TARGET_TRASH_BATCHES = "targetTrashBatches"
    const val TARGET_TRASH_BATCH_ITEMS = "targetTrashBatchItems"
    const val TARGET_TRASH_BATCH_CREATED_AT = "targetTrashBatchCreatedAt"
    const val TARGET_TRASH_BATCH_UPDATED_AT = "targetTrashBatchUpdatedAt"
    const val TARGET_TRASH_ITEM_DISPLAY_NAME = "targetTrashItemDisplayName"
    const val TARGET_TRASH_ITEM_STATE = "targetTrashItemState"

    const val TARGET_REPLACEMENT_HISTORY_ID = "targetReplacementHistoryId"
    const val TARGET_REPLACEMENT_UNDO_STATE = "targetReplacementUndoState"
    const val TARGET_REPLACEMENT_PREVIOUS_SIZE = "targetReplacementPreviousSize"
    const val TARGET_REPLACEMENT_CREATED_AT = "targetReplacementCreatedAt"

    const val PLAYBACK_HISTORY_ENABLED = "playbackHistoryEnabled"
    const val PLAYBACK_PROGRESS_PRESENT = "playbackProgressPresent"
    const val PLAYBACK_POSITION_MILLIS = "playbackPositionMillis"
    const val PLAYBACK_DURATION_MILLIS = "playbackDurationMillis"
    const val PLAYBACK_UPDATED_AT_MILLIS = "playbackUpdatedAtMillis"

    // v25 target rename (docs/dev/explorer-action-protocol-v25.md).
    /** ArrayList of Bundle: the entries of one `renameEntries` request or of a batch result. */
    const val RENAME_ENTRIES = "renameEntries"
    /** String: request target the entry belongs to; [RELATIVE_PATH] names a listed descendant, empty means the target. */
    const val RENAME_TARGET_ID = "renameTargetId"
    /** String: the new name, one path segment of at most `MAX_RENAME_NAME_BYTES` UTF-8 bytes. */
    const val RENAME_NEW_NAME = "renameNewName"
    /** Boolean: replace an existing entry of that name; the host moves it to its Trash first and restores it on undo. */
    const val RENAME_OVERWRITE = "renameOverwrite"
    /** Boolean request flag: revalidate every entry first and rename nothing when one fails. */
    const val RENAME_ALL_OR_NOTHING = "renameAllOrNothing"
    /** String: relative path of the entry after the rename, below the same request target. */
    const val RENAME_NEW_RELATIVE_PATH = "renameNewRelativePath"
    const val RENAME_ITEM_STATE = "renameItemState"
    const val RENAME_BATCH_ID = "renameBatchId"
    const val RENAME_BATCH_STATE = "renameBatchState"
    const val RENAME_BATCHES = "renameBatches"
    const val RENAME_BATCH_CREATED_AT = "renameBatchCreatedAt"
    const val RENAME_BATCH_UPDATED_AT = "renameBatchUpdatedAt"
    const val RENAME_RENAMED_COUNT = "renameRenamedCount"
    const val RENAME_NOT_RENAMED_COUNT = "renameNotRenamedCount"
    const val RENAME_RESTORED_COUNT = "renameRestoredCount"
    /** Boolean of a `listRenameParent` page: names differing only in case collide in this file system. */
    const val RENAME_CASE_INSENSITIVE = "renameCaseInsensitive"
    /** Boolean of a `listRenameParent` page: the file system rejects `\ : * ? " < > |` in names. */
    const val RENAME_RESTRICTED_CHARACTERS = "renameRestrictedCharacters"

    // v26 photo metadata (docs/dev/explorer-action-protocol-v26.md).
    /** ArrayList of Bundle: the entries of one `readMetadata` request, each with [RENAME_TARGET_ID] and [RELATIVE_PATH]. */
    const val METADATA_ENTRIES = "metadataEntries"
    /** ArrayList of Bundle: one answer per request entry, in request order; each holds [METADATA] when the host read any. */
    const val METADATA_ITEMS = "metadataItems"
    /** Bundle: the photo metadata of one entry, keyed by [ExplorerPhotoMetadataKeys]. */
    const val METADATA = "metadata"
}
