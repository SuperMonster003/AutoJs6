package org.autojs.autojs.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.autojs.autojs.annotation.ScriptClass
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.resource.RuntimeResourceScope.Stage
import org.mozilla.javascript.Scriptable
import java.lang.ref.WeakReference

/**
 * Created by Stardust on Apr 1, 2017.
 * Modified by SuperMonster003 as of Jan 21, 2023.
 * Transformed by SuperMonster003 on Apr 18, 2023.
 */
@ScriptClass
@SuppressLint("ViewConstructor")
class InjectableWebView(context: Context, jsCtx: org.mozilla.javascript.Context, scriptable: Scriptable, url: String?) : WebView(context) {

    private var mInjectableWebClient: InjectableWebClient

    init {
        settings.apply {
            useWideViewPort = true
            builtInZoomControls = true
            loadWithOverviewMode = true
            @SuppressLint("SetJavaScriptEnabled")
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            displayZoomControls = false
        }
        InjectableWebClient(jsCtx, scriptable).let {
            mInjectableWebClient = it
            webViewClient = it
        }
        webChromeClient = WebChromeClient()
        if (url != null) {
            settings.takeIf { url.startsWith("file:") }?.apply {
                allowFileAccess = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }
            loadUrl(url)
        }
    }

    @ScriptInterface
    fun inject(script: String?, callback: ValueCallback<String?>?) = mInjectableWebClient.inject(script, callback)

    @ScriptInterface
    fun inject(script: String?) = mInjectableWebClient.inject(script)

}

/**
 * Registers this WebView with the resources of [scriptRuntime] (appendix C Q42 of docs/memory/ROADMAP.md, P4.4): when the script
 * ends, after its `exit` handlers, the WebView is destroyed on the thread that created it, unless it is attached to a window by
 * then. A WebView on screen belongs to the UI lifecycle of its window (an Activity, or a floating window the script did not
 * close), and destroying it would break what is shown (red line R2). The scope holds the WebView weakly, so a WebView the
 * script drops is collected as before.
 * zh-CN: 把本 WebView 登记到 [scriptRuntime] 的资源中 (docs/memory/ROADMAP.md 附录 C Q42, P4.4): 脚本结束时, 在其 `exit` 处理器之后, 于创建它的线程上
 * 销毁该 WebView, 除非它此时已挂到窗口上. 显示中的 WebView 归其窗口的界面生命周期管理 (Activity, 或脚本未关闭的悬浮窗), 销毁它会破坏正在显示的内容
 * (红线 R2). 作用域以弱引用持有, 脚本丢弃的 WebView 照旧被回收.
 */
internal fun InjectableWebView.destroyOnExitOf(scriptRuntime: ScriptRuntime): InjectableWebView {
    // A WebView can only be created on a thread with a Looper, and must be destroyed on that thread.
    // zh-CN: WebView 只能在有 Looper 的线程上创建, 也必须在该线程上销毁.
    val looper = Looper.myLooper() ?: return this
    val reference = WeakReference(this)
    scriptRuntime.resourceScope.own(Stage.WEB_VIEWS, "web.newInjectableWebView") {
        val view = reference.get() ?: return@own
        val release = Runnable { view.destroyUnlessAttached() }
        when {
            Looper.myLooper() === looper -> release.run()
            !Handler(looper).post(release) -> Log.w(WEB_VIEW_TAG, "WebView of an ended script not destroyed: the thread that created it has quit")
        }
    }
    scriptRuntime.resourceScope.track(this, "web.newInjectableWebView")
    return this
}

private fun WebView.destroyUnlessAttached() {
    if (isAttachedToWindow) return
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}

private const val WEB_VIEW_TAG = "InjectableWebView"
