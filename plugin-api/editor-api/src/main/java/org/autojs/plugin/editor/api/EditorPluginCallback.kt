package org.autojs.plugin.editor.api

import android.os.Bundle

/**
 * One callback instance receives all asynchronous events for an editor session.
 * The plugin must deliver every callback on the Android main thread.
 */
interface EditorPluginCallback {

    fun onReady(state: EditorPluginState) = Unit

    fun onStateChanged(state: EditorPluginState) = Unit

    fun onTextChanged(text: String, state: EditorPluginState) = Unit

    fun onCursorChanged(lineText: String, line: Int, column: Int, state: EditorPluginState) = Unit

    fun onBreakpointChanged(line: Int, enabled: Boolean, state: EditorPluginState) = Unit

    fun onHistoryOperation(token: String, direction: EditorPluginHistoryDirection) = Unit

    fun onSelectionAction(action: EditorPluginSelectionAction) = Unit

    fun onTextSizeSpChangedByGesture(size: Float, persist: Boolean) = Unit

    fun onUserTouchInTextArea() = Unit

    fun onFailure(failure: EditorPluginFailure) = Unit

    fun onEvent(name: String, payload: Bundle? = null) = Unit

    fun onError(message: String, detail: String? = null) = Unit
}

fun interface EditorPluginBooleanCallback {
    fun onComplete(success: Boolean)
}

fun interface EditorPluginTextCallback {
    fun onText(text: String)
}
