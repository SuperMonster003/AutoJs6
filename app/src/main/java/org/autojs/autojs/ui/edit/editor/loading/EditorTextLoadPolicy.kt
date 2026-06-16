package org.autojs.autojs.ui.edit.editor.loading

import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType

object EditorTextLoadPolicy {

    const val LARGE_FILE_THRESHOLD_BYTES = 512 * 1024

    fun shouldStreamAsLargeFile(sizeBytes: Long?): Boolean {
        return sizeBytes != null && sizeBytes >= LARGE_FILE_THRESHOLD_BYTES
    }

    fun shouldLoadProgressively(textLength: Int): Boolean {
        return textLength > LARGE_FILE_THRESHOLD_BYTES
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
        return "$route;thresholdBytes=$LARGE_FILE_THRESHOLD_BYTES"
    }
}
