package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import android.graphics.Rect
import android.text.TextWatcher
import android.view.View
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.ui.edit.editor.CodeEditor
import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences
import org.autojs.autojs.ui.edit.editor.LayoutHelper
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.widget.SimpleTextWatcher
import org.autojs.autojs.util.DisplayUtils.pxToSp
import java.util.IdentityHashMap

class NativeCodeEditorEngine(
    val codeEditor: CodeEditor,
) : ScriptEditorEngine {

    private val cursorCallbacks = IdentityHashMap<EditorCursorChangeListener, CodeEditor.CursorChangeCallback>()
    private val textChangeCallbacks = IdentityHashMap<EditorTextChangeListener, TextWatcher>()
    private val tmpCursorRect = Rect()

    private var breakpointChangeListener: CodeEditor.BreakpointChangeListener? = null
    private var readOnly = false
    private var themeKey: String? = null
    private var searchQuery: String? = null
    private var searchOptions: EditorSearchOptions? = null

    init {
        applyDisplayPreferences()
    }

    override val view: View
        get() = codeEditor

    override val inputView: View
        get() = codeEditor.codeEditText

    override val engineType: EditorEngineType
        get() = EditorEngineType.NATIVE

    override val isReady: Boolean
        get() = true

    override var text: String
        get() = codeEditor.text
        set(value) {
            codeEditor.text = value
        }

    override val textSnapshot: String
        get() = codeEditor.text

    override val isTextChanged: Boolean
        get() = codeEditor.isTextChanged

    override val canUndo: Boolean
        get() = !readOnly && codeEditor.canUndo()

    override val canRedo: Boolean
        get() = !readOnly && codeEditor.canRedo()

    override val lineCountSnapshot: Int
        get() = codeEditor.codeEditText.getLogicalLineCount()

    override val selection: EditorSelection
        get() {
            val length = textSnapshot.length
            val start = codeEditor.codeEditText.selectionStart.coerceIn(0, length)
            val end = codeEditor.codeEditText.selectionEnd.coerceIn(0, length)
            return createSelection(minOf(start, end), maxOf(start, end))
        }

    override val selectedTextSnapshot: String
        get() {
            val s = selection.startOffset
            val e = selection.endOffset
            return if (s == e) "" else textSnapshot.substring(s, e)
        }

    override val breakpoints: Map<Int, EditorBreakpoint>
        get() = codeEditor.breakpoints.mapValues { (_, breakpoint) ->
            EditorBreakpoint(line = breakpoint.line, enabled = breakpoint.enabled)
        }

    override val breakpointsSnapshot: List<EditorBreakpoint>
        get() = breakpoints.values.toList()

    override val foundIndexSnapshot: Int
        get() = codeEditor.getFoundIndex()

    override val isUserTouching: Boolean
        get() = codeEditor.isUserTouching()

    override val isLoadingText: Boolean
        get() = codeEditor.codeEditText.isLoadingText()

    override val isReadOnly: Boolean
        get() = readOnly

    override var onUserTouchInTextArea: (() -> Unit)?
        get() = codeEditor.codeEditText.onUserTouchInTextArea
        set(value) {
            codeEditor.codeEditText.onUserTouchInTextArea = value
        }

    override fun setInitialText(text: String?) {
        codeEditor.setInitialText(text)
    }

    override fun getTextForSaving(callback: (String) -> Unit) {
        callback(codeEditor.text)
    }

    override fun markTextAsSaved() {
        codeEditor.markTextAsSaved()
    }

    override fun insert(text: String?) {
        if (!canMutateText()) {
            return
        }
        codeEditor.insert(text)
    }

    override fun insert(line: Int, text: String?) {
        if (!canMutateText()) {
            return
        }
        if (line in 0 until lineCountSnapshot) {
            codeEditor.insert(line, text)
        }
    }

    override fun copyLine() {
        codeEditor.copyLine()
    }

    override fun deleteLine() {
        if (!canMutateText()) {
            return
        }
        codeEditor.deleteLine()
    }

    override fun clear() {
        if (!canMutateText()) {
            return
        }
        codeEditor.clear()
    }

    override fun toggleComment() {
        if (!canMutateText()) {
            return
        }
        codeEditor.commentHelper.toggle()
    }

    override fun beautifyCode() {
        if (!canMutateText()) {
            return
        }
        codeEditor.beautifyCode()
    }

    override fun undo() {
        if (!canMutateText()) {
            return
        }
        codeEditor.undo()
    }

    override fun redo() {
        if (!canMutateText()) {
            return
        }
        codeEditor.redo()
    }

    override fun setRedoUndoEnabled(enabled: Boolean) {
        codeEditor.setRedoUndoEnabled(enabled)
    }

    override fun markUndoRedoBaselineAsUnchanged() {
        codeEditor.markUndoRedoBaselineAsUnchanged()
    }

    override fun jumpTo(line: Int, column: Int) {
        val offset = offsetForLineColumn(line, column) ?: return
        codeEditor.codeEditText.setSelection(offset)
    }

    override fun jumpToStart() {
        codeEditor.jumpToStart()
    }

    override fun jumpToEnd() {
        codeEditor.jumpToEnd()
    }

    override fun jumpToLineStart() {
        codeEditor.jumpToLineStart()
    }

    override fun jumpToLineEnd() {
        codeEditor.jumpToLineEnd()
    }

    override fun jumpToNextLine() {
        codeEditor.jumpToNextLine()
    }

    override fun jumpToPrevLine() {
        codeEditor.jumpToPrevLine()
    }

    override fun moveCursor(deltaChars: Int) {
        codeEditor.moveCursor(deltaChars)
    }

    @Throws(EditorSearchSyntaxException::class)
    override fun find(query: String, options: EditorSearchOptions): EditorSearchResult {
        searchQuery = query
        searchOptions = options
        try {
            codeEditor.find(query, options.usingRegex)
        } catch (e: CodeEditor.CheckedPatternSyntaxException) {
            throw EditorSearchSyntaxException(e)
        }
        return currentSearchResult(query)
    }

    override fun findNext(): EditorSearchResult {
        codeEditor.findNext()
        return currentSearchResult(searchQuery)
    }

    override fun findPrev(): EditorSearchResult {
        codeEditor.findPrev()
        return currentSearchResult(searchQuery)
    }

    @Throws(EditorSearchSyntaxException::class)
    override fun replace(query: String, replacement: String, options: EditorSearchOptions): EditorSearchResult {
        searchQuery = query
        searchOptions = options
        try {
            codeEditor.replace(query, replacement, options.usingRegex)
        } catch (e: CodeEditor.CheckedPatternSyntaxException) {
            throw EditorSearchSyntaxException(e)
        }
        return currentSearchResult(query)
    }

    override fun replaceSelection(): EditorSearchResult {
        if (!canMutateText()) {
            return currentSearchResult(searchQuery).copy(found = false)
        }
        codeEditor.replaceSelection()
        return currentSearchResult(searchQuery)
    }

    @Throws(EditorSearchSyntaxException::class)
    override fun replaceAll(query: String, replacement: String?, options: EditorSearchOptions): EditorSearchResult {
        if (!canMutateText()) {
            return EditorSearchResult(found = false, foundIndex = codeEditor.getFoundIndex(), query = query)
        }
        searchQuery = query
        searchOptions = options
        try {
            codeEditor.replaceAll(query, replacement, options.usingRegex)
        } catch (e: CodeEditor.CheckedPatternSyntaxException) {
            throw EditorSearchSyntaxException(e)
        }
        return EditorSearchResult(found = true, foundIndex = codeEditor.getFoundIndex(), query = query)
    }

    override fun resetSearchState() {
        searchQuery = null
        searchOptions = null
        codeEditor.resetFoundIndexForFindNextFromCursor()
    }

    override fun resetFoundIndexForFindNextFromCursor() {
        codeEditor.resetFoundIndexForFindNextFromCursor()
    }

    override fun resetFoundIndexForFindPrevFromCursor() {
        codeEditor.resetFoundIndexForFindPrevFromCursor()
    }

    override fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        codeEditor.setReadOnly(readOnly)
    }

    override fun setDebuggingLine(line: Int) {
        codeEditor.setDebuggingLine(line)
    }

    override fun addOrRemoveBreakpointAtCurrentLine() {
        codeEditor.addOrRemoveBreakpointAtCurrentLine()
    }

    override fun removeAllBreakpoints() {
        codeEditor.removeAllBreakpoints()
    }

    override fun setBreakpointChangeListener(listener: EditorBreakpointChangeListener?) {
        breakpointChangeListener = listener?.let {
            object : CodeEditor.BreakpointChangeListener {
                override fun onBreakpointChange(line: Int, enabled: Boolean) {
                    it.onBreakpointChange(line, enabled)
                }

                override fun onAllBreakpointRemoved(count: Int) {
                    it.onAllBreakpointRemoved(count)
                }
            }
        }
        codeEditor.setBreakpointChangeListener(breakpointChangeListener)
    }

    override fun setTheme(theme: Theme) {
        themeKey = theme.name
        codeEditor.setTheme(theme)
    }

    override fun setTextSizeSp(size: Float) {
        codeEditor.codeEditText.textSize = size
        codeEditor.lastTextSize = size.toInt()
    }

    override fun getTextSizeSp(): Float {
        return pxToSp(codeEditor.codeEditText.textSize)
    }

    override fun notifyPinchToZoomStrategyChanged(newKey: String?) {
        codeEditor.notifyPinchToZoomStrategyChanged(newKey)
    }

    override fun notifyDisplayPreferencesChanged() {
        applyDisplayPreferences()
    }

    private fun applyDisplayPreferences() {
        codeEditor.setWordWrapEnabled(EditorDisplayPreferences.isWordWrapEnabled())
        codeEditor.setGutterDisplayOptions(
            showBreakpointMarkers = EditorDisplayPreferences.isBreakpointMarkersEnabled(),
            showLineNumbers = EditorDisplayPreferences.isLineNumbersEnabled(),
            showFoldMarkers = EditorDisplayPreferences.isFoldMarkersEnabled(),
            gutterWidthMode = EditorDisplayPreferences.gutterWidthMode(),
        )
    }

    override fun addCursorChangeCallback(listener: EditorCursorChangeListener) {
        if (cursorCallbacks.containsKey(listener)) return
        val nativeCallback = object : CodeEditor.CursorChangeCallback {
            override fun onCursorChange(line: String, cursor: Int) {
                listener.onCursorChange(line, cursor)
            }
        }
        cursorCallbacks[listener] = nativeCallback
        codeEditor.addCursorChangeCallback(nativeCallback)
    }

    override fun removeCursorChangeCallback(listener: EditorCursorChangeListener) {
        val nativeCallback = cursorCallbacks.remove(listener) ?: return
        codeEditor.removeCursorChangeCallback(nativeCallback)
    }

    override fun addTextChangeListener(listener: EditorTextChangeListener) {
        if (textChangeCallbacks.containsKey(listener)) return
        val watcher = SimpleTextWatcher(SimpleTextWatcher.AfterTextChangedListener { s ->
            listener.onTextChanged(s)
        })
        textChangeCallbacks[listener] = watcher
        codeEditor.codeEditText.addTextChangedListener(watcher)
    }

    override fun removeTextChangeListener(listener: EditorTextChangeListener) {
        val watcher = textChangeCallbacks.remove(listener) ?: return
        codeEditor.codeEditText.removeTextChangedListener(watcher)
    }

    override fun createAutoCompletion(context: Context): AutoCompletion {
        return AutoCompletion(context, codeEditor.codeEditText)
    }

    override fun requestEditorFocus() {
        codeEditor.codeEditText.requestFocus()
    }

    override fun ensureCursorVisibleIfPossible() {
        val editText = codeEditor.codeEditText
        val textLen = editText.text?.length ?: 0
        if (textLen <= 0) return

        val layout = editText.layout ?: return
        val sel = editText.selectionStart.coerceIn(0, textLen)

        val line = LayoutHelper.getLineOfChar(layout, sel).coerceAtLeast(0)
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)
        val x = runCatching { layout.getPrimaryHorizontal(sel).toInt() }.getOrElse { 0 }
        val left = (x - editText.paddingLeft - 16).coerceAtLeast(0)

        tmpCursorRect.set(left, lineTop, left + 32, lineBottom)
        editText.requestRectangleOnScreen(tmpCursorRect, true)
    }

    override fun refreshHighlightTokensIfAllowed() {
        codeEditor.refreshHighlightTokensIfAllowed()
    }

    override fun setProgress(progress: Boolean, interactive: Boolean) {
        codeEditor.setProgress(progress, interactive)
    }

    override fun beginLargeTextLoading() {
        val editText = codeEditor.codeEditText
        editText.setLoadingText(true)
        codeEditor.setRedoUndoEnabled(false)
        editText.setLoadingGutterDigits(3)
        editText.setText("")
        editText.setSelection(0)
    }

    override fun appendTextChunk(chunk: CharSequence) {
        val editText = codeEditor.codeEditText
        editText.beginBatchEdit()
        try {
            editText.text?.append(chunk)
        } finally {
            editText.endBatchEdit()
        }
    }

    override fun endLargeTextLoading(markClean: Boolean) {
        codeEditor.codeEditText.setLoadingText(false)
        codeEditor.setRedoUndoEnabled(true)
        if (markClean) {
            codeEditor.markUndoRedoBaselineAsUnchanged()
        }
    }

    override fun cancelLargeTextLoading() {
        codeEditor.codeEditText.setLoadingText(false)
    }

    override fun setLoadingText(loading: Boolean) {
        codeEditor.codeEditText.setLoadingText(loading)
    }

    override fun createStateSnapshot(): EditorStateSnapshot {
        val currentSelection = selection
        return EditorStateSnapshot(
            text = textSnapshot,
            dirty = isTextChanged,
            cursorLine = currentSelection.startLine,
            cursorColumn = currentSelection.startColumn,
            selection = currentSelection,
            firstVisibleLine = firstVisibleLine(),
            firstVisibleColumn = codeEditor.scrollX,
            breakpoints = breakpointsSnapshot,
            debuggingLine = codeEditor.codeEditText.debuggingLine,
            readOnly = readOnly,
            fontSizeSp = getTextSizeSp(),
            themeKey = themeKey,
            searchQuery = searchQuery,
            searchOptions = searchOptions,
        )
    }

    override fun restoreStateSnapshot(snapshot: EditorStateSnapshot) {
        codeEditor.setInitialText(snapshot.text)
        if (snapshot.dirty) {
            codeEditor.markTextAsChanged()
        } else {
            codeEditor.markTextAsSaved()
        }
        setReadOnly(snapshot.readOnly)
        setTextSizeSp(snapshot.fontSizeSp)
        setDebuggingLine(snapshot.debuggingLine)
        restoreBreakpoints(snapshot.breakpoints)

        val targetSelection = snapshot.selection
        if (targetSelection != null) {
            val start = targetSelection.startOffset.coerceIn(0, textSnapshot.length)
            val end = targetSelection.endOffset.coerceIn(0, textSnapshot.length)
            codeEditor.codeEditText.setSelection(start, end)
        } else {
            jumpTo(snapshot.cursorLine, snapshot.cursorColumn)
        }

        searchQuery = snapshot.searchQuery
        searchOptions = snapshot.searchOptions
        restoreScroll(snapshot)
    }

    override fun destroy() {
        codeEditor.destroy()
    }

    private fun currentSearchResult(query: String?): EditorSearchResult {
        val index = codeEditor.getFoundIndex()
        return EditorSearchResult(found = index >= 0, foundIndex = index, query = query)
    }

    private fun canMutateText(): Boolean = !readOnly || isLoadingText

    private fun createSelection(start: Int, end: Int): EditorSelection {
        val startPosition = positionForOffset(start)
        val endPosition = positionForOffset(end)
        return EditorSelection(
            startOffset = start,
            endOffset = end,
            startLine = startPosition.first,
            startColumn = startPosition.second,
            endLine = endPosition.first,
            endColumn = endPosition.second,
        )
    }

    private fun positionForOffset(offset: Int): Pair<Int, Int> {
        val text = textSnapshot
        val safeOffset = offset.coerceIn(0, text.length)
        val editText = codeEditor.codeEditText
        return editText.getLogicalLineForOffset(safeOffset) to editText.getLogicalColumnForOffset(safeOffset)
    }

    private fun offsetForLineColumn(line: Int, column: Int): Int? {
        if (line < 0) return null
        val editText = codeEditor.codeEditText
        if (line !in 0 until editText.getLogicalLineCount()) return null
        val lineStart = editText.getLogicalLineStart(line)
        val lineEnd = editText.getLogicalLineEnd(line)
        return (lineStart + column.coerceAtLeast(0)).coerceAtMost(lineEnd)
    }

    private fun firstVisibleLine(): Int? {
        val layout = codeEditor.codeEditText.layout ?: return null
        return codeEditor.codeEditText.getLogicalLineForVisualLine(layout.getLineForVertical(codeEditor.scrollY))
    }

    private fun restoreScroll(snapshot: EditorStateSnapshot) {
        val firstVisibleLine = snapshot.firstVisibleLine ?: return
        codeEditor.codeEditText.post {
            val layout = codeEditor.codeEditText.layout ?: return@post
            val line = codeEditor.codeEditText
                .getVisualLineForLogicalLine(firstVisibleLine)
                .coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
            val scrollX = if (codeEditor.codeEditText.isWordWrapEnabled) {
                0
            } else {
                (snapshot.firstVisibleColumn ?: 0).coerceAtLeast(0)
            }
            codeEditor.scrollTo(scrollX, layout.getLineTop(line).coerceAtLeast(0))
        }
    }

    private fun restoreBreakpoints(breakpoints: List<EditorBreakpoint>) {
        val editText = codeEditor.codeEditText
        editText.breakpoints.clear()
        breakpoints.forEach { breakpoint ->
            if (breakpoint.line >= 0) {
                editText.breakpoints[breakpoint.line] = CodeEditor.Breakpoint(breakpoint.line).also {
                    it.enabled = breakpoint.enabled
                }
            }
        }
        editText.invalidate()
    }
}
