package org.autojs.autojs.core.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Created by Stardust on 2017/11/29.
 */
class JsWebView : WebView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

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
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
    }
}