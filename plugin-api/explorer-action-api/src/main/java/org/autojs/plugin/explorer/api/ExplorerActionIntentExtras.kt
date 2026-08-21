package org.autojs.plugin.explorer.api

object ExplorerActionIntentExtras {
    private const val PREFIX = "org.autojs.plugin.explorer.extra."

    const val ACTION_ID = "${PREFIX}ACTION_ID"
    const val PROTOCOL_VERSION = "${PREFIX}PROTOCOL_VERSION"
    const val REQUEST_ID = "${PREFIX}REQUEST_ID"
    const val DISPLAY_NAME = "${PREFIX}DISPLAY_NAME"
    const val SIZE = "${PREFIX}SIZE"

    /** v4 ordered ArrayList<Bundle>; each element uses [ExplorerActionTargetKeys]. */
    const val TARGETS = "${PREFIX}TARGETS"

    /** v4 human-readable common parent path. It is display metadata, not a filesystem grant. */
    const val PARENT_DISPLAY_PATH = "${PREFIX}PARENT_DISPLAY_PATH"

    /**
     * v4 Bundle containing an [IExplorerActionHostSession] under
     * [ExplorerActionHostSessionKeys.BINDER]. Present only when the action needs directory,
     * multiple-target, or create-in-parent capabilities.
     */
    const val HOST_SESSION = "${PREFIX}HOST_SESSION"

    /**
     * v1/v2 grant this parent URI through ClipData. v4 supplies it only as ungranted context;
     * plugins must not assume it is readable. v3 output transactions deliberately omit it.
     */
    const val PARENT_URI = "${PREFIX}PARENT_URI"
    const val HOST_PACKAGE_NAME = "${PREFIX}HOST_PACKAGE_NAME"
    const val HOST_VERSION_NAME = "${PREFIX}HOST_VERSION_NAME"
    const val HOST_VERSION_CODE = "${PREFIX}HOST_VERSION_CODE"
    const val SOURCE_SURFACE = "${PREFIX}SOURCE_SURFACE"

    /** v3 output transaction identifier. A successful plugin result must echo this value. */
    const val OUTPUT_TRANSACTION_ID = "${PREFIX}OUTPUT_TRANSACTION_ID"

    /**
     * v3 host-owned, bounded, write-only URI. The plugin must close its writer before returning
     * RESULT_OK and cannot read, seek, replace, or choose the final sibling path.
     */
    const val OUTPUT_URI = "${PREFIX}OUTPUT_URI"

    /** v3 concrete MIME types accepted by the host for the staged output. */
    const val OUTPUT_MIME_TYPES = "${PREFIX}OUTPUT_MIME_TYPES"

    /** v3 maximum number of bytes accepted for the staged output. */
    const val MAX_OUTPUT_BYTES = "${PREFIX}MAX_OUTPUT_BYTES"

    /** v3 host-validated name suffix that the host uses when choosing the new sibling file name. */
    const val OUTPUT_NAME_SUFFIX = "${PREFIX}OUTPUT_NAME_SUFFIX"
}
