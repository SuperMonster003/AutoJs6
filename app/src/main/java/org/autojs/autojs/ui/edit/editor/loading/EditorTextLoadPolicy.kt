package org.autojs.autojs.ui.edit.editor.loading

import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType

object EditorTextLoadPolicy {

    const val LARGE_FILE_THRESHOLD_BYTES = 512 * 1024
    const val ACE_UNSAFE_LINE_LENGTH = 64 * 1024
    const val ACE_LIGHTWEIGHT_DOCUMENT_LENGTH = 256 * 1024

    fun shouldStreamAsLargeFile(sizeBytes: Long?): Boolean {
        return sizeBytes != null && EditorKeys.shouldEnableLargeFileModeForSize(sizeBytes)
    }

    fun shouldLoadProgressively(textLength: Int): Boolean {
        return EditorKeys.shouldEnableLargeFileModeForSize(textLength.toLong())
    }

    /**
     * ACE's JavaScript mode, wrapping, diagnostics, and bridge snapshots all become expensive for
     * a pathological single line. This is common when UTF-8 bytes are deliberately reloaded as an
     * explicit-endian UTF-16 charset, because the original byte-level newlines disappear.
     */
    fun hasAceUnsafeLine(text: CharSequence): Boolean {
        var currentLineLength = 0
        text.forEach { char ->
            if (char == '\n' || char == '\r') {
                currentLineLength = 0
            } else {
                currentLineLength++
                if (currentLineLength > ACE_UNSAFE_LINE_LENGTH) return true
            }
        }
        return false
    }

    /**
     * Start sizeable Android WebView documents in lightweight mode as well. Waiting until a wrong
     * encoding creates one pathological line is too late: JavaScript tokenization and semantic
     * work for the original document may already occupy the event loop and delay Reload history.
     */
    fun requiresAceLightweightMode(text: CharSequence): Boolean {
        return text.length >= ACE_LIGHTWEIGHT_DOCUMENT_LENGTH || hasAceUnsafeLine(text)
    }

    fun shouldRouteAceToNative(
        engineType: EditorEngineType,
        largeText: Boolean,
        forceNativeRoute: Boolean,
    ): Boolean {
        return engineType == EditorEngineType.ACE && forceNativeRoute
    }

    fun largeFilePolicyLabel(
        largeFileMode: Boolean,
        forceNativeRoute: Boolean,
    ): String {
        val route = when {
            forceNativeRoute -> "debug-force-native"
            largeFileMode -> "auto-native-large-file"
            else -> "auto"
        }
        return "$route;thresholdBytes=${EditorKeys.largeFileModeThresholdBytes}"
    }
}
