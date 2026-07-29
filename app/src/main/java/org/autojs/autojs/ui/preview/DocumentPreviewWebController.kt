@file:Suppress("DEPRECATION")

package org.autojs.autojs.ui.preview

import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.ByteArrayInputStream
import java.io.File

class DocumentPreviewWebController(
    context: Context,
    private val webView: WebView,
    resourceRoot: File,
    private val onExternalLink: (Uri) -> Unit,
    private val onPageFinished: () -> Unit = {},
) {

    private val documentPathHandler = DocumentPreviewDocumentPathHandler(resourceRoot)

    private val assetLoader = WebViewAssetLoader.Builder()
        .setDomain(DocumentPreviewWebOrigin.DOMAIN)
        .addPathHandler(
            DocumentPreviewWebOrigin.PREVIEW_ASSET_PATH_PREFIX,
            DocumentPreviewAssetPathHandler(context.assets),
        )
        .addPathHandler(
            DocumentPreviewWebOrigin.DOCUMENT_PATH_PREFIX,
            documentPathHandler,
        )
        .build()

    init {
        configureSettings()
        configureClient()
    }

    private fun configureSettings() {
        webView.settings.apply {
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = false
            databaseEnabled = false
            setGeolocationEnabled(false)

            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkImage = false
            blockNetworkLoads = false

            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            saveFormData = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
    }

    private fun configureClient() {
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val uri = request.url
                if (
                    DocumentPreviewRequestPolicy.shouldLetWebViewLoadDataResource(
                        uri.toString(),
                        request.isForMainFrame,
                    )
                ) {
                    return null
                }
                if (uri.scheme.equals("https", ignoreCase = true) && DocumentPreviewWebOrigin.isDomain(uri.host)) {
                    return assetLoader.shouldInterceptRequest(uri) ?: forbidden()
                }
                if (
                    DocumentPreviewRequestPolicy.shouldLetWebViewLoadHttpsSubresource(
                        uri.toString(),
                        request.isForMainFrame,
                    )
                ) {
                    return null
                }
                return forbidden()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) return true
                val uri = request.url
                if (isLocalAnchor(uri)) return false
                if (DocumentPreviewWebOrigin.isDomain(uri.host)) return true
                if (request.hasGesture() && uri.scheme in EXTERNAL_LINK_SCHEMES) {
                    onExternalLink(uri)
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url != "about:blank") {
                    onPageFinished()
                }
            }
        }
    }

    fun show(html: String) {
        documentPathHandler.updateDocument(html)
        webView.loadUrl(DocumentPreviewWebOrigin.DOCUMENT_URL)
    }

    fun destroy() {
        documentPathHandler.clearDocument()
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.clearFormData()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun isLocalAnchor(uri: Uri): Boolean {
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            !DocumentPreviewWebOrigin.isDomain(uri.host) ||
            uri.fragment == null
        ) {
            return false
        }
        return uri.path == DocumentPreviewWebOrigin.DOCUMENT_PATH_PREFIX ||
            uri.path == Uri.parse(DocumentPreviewWebOrigin.DOCUMENT_URL).path
    }

    companion object {
        private val EXTERNAL_LINK_SCHEMES = setOf("http", "https")

        private val FORBIDDEN_HEADERS = mapOf(
            "Cache-Control" to "no-store, max-age=0",
            "X-Content-Type-Options" to "nosniff",
        )

        private fun forbidden() = WebResourceResponse(
            "text/plain",
            Charsets.UTF_8.name(),
            403,
            "Forbidden",
            FORBIDDEN_HEADERS,
            ByteArrayInputStream(ByteArray(0)),
        )
    }
}

internal object DocumentPreviewRequestPolicy {

    fun shouldLetWebViewLoadDataResource(
        url: String,
        isForMainFrame: Boolean,
    ): Boolean =
        !isForMainFrame &&
            url.startsWith("data:", ignoreCase = true) &&
            DocumentPreviewUrlPolicy.isSafeResource(url)

    fun shouldLetWebViewLoadHttpsSubresource(
        url: String,
        isForMainFrame: Boolean,
    ): Boolean {
        if (isForMainFrame) return false
        val host = DocumentPreviewUrlPolicy.safeRemoteHttpsHost(url) ?: return false
        return !DocumentPreviewWebOrigin.isDomain(host)
    }
}
