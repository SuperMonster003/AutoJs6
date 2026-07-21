@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package org.autojs.autojs.ui.edit.editor.ace

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences
import org.autojs.autojs.ui.edit.editor.EditorPinchToZoomPreferences
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnostics
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnosticsSnapshot
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceRuntimeEventHistory
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailure
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailureType
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs.ui.edit.editor.ace.health.AceJsErrorClassifier
import org.autojs.autojs.ui.edit.editor.ace.health.AceRuntimeHealthMonitor
import org.autojs.autojs.ui.edit.editor.ace.lsp.AceLspServerManager
import org.autojs.autojs.ui.edit.editor.loading.EditorTextLoadPolicy
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
class AceCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val fontManager = AceEditorFontManager.get(context)
    private var requestedFontId = AceEditorFontPreferences.getId()
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler(APP_ASSET_PATH, WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler(VIRTUAL_FONT_PATH, WebViewAssetLoader.PathHandler(::virtualFontResponse))
        .build()
    private var fontCatalogChangeSubscription: AceFontCatalogChangeSubscription? = null
    private val pendingScripts = ArrayDeque<PendingScript>()
    private val bridge = AceBridge(this)
    private val textMirror = AceTextMirror()
    private val imeController = AceImeController(IME_REQUEST_COALESCE_MS)
    private val singleTouchScrollCancelSlopPx = minOf(
        ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        resources.displayMetrics.density * SINGLE_TOUCH_SCROLL_CANCEL_SLOP_DP,
    ).coerceAtLeast(1f)
    private val eventHistory = AceRuntimeEventHistory()
    private val lspServerManager = AceLspServerManager()
    @Volatile
    private var lspRuntimeState: String? = null
    private val healthMonitor = AceRuntimeHealthMonitor(mainHandler) { failure ->
        notifyFallbackRequired(failure)
    }
    private val mirrorCalibrationRunnable = Runnable {
        calibrateTextMirrorIfSmall()
    }
    private val scheduledResizeRunnable = Runnable {
        runScheduledResize()
    }
    private val pendingSelectionActionModeRunnable = Runnable {
        flushPendingSelectionActionModeRequest()
    }
    private val restoreSelectionActionModeRunnable = Runnable {
        restoreUnexpectedSelectionActionModeAfterTouch()
    }
    private val imeSettleRunnable = Runnable {
        scheduleAceResize("ime_settle", RESIZE_SETTLE_DELAY_MS)
        healthMonitor.resumeHeartbeat()
    }

    val webView: WebView = AceWebView(
        context = context,
        softKeyboardCursorKeyHandler = { keyCode, event ->
            handleSoftKeyboardCursorKey(keyCode, event)
        },
        softKeyboardSelectionHandler = { offset, delta, source ->
            handleSoftKeyboardCursorSelection(offset, delta, source)
        },
        imeTextMutationHandler = { reason ->
            suppressTextMutationSelectionMenu(reason)
        },
        allowHostActionModeStart = {
            startingHostSelectionActionMode
        },
        suppressHostActionModeStart = {
            shouldSuppressSelectionActionModeForTextMutation() || isPinchSelectionActionModeSuppressed()
        },
        onSuppressedActionModeStart = {
            eventHistory.record("webview_action_mode_suppressed")
        },
        suppressImeInputConnection = {
            shouldSuppressSoftInputForPinch()
        },
    )
    private val loadingOverlay: View = View(context).apply {
        setBackgroundColor(editorBackgroundColor())
    }

    var listener: Listener? = null
    var onUserTouchInTextArea: (() -> Unit)? = null
    var onTextSizeSpChangedByGesture: ((Float, Boolean) -> Unit)? = null

    var isReady = false
        private set

    var textSnapshot = ""
        private set

    var dirty = false
        private set

    var canUndo = false
        private set

    var canRedo = false
        private set

    var lineCountSnapshot = 1
        private set

    var cursorLine = 0
        private set

    var cursorColumn = 0
        private set

    var cursorLineText = ""
        private set

    var breakpointsSnapshot: List<Int> = emptyList()
        private set

    var selectionSnapshot: AceSelection = AceSelection.EMPTY
        private set

    var selectedTextSnapshot = ""
        private set

    var completionPopupOpen = false
        private set

    var firstVisibleLine = 0
        private set

    var firstVisibleColumn = 0
        private set

    val textMirrorRevision: Long
        get() = textMirror.revision

    private var loaded = false
    private var destroyed = false
    private var readOnly = false
    private var userTouching = false
    private var firstPaintReceived = false
    private var pendingResizeReason: String? = null
    private var lastFirstPaintAtUptimeMillis = 0L
    private var lastResizeAtUptimeMillis = 0L
    private var lastAceResizeScheduledReason: String? = null
    private var lastAceResizeScheduledAtUptimeMillis = 0L
    private var lastAceResizeDispatchedReason: String? = null
    private var lastAceResizeDispatchedAtUptimeMillis = 0L
    private var lastAceResizeCompletedReason: String? = null
    private var lastAceResizeCompletedAtUptimeMillis = 0L
    private var lastActionModeAtUptimeMillis = 0L
    private var actionModeActive = false
    private var selectionActionMode: ActionMode? = null
    private val selectionActionModeRect = Rect()
    private var startingHostSelectionActionMode = false
    private var pendingSelectionActionModeRequest: SelectionActionModeRequest? = null
    private var lastSelectionActionModeRequest: SelectionActionModeRequest? = null
    private var selectionActionModeExpectedFinish = false
    private var selectionActionModeStabilizeUntilUptimeMillis = 0L
    private var selectionActionModeStabilizeRestarted = false
    private var textMutationActionModeSuppressUntilUptimeMillis = 0L
    private var lastTextTouchX = -1f
    private var lastTextTouchY = -1f
    private var lastTextTouchAtUptimeMillis = 0L
    private var domPinchTouchActive = false
    private var bridgePinchActive = false
    private var pinchSoftInputSuppressUntilUptimeMillis = 0L
    private var pinchSelectionActionModeSuppressUntilUptimeMillis = 0L
    private var pinchStartedWithImeVisible = false
    private var singleTouchStartX = 0f
    private var singleTouchStartY = 0f
    private var singleTouchMoveCancelled = false
    private var bridgeEventWindowStartedAtUptimeMillis = 0L
    private var bridgeEventWindowCount = 0
    private var lastBridgeEventWindowCount = 0
    private var maxBridgeEventWindowCount = 0
    private var maxBridgeEventWindowStartedAtUptimeMillis = 0L
    private var lastBridgeEventName: String? = null
    private var baseTextSizeSp = DEFAULT_TEXT_SIZE_SP
    private var viewZoomScale = 1f
    private var pinchGestureActive = false
    private var pinchTouchSuppressUntilUptimeMillis = 0L
    private var pinchZoomStrategy = PinchZoomStrategy.CHANGE_TEXT_SIZE
    private var scaleViewGestureChanged = false
    private var lastPinchFocusX = 0f
    private var lastPinchFocusY = 0f
    private var lastTextSizeScaleFactor = 1.0
    private val changeTextSizeScaleGestureDetector = ScaleGestureDetector(context, ChangeTextSizeScaleListener())
    private val scaleViewScaleGestureDetector = ScaleGestureDetector(context, ScaleViewScaleListener())

    val healthState: AceHealthState
        get() = healthMonitor.state

    val lastFailure: AceFailure?
        get() = healthMonitor.lastFailure

    fun createDiagnosticsSnapshot(
        fileSizeBytes: Long? = null,
        largeFilePolicy: String = "auto",
    ): AceDiagnosticsSnapshot {
        val failure = lastFailure
        val lspSnapshot = lspServerManager.snapshot()
        return AceDiagnosticsSnapshot(
            engineType = "ACE",
            aceHealthState = healthState.name,
            lastFailureType = failure?.type?.name,
            lastFailureMessage = failure?.message,
            lastFailureFatal = failure?.fatal,
            assetUrl = EDITOR_URL,
            webViewVersion = AceDiagnostics.currentWebViewVersion(),
            androidVersion = AceDiagnostics.androidVersion(),
            lspEnabled = lspSnapshot.enabled,
            lspState = lspSnapshot.state,
            lspTransport = lspSnapshot.transport,
            lspServerUri = lspSnapshot.serverUri,
            lspRootUri = lspSnapshot.rootUri,
            lspDocumentUri = lspSnapshot.documentUri,
            lspFallback = lspSnapshot.fallback,
            lspCompletionProvider = lspSnapshot.completionProvider,
            lspHoverProvider = lspSnapshot.hoverProvider,
            lspDiagnosticProvider = lspSnapshot.diagnosticProvider,
            lspSignatureProvider = lspSnapshot.signatureProvider,
            lspServerAvailable = lspSnapshot.serverAvailable,
            lspStartSupported = lspSnapshot.startSupported,
            lspSessionRevision = lspSnapshot.sessionRevision,
            lspRuntimeState = lspRuntimeState,
            aceFont = requestedFontId,
            fileSizeBytes = fileSizeBytes,
            largeFilePolicy = largeFilePolicy,
            textMirrorRevision = textMirrorRevision,
            jsRevision = ACE_RUNTIME_REVISION,
            lastReadyAtUptimeMillis = healthMonitor.lastReadyAtUptimeMillis.takeIf { it > 0L },
            lastFirstPaintAtUptimeMillis = lastFirstPaintAtUptimeMillis.takeIf { it > 0L },
            lastStateAtUptimeMillis = healthMonitor.lastStateAtUptimeMillis.takeIf { it > 0L },
            lastHeartbeatAtUptimeMillis = healthMonitor.lastHeartbeatAtUptimeMillis.takeIf { it > 0L },
            lastHeartbeatSuspendedAtUptimeMillis = healthMonitor.lastHeartbeatSuspendedAtUptimeMillis.takeIf { it > 0L },
            lastHeartbeatResumedAtUptimeMillis = healthMonitor.lastHeartbeatResumedAtUptimeMillis.takeIf { it > 0L },
            heartbeatSuspendedReason = healthMonitor.heartbeatSuspendedReason,
            lastImeState = imeController.state.name,
            lastImeHeight = imeController.lastImeHeight,
            lastImeRequestAtUptimeMillis = imeController.lastImeRequestAtUptimeMillis.takeIf { it > 0L },
            lastShowSoftInputAtUptimeMillis = imeController.lastShowSoftInputAtUptimeMillis.takeIf { it > 0L },
            lastHideSoftInputAtUptimeMillis = imeController.lastHideSoftInputAtUptimeMillis.takeIf { it > 0L },
            lastImeTransitionStartedAtUptimeMillis = imeController.lastImeTransitionStartedAtUptimeMillis.takeIf { it > 0L },
            lastImeTransitionEndedAtUptimeMillis = imeController.lastImeTransitionEndedAtUptimeMillis.takeIf { it > 0L },
            lastImeIgnoredRequestReason = imeController.lastIgnoredRequestReason,
            lastResizeAtUptimeMillis = lastResizeAtUptimeMillis.takeIf { it > 0L },
            pendingAceResizeReason = pendingResizeReason,
            lastAceResizeScheduledReason = lastAceResizeScheduledReason,
            lastAceResizeScheduledAtUptimeMillis = lastAceResizeScheduledAtUptimeMillis.takeIf { it > 0L },
            lastAceResizeDispatchedReason = lastAceResizeDispatchedReason,
            lastAceResizeDispatchedAtUptimeMillis = lastAceResizeDispatchedAtUptimeMillis.takeIf { it > 0L },
            lastAceResizeCompletedReason = lastAceResizeCompletedReason,
            lastAceResizeCompletedAtUptimeMillis = lastAceResizeCompletedAtUptimeMillis.takeIf { it > 0L },
            lastActionModeAtUptimeMillis = lastActionModeAtUptimeMillis.takeIf { it > 0L },
            viewportPolicy = null,
            lastViewportPolicyEvent = null,
            lastViewportPolicyEventAtUptimeMillis = null,
            lastViewportPolicyImeVisible = null,
            lastViewportPolicyBottomInset = null,
            lastHostImeVisible = null,
            lastHostImeBottomInset = null,
            lastHostImeEventAtUptimeMillis = null,
            lastBridgeEventName = lastBridgeEventName,
            currentBridgeEventWindowCount = bridgeEventWindowCount,
            lastBridgeEventWindowCount = lastBridgeEventWindowCount,
            maxBridgeEventWindowCount = maxBridgeEventWindowCount,
            maxBridgeEventWindowStartedAtUptimeMillis = maxBridgeEventWindowStartedAtUptimeMillis.takeIf { it > 0L },
            runtimeEventHistory = eventHistory.snapshot(),
        )
    }

    init {
        setBackgroundColor(editorBackgroundColor())
        addView(webView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(loadingOverlay, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        applyPinchToZoomStrategy()
        configureWebView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (fontCatalogChangeSubscription == null) {
            fontCatalogChangeSubscription = fontManager.observeCatalogChanges {
                if (!destroyed) {
                    applyRequestedFont()
                }
            }
        }
        if (loaded && !destroyed) {
            applyRequestedFont()
        }
        lspServerManager.attach()
        val lspSnapshot = lspServerManager.snapshot()
        eventHistory.record("lsp_manager", "state=${lspSnapshot.state},attached=${lspSnapshot.attached}")
        loadEditor()
    }

    override fun onDetachedFromWindow() {
        fontCatalogChangeSubscription?.cancel()
        fontCatalogChangeSubscription = null
        super.onDetachedFromWindow()
    }

    fun loadEditor() {
        if (loaded) {
            return
        }
        loaded = true
        showLoadingOverlay()
        eventHistory.record("page_load_start", EDITOR_URL)
        healthMonitor.markLoadingPage()
        if (AceEditorTestHooks.shouldForceRendererGoneFallback()) {
            reportFatalFailure(
                AceFailureType.RENDER_PROCESS_GONE,
                "Forced ACE renderer fallback from debug test hook",
            )
            return
        }
        webView.loadUrl(EDITOR_URL)
    }

    fun isUserTouching(): Boolean = userTouching

    fun setDocumentPath(path: String?) {
        lspServerManager.setDocumentPath(path)
        refreshLsp()
    }

    fun refreshLsp() {
        if (isReady) {
            invokeAce("refreshLsp")
        }
    }

    fun setText(text: String?) {
        resetTextMirror(text.orEmpty())
        dirty = false
        invokeAce(
            "setText",
            "${quote(textSnapshot)}, false, ${EditorTextLoadPolicy.requiresAceLightweightMode(textSnapshot)}",
        )
    }

    fun setTextDirty(text: String?) {
        resetTextMirror(text.orEmpty())
        dirty = true
        invokeAce(
            "setTextDirty",
            "${quote(textSnapshot)}, false, ${EditorTextLoadPolicy.requiresAceLightweightMode(textSnapshot)}",
        )
    }

    fun markClean() {
        dirty = false
        invokeAce("markClean")
    }

    fun markDirty() {
        dirty = true
        invokeAce("markDirty")
    }

    fun refreshState() {
        invokeAce("refreshState")
    }

    fun replaceAllTextUndoably(
        text: String,
        token: String,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val previousText = textSnapshot
        resetTextMirror(text)
        invokeAceBoolean(
            method = "replaceAllTextUndoably",
            args = "${quote(text)}, ${quote(token)}, false, ${EditorTextLoadPolicy.requiresAceLightweightMode(text)}",
        ) { succeeded ->
            if (succeeded) {
                // A true bridge acknowledgement means the group is already attached to ACE's
                // UndoManager. Keep the host cache authoritative while read-only loading-state
                // notifications are still draining.
                canUndo = true
                canRedo = false
            }
            if (!succeeded && textSnapshot == text) {
                resetTextMirror(previousText)
            }
            onComplete?.invoke(succeeded)
        }
    }

    fun addUndoableHistoryMarker(
        token: String,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        invokeAceBoolean(
            method = "addUndoableHistoryMarker",
            args = quote(token),
        ) { succeeded ->
            if (succeeded) {
                canUndo = true
                canRedo = false
            }
            onComplete?.invoke(succeeded)
        }
    }

    fun getText(callback: (String) -> Unit) {
        if (destroyed) {
            callback(textSnapshot)
            return
        }
        val fallback = quote(textMirror.text)
        evaluateOrQueue("window.AutoJsAce ? window.AutoJsAce.getText() : $fallback") {
            callback(decodeJsStringResult(it))
        }
    }

    fun insert(text: String?) {
        if (!canMutateText("insert")) {
            return
        }
        invokeAce("insert", quote(text.orEmpty()))
    }

    fun insertShortcutText(text: String?) {
        if (!canMutateText("insertShortcutText")) {
            return
        }
        invokeAce("insertShortcutText", quote(text.orEmpty()))
    }

    fun insertTab() {
        if (!canMutateText("insertTab")) {
            return
        }
        invokeAce("insertTab")
    }

    fun acceptCompletionOrInsertTab() {
        if (!canMutateText("acceptCompletionOrInsertTab")) {
            return
        }
        invokeAce("acceptCompletionOrInsertTab")
    }

    fun moveCompletionSelectionOrCursor(delta: Int) {
        invokeAce("moveCompletionSelectionOrCursor", delta.toString())
    }

    fun handleEscapeKey() {
        invokeAce("handleEscapeKey")
    }

    fun insert(line: Int, text: String?) {
        if (!canMutateText("insertAtLine")) {
            return
        }
        invokeAce("insertAtLine", "${line.coerceAtLeast(0)}, ${quote(text.orEmpty())}")
    }

    fun appendTextChunk(text: String?) {
        invokeAce("appendTextChunk", quote(text.orEmpty()))
    }

    fun selectRange(startOffset: Int, endOffset: Int) {
        val safeStart = startOffset.coerceIn(0, textSnapshot.length)
        val safeEnd = endOffset.coerceIn(0, textSnapshot.length)
        selectionSnapshot = selectionForOffsets(safeStart, safeEnd)
        selectedTextSnapshot = textSnapshot.substring(minOf(safeStart, safeEnd), maxOf(safeStart, safeEnd))
        invokeAce("selectRange", "$safeStart, $safeEnd")
    }

    fun jumpTo(line: Int, column: Int = 0) {
        val (targetLine, targetColumn) = updateCursorSnapshotForJump(line, column)
        invokeAce("jumpTo", "$targetLine, $targetColumn")
    }

    fun jumpToStart() {
        invokeAce("jumpToStart")
    }

    fun jumpToEnd() {
        invokeAce("jumpToEnd")
    }

    fun jumpToLineStart() {
        invokeAce("jumpToLineStart")
    }

    fun jumpToLineEnd() {
        invokeAce("jumpToLineEnd")
    }

    fun jumpToNextLine() {
        invokeAce("jumpToNextLine")
    }

    fun jumpToPrevLine() {
        invokeAce("jumpToPrevLine")
    }

    fun moveCursor(deltaChars: Int) {
        invokeAce("moveCursor", deltaChars.toString())
    }

    fun deleteLine() {
        if (!canMutateText("deleteLine")) {
            return
        }
        invokeAce("deleteLine")
    }

    fun clear() {
        if (!canMutateText("clear")) {
            return
        }
        resetTextMirror("")
        selectionSnapshot = AceSelection.EMPTY
        selectedTextSnapshot = ""
        invokeAce("clear")
    }

    fun toggleComment() {
        if (!canMutateText("toggleComment")) {
            return
        }
        invokeAce("toggleComment")
    }

    fun beautify() {
        if (!canMutateText("beautify")) {
            return
        }
        invokeAce("beautify")
    }

    fun replaceSelection(text: String?) {
        if (!canMutateText("replaceSelection")) {
            return
        }
        invokeAce("replaceSelection", quote(text.orEmpty()))
    }

    fun replaceStoredSelection() {
        if (!canMutateText("replaceStoredSelection")) {
            return
        }
        invokeAce("replaceStoredSelection")
    }

    fun setReplacement(text: String?) {
        invokeAce("setReplacement", quote(text.orEmpty()))
    }

    fun replaceAll(query: String, replacement: String?, usingRegex: Boolean) {
        if (!canMutateText("replaceAll")) {
            return
        }
        invokeAce("replaceAll", "${quote(query)}, ${quote(replacement.orEmpty())}, $usingRegex")
    }

    fun undo() {
        if (!canMutateText("undo")) {
            return
        }
        invokeAce("undo")
    }

    fun redo() {
        if (!canMutateText("redo")) {
            return
        }
        invokeAce("redo")
    }

    fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        selectionActionMode?.invalidate()
        if (readOnly) {
            hideSoftInput()
        }
        invokeAce("setReadOnly", readOnly.toString())
    }

    fun setTheme(theme: String) {
        invokeAce("setTheme", quote(theme))
    }

    fun setTextSizeSp(size: Float) {
        baseTextSizeSp = size.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        viewZoomScale = 1f
        applyEffectiveTextSizeSp(baseTextSizeSp, focusX = null, focusY = null)
    }

    fun notifyPinchToZoomStrategyChanged(newKey: String?) {
        applyPinchToZoomStrategy()
    }

    fun setFontFamily(fontFamily: String) {
        invokeAce("setFontFamily", quote(fontFamily))
    }

    fun setFontDescriptor(descriptor: AceFontDescriptor) {
        invokeAce("setFontDescriptor", quote(descriptor.toJson()))
    }

    fun setAceEditorFont(fontId: String) {
        requestedFontId = fontId
        applyRequestedFont()
    }

    private fun applyRequestedFont() {
        setFontDescriptor(fontManager.descriptor(requestedFontId))
    }

    fun notifyDisplayPreferencesChanged() {
        invokeAce("setWordWrapEnabled", EditorDisplayPreferences.isWordWrapEnabled().toString())
        invokeAce("setWordWrapIndentStyle", quote(EditorDisplayPreferences.wordWrapIndentStyle()))
        invokeAce("setLineNumbersEnabled", EditorDisplayPreferences.isLineNumbersEnabled().toString())
        invokeAce("setPrintMarginEnabled", EditorDisplayPreferences.isPrintMarginEnabled().toString())
        invokeAce("setIndentGuidesEnabled", EditorDisplayPreferences.isIndentGuidesEnabled().toString())
        invokeAce("setBreakpointMarkersEnabled", EditorDisplayPreferences.isBreakpointMarkersEnabled().toString())
        invokeAce("setFoldMarkersEnabled", EditorDisplayPreferences.isFoldMarkersEnabled().toString())
        invokeAce("setGutterWidthMode", quote(EditorDisplayPreferences.gutterWidthMode()))
        invokeAce("setFontLigaturesEnabled", EditorDisplayPreferences.isFontLigaturesEnabled().toString())
        invokeAce("setFontStylesEnabled", EditorDisplayPreferences.isFontStylesEnabled().toString())
    }

    private fun applyPinchToZoomStrategy() {
        pinchZoomStrategy = PinchZoomStrategy.fromChangeTextSizeEnabled(
            EditorPinchToZoomPreferences.isChangeTextSizeEnabled(),
        )
        pinchGestureActive = false
        lastTextSizeScaleFactor = 1.0
        if (pinchZoomStrategy != PinchZoomStrategy.SCALE_VIEW && viewZoomScale != 1f) {
            viewZoomScale = 1f
            applyEffectiveTextSizeSp(baseTextSizeSp, focusX = null, focusY = null)
        }
    }

    internal fun handleBridgePinchZoom(textSizeSp: Double, focusX: Double, focusY: Double, phase: String) {
        postToMain {
            if (!isReady || destroyed) {
                bridgePinchActive = false
                return@postToMain
            }
            when (phase) {
                "begin" -> {
                    if (pinchZoomStrategy == PinchZoomStrategy.DISABLED) {
                        bridgePinchActive = false
                        return@postToMain
                    }
                    pinchStartedWithImeVisible = isImeVisibleOrShowing()
                    bridgePinchActive = true
                    markPinchTouchSuppressed()
                    suppressSelectionActionModeAfterPinch()
                    suppressSoftInputAfterPinch()
                    pendingSelectionActionModeRequest = null
                    mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
                    finishSelectionActionMode()
                    cancelAceTouchInteraction("pinch_zoom_begin")
                }
                "end", "cancel" -> {
                    if (bridgePinchActive && phase == "end" && pinchZoomStrategy != PinchZoomStrategy.DISABLED) {
                        val targetSize = textSizeSp
                            .takeIf { it.isFinite() && it > 0.0 }
                            ?.toFloat()
                            ?: baseTextSizeSp
                        val safeSize = targetSize
                            .roundToInt()
                            .coerceIn(MIN_TEXT_SIZE_SP.roundToInt(), MAX_TEXT_SIZE_SP.roundToInt())
                            .toFloat()
                        syncBridgePinchTextSize(safeSize, persist = true)
                    }
                    bridgePinchActive = false
                    markPinchTouchSuppressed()
                    suppressSelectionActionModeAfterPinch()
                    suppressSoftInputAfterPinch()
                }
            }
        }
    }

    private fun syncBridgePinchTextSize(size: Float, persist: Boolean) {
        val safeSize = size.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        baseTextSizeSp = safeSize
        viewZoomScale = 1f
        scaleViewGestureChanged = false
        onTextSizeSpChangedByGesture?.invoke(safeSize, persist)
    }

    private fun handlePinchToZoomTouch(event: MotionEvent): Boolean {
        val detector = when (pinchZoomStrategy) {
            PinchZoomStrategy.CHANGE_TEXT_SIZE -> changeTextSizeScaleGestureDetector
            PinchZoomStrategy.SCALE_VIEW -> scaleViewScaleGestureDetector
            PinchZoomStrategy.DISABLED -> null
        } ?: return false

        val action = event.actionMasked
        val suppressingRecentPinchTouch = isPinchTouchSuppressed()
        detector.onTouchEvent(event)
        val handlingPinch = pinchGestureActive || detector.isInProgress || event.pointerCount > 1
        if (handlingPinch) {
            markPinchTouchSuppressed()
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            commitScaleViewTextSizeGestureIfNeeded()
            pinchGestureActive = false
        }
        return handlingPinch || suppressingRecentPinchTouch
    }

    private fun markPinchTouchSuppressed() {
        pinchTouchSuppressUntilUptimeMillis = maxOf(
            pinchTouchSuppressUntilUptimeMillis,
            SystemClock.uptimeMillis() + PINCH_TOUCH_SUPPRESS_MS,
        )
    }

    private fun suppressSoftInputAfterPinch() {
        pinchSoftInputSuppressUntilUptimeMillis = maxOf(
            pinchSoftInputSuppressUntilUptimeMillis,
            SystemClock.uptimeMillis() + PINCH_SOFT_INPUT_SUPPRESS_MS,
        )
    }

    private fun releaseSoftInputSuppressionAfterPinch(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now > pinchSoftInputSuppressUntilUptimeMillis) {
            return
        }
        pinchSoftInputSuppressUntilUptimeMillis = 0L
        pinchStartedWithImeVisible = false
        eventHistory.record("ime_show_unblocked", "reason=$reason", now)
    }

    private fun suppressSelectionActionModeAfterPinch() {
        pinchSelectionActionModeSuppressUntilUptimeMillis = maxOf(
            pinchSelectionActionModeSuppressUntilUptimeMillis,
            SystemClock.uptimeMillis() + PINCH_SELECTION_ACTION_MODE_SUPPRESS_MS,
        )
    }

    private fun isPinchTouchSuppressed(): Boolean {
        return SystemClock.uptimeMillis() <= pinchTouchSuppressUntilUptimeMillis
    }

    private fun isPinchSelectionActionModeSuppressed(): Boolean {
        return bridgePinchActive ||
            domPinchTouchActive ||
            SystemClock.uptimeMillis() <= pinchSelectionActionModeSuppressUntilUptimeMillis
    }

    private fun isImeVisibleOrShowing(): Boolean {
        return imeController.state == AceImeState.VISIBLE || imeController.state == AceImeState.SHOWING
    }

    private fun shouldSuppressSoftInputForPinch(): Boolean {
        return !pinchStartedWithImeVisible &&
            SystemClock.uptimeMillis() <= pinchSoftInputSuppressUntilUptimeMillis
    }

    private fun recordTextTouch(event: MotionEvent) {
        if (event.pointerCount != 1 || webView.width <= 0 || webView.height <= 0) {
            return
        }
        if (event.x < 0f || event.x > webView.width || event.y < 0f || event.y > webView.height) {
            return
        }
        lastTextTouchX = event.x
        lastTextTouchY = event.y
        lastTextTouchAtUptimeMillis = SystemClock.uptimeMillis()
    }

    private fun changeBaseTextSizeBy(delta: Int, focusX: Float, focusY: Float) {
        val next = (baseTextSizeSp.roundToInt() + delta)
            .coerceIn(MIN_TEXT_SIZE_SP.roundToInt(), MAX_TEXT_SIZE_SP.roundToInt())
            .toFloat()
        if (next == baseTextSizeSp) {
            return
        }
        baseTextSizeSp = next
        viewZoomScale = 1f
        applyEffectiveTextSizeSp(next, focusX, focusY)
        onTextSizeSpChangedByGesture?.invoke(next, false)
    }

    private fun scaleViewBy(scaleFactor: Float, focusX: Float, focusY: Float) {
        if (scaleFactor <= 0f || baseTextSizeSp <= 0f) {
            return
        }
        lastPinchFocusX = focusX
        lastPinchFocusY = focusY
        val currentSize = baseTextSizeSp
        val nextSize = (currentSize * scaleFactor).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        if (abs(nextSize - currentSize) < MIN_SCALE_VIEW_TEXT_SIZE_DELTA_SP) {
            return
        }
        baseTextSizeSp = nextSize
        viewZoomScale = 1f
        scaleViewGestureChanged = true
        applyEffectiveTextSizeSp(nextSize, focusX, focusY)
        onTextSizeSpChangedByGesture?.invoke(nextSize, false)
    }

    private fun currentScaleViewTextSizeSp(): Float {
        return (baseTextSizeSp * viewZoomScale).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
    }

    private fun commitScaleViewTextSizeGesture(focusX: Float, focusY: Float) {
        if (baseTextSizeSp <= 0f) {
            return
        }
        val previousBaseTextSizeSp = baseTextSizeSp
        val effectiveSize = currentScaleViewTextSizeSp()
        val committedSize = effectiveSize
            .roundToInt()
            .coerceIn(MIN_TEXT_SIZE_SP.roundToInt(), MAX_TEXT_SIZE_SP.roundToInt())
            .toFloat()
        val shouldApplyCommittedSize = viewZoomScale != 1f ||
            abs(committedSize - effectiveSize) >= MIN_SCALE_VIEW_TEXT_SIZE_DELTA_SP
        val shouldPersistCommittedSize = scaleViewGestureChanged ||
            viewZoomScale != 1f ||
            abs(committedSize - previousBaseTextSizeSp) >= MIN_SCALE_VIEW_TEXT_SIZE_DELTA_SP
        baseTextSizeSp = committedSize
        viewZoomScale = 1f
        scaleViewGestureChanged = false
        if (shouldApplyCommittedSize) {
            applyEffectiveTextSizeSp(committedSize, focusX, focusY)
        }
        if (shouldPersistCommittedSize) {
            onTextSizeSpChangedByGesture?.invoke(committedSize, true)
        }
    }

    private fun commitScaleViewTextSizeGestureIfNeeded() {
        if (pinchZoomStrategy == PinchZoomStrategy.SCALE_VIEW && scaleViewGestureChanged) {
            commitScaleViewTextSizeGesture(lastPinchFocusX, lastPinchFocusY)
        }
    }

    private fun applyEffectiveTextSizeSp(size: Float, focusX: Float?, focusY: Float?) {
        val safeSize = size.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        if (focusX == null || focusY == null) {
            invokeAce("setFontSizeSp", safeSize.toString())
            return
        }
        val cssScale = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val focusCssX = focusX / cssScale
        val focusCssY = focusY / cssScale
        invokeAce("setFontSizeSpKeepingFocus", "$safeSize, $focusCssX, $focusCssY")
    }

    fun toggleBreakpoint(line: Int) {
        invokeAce("toggleBreakpoint", line.coerceAtLeast(0).toString())
    }

    fun setBreakpoint(line: Int, enabled: Boolean) {
        invokeAce("setBreakpoint", "${line.coerceAtLeast(0)}, $enabled")
    }

    fun addOrRemoveBreakpointAtCurrentLine() {
        toggleBreakpoint(cursorLine)
    }

    fun removeAllBreakpoints() {
        invokeAce("clearBreakpoints")
    }

    fun setDebuggingLine(line: Int) {
        invokeAce("setDebuggingLine", line.toString())
    }

    fun restoreScroll(firstVisibleLine: Int?, firstVisibleColumn: Int?) {
        if (firstVisibleLine == null && firstVisibleColumn == null) {
            return
        }
        invokeAce(
            "restoreScroll",
            "${(firstVisibleLine ?: 0).coerceAtLeast(0)}, ${(firstVisibleColumn ?: 0).coerceAtLeast(0)}",
        )
    }

    fun requestEditorFocus() {
        eventHistory.record("focus_request", "requestEditorFocus")
        webView.requestFocus()
        evaluateOrQueue("window.AutoJsAce && window.AutoJsAce.focus && window.AutoJsAce.focus('requestEditorFocus');")
    }

    fun destroy() {
        destroyed = true
        fontCatalogChangeSubscription?.cancel()
        fontCatalogChangeSubscription = null
        eventHistory.record("destroy", "AceCodeEditor.destroy")
        finishSelectionActionMode()
        lspServerManager.detach()
        healthMonitor.markDestroyed()
        mainHandler.removeCallbacks(mirrorCalibrationRunnable)
        mainHandler.removeCallbacks(scheduledResizeRunnable)
        mainHandler.removeCallbacks(imeSettleRunnable)
        mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
        mainHandler.removeCallbacks(restoreSelectionActionModeRunnable)
        while (pendingScripts.isNotEmpty()) {
            pendingScripts.removeFirst().callback?.onReceiveValue(null)
        }
        webView.stopLoading()
        runCatching { webView.removeJavascriptInterface(JS_BRIDGE_NAME) }
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        removeView(loadingOverlay)
        removeView(webView)
        webView.destroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyEditorBackground()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw <= 0 || oldh <= 0 || w <= 0 || h <= 0 || (w == oldw && h == oldh)) {
            return
        }
        val heightDelta = kotlin.math.abs(h - oldh)
        val reason = if (heightDelta >= imeHeightThresholdPx()) {
            "ime_or_view_size_changed"
        } else {
            "view_size_changed"
        }
        eventHistory.record("view_size_changed", "reason=$reason,old=${oldw}x$oldh,new=${w}x$h")
        scheduleTransientResize(reason, RESIZE_SETTLE_DELAY_MS)
    }

    internal fun handleEvent(name: String, payloadJson: String?) {
        postToMain {
            if (destroyed) return@postToMain
            recordBridgeEvent("notifyEvent:$name")
            healthMonitor.markStateEvent()
            eventHistory.record("js_event", "name=$name")
            when (name) {
                "fatalError",
                "lspError",
                "completionError",
                "recoverableError",
                    -> reportClassifiedJsFailure(name, payloadJson)
                "fontLoadError" -> handleFontLoadError(payloadJson)
                "firstPaint" -> handleFirstPaint()
                "resizeDone" -> handleResizeDone(payloadJson)
                "changeDelta" -> handleChangeDelta(payloadJson)
                "historyOperation" -> handleHistoryOperation(payloadJson)
                "lspStateChanged" -> handleLspStateChanged(payloadJson)
            }
            listener?.onEvent(name, payloadJson)
        }
    }

    internal fun handleReady(stateJson: String?) {
        postToMain {
            if (destroyed) return@postToMain
            recordBridgeEvent("notifyReady")
            if (AceEditorTestHooks.shouldForceReadyTimeout()) {
                return@postToMain
            }
            eventHistory.record("bridge_ready")
            val state = updateState(
                stateJson = stateJson,
                acceptTextSnapshot = pendingScripts.isEmpty() || textSnapshot.isEmpty(),
            )
            healthMonitor.markBridgeReady()
            isReady = true
            listener?.onReady(state)
            flushPendingScripts()
            scheduleAceResize("bridge_ready", 0L)
            requestFirstPaintWhenStable()
            healthMonitor.startHeartbeat { evaluateHeartbeat() }
            eventHistory.record("heartbeat_start")
        }
    }

    internal fun handleStateChanged(stateJson: String?) {
        postToMain {
            if (destroyed) return@postToMain
            recordBridgeEvent("notifyStateChanged")
            healthMonitor.markStateEvent()
            val state = updateState(stateJson)
            listener?.onStateChanged(state)
        }
    }

    internal fun handleTextChanged(stateJson: String?) {
        postToMain {
            if (destroyed) return@postToMain
            recordBridgeEvent("notifyTextChanged")
            healthMonitor.markChangeEvent()
            val state = updateState(stateJson)
            listener?.onTextChanged(state.text, state)
        }
    }

    internal fun handleCursorChanged(lineText: String, line: Int, column: Int, stateJson: String?) {
        postToMain {
            if (destroyed) return@postToMain
            recordBridgeEvent("notifyCursorChanged")
            healthMonitor.markStateEvent()
            val mirroredLineText = lineTextAt(line)
            val state = updateState(stateJson).copy(
                cursor = AceCursor(line = line, column = column, lineText = mirroredLineText),
            ).also {
                cursorLine = it.cursor.line
                cursorColumn = it.cursor.column
                cursorLineText = it.cursor.lineText
            }
            listener?.onCursorChanged(mirroredLineText, line, column, state)
        }
    }

    internal fun handleBreakpointChanged(line: Int, enabled: Boolean, stateJson: String?) {
        postToMain {
            if (destroyed) return@postToMain
            recordBridgeEvent("notifyBreakpointChanged")
            healthMonitor.markStateEvent()
            val state = updateState(stateJson)
            listener?.onBreakpointChanged(line, enabled, state)
        }
    }

    internal fun handleError(message: String) {
        postToMain {
            if (destroyed) return@postToMain
            recordBridgeEvent("notifyError")
            listener?.onError(message.ifBlank { "Unknown ACE editor error" })
        }
    }

    private fun notifyFallbackRequired(failure: AceFailure) {
        postToMain {
            if (destroyed) return@postToMain
            eventHistory.record("fallback", "type=${failure.type.name},fatal=${failure.fatal},message=${failure.message}")
            listener?.onFailure(failure)
        }
    }

    private fun recordBridgeEvent(name: String) {
        val now = SystemClock.uptimeMillis()
        if (bridgeEventWindowStartedAtUptimeMillis <= 0L) {
            bridgeEventWindowStartedAtUptimeMillis = now
        }
        if (now - bridgeEventWindowStartedAtUptimeMillis >= BRIDGE_EVENT_WINDOW_MS) {
            lastBridgeEventWindowCount = bridgeEventWindowCount
            bridgeEventWindowStartedAtUptimeMillis = now
            bridgeEventWindowCount = 0
        }
        bridgeEventWindowCount += 1
        lastBridgeEventName = name
        if (bridgeEventWindowCount > maxBridgeEventWindowCount) {
            maxBridgeEventWindowCount = bridgeEventWindowCount
            maxBridgeEventWindowStartedAtUptimeMillis = bridgeEventWindowStartedAtUptimeMillis
        }
        if (bridgeEventWindowCount == BRIDGE_EVENT_STORM_THRESHOLD) {
            eventHistory.record(
                "bridge_event_storm",
                "count=$bridgeEventWindowCount,windowMillis=$BRIDGE_EVENT_WINDOW_MS,last=$name",
                now,
            )
        }
    }

    private fun handleChangeDelta(payloadJson: String?) {
        val delta = AceTextDelta.fromJson(payloadJson)
        if (delta == null) {
            handleError("Invalid ACE text delta")
            scheduleTextMirrorCalibration()
            return
        }
        if (textMirror.applyDelta(delta)) {
            textSnapshot = textMirror.text
            scheduleTextMirrorCalibration()
        } else {
            handleError("ACE text mirror delta failed")
            scheduleTextMirrorCalibration()
        }
    }

    private fun handleFirstPaint() {
        if (firstPaintReceived) {
            return
        }
        firstPaintReceived = true
        lastFirstPaintAtUptimeMillis = SystemClock.uptimeMillis()
        eventHistory.record("first_paint")
        hideLoadingOverlay()
        requestEditorFocus()
        mainHandler.postDelayed({ requestEditorFocus() }, INITIAL_FOCUS_RETRY_DELAY_MS)
    }

    private fun handleResizeDone(payloadJson: String?) {
        val now = SystemClock.uptimeMillis()
        lastResizeAtUptimeMillis = now
        lastAceResizeCompletedAtUptimeMillis = now
        lastAceResizeCompletedReason = resizeReasonFromPayload(payloadJson, lastAceResizeDispatchedReason)
        eventHistory.record("resize_done", "reason=${lastAceResizeCompletedReason.orEmpty()}", now)
    }

    private fun reportFatalFailure(type: AceFailureType, message: String, detail: String? = null) {
        reportFailure(
            AceFailure(
                type = type,
                message = message.ifBlank { "Unknown ACE editor failure" },
                detail = detail,
            ),
        )
        handleError(message)
    }

    private fun reportClassifiedJsFailure(name: String, payloadJson: String?) {
        val classification = AceJsErrorClassifier.classifyBridgeEvent(name, isReady, payloadJson)
        reportFailure(
            AceFailure(
                type = classification.type,
                message = classification.message,
                detail = payloadJson,
                fatal = classification.fatal,
            ),
            "js_${classification.category}",
        )
        handleError(classification.message)
    }

    private fun reportFailure(failure: AceFailure, eventType: String = "failure") {
        eventHistory.record(
            eventType,
            "type=${failure.type.name},fatal=${failure.fatal},message=${failure.message}",
            failure.timestampUptimeMillis,
        )
        healthMonitor.reportFailure(failure)
    }

    internal fun bridgeTheme(): String {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            "ace/theme/tomorrow_night"
        } else {
            "ace/theme/textmate"
        }
    }

    internal fun bridgeFontFamily(): String {
        return AceEditorFontPreferences.get().cssFontFamily
    }

    private fun handleHistoryOperation(payloadJson: String?) {
        val payload = runCatching { JSONObject(payloadJson.orEmpty()) }.getOrNull() ?: return
        val token = payload.optString("token")
        val undo = when (payload.optString("direction")) {
            "undo" -> true
            "redo" -> false
            else -> return
        }
        val state = payload.optJSONObject("state")?.let {
            updateState(it.toString(), acceptTextSnapshot = false)
        } ?: currentState()
        listener?.onStateChanged(state)
        listener?.onHistoryOperation(token, undo)
    }

    private fun handleLspStateChanged(payloadJson: String?) {
        val state = runCatching { JSONObject(payloadJson.orEmpty()) }.getOrNull() ?: return
        val loader = state.optJSONObject("tsLoader")
        val service = state.optJSONObject("tsService")
        lspRuntimeState = JSONObject().apply {
            put("serviceStatus", state.optString("serviceStatus", "unknown"))
            put("completionProvider", state.optString("completionProvider", "unknown"))
            put("hoverProvider", state.optString("hoverProvider", "unknown"))
            put("diagnosticProvider", state.optString("diagnosticProvider", "unknown"))
            put("signatureProvider", state.optString("signatureProvider", "unknown"))
            put("semanticServiceSuppressed", state.optBoolean("semanticServiceSuppressed", false))
            put("semanticServiceReason", state.optString("semanticServiceReason", ""))
            put("documentLength", state.optLong("documentLength", 0L))
            put("maxDocumentLength", state.optLong("maxDocumentLength", 0L))
            put("lastSemanticOperation", state.optString("lastSemanticOperation", ""))
            put("lastSemanticDurationMs", state.optLong("lastSemanticDurationMs", 0L))
            put("tsLoaderState", loader?.optString("state", "unknown") ?: "unknown")
            put("tsLoaderAttempts", loader?.optInt("attempts", 0) ?: 0)
            put("tsReady", service?.optBoolean("ready", false) ?: false)
            put("tsVersion", service?.optString("version", "") ?: "")
            put("tsReason", service?.optString("reason", "") ?: "")
        }.toString()
        eventHistory.record(
            "lsp_runtime",
            "status=${state.optString("serviceStatus", "unknown")},provider=${state.optString("completionProvider", "unknown")}",
        )
    }

    /** A browser-decode failure invalidates only the downloaded cache; the requested preference is retained. */
    private fun handleFontLoadError(payloadJson: String?) {
        eventHistory.record("font_load_error", payloadJson.orEmpty().take(FONT_ERROR_DIAGNOSTIC_LIMIT))
        val payload = runCatching { JSONObject(payloadJson.orEmpty()) }.getOrNull() ?: return
        if (payload.optString("source") != AceFontDescriptor.Source.INSTALLED.value) return
        if (!payload.optBoolean("invalidateInstalledCache", false)) return
        val fontId = payload.optString("id")
        val sha256 = payload.optString("sha256")
        if (fontId.isBlank() || sha256.isBlank()) return
        runCatching { fontManager.invalidateInstalled(fontId, sha256) }
    }

    internal fun bridgeFontDescriptor(): String {
        return fontManager.descriptor(requestedFontId).toJson()
    }

    internal fun bridgeFontLigaturesEnabled(): Boolean {
        return AceEditorDisplayPreferences.isFontLigaturesEnabled()
    }

    internal fun bridgeFontStylesEnabled(): Boolean {
        return AceEditorDisplayPreferences.isFontStylesEnabled()
    }

    internal fun bridgeWordWrapEnabled(): Boolean {
        return AceEditorDisplayPreferences.isWordWrapEnabled()
    }

    internal fun bridgeWordWrapIndentStyle(): String {
        return AceEditorDisplayPreferences.wordWrapIndentStyle()
    }

    internal fun bridgeLineNumbersEnabled(): Boolean {
        return AceEditorDisplayPreferences.isLineNumbersEnabled()
    }

    internal fun bridgePrintMarginEnabled(): Boolean {
        return AceEditorDisplayPreferences.isPrintMarginEnabled()
    }

    internal fun bridgeIndentGuidesEnabled(): Boolean {
        return AceEditorDisplayPreferences.isIndentGuidesEnabled()
    }

    internal fun bridgeBreakpointMarkersEnabled(): Boolean {
        return AceEditorDisplayPreferences.isBreakpointMarkersEnabled()
    }

    internal fun bridgeFoldMarkersEnabled(): Boolean {
        return AceEditorDisplayPreferences.isFoldMarkersEnabled()
    }

    internal fun bridgeGutterWidthMode(): String {
        return AceEditorDisplayPreferences.gutterWidthMode()
    }

    internal fun bridgeLspOptions(): String {
        return lspServerManager.bridgeOptionsJson()
    }

    internal fun bridgePinchToZoomStrategy(): String {
        return pinchZoomStrategy.bridgeName
    }

    internal fun showSoftInput() {
        if (readOnly) {
            eventHistory.record("ime_show_blocked", "readOnly=true")
            return
        }
        postToMain {
            val now = SystemClock.uptimeMillis()
            if (shouldSuppressSoftInputForPinch()) {
                eventHistory.record(
                    "ime_show_blocked",
                    "reason=pinch_zoom,remaining=${pinchSoftInputSuppressUntilUptimeMillis - now}",
                    now,
                )
                return@postToMain
            }
            val decision = imeController.requestShow(now)
            eventHistory.record(
                "ime_show_request",
                "accepted=${decision.accepted},reason=${decision.reason},state=${imeController.state.name}",
                now,
            )
            if (!decision.accepted) {
                return@postToMain
            }
            webView.requestFocus()
            scheduleTransientResize("ime_show_requested", RESIZE_SETTLE_DELAY_MS)
            ViewUtils.showSoftInput(webView)
        }
    }

    internal fun hideSoftInput() {
        postToMain {
            val now = SystemClock.uptimeMillis()
            val decision = imeController.requestHide(now)
            eventHistory.record(
                "ime_hide_request",
                "accepted=${decision.accepted},reason=${decision.reason},state=${imeController.state.name}",
                now,
            )
            if (!decision.accepted) {
                return@postToMain
            }
            scheduleTransientResize("ime_hide_requested", RESIZE_SETTLE_DELAY_MS)
            ViewUtils.hideSoftInput(webView)
        }
    }

    fun ensureCursorVisible(reason: String = "ensure_cursor_visible") {
        postToMain {
            scheduleAceResize(reason, RESIZE_SETTLE_DELAY_MS)
        }
    }

    fun onHostImeInsetsChanged(visible: Boolean, bottomInset: Int) {
        postToMain {
            val now = SystemClock.uptimeMillis()
            imeController.onHostImeInsetsChanged(visible, bottomInset, now)
            eventHistory.record(
                "ime_insets",
                "visible=$visible,bottomInset=$bottomInset,state=${imeController.state.name}",
                now,
            )
            val reason = if (visible) {
                "host_ime_visible:$bottomInset"
            } else {
                "host_ime_hidden"
            }
            scheduleTransientResize(reason, RESIZE_SETTLE_DELAY_MS)
        }
    }

    internal fun startActionMode(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        hasSelection: Boolean,
        selectAll: Boolean,
    ) {
        postToMain {
            val request = SelectionActionModeRequest(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                hasSelection = hasSelection,
                selectAll = selectAll,
            )
            if (isPinchSelectionActionModeSuppressed()) {
                pendingSelectionActionModeRequest = null
                mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
                eventHistory.record(
                    "action_mode_suppressed",
                    "reason=pinch_zoom,selectionLength=${selectedTextSnapshot.length}",
                )
                finishSelectionActionMode()
                cancelAceTouchInteraction("pinch_action_mode")
                return@postToMain
            }
            if (shouldSuppressSelectionActionModeForTextMutation()) {
                pendingSelectionActionModeRequest = null
                mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
                eventHistory.record(
                    "action_mode_suppressed",
                    "reason=text_mutation,selectionLength=${selectedTextSnapshot.length}",
                )
                finishSelectionActionMode()
                return@postToMain
            }
            if (userTouching && !selectAll) {
                pendingSelectionActionModeRequest = request
                eventHistory.record(
                    "action_mode_deferred",
                    "touching=true,selectionLength=${selectedTextSnapshot.length}",
                )
                return@postToMain
            }
            startSelectionActionMode(request)
        }
    }

    internal fun finishActionMode() {
        postToMain {
            pendingSelectionActionModeRequest = null
            mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
            finishSelectionActionMode()
        }
    }

    internal fun performLongPressFeedback() {
        postToMain {
            if (isPinchSelectionActionModeSuppressed()) {
                eventHistory.record("long_press_feedback_suppressed", "reason=pinch_zoom")
                return@postToMain
            }
            webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun startSelectionActionMode(request: SelectionActionModeRequest) {
        updateSelectionActionModeRect(request.left, request.top, request.right, request.bottom)
        if (!request.hasSelection || selectedTextSnapshot.isEmpty()) {
            finishSelectionActionMode()
            return
        }
        lastSelectionActionModeRequest = request
        selectionActionModeExpectedFinish = false
        actionModeActive = true
        lastActionModeAtUptimeMillis = SystemClock.uptimeMillis()
        healthMonitor.markStateEvent()
        if (selectionActionMode == null) {
            selectionActionMode = createSelectionActionMode()
            if (selectionActionMode == null) {
                actionModeActive = false
                eventHistory.record("action_mode_unavailable", "selectionLength=${selectedTextSnapshot.length}")
                return
            }
        } else {
            invalidateSelectionActionMode()
        }
        scheduleAceResize(
            "action_mode_started:${request.left.toFloat()},${request.top.toFloat()},${request.right.toFloat()},${request.bottom.toFloat()},${request.hasSelection},${request.selectAll}",
            RESIZE_SETTLE_DELAY_MS,
        )
    }

    private fun schedulePendingSelectionActionModeAfterTouch() {
        if (pendingSelectionActionModeRequest == null) {
            return
        }
        mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
        mainHandler.postDelayed(pendingSelectionActionModeRunnable, ACTION_MODE_AFTER_TOUCH_DELAY_MS)
    }

    private fun flushPendingSelectionActionModeRequest() {
        val request = pendingSelectionActionModeRequest ?: return
        pendingSelectionActionModeRequest = null
        if (userTouching) {
            pendingSelectionActionModeRequest = request
            schedulePendingSelectionActionModeAfterTouch()
            return
        }
        startSelectionActionMode(request)
    }

    private fun createSelectionActionMode(): ActionMode? {
        val callback = createSelectionActionModeCallback()
        startingHostSelectionActionMode = true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                webView.startActionMode(callback, ActionMode.TYPE_FLOATING)
            } else {
                webView.startActionMode(callback)
            }
        } finally {
            startingHostSelectionActionMode = false
        }
    }

    private fun invalidateSelectionActionMode() {
        val mode = selectionActionMode ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mode.invalidateContentRect()
        }
        mode.invalidate()
    }

    private fun createSelectionActionModeCallback(): ActionMode.Callback {
        return object : ActionMode.Callback2() {

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.title = null
                menu.add(0, ACTION_MODE_COPY, 0, android.R.string.copy)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(0, ACTION_MODE_PASTE, 1, android.R.string.paste)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, ACTION_MODE_SELECT_ALL, 2, R.string.text_select_all)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                if (!readOnly) {
                    menu.add(0, R.id.action_delete_line, 3, R.string.text_delete_line)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                menu.add(0, R.id.action_copy_line, 4, R.string.text_copy_line)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.findItem(ACTION_MODE_COPY)?.isEnabled = selectedTextSnapshot.isNotEmpty()
                menu.findItem(ACTION_MODE_PASTE)?.isEnabled = !readOnly && ClipboardUtils.getClipOrEmpty(context).isNotEmpty()
                menu.findItem(ACTION_MODE_SELECT_ALL)?.isEnabled = textSnapshot.isNotEmpty()
                menu.findItem(R.id.action_delete_line)?.isEnabled = !readOnly && textSnapshot.isNotEmpty()
                menu.findItem(R.id.action_copy_line)?.isEnabled = cursorLineText.isNotEmpty()
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    ACTION_MODE_COPY -> {
                        copySelectedTextToClipboard()
                        listener?.onSelectionAction(SelectionAction.Copy)
                        finishSelectionActionModeForMenuItem(mode)
                        true
                    }
                    ACTION_MODE_PASTE -> {
                        pasteClipboardIntoSelection()
                        listener?.onSelectionAction(SelectionAction.Paste)
                        finishSelectionActionModeForMenuItem(mode)
                        true
                    }
                    ACTION_MODE_SELECT_ALL -> {
                        selectRange(0, textSnapshot.length)
                        listener?.onSelectionAction(SelectionAction.SelectAll)
                        mode.invalidate()
                        true
                    }
                    R.id.action_delete_line -> {
                        deleteLine()
                        listener?.onSelectionAction(SelectionAction.DeleteLine)
                        finishSelectionActionModeForMenuItem(mode)
                        true
                    }
                    R.id.action_copy_line -> {
                        copyCurrentLineToClipboard()
                        listener?.onSelectionAction(SelectionAction.CopyLine)
                        finishSelectionActionModeForMenuItem(mode)
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                val expected = selectionActionModeExpectedFinish
                selectionActionModeExpectedFinish = false
                markSelectionActionModeFinished(mode, expected)
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                outRect.set(selectionActionModeRect)
            }
        }
    }

    private fun updateSelectionActionModeRect(left: Double, top: Double, right: Double, bottom: Double) {
        val scale = resources.displayMetrics.density.toDouble().takeIf { it > 0.0 } ?: 1.0
        val maxRight = webView.width.takeIf { it > 1 } ?: Int.MAX_VALUE
        val maxBottom = webView.height.takeIf { it > 1 } ?: Int.MAX_VALUE
        if (hasRecentTextTouchAnchor()) {
            val anchorLeft = lastTextTouchX.roundToInt().coerceIn(0, maxRight - 1)
            val anchorTop = lastTextTouchY.roundToInt().coerceIn(0, maxBottom - 1)
            val lineHeight = (resources.displayMetrics.density * ACTION_MODE_TOUCH_ANCHOR_HEIGHT_DP)
                .roundToInt()
                .coerceAtLeast(1)
            selectionActionModeRect.set(
                anchorLeft,
                anchorTop,
                (anchorLeft + 1).coerceAtMost(maxRight),
                (anchorTop + lineHeight).coerceAtMost(maxBottom),
            )
            return
        }
        if (isProbablyInvalidActionModeRect(left, top, right, bottom)) {
            if (!selectionActionModeRect.isEmpty) {
                return
            }
            setFallbackSelectionActionModeRect(maxRight, maxBottom)
            return
        }
        val safeLeft = (minOf(left, right) * scale).toInt().coerceIn(0, maxRight - 1)
        val safeTop = (minOf(top, bottom) * scale).toInt().coerceIn(0, maxBottom - 1)
        val safeRight = (maxOf(left, right) * scale).toInt().coerceIn(safeLeft + 1, maxRight)
        val safeBottom = (maxOf(top, bottom) * scale).toInt().coerceIn(safeTop + 1, maxBottom)
        selectionActionModeRect.set(safeLeft, safeTop, safeRight, safeBottom)
    }

    private fun setFallbackSelectionActionModeRect(maxRight: Int, maxBottom: Int) {
        val safeMaxRight = maxRight.takeIf { it != Int.MAX_VALUE } ?: resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val safeMaxBottom = maxBottom.takeIf { it != Int.MAX_VALUE } ?: resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val lineHeight = (resources.displayMetrics.density * ACTION_MODE_TOUCH_ANCHOR_HEIGHT_DP)
            .roundToInt()
            .coerceAtLeast(1)
        val centerLeft = (safeMaxRight / 2).coerceIn(0, safeMaxRight - 1)
        val centerTop = (safeMaxBottom / 2).coerceIn(0, safeMaxBottom - 1)
        selectionActionModeRect.set(
            centerLeft,
            centerTop,
            (centerLeft + 1).coerceAtMost(safeMaxRight),
            (centerTop + lineHeight).coerceAtMost(safeMaxBottom),
        )
    }

    private fun isProbablyInvalidActionModeRect(left: Double, top: Double, right: Double, bottom: Double): Boolean {
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
            return true
        }
        val width = abs(right - left)
        val height = abs(bottom - top)
        return minOf(left, right) <= 1.0 && minOf(top, bottom) <= 1.0 && width <= 2.0 && height <= 2.0
    }

    private fun hasRecentTextTouchAnchor(): Boolean {
        return lastTextTouchAtUptimeMillis > 0L &&
            SystemClock.uptimeMillis() - lastTextTouchAtUptimeMillis <= ACTION_MODE_TOUCH_ANCHOR_MAX_AGE_MS &&
            lastTextTouchX >= 0f &&
            lastTextTouchY >= 0f
    }

    private fun copySelectedTextToClipboard() {
        if (selectedTextSnapshot.isEmpty()) {
            return
        }
        ClipboardUtils.setClip(context, selectedTextSnapshot)
        eventHistory.record("action_mode_copy", "length=${selectedTextSnapshot.length}")
    }

    private fun copyCurrentLineToClipboard() {
        ClipboardUtils.setClip(context, cursorLineText)
        eventHistory.record("action_mode_copy_line", "line=${cursorLine + 1},length=${cursorLineText.length}")
    }

    private fun pasteClipboardIntoSelection() {
        if (!canMutateText("actionModePaste")) {
            return
        }
        val clip = ClipboardUtils.getClipOrEmpty(context).toString()
        if (clip.isEmpty()) {
            return
        }
        replaceSelection(clip)
        eventHistory.record("action_mode_paste", "length=${clip.length}")
    }

    private fun canMutateText(operation: String): Boolean {
        if (!readOnly) {
            return true
        }
        eventHistory.record("read_only_blocked", operation)
        return false
    }

    private fun finishSelectionActionMode() {
        val mode = selectionActionMode
        if (mode != null) {
            selectionActionModeExpectedFinish = true
            mode.finish()
            return
        }
        markSelectionActionModeFinished(expected = true)
    }

    private fun finishSelectionActionModeForMenuItem(mode: ActionMode) {
        selectionActionModeExpectedFinish = true
        mode.finish()
    }

    private fun markSelectionActionModeFinished(mode: ActionMode? = null, expected: Boolean = false) {
        if (mode == null || selectionActionMode === mode) {
            selectionActionMode = null
        }
        if (expected) {
            cancelSelectionActionModeRestore()
        }
        if (maybeRestoreUnexpectedSelectionActionModeAfterTouch(expected)) {
            return
        }
        if (!actionModeActive) {
            return
        }
        actionModeActive = false
        lastActionModeAtUptimeMillis = SystemClock.uptimeMillis()
        healthMonitor.markStateEvent()
        eventHistory.record("action_mode_finished")
    }

    private fun cancelSelectionActionModeRestore() {
        mainHandler.removeCallbacks(restoreSelectionActionModeRunnable)
        selectionActionModeStabilizeUntilUptimeMillis = 0L
        selectionActionModeStabilizeRestarted = false
    }

    private fun shouldSuppressSelectionActionModeForTextMutation(): Boolean {
        return SystemClock.uptimeMillis() <= textMutationActionModeSuppressUntilUptimeMillis
    }

    private fun stabilizeSelectionActionModeAfterTouch() {
        if (selectedTextSnapshot.isEmpty() && pendingSelectionActionModeRequest == null) {
            return
        }
        selectionActionModeStabilizeUntilUptimeMillis =
            SystemClock.uptimeMillis() + ACTION_MODE_POST_TOUCH_STABILIZE_MS
        selectionActionModeStabilizeRestarted = false
        eventHistory.record(
            "action_mode_stabilize_after_touch",
            "selectionLength=${selectedTextSnapshot.length}",
        )
    }

    private fun maybeRestoreUnexpectedSelectionActionModeAfterTouch(expected: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        val request = lastSelectionActionModeRequest
        if (
            expected ||
            userTouching ||
            selectedTextSnapshot.isEmpty() ||
            request == null ||
            selectionActionModeStabilizeRestarted ||
            now > selectionActionModeStabilizeUntilUptimeMillis
        ) {
            return false
        }
        selectionActionModeStabilizeRestarted = true
        eventHistory.record(
            "action_mode_restore_after_touch",
            "selectionLength=${selectedTextSnapshot.length},remaining=${selectionActionModeStabilizeUntilUptimeMillis - now}",
        )
        mainHandler.removeCallbacks(restoreSelectionActionModeRunnable)
        mainHandler.postDelayed(restoreSelectionActionModeRunnable, ACTION_MODE_RESTORE_AFTER_TOUCH_DELAY_MS)
        return true
    }

    private fun restoreUnexpectedSelectionActionModeAfterTouch() {
        val request = lastSelectionActionModeRequest ?: return
        if (userTouching || selectedTextSnapshot.isEmpty() || selectionActionMode != null) {
            return
        }
        startSelectionActionMode(request)
    }

    private fun suppressSelectionActionModeForPointerGuard(reason: String) {
        pendingSelectionActionModeRequest = null
        mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
        finishSelectionActionMode()
        cancelAceTouchInteraction(reason)
    }

    private fun cancelAceTouchInteraction(reason: String) {
        if (isReady) {
            invokeAce("cancelTouchInteraction", quote(reason))
        }
    }

    private fun scheduleTransientResize(reason: String, delayMillis: Long) {
        if (destroyed) {
            return
        }
        eventHistory.record("transient_resize", "reason=$reason,delayMillis=$delayMillis")
        healthMonitor.suspendHeartbeat(reason, IME_TRANSITION_HEARTBEAT_SUSPEND_MS)
        scheduleAceResize(reason, delayMillis)
        mainHandler.removeCallbacks(imeSettleRunnable)
        mainHandler.postDelayed(imeSettleRunnable, IME_TRANSITION_HEARTBEAT_SUSPEND_MS)
    }

    private fun scheduleAceResize(reason: String, delayMillis: Long) {
        if (destroyed) {
            return
        }
        val now = SystemClock.uptimeMillis()
        pendingResizeReason = reason
        lastAceResizeScheduledReason = reason
        lastAceResizeScheduledAtUptimeMillis = now
        eventHistory.record("resize_scheduled", "reason=$reason,delayMillis=$delayMillis", now)
        mainHandler.removeCallbacks(scheduledResizeRunnable)
        mainHandler.postDelayed(scheduledResizeRunnable, delayMillis.coerceAtLeast(0L))
    }

    private fun requestFirstPaintWhenStable() {
        invokeAce("requestFirstPaint", quote("native_ready"))
    }

    private fun runScheduledResize() {
        if (destroyed) {
            return
        }
        val reason = pendingResizeReason ?: "scheduled_resize"
        pendingResizeReason = null
        val now = SystemClock.uptimeMillis()
        lastResizeAtUptimeMillis = now
        lastAceResizeDispatchedReason = reason
        lastAceResizeDispatchedAtUptimeMillis = now
        eventHistory.record("resize_dispatched", "reason=$reason", now)
        invokeAce("scheduleResize", quote(reason))
    }

    private fun showLoadingOverlay() {
        applyEditorBackground()
        firstPaintReceived = false
        loadingOverlay.animate().cancel()
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.bringToFront()
    }

    private fun hideLoadingOverlay() {
        loadingOverlay.animate().cancel()
        loadingOverlay.visibility = View.GONE
    }

    private fun applyEditorBackground() {
        val color = editorBackgroundColor()
        setBackgroundColor(color)
        webView.setBackgroundColor(color)
        loadingOverlay.setBackgroundColor(color)
    }

    private fun editorBackgroundColor(): Int {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            Color.rgb(30, 30, 30)
        } else {
            Color.rgb(247, 248, 250)
        }
    }

    private fun imeHeightThresholdPx(): Int {
        return (resources.displayMetrics.density * IME_HEIGHT_THRESHOLD_DP).toInt()
    }

    private fun handleDomPinchTouchDispatch(event: MotionEvent): Boolean? {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!domPinchTouchActive) {
                    pinchStartedWithImeVisible = isImeVisibleOrShowing()
                }
                domPinchTouchActive = true
                userTouching = true
                markPinchTouchSuppressed()
                suppressSelectionActionModeAfterPinch()
                suppressSoftInputAfterPinch()
                onUserTouchInTextArea?.invoke()
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (domPinchTouchActive || event.pointerCount > 1) {
                    domPinchTouchActive = true
                    userTouching = true
                    markPinchTouchSuppressed()
                    suppressSelectionActionModeAfterPinch()
                    suppressSoftInputAfterPinch()
                    return false
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (domPinchTouchActive) {
                    markPinchTouchSuppressed()
                    suppressSelectionActionModeAfterPinch()
                    suppressSoftInputAfterPinch()
                    return false
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
                -> {
                if (domPinchTouchActive) {
                    domPinchTouchActive = false
                    userTouching = false
                    markPinchTouchSuppressed()
                    suppressSelectionActionModeAfterPinch()
                    suppressSoftInputAfterPinch()
                    return true
                }
            }
        }
        return null
    }

    private fun handleSingleTouchScrollGuard(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                singleTouchStartX = event.x
                singleTouchStartY = event.y
                singleTouchMoveCancelled = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1 || singleTouchMoveCancelled) {
                    return
                }
                if (selectedTextSnapshot.isNotEmpty() || selectionActionMode != null) {
                    return
                }
                val dx = event.x - singleTouchStartX
                val dy = event.y - singleTouchStartY
                if (dx * dx + dy * dy < singleTouchScrollCancelSlopPx * singleTouchScrollCancelSlopPx) {
                    return
                }
                singleTouchMoveCancelled = true
                pendingSelectionActionModeRequest = null
                mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
                cancelSelectionActionModeRestore()
                cancelAceTouchInteraction("single_finger_scroll")
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
                -> {
                singleTouchMoveCancelled = false
            }
        }
    }

    private fun configureWebView() {
        webView.setBackgroundColor(editorBackgroundColor())
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isHorizontalScrollBarEnabled = true
        webView.isVerticalScrollBarEnabled = true
        webView.setOnFocusChangeListener { _, hasFocus ->
            eventHistory.record(if (hasFocus) "focus" else "blur", "webView")
        }
        webView.setOnTouchListener { _, event ->
            handleDomPinchTouchDispatch(event)?.let { consume ->
                return@setOnTouchListener consume
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN && event.pointerCount == 1 && !domPinchTouchActive) {
                releaseSoftInputSuppressionAfterPinch("single_touch")
            }
            handleSingleTouchScrollGuard(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE,
                    -> {
                    recordTextTouch(event)
                    userTouching = true
                    onUserTouchInTextArea?.invoke()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_UP,
                    -> {
                    recordTextTouch(event)
                    userTouching = false
                    stabilizeSelectionActionModeAfterTouch()
                    schedulePendingSelectionActionModeAfterTouch()
                }
            }
            false
        }

        val minimumFontSize = resources.getInteger(R.integer.editor_text_size_min_value).coerceAtLeast(1)
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = false
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            setSupportMultipleWindows(false)
            this.minimumFontSize = minimumFontSize
            minimumLogicalFontSize = minimumFontSize
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.addJavascriptInterface(bridge, JS_BRIDGE_NAME)
        webView.webViewClient = WhitelistWebViewClient()
        webView.webChromeClient = AceWebChromeClient()
    }

    private fun handleSoftKeyboardCursorKey(keyCode: Int, event: KeyEvent): Boolean {
        if (!isReady || !isSoftKeyboardCursorKey(keyCode, event)) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val step = event.repeatCount.coerceAtLeast(0) + 1
            val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -step else step
            eventHistory.record(
                "ime_cursor_key",
                "keyCode=$keyCode,repeat=${event.repeatCount},delta=$delta",
            )
            moveCursor(delta)
        }
        return event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
    }

    private fun handleSoftKeyboardCursorSelection(offset: Int, delta: Int, source: String) {
        if (!isReady) {
            return
        }
        eventHistory.record(
            "ime_cursor_selection",
            "source=$source,offset=$offset,delta=$delta,mode=textarea_window",
        )
    }

    private fun suppressTextMutationSelectionMenu(reason: String) {
        textMutationActionModeSuppressUntilUptimeMillis = maxOf(
            textMutationActionModeSuppressUntilUptimeMillis,
            SystemClock.uptimeMillis() + ACTION_MODE_TEXT_MUTATION_SUPPRESS_MS,
        )
        pendingSelectionActionModeRequest = null
        mainHandler.removeCallbacks(pendingSelectionActionModeRunnable)
        finishSelectionActionMode()
        if (isReady) {
            invokeAce("suppressTextMutationSelectionMenu", quote(reason))
        }
    }

    private fun isSoftKeyboardCursorKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return false
        }
        val fromSoftKeyboard = event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0 ||
            event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD
        return fromSoftKeyboard &&
            (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)
    }

    private class AceWebView(
        context: Context,
        private val softKeyboardCursorKeyHandler: (Int, KeyEvent) -> Boolean,
        private val softKeyboardSelectionHandler: (Int, Int, String) -> Unit,
        private val imeTextMutationHandler: (String) -> Unit,
        private val allowHostActionModeStart: () -> Boolean,
        private val suppressHostActionModeStart: () -> Boolean,
        private val onSuppressedActionModeStart: () -> Unit,
        private val suppressImeInputConnection: () -> Boolean,
    ) : WebView(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL)) {
                imeTextMutationHandler("dispatchKeyEvent:${event.keyCode}")
            }
            return softKeyboardCursorKeyHandler(event.keyCode, event) || super.dispatchKeyEvent(event)
        }

        override fun onCheckIsTextEditor(): Boolean {
            return !suppressImeInputConnection() && super.onCheckIsTextEditor()
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            if (suppressImeInputConnection()) {
                return null
            }
            val baseConnection = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(baseConnection, true) {
                private var lastCollapsedSelection = -1
                private var ignoreSelectionUntilUptimeMillis = 0L

                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    markImeTextMutation("commitText")
                    return super.commitText(text, newCursorPosition)
                }

                override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    markImeTextMutation("setComposingText")
                    return super.setComposingText(text, newCursorPosition)
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    markImeTextMutation("deleteSurroundingText")
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }

                override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                    markImeTextMutation("deleteSurroundingTextInCodePoints")
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    if (event.action == KeyEvent.ACTION_DOWN &&
                        (event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL)
                    ) {
                        markImeTextMutation("InputConnection.sendKeyEvent:${event.keyCode}")
                    }
                    return super.sendKeyEvent(event)
                }

                override fun setSelection(start: Int, end: Int): Boolean {
                    if (start == end && start >= 0) {
                        val previousSelection = lastCollapsedSelection
                        val delta = if (previousSelection >= 0) start - previousSelection else 0
                        lastCollapsedSelection = start
                        if (SystemClock.uptimeMillis() < ignoreSelectionUntilUptimeMillis) {
                            super.setSelection(start, end)
                            softKeyboardSelectionHandler(start, delta, "InputConnection.setSelection.suppressed")
                            return true
                        }
                        super.setSelection(start, end)
                        softKeyboardSelectionHandler(start, delta, "InputConnection.setSelection.textarea")
                        // Gboard spacebar cursor mode reports offsets inside ACE's hidden
                        // textarea window. Let WebView update that local selection so the
                        // bridge can map the local window delta back to the ACE caret, while
                        // still consuming the call to avoid document-absolute fallback moves.
                        return true
                    } else {
                        lastCollapsedSelection = -1
                    }
                    return super.setSelection(start, end)
                }

                private fun markImeTextMutation(reason: String) {
                    lastCollapsedSelection = -1
                    ignoreSelectionUntilUptimeMillis =
                        SystemClock.uptimeMillis() + IME_TEXT_INPUT_SELECTION_SUPPRESS_MS
                    imeTextMutationHandler(reason)
                }
            }
        }

        override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
            return startHostActionModeIfAllowed {
                super.startActionMode(callback)
            }
        }

        override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
            return startHostActionModeIfAllowed {
                super.startActionMode(callback, type)
            }
        }

        private fun startHostActionModeIfAllowed(block: () -> ActionMode?): ActionMode? {
            if (allowHostActionModeStart() && !suppressHostActionModeStart()) {
                return block()
            }
            onSuppressedActionModeStart()
            return null
        }
    }

    private fun invokeAce(method: String, args: String = "") {
        evaluateOrQueue("window.AutoJsAce && window.AutoJsAce.$method($args);")
    }

    private fun invokeAceBoolean(
        method: String,
        args: String = "",
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        var completed = false
        val timeout = Runnable {
            if (completed) return@Runnable
            completed = true
            eventHistory.record("ace_boolean_ack_timeout", "method=$method")
            onComplete?.invoke(false)
        }
        mainHandler.postDelayed(timeout, BOOLEAN_ACK_TIMEOUT_MS)
        val callback = ValueCallback<String> { result ->
            if (completed) return@ValueCallback
            completed = true
            mainHandler.removeCallbacks(timeout)
            onComplete?.invoke(result == "true")
        }
        evaluateOrQueue(
            "!!(window.AutoJsAce && window.AutoJsAce.$method($args))",
            callback,
        )
    }

    private fun evaluateOrQueue(script: String, callback: ValueCallback<String>? = null) {
        if (!isReady) {
            pendingScripts.add(PendingScript(script, callback))
            return
        }
        evaluate(script, callback)
    }

    private fun evaluate(script: String, callback: ValueCallback<String>? = null) {
        postToMain {
            if (destroyed) {
                callback?.onReceiveValue(null)
                return@postToMain
            }
            runCatching {
                webView.evaluateJavascript(script, callback)
            }.onFailure {
                val message = "ACE JavaScript evaluation failed: ${it.message.orEmpty()}"
                val retryableDuringIme = healthMonitor.isHeartbeatSuspended() || imeController.isTransitioning
                eventHistory.record(
                    "evaluateJavascript_error",
                    "retryable=$retryableDuringIme,message=$message",
                )
                if (retryableDuringIme) {
                    reportFailure(
                        AceFailure(
                            type = AceFailureType.EVALUATE_JAVASCRIPT_ERROR,
                            message = message,
                            fatal = false,
                        ),
                        "evaluate_retryable",
                    )
                    handleError(message)
                } else {
                    reportFatalFailure(AceFailureType.EVALUATE_JAVASCRIPT_ERROR, message)
                }
                callback?.onReceiveValue(null)
            }
        }
    }

    private fun evaluateHeartbeat() {
        eventHistory.record("heartbeat_check")
        evaluate("!!(window.AutoJsAce && window.AutoJsAce.isReady && window.AutoJsAce.isReady())") {
            val ok = it == "true"
            eventHistory.record("heartbeat_result", "ok=$ok")
            healthMonitor.recordHeartbeatResult(ok)
        }
    }

    private fun flushPendingScripts() {
        while (pendingScripts.isNotEmpty()) {
            val pending = pendingScripts.removeFirst()
            evaluate(pending.script, pending.callback)
        }
    }

    private fun updateState(stateJson: String?, acceptTextSnapshot: Boolean = true): AceEditorState {
        val parsedState = parseState(stateJson)
        if (parsedState.textIncluded && acceptTextSnapshot) {
            resetTextMirror(parsedState.text)
        } else {
            textSnapshot = textMirror.text
        }
        val selectionStart = parsedState.selection.startOffset.coerceIn(0, textSnapshot.length)
        val selectionEnd = parsedState.selection.endOffset.coerceIn(0, textSnapshot.length)
        val selectedText = textSnapshot.substring(
            minOf(selectionStart, selectionEnd),
            maxOf(selectionStart, selectionEnd),
        )
        val state = parsedState.copy(
            text = textSnapshot,
            cursor = parsedState.cursor.copy(lineText = lineTextAt(parsedState.cursor.line)),
            selectedText = selectedText,
        )
        dirty = state.dirty
        canUndo = state.canUndo
        canRedo = state.canRedo
        lineCountSnapshot = state.lineCount
        cursorLine = state.cursor.line
        cursorColumn = state.cursor.column
        cursorLineText = state.cursor.lineText
        breakpointsSnapshot = state.breakpoints
        selectionSnapshot = state.selection
        selectedTextSnapshot = state.selectedText
        completionPopupOpen = state.completionPopupOpen
        firstVisibleLine = state.firstVisibleLine
        firstVisibleColumn = state.firstVisibleColumn
        return state
    }

    private fun parseState(stateJson: String?): AceEditorState {
        if (stateJson.isNullOrBlank()) {
            return currentState()
        }
        return runCatching {
            val obj = JSONObject(stateJson)
            val textIncluded = obj.has("text")
            val cursorObj = obj.optJSONObject("cursor")
            val selectionObj = obj.optJSONObject("selection")
            val scrollObj = obj.optJSONObject("scroll")
            val breakpointsArray = obj.optJSONArray("breakpoints") ?: JSONArray()
            val breakpoints = buildList {
                for (i in 0 until breakpointsArray.length()) {
                    add(breakpointsArray.optInt(i))
                }
            }
            AceEditorState(
                text = if (textIncluded) obj.optString("text", textMirror.text) else textMirror.text,
                dirty = obj.optBoolean("dirty", dirty),
                canUndo = obj.optBoolean("canUndo", canUndo),
                canRedo = obj.optBoolean("canRedo", canRedo),
                lineCount = obj.optInt("lineCount", lineCountSnapshot).coerceAtLeast(1),
                cursor = AceCursor(
                    line = cursorObj?.optInt("row", cursorLine) ?: cursorLine,
                    column = cursorObj?.optInt("column", cursorColumn) ?: cursorColumn,
                    lineText = cursorObj?.optString("lineText", cursorLineText) ?: cursorLineText,
                ),
                selection = AceSelection(
                    startOffset = selectionObj?.optInt("startOffset", selectionSnapshot.startOffset) ?: selectionSnapshot.startOffset,
                    endOffset = selectionObj?.optInt("endOffset", selectionSnapshot.endOffset) ?: selectionSnapshot.endOffset,
                    startLine = selectionObj?.optInt("startLine", selectionSnapshot.startLine) ?: selectionSnapshot.startLine,
                    startColumn = selectionObj?.optInt("startColumn", selectionSnapshot.startColumn) ?: selectionSnapshot.startColumn,
                    endLine = selectionObj?.optInt("endLine", selectionSnapshot.endLine) ?: selectionSnapshot.endLine,
                    endColumn = selectionObj?.optInt("endColumn", selectionSnapshot.endColumn) ?: selectionSnapshot.endColumn,
                ),
                selectedText = obj.optString("selectedText", selectedTextSnapshot),
                completionPopupOpen = obj.optBoolean("completionPopupOpen", completionPopupOpen),
                breakpoints = breakpoints,
                firstVisibleLine = scrollObj?.optInt("firstVisibleLine", firstVisibleLine) ?: firstVisibleLine,
                firstVisibleColumn = scrollObj?.optInt("firstVisibleColumn", firstVisibleColumn) ?: firstVisibleColumn,
                textIncluded = textIncluded,
            )
        }.getOrElse {
            handleError("Invalid ACE state: ${it.message}")
            currentState()
        }
    }

    private fun currentState(): AceEditorState {
        return AceEditorState(
            text = textMirror.text,
            dirty = dirty,
            canUndo = canUndo,
            canRedo = canRedo,
            lineCount = lineCountSnapshot,
            cursor = AceCursor(cursorLine, cursorColumn, cursorLineText),
            selection = selectionSnapshot,
            selectedText = selectedTextSnapshot,
            completionPopupOpen = completionPopupOpen,
            breakpoints = breakpointsSnapshot,
            firstVisibleLine = firstVisibleLine,
            firstVisibleColumn = firstVisibleColumn,
        )
    }

    private fun resetTextMirror(text: String) {
        textMirror.reset(text)
        textSnapshot = text
    }

    private fun scheduleTextMirrorCalibration() {
        mainHandler.removeCallbacks(mirrorCalibrationRunnable)
        if (destroyed || !isReady || textMirror.length > AceTextMirror.SMALL_TEXT_CALIBRATION_LIMIT) {
            return
        }
        mainHandler.postDelayed(mirrorCalibrationRunnable, TEXT_MIRROR_CALIBRATION_DELAY_MS)
    }

    private fun calibrateTextMirrorIfSmall() {
        if (destroyed || !isReady || textMirror.length > AceTextMirror.SMALL_TEXT_CALIBRATION_LIMIT) {
            return
        }
        val requestedRevision = textMirror.revision
        getText { latestText ->
            if (!destroyed && textMirror.revision == requestedRevision &&
                latestText.length <= AceTextMirror.SMALL_TEXT_CALIBRATION_LIMIT
            ) {
                resetTextMirror(latestText)
            }
        }
    }

    private fun decodeJsStringResult(result: String?): String {
        if (result.isNullOrBlank() || result == "null" || result == "undefined") {
            return ""
        }
        return runCatching {
            JSONArray("[$result]").optString(0)
        }.getOrElse { result.trim('"') }
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun quote(value: String): String = JSONObject.quote(value)

    private fun resizeReasonFromPayload(payloadJson: String?, fallback: String?): String? {
        if (payloadJson.isNullOrBlank()) {
            return fallback
        }
        return runCatching {
            JSONObject(payloadJson).optString("reason").ifBlank { fallback }
        }.getOrElse { fallback }
    }

    private fun selectionForOffsets(start: Int, end: Int): AceSelection {
        val startPosition = positionForOffset(start)
        val endPosition = positionForOffset(end)
        return AceSelection(
            startOffset = start,
            endOffset = end,
            startLine = startPosition.first,
            startColumn = startPosition.second,
            endLine = endPosition.first,
            endColumn = endPosition.second,
        )
    }

    private fun updateCursorSnapshotForJump(line: Int, column: Int): Pair<Int, Int> {
        val requestedLine = line.coerceAtLeast(0)
        val requestedColumn = column.coerceAtLeast(0)
        if (textSnapshot.isEmpty()) {
            cursorLine = requestedLine
            cursorColumn = requestedColumn
            cursorLineText = ""
            selectionSnapshot = AceSelection(
                startOffset = 0,
                endOffset = 0,
                startLine = requestedLine,
                startColumn = requestedColumn,
                endLine = requestedLine,
                endColumn = requestedColumn,
            )
            selectedTextSnapshot = ""
            return requestedLine to requestedColumn
        }
        val offset = offsetForPosition(requestedLine, requestedColumn)
        val (safeLine, safeColumn) = positionForOffset(offset)
        cursorLine = safeLine
        cursorColumn = safeColumn
        cursorLineText = lineTextAt(safeLine)
        selectionSnapshot = selectionForOffsets(offset, offset)
        selectedTextSnapshot = ""
        return safeLine to safeColumn
    }

    private fun offsetForPosition(line: Int, column: Int): Int {
        val targetLine = line.coerceAtLeast(0)
        var currentLine = 0
        var lineStart = 0
        while (currentLine < targetLine && lineStart < textSnapshot.length) {
            val nextLine = textSnapshot.indexOf('\n', lineStart)
            if (nextLine < 0) {
                return textSnapshot.length
            }
            lineStart = nextLine + 1
            currentLine++
        }
        val lineEnd = textSnapshot.indexOf('\n', lineStart).let { index ->
            if (index < 0) textSnapshot.length else index
        }
        return (lineStart + column.coerceAtLeast(0)).coerceAtMost(lineEnd)
    }

    private fun lineTextAt(line: Int): String {
        if (textSnapshot.isEmpty()) {
            return ""
        }
        val lineStart = offsetForPosition(line, 0)
        val lineEnd = textSnapshot.indexOf('\n', lineStart).let { index ->
            if (index < 0) textSnapshot.length else index
        }
        return textSnapshot.substring(lineStart, lineEnd)
    }

    private fun positionForOffset(offset: Int): Pair<Int, Int> {
        val safeOffset = offset.coerceIn(0, textSnapshot.length)
        var line = 0
        var lineStart = 0
        for (i in 0 until safeOffset) {
            if (textSnapshot[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return line to (safeOffset - lineStart)
    }

    private fun isAllowedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        return url == "about:blank" ||
            url.startsWith(ASSET_URL_PREFIX) ||
            url.startsWith(AceFontDescriptor.VIRTUAL_FONT_URL_PREFIX)
    }

    private fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun emptyOptionalResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            "image/x-icon",
            null,
            204,
            "No Content",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun interceptResource(url: String?): WebResourceResponse? {
        if (AceOptionalWebResourceRoute.matches(url)) {
            postToMain { eventHistory.record("optional_resource_ignored", "favicon") }
            return emptyOptionalResourceResponse()
        }
        if (!isAllowedUrl(url)) {
            return emptyBlockedResponse().also { reportBlockedUrl("resource", url) }
        }
        if (url == "about:blank") return null
        return runCatching { assetLoader.shouldInterceptRequest(Uri.parse(url)) }
            .getOrNull()
            ?: emptyBlockedResponse()
    }

    private fun virtualFontResponse(route: String): WebResourceResponse {
        val parsed = AceInstalledFontRoute.parse(route)
        val opened = parsed?.let {
            fontManager.openInstalledFont(it.fontId, it.sha256)
        }
        if (opened == null) {
            return WebResourceResponse(
                "font/woff2",
                null,
                404,
                "Not Found",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream(ByteArray(0)),
            )
        }
        return WebResourceResponse(
            "font/woff2",
            null,
            200,
            "OK",
            mapOf(
                "Cache-Control" to "private, max-age=31536000, immutable",
                "Content-Length" to opened.sizeBytes.toString(),
            ),
            opened.inputStream,
        )
    }

    private fun reportBlockedUrl(kind: String, url: String?) {
        reportFatalFailure(
            AceFailureType.SECURITY_BLOCKED_URL,
            "Blocked ACE $kind: ${url.orEmpty()}",
            url,
        )
    }

    private inner class WhitelistWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return shouldBlockUrl(request.url.toString())
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return shouldBlockUrl(url)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return interceptResource(request.url.toString())
        }

        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            return interceptResource(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!isAllowedUrl(url)) {
                reportBlockedUrl("page", url)
            } else {
                eventHistory.record("page_loaded", url)
                healthMonitor.markPageLoaded()
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                reportFatalFailure(
                    AceFailureType.PAGE_LOAD_ERROR,
                    "ACE page error: ${error.description}",
                    request.url.toString(),
                )
            }
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            reportFatalFailure(
                AceFailureType.PAGE_LOAD_ERROR,
                "ACE page error: $description",
                failingUrl,
            )
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail != null) {
                eventHistory.record("renderer_gone", "didCrash=${detail.didCrash()}")
                reportFatalFailure(
                    AceFailureType.RENDER_PROCESS_GONE,
                    "WebView renderer process gone. didCrash=${detail.didCrash()}",
                )
            } else {
                eventHistory.record("renderer_gone")
                reportFatalFailure(
                    AceFailureType.RENDER_PROCESS_GONE,
                    "WebView renderer process gone",
                )
            }
            return true
        }

        private fun shouldBlockUrl(url: String?): Boolean {
            val blocked = !isAllowedUrl(url)
            if (blocked) {
                reportBlockedUrl("navigation", url)
            }
            return blocked
        }
    }

    private inner class AceWebChromeClient : WebChromeClient() {

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                val message = "ACE console: ${consoleMessage.message()}"
                val fatal = shouldTreatConsoleErrorAsFatal(consoleMessage)
                eventHistory.record(
                    "console_error",
                    "fatal=$fatal,source=${consoleMessage.sourceId()},message=${consoleMessage.message()}",
                )
                if (fatal) {
                    reportFatalFailure(
                        AceFailureType.CONSOLE_FATAL,
                        message,
                        consoleMessage.sourceId(),
                    )
                } else {
                    handleError(message)
                }
            }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.deny()
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            return true
        }

        private fun shouldTreatConsoleErrorAsFatal(consoleMessage: ConsoleMessage): Boolean {
            return AceJsErrorClassifier.shouldTreatConsoleErrorAsFatal(
                isReady = isReady,
                source = consoleMessage.sourceId(),
                message = consoleMessage.message(),
            )
        }
    }

    private inner class ChangeTextSizeScaleListener : SimpleOnScaleGestureListener() {

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            pinchGestureActive = true
            markPinchTouchSuppressed()
            lastTextSizeScaleFactor = 1.0
            cancelAceTouchInteraction("pinch_zoom_begin")
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentFactor = floor((detector.scaleFactor * 10).toDouble()) / 10
            if (currentFactor > 0 && currentFactor != lastTextSizeScaleFactor) {
                changeBaseTextSizeBy(
                    delta = if (currentFactor > lastTextSizeScaleFactor) 1 else -1,
                    focusX = detector.focusX,
                    focusY = detector.focusY,
                )
                lastTextSizeScaleFactor = currentFactor
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            pinchGestureActive = false
            markPinchTouchSuppressed()
            lastTextSizeScaleFactor = 1.0
            onTextSizeSpChangedByGesture?.invoke(baseTextSizeSp, true)
            cancelAceTouchInteraction("pinch_zoom_end")
            super.onScaleEnd(detector)
        }
    }

    private inner class ScaleViewScaleListener : SimpleOnScaleGestureListener() {

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            pinchGestureActive = true
            markPinchTouchSuppressed()
            scaleViewGestureChanged = false
            viewZoomScale = 1f
            lastPinchFocusX = detector.focusX
            lastPinchFocusY = detector.focusY
            cancelAceTouchInteraction("pinch_zoom_begin")
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleViewBy(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            pinchGestureActive = false
            markPinchTouchSuppressed()
            commitScaleViewTextSizeGesture(lastPinchFocusX, lastPinchFocusY)
            cancelAceTouchInteraction("pinch_zoom_end")
            super.onScaleEnd(detector)
        }
    }

    private enum class PinchZoomStrategy {
        CHANGE_TEXT_SIZE,
        SCALE_VIEW,
        DISABLED,
        ;

        val bridgeName: String
            get() = when (this) {
                CHANGE_TEXT_SIZE -> "change_text_size"
                SCALE_VIEW -> "change_text_size"
                DISABLED -> "disabled"
            }

        companion object {
            fun fromChangeTextSizeEnabled(enabled: Boolean): PinchZoomStrategy =
                if (enabled) CHANGE_TEXT_SIZE else DISABLED
        }
    }

    data class AceEditorState(
        val text: String,
        val dirty: Boolean,
        val canUndo: Boolean,
        val canRedo: Boolean,
        val lineCount: Int,
        val cursor: AceCursor,
        val selection: AceSelection,
        val selectedText: String,
        val completionPopupOpen: Boolean = false,
        val breakpoints: List<Int>,
        val firstVisibleLine: Int,
        val firstVisibleColumn: Int,
        val textIncluded: Boolean = true,
    )

    data class AceCursor(
        val line: Int,
        val column: Int,
        val lineText: String,
    )

    data class AceSelection(
        val startOffset: Int,
        val endOffset: Int,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
    ) {
        companion object {
            val EMPTY = AceSelection(
                startOffset = 0,
                endOffset = 0,
                startLine = 0,
                startColumn = 0,
                endLine = 0,
                endColumn = 0,
            )
        }
    }

    interface Listener {
        fun onReady(state: AceEditorState) = Unit
        fun onStateChanged(state: AceEditorState) = Unit
        fun onTextChanged(text: String, state: AceEditorState) = Unit
        fun onCursorChanged(lineText: String, line: Int, column: Int, state: AceEditorState) = Unit
        fun onBreakpointChanged(line: Int, enabled: Boolean, state: AceEditorState) = Unit
        fun onHistoryOperation(token: String, undo: Boolean) = Unit
        fun onSelectionAction(action: SelectionAction) = Unit
        fun onEvent(name: String, payloadJson: String?) = Unit
        fun onError(message: String) = Unit
        fun onFailure(failure: AceFailure) = Unit
    }

    enum class SelectionAction {
        Copy,
        Paste,
        SelectAll,
        DeleteLine,
        CopyLine,
    }

    private data class PendingScript(
        val script: String,
        val callback: ValueCallback<String>?,
    )

    private data class SelectionActionModeRequest(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
        val hasSelection: Boolean,
        val selectAll: Boolean,
    )

    companion object {
        private const val JS_BRIDGE_NAME = "autojs"
        private const val APP_ASSET_PATH = "/assets/"
        private const val VIRTUAL_FONT_PATH = "/autojs6-fonts/"
        private const val APP_ASSET_ORIGIN = "https://appassets.androidplatform.net"
        private const val BOOLEAN_ACK_TIMEOUT_MS = 10_000L
        private const val ASSET_URL_PREFIX = "$APP_ASSET_ORIGIN${APP_ASSET_PATH}editor/ace-builds-1.4.12/"
        private const val EDITOR_URL = "${ASSET_URL_PREFIX}autojs6_editor.html"
        private const val ACE_RUNTIME_REVISION = "ace-builds-1.4.12-autojs6"
        private const val FONT_ERROR_DIAGNOSTIC_LIMIT = 1_024
        private const val TEXT_MIRROR_CALIBRATION_DELAY_MS = 500L
        private const val IME_REQUEST_COALESCE_MS = 150L
        private const val IME_TRANSITION_HEARTBEAT_SUSPEND_MS = 800L
        private const val IME_TEXT_INPUT_SELECTION_SUPPRESS_MS = 120L
        private const val RESIZE_SETTLE_DELAY_MS = 120L
        private const val IME_HEIGHT_THRESHOLD_DP = 80f
        private const val ACTION_MODE_AFTER_TOUCH_DELAY_MS = 48L
        private const val ACTION_MODE_RESTORE_AFTER_TOUCH_DELAY_MS = 120L
        private const val ACTION_MODE_POST_TOUCH_STABILIZE_MS = 2_000L
        private const val ACTION_MODE_TEXT_MUTATION_SUPPRESS_MS = 1_200L
        private const val ACTION_MODE_TOUCH_ANCHOR_MAX_AGE_MS = 2_000L
        private const val ACTION_MODE_TOUCH_ANCHOR_HEIGHT_DP = 24f
        private const val INITIAL_FOCUS_RETRY_DELAY_MS = 120L
        private const val PINCH_TOUCH_SUPPRESS_MS = 320L
        private const val PINCH_SOFT_INPUT_SUPPRESS_MS = 1_800L
        private const val PINCH_SELECTION_ACTION_MODE_SUPPRESS_MS = 1_600L
        private const val SINGLE_TOUCH_SCROLL_CANCEL_SLOP_DP = 2.5f
        private const val BRIDGE_EVENT_WINDOW_MS = 1_000L
        private const val BRIDGE_EVENT_STORM_THRESHOLD = 60
        private const val ACTION_MODE_COPY = 1
        private const val ACTION_MODE_PASTE = 2
        private const val ACTION_MODE_SELECT_ALL = 3
        private const val DEFAULT_TEXT_SIZE_SP = 14f
        private const val MIN_TEXT_SIZE_SP = 1f
        private const val MAX_TEXT_SIZE_SP = 96f
        private const val MIN_VIEW_ZOOM_SCALE = MIN_TEXT_SIZE_SP / DEFAULT_TEXT_SIZE_SP
        private const val MAX_VIEW_ZOOM_SCALE = MAX_TEXT_SIZE_SP / DEFAULT_TEXT_SIZE_SP
        private const val MIN_SCALE_VIEW_TEXT_SIZE_DELTA_SP = 0.05f

        fun hasRequiredAssets(context: Context): Boolean {
            return AceEditorAssets.hasRequired(context)
        }
    }
}
