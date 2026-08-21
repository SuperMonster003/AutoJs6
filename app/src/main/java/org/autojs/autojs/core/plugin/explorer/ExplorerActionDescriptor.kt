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
    val mimeTypes: Set<String>,
    val extensions: Set<String>,
    val outputMode: Int,
    val outputMimeTypes: Set<String>,
    val outputNameSuffix: String?,
    val maxOutputBytes: Long?,
)

internal data class ExplorerActionTarget(
    val mimeType: String,
    val extension: String,
    val kind: Int = org.autojs.plugin.explorer.api.ExplorerActionValues.TARGET_FILE,
)
