package org.autojs.autojs.ui.edit.editor.ace.lsp

import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AceLspServerManager(
    private val enabledProvider: () -> Boolean = { AceEditorLspPreferences.isEnabled() },
    private val documentAllowedProvider: (String?) -> Boolean = { AceEditorLspPreferences.isDocumentAllowed(it) },
) {

    @Volatile
    private var attached = false

    @Volatile
    private var sessionRevision = 0L

    @Volatile
    private var documentPath: String = SYNTHETIC_DOCUMENT_URI

    @Synchronized
    fun setDocumentPath(path: String?) {
        val normalized = path?.takeIf { it.isNotBlank() } ?: SYNTHETIC_DOCUMENT_URI
        if (documentPath == normalized) {
            return
        }
        documentPath = normalized
        sessionRevision += 1
    }

    @Synchronized
    fun attach() {
        if (!attached) {
            attached = true
            sessionRevision += 1
        }
    }

    @Synchronized
    fun detach() {
        if (attached) {
            attached = false
            sessionRevision += 1
        }
    }

    @Synchronized
    fun snapshot(): AceLspServerSnapshot {
        val enabled = enabledProvider()
        if (!enabled) {
            return AceLspServerSnapshot(
                enabled = false,
                attached = attached,
                state = STATE_DISABLED,
                sessionRevision = sessionRevision,
            )
        }
        if (!documentAllowedProvider(documentPath)) {
            return AceLspServerSnapshot(
                enabled = false,
                attached = attached,
                state = STATE_DISABLED_FILE_TYPE,
                reason = REASON_DISABLED_FILE_TYPE,
                sessionRevision = sessionRevision,
            )
        }
        return AceLspServerSnapshot(
            enabled = true,
            attached = attached,
            state = STATE_LOCAL_LANGUAGE_SERVICE,
            transport = TRANSPORT_IN_PROCESS,
            serverUri = null,
            rootUri = SYNTHETIC_ROOT_URI,
            documentUri = documentUriForPath(documentPath),
            libraryUris = DEFAULT_LIBRARY_URIS,
            fallback = FALLBACK_STATIC_COMPLETION,
            startSupported = false,
            serverAvailable = false,
            reason = REASON_LOCAL_LANGUAGE_SERVICE,
            completionProvider = COMPLETION_PROVIDER_LOCAL_INDEX,
            hoverProvider = HOVER_PROVIDER_LOCAL_INDEX,
            diagnosticProvider = DIAGNOSTIC_PROVIDER_ACE_JSHINT,
            signatureProvider = SIGNATURE_PROVIDER_STATIC_LOCAL,
            features = DEFAULT_FEATURES,
            maxDocumentLength = MAX_DOCUMENT_LENGTH,
            sessionRevision = sessionRevision,
        )
    }

    fun bridgeOptionsJson(): String {
        val snapshot = snapshot()
        if (!snapshot.enabled) {
            return "{}"
        }
        return buildString {
            append("{")
            append("\"enabled\":true")
            append(",\"manager\":").appendJsonString(MANAGER_NAME)
            append(",\"state\":").appendJsonString(snapshot.state)
            append(",\"transport\":").appendJsonString(snapshot.transport)
            append(",\"serverUri\":null")
            append(",\"rootUri\":").appendJsonString(snapshot.rootUri)
            append(",\"documentUri\":").appendJsonString(snapshot.documentUri)
            append(",\"libraryUris\":")
            append(snapshot.libraryUris.joinToString(prefix = "[", postfix = "]") { jsonString(it) })
            append(",\"fallback\":").appendJsonString(snapshot.fallback)
            append(",\"startSupported\":").append(snapshot.startSupported)
            append(",\"serverAvailable\":").append(snapshot.serverAvailable)
            append(",\"reason\":").appendJsonString(snapshot.reason)
            append(",\"completionProvider\":").appendJsonString(snapshot.completionProvider)
            append(",\"hoverProvider\":").appendJsonString(snapshot.hoverProvider)
            append(",\"diagnosticProvider\":").appendJsonString(snapshot.diagnosticProvider)
            append(",\"signatureProvider\":").appendJsonString(snapshot.signatureProvider)
            append(",\"features\":")
            append(snapshot.features.joinToString(prefix = "[", postfix = "]") { jsonString(it) })
            append(",\"maxDocumentLength\":").append(snapshot.maxDocumentLength)
            append("}")
        }
    }

    private fun StringBuilder.appendJsonString(value: String?): StringBuilder {
        return append(jsonString(value))
    }

    companion object {
        const val MANAGER_NAME = "native-local"
        const val STATE_DISABLED = "disabled"
        const val STATE_DISABLED_FILE_TYPE = "disabled-file-type"
        const val STATE_LOCAL_LANGUAGE_SERVICE = "local-language-service"
        const val TRANSPORT_IN_PROCESS = "in-process"
        const val FALLBACK_STATIC_COMPLETION = "static-completion"
        const val REASON_DISABLED_FILE_TYPE = "file-type-disabled"
        const val REASON_LOCAL_LANGUAGE_SERVICE = "bundled-typescript-language-service"
        const val COMPLETION_PROVIDER_LOCAL_INDEX = "local-index"
        const val HOVER_PROVIDER_LOCAL_INDEX = "local-index"
        const val COMPLETION_PROVIDER_TYPESCRIPT = "typescript-language-service"
        const val HOVER_PROVIDER_TYPESCRIPT = "typescript-language-service"
        const val DIAGNOSTIC_PROVIDER_ACE_JSHINT = "ace-jshint"
        const val DIAGNOSTIC_PROVIDER_TYPESCRIPT = "typescript-language-service"
        const val SIGNATURE_PROVIDER_STATIC_LOCAL = "static-local"
        const val SIGNATURE_PROVIDER_TYPESCRIPT = "typescript-language-service"
        const val SYNTHETIC_ROOT_URI = "file:///autojs6/editor"
        const val SYNTHETIC_DOCUMENT_URI = "file:///autojs6/editor/current.js"
        const val MAX_DOCUMENT_LENGTH = 512 * 1024

        val DEFAULT_LIBRARY_URIS = listOf(
            "autojs6/typescript/lib.es2020.d.ts",
            "autojs6/types/lib.autojs6.d.ts",
            "autojs6/types/lib.autojs6.extra.d.ts",
        )
        val DEFAULT_FEATURES = listOf("completion", "hover", "diagnostics", "signatureHelp")

        fun preferenceSnapshot(): AceLspServerSnapshot {
            return AceLspServerManager().snapshot()
        }

        fun documentUriForPath(path: String?): String {
            val fileName = documentFileName(path)
            return "$SYNTHETIC_ROOT_URI/${encodePathSegment(fileName)}"
        }

        private fun documentFileName(path: String?): String {
            return path
                ?.substringBefore('?')
                ?.substringBefore('#')
                ?.replace('\\', '/')
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?: SYNTHETIC_DOCUMENT_URI.substringAfterLast('/')
        }

        private fun encodePathSegment(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        }

        private fun jsonString(value: String?): String {
            if (value == null) {
                return "null"
            }
            return buildString {
                append('"')
                for (ch in value) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> {
                            if (ch.code < 0x20) {
                                append("\\u")
                                append(ch.code.toString(16).padStart(4, '0'))
                            } else {
                                append(ch)
                            }
                        }
                    }
                }
                append('"')
            }
        }
    }
}

data class AceLspServerSnapshot(
    val enabled: Boolean,
    val attached: Boolean,
    val state: String,
    val transport: String? = null,
    val serverUri: String? = null,
    val rootUri: String? = null,
    val documentUri: String? = null,
    val libraryUris: List<String> = emptyList(),
    val fallback: String? = null,
    val startSupported: Boolean = false,
    val serverAvailable: Boolean = false,
    val reason: String? = null,
    val completionProvider: String? = null,
    val hoverProvider: String? = null,
    val diagnosticProvider: String? = null,
    val signatureProvider: String? = null,
    val features: List<String> = emptyList(),
    val maxDocumentLength: Int = 0,
    val sessionRevision: Long = 0L,
)
