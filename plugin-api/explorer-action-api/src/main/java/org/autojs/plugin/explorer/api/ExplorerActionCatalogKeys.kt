package org.autojs.plugin.explorer.api

object ExplorerActionCatalogKeys {
    const val PROTOCOL_VERSION = "protocolVersion"
    const val ACTIONS = "actions"

    const val ID = "id"
    const val LABEL_RESOURCE_NAME = "labelResourceName"
    const val LABEL_FALLBACK = "labelFallback"
    const val ACTIVITY_CLASS_NAME = "activityClassName"
    /** v5. Omitted descriptors are launched as activities. */
    const val PRESENTATION = "presentation"
    const val PRIORITY = "priority"
    const val TARGET_KIND = "targetKind"
    /** v4. Omitted descriptors retain the v1-v3 single-target behavior. */
    const val CARDINALITY = "cardinality"
    const val ACCESS_MODE = "accessMode"
    const val PLACEMENT = "placement"
    const val MIME_TYPES = "mimeTypes"
    const val EXTENSIONS = "extensions"
    /** v14 optional. Matches bounded compound suffixes such as `zip.001`. */
    const val FILE_NAME_SUFFIXES = "fileNameSuffixes"
    /** v17 optional. An overflow-only host archive action used when this primary action does not match. */
    const val PROBE_FOR_ACTION_ID = "probeForActionId"
    const val OUTPUT_MODE = "outputMode"
    const val OUTPUT_MIME_TYPES = "outputMimeTypes"
    const val OUTPUT_NAME_SUFFIX = "outputNameSuffix"
    const val MAX_OUTPUT_BYTES = "maxOutputBytes"

    /** v12 optional. Grants direct-parent enumeration and direct-sibling read access for one file. */
    const val READ_SIBLINGS = "readSiblings"

    /** v12 optional. Grants access to the host's opt-in playback progress channel. */
    const val PLAYBACK_PROGRESS = "playbackProgress"

    /**
     * v22 optional. The host may open only exact sibling names formed by appending one declared
     * suffix to the selected file name. No sibling paths or directory listings are disclosed.
     */
    const val RELATED_FILE_SUFFIXES = "relatedFileSuffixes"

    /**
     * v23 optional. Valid only for a single-directory, read-only Activity action without output,
     * matchers or session capabilities. The host then adds the canonical filesystem path of the
     * directory the user selected under [ExplorerActionTargetKeys.FILESYSTEM_PATH]. Disclosing the
     * path grants nothing: the plugin can use it only under its own storage permissions, and the
     * host neither creates a host file session nor discloses any other path for such an action.
     */
    const val FILESYSTEM_PATH = "filesystemPath"
}
