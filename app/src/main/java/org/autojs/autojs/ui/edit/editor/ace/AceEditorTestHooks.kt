package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs6.BuildConfig

object AceEditorTestHooks {

    @Volatile
    var forceLargeFileNativeRoute: Boolean = false

    fun shouldForceLargeFileNativeRoute(): Boolean = BuildConfig.DEBUG && forceLargeFileNativeRoute
}
