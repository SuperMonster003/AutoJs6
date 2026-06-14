package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs6.BuildConfig

object AceEditorTestHooks {

    @Volatile
    var forceAssetMissing: Boolean = false

    @Volatile
    var forceReadyTimeout: Boolean = false

    @Volatile
    var forceJsInitThrow: Boolean = false

    @Volatile
    var forceRendererGoneFallback: Boolean = false

    @Volatile
    var forceLargeFileNativeRoute: Boolean = false

    fun shouldForceAssetMissing(): Boolean = BuildConfig.DEBUG && forceAssetMissing

    fun shouldForceReadyTimeout(): Boolean = BuildConfig.DEBUG && forceReadyTimeout

    fun shouldForceJsInitThrow(): Boolean = BuildConfig.DEBUG && forceJsInitThrow

    fun shouldForceRendererGoneFallback(): Boolean = BuildConfig.DEBUG && forceRendererGoneFallback

    fun shouldForceLargeFileNativeRoute(): Boolean = BuildConfig.DEBUG && forceLargeFileNativeRoute

    fun reset() {
        forceAssetMissing = false
        forceReadyTimeout = false
        forceJsInitThrow = false
        forceRendererGoneFallback = false
        forceLargeFileNativeRoute = false
    }
}
