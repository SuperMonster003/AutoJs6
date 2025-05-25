package org.autojs.autojs.core.ui.widget

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.webkit.ClientCertRequest
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.AnyThread
import androidx.annotation.Keep
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.app.OnActivityResultDelegate
import org.autojs.autojs.event.CoroutineSyncEventHost
import org.autojs.autojs.event.CoroutineSyncEventHost.Companion.Event
import org.autojs.autojs.event.EventResult
import org.autojs.autojs.event.IEventEmitter
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.api.Resolvable
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Created by SuperMonster003 on May 23, 2025.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on May 23, 2025.
@Keep
abstract class EventWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : WebView(context, attrs, defStyleAttr, defStyleRes), IEventEmitter {

    private val bridge = AutoJs(this)

    private val pendingEvents = Collections.synchronizedList(mutableListOf<PendingJsEvent>())

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val syncEventHost = CoroutineSyncEventHost(coroutineScope) { event ->
        when (event.sync) {
            true -> syncWebViewEventCallback?.onSyncWebViewEvent(event)
            else -> webViewEventCallback?.onWebViewEvent(event)
        } != null
    }.apply { onError = ::onError }

    @Volatile
    var webViewEventCallback: WebViewEventCallback? = null

    @Volatile
    var syncWebViewEventCallback: SyncViewEventCallback? = null

    @Volatile
    var javascriptEventCallback: JavaScriptEventCallback? = null
        private set

    init {
        initSettings()
        setWebViewClient(InternalClient())
        setWebChromeClient(InternalChromeClient())
        addJavascriptInterface(bridge, "\$autojs")
    }

    private fun initSettings() = settings.run {
        @SuppressLint("SetJavaScriptEnabled")
        javaScriptEnabled = true

        javaScriptCanOpenWindowsAutomatically = true
        domStorageEnabled = true
        builtInZoomControls = false
        allowFileAccess = true

        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = true

        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = true
    }

    @AnyThread
    @ScriptInterface
    fun evalInternal(code: String): Resolvable {
        val promise = newPromise()
        RhinoUtils.dispatchToMainThread {
            evaluateJavascript(code) { promise.resolve(it) }
        }
        return promise
    }

    override fun emitEvent(event: String, vararg args: Any?): EventResult {
        return emitInScope(event, coroutineScope, *args)
    }

    fun emitInScope(eventName: String, scope: CoroutineScope, vararg args: Any?): EventResult {
        return syncEventHost.emitInScope(eventName, scope, *args)
    }

    private inner class InternalClient : WebViewClient() {

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (emitEvent("update_visited_history", view, url, isReload).callSuper) {
                super.doUpdateVisitedHistory(view, url, isReload)
            }
        }

        override fun onFormResubmission(view: WebView, dontResend: Message?, resend: Message?) {
            if (emitEvent("form_resubmission", view, dontResend, resend).callSuper) {
                super.onFormResubmission(view, dontResend, resend)
            }
        }

        override fun onLoadResource(view: WebView, url: String?) {
            if (emitEvent("load_resource", view, url).callSuper) {
                super.onLoadResource(view, url)
            }
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            if (emitEvent("page_commit_visible", view, url).callSuper) {
                super.onPageCommitVisible(view, url)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (emitEvent("page_finished", view, url).callSuper) {
                super.onPageFinished(view, url)
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (emitEvent("page_started", view, url, favicon).callSuper) {
                super.onPageStarted(view, url, favicon)
            }
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest?) {
            if (emitEvent("received_client_cert_request", view, request).callSuper) {
                super.onReceivedClientCertRequest(view, request)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest?, error: WebResourceError?) {
            if (emitEvent("received_error", view, request, error).callSuper) {
                super.onReceivedError(view, request, error)
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler?, host: String?, realm: String?) {
            if (emitEvent("received_http_auth_request", view, handler, host, realm).callSuper) {
                super.onReceivedHttpAuthRequest(view, handler, host, realm)
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            if (emitEvent("received_http_error", view, request, errorResponse).callSuper) {
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }

        override fun onReceivedLoginRequest(view: WebView, realm: String?, account: String?, args: String?) {
            if (emitEvent("received_login_request", view, realm, account, args).callSuper) {
                super.onReceivedLoginRequest(view, realm, account, args)
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler?, error: SslError?) {
            if (emitEvent("received_ssl_error", view, handler, error).callSuper) {
                super.onReceivedSslError(view, handler, error)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            val eventResult = emitEvent("render_process_gone", view, detail)
            val superResult = super.onRenderProcessGone(view, detail)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
            if (emitEvent("safe_browsing_hit", view, request, threatType, callback).callSuper) {
                super.onSafeBrowsingHit(view, request, threatType, callback)
            }
        }

        override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
            if (emitEvent("scale_changed", view, oldScale, newScale).callSuper) {
                super.onScaleChanged(view, oldScale, newScale)
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onTooManyRedirects(view: WebView, cancelMsg: Message?, continueMsg: Message?) {
            if (emitEvent("too_many_redirects", view, cancelMsg, continueMsg).callSuper) {
                super.onTooManyRedirects(view, cancelMsg, continueMsg)
            }
        }

        override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent?) {
            if (emitEvent("unhandled_key_event", view, event).callSuper) {
                super.onUnhandledKeyEvent(view, event)
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
            // 本地注入 AutoJs SDK
            if (url?.toUri() == AUTOJS_SDK_URI) {
                return WebResourceResponse(
                    /* mimeType = */ "application/javascript",
                    /* encoding = */ "UTF-8",
                    /* statusCode = */ 200,
                    /* reasonPhrase = */ "OK",
                    /* responseHeaders = */ emptyMap(),
                    /* data = */ ByteArrayInputStream(AUTOJS_SDK_JS)
                )
            }
            val eventResult = emitEvent("should_intercept_request", view, url)
            val superResult = super.shouldInterceptRequest(view, url)
            return when {
                eventResult.callSuper -> superResult
                else -> eventResult.result as? WebResourceResponse? ?: superResult
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // 本地注入 AutoJs SDK
            if (request.url == AUTOJS_SDK_URI) {
                return WebResourceResponse(
                    /* mimeType = */ "application/javascript",
                    /* encoding = */ "UTF-8",
                    /* statusCode = */ 200,
                    /* reasonPhrase = */ "OK",
                    /* responseHeaders = */ emptyMap(),
                    /* data = */ ByteArrayInputStream(AUTOJS_SDK_JS)
                )
            }
            val eventResult = emitEvent("should_intercept_request", view, request)
            val superResult = super.shouldInterceptRequest(view, request)
            return when {
                eventResult.callSuper -> superResult
                else -> eventResult.result as? WebResourceResponse? ?: superResult
            }
        }

        override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
            val eventResult = emitEvent("should_override_key_event", view, event)
            val superResult = super.shouldOverrideKeyEvent(view, event)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val eventResult = emitEvent("should_override_url_loading", view, request)
            val superResult = super.shouldOverrideUrlLoading(view, request)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

    }

    private inner class InternalChromeClient : WebChromeClient() {

        override fun getDefaultVideoPoster(): Bitmap? {
            val eventResult = emitEvent("get_default_video_poster")
            val superResult = super.getDefaultVideoPoster()
            return when {
                eventResult.callSuper -> superResult
                else -> eventResult.result as? Bitmap? ?: superResult
            }
        }

        override fun getVideoLoadingProgressView(): View? {
            val eventResult = emitEvent("get_video_loading_progress_view")
            val superResult = super.getVideoLoadingProgressView()
            return when {
                eventResult.callSuper -> superResult
                else -> eventResult.result as? View? ?: superResult
            }
        }

        override fun getVisitedHistory(callback: ValueCallback<Array<out String?>?>?) {
            if (emitEvent("get_visited_history", callback).callSuper) {
                super.getVisitedHistory(callback)
            }
        }

        override fun onCloseWindow(window: WebView?) {
            if (emitEvent("close_window", window).callSuper) {
                super.onCloseWindow(window)
            }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            val eventResult = emitEvent("console_message", consoleMessage)
            val superResult = super.onConsoleMessage(consoleMessage)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            val eventResult = emitEvent("create_window", view, isDialog, isUserGesture, resultMsg)
            val superResult = super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun onGeolocationPermissionsHidePrompt() {
            if (emitEvent("geolocation_permissions_hide_prompt").callSuper) {
                super.onGeolocationPermissionsHidePrompt()
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            if (emitEvent("geolocation_permissions_show_prompt", origin, callback).callSuper) {
                super.onGeolocationPermissionsShowPrompt(origin, callback)
            }
        }

        override fun onHideCustomView() {
            if (emitEvent("hide_custom_view").callSuper) {
                super.onHideCustomView()
            }
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            val eventResult = emitEvent("js_alert", view, url, message, result)
            val superResult = super.onJsAlert(view, url, message, result)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            val eventResult = emitEvent("js_before_unload", view, url, message, result)
            val superResult = super.onJsBeforeUnload(view, url, message, result)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            val eventResult = emitEvent("js_confirm", view, url, message, result)
            val superResult = super.onJsConfirm(view, url, message, result)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
            val eventResult = emitEvent("js_prompt", view, url, message, defaultValue, result)
            val superResult = super.onJsPrompt(view, url, message, defaultValue, result)
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onJsTimeout(): Boolean {
            val eventResult = emitEvent("js_timeout")
            val superResult = super.onJsTimeout()
            return when {
                eventResult.callSuper -> superResult
                else -> coerceBoolean(eventResult.result, superResult)
            }
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            if (emitEvent("permission_request", request).callSuper) {
                super.onPermissionRequest(request)
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            if (emitEvent("permission_request_canceled", request).callSuper) {
                super.onPermissionRequestCanceled(request)
            }
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (emitEvent("progress_changed", view, newProgress).callSuper) {
                super.onProgressChanged(view, newProgress)
            }
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            if (emitEvent("received_icon", view, icon).callSuper) {
                super.onReceivedIcon(view, icon)
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (emitEvent("received_title", view, title).callSuper) {
                super.onReceivedTitle(view, title)
            }
        }

        override fun onReceivedTouchIconUrl(view: WebView?, url: String?, precomposed: Boolean) {
            if (emitEvent("received_touch_icon_url", view, url, precomposed).callSuper) {
                super.onReceivedTouchIconUrl(view, url, precomposed)
            }
        }

        override fun onRequestFocus(view: WebView?) {
            if (emitEvent("request_focus", view).callSuper) {
                super.onRequestFocus(view)
            }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (emitEvent("show_custom_view", view, callback).callSuper) {
                super.onShowCustomView(view, callback)
            }
        }

        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<out Uri?>?>?, fileChooserParams: FileChooserParams?): Boolean {
            val eventResult = emitEvent("show_file_chooser", webView, filePathCallback, fileChooserParams)
            val superResult = super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
            if (!eventResult.callSuper) {
                return coerceBoolean(eventResult.result, superResult)
            }
            var handled = false
            try {
                val activity = webView?.context?.findActivity()
                if (activity is OnActivityResultDelegate.DelegateHost) {
                    val mediator = activity.getOnActivityResultDelegateMediator()
                    val intent = fileChooserParams?.createIntent()
                    val type = intent?.type
                    if (type != null && type.startsWith(".")) {
                        intent.type = Mime.fromFile(type)
                    }
                    try {
                        activity.startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                        mediator.addDelegate(FileChooserCallback(filePathCallback, mediator))
                        handled = true
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        filePathCallback?.onReceiveValue(null)
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            return handled || superResult
        }

    }

    private inner class FileChooserCallback(
        private val filePathCallback: ValueCallback<Array<out Uri?>?>?,
        private val mediator: OnActivityResultDelegate.Mediator,
    ) : OnActivityResultDelegate {

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
                filePathCallback?.onReceiveValue(FileChooserParams.parseResult(resultCode, data))
                emitEvent("file_chooser_result", requestCode, resultCode, data)
                mediator.removeDelegate(this)
            }
        }

    }

    open fun onError(t: Throwable) {
        org.autojs.autojs.AutoJs.instance.globalConsole.error(t)
        t.printStackTrace()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }

    interface JavaScriptEventCallback {
        fun onWebJavaScriptEvent(eventName: String, args: String?)
    }

    interface WebViewEventCallback {
        fun onWebViewEvent(event: Event)
    }

    interface SyncViewEventCallback {
        fun onSyncWebViewEvent(event: Event)
    }

    @ScriptInterface
    fun sendEventToWebJavaScript(event: String, jsonArgs: String?) {
        val eventName = escapeToStr(event)
        val args = escapeToStr(jsonArgs ?: "")
        val js = "\$autojs?.onEventInternal?.($eventName, $args)"
        RhinoUtils.dispatchToMainThread {
            evaluateJavascript(js, null)
        }
    }

    @ScriptInterface
    fun setJavascriptEventCallback(callback: JavaScriptEventCallback) {
        javascriptEventCallback = callback
        synchronized(pendingEvents) {
            for (pendingEvent in pendingEvents) {
                callback.onWebJavaScriptEvent(pendingEvent.event, pendingEvent.jsonArgs)
            }
            pendingEvents.clear()
        }
    }

    @ScriptInterface
    fun setSyncEventEnabled(event: String, alwaysSync: Boolean) {
        val coroutineSyncEventHost = syncEventHost
        if (event.isEmpty()) {
            coroutineSyncEventHost.alwaysSync = alwaysSync
        } else {
            coroutineSyncEventHost.syncEventTable.put(event, alwaysSync)
        }
    }

    abstract fun escapeToStr(src: String): String

    abstract fun newPromise(): Resolvable

    companion object {

        const val FILE_CHOOSER_REQUEST_CODE = 24009

        val AUTOJS_SDK_URI: Uri = "autojs://sdk/v1.js".toUri()

        val AUTOJS_SDK_JS: ByteArray by lazy {
            val assets = GlobalAppContext.get().applicationContext.assets
            val eventsJs = assets.open("web/dist/events.min@3.3.0.js").bufferedReader().readText()
            val autoJsJs = assets.open("web/dist/autojs.sdk.v1.js").bufferedReader().readText()
            (eventsJs + "\n" + autoJsJs).toByteArray(StandardCharsets.UTF_8)
        }

        data class PendingJsEvent(val event: String, val jsonArgs: String)

        @Keep
        class AutoJs(private val webView: EventWebView) {

            @JavascriptInterface
            fun sendEventInternal(event: String, jsonArgs: String) {
                val callback = webView.javascriptEventCallback
                if (callback == null) {
                    webView.pendingEvents.add(PendingJsEvent(event, jsonArgs))
                } else {
                    callback.onWebJavaScriptEvent(event, jsonArgs)
                }
            }
        }

    }

}