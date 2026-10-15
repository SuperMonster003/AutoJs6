package org.autojs.plugin.explorer.api

object ExplorerActionHostSessionValues {
    const val OUTPUT_CONFLICT_FAIL = 1
    const val OUTPUT_CONFLICT_AUTO_RENAME = 2

    const val OUTPUT_KIND_FILE = 1
    const val OUTPUT_KIND_DIRECTORY_TREE = 2
    const val OUTPUT_KIND_TARGET_REPLACEMENT = 3

    const val OUTPUT_STATE_PREPARED = 1
    const val OUTPUT_STATE_WRITING = 2
    const val OUTPUT_STATE_VERIFYING = 3
    const val OUTPUT_STATE_COMMITTED = 4
    const val OUTPUT_STATE_ABORTED = 5
    const val OUTPUT_STATE_UNKNOWN = 6

    const val OUTPUT_BATCH_STATE_PREPARED = 1
    const val OUTPUT_BATCH_STATE_COMMITTING = 2
    const val OUTPUT_BATCH_STATE_COMMITTED = 3
    const val OUTPUT_BATCH_STATE_ABORTED = 4
    const val OUTPUT_BATCH_STATE_RECOVERY_REQUIRED = 5
    const val OUTPUT_BATCH_STATE_UNKNOWN = 6

    const val TARGET_TRASH_STATE_COMMITTED = 1
    const val TARGET_TRASH_STATE_RECOVERY_REQUIRED = 2
    const val TARGET_TRASH_STATE_FAILED = 3
    const val TARGET_TRASH_STATE_UNKNOWN = 4

    const val TARGET_TRASH_BATCH_STATE_AVAILABLE = 1
    const val TARGET_TRASH_BATCH_STATE_PARTIAL = 2
    const val TARGET_TRASH_BATCH_STATE_RESTORED = 3
    const val TARGET_TRASH_BATCH_STATE_STALE = 4
    const val TARGET_TRASH_BATCH_STATE_RECOVERY_REQUIRED = 5
    const val TARGET_TRASH_BATCH_STATE_FAILED = 6
    const val TARGET_TRASH_BATCH_STATE_UNKNOWN = 7

    const val TARGET_TRASH_ITEM_STATE_MOVED = 1
    const val TARGET_TRASH_ITEM_STATE_NOT_MOVED = 2
    const val TARGET_TRASH_ITEM_STATE_RESTORED = 3
    const val TARGET_TRASH_ITEM_STATE_STALE = 4
    const val TARGET_TRASH_ITEM_STATE_RECOVERY_REQUIRED = 5
    const val TARGET_TRASH_ITEM_STATE_UNKNOWN = 6

    const val TARGET_REPLACEMENT_UNDO_STATE_NONE = 0
    const val TARGET_REPLACEMENT_UNDO_STATE_AVAILABLE = 1
    const val TARGET_REPLACEMENT_UNDO_STATE_RESTORED = 2
    const val TARGET_REPLACEMENT_UNDO_STATE_STALE = 3
    const val TARGET_REPLACEMENT_UNDO_STATE_RECOVERY_REQUIRED = 4
    const val TARGET_REPLACEMENT_UNDO_STATE_UNKNOWN = 5

    const val PLAYBACK_REPORT_PROGRESS = 1
    const val PLAYBACK_REPORT_COMPLETED = 2

    // v25 rename batches.
    /** Every entry was renamed. */
    const val RENAME_BATCH_STATE_COMMITTED = 1
    /** Some entries were renamed; the others were stale, in conflict or failed and kept their names. */
    const val RENAME_BATCH_STATE_PARTIAL = 2
    /** Nothing was renamed: an all-or-nothing request found an entry it could not rename. */
    const val RENAME_BATCH_STATE_REJECTED = 3
    /** The batch was undone; every renamed entry that was still unchanged got its old name back. */
    const val RENAME_BATCH_STATE_RESTORED = 4
    const val RENAME_BATCH_STATE_RECOVERY_REQUIRED = 5
    const val RENAME_BATCH_STATE_UNKNOWN = 6

    const val RENAME_ITEM_STATE_RENAMED = 1
    /** Kept its name: the new name was taken, invalid or the rename failed. */
    const val RENAME_ITEM_STATE_NOT_RENAMED = 2
    /** Kept its name: the entry changed or disappeared after the plugin listed it. */
    const val RENAME_ITEM_STATE_STALE = 3
    const val RENAME_ITEM_STATE_RESTORED = 4
    const val RENAME_ITEM_STATE_RECOVERY_REQUIRED = 5
    const val RENAME_ITEM_STATE_UNKNOWN = 6
}
