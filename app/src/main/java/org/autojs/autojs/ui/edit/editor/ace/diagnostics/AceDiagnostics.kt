package org.autojs.autojs.ui.edit.editor.ace.diagnostics

import android.os.Build
import android.webkit.WebView
import org.autojs.autojs.ui.edit.editor.ace.lsp.AceLspServerManager
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType

object AceDiagnostics {

    fun createNativeSnapshot(
        engineType: EditorEngineType,
        fileSizeBytes: Long?,
        largeFilePolicy: String,
    ): AceDiagnosticsSnapshot {
        val lspSnapshot = AceLspServerManager.preferenceSnapshot()
        return AceDiagnosticsSnapshot(
            engineType = engineType.name,
            aceHealthState = null,
            lastFailureType = null,
            lastFailureMessage = null,
            lastFailureFatal = null,
            assetUrl = null,
            webViewVersion = currentWebViewVersion(),
            androidVersion = androidVersion(),
            lspEnabled = lspSnapshot.enabled,
            lspState = lspSnapshot.state,
            lspTransport = lspSnapshot.transport,
            lspServerUri = lspSnapshot.serverUri,
            lspRootUri = lspSnapshot.rootUri,
            lspDocumentUri = lspSnapshot.documentUri,
            lspFallback = lspSnapshot.fallback,
            lspCompletionProvider = lspSnapshot.completionProvider,
            lspHoverProvider = lspSnapshot.hoverProvider,
            lspDiagnosticProvider = lspSnapshot.diagnosticProvider,
            lspSignatureProvider = lspSnapshot.signatureProvider,
            lspServerAvailable = lspSnapshot.serverAvailable,
            lspStartSupported = lspSnapshot.startSupported,
            lspSessionRevision = lspSnapshot.sessionRevision,
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
            lastBridgeEventName = null,
            currentBridgeEventWindowCount = 0,
            lastBridgeEventWindowCount = 0,
            maxBridgeEventWindowCount = 0,
            maxBridgeEventWindowStartedAtUptimeMillis = null,
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
            appendLine("lspTransport=${snapshot.lspTransport.orUnknown()}")
            appendLine("lspServerUri=${snapshot.lspServerUri.orUnknown()}")
            appendLine("lspRootUri=${snapshot.lspRootUri.orUnknown()}")
            appendLine("lspDocumentUri=${snapshot.lspDocumentUri.orUnknown()}")
            appendLine("lspFallback=${snapshot.lspFallback.orUnknown()}")
            appendLine("lspCompletionProvider=${snapshot.lspCompletionProvider.orUnknown()}")
            appendLine("lspHoverProvider=${snapshot.lspHoverProvider.orUnknown()}")
            appendLine("lspDiagnosticProvider=${snapshot.lspDiagnosticProvider.orUnknown()}")
            appendLine("lspSignatureProvider=${snapshot.lspSignatureProvider.orUnknown()}")
            appendLine("lspServerAvailable=${snapshot.lspServerAvailable}")
            appendLine("lspStartSupported=${snapshot.lspStartSupported}")
            appendLine("lspSessionRevision=${snapshot.lspSessionRevision}")
            appendLine("lspRuntimeState=${snapshot.lspRuntimeState.orUnknown()}")
            appendLine("aceFont=${snapshot.aceFont.orUnknown()}")
            appendLine("fileSizeBytes=${snapshot.fileSizeBytes?.toString().orUnknown()}")
            appendLine("largeFilePolicy=${snapshot.largeFilePolicy}")
            appendLine("textMirrorRevision=${snapshot.textMirrorRevision?.toString().orUnknown()}")
            appendLine("jsRevision=${snapshot.jsRevision.orUnknown()}")
            appendLine("lastReadyAtUptimeMillis=${snapshot.lastReadyAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastFirstPaintAtUptimeMillis=${snapshot.lastFirstPaintAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastThemeAppliedAtUptimeMillis=${snapshot.lastThemeAppliedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastPageLoadStartedAtUptimeMillis=${snapshot.lastPageLoadStartedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastHostDocumentLoadingStartedAtUptimeMillis=${snapshot.lastHostDocumentLoadingStartedAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastHostDocumentReadyAtUptimeMillis=${snapshot.lastHostDocumentReadyAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastDeferredStaticIndexReadyAtUptimeMillis=${snapshot.lastDeferredStaticIndexReadyAtUptimeMillis?.toString().orUnknown()}")
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
            appendLine("lastBridgeEventName=${snapshot.lastBridgeEventName.orUnknown()}")
            appendLine("currentBridgeEventWindowCount=${snapshot.currentBridgeEventWindowCount}")
            appendLine("lastBridgeEventWindowCount=${snapshot.lastBridgeEventWindowCount}")
            appendLine("maxBridgeEventWindowCount=${snapshot.maxBridgeEventWindowCount}")
            appendLine("maxBridgeEventWindowStartedAtUptimeMillis=${snapshot.maxBridgeEventWindowStartedAtUptimeMillis?.toString().orUnknown()}")
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
        return AceLspServerManager.preferenceSnapshot().state
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"

    const val LSP_DISABLED = AceLspServerManager.STATE_DISABLED
    const val LSP_LOCAL_LANGUAGE_SERVICE = AceLspServerManager.STATE_LOCAL_LANGUAGE_SERVICE
}
