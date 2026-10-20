package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import org.autojs.autojs.core.plugin.editor.AceEditorPluginHost
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorProjectSnapshotProvider
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnostics
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnosticsSnapshot
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailure
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailureType
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.editor.api.EditorPluginBooleanCallback
import org.autojs.plugin.editor.api.EditorPluginBreakpoint
import org.autojs.plugin.editor.api.EditorPluginCallback
import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeAction
import org.autojs.plugin.editor.api.EditorPluginDiagnostics
import org.autojs.plugin.editor.api.EditorPluginDefinitionTarget
import org.autojs.plugin.editor.api.EditorPluginFailure
import org.autojs.plugin.editor.api.EditorPluginHealthState
import org.autojs.plugin.editor.api.EditorPluginHistoryDirection
import org.autojs.plugin.editor.api.EditorPluginProjectRenameCallback
import org.autojs.plugin.editor.api.EditorPluginProjectRenameRequest
import org.autojs.plugin.editor.api.EditorPluginProjectRenameResult
import org.autojs.plugin.editor.api.EditorPluginSearchOptions
import org.autojs.plugin.editor.api.EditorPluginSearchResult
import org.autojs.plugin.editor.api.EditorPluginSearchSyntaxException
import org.autojs.plugin.editor.api.EditorPluginSelection
import org.autojs.plugin.editor.api.EditorPluginSelectionAction
import org.autojs.plugin.editor.api.EditorPluginSession
import org.autojs.plugin.editor.api.EditorPluginSessionConfig
import org.autojs.plugin.editor.api.EditorPluginSnapshot
import org.autojs.plugin.editor.api.EditorPluginState
import org.autojs.plugin.editor.api.EditorPluginTextCallback
import org.autojs.plugin.editor.api.EditorPluginTheme
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** Host-side adapter for the separately installed Ace editor plugin. */
class AceScriptEditorEngine private constructor(
    private val session: EditorPluginSession,
    private val releaseSession: () -> Unit,
    private val callbackRelay: CallbackRelay,
    private val pluginContractVersion: Int = 0,
    private val persistTextSize: (Int) -> Unit = { EditorKeys.EDITOR_TEXT_SIZE.set(it) },
) : ScriptEditorEngine {

    // Keep the already-created views on the host side. Once the package disappears, even a
    // seemingly harmless interface property access must not be required to detach the editor.
    private val editorView = session.view
    private val editorInputView = session.inputView
    private val cursorCallbacks = LinkedHashSet<EditorCursorChangeListener>()
    private val textChangeCallbacks = LinkedHashSet<EditorTextChangeListener>()
    private var breakpointChangeListener: EditorBreakpointChangeListener? = null
    private var historyOperationListener: EditorHistoryOperationListener? = null
    private var suppressProgrammaticTextChange = false
    @Volatile
    private var destroyed = false
    private var pendingFatalFailure: AceFailure? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val textCacheLock = Any()
    private val cachedTextRevision = AtomicLong(0L)
    private val pendingTextSnapshotTimeouts = CopyOnWriteArraySet<Runnable>()

    @Volatile
    private var cachedTextSnapshot = ""

    @Volatile
    private var cachedPluginState: EditorPluginState? = null

    @Volatile
    private var cachedHostSnapshot: EditorStateSnapshot? = null

    @Volatile
    private var cachedDirty = false

    @Volatile
    private var cachedReadOnly = false

    @Volatile
    private var cachedTextSizeSp = 14f

    @Volatile
    private var cachedDebuggingLine = -1

    @Volatile
    private var cachedThemeKey: String? = null

    @Volatile
    private var cachedSearchQuery: String? = null

    @Volatile
    private var cachedSearchOptions: EditorSearchOptions? = null

    @Volatile
    private var cachedDiagnosticsSnapshot: AceDiagnosticsSnapshot? = null

    var onFatalFailure: ((AceFailure) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                pendingFatalFailure?.also {
                    pendingFatalFailure = null
                    value(it)
                }
            }
        }

    var onAceStateChanged: (() -> Unit)? = null
    var onSelectionAction: ((SelectionAction) -> Unit)? = null
    var onDefinitionNavigationRequested: ((EditorPluginDefinitionTarget) -> Unit)? = null
    var onCurrentDocumentCodeActionRequested:
        ((EditorPluginCurrentDocumentCodeAction, EditorPluginBooleanCallback) -> Unit)? = null
    var onProjectRenameRequested:
        ((EditorPluginProjectRenameRequest, EditorPluginProjectRenameCallback) -> Unit)? = null
    override var onUserTouchInTextArea: (() -> Unit)? = null

    override val view: View
        get() = editorView

    override val inputView: View
        get() = editorInputView

    override val engineType: EditorEngineType
        get() = EditorEngineType.ACE

    override val isReady: Boolean
        get() = runCatching { session.isReady }.getOrDefault(false)

    override var text: String
        get() {
            val requestedRevision = cachedTextRevision.get()
            return runCatching { session.text }
                .map { text -> updateCachedTextIfRevisionUnchanged(requestedRevision, text) }
                .getOrElse { cachedTextSnapshot }
        }
        set(value) {
            updateCachedText(value)
            suppressProgrammaticTextChange = true
            session.text = value
        }

    override val textSnapshot: String
        get() {
            val requestedRevision = cachedTextRevision.get()
            return runCatching { session.textSnapshot }
                .map { text -> updateCachedTextIfRevisionUnchanged(requestedRevision, text) }
                .getOrElse { cachedTextSnapshot }
        }

    override val isTextChanged: Boolean
        get() = runCatching { session.isTextChanged }.getOrElse { cachedDirty }

    override val canUndo: Boolean
        get() = runCatching { session.canUndo }.getOrElse { cachedPluginState?.canUndo == true }

    override val canRedo: Boolean
        get() = runCatching { session.canRedo }.getOrElse { cachedPluginState?.canRedo == true }

    override val lineCountSnapshot: Int
        get() = runCatching { session.lineCountSnapshot }
            .getOrElse { cachedPluginState?.lineCount ?: cachedTextSnapshot.lineSequence().count() }

    override val selection: EditorSelection
        get() = runCatching { AceEditorPluginMappings.toHost(session.selection) }
            .getOrElse {
                cachedPluginState?.selection?.let(AceEditorPluginMappings::toHost) ?: EditorSelection.EMPTY
            }

    override val selectedTextSnapshot: String
        get() = runCatching { session.selectedTextSnapshot }
            .getOrElse { cachedPluginState?.selectedText.orEmpty() }

    override val breakpoints: Map<Int, EditorBreakpoint>
        get() = breakpointsSnapshot.associateBy(EditorBreakpoint::line)

    override val breakpointsSnapshot: List<EditorBreakpoint>
        get() = runCatching { session.breakpointsSnapshot.map(AceEditorPluginMappings::toHost) }
            .getOrElse {
                cachedPluginState?.breakpoints.orEmpty().map { EditorBreakpoint(line = it) }
            }

    override val foundIndexSnapshot: Int
        get() = runCatching { session.foundIndexSnapshot }.getOrDefault(-1)

    override val isUserTouching: Boolean
        get() = runCatching { session.isUserTouching }.getOrDefault(false)

    override val isLoadingText: Boolean
        get() = runCatching { session.isLoadingText }.getOrDefault(false)

    override val isReadOnly: Boolean
        get() = runCatching { session.isReadOnly }.getOrElse { cachedReadOnly }

    private val pluginCallback = object : EditorPluginCallback {
        override fun onReady(state: EditorPluginState) {
            cacheState(state)
            onAceStateChanged?.invoke()
        }

        override fun onStateChanged(state: EditorPluginState) {
            cacheState(state)
            onAceStateChanged?.invoke()
        }

        override fun onTextChanged(text: String, state: EditorPluginState) {
            cacheState(state)
            // The dedicated text argument is the authoritative value for this callback. Some
            // plugin state payloads intentionally omit text or may describe the preceding frame.
            updateCachedText(text)
            if (suppressProgrammaticTextChange) {
                suppressProgrammaticTextChange = false
            } else {
                textChangeCallbacks.toList().forEach { it.onTextChanged(text) }
            }
        }

        override fun onCursorChanged(lineText: String, line: Int, column: Int, state: EditorPluginState) {
            cacheState(state)
            cursorCallbacks.toList().forEach { it.onCursorChange(lineText, line, column) }
        }

        override fun onBreakpointChanged(line: Int, enabled: Boolean, state: EditorPluginState) {
            cacheState(state)
            breakpointChangeListener?.onBreakpointChange(line, enabled)
        }

        override fun onHistoryOperation(token: String, direction: EditorPluginHistoryDirection) {
            historyOperationListener?.onHistoryOperation(
                token,
                when (direction) {
                    EditorPluginHistoryDirection.UNDO -> EditorHistoryDirection.UNDO
                    EditorPluginHistoryDirection.REDO -> EditorHistoryDirection.REDO
                },
            )
        }

        override fun onSelectionAction(action: EditorPluginSelectionAction) {
            onSelectionAction?.invoke(AceEditorPluginMappings.toHost(action))
        }

        override fun onDefinitionNavigationRequested(target: EditorPluginDefinitionTarget) {
            this@AceScriptEditorEngine.onDefinitionNavigationRequested?.invoke(target)
        }

        override fun onCurrentDocumentCodeActionRequested(
            action: EditorPluginCurrentDocumentCodeAction,
            onComplete: EditorPluginBooleanCallback,
        ) {
            val handler = this@AceScriptEditorEngine.onCurrentDocumentCodeActionRequested
            if (handler == null) {
                onComplete.onComplete(false)
                return
            }
            runCatching { handler(action, onComplete) }
                .onFailure { onComplete.onComplete(false) }
        }

        override fun onProjectRenameRequested(
            request: EditorPluginProjectRenameRequest,
            onComplete: EditorPluginProjectRenameCallback,
        ) {
            val handler = this@AceScriptEditorEngine.onProjectRenameRequested
            if (handler == null) {
                onComplete.onComplete(EditorPluginProjectRenameResult.REJECTED)
                return
            }
            runCatching { handler(request, onComplete) }
                .onFailure { onComplete.onComplete(EditorPluginProjectRenameResult.FAILED) }
        }

        override fun onTextSizeSpChangedByGesture(size: Float, persist: Boolean) {
            if (persist) {
                persistTextSize(size.roundToInt())
            }
        }

        override fun onUserTouchInTextArea() {
            this@AceScriptEditorEngine.onUserTouchInTextArea?.invoke()
        }

        override fun onFailure(failure: EditorPluginFailure) {
            if (!failure.fatal) {
                return
            }
            val mapped = AceEditorPluginMappings.toHost(failure)
            onFatalFailure?.invoke(mapped) ?: run { pendingFatalFailure = mapped }
        }
    }

    init {
        callbackRelay.attach(pluginCallback)
    }

    fun createAceDiagnosticsSnapshot(
        fileSizeBytes: Long? = null,
        largeFilePolicy: String = "auto",
    ): AceDiagnosticsSnapshot {
        return runCatching {
            session.createDiagnosticsSnapshot(fileSizeBytes, largeFilePolicy)
                .toAceDiagnosticsSnapshot(fileSizeBytes, largeFilePolicy)
        }.onSuccess { snapshot ->
            cachedDiagnosticsSnapshot = snapshot
        }.getOrElse {
            cachedDiagnosticsSnapshot
                ?: AceDiagnostics.createNativeSnapshot(EditorEngineType.ACE, fileSizeBytes, largeFilePolicy)
        }
    }

    fun setAceEditorFont(fontId: String) {
        session.setEditorFont(fontId)
    }

    fun refreshState() {
        session.refreshState()
    }

    fun insertShortcutText(text: String?) {
        session.insertShortcutText(text)
    }

    fun insertTab() {
        session.insertTab()
    }

    fun acceptCompletionOrInsertTab() {
        session.acceptCompletionOrInsertTab()
    }

    fun moveCompletionSelectionOrCursor(delta: Int) {
        session.moveCompletionSelectionOrCursor(delta)
    }

    fun isCompletionPopupOpen(): Boolean = session.completionPopupOpen

    fun handleEscapeKey() {
        session.handleEscapeKey()
    }

    /**
     * Whether the installed plugin implements editor contract 6 or newer, which added
     * [triggerSmartHint]. Older plugins are still loaded, so callers must keep a fallback.
     */
    val supportsSmartHint: Boolean
        get() = pluginContractVersion >= SMART_HINT_CONTRACT_VERSION

    fun triggerSmartHint() {
        if (!supportsSmartHint) {
            return
        }
        session.triggerSmartHint()
    }

    fun handleSelectionBackIfNeeded(): Boolean {
        if (selectedTextSnapshot.isEmpty()) {
            return false
        }
        session.handleEscapeKey()
        return true
    }

    fun refreshLsp() {
        session.refreshLsp()
    }

    fun selectAll() {
        session.selectAll()
    }

    override fun setInitialText(text: String?) {
        updateCachedText(text.orEmpty())
        cachedDirty = false
        suppressProgrammaticTextChange = true
        session.setInitialText(text)
    }

    override fun getTextForSaving(callback: (String) -> Unit) {
        if (destroyed) {
            callback(cachedTextSnapshot)
            return
        }
        val delivered = AtomicBoolean(false)
        val requestedRevision = cachedTextRevision.get()
        lateinit var timeout: Runnable
        timeout = Runnable {
            if (destroyed) {
                delivered.set(true)
                pendingTextSnapshotTimeouts -= timeout
                return@Runnable
            }
            if (delivered.compareAndSet(false, true)) {
                pendingTextSnapshotTimeouts -= timeout
                callback(cachedTextSnapshot)
            }
        }
        pendingTextSnapshotTimeouts += timeout
        mainHandler.postDelayed(timeout, TEXT_SNAPSHOT_TIMEOUT_MS)
        runCatching {
            session.getTextForSaving(EditorPluginTextCallback { text ->
                if (destroyed) {
                    delivered.set(true)
                    pendingTextSnapshotTimeouts -= timeout
                    mainHandler.removeCallbacks(timeout)
                    return@EditorPluginTextCallback
                }
                if (delivered.compareAndSet(false, true)) {
                    pendingTextSnapshotTimeouts -= timeout
                    mainHandler.removeCallbacks(timeout)
                    callback(updateCachedTextIfRevisionUnchanged(requestedRevision, text))
                }
            })
        }.onFailure {
            pendingTextSnapshotTimeouts -= timeout
            mainHandler.removeCallbacks(timeout)
            if (delivered.compareAndSet(false, true)) {
                callback(cachedTextSnapshot)
            }
        }
    }

    override fun markTextAsSaved() {
        cachedDirty = false
        session.markTextAsSaved()
    }

    override fun markTextAsChanged() {
        cachedDirty = true
        session.markTextAsChanged()
    }

    override fun replaceAllTextUndoably(text: String, token: String, onComplete: ((Boolean) -> Unit)?) {
        session.replaceAllTextUndoably(text, token, onComplete?.let { EditorPluginBooleanCallback(it) })
    }

    override fun addUndoableHistoryMarker(token: String, onComplete: ((Boolean) -> Unit)?) {
        session.addUndoableHistoryMarker(token, onComplete?.let { EditorPluginBooleanCallback(it) })
    }

    override fun setHistoryOperationListener(listener: EditorHistoryOperationListener?) {
        historyOperationListener = listener
    }

    override fun insert(text: String?) = session.insert(text)

    override fun insert(line: Int, text: String?) = session.insertAtLine(line, text)

    override fun copyLine() = session.copyLine()

    override fun deleteLine() = session.deleteLine()

    override fun clear() = session.clear()

    override fun toggleComment() = session.toggleComment()

    override fun beautifyCode() = session.beautifyCode()

    override val supportsCodeFolding: Boolean
        get() = pluginContractVersion >= CODE_FOLDING_CONTRACT_VERSION

    override fun foldAll() {
        if (supportsCodeFolding) session.foldAll()
    }

    override fun unfoldAll() {
        if (supportsCodeFolding) session.unfoldAll()
    }

    override fun undo() = session.undo()

    override fun redo() = session.redo()

    override fun setRedoUndoEnabled(enabled: Boolean) = session.setRedoUndoEnabled(enabled)

    override fun markUndoRedoBaselineAsUnchanged() = session.markUndoRedoBaselineAsUnchanged()

    override fun jumpTo(line: Int, column: Int) = session.jumpTo(line, column)

    override fun jumpToStart() = session.jumpToStart()

    override fun selectRange(startOffset: Int, endOffset: Int) = session.selectRange(startOffset, endOffset)

    override fun jumpToEnd() = session.jumpToEnd()

    override fun jumpToLineStart() = session.jumpToLineStart()

    override fun jumpToLineEnd() = session.jumpToLineEnd()

    override fun jumpToNextLine() = session.jumpToNextLine()

    override fun jumpToPrevLine() = session.jumpToPrevLine()

    override fun moveCursor(deltaChars: Int) = session.moveCursor(deltaChars)

    override fun find(query: String, options: EditorSearchOptions): EditorSearchResult = mapSearchFailure {
        session.find(query, AceEditorPluginMappings.toPlugin(options))
    }

    override fun findNext(): EditorSearchResult = mapSearchFailure(session::findNext)

    override fun findPrev(): EditorSearchResult = mapSearchFailure(session::findPrev)

    override fun replace(query: String, replacement: String, options: EditorSearchOptions): EditorSearchResult =
        mapSearchFailure {
            session.replace(query, replacement, AceEditorPluginMappings.toPlugin(options))
        }

    override fun replaceSelection(): EditorSearchResult = mapSearchFailure(session::replaceSelection)

    override fun replaceAll(query: String, replacement: String?, options: EditorSearchOptions): EditorSearchResult =
        mapSearchFailure {
            session.replaceAll(query, replacement, AceEditorPluginMappings.toPlugin(options))
        }

    override fun resetSearchState() = session.resetSearchState()

    override fun resetFoundIndexForFindNextFromCursor() = session.resetFoundIndexForFindNextFromCursor()

    override fun resetFoundIndexForFindPrevFromCursor() = session.resetFoundIndexForFindPrevFromCursor()

    override fun setReadOnly(readOnly: Boolean) {
        cachedReadOnly = readOnly
        session.setReadOnly(readOnly)
    }

    override fun setDebuggingLine(line: Int) {
        cachedDebuggingLine = line
        session.setDebuggingLine(line)
    }

    override fun addOrRemoveBreakpointAtCurrentLine() = session.addOrRemoveBreakpointAtCurrentLine()

    override fun removeAllBreakpoints() = session.removeAllBreakpoints()

    override fun setBreakpointChangeListener(listener: EditorBreakpointChangeListener?) {
        breakpointChangeListener = listener
    }

    override fun setTheme(theme: Theme) {
        cachedThemeKey = theme.name
        session.setTheme(AceEditorPluginMappings.toPlugin(theme))
    }

    override fun setTextSizeSp(size: Float) {
        cachedTextSizeSp = size
        session.setTextSizeSp(size)
    }

    override fun getTextSizeSp(): Float = runCatching { session.getTextSizeSp() }
        .onSuccess { cachedTextSizeSp = it }
        .getOrElse { cachedTextSizeSp }

    override fun notifyPinchToZoomStrategyChanged(newKey: String?) =
        session.notifyPinchToZoomStrategyChanged(newKey)

    override fun notifyDisplayPreferencesChanged() = session.notifyDisplayPreferencesChanged()

    override fun setDocumentPath(path: String?) = session.setDocumentPath(path)

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

    override fun requestEditorFocus() = session.requestEditorFocus()

    override fun ensureCursorVisibleIfPossible() = session.ensureCursorVisible()

    override fun onImeInsetsChanged(visible: Boolean, bottomInset: Int) =
        session.onImeInsetsChanged(visible, bottomInset)

    override fun refreshHighlightTokensIfAllowed() = session.refreshHighlightTokensIfAllowed()

    override fun setProgress(progress: Boolean, interactive: Boolean) = session.setProgress(progress, interactive)

    override fun beginLargeTextLoading() = session.beginLargeTextLoading()

    override fun appendTextChunk(chunk: CharSequence) = session.appendTextChunk(chunk)

    override fun endLargeTextLoading(markClean: Boolean) = session.endLargeTextLoading(markClean)

    override fun cancelLargeTextLoading() = session.cancelLargeTextLoading()

    override fun setLoadingText(loading: Boolean) = session.setLoadingText(loading)

    override fun createStateSnapshot(): EditorStateSnapshot = runCatching {
        AceEditorPluginMappings.toHost(session.createStateSnapshot())
    }.onSuccess { snapshot ->
        cacheSnapshot(snapshot)
    }.getOrElse {
        createEmergencyStateSnapshot()
    }

    override fun restoreStateSnapshot(snapshot: EditorStateSnapshot) {
        cacheSnapshot(snapshot)
        suppressProgrammaticTextChange = true
        session.restoreStateSnapshot(AceEditorPluginMappings.toPlugin(snapshot))
    }

    override fun destroy() {
        if (destroyed) {
            return
        }
        destroyed = true
        pendingTextSnapshotTimeouts.forEach(mainHandler::removeCallbacks)
        pendingTextSnapshotTimeouts.clear()
        callbackRelay.detach()
        cursorCallbacks.clear()
        textChangeCallbacks.clear()
        breakpointChangeListener = null
        historyOperationListener = null
        onUserTouchInTextArea = null
        onFatalFailure = null
        onAceStateChanged = null
        onSelectionAction = null
        onDefinitionNavigationRequested = null
        onCurrentDocumentCodeActionRequested = null
        onProjectRenameRequested = null
        releaseSession()
    }

    private fun mapSearchFailure(operation: () -> EditorPluginSearchResult): EditorSearchResult {
        return try {
            AceEditorPluginMappings.toHost(operation())
        } catch (e: EditorPluginSearchSyntaxException) {
            throw EditorSearchSyntaxException(e)
        }
    }

    private fun cacheState(state: EditorPluginState) {
        cachedPluginState = state
        if (state.textIncluded) {
            updateCachedText(state.text)
        }
        cachedDirty = state.dirty
    }

    private fun cacheSnapshot(snapshot: EditorStateSnapshot) {
        cachedHostSnapshot = snapshot
        updateCachedText(snapshot.text)
        cachedDirty = snapshot.dirty
        cachedReadOnly = snapshot.readOnly
        cachedTextSizeSp = snapshot.fontSizeSp
        cachedDebuggingLine = snapshot.debuggingLine
        cachedThemeKey = snapshot.themeKey
        cachedSearchQuery = snapshot.searchQuery
        cachedSearchOptions = snapshot.searchOptions
    }

    private fun updateCachedText(text: String): String = synchronized(textCacheLock) {
        cachedTextSnapshot = text
        cachedTextRevision.incrementAndGet()
        text
    }

    private fun updateCachedTextIfRevisionUnchanged(
        expectedRevision: Long,
        text: String,
    ): String = synchronized(textCacheLock) {
        if (cachedTextRevision.get() != expectedRevision) {
            cachedTextSnapshot
        } else {
            cachedTextSnapshot = text
            cachedTextRevision.incrementAndGet()
            text
        }
    }

    private fun createEmergencyStateSnapshot(): EditorStateSnapshot {
        val state = cachedPluginState
        val prior = cachedHostSnapshot
        val selection = state?.selection?.let(AceEditorPluginMappings::toHost) ?: prior?.selection
        return EditorStateSnapshot(
            text = cachedTextSnapshot,
            dirty = cachedDirty || state?.dirty == true || prior?.dirty == true,
            cursorLine = state?.cursor?.line ?: prior?.cursorLine ?: selection?.endLine ?: 0,
            cursorColumn = state?.cursor?.column ?: prior?.cursorColumn ?: selection?.endColumn ?: 0,
            selection = selection,
            firstVisibleLine = state?.firstVisibleLine ?: prior?.firstVisibleLine,
            firstVisibleColumn = state?.firstVisibleColumn ?: prior?.firstVisibleColumn,
            breakpoints = state?.breakpoints
                ?.map { EditorBreakpoint(line = it) }
                ?: prior?.breakpoints.orEmpty(),
            debuggingLine = prior?.debuggingLine ?: cachedDebuggingLine,
            readOnly = prior?.readOnly ?: cachedReadOnly,
            fontSizeSp = prior?.fontSizeSp ?: cachedTextSizeSp,
            themeKey = prior?.themeKey ?: cachedThemeKey,
            searchQuery = prior?.searchQuery ?: cachedSearchQuery,
            searchOptions = prior?.searchOptions ?: cachedSearchOptions,
        )
    }

    enum class SelectionAction {
        Copy,
        Paste,
        SelectAll,
        DeleteLine,
        CopyLine,
    }

    companion object {
        private const val TEXT_SNAPSHOT_TIMEOUT_MS = 750L
        private const val SMART_HINT_CONTRACT_VERSION = 6
        private const val CODE_FOLDING_CONTRACT_VERSION = 7

        @JvmStatic
        fun create(context: Context): AceScriptEditorEngine {
            val applicationContext = context.applicationContext
            val callbackRelay = CallbackRelay()
            val loadedSession = AceEditorPluginHost.createSession(
                context = context,
                config = EditorPluginSessionConfig(
                    hostPackageName = applicationContext.packageName,
                    hostVersionName = BuildConfig.VERSION_NAME,
                    hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    storageDirectoryPath = File(applicationContext.noBackupFilesDir, "ace_fonts").absolutePath,
                    projectSnapshotProvider = TypeScriptEditorProjectSnapshotProvider,
                ),
                callback = callbackRelay,
            )
            return try {
                AceScriptEditorEngine(
                    session = loadedSession.session,
                    releaseSession = loadedSession::destroy,
                    callbackRelay = callbackRelay,
                    pluginContractVersion = loadedSession.plugin.candidate.contractVersion,
                )
            } catch (t: Throwable) {
                loadedSession.destroy()
                throw t
            }
        }
    }

    private class CallbackRelay : EditorPluginCallback {
        private val lock = Any()
        private val pending = ArrayDeque<(EditorPluginCallback) -> Unit>()
        private var delegate: EditorPluginCallback? = null

        fun attach(callback: EditorPluginCallback) {
            val queued = synchronized(lock) {
                delegate = callback
                pending.toList().also { pending.clear() }
            }
            queued.forEach { it(callback) }
        }

        fun detach() {
            synchronized(lock) {
                delegate = null
                pending.clear()
            }
        }

        private fun dispatch(event: (EditorPluginCallback) -> Unit) {
            val target = synchronized(lock) {
                delegate ?: run {
                    pending.addLast(event)
                    null
                }
            }
            target?.let(event)
        }

        override fun onReady(state: EditorPluginState) = dispatch { it.onReady(state) }
        override fun onStateChanged(state: EditorPluginState) = dispatch { it.onStateChanged(state) }
        override fun onTextChanged(text: String, state: EditorPluginState) =
            dispatch { it.onTextChanged(text, state) }

        override fun onCursorChanged(lineText: String, line: Int, column: Int, state: EditorPluginState) =
            dispatch { it.onCursorChanged(lineText, line, column, state) }

        override fun onBreakpointChanged(line: Int, enabled: Boolean, state: EditorPluginState) =
            dispatch { it.onBreakpointChanged(line, enabled, state) }

        override fun onHistoryOperation(token: String, direction: EditorPluginHistoryDirection) =
            dispatch { it.onHistoryOperation(token, direction) }

        override fun onSelectionAction(action: EditorPluginSelectionAction) =
            dispatch { it.onSelectionAction(action) }

        override fun onDefinitionNavigationRequested(target: EditorPluginDefinitionTarget) =
            dispatch { it.onDefinitionNavigationRequested(target) }

        override fun onCurrentDocumentCodeActionRequested(
            action: EditorPluginCurrentDocumentCodeAction,
            onComplete: EditorPluginBooleanCallback,
        ) = dispatch { it.onCurrentDocumentCodeActionRequested(action, onComplete) }

        override fun onProjectRenameRequested(
            request: EditorPluginProjectRenameRequest,
            onComplete: EditorPluginProjectRenameCallback,
        ) = dispatch { it.onProjectRenameRequested(request, onComplete) }

        override fun onTextSizeSpChangedByGesture(size: Float, persist: Boolean) =
            dispatch { it.onTextSizeSpChangedByGesture(size, persist) }

        override fun onUserTouchInTextArea() = dispatch(EditorPluginCallback::onUserTouchInTextArea)
        override fun onFailure(failure: EditorPluginFailure) = dispatch { it.onFailure(failure) }
        override fun onEvent(name: String, payload: Bundle?) = dispatch { it.onEvent(name, payload) }
        override fun onError(message: String, detail: String?) = dispatch { it.onError(message, detail) }
    }
}

internal object AceEditorPluginMappings {
    fun toHost(selection: EditorPluginSelection): EditorSelection = EditorSelection(
        startOffset = selection.startOffset,
        endOffset = selection.endOffset,
        startLine = selection.startLine,
        startColumn = selection.startColumn,
        endLine = selection.endLine,
        endColumn = selection.endColumn,
    )

    fun toPlugin(selection: EditorSelection): EditorPluginSelection = EditorPluginSelection(
        startOffset = selection.startOffset,
        endOffset = selection.endOffset,
        startLine = selection.startLine,
        startColumn = selection.startColumn,
        endLine = selection.endLine,
        endColumn = selection.endColumn,
    )

    fun toHost(breakpoint: EditorPluginBreakpoint): EditorBreakpoint =
        EditorBreakpoint(line = breakpoint.line, enabled = breakpoint.enabled)

    fun toPlugin(breakpoint: EditorBreakpoint): EditorPluginBreakpoint =
        EditorPluginBreakpoint(line = breakpoint.line, enabled = breakpoint.enabled)

    fun toPlugin(options: EditorSearchOptions): EditorPluginSearchOptions = EditorPluginSearchOptions(
        usingRegex = options.usingRegex,
        caseSensitive = options.caseSensitive,
        wholeWord = options.wholeWord,
    )

    fun toHost(options: EditorPluginSearchOptions): EditorSearchOptions = EditorSearchOptions(
        usingRegex = options.usingRegex,
        caseSensitive = options.caseSensitive,
        wholeWord = options.wholeWord,
    )

    fun toHost(result: EditorPluginSearchResult): EditorSearchResult = EditorSearchResult(
        found = result.found,
        foundIndex = result.foundIndex,
        query = result.query,
    )

    fun toPlugin(theme: Theme): EditorPluginTheme = EditorPluginTheme(
        id = theme.name,
        aceTheme = theme.aceTheme,
        isDark = theme.isDark,
        backgroundColor = theme.backgroundColor,
        foregroundColor = theme.foregroundColor,
    )

    fun toHost(snapshot: EditorPluginSnapshot): EditorStateSnapshot = EditorStateSnapshot(
        text = snapshot.text,
        dirty = snapshot.dirty,
        cursorLine = snapshot.cursorLine,
        cursorColumn = snapshot.cursorColumn,
        selection = snapshot.selection?.let(::toHost),
        firstVisibleLine = snapshot.firstVisibleLine,
        firstVisibleColumn = snapshot.firstVisibleColumn,
        breakpoints = snapshot.breakpoints.map(::toHost),
        debuggingLine = snapshot.debuggingLine,
        readOnly = snapshot.readOnly,
        fontSizeSp = snapshot.fontSizeSp,
        themeKey = snapshot.themeId,
        searchQuery = snapshot.searchQuery,
        searchOptions = snapshot.searchOptions?.let(::toHost),
    )

    fun toPlugin(snapshot: EditorStateSnapshot): EditorPluginSnapshot = EditorPluginSnapshot(
        text = snapshot.text,
        dirty = snapshot.dirty,
        cursorLine = snapshot.cursorLine,
        cursorColumn = snapshot.cursorColumn,
        selection = snapshot.selection?.let(::toPlugin),
        firstVisibleLine = snapshot.firstVisibleLine,
        firstVisibleColumn = snapshot.firstVisibleColumn,
        breakpoints = snapshot.breakpoints.map(::toPlugin),
        debuggingLine = snapshot.debuggingLine,
        readOnly = snapshot.readOnly,
        fontSizeSp = snapshot.fontSizeSp,
        themeId = snapshot.themeKey,
        searchQuery = snapshot.searchQuery,
        searchOptions = snapshot.searchOptions?.let(::toPlugin),
    )

    fun toHost(action: EditorPluginSelectionAction): AceScriptEditorEngine.SelectionAction = when (action) {
        EditorPluginSelectionAction.COPY -> AceScriptEditorEngine.SelectionAction.Copy
        EditorPluginSelectionAction.PASTE -> AceScriptEditorEngine.SelectionAction.Paste
        EditorPluginSelectionAction.SELECT_ALL -> AceScriptEditorEngine.SelectionAction.SelectAll
        EditorPluginSelectionAction.DELETE_LINE -> AceScriptEditorEngine.SelectionAction.DeleteLine
        EditorPluginSelectionAction.COPY_LINE -> AceScriptEditorEngine.SelectionAction.CopyLine
    }

    fun toHost(failure: EditorPluginFailure): AceFailure = AceFailure(
        type = runCatching { AceFailureType.valueOf(failure.type.name) }.getOrDefault(AceFailureType.UNKNOWN),
        message = failure.message,
        detail = failure.detail,
        state = failure.state?.let {
            runCatching { AceHealthState.valueOf(it.name) }.getOrNull()
        },
        fatal = failure.fatal,
        timestampUptimeMillis = failure.timestampUptimeMillis.takeIf { it > 0L } ?: SystemClock.uptimeMillis(),
    )
}

private fun EditorPluginDiagnostics.toAceDiagnosticsSnapshot(
    fallbackFileSizeBytes: Long?,
    fallbackLargeFilePolicy: String,
): AceDiagnosticsSnapshot = values.run {
    AceDiagnosticsSnapshot(
        engineType = stringOrNull("engineType") ?: "ACE",
        aceHealthState = stringOrNull("aceHealthState"),
        lastFailureType = stringOrNull("lastFailureType"),
        lastFailureMessage = stringOrNull("lastFailureMessage"),
        lastFailureFatal = booleanOrNull("lastFailureFatal"),
        assetUrl = stringOrNull("assetUrl"),
        webViewVersion = stringOrNull("webViewVersion") ?: "unknown",
        androidVersion = stringOrNull("androidVersion") ?: Build.VERSION.RELEASE.orEmpty(),
        lspEnabled = booleanOrNull("lspEnabled") ?: false,
        lspState = stringOrNull("lspState") ?: "unknown",
        lspTransport = stringOrNull("lspTransport"),
        lspServerUri = stringOrNull("lspServerUri"),
        lspRootUri = stringOrNull("lspRootUri"),
        lspDocumentUri = stringOrNull("lspDocumentUri"),
        lspFallback = stringOrNull("lspFallback"),
        lspCompletionProvider = stringOrNull("lspCompletionProvider"),
        lspHoverProvider = stringOrNull("lspHoverProvider"),
        lspDiagnosticProvider = stringOrNull("lspDiagnosticProvider"),
        lspSignatureProvider = stringOrNull("lspSignatureProvider"),
        lspServerAvailable = booleanOrNull("lspServerAvailable") ?: false,
        lspStartSupported = booleanOrNull("lspStartSupported") ?: false,
        lspSessionRevision = longOrNull("lspSessionRevision") ?: 0L,
        lspRuntimeState = stringOrNull("lspRuntimeState"),
        aceFont = stringOrNull("aceFont"),
        fileSizeBytes = longOrNull("fileSizeBytes") ?: fallbackFileSizeBytes,
        largeFilePolicy = stringOrNull("largeFilePolicy") ?: fallbackLargeFilePolicy,
        textMirrorRevision = longOrNull("textMirrorRevision"),
        jsRevision = stringOrNull("jsRevision"),
        lastReadyAtUptimeMillis = longOrNull("lastReadyAtUptimeMillis"),
        lastFirstPaintAtUptimeMillis = longOrNull("lastFirstPaintAtUptimeMillis"),
        lastThemeAppliedAtUptimeMillis = longOrNull("lastThemeAppliedAtUptimeMillis"),
        lastPageLoadStartedAtUptimeMillis = longOrNull("lastPageLoadStartedAtUptimeMillis"),
        lastHostDocumentLoadingStartedAtUptimeMillis =
            longOrNull("lastHostDocumentLoadingStartedAtUptimeMillis"),
        lastHostDocumentReadyAtUptimeMillis = longOrNull("lastHostDocumentReadyAtUptimeMillis"),
        lastDeferredStaticIndexReadyAtUptimeMillis =
            longOrNull("lastDeferredStaticIndexReadyAtUptimeMillis"),
        lastStateAtUptimeMillis = longOrNull("lastStateAtUptimeMillis"),
        lastHeartbeatAtUptimeMillis = longOrNull("lastHeartbeatAtUptimeMillis"),
        lastHeartbeatSuspendedAtUptimeMillis = longOrNull("lastHeartbeatSuspendedAtUptimeMillis"),
        lastHeartbeatResumedAtUptimeMillis = longOrNull("lastHeartbeatResumedAtUptimeMillis"),
        heartbeatSuspendedReason = stringOrNull("heartbeatSuspendedReason"),
        lastImeState = stringOrNull("lastImeState"),
        lastImeHeight = intOrNull("lastImeHeight"),
        lastImeRequestAtUptimeMillis = longOrNull("lastImeRequestAtUptimeMillis"),
        lastShowSoftInputAtUptimeMillis = longOrNull("lastShowSoftInputAtUptimeMillis"),
        lastHideSoftInputAtUptimeMillis = longOrNull("lastHideSoftInputAtUptimeMillis"),
        lastImeTransitionStartedAtUptimeMillis = longOrNull("lastImeTransitionStartedAtUptimeMillis"),
        lastImeTransitionEndedAtUptimeMillis = longOrNull("lastImeTransitionEndedAtUptimeMillis"),
        lastImeIgnoredRequestReason = stringOrNull("lastImeIgnoredRequestReason"),
        lastResizeAtUptimeMillis = longOrNull("lastResizeAtUptimeMillis"),
        pendingAceResizeReason = stringOrNull("pendingAceResizeReason"),
        lastAceResizeScheduledReason = stringOrNull("lastAceResizeScheduledReason"),
        lastAceResizeScheduledAtUptimeMillis = longOrNull("lastAceResizeScheduledAtUptimeMillis"),
        lastAceResizeDispatchedReason = stringOrNull("lastAceResizeDispatchedReason"),
        lastAceResizeDispatchedAtUptimeMillis = longOrNull("lastAceResizeDispatchedAtUptimeMillis"),
        lastAceResizeCompletedReason = stringOrNull("lastAceResizeCompletedReason"),
        lastAceResizeCompletedAtUptimeMillis = longOrNull("lastAceResizeCompletedAtUptimeMillis"),
        lastActionModeAtUptimeMillis = longOrNull("lastActionModeAtUptimeMillis"),
        viewportPolicy = stringOrNull("viewportPolicy"),
        lastViewportPolicyEvent = stringOrNull("lastViewportPolicyEvent"),
        lastViewportPolicyEventAtUptimeMillis = longOrNull("lastViewportPolicyEventAtUptimeMillis"),
        lastViewportPolicyImeVisible = booleanOrNull("lastViewportPolicyImeVisible"),
        lastViewportPolicyBottomInset = intOrNull("lastViewportPolicyBottomInset"),
        lastHostImeVisible = booleanOrNull("lastHostImeVisible"),
        lastHostImeBottomInset = intOrNull("lastHostImeBottomInset"),
        lastHostImeEventAtUptimeMillis = longOrNull("lastHostImeEventAtUptimeMillis"),
        lastBridgeEventName = stringOrNull("lastBridgeEventName"),
        currentBridgeEventWindowCount = intOrNull("currentBridgeEventWindowCount") ?: 0,
        lastBridgeEventWindowCount = intOrNull("lastBridgeEventWindowCount") ?: 0,
        maxBridgeEventWindowCount = intOrNull("maxBridgeEventWindowCount") ?: 0,
        maxBridgeEventWindowStartedAtUptimeMillis = longOrNull("maxBridgeEventWindowStartedAtUptimeMillis"),
        runtimeEventHistory = getStringArrayList("runtimeEventHistory")?.toList()
            ?: getStringArray("runtimeEventHistory")?.toList()
            ?: emptyList(),
        createdAtEpochMillis = longOrNull("createdAtEpochMillis") ?: System.currentTimeMillis(),
    )
}

private fun Bundle.stringOrNull(key: String): String? = if (containsKey(key)) getString(key) else null
private fun Bundle.booleanOrNull(key: String): Boolean? = if (containsKey(key)) getBoolean(key) else null
private fun Bundle.intOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null
private fun Bundle.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null
