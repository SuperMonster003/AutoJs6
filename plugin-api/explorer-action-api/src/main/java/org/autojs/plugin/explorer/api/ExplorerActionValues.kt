package org.autojs.plugin.explorer.api

object ExplorerActionValues {
    const val TARGET_FILE = 1
    const val TARGET_DIRECTORY = 2
    const val TARGET_MIXED = 3

    const val CARDINALITY_SINGLE = 1
    const val CARDINALITY_MULTIPLE = 2

    const val ACCESS_READ_ONLY = 1
    const val ACCESS_CREATE_IN_PARENT = 2

    const val PLACEMENT_OVERFLOW = 1
    const val PLACEMENT_PRIMARY = 2
    const val PLACEMENT_SELECTION_TOOLBAR = 3

    const val OUTPUT_NONE = 0
    const val OUTPUT_CREATE_SIBLING = 1
}
