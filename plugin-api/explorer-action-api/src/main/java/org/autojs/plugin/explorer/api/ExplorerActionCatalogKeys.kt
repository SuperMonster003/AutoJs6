package org.autojs.plugin.explorer.api

object ExplorerActionCatalogKeys {
    const val PROTOCOL_VERSION = "protocolVersion"
    const val ACTIONS = "actions"

    const val ID = "id"
    const val LABEL_RESOURCE_NAME = "labelResourceName"
    const val LABEL_FALLBACK = "labelFallback"
    const val ACTIVITY_CLASS_NAME = "activityClassName"
    const val PRIORITY = "priority"
    const val TARGET_KIND = "targetKind"
    /** v4. Omitted descriptors retain the v1-v3 single-target behavior. */
    const val CARDINALITY = "cardinality"
    const val ACCESS_MODE = "accessMode"
    const val PLACEMENT = "placement"
    const val MIME_TYPES = "mimeTypes"
    const val EXTENSIONS = "extensions"
    const val OUTPUT_MODE = "outputMode"
    const val OUTPUT_MIME_TYPES = "outputMimeTypes"
    const val OUTPUT_NAME_SUFFIX = "outputNameSuffix"
    const val MAX_OUTPUT_BYTES = "maxOutputBytes"
}
