package org.autojs.plugin.explorer.api

object ExplorerActionIntentValues {
    const val SOURCE_SURFACE_MAIN = "main"

    /** The source content URI is always the first ClipData item. */
    const val CLIP_ITEM_TARGET_INDEX = 0

    /** v1/v2 read-only actions only. v3 output transactions do not include this item. */
    const val CLIP_ITEM_PARENT_INDEX = 1

    const val CLIP_ITEM_COUNT_READ_ONLY = 2
    const val CLIP_ITEM_COUNT_OUTPUT_TRANSACTION = 1

    /** v4 ClipData contains only the ordered target URIs. Parent metadata is not granted. */
    fun v4ClipItemCount(targetCount: Int): Int = targetCount
}
