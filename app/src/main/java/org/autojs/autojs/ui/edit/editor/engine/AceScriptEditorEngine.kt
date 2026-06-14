package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import android.graphics.Color
import android.view.View
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.ui.edit.editor.ace.AceCodeEditor
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.util.ClipboardUtils
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class AceScriptEditorEngine(
    private val context: Context,
    private val aceCodeEditor: AceCodeEditor = AceCodeEditor(context),
) : ScriptEditorEngine {

    private val cursorCallbacks = LinkedHashSet<EditorCursorChangeListener>()
    private val textChangeCallbacks = LinkedHashSet<EditorTextChangeListener>()

    private var breakpointChangeListener: EditorBreakpointChangeListener? = null
    private var readOnly = false
    private var redoUndoEnabled = true
    private var loadingText = false
    private var debuggingLine = -1
    private var fontSizeSp = DEFAULT_TEXT_SIZE_SP
    private var themeKey: String? = null
    private var searchQuery: String? = null
    private var searchOptions: EditorSearchOptions? = null
    private var replacementText = ""
    private var foundIndex = -1
    private var foundLength = 0

    override val view: View
        get() = aceCodeEditor

    override val inputView: View
        get() = aceCodeEditor.webView

    override val engineType: EditorEngineType
        get() = EditorEngineType.ACE

    override val isReady: Boolean
        get() = aceCodeEditor.isReady

    override var text: String
        get() = aceCodeEditor.textSnapshot
        set(value) {
            searchQuery = null
            foundIndex = -1
            foundLength = 0
            aceCodeEditor.setText(value)
        }

    override val textSnapshot: String
        get() = aceCodeEditor.textSnapshot

    override val isTextChanged: Boolean
        get() = aceCodeEditor.dirty

    override val canUndo: Boolean
        get() = redoUndoEnabled && aceCodeEditor.canUndo

    override val canRedo: Boolean
        get() = redoUndoEnabled && aceCodeEditor.canRedo

    override val lineCountSnapshot: Int
        get() = aceCodeEditor.lineCountSnapshot

    override val selection: EditorSelection
        get() = aceCodeEditor.selectionSnapshot.toEditorSelection()

    override val selectedTextSnapshot: String
        get() = aceCodeEditor.selectedTextSnapshot

    override val breakpoints: Map<Int, EditorBreakpoint>
        get() = breakpointsSnapshot.associateBy { it.line }

    override val breakpointsSnapshot: List<EditorBreakpoint>
        get() = aceCodeEditor.breakpointsSnapshot.map { EditorBreakpoint(line = it) }

    override val foundIndexSnapshot: Int
        get() = foundIndex

    override val isUserTouching: Boolean
        get() = aceCodeEditor.isUserTouching()

    override val isLoadingText: Boolean
        get() = loadingText

    override var onUserTouchInTextArea: (() -> Unit)?
        get() = aceCodeEditor.onUserTouchInTextArea
        set(value) {
            aceCodeEditor.onUserTouchInTextArea = value
        }

    init {
        aceCodeEditor.listener = createAceListener()
        aceCodeEditor.setTextSizeSp(fontSizeSp)
    }

    override fun setInitialText(text: String?) {
        this.text = text.orEmpty()
        aceCodeEditor.markClean()
    }

    override fun getTextForSaving(callback: (String) -> Unit) {
        aceCodeEditor.getText(callback)
    }

    override fun markTextAsSaved() {
        aceCodeEditor.markClean()
    }

    override fun insert(text: String?) {
        aceCodeEditor.insert(text.orEmpty())
    }

    override fun insert(line: Int, text: String?) {
        aceCodeEditor.insert(line, text.orEmpty())
    }

    override fun copyLine() {
        ClipboardUtils.setClip(context, aceCodeEditor.cursorLineText)
    }

    override fun deleteLine() {
        aceCodeEditor.deleteLine()
    }

    override fun clear() {
        aceCodeEditor.clear()
        resetSearchState()
    }

    override fun toggleComment() {
        aceCodeEditor.toggleComment()
    }

    override fun beautifyCode() {
        aceCodeEditor.beautify()
    }

    override fun undo() {
        if (redoUndoEnabled) {
            aceCodeEditor.undo()
        }
    }

    override fun redo() {
        if (redoUndoEnabled) {
            aceCodeEditor.redo()
        }
    }

    override fun setRedoUndoEnabled(enabled: Boolean) {
        redoUndoEnabled = enabled
    }

    override fun markUndoRedoBaselineAsUnchanged() {
        aceCodeEditor.markClean()
    }

    override fun jumpTo(line: Int, column: Int) {
        aceCodeEditor.jumpTo(line, column)
    }

    override fun jumpToStart() {
        aceCodeEditor.jumpToStart()
    }

    override fun jumpToEnd() {
        aceCodeEditor.jumpToEnd()
    }

    override fun jumpToLineStart() {
        aceCodeEditor.jumpToLineStart()
    }

    override fun jumpToLineEnd() {
        aceCodeEditor.jumpToLineEnd()
    }

    override fun jumpToNextLine() {
        aceCodeEditor.jumpToNextLine()
    }

    override fun jumpToPrevLine() {
        aceCodeEditor.jumpToPrevLine()
    }

    override fun moveCursor(deltaChars: Int) {
        val target = (selection.startOffset + deltaChars).coerceIn(0, textSnapshot.length)
        selectRange(target, target)
    }

    @Throws(EditorSearchSyntaxException::class)
    override fun find(query: String, options: EditorSearchOptions): EditorSearchResult {
        searchQuery = query
        searchOptions = options
        val start = selection.endOffset.coerceIn(0, textSnapshot.length)
        return findFrom(query, options, start, backwards = false)
    }

    override fun findNext(): EditorSearchResult {
        val query = searchQuery ?: return EditorSearchResult(found = false)
        val options = searchOptions ?: EditorSearchOptions()
        val start = when {
            foundIndex >= 0 -> (foundIndex + foundLength.coerceAtLeast(1)).coerceAtMost(textSnapshot.length)
            else -> selection.endOffset.coerceIn(0, textSnapshot.length)
        }
        return runCatching { findFrom(query, options, start, backwards = false) }
            .getOrElse { EditorSearchResult(found = false, query = query) }
    }

    override fun findPrev(): EditorSearchResult {
        val query = searchQuery ?: return EditorSearchResult(found = false)
        val options = searchOptions ?: EditorSearchOptions()
        val start = when {
            foundIndex >= 0 -> foundIndex.coerceAtLeast(0)
            else -> selection.startOffset.coerceIn(0, textSnapshot.length)
        }
        return runCatching { findFrom(query, options, start, backwards = true) }
            .getOrElse { EditorSearchResult(found = false, query = query) }
    }

    @Throws(EditorSearchSyntaxException::class)
    override fun replace(query: String, replacement: String, options: EditorSearchOptions): EditorSearchResult {
        replacementText = replacement
        aceCodeEditor.setReplacement(replacement)
        return find(query, options)
    }

    override fun replaceSelection(): EditorSearchResult {
        val currentSelection = selection
        if (!currentSelection.isCollapsed) {
            val start = currentSelection.startOffset
            val end = currentSelection.endOffset
            foundIndex = start
            foundLength = replacementText.length
            aceCodeEditor.replaceStoredSelection()
            aceCodeEditor.selectRange(start, start + replacementText.length)
        }
        return EditorSearchResult(found = foundIndex >= 0, foundIndex = foundIndex, query = searchQuery)
    }

    @Throws(EditorSearchSyntaxException::class)
    override fun replaceAll(query: String, replacement: String?, options: EditorSearchOptions): EditorSearchResult {
        validatePatternIfNeeded(query, options)
        val next = replaceAllInText(textSnapshot, query, replacement.orEmpty(), options)
        val changed = next != textSnapshot
        if (changed) {
            aceCodeEditor.setTextDirty(next)
            foundIndex = -1
            foundLength = 0
        }
        return EditorSearchResult(found = changed, foundIndex = foundIndex, query = query)
    }

    override fun resetSearchState() {
        searchQuery = null
        searchOptions = null
        replacementText = ""
        foundIndex = -1
        foundLength = 0
    }

    override fun resetFoundIndexForFindNextFromCursor() {
        foundIndex = (selection.endOffset - 1).coerceAtLeast(-1)
        foundLength = 0
    }

    override fun resetFoundIndexForFindPrevFromCursor() {
        foundIndex = selection.startOffset.coerceIn(0, textSnapshot.length)
        foundLength = 0
    }

    override fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        aceCodeEditor.setReadOnly(readOnly)
    }

    override fun setDebuggingLine(line: Int) {
        debuggingLine = line
        aceCodeEditor.setDebuggingLine(line)
    }

    override fun addOrRemoveBreakpointAtCurrentLine() {
        aceCodeEditor.addOrRemoveBreakpointAtCurrentLine()
    }

    override fun removeAllBreakpoints() {
        aceCodeEditor.removeAllBreakpoints()
    }

    override fun setBreakpointChangeListener(listener: EditorBreakpointChangeListener?) {
        breakpointChangeListener = listener
    }

    override fun setTheme(theme: Theme) {
        themeKey = theme.name
        aceCodeEditor.setTheme(theme.toAceTheme())
    }

    override fun setTextSizeSp(size: Float) {
        fontSizeSp = size.coerceIn(8f, 40f)
        aceCodeEditor.setTextSizeSp(fontSizeSp)
    }

    override fun getTextSizeSp(): Float = fontSizeSp

    override fun notifyPinchToZoomStrategyChanged(newKey: String?) {
        /* ACE pinch-to-zoom is handled by WebView for now. */
    }

    override fun addCursorChangeCallback(listener: EditorCursorChangeListener) {
        cursorCallbacks += listener
    }

    override fun removeCursorChangeCallback(listener: EditorCursorChangeListener) {
        cursorCallbacks -= listener
    }

    override fun addTextChangeListener(listener: EditorTextChangeListener) {
        textChangeCallbacks += listener
    }

    override fun removeTextChangeListener(listener: EditorTextChangeListener) {
        textChangeCallbacks -= listener
    }

    override fun createAutoCompletion(context: Context): AutoCompletion? = null

    override fun requestEditorFocus() {
        aceCodeEditor.requestEditorFocus()
    }

    override fun ensureCursorVisibleIfPossible() {
        aceCodeEditor.requestEditorFocus()
    }

    override fun refreshHighlightTokensIfAllowed() {
        /* ACE handles token refresh internally. */
    }

    override fun setProgress(progress: Boolean, interactive: Boolean) {
        /* The ACE MVP does not show an in-editor progress dialog. */
    }

    override fun beginLargeTextLoading() {
        loadingText = true
        setRedoUndoEnabled(false)
        text = ""
    }

    override fun appendTextChunk(chunk: CharSequence) {
        aceCodeEditor.insert(chunk.toString())
    }

    override fun endLargeTextLoading(markClean: Boolean) {
        loadingText = false
        setRedoUndoEnabled(true)
        if (markClean) {
            aceCodeEditor.markClean()
        }
    }

    override fun cancelLargeTextLoading() {
        loadingText = false
        setRedoUndoEnabled(true)
    }

    override fun setLoadingText(loading: Boolean) {
        loadingText = loading
    }

    override fun setLoadingGutterDigits(digits: Int) {
        /* ACE gutter width is managed by the renderer. */
    }

    override fun createStateSnapshot(): EditorStateSnapshot {
        val currentSelection = selection
        return EditorStateSnapshot(
            text = textSnapshot,
            dirty = isTextChanged,
            cursorLine = currentSelection.startLine,
            cursorColumn = currentSelection.startColumn,
            selection = currentSelection,
            firstVisibleLine = null,
            firstVisibleColumn = null,
            breakpoints = breakpointsSnapshot,
            debuggingLine = debuggingLine,
            readOnly = readOnly,
            fontSizeSp = fontSizeSp,
            themeKey = themeKey,
            searchQuery = searchQuery,
            searchOptions = searchOptions,
        )
    }

    override fun restoreStateSnapshot(snapshot: EditorStateSnapshot) {
        text = snapshot.text
        setReadOnly(snapshot.readOnly)
        setTextSizeSp(snapshot.fontSizeSp)
        setDebuggingLine(snapshot.debuggingLine)
        restoreBreakpoints(snapshot.breakpoints)
        snapshot.selection?.let {
            selectRange(it.startOffset, it.endOffset)
        } ?: jumpTo(snapshot.cursorLine, snapshot.cursorColumn)
        searchQuery = snapshot.searchQuery
        searchOptions = snapshot.searchOptions
    }

    override fun destroy() {
        aceCodeEditor.destroy()
    }

    private fun createAceListener(): AceCodeEditor.Listener {
        return object : AceCodeEditor.Listener {
            override fun onTextChanged(text: String, state: AceCodeEditor.AceEditorState) {
                textChangeCallbacks.forEach { it.onTextChanged(text) }
            }

            override fun onCursorChanged(lineText: String, line: Int, column: Int, state: AceCodeEditor.AceEditorState) {
                cursorCallbacks.forEach { it.onCursorChange(lineText, column) }
            }

            override fun onBreakpointChanged(line: Int, enabled: Boolean, state: AceCodeEditor.AceEditorState) {
                breakpointChangeListener?.onBreakpointChange(line, enabled)
            }
        }
    }

    @Throws(EditorSearchSyntaxException::class)
    private fun findFrom(query: String, options: EditorSearchOptions, start: Int, backwards: Boolean): EditorSearchResult {
        validatePatternIfNeeded(query, options)
        val match = findMatch(textSnapshot, query, options, start, backwards)
        return if (match != null) {
            foundIndex = match.start
            foundLength = match.end - match.start
            selectRange(match.start, match.end)
            EditorSearchResult(found = true, foundIndex = foundIndex, query = query)
        } else {
            foundIndex = -1
            foundLength = 0
            EditorSearchResult(found = false, foundIndex = -1, query = query)
        }
    }

    private fun selectRange(start: Int, end: Int) {
        aceCodeEditor.selectRange(start, end)
    }

    @Throws(EditorSearchSyntaxException::class)
    private fun validatePatternIfNeeded(query: String, options: EditorSearchOptions) {
        if (!options.usingRegex) return
        try {
            Pattern.compile(query, options.regexFlags())
        } catch (e: PatternSyntaxException) {
            throw EditorSearchSyntaxException(e)
        }
    }

    private fun findMatch(text: String, query: String, options: EditorSearchOptions, start: Int, backwards: Boolean): Match? {
        if (query.isEmpty()) return null
        return if (options.usingRegex) {
            findRegexMatch(text, query, options, start, backwards)
        } else {
            findPlainMatch(text, query, options, start, backwards)
        }
    }

    private fun findPlainMatch(text: String, query: String, options: EditorSearchOptions, start: Int, backwards: Boolean): Match? {
        val safeStart = start.coerceIn(0, text.length)
        if (!backwards) {
            findPlainForward(text, query, options, safeStart)?.let { return it }
            return findPlainForward(text, query, options, 0)
        }
        findPlainBackward(text, query, options, safeStart)?.let { return it }
        return findPlainBackward(text, query, options, text.length)
    }

    private fun findPlainForward(text: String, query: String, options: EditorSearchOptions, start: Int): Match? {
        var index = text.indexOf(query, start, ignoreCase = !options.caseSensitive)
        while (index >= 0) {
            val end = index + query.length
            if (!options.wholeWord || text.isWholeWordRange(index, end)) {
                return Match(index, end)
            }
            index = text.indexOf(query, end.coerceAtMost(text.length), ignoreCase = !options.caseSensitive)
        }
        return null
    }

    private fun findPlainBackward(text: String, query: String, options: EditorSearchOptions, start: Int): Match? {
        var index = text.lastIndexOf(query, (start - 1).coerceAtLeast(0), ignoreCase = !options.caseSensitive)
        while (index >= 0) {
            val end = index + query.length
            if (!options.wholeWord || text.isWholeWordRange(index, end)) {
                return Match(index, end)
            }
            index = text.lastIndexOf(query, (index - 1).coerceAtLeast(0), ignoreCase = !options.caseSensitive)
        }
        return null
    }

    private fun findRegexMatch(text: String, query: String, options: EditorSearchOptions, start: Int, backwards: Boolean): Match? {
        val pattern = Pattern.compile(query, options.regexFlags())
        val safeStart = start.coerceIn(0, text.length)
        val matcher = pattern.matcher(text)
        var first: Match? = null
        var previous: Match? = null
        while (matcher.find()) {
            val match = Match(matcher.start(), matcher.end())
            if (options.wholeWord && !text.isWholeWordRange(match.start, match.end)) {
                continue
            }
            if (first == null) first = match
            if (!backwards && match.start >= safeStart) return match
            if (backwards && match.end <= safeStart) previous = match
        }
        return if (backwards) previous ?: lastRegexMatch(text, pattern, options) else first
    }

    private fun lastRegexMatch(text: String, pattern: Pattern, options: EditorSearchOptions): Match? {
        val matcher = pattern.matcher(text)
        var last: Match? = null
        while (matcher.find()) {
            val match = Match(matcher.start(), matcher.end())
            if (!options.wholeWord || text.isWholeWordRange(match.start, match.end)) {
                last = match
            }
        }
        return last
    }

    @Throws(EditorSearchSyntaxException::class)
    private fun replaceAllInText(text: String, query: String, replacement: String, options: EditorSearchOptions): String {
        if (query.isEmpty()) return text
        if (options.usingRegex) {
            return try {
                Pattern.compile(query, options.regexFlags()).matcher(text).replaceAll(replacement)
            } catch (e: PatternSyntaxException) {
                throw EditorSearchSyntaxException(e)
            }
        }
        return text.replace(query, replacement, ignoreCase = !options.caseSensitive)
    }

    private fun restoreBreakpoints(breakpoints: List<EditorBreakpoint>) {
        aceCodeEditor.removeAllBreakpoints()
        breakpoints.forEach { aceCodeEditor.setBreakpoint(it.line, it.enabled) }
    }

    private fun AceCodeEditor.AceSelection.toEditorSelection(): EditorSelection {
        return EditorSelection(
            startOffset = startOffset,
            endOffset = endOffset,
            startLine = startLine,
            startColumn = startColumn,
            endLine = endLine,
            endColumn = endColumn,
        )
    }

    private fun EditorSearchOptions.regexFlags(): Int {
        return if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
    }

    private fun String.isWholeWordRange(start: Int, end: Int): Boolean {
        val beforeOk = start <= 0 || !this[start - 1].isWordPart()
        val afterOk = end >= length || !this[end].isWordPart()
        return beforeOk && afterOk
    }

    private fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '_'

    private fun Theme.toAceTheme(): String {
        return if (isDarkBackground(backgroundColor)) {
            "ace/theme/tomorrow_night"
        } else {
            "ace/theme/textmate"
        }
    }

    private fun isDarkBackground(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance < 0.5
    }

    private data class Match(
        val start: Int,
        val end: Int,
    )

    companion object {
        private const val DEFAULT_TEXT_SIZE_SP = 14f
    }
}
