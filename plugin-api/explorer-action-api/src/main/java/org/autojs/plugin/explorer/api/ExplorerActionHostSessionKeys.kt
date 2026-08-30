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
}
