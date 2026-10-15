package org.autojs.autojs.core.plugin.explorer

internal data class ExplorerActionDescriptor(
    val protocolVersion: Int,
    val id: String,
    val labelResourceName: String?,
    val labelFallback: String?,
    val activityClassName: String,
    val priority: Int,
    val targetKind: Int,
    val cardinality: Int,
    val accessMode: Int,
    val placement: Int,
    val presentation: Int,
    val mimeTypes: Set<String>,
    val extensions: Set<String>,
    val fileNameSuffixes: Set<String>,
    val probeForActionId: String?,
    val outputMode: Int,
    val outputMimeTypes: Set<String>,
    val outputNameSuffix: String?,
    val maxOutputBytes: Long?,
    val readSiblings: Boolean,
    val playbackProgress: Boolean,
    val relatedFileSuffixes: Set<String>,
    /** v23: the host discloses the canonical path of the single selected directory. */
    val filesystemPath: Boolean = false,
    /** v24: the host session may move request targets into the host Trash without output proof. */
    val targetTrash: Boolean = false,
    /** v25: the host session may rename request targets and their listed descendants in place. */
    val targetRename: Boolean = false,
)

internal data class ExplorerActionTarget(
    val mimeType: String,
    val extension: String,
    val kind: Int = org.autojs.plugin.explorer.api.ExplorerActionValues.TARGET_FILE,
    val fileName: String = "",
)
