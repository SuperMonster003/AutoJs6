package org.autojs.autojs.ui.edit.editor.ace.diagnostics

import android.os.Build
import android.webkit.WebView
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType

object AceDiagnostics {

    fun createNativeSnapshot(
        engineType: EditorEngineType,
        fileSizeBytes: Long?,
        largeFilePolicy: String,
    ): AceDiagnosticsSnapshot {
        return AceDiagnosticsSnapshot(
            engineType = engineType.name,
            aceHealthState = null,
            lastFailureType = null,
            lastFailureMessage = null,
            lastFailureFatal = null,
            assetUrl = null,
            webViewVersion = currentWebViewVersion(),
            androidVersion = androidVersion(),
            lspEnabled = AceEditorLspPreferences.isEnabled(),
            lspState = lspState(),
            aceFont = null,
            fileSizeBytes = fileSizeBytes,
            largeFilePolicy = largeFilePolicy,
            textMirrorRevision = null,
            jsRevision = null,
            lastReadyAtUptimeMillis = null,
            lastFirstPaintAtUptimeMillis = null,
            lastStateAtUptimeMillis = null,
            lastHeartbeatAtUptimeMillis = null,
            lastHeartbeatSuspendedAtUptimeMillis = null,
            lastHeartbeatResumedAtUptimeMillis = null,
            heartbeatSuspendedReason = null,
            lastImeState = null,
            lastImeHeight = null,
            lastImeRequestAtUptimeMillis = null,
            lastShowSoftInputAtUptimeMillis = null,
            lastHideSoftInputAtUptimeMillis = null,
            lastImeTransitionStartedAtUptimeMillis = null,
            lastImeTransitionEndedAtUptimeMillis = null,
            lastImeIgnoredRequestReason = null,
            lastResizeAtUptimeMillis = null,
            pendingAceResizeReason = null,
            lastAceResizeScheduledReason = null,
            lastAceResizeScheduledAtUptimeMillis = null,
            lastAceResizeDispatchedReason = null,
            lastAceResizeDispatchedAtUptimeMillis = null,
            lastAceResizeCompletedReason = null,
            lastAceResizeCompletedAtUptimeMillis = null,
            lastActionModeAtUptimeMillis = null,
            viewportPolicy = null,
            lastViewportPolicyEvent = null,
            lastViewportPolicyEventAtUptimeMillis = null,
            lastViewportPolicyImeVisible = null,
            lastViewportPolicyBottomInset = null,
            lastHostImeVisible = null,
            lastHostImeBottomInset = null,
            lastHostImeEventAtUptimeMillis = null,
            runtimeEventHistory = emptyList(),
        )
    }

    fun format(snapshot: AceDiagnosticsSnapshot): String {
        return buildString {
            appendLine("ACE diagnostics")
            appendLine("createdAtEpochMillis=${snapshot.createdAtEpochMillis}")
            appendLine("engineType=${snapshot.engineType}")
            appendLine("aceHealthState=${snapshot.aceHealthState.orUnknown()}")
            appendLine("lastFailureType=${snapshot.lastFailureType.orUnknown()}")
            appendLine("lastFailureMessage=${snapshot.lastFailureMessage.orUnknown()}")
            appendLine("lastFailureFatal=${snapshot.lastFailureFatal?.toString().orUnknown()}")
            appendLine("assetUrl=${snapshot.assetUrl.orUnknown()}")
            appendLine("webViewVersion=${snapshot.webViewVersion}")
            appendLine("androidVersion=${snapshot.androidVersion}")
            appendLine("lspEnabled=${snapshot.lspEnabled}")
            appendLine("lspState=${snapshot.lspState}")
            appendLine("aceFont=${snapshot.aceFont.orUnknown()}")
            appendLine("fileSizeBytes=${snapshot.fileSizeBytes?.toString().orUnknown()}")
            appendLine("largeFilePolicy=${snapshot.largeFilePolicy}")
            appendLine("textMirrorRevision=${snapshot.textMirrorRevision?.toString().orUnknown()}")
            appendLine("jsRevision=${snapshot.jsRevision.orUnknown()}")
            appendLine("lastReadyAtUptimeMillis=${snapshot.lastReadyAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastFirstPaintAtUptimeMillis=${snapshot.lastFirstPaintAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastStateAtUptimeMillis=${snapshot.lastStateAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastHeartbeatAtUptimeMillis=${snapshot.lastHeartbeatAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastHeartbeatSuspendedAtUptimeMillis=${snapshot.lastHeartbeatSuspendedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastHeartbeatResumedAtUptimeMillis=${snapshot.lastHeartbeatResumedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("heartbeatSuspendedReason=${snapshot.heartbeatSuspendedReason.orUnknown()}")
            appendLine("lastImeState=${snapshot.lastImeState.orUnknown()}")
            appendLine("lastImeHeight=${snapshot.lastImeHeight?.toString().orUnknown()}")
            appendLine("lastImeRequestAtUptimeMillis=${snapshot.lastImeRequestAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastShowSoftInputAtUptimeMillis=${snapshot.lastShowSoftInputAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastHideSoftInputAtUptimeMillis=${snapshot.lastHideSoftInputAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastImeTransitionStartedAtUptimeMillis=${snapshot.lastImeTransitionStartedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastImeTransitionEndedAtUptimeMillis=${snapshot.lastImeTransitionEndedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastImeIgnoredRequestReason=${snapshot.lastImeIgnoredRequestReason.orUnknown()}")
            appendLine("lastResizeAtUptimeMillis=${snapshot.lastResizeAtUptimeMillis?.toString().orUnknown()}")
            appendLine("pendingAceResizeReason=${snapshot.pendingAceResizeReason.orUnknown()}")
            appendLine("lastAceResizeScheduledReason=${snapshot.lastAceResizeScheduledReason.orUnknown()}")
            appendLine("lastAceResizeScheduledAtUptimeMillis=${snapshot.lastAceResizeScheduledAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastAceResizeDispatchedReason=${snapshot.lastAceResizeDispatchedReason.orUnknown()}")
            appendLine("lastAceResizeDispatchedAtUptimeMillis=${snapshot.lastAceResizeDispatchedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastAceResizeCompletedReason=${snapshot.lastAceResizeCompletedReason.orUnknown()}")
            appendLine("lastAceResizeCompletedAtUptimeMillis=${snapshot.lastAceResizeCompletedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastActionModeAtUptimeMillis=${snapshot.lastActionModeAtUptimeMillis?.toString().orUnknown()}")
            appendLine("viewportPolicy=${snapshot.viewportPolicy.orUnknown()}")
            appendLine("lastViewportPolicyEvent=${snapshot.lastViewportPolicyEvent.orUnknown()}")
            appendLine("lastViewportPolicyEventAtUptimeMillis=${snapshot.lastViewportPolicyEventAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastViewportPolicyImeVisible=${snapshot.lastViewportPolicyImeVisible?.toString().orUnknown()}")
            appendLine("lastViewportPolicyBottomInset=${snapshot.lastViewportPolicyBottomInset?.toString().orUnknown()}")
            appendLine("lastHostImeVisible=${snapshot.lastHostImeVisible?.toString().orUnknown()}")
            appendLine("lastHostImeBottomInset=${snapshot.lastHostImeBottomInset?.toString().orUnknown()}")
            appendLine("lastHostImeEventAtUptimeMillis=${snapshot.lastHostImeEventAtUptimeMillis?.toString().orUnknown()}")
            if (snapshot.runtimeEventHistory.isEmpty()) {
                appendLine("runtimeEventHistory=empty")
            } else {
                appendLine("runtimeEventHistory=${snapshot.runtimeEventHistory.size}")
                snapshot.runtimeEventHistory.forEach {
                    appendLine("runtimeEvent=$it")
                }
            }
        }.trimEnd()
    }

    fun currentWebViewVersion(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "unavailable-before-api-26"
        }
        return WebView.getCurrentWebViewPackage()?.let { pkg ->
            val versionName = pkg.versionName ?: "unknown"
            "${pkg.packageName} $versionName"
        } ?: "unknown"
    }

    fun androidVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, ${Build.VERSION.CODENAME})"
    }

    fun lspState(): String {
        return if (AceEditorLspPreferences.isEnabled()) LSP_CONFIGURED_NO_SERVER else LSP_DISABLED
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"

    const val LSP_DISABLED = "disabled"
    const val LSP_CONFIGURED_NO_SERVER = "configured-no-server"
}
