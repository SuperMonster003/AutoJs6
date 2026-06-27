package org.autojs.autojs.ui.edit.editor.ace.lsp

import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences

class AceLspServerManager(
    private val enabledProvider: () -> Boolean = { AceEditorLspPreferences.isEnabled() },
) {

    private var attached = false
    private var sessionRevision = 0L

    fun attach() {
        if (!attached) {
            attached = true
            sessionRevision += 1
        }
    }

    fun detach() {
        if (attached) {
            attached = false
            sessionRevision += 1
        }
    }

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
        return AceLspServerSnapshot(
            enabled = true,
            attached = attached,
            state = STATE_CONFIGURED_NO_SERVER,
            transport = TRANSPORT_UNAVAILABLE,
            serverUri = null,
            rootUri = SYNTHETIC_ROOT_URI,
            documentUri = SYNTHETIC_DOCUMENT_URI,
            libraryUris = DEFAULT_LIBRARY_URIS,
            fallback = FALLBACK_STATIC_COMPLETION,
            startSupported = false,
            serverAvailable = false,
            reason = REASON_NO_ALPHA_SERVER_ENDPOINT,
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
            append("}")
        }
    }

    private fun StringBuilder.appendJsonString(value: String?): StringBuilder {
        return append(jsonString(value))
    }

    companion object {
        const val MANAGER_NAME = "native-alpha"
        const val STATE_DISABLED = "disabled"
        const val STATE_CONFIGURED_NO_SERVER = "configured-no-server"
        const val TRANSPORT_UNAVAILABLE = "unavailable"
        const val FALLBACK_STATIC_COMPLETION = "static-completion"
        const val REASON_NO_ALPHA_SERVER_ENDPOINT = "no-alpha-server-endpoint"
        const val SYNTHETIC_ROOT_URI = "file:///autojs6/editor"
        const val SYNTHETIC_DOCUMENT_URI = "file:///autojs6/editor/current.js"

        val DEFAULT_LIBRARY_URIS = listOf("autojs6/types/lib.autojs6.d.ts")

        fun preferenceSnapshot(): AceLspServerSnapshot {
            return AceLspServerManager().snapshot()
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
    val sessionRevision: Long = 0L,
)
