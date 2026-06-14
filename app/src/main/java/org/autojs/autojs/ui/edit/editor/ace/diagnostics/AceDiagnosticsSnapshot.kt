package org.autojs.autojs.ui.edit.editor.ace.diagnostics

data class AceDiagnosticsSnapshot(
    val engineType: String,
    val aceHealthState: String?,
    val lastFailureType: String?,
    val lastFailureMessage: String?,
    val lastFailureFatal: Boolean?,
    val assetUrl: String?,
    val webViewVersion: String,
    val androidVersion: String,
    val lspEnabled: Boolean,
    val lspState: String,
    val aceFont: String?,
    val fileSizeBytes: Long?,
    val largeFilePolicy: String,
    val textMirrorRevision: Long?,
    val jsRevision: String?,
    val lastReadyAtUptimeMillis: Long?,
    val lastStateAtUptimeMillis: Long?,
    val lastHeartbeatAtUptimeMillis: Long?,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
