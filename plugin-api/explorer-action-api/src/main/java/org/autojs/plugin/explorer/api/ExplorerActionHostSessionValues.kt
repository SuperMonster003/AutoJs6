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
}
