package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import android.view.View
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.ui.edit.theme.Theme

interface ScriptEditorEngine {

    val view: View
    val inputView: View
    val engineType: EditorEngineType
    val isReady: Boolean

    var text: String
    val textSnapshot: String
    val isTextChanged: Boolean
    val canUndo: Boolean
    val canRedo: Boolean
    val lineCountSnapshot: Int
    val selection: EditorSelection
    val selectedTextSnapshot: String
    val breakpoints: Map<Int, EditorBreakpoint>
    val breakpointsSnapshot: List<EditorBreakpoint>
    val foundIndexSnapshot: Int
    val isUserTouching: Boolean
    val isLoadingText: Boolean
    val isReadOnly: Boolean

    var onUserTouchInTextArea: (() -> Unit)?

    fun setInitialText(text: String?)
    fun getTextForSaving(callback: (String) -> Unit)
    fun markTextAsSaved()
    fun markTextAsChanged()

    /** Replaces the complete document as one undoable history entry. */
    fun replaceAllTextUndoably(
        text: String,
        token: String,
        onComplete: ((Boolean) -> Unit)? = null,
    )

    /** Adds an undoable history entry without changing the document text. */
    fun addUndoableHistoryMarker(
        token: String,
        onComplete: ((Boolean) -> Unit)? = null,
    )

    fun setHistoryOperationListener(listener: EditorHistoryOperationListener?)

    fun insert(text: String?)
    fun insert(line: Int, text: String?)

    fun copyLine()
    fun deleteLine()
    fun clear()
    fun toggleComment()
    fun beautifyCode()

    val supportsCodeFolding: Boolean get() = false
    fun foldAll() = Unit
    fun unfoldAll() = Unit

    fun undo()
    fun redo()
    fun setRedoUndoEnabled(enabled: Boolean)
    fun markUndoRedoBaselineAsUnchanged()

    fun jumpTo(line: Int, column: Int = 0)
    fun selectRange(startOffset: Int, endOffset: Int)
    fun jumpToStart()
    fun jumpToEnd()
    fun jumpToLineStart()
    fun jumpToLineEnd()
    fun jumpToNextLine()
    fun jumpToPrevLine()
    fun moveCursor(deltaChars: Int)

    @Throws(EditorSearchSyntaxException::class)
    fun find(query: String, options: EditorSearchOptions): EditorSearchResult

    fun findNext(): EditorSearchResult
    fun findPrev(): EditorSearchResult

    @Throws(EditorSearchSyntaxException::class)
    fun replace(query: String, replacement: String, options: EditorSearchOptions): EditorSearchResult

    fun replaceSelection(): EditorSearchResult

    @Throws(EditorSearchSyntaxException::class)
    fun replaceAll(query: String, replacement: String?, options: EditorSearchOptions): EditorSearchResult

    fun resetSearchState()
    fun resetFoundIndexForFindNextFromCursor()
    fun resetFoundIndexForFindPrevFromCursor()

    fun setReadOnly(readOnly: Boolean)
    fun setDebuggingLine(line: Int)
    fun addOrRemoveBreakpointAtCurrentLine()
    fun removeAllBreakpoints()
    fun setBreakpointChangeListener(listener: EditorBreakpointChangeListener?)

    fun setTheme(theme: Theme)
    fun setTextSizeSp(size: Float)
    fun getTextSizeSp(): Float
    fun notifyPinchToZoomStrategyChanged(newKey: String?)
    fun notifyDisplayPreferencesChanged()
    fun setDocumentPath(path: String?) = Unit

    fun addCursorChangeCallback(listener: EditorCursorChangeListener)
    fun removeCursorChangeCallback(listener: EditorCursorChangeListener)
    fun addTextChangeListener(listener: EditorTextChangeListener)
    fun removeTextChangeListener(listener: EditorTextChangeListener)
    fun createAutoCompletion(context: Context): AutoCompletion?

    fun requestEditorFocus()
    fun ensureCursorVisibleIfPossible()
    fun onImeInsetsChanged(visible: Boolean, bottomInset: Int) = Unit
    fun refreshHighlightTokensIfAllowed()
    fun setProgress(progress: Boolean, interactive: Boolean = false)

    fun beginLargeTextLoading()
    fun appendTextChunk(chunk: CharSequence)
    fun endLargeTextLoading(markClean: Boolean)
    fun cancelLargeTextLoading()
    fun setLoadingText(loading: Boolean)

    fun createStateSnapshot(): EditorStateSnapshot
    fun restoreStateSnapshot(snapshot: EditorStateSnapshot)

    fun destroy()
}
