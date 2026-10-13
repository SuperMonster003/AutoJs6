package org.autojs.plugin.editor.api

import android.view.View

/**
 * Same-process editor controller. Implementations own the returned views until [destroy].
 * Every member must be accessed on the Android main thread, including property reads,
 * asynchronous callback delivery and [destroy].
 */
interface EditorPluginSession {

    val view: View
    val inputView: View
    val isReady: Boolean
    val currentState: EditorPluginState
    val textSnapshot: String
    val isTextChanged: Boolean
    val canUndo: Boolean
    val canRedo: Boolean
    val lineCountSnapshot: Int
    val selection: EditorPluginSelection
    val selectedTextSnapshot: String
    val breakpointsSnapshot: List<EditorPluginBreakpoint>
    val foundIndexSnapshot: Int
    val isUserTouching: Boolean
    val isLoadingText: Boolean
    val isReadOnly: Boolean
    val completionPopupOpen: Boolean

    var text: String

    fun setInitialText(text: String?)
    fun getTextForSaving(callback: EditorPluginTextCallback)
    fun markTextAsSaved()
    fun markTextAsChanged()
    fun refreshState()

    fun replaceAllTextUndoably(
        text: String,
        token: String,
        onComplete: EditorPluginBooleanCallback? = null,
    )

    fun addUndoableHistoryMarker(
        token: String,
        onComplete: EditorPluginBooleanCallback? = null,
    )

    fun insert(text: String?)
    fun insertAtLine(line: Int, text: String?)
    fun insertShortcutText(text: String?)
    fun insertTab()
    fun acceptCompletionOrInsertTab()
    fun moveCompletionSelectionOrCursor(delta: Int)
    fun handleEscapeKey()

    /**
     * Context-aware smart hint trigger (contract 6+): starts completion, shows or cycles
     * signature help, or shows the symbol documentation for the current cursor position.
     */
    fun triggerSmartHint()

    fun copyLine()
    fun deleteLine()
    fun clear()
    fun toggleComment()
    fun beautifyCode()

    /** Folds all code blocks in the document, including in read-only mode (contract 7+). */
    fun foldAll()

    /** Removes all document folds, including nested folds, in read-only mode too (contract 7+). */
    fun unfoldAll()

    fun undo()
    fun redo()
    fun setRedoUndoEnabled(enabled: Boolean)
    fun markUndoRedoBaselineAsUnchanged()

    fun jumpTo(line: Int, column: Int = 0)
    fun jumpToStart()
    fun jumpToEnd()
    fun jumpToLineStart()
    fun jumpToLineEnd()
    fun jumpToNextLine()
    fun jumpToPrevLine()
    fun moveCursor(deltaChars: Int)
    fun selectRange(startOffset: Int, endOffset: Int)
    fun selectAll()

    @Throws(EditorPluginSearchSyntaxException::class)
    fun find(query: String, options: EditorPluginSearchOptions): EditorPluginSearchResult

    fun findNext(): EditorPluginSearchResult
    fun findPrev(): EditorPluginSearchResult

    @Throws(EditorPluginSearchSyntaxException::class)
    fun replace(
        query: String,
        replacement: String,
        options: EditorPluginSearchOptions,
    ): EditorPluginSearchResult

    fun replaceSelection(): EditorPluginSearchResult

    @Throws(EditorPluginSearchSyntaxException::class)
    fun replaceAll(
        query: String,
        replacement: String?,
        options: EditorPluginSearchOptions,
    ): EditorPluginSearchResult

    fun resetSearchState()
    fun resetFoundIndexForFindNextFromCursor()
    fun resetFoundIndexForFindPrevFromCursor()

    fun setReadOnly(readOnly: Boolean)
    fun setDebuggingLine(line: Int)
    fun setBreakpoint(line: Int, enabled: Boolean)
    fun addOrRemoveBreakpointAtCurrentLine()
    fun removeAllBreakpoints()

    fun setTheme(theme: EditorPluginTheme)
    fun setTextSizeSp(size: Float)
    fun getTextSizeSp(): Float
    fun setEditorFont(fontId: String)
    fun notifyPinchToZoomStrategyChanged(newKey: String?)
    fun notifyDisplayPreferencesChanged()
    fun setDocumentPath(path: String?)
    fun refreshLsp()

    fun requestEditorFocus()
    fun ensureCursorVisible()
    fun onImeInsetsChanged(visible: Boolean, bottomInset: Int)
    fun refreshHighlightTokensIfAllowed()
    fun setProgress(progress: Boolean, interactive: Boolean = false)

    fun beginLargeTextLoading()
    fun appendTextChunk(chunk: CharSequence)
    fun endLargeTextLoading(markClean: Boolean)
    fun cancelLargeTextLoading()
    fun setLoadingText(loading: Boolean)

    fun createStateSnapshot(): EditorPluginSnapshot
    fun restoreStateSnapshot(snapshot: EditorPluginSnapshot)

    fun createDiagnosticsSnapshot(
        fileSizeBytes: Long? = null,
        largeFilePolicy: String = "auto",
    ): EditorPluginDiagnostics

    fun destroy()
}
