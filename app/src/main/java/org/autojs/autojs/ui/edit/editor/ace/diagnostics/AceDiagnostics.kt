package org.autojs.autojs.ui.edit.editor.ace.diagnostics

import android.os.Build
import android.webkit.WebView
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
            lspEnabled = false,
            lspState = LSP_DISABLED,
            aceFont = null,
            fileSizeBytes = fileSizeBytes,
            largeFilePolicy = largeFilePolicy,
            textMirrorRevision = null,
            jsRevision = null,
            lastReadyAtUptimeMillis = null,
            lastStateAtUptimeMillis = null,
            lastHeartbeatAtUptimeMillis = null,
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
            appendLine("lastStateAtUptimeMillis=${snapshot.lastStateAtUptimeMillis?.toString().orUnknown()}")
            appendLine("lastHeartbeatAtUptimeMillis=${snapshot.lastHeartbeatAtUptimeMillis?.toString().orUnknown()}")
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

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"

    const val LSP_DISABLED = "disabled"
}
