package org.autojs.plugin.explorer.api

object ExplorerActionProtocol {
    const val VERSION = 7
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_SUPPORTED_VERSION = VERSION

    const val MAX_ACTIONS_PER_PLUGIN = 32
    const val MAX_ACTION_ID_LENGTH = 128
    const val MAX_LABEL_LENGTH = 160
    const val MAX_ACTIVITY_CLASS_NAME_LENGTH = 512
    const val MAX_MATCHERS_PER_KIND = 128
    const val MAX_TARGETS_PER_REQUEST = 128
    const val MAX_TARGET_ID_LENGTH = 128
    const val MAX_TARGET_DISPLAY_NAME_LENGTH = 4_096
    const val MAX_PARENT_DISPLAY_PATH_LENGTH = 16_384
    const val MAX_SESSION_RELATIVE_PATH_LENGTH = 16_384
    const val MAX_SESSION_PAGE_SIZE = 128
    const val MAX_OUTPUT_DISPLAY_NAME_LENGTH = 255
    const val MAX_OUTPUT_MIME_TYPE_LENGTH = 255
    const val MAX_OUTPUT_NAME_SUFFIX_LENGTH = 128
    const val MAX_ARCHIVE_SESSION_ID_LENGTH = 128
    const val MAX_ARCHIVE_ENTRY_ID_LENGTH = 128
    const val MAX_ARCHIVE_DISPLAY_NAME_LENGTH = 4_096
    const val MAX_ARCHIVE_ITEMS = 100_000
    const val MAX_ARCHIVE_PAGE_SIZE = 128
    /** Protocol envelope. A host may advertise and enforce a smaller per-action runtime limit. */
    const val MAX_OUTPUT_BYTES = 4L * 1024L * 1024L * 1024L
}
