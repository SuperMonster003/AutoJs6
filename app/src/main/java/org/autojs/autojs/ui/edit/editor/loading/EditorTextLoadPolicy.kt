package org.autojs.autojs.ui.edit.editor.loading

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType

object EditorTextLoadPolicy {

    const val LARGE_FILE_THRESHOLD_BYTES = 512 * 1024

    fun shouldStreamAsLargeFile(sizeBytes: Long?): Boolean {
        return sizeBytes != null && Pref.shouldEnableLargeFileModeForSize(sizeBytes)
    }

    fun shouldLoadProgressively(textLength: Int): Boolean {
        return Pref.shouldEnableLargeFileModeForSize(textLength.toLong())
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
        return "$route;thresholdBytes=${Pref.largeFileModeThresholdBytes}"
    }
}
