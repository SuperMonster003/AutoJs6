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
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnostics
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnosticsSnapshot
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceRuntimeEventHistory
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailure
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailureType
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs.ui.edit.editor.ace.health.AceJsErrorClassifier
import org.autojs.autojs.ui.edit.editor.ace.health.AceRuntimeHealthMonitor
import org.autojs.autojs.ui.edit.editor.ace.lsp.AceLspServerManager
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.ArrayDeque

@SuppressLint("SetJavaScriptEnabled")
class AceCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingScripts = ArrayDeque<PendingScript>()
    private val bridge = AceBridge(this)
    private val textMirror = AceTextMirror()
    private val imeController = AceImeController(IME_REQUEST_COALESCE_MS)
    private val eventHistory = AceRuntimeEventHistory()
    private val lspServerManager = AceLspServerManager()
    private val healthMonitor = AceRuntimeHealthMonitor(mainHandler) { failure ->
        notifyFallbackRequired(failure)
    }
    private val mirrorCalibrationRunnable = Runnable {
        calibrateTextMirrorIfSmall()
    }
    private val scheduledResizeRunnable = Runnable {
        runScheduledResize()
    }
    private val imeSettleRunnable = Runnable {
        scheduleAceResize("ime_settle", RESIZE_SETTLE_DELAY_MS)
        healthMonitor.resumeHeartbeat()
    }

    val webView: WebView = WebView(context)
    private val loadingOverlay: View = View(context).apply {
        setBackgroundColor(editorBackgroundColor())
    }

    var listener: Listener? = null
    var onUserTouchInTextArea: (() -> Unit)? = null

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

    var firstVisibleLine = 0
        private set

    var firstVisibleColumn = 0
        private set

    val textMirrorRevision: Long
        get() = textMirror.revision

    private var loaded = false
    private var destroyed = false
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
    private var bridgeEventWindowStartedAtUptimeMillis = 0L
    private var bridgeEventWindowCount = 0
    private var lastBridgeEventWindowCount = 0
    private var maxBridgeEventWindowCount = 0
    private var maxBridgeEventWindowStartedAtUptimeMillis = 0L
    private var lastBridgeEventName: String? = null

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
            lspServerAvailable = lspSnapshot.serverAvailable,
            lspStartSupported = lspSnapshot.startSupported,
            lspSessionRevision = lspSnapshot.sessionRevision,
            aceFont = AceEditorFontPreferences.get().preferenceValue,
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
        configureWebView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lspServerManager.attach()
        val lspSnapshot = lspServerManager.snapshot()
        eventHistory.record("lsp_manager", "state=${lspSnapshot.state},attached=${lspSnapshot.attached}")
        loadEditor()
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

    fun setText(text: String?) {
        resetTextMirror(text.orEmpty())
        dirty = false
        invokeAce("setText", quote(textSnapshot))
    }

    fun setTextDirty(text: String?) {
        resetTextMirror(text.orEmpty())
        dirty = true
        invokeAce("setTextDirty", quote(textSnapshot))
    }

    fun markClean() {
        dirty = false
        invokeAce("markClean")
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
        invokeAce("insert", quote(text.orEmpty()))
    }

    fun insert(line: Int, text: String?) {
        invokeAce("insertAtLine", "${line.coerceAtLeast(0)}, ${quote(text.orEmpty())}")
    }

    fun selectRange(startOffset: Int, endOffset: Int) {
        val safeStart = startOffset.coerceIn(0, textSnapshot.length)
        val safeEnd = endOffset.coerceIn(0, textSnapshot.length)
        selectionSnapshot = selectionForOffsets(safeStart, safeEnd)
        selectedTextSnapshot = textSnapshot.substring(minOf(safeStart, safeEnd), maxOf(safeStart, safeEnd))
        invokeAce("selectRange", "$safeStart, $safeEnd")
    }

    fun jumpTo(line: Int, column: Int = 0) {
        invokeAce("jumpTo", "${line.coerceAtLeast(0)}, ${column.coerceAtLeast(0)}")
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
        invokeAce("deleteLine")
    }

    fun clear() {
        resetTextMirror("")
        selectionSnapshot = AceSelection.EMPTY
        selectedTextSnapshot = ""
        invokeAce("clear")
    }

    fun toggleComment() {
        invokeAce("toggleComment")
    }

    fun beautify() {
        invokeAce("beautify")
    }

    fun replaceSelection(text: String?) {
        invokeAce("replaceSelection", quote(text.orEmpty()))
    }

    fun replaceStoredSelection() {
        invokeAce("replaceStoredSelection")
    }

    fun setReplacement(text: String?) {
        invokeAce("setReplacement", quote(text.orEmpty()))
    }

    fun replaceAll(query: String, replacement: String?, usingRegex: Boolean) {
        invokeAce("replaceAll", "${quote(query)}, ${quote(replacement.orEmpty())}, $usingRegex")
    }

    fun undo() {
        invokeAce("undo")
    }

    fun redo() {
        invokeAce("redo")
    }

    fun setReadOnly(readOnly: Boolean) {
        invokeAce("setReadOnly", readOnly.toString())
    }

    fun setTheme(theme: String) {
        invokeAce("setTheme", quote(theme))
    }

    fun setTextSizeSp(size: Float) {
        invokeAce("setFontSizeSp", size.coerceIn(8f, 40f).toString())
    }

    fun setFontFamily(fontFamily: String) {
        invokeAce("setFontFamily", quote(fontFamily))
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
        evaluateOrQueue("window.AutoJsAce && window.AutoJsAce.jumpTo($cursorLine, $cursorColumn);")
    }

    fun destroy() {
        destroyed = true
        eventHistory.record("destroy", "AceCodeEditor.destroy")
        finishSelectionActionMode()
        lspServerManager.detach()
        healthMonitor.markDestroyed()
        mainHandler.removeCallbacks(mirrorCalibrationRunnable)
        mainHandler.removeCallbacks(scheduledResizeRunnable)
        mainHandler.removeCallbacks(imeSettleRunnable)
        pendingScripts.clear()
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
            recordBridgeEvent("notifyEvent:$name")
            healthMonitor.markStateEvent()
            eventHistory.record("js_event", "name=$name")
            when (name) {
                "fatalError",
                "lspError",
                "completionError",
                "recoverableError",
                    -> reportClassifiedJsFailure(name, payloadJson)
                "firstPaint" -> handleFirstPaint()
                "resizeDone" -> handleResizeDone(payloadJson)
                "changeDelta" -> handleChangeDelta(payloadJson)
            }
            listener?.onEvent(name, payloadJson)
        }
    }

    internal fun handleReady(stateJson: String?) {
        postToMain {
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
            recordBridgeEvent("notifyStateChanged")
            healthMonitor.markStateEvent()
            val state = updateState(stateJson)
            listener?.onStateChanged(state)
        }
    }

    internal fun handleTextChanged(stateJson: String?) {
        postToMain {
            recordBridgeEvent("notifyTextChanged")
            healthMonitor.markChangeEvent()
            val state = updateState(stateJson)
            listener?.onTextChanged(state.text, state)
        }
    }

    internal fun handleCursorChanged(lineText: String, line: Int, column: Int, stateJson: String?) {
        postToMain {
            recordBridgeEvent("notifyCursorChanged")
            healthMonitor.markStateEvent()
            val state = updateState(stateJson).copy(
                cursor = AceCursor(line = line, column = column, lineText = lineText),
            ).also {
                cursorLine = it.cursor.line
                cursorColumn = it.cursor.column
                cursorLineText = it.cursor.lineText
            }
            listener?.onCursorChanged(lineText, line, column, state)
        }
    }

    internal fun handleBreakpointChanged(line: Int, enabled: Boolean, stateJson: String?) {
        postToMain {
            recordBridgeEvent("notifyBreakpointChanged")
            healthMonitor.markStateEvent()
            val state = updateState(stateJson)
            listener?.onBreakpointChanged(line, enabled, state)
        }
    }

    internal fun handleError(message: String) {
        postToMain {
            recordBridgeEvent("notifyError")
            listener?.onError(message.ifBlank { "Unknown ACE editor error" })
        }
    }

    private fun notifyFallbackRequired(failure: AceFailure) {
        postToMain {
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

    internal fun bridgeLspOptions(): String {
        return lspServerManager.bridgeOptionsJson()
    }

    internal fun showSoftInput() {
        postToMain {
            val now = SystemClock.uptimeMillis()
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
            updateSelectionActionModeRect(left, top, right, bottom)
            if (!hasSelection || selectedTextSnapshot.isEmpty()) {
                finishSelectionActionMode()
                return@postToMain
            }
            actionModeActive = true
            lastActionModeAtUptimeMillis = SystemClock.uptimeMillis()
            healthMonitor.markStateEvent()
            if (selectionActionMode == null) {
                selectionActionMode = startSelectionActionMode()
                if (selectionActionMode == null) {
                    actionModeActive = false
                    eventHistory.record("action_mode_unavailable", "selectionLength=${selectedTextSnapshot.length}")
                    return@postToMain
                }
            } else {
                selectionActionMode?.invalidate()
            }
            scheduleAceResize(
                "action_mode_started:${left.toFloat()},${top.toFloat()},${right.toFloat()},${bottom.toFloat()},$hasSelection,$selectAll",
                RESIZE_SETTLE_DELAY_MS,
            )
        }
    }

    internal fun finishActionMode() {
        postToMain {
            finishSelectionActionMode()
        }
    }

    internal fun performLongPressFeedback() {
        postToMain {
            webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun startSelectionActionMode(): ActionMode? {
        val callback = createSelectionActionModeCallback()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            webView.startActionMode(callback)
        }
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
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.findItem(ACTION_MODE_COPY)?.isEnabled = selectedTextSnapshot.isNotEmpty()
                menu.findItem(ACTION_MODE_PASTE)?.isEnabled = ClipboardUtils.getClipOrEmpty(context).isNotEmpty()
                menu.findItem(ACTION_MODE_SELECT_ALL)?.isEnabled = textSnapshot.isNotEmpty()
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    ACTION_MODE_COPY -> {
                        copySelectedTextToClipboard()
                        mode.finish()
                        true
                    }
                    ACTION_MODE_PASTE -> {
                        pasteClipboardIntoSelection()
                        mode.finish()
                        true
                    }
                    ACTION_MODE_SELECT_ALL -> {
                        selectRange(0, textSnapshot.length)
                        mode.invalidate()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                markSelectionActionModeFinished(mode)
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
        val safeLeft = (minOf(left, right) * scale).toInt().coerceIn(0, maxRight - 1)
        val safeTop = (minOf(top, bottom) * scale).toInt().coerceIn(0, maxBottom - 1)
        val safeRight = (maxOf(left, right) * scale).toInt().coerceIn(safeLeft + 1, maxRight)
        val safeBottom = (maxOf(top, bottom) * scale).toInt().coerceIn(safeTop + 1, maxBottom)
        selectionActionModeRect.set(safeLeft, safeTop, safeRight, safeBottom)
    }

    private fun copySelectedTextToClipboard() {
        if (selectedTextSnapshot.isEmpty()) {
            return
        }
        ClipboardUtils.setClip(context, selectedTextSnapshot)
        eventHistory.record("action_mode_copy", "length=${selectedTextSnapshot.length}")
    }

    private fun pasteClipboardIntoSelection() {
        val clip = ClipboardUtils.getClipOrEmpty(context).toString()
        if (clip.isEmpty()) {
            return
        }
        replaceSelection(clip)
        eventHistory.record("action_mode_paste", "length=${clip.length}")
    }

    private fun finishSelectionActionMode() {
        val mode = selectionActionMode
        if (mode != null) {
            mode.finish()
            return
        }
        markSelectionActionModeFinished()
    }

    private fun markSelectionActionModeFinished(mode: ActionMode? = null) {
        if (mode == null || selectionActionMode === mode) {
            selectionActionMode = null
        }
        if (!actionModeActive) {
            return
        }
        actionModeActive = false
        lastActionModeAtUptimeMillis = SystemClock.uptimeMillis()
        healthMonitor.markStateEvent()
        eventHistory.record("action_mode_finished")
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
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE,
                    -> {
                    userTouching = true
                    onUserTouchInTextArea?.invoke()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_UP,
                    -> userTouching = false
            }
            false
        }

        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = false
            databaseEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.addJavascriptInterface(bridge, JS_BRIDGE_NAME)
        webView.webViewClient = WhitelistWebViewClient()
        webView.webChromeClient = AceWebChromeClient()
    }

    private fun invokeAce(method: String, args: String = "") {
        evaluateOrQueue("window.AutoJsAce && window.AutoJsAce.$method($args);")
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
        val state = parsedState.copy(text = textSnapshot)
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
        getText { latestText ->
            if (latestText.length <= AceTextMirror.SMALL_TEXT_CALIBRATION_LIMIT) {
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
        return url == "about:blank" || url.startsWith(ASSET_URL_PREFIX)
    }

    private fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
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
            return if (isAllowedUrl(request.url.toString())) null else emptyBlockedResponse().also {
                reportBlockedUrl("resource", request.url.toString())
            }
        }

        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            return if (isAllowedUrl(url)) null else emptyBlockedResponse().also {
                reportBlockedUrl("resource", url)
            }
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

    data class AceEditorState(
        val text: String,
        val dirty: Boolean,
        val canUndo: Boolean,
        val canRedo: Boolean,
        val lineCount: Int,
        val cursor: AceCursor,
        val selection: AceSelection,
        val selectedText: String,
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
        fun onEvent(name: String, payloadJson: String?) = Unit
        fun onError(message: String) = Unit
        fun onFailure(failure: AceFailure) = Unit
    }

    private data class PendingScript(
        val script: String,
        val callback: ValueCallback<String>?,
    )

    companion object {
        private const val JS_BRIDGE_NAME = "autojs"
        private const val ASSET_URL_PREFIX = "file:///android_asset/editor/ace-builds-1.4.12/"
        private const val EDITOR_URL = "${ASSET_URL_PREFIX}autojs6_editor.html"
        private const val EDITOR_ASSET_PATH = "editor/ace-builds-1.4.12/autojs6_editor.html"
        private const val ACE_RUNTIME_REVISION = "ace-builds-1.4.12-autojs6"
        private const val TEXT_MIRROR_CALIBRATION_DELAY_MS = 500L
        private const val IME_REQUEST_COALESCE_MS = 150L
        private const val IME_TRANSITION_HEARTBEAT_SUSPEND_MS = 800L
        private const val RESIZE_SETTLE_DELAY_MS = 120L
        private const val IME_HEIGHT_THRESHOLD_DP = 80f
        private const val BRIDGE_EVENT_WINDOW_MS = 1_000L
        private const val BRIDGE_EVENT_STORM_THRESHOLD = 60
        private const val ACTION_MODE_COPY = 1
        private const val ACTION_MODE_PASTE = 2
        private const val ACTION_MODE_SELECT_ALL = 3

        fun hasRequiredAssets(context: Context): Boolean {
            if (AceEditorTestHooks.shouldForceAssetMissing()) {
                return false
            }
            return runCatching {
                context.assets.open(EDITOR_ASSET_PATH).close()
            }.isSuccess
        }
    }
}
