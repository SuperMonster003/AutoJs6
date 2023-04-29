package org.autojs.autojs.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.autojs.autojs.annotation.ScriptClass
import org.autojs.autojs.annotation.ScriptInterface
import org.mozilla.javascript.Scriptable

/**
 * Created by Stardust on 2017/4/1.
 * Modified by SuperMonster003 as of Jan 21, 2023.
 * Transformed by SuperMonster003 on Apr 18, 2023.
 */
@ScriptClass
@SuppressLint("ViewConstructor", "SetJavaScriptEnabled")
class InjectableWebView(context: Context?, jsCtx: org.mozilla.javascript.Context, scriptable: Scriptable, url: String?) : WebView(context!!) {

    private var mInjectableWebClient: InjectableWebClient

    init {
        settings.apply {
            useWideViewPort = true
            builtInZoomControls = true
            loadWithOverviewMode = true
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