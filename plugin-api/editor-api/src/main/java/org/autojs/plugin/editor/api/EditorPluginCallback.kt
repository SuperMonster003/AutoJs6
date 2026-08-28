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

    /** Available from editor contract 3. The host must validate the target before opening it. */
    fun onDefinitionNavigationRequested(target: EditorPluginDefinitionTarget) = Unit

    /**
     * Available from editor contract 4. The host must validate the exact active-document edits
     * against its current text before approving them. A plugin must not apply the action unless
     * [onComplete][EditorPluginBooleanCallback.onComplete] reports `true`.
     */
    fun onCurrentDocumentCodeActionRequested(
        action: EditorPluginCurrentDocumentCodeAction,
        onComplete: EditorPluginBooleanCallback,
    ) = onComplete.onComplete(false)

    /**
     * Available from editor contract 5. The host owns preview, validation and publication of the
     * complete multi-file transaction. A plugin must never write project files or apply a second
     * active-buffer edit after reporting this request.
     */
    fun onProjectRenameRequested(
        request: EditorPluginProjectRenameRequest,
        onComplete: EditorPluginProjectRenameCallback,
    ) = onComplete.onComplete(EditorPluginProjectRenameResult.REJECTED)

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

fun interface EditorPluginProjectRenameCallback {
    fun onComplete(result: EditorPluginProjectRenameResult)
}

enum class EditorPluginProjectRenameResult {
    APPLIED,
    REJECTED,
    STALE,
    FAILED,
}
