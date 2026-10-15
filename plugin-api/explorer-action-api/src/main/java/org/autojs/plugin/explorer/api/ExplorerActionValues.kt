package org.autojs.plugin.explorer.api

object ExplorerActionValues {
    const val TARGET_FILE = 1
    const val TARGET_DIRECTORY = 2
    const val TARGET_MIXED = 3

    const val CARDINALITY_SINGLE = 1
    const val CARDINALITY_MULTIPLE = 2

    const val ACCESS_READ_ONLY = 1
    const val ACCESS_CREATE_IN_PARENT = 2
    /** v8. Rebuilds one selected file through a host-owned staging and atomic replacement. */
    const val ACCESS_REPLACE_TARGET = 3
    /** v9. Combines target replacement with controlled sibling creation for one selected file. */
    const val ACCESS_MANAGE_TARGET = 4

    const val PLACEMENT_OVERFLOW = 1
    const val PLACEMENT_PRIMARY = 2
    const val PLACEMENT_SELECTION_TOOLBAR = 3
    /**
     * v25. The "More" menu of the host's single-item rename dialog; requires
     * [ExplorerActionCatalogKeys.TARGET_RENAME] and a single target.
     */
    const val PLACEMENT_RENAME_DIALOG = 4

    const val PRESENTATION_ACTIVITY = 1
    /** v5. The host renders a read-only archive session in its own Explorer UI. */
    const val PRESENTATION_HOST_EXPLORER = 2

    const val OUTPUT_NONE = 0
    const val OUTPUT_CREATE_SIBLING = 1
}
