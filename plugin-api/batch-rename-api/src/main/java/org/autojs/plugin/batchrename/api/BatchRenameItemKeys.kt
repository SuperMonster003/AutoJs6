package org.autojs.plugin.batchrename.api

/**
 * Keys of one item Bundle: request items of [BatchRenameRequestKeys.ITEMS] and answer items of
 * [BatchRenameResultKeys.ITEMS] share [INDEX].
 *
 * zh-CN: 单个条目 Bundle 的键: [BatchRenameRequestKeys.ITEMS] 的请求条目与 [BatchRenameResultKeys.ITEMS] 的应答条目共用 [INDEX].
 */
object BatchRenameItemKeys {

    // Request item.

    /** Int: position of the item in the request, 0 based; answers carry the same value. */
    const val INDEX = "index"

    /** String: [BatchRenameParentKeys.PARENT_ID] of the directory holding the item. */
    const val PARENT_ID = "parentId"

    /** String: current name of the item (one path segment). */
    const val NAME = "name"

    /** Int: [BatchRenameValues.KIND_FILE] or [BatchRenameValues.KIND_DIRECTORY]. */
    const val KIND = "kind"

    /** Int: 0 for a selected item, 1 for its children, and so on. */
    const val DEPTH = "depth"

    /** Long: last modification time in epoch milliseconds, the `modified` date source. */
    const val LAST_MODIFIED = "lastModified"

    /** Long: size in bytes, -1 for directories; recorded for diagnostics and future variables. */
    const val LENGTH = "length"

    /** Boolean: excluded by the caller; keeps its name and does not advance counters. */
    const val EXCLUDED = "excluded"

    // Answer item.

    /** String: the computed name; equals [NAME] when the item is not renamed. */
    const val NEW_NAME = "newName"

    /** String: one of the `STATUS_*` values of [BatchRenameValues]. */
    const val STATUS = "status"

    /** String: one of the `REASON_*` values of [BatchRenameValues]; absent for plain renames. */
    const val REASON = "reason"

    /** Boolean: the rename needs a temporary name first (case-only change, swap or cycle). */
    const val TWO_PHASE = "twoPhase"

    /** Boolean: the rename replaces an existing entry (overwrite policy); the host moves it to its Trash first. */
    const val OVERWRITES = "overwrites"
}
