package org.autojs.autojs.engine

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.security.KeyChain
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Asynchronous Android continuation objects stay on main; scripts receive only JSON requests. */
internal class RuntimeWebCallbacks(private val web: RuntimeWebView, private val emit: (String, JSONObject) -> Unit) {
    private data class Client(val id: String = "", val callbacks: Set<String> = emptySet(), val timeout: Long? = null)
    private data class Pending(val family: String, val name: String, val cancel: () -> Unit,
                               val reply: (Any?) -> Unit, val timeout: Runnable)
    private val main = Handler(Looper.getMainLooper())
    private val pending = LinkedHashMap<String, Pending>()
    private val actions = LinkedHashMap<String, RuntimeWebActionActivity.Action>()
    private val pickerActions = LinkedHashSet<String>()
    private val tasks = LinkedHashMap<String, () -> Unit>()
    private val fileGrants = LinkedHashSet<Uri>()
    @Volatile private var viewClient = Client()
    @Volatile private var chromeClient = Client()
    @Volatile private var downloadClient = Client()
    @Volatile private var policy = RuntimeWebPolicy()
    private var consumeConsole = false
    private var fullscreenAllowed = true
    private var fullscreen: RuntimeWebActionActivity.Action? = null
    private var htmlChooser: (() -> Unit)? = null
    private val permissionRequests = HashMap<PermissionRequest, String>()
    private val historyRequests = ArrayList<ValueCallback<Array<String>>>()
    private var uiEpoch = 0
    var resolveView: ((JSONObject) -> RuntimeWebView)? = null
    private val downloads = RuntimeWebDownloads(web)

    private fun family(name: String) = when (name) { "client" -> viewClient; "chrome" -> chromeClient; "download" -> downloadClient; else -> error("Invalid WebView client family") }
    fun configure(value: JSONObject) {
        val name = value.getString("family")
        val callbacks = value.getJSONArray("callbacks").let { list -> (0 until list.length()).map(list::getString).toSet() }
        val allowed = when (name) { "client" -> clientNames; "chrome" -> chromeNames; "download" -> setOf("onDownloadStart"); else -> error("Invalid WebView client family") }
        require(callbacks.all { it in allowed }) { "Unsupported WebView callback name" }
        val options = value.optJSONObject("options") ?: JSONObject()
        val next = Client(value.getString("clientId"), callbacks, options.opt("callbackTimeoutMs")?.let { RuntimeViewAttributes.integer(it, 100, 120000).toLong() })
        val rules = if (name == "client") RuntimeWebPolicy(options) else null
        cancelFamily(name)
        when (name) {
            "client" -> { viewClient = next; policy = requireNotNull(rules) }
            "chrome" -> {
                chromeClient = next; consumeConsole = options.optBoolean("consumeConsoleMessages", false)
                fullscreenAllowed = options.optBoolean("fullscreen", true)
                if (!fullscreenAllowed) { fullscreen?.close(); fullscreen = null }
                web.settings.setSupportMultipleWindows("onCreateWindow" in callbacks)
                web.settings.javaScriptCanOpenWindowsAutomatically = options.optBoolean("allowAutomaticWindows", false)
                historyRequests.toList().also { historyRequests.clear() }.forEach(chrome::getVisitedHistory)
            }
            else -> downloadClient = next
        }
    }
    fun clear(name: String, clientId: String): Boolean {
        if (family(name).id != clientId) return false
        cancelFamily(name)
        when (name) { "client" -> viewClient = Client(); "chrome" -> { chromeClient = Client(); web.settings.setSupportMultipleWindows(false) }; else -> downloadClient = Client() }
        return true
    }
    private fun signal(name: String, value: JSONObject = JSONObject()) { if (!web.disposed) emit(name, value) }
    private fun notify(family: String, name: String, data: JSONObject) {
        val target = this.family(family)
        if (name !in target.callbacks) return
        val event = JSONObject().put("family", family).put("clientId", target.id).put("callback", name).put("data", data)
        if (Looper.myLooper() == Looper.getMainLooper()) signal("webCallback", event) else main.post { signal("webCallback", event) }
    }
    private fun request(family: String, name: String, data: JSONObject, cancel: () -> Unit, reply: (Any?) -> Unit): String? {
        val target = this.family(family)
        if (name !in target.callbacks) return null
        val id = UUID.randomUUID().toString()
        val timeout = Runnable {
            this.cancel(id)
            signal("webCallbackError", JSONObject().put("callback", name).put("message", "WebView callback timed out"))
        }
        pending[id] = Pending(family, name, cancel, reply, timeout)
        main.postDelayed(timeout, target.timeout ?: if (name == "onShowFileChooser") 120000 else 15000)
        signal("webCallback", JSONObject().put("family", family).put("clientId", target.id).put("callback", name)
            .put("requestId", id).put("data", data))
        return id
    }
    private fun cancel(id: String) {
        pending.remove(id)?.let { main.removeCallbacks(it.timeout); it.cancel() }
    }
    private fun cancelFamily(family: String) { pending.filterValues { it.family == family }.keys.toList().forEach(::cancel) }
    private fun reply(id: String, value: Any?): Boolean {
        val target = pending.remove(id) ?: return false
        main.removeCallbacks(target.timeout)
        try { target.reply(if (value === JSONObject.NULL) null else value) }
        catch (failure: Throwable) { target.cancel(); throw failure }
        return true
    }
    private fun location(url: String?, message: String? = null) = JSONObject().put("url", url ?: JSONObject.NULL)
        .put("message", message ?: JSONObject.NULL).put("title", web.title ?: JSONObject.NULL)
    private fun permitted(permission: String) = web.context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun selected(uris: List<Uri>?): Array<Uri>? {
        val acquired = ArrayList<Uri>()
        try { uris?.forEach { if (it !in fileGrants) { RuntimeWebFileAccess.acquire(web.context, it); fileGrants.add(it); acquired.add(it) } } }
        catch (failure: Throwable) { acquired.forEach { RuntimeWebFileAccess.release(web.context, it); fileGrants.remove(it) }; throw failure }
        return uris?.toTypedArray()
    }
    private fun uriReply(value: Any?): Array<Uri>? {
        if (value == null) return null
        require(value is JSONArray) { "File chooser reply must be a URI array or null" }
        return selected((0 until value.length()).map { Uri.parse(value.getString(it)) })
    }
    private fun picker(options: JSONObject, complete: (Result<Array<Uri>?>) -> Unit): String {
        pickerActions.toList().forEach { actions.remove(it)?.close() }; pickerActions.clear()
        val id = UUID.randomUUID().toString()
        val timeout = Runnable { actions.remove(id)?.close() }
        val action = RuntimeWebActionActivity.chooseFiles(web.context, options) { value ->
            actions.remove(id); pickerActions.remove(id); main.removeCallbacks(timeout)
            complete(value.mapCatching { selected(it) })
        }
        actions[id] = action; pickerActions.add(id); main.postDelayed(timeout, 120000)
        return id
    }
    private fun js(kind: String, url: String?, message: String?, result: JsResult, defaultValue: String? = null): Boolean {
        if (web.disposed) { result.cancel(); return true }
        val data = location(url, message).put("defaultValue", defaultValue ?: JSONObject.NULL)
        val finished = AtomicBoolean()
        fun complete(value: Any?) {
            // Some WebView providers use JsPromptResult for alert/confirm too. The callback kind owns the reply type.
            require(value == null || if (kind == "onJsPrompt") value is String else value is Boolean) { "Invalid JavaScript dialog reply" }
            if (!finished.compareAndSet(false, true)) return
            if (kind == "onJsPrompt") { if (value is String) (result as JsPromptResult).confirm(value) else result.cancel() }
            else if (value == true) result.confirm() else result.cancel()
        }
        if (request("chrome", kind, data, { complete(null) }, ::complete) == null) {
            if (!web.isAttachedToWindow) { complete(null); return true }
            val id = UUID.randomUUID().toString()
            actions[id] = RuntimeWebActionActivity.dialog(web.context, kind, data) { value -> actions.remove(id); complete(value) }
        }
        return true
    }

    val client = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            signal("pageStarted", location(url)); notify("client", "onPageStarted", location(url))
        }
        override fun onPageFinished(view: WebView, url: String?) {
            signal("pageFinished", location(url)); notify("client", "onPageFinished", location(url))
        }
        override fun onLoadResource(view: WebView, url: String?) { notify("client", "onLoadResource", location(url)) }
        override fun onPageCommitVisible(view: WebView, url: String?) { notify("client", "onPageCommitVisible", location(url)) }
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { notify("client", "doUpdateVisitedHistory", location(url).put("reload", isReload)) }
        override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) { notify("client", "onScaleChanged", JSONObject().put("oldScale", oldScale.toDouble()).put("newScale", newScale.toDouble())) }
        override fun onReceivedLoginRequest(view: WebView, realm: String?, account: String?, args: String?) {
            notify("client", "onReceivedLoginRequest", JSONObject().put("realm", realm).put("account", account).put("args", args))
        }
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            val data = RuntimeWebPolicy.request(request).put("errorCode", error.errorCode).put("message", error.description.toString())
            signal("webError", data); notify("client", "onReceivedError", data)
        }
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            val data = RuntimeWebPolicy.request(request).put("statusCode", response.statusCode).put("reasonPhrase", response.reasonPhrase)
                .put("mimeType", response.mimeType).put("responseHeaders", JSONObject(response.responseHeaders ?: emptyMap<String, String>()))
            signal("httpError", data); notify("client", "onReceivedHttpError", data)
        }
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val blocked = policy.navigation(request)
            notify("client", "shouldOverrideUrlLoading", RuntimeWebPolicy.request(request).put("blocked", blocked))
            return blocked
        }
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (web.disposed) return null
            val response = policy.resource(request)
            notify("client", "shouldInterceptRequest", RuntimeWebPolicy.request(request).put("intercepted", response != null))
            return response
        }
        override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean {
            val blocked = event.keyCode in policy.keyCodes
            notify("client", "shouldOverrideKeyEvent", JSONObject().put("keyCode", event.keyCode).put("action", event.action).put("blocked", blocked))
            return blocked
        }
        override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) { notify("client", "onUnhandledKeyEvent", JSONObject().put("keyCode", event.keyCode).put("action", event.action)) }
        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
            if (request("client", "onFormResubmission", location(view.url), { dontResend.sendToTarget() }) { value ->
                if (value == true) resend.sendToTarget() else dontResend.sendToTarget()
            } == null) dontResend.sendToTarget()
        }
        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            if (request("client", "onReceivedHttpAuthRequest", JSONObject().put("host", host).put("realm", realm), handler::cancel) { value ->
                if (value == null) handler.cancel() else { require(value is JSONObject); handler.proceed(value.getString("username"), value.getString("password")) }
            } == null) handler.cancel()
        }
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            if (request("client", "onReceivedSslError", location(error.url).put("primaryError", error.primaryError), handler::cancel) { value ->
                if (value == "proceed") handler.proceed() else { require(value == null || value == "cancel"); handler.cancel() }
            } == null) handler.cancel()
        }
        override fun onReceivedClientCertRequest(view: WebView, certificate: ClientCertRequest) {
            val data = JSONObject().put("host", certificate.host).put("port", certificate.port)
                .put("keyTypes", JSONArray(certificate.keyTypes?.toList() ?: emptyList<String>()))
                .put("principals", JSONArray(certificate.principals?.map { it.name } ?: emptyList<String>()))
            if (request("client", "onReceivedClientCertRequest", data, certificate::cancel) { value ->
                if (value == "ignore") certificate.ignore() else if (value == null || value == "cancel") certificate.cancel() else {
                    require(value is JSONObject); val alias = value.getString("alias")
                    val epoch = uiEpoch; val clientId = viewClient.id
                    io.execute {
                        val keys = runCatching { requireNotNull(KeyChain.getPrivateKey(web.context, alias)) to requireNotNull(KeyChain.getCertificateChain(web.context, alias)) }
                        main.post { if (web.disposed || uiEpoch != epoch || viewClient.id != clientId) certificate.cancel() else keys.fold({ certificate.proceed(it.first, it.second) }, { certificate.cancel(); signal("webCallbackError", JSONObject().put("callback", "onReceivedClientCertRequest").put("message", it.message)) }) }
                    }
                }
            } == null) certificate.cancel()
        }
        override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, response: SafeBrowsingResponse) {
            if (Build.VERSION.SDK_INT < 27) return
            if (this@RuntimeWebCallbacks.request("client", "onSafeBrowsingHit", RuntimeWebPolicy.request(request).put("threatType", threatType), { response.backToSafety(true) }) { value ->
                when (value) { "proceed" -> response.proceed(true); "showInterstitial" -> response.showInterstitial(true); else -> { require(value == null || value == "backToSafety"); response.backToSafety(true) } }
            } == null) response.backToSafety(true)
        }
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val data = JSONObject().put("didCrash", detail.didCrash()).put("rendererPriorityAtExit", detail.rendererPriorityAtExit())
            notify("client", "onRenderProcessGone", data); signal("webError", data.put("message", "WebView renderer exited")); web.release(); return true
        }
    }

    val chrome = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, progress: Int) {
            val data = JSONObject().put("progress", progress); signal("progressChanged", data); notify("chrome", "onProgressChanged", data)
        }
        override fun onReceivedTitle(view: WebView, title: String?) { val data = JSONObject().put("title", title ?: JSONObject.NULL); signal("titleChanged", data); notify("chrome", "onReceivedTitle", data) }
        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            if ("onReceivedIcon" !in chromeClient.callbacks) return
            val data = JSONObject()
            icon?.let { source ->
                val bitmap = Bitmap.createScaledBitmap(source, minOf(source.width, 64), minOf(source.height, 64), true)
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                data.put("width", bitmap.width).put("height", bitmap.height).put("pngBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                if (bitmap !== source) bitmap.recycle()
            }; notify("chrome", "onReceivedIcon", data)
        }
        override fun onReceivedTouchIconUrl(view: WebView, url: String?, precomposed: Boolean) { notify("chrome", "onReceivedTouchIconUrl", location(url).put("precomposed", precomposed)) }
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            notify("chrome", "onConsoleMessage", JSONObject().put("message", message.message()).put("sourceId", message.sourceId())
                .put("lineNumber", message.lineNumber()).put("level", message.messageLevel().name.lowercase()))
            return consumeConsole
        }
        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult) = js("onJsAlert", url, message, result)
        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult) = js("onJsConfirm", url, message, result)
        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult) = js("onJsPrompt", url, message, result, defaultValue)
        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult) = js("onJsBeforeUnload", url, message, result)
        override fun onPermissionRequest(permission: PermissionRequest) {
            if (web.disposed) { permission.deny(); return }
            val id = request("chrome", "onPermissionRequest", JSONObject().put("origin", permission.origin.toString()).put("resources", JSONArray(permission.resources.toList())), { permissionRequests.remove(permission); permission.deny() }) { value ->
                permissionRequests.remove(permission)
                if (value == null) permission.deny() else {
                    require(value is JSONArray); val resources = (0 until value.length()).map(value::getString)
                    require(resources.all { it in permission.resources }) { "Cannot grant an unrequested web resource" }
                    require(PermissionRequest.RESOURCE_AUDIO_CAPTURE !in resources || permitted(Manifest.permission.RECORD_AUDIO)) { "Android microphone permission is required" }
                    require(PermissionRequest.RESOURCE_VIDEO_CAPTURE !in resources || permitted(Manifest.permission.CAMERA)) { "Android camera permission is required" }
                    if (resources.isEmpty()) permission.deny() else permission.grant(resources.toTypedArray())
                }
            }
            if (id == null) permission.deny() else permissionRequests[permission] = id
        }
        override fun onPermissionRequestCanceled(permission: PermissionRequest) {
            permissionRequests.remove(permission)?.let(::cancel)
            notify("chrome", "onPermissionRequestCanceled", JSONObject().put("origin", permission.origin.toString()).put("resources", JSONArray(permission.resources.toList())))
        }
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (request("chrome", "onGeolocationPermissionsShowPrompt", JSONObject().put("origin", origin), { callback.invoke(origin, false, false) }) { value ->
                val allow = value == true || value is JSONObject && value.optBoolean("allow")
                require(!allow || permitted(Manifest.permission.ACCESS_COARSE_LOCATION) || permitted(Manifest.permission.ACCESS_FINE_LOCATION)) { "Android location permission is required" }
                callback.invoke(origin, allow, value is JSONObject && value.optBoolean("retain"))
            } == null) callback.invoke(origin, false, false)
        }
        override fun onGeolocationPermissionsHidePrompt() {
            pending.filterValues { it.name == "onGeolocationPermissionsShowPrompt" }.keys.toList().forEach(::cancel)
            notify("chrome", "onGeolocationPermissionsHidePrompt", JSONObject())
        }
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            if (web.disposed) { callback.onReceiveValue(null); return true }
            htmlChooser?.invoke()
            val done = AtomicBoolean()
            fun complete(value: Array<Uri>?) { if (done.compareAndSet(false, true)) { htmlChooser = null; callback.onReceiveValue(value) } }
            val options = JSONObject().put("acceptTypes", JSONArray(params.acceptTypes.toList())).put("multiple", params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            val data = JSONObject(options.toString()).put("mode", params.mode).put("captureEnabled", params.isCaptureEnabled)
                .put("title", params.title?.toString() ?: JSONObject.NULL).put("filenameHint", params.filenameHint ?: JSONObject.NULL)
            val requestId = request("chrome", "onShowFileChooser", data, { complete(null) }) { value ->
                require(value !is JSONArray || params.mode == FileChooserParams.MODE_OPEN_MULTIPLE || value.length() <= 1) { "A single-file request cannot accept multiple files" }
                complete(uriReply(value))
            }
            if (requestId != null) htmlChooser = { cancel(requestId); complete(null) } else {
                if (!web.isAttachedToWindow) { complete(null); return true }
                if (params.mode !in setOf(FileChooserParams.MODE_OPEN, FileChooserParams.MODE_OPEN_MULTIPLE)) {
                    complete(null); signal("webCallbackError", JSONObject().put("callback", "onShowFileChooser").put("message", "The default document picker supports open and multiple modes")); return true
                }
                val id = picker(options) { value -> value.fold(::complete) { complete(null); signal("webCallbackError", JSONObject().put("callback", "onShowFileChooser").put("message", it.message)) } }
                htmlChooser = { actions.remove(id)?.close(); complete(null) }
            }
            return true
        }
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (web.disposed || "onCreateWindow" !in chromeClient.callbacks) return false
            val once = AtomicBoolean()
            fun complete(child: WebView?) {
                if (once.compareAndSet(false, true)) { (resultMsg.obj as WebView.WebViewTransport).webView = child; resultMsg.sendToTarget() }
            }
            request("chrome", "onCreateWindow", location(view.url).put("dialog", isDialog).put("userGesture", isUserGesture), { complete(null) }) { value ->
                val child = if (value == null) null else requireNotNull(resolveView) { "Window creation requires an execution-owned WebView" }(value as JSONObject)
                require(child == null || child !== web && child.url == null) { "A popup requires a different, not-yet-navigated WebView" }
                complete(child)
            }
            return true
        }
        override fun onCloseWindow(window: WebView) { notify("chrome", "onCloseWindow", JSONObject()) }
        override fun onRequestFocus(view: WebView) { notify("chrome", "onRequestFocus", JSONObject()) }
        override fun getVisitedHistory(callback: ValueCallback<Array<String>>) {
            // WebView may ask during construction, before the asynchronous script client can be installed.
            if (chromeClient.id.isEmpty() && !web.disposed) { historyRequests.add(callback); return }
            if (request("chrome", "getVisitedHistory", JSONObject(), { callback.onReceiveValue(emptyArray()) }) { value ->
                if (value == null) callback.onReceiveValue(emptyArray()) else {
                    require(value is JSONArray); callback.onReceiveValue(Array(value.length()) { value.getString(it) })
                }
            } == null) callback.onReceiveValue(emptyArray())
        }
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (!fullscreenAllowed || fullscreen != null || !web.isAttachedToWindow) { callback.onCustomViewHidden(); return }
            fullscreen = RuntimeWebActionActivity.fullscreen(web.context, view) {
                fullscreen = null; callback.onCustomViewHidden(); notify("chrome", "onHideCustomView", JSONObject())
            }
            notify("chrome", "onShowCustomView", JSONObject())
        }
        override fun onHideCustomView() { fullscreen?.close(); fullscreen = null }
    }

    fun install() {
        web.webViewClient = client; web.webChromeClient = chrome
        web.setDownloadListener { url, userAgent, disposition, mime, length ->
            val data = JSONObject().put("url", url).put("userAgent", userAgent).put("contentDisposition", disposition)
                .put("mimeType", mime).put("contentLength", length).put("suggestedFileName", URLUtil.guessFileName(url, disposition, mime))
            signal("downloadStart", data); notify("download", "onDownloadStart", data)
        }
    }
    fun command(name: String, args: JSONArray, complete: (Result<Any?>) -> Unit) {
        check(!web.disposed) { "WebView is disposed" }
        val value: Any? = when (name) {
            "configureWebClient" -> { configure(args.getJSONObject(0)); null }
            "clearWebClient" -> clear(args.getString(0), args.getString(1))
            "replyWebRequest" -> reply(args.getString(0), args.opt(1))
            "beginWebEvaluation" -> {
                val task = UUID.randomUUID().toString(); var finished = false
                val timeout = args.optJSONObject(1)?.opt("timeoutMs")?.let { RuntimeViewAttributes.integer(it, 100, 120000).toLong() } ?: 30000
                val cancel = web.evaluate(args.getString(0), { result ->
                    finished = true; tasks.remove(task)
                    result.fold({ value -> signal("webTaskResult", JSONObject().put("taskId", task).put("value", value ?: JSONObject.NULL)) },
                        { failure -> signal("webTaskResult", JSONObject().put("taskId", task).put("error", failure.message ?: "Evaluation failed")) })
                }, timeout)
                if (!finished) tasks[task] = { cancel.close() }
                JSONObject().put("taskId", task)
            }
            "cancelWebTask" -> { val cancel = tasks.remove(args.getString(0)); cancel?.invoke(); cancel != null }
            "chooseFiles" -> {
                val task = UUID.randomUUID().toString(); var finished = false
                val pickerId = picker(args.optJSONObject(0) ?: JSONObject()) { result ->
                    finished = true; tasks.remove(task)
                    result.fold({ uris -> signal("webTaskResult", JSONObject().put("taskId", task).put("value", uris?.let { JSONArray(it.map(Uri::toString)) } ?: JSONObject.NULL)) },
                        { failure -> signal("webTaskResult", JSONObject().put("taskId", task).put("error", failure.message ?: "File selection failed")) })
                }
                if (!finished) tasks[task] = { actions.remove(pickerId)?.close() }
                JSONObject().put("taskId", task).put("pickerId", pickerId)
            }
            "cancelFileChooser" -> {
                if (!args.isNull(0)) { val id = args.getString(0); if (pickerActions.remove(id)) actions.remove(id)?.close() }
                else { htmlChooser?.invoke(); pickerActions.toList().forEach { actions.remove(it)?.close() }; pickerActions.clear() }
                true
            }
            "exitFullscreen" -> { val active = fullscreen != null; fullscreen?.close(); fullscreen = null; active }
            "download" -> downloads.start(args.getJSONObject(0))
            "getDownload" -> downloads.get(downloadNumber(args.get(0), 1))
            "cancelDownload" -> downloads.remove(downloadNumber(args.get(0), 1))
            "readDownload" -> {
                val id = downloadNumber(args.get(0), 1); val offset = if (args.isNull(1)) 0 else downloadNumber(args.get(1), 0)
                val length = if (args.isNull(2)) 32768 else RuntimeViewAttributes.integer(args.get(2), 1, 32768)
                io.execute { val result = runCatching { downloads.read(id, offset, length) }; main.post { complete(result) } }; return
            }
            else -> error("Unknown WebView operation: $name")
        }
        complete(Result.success(value))
    }
    fun cancelUi() {
        uiEpoch++
        tasks.values.toList().forEach { it() }; tasks.clear()
        pending.keys.toList().forEach(::cancel); permissionRequests.clear()
        htmlChooser?.invoke(); htmlChooser = null
        actions.values.toList().forEach { it.close() }; actions.clear(); pickerActions.clear(); fullscreen?.close(); fullscreen = null
    }
    fun close() {
        cancelUi(); fileGrants.forEach { runCatching { RuntimeWebFileAccess.release(web.context, it) } }; fileGrants.clear()
        historyRequests.toList().also { historyRequests.clear() }.forEach { it.onReceiveValue(emptyArray()) }
        resolveView = null; policy = RuntimeWebPolicy(); viewClient = Client(); chromeClient = Client(); downloadClient = Client()
    }

    companion object {
        private fun downloadNumber(value: Any, min: Int): Long {
            val number = (value as? Number)?.toDouble()
            require(number != null && number.isFinite() && number % 1.0 == 0.0 && number >= min && number <= 9007199254740991.0) { "Download IDs and offsets must be safe integers" }
            return number.toLong()
        }
        private val io = Executors.newCachedThreadPool { task -> Thread(task, "runtime-web-io").apply { isDaemon = true } }
        val clientNames = setOf("onPageStarted", "onPageFinished", "onLoadResource", "onPageCommitVisible", "doUpdateVisitedHistory", "onScaleChanged",
            "onReceivedError", "onReceivedHttpError", "onReceivedLoginRequest", "shouldOverrideUrlLoading", "shouldInterceptRequest", "shouldOverrideKeyEvent", "onUnhandledKeyEvent",
            "onFormResubmission", "onReceivedHttpAuthRequest", "onReceivedSslError", "onReceivedClientCertRequest", "onSafeBrowsingHit", "onRenderProcessGone")
        val chromeNames = setOf("onProgressChanged", "onReceivedTitle", "onReceivedIcon", "onReceivedTouchIconUrl", "onConsoleMessage",
            "onJsAlert", "onJsConfirm", "onJsPrompt", "onJsBeforeUnload", "onPermissionRequest", "onPermissionRequestCanceled",
            "onGeolocationPermissionsShowPrompt", "onGeolocationPermissionsHidePrompt", "onShowFileChooser", "onCreateWindow", "onCloseWindow", "onRequestFocus", "onShowCustomView", "onHideCustomView", "getVisitedHistory")
        val methods = setOf("configureWebClient", "clearWebClient", "replyWebRequest", "beginWebEvaluation", "cancelWebTask", "chooseFiles", "cancelFileChooser", "exitFullscreen", "download", "getDownload", "cancelDownload", "readDownload")
    }
}
