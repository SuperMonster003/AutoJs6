@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package org.autojs.autojs.ui.edit.editor.ace

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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

    val webView: WebView = WebView(context)

    var listener: Listener? = null

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

    private var loaded = false
    private var userTouching = false

    init {
        addView(webView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        configureWebView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadEditor()
    }

    fun loadEditor() {
        if (loaded) {
            return
        }
        loaded = true
        webView.loadUrl(EDITOR_URL)
    }

    fun isUserTouching(): Boolean = userTouching

    fun setText(text: String?) {
        textSnapshot = text.orEmpty()
        invokeAce("setText", quote(textSnapshot))
    }

    fun getText(callback: (String) -> Unit) {
        val fallback = quote(textSnapshot)
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

    fun jumpTo(line: Int, column: Int = 0) {
        invokeAce("jumpTo", "${line.coerceAtLeast(0)}, ${column.coerceAtLeast(0)}")
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

    fun toggleBreakpoint(line: Int) {
        invokeAce("toggleBreakpoint", line.coerceAtLeast(0).toString())
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

    fun requestEditorFocus() {
        webView.requestFocus()
        evaluateOrQueue("window.AutoJsAce && window.AutoJsAce.jumpTo($cursorLine, $cursorColumn);")
    }

    fun destroy() {
        pendingScripts.clear()
        webView.stopLoading()
        runCatching { webView.removeJavascriptInterface(JS_BRIDGE_NAME) }
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        removeView(webView)
        webView.destroy()
    }

    internal fun handleEvent(name: String, payloadJson: String?) {
        postToMain {
            listener?.onEvent(name, payloadJson)
        }
    }

    internal fun handleReady(stateJson: String?) {
        postToMain {
            val state = updateState(stateJson)
            isReady = true
            listener?.onReady(state)
            flushPendingScripts()
        }
    }

    internal fun handleStateChanged(stateJson: String?) {
        postToMain {
            val state = updateState(stateJson)
            listener?.onStateChanged(state)
        }
    }

    internal fun handleTextChanged(stateJson: String?) {
        postToMain {
            val state = updateState(stateJson)
            listener?.onTextChanged(state.text, state)
        }
    }

    internal fun handleCursorChanged(lineText: String, line: Int, column: Int, stateJson: String?) {
        postToMain {
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
            val state = updateState(stateJson)
            listener?.onBreakpointChanged(line, enabled, state)
        }
    }

    internal fun handleError(message: String) {
        postToMain {
            listener?.onError(message.ifBlank { "Unknown ACE editor error" })
        }
    }

    internal fun bridgeTheme(): String {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            "ace/theme/tomorrow_night"
        } else {
            "ace/theme/textmate"
        }
    }

    private fun configureWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isHorizontalScrollBarEnabled = true
        webView.isVerticalScrollBarEnabled = true
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE,
                    -> userTouching = true

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
            webView.evaluateJavascript(script, callback)
        }
    }

    private fun flushPendingScripts() {
        while (pendingScripts.isNotEmpty()) {
            val pending = pendingScripts.removeFirst()
            evaluate(pending.script, pending.callback)
        }
    }

    private fun updateState(stateJson: String?): AceEditorState {
        val state = parseState(stateJson)
        textSnapshot = state.text
        dirty = state.dirty
        canUndo = state.canUndo
        canRedo = state.canRedo
        lineCountSnapshot = state.lineCount
        cursorLine = state.cursor.line
        cursorColumn = state.cursor.column
        cursorLineText = state.cursor.lineText
        breakpointsSnapshot = state.breakpoints
        return state
    }

    private fun parseState(stateJson: String?): AceEditorState {
        if (stateJson.isNullOrBlank()) {
            return currentState()
        }
        return runCatching {
            val obj = JSONObject(stateJson)
            val cursorObj = obj.optJSONObject("cursor")
            val breakpointsArray = obj.optJSONArray("breakpoints") ?: JSONArray()
            val breakpoints = buildList {
                for (i in 0 until breakpointsArray.length()) {
                    add(breakpointsArray.optInt(i))
                }
            }
            AceEditorState(
                text = obj.optString("text", textSnapshot),
                dirty = obj.optBoolean("dirty", dirty),
                canUndo = obj.optBoolean("canUndo", canUndo),
                canRedo = obj.optBoolean("canRedo", canRedo),
                lineCount = obj.optInt("lineCount", lineCountSnapshot).coerceAtLeast(1),
                cursor = AceCursor(
                    line = cursorObj?.optInt("row", cursorLine) ?: cursorLine,
                    column = cursorObj?.optInt("column", cursorColumn) ?: cursorColumn,
                    lineText = cursorObj?.optString("lineText", cursorLineText) ?: cursorLineText,
                ),
                breakpoints = breakpoints,
            )
        }.getOrElse {
            handleError("Invalid ACE state: ${it.message}")
            currentState()
        }
    }

    private fun currentState(): AceEditorState {
        return AceEditorState(
            text = textSnapshot,
            dirty = dirty,
            canUndo = canUndo,
            canRedo = canRedo,
            lineCount = lineCountSnapshot,
            cursor = AceCursor(cursorLine, cursorColumn, cursorLineText),
            breakpoints = breakpointsSnapshot,
        )
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

    private fun isAllowedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        return url == "about:blank" || url.startsWith(ASSET_URL_PREFIX)
    }

    private fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
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
                handleError("Blocked ACE resource: ${request.url}")
            }
        }

        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            return if (isAllowedUrl(url)) null else emptyBlockedResponse().also {
                handleError("Blocked ACE resource: $url")
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!isAllowedUrl(url)) {
                handleError("Blocked ACE page: $url")
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                handleError("ACE page error: ${error.description}")
            }
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            handleError("ACE page error: $description")
        }

        private fun shouldBlockUrl(url: String?): Boolean {
            val blocked = !isAllowedUrl(url)
            if (blocked) {
                handleError("Blocked ACE navigation: ${url.orEmpty()}")
            }
            return blocked
        }
    }

    private inner class AceWebChromeClient : WebChromeClient() {

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                handleError("ACE console: ${consoleMessage.message()}")
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
    }

    data class AceEditorState(
        val text: String,
        val dirty: Boolean,
        val canUndo: Boolean,
        val canRedo: Boolean,
        val lineCount: Int,
        val cursor: AceCursor,
        val breakpoints: List<Int>,
    )

    data class AceCursor(
        val line: Int,
        val column: Int,
        val lineText: String,
    )

    interface Listener {
        fun onReady(state: AceEditorState) = Unit
        fun onStateChanged(state: AceEditorState) = Unit
        fun onTextChanged(text: String, state: AceEditorState) = Unit
        fun onCursorChanged(lineText: String, line: Int, column: Int, state: AceEditorState) = Unit
        fun onBreakpointChanged(line: Int, enabled: Boolean, state: AceEditorState) = Unit
        fun onEvent(name: String, payloadJson: String?) = Unit
        fun onError(message: String) = Unit
    }

    private data class PendingScript(
        val script: String,
        val callback: ValueCallback<String>?,
    )

    companion object {
        private const val JS_BRIDGE_NAME = "autojs"
        private const val ASSET_URL_PREFIX = "file:///android_asset/editor/ace-builds-1.4.12/"
        private const val EDITOR_URL = "${ASSET_URL_PREFIX}autojs6_editor.html"
    }
}
