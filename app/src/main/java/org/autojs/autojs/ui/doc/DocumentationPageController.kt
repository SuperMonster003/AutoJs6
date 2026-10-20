package org.autojs.autojs.ui.doc

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.webkit.WebView
import androidx.core.view.isVisible
import androidx.webkit.WebViewAssetLoader
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.docs.OfflineDocsAssetPathHandler
import org.autojs.autojs.core.plugin.docs.OfflineDocsPluginStateMonitor
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.DocsUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract

/**
 * Keeps every documentation WebView on the same source, plugin lifecycle, and unavailable-state
 * path. The Offline Docs plugin is never selected implicitly; changing to online documentation
 * remains an explicit user choice.
 */
class DocumentationPageController(
    private val context: Context,
    root: View,
    private val eWebView: EWebView,
) {

    private val webView: WebView = eWebView.webView
    private val unavailableView: View = root.findViewById(R.id.offline_docs_unavailable)
    private var desiredSuffix = DocsUtils.INDEX_URL_SUFFIX
    private var loadedPluginIdentity: String? = null
    private var assetLoader: WebViewAssetLoader? = null
    private var clearHistoryAfterNextDocumentationPage = false
    private var started = false
    private var restartRequiresReload = false
    private var disposed = false

    private val pageFinishedListener = EWebView.OnPageFinishedListener { _, url ->
        if (clearHistoryAfterNextDocumentationPage && DocsUtils.extractUrlSuffix(url) != null) {
            clearHistoryAfterNextDocumentationPage = false
            webView.clearHistory()
        }
    }

    val isUnavailable: Boolean
        get() = unavailableView.isVisible

    val currentUrl: String
        get() = if (isUnavailable) {
            DocsUtils.getUrl(desiredSuffix)
        } else {
            webView.url?.takeIf { DocsUtils.extractUrlSuffix(it) != null }
                ?: DocsUtils.getUrl(desiredSuffix)
        }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (DocsUtils.isDocumentationSourcePreferenceKey(changedKey)) {
            webView.post { reloadByCurrentSource() }
        }
    }

    private val pluginStateListener: (OfflineDocsPluginStateMonitor.Change) -> Unit = { change ->
        webView.post {
            if (!disposed && DocsUtils.isOfflineSourceSelected()) {
                desiredSuffix = currentSuffix()
                if (change.requiresContentRelease || change.requiresContentReload) {
                    // A package can be replaced in place without changing its logical identity.
                    // Drop the old AssetManager before rebuilding the WebView interceptor.
                    releaseOfflineContent()
                }
                renderCurrentSource(forceReload = true)
            }
        }
    }

    init {
        eWebView.addOnPageFinishedListener(pageFinishedListener)
        root.findViewById<View>(R.id.offline_docs_plugin_center).setOnClickListener {
            PluginCenterActivity.launch(
                context = context,
                sourceScope = PluginIndexSource.OFFICIAL,
                initialQuery = OfflineDocsPluginContract.DEFAULT_PACKAGE_NAME,
            )
        }
        root.findViewById<View>(R.id.offline_docs_switch_online).setOnClickListener {
            confirmSwitchToOnlineDocumentation()
        }
    }

    fun start(initialUrl: String?) {
        check(!disposed) { "Documentation controller has already been disposed" }
        if (!started) {
            started = true
            Pref.get().registerOnSharedPreferenceChangeListener(preferenceChangeListener)
            OfflineDocsPluginStateMonitor.addListener(context, pluginStateListener)
        }
        routeSuffix(initialUrl)?.let { desiredSuffix = it }
        val forceReload = restartRequiresReload
        restartRequiresReload = false
        renderCurrentSource(forceReload)
    }

    /**
     * Temporarily suspends external listeners while the owning view is detached. Unlike
     * [dispose], this controller can be started again when the same view is reattached.
     */
    fun stop() {
        if (!started || disposed) return
        started = false
        Pref.get().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        OfflineDocsPluginStateMonitor.removeListener(pluginStateListener)
        webView.stopLoading()
        releaseOfflineContent()
        restartRequiresReload = true
    }

    fun loadUrl(url: String?) {
        routeSuffix(url)?.let { desiredSuffix = it }
        renderCurrentSource(forceReload = false)
    }

    fun reloadByCurrentSource() {
        desiredSuffix = currentSuffix()
        renderCurrentSource(forceReload = true)
    }

    fun refreshPluginState() {
        if (!disposed) {
            OfflineDocsPluginStateMonitor.refresh()
            if (DocsUtils.isOfflineSourceSelected()) {
                desiredSuffix = currentSuffix()
                renderCurrentSource(forceReload = false)
            }
        }
    }

    fun canGoBack(): Boolean = !isUnavailable &&
            !clearHistoryAfterNextDocumentationPage &&
            webView.canGoBack()

    fun goBack() {
        if (canGoBack()) webView.goBack()
    }

    fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        eWebView.removeOnPageFinishedListener(pageFinishedListener)
        clearHistoryAfterNextDocumentationPage = false
        releaseOfflineContent()
    }

    private fun renderCurrentSource(forceReload: Boolean) {
        if (disposed) return
        when (DocsUtils.currentSource) {
            DocsUtils.Source.ONLINE -> showOnlineDocumentation(forceReload)
            DocsUtils.Source.OFFLINE -> showOfflineDocumentation(forceReload)
        }
    }

    private fun showOnlineDocumentation(forceReload: Boolean) {
        releaseOfflineContent()
        showWebView()
        loadIntoWebView(DocsUtils.getOnlineUrl(desiredSuffix), forceReload)
    }

    private fun showOfflineDocumentation(forceReload: Boolean) {
        val state = OfflineDocsPluginStateMonitor.current(context)
        val plugin = OfflineDocsPluginStateMonitor.loadedPlugin(context)
        when {
            state.status == OfflineDocsPluginStateMonitor.Status.LOADING -> {
                showPendingOfflineDocumentation()
                return
            }
            !state.available -> {
                showUnavailable()
                return
            }
            plugin == null -> {
                showPendingOfflineDocumentation()
                OfflineDocsPluginStateMonitor.refresh()
                return
            }
        }
        if (assetLoader == null || loadedPluginIdentity != plugin.candidate.packageIdentity) {
            val nextAssetLoader = WebViewAssetLoader.Builder()
                .setDomain(OfflineDocsAssetPathHandler.DOMAIN)
                .addPathHandler(
                    OfflineDocsAssetPathHandler.PATH_PREFIX,
                    OfflineDocsAssetPathHandler(plugin),
                )
                .build()
            loadedPluginIdentity = plugin.candidate.packageIdentity
            assetLoader = nextAssetLoader
            eWebView.setRequestInterceptor { request ->
                val url = request.url
                if (url.scheme == "https" && url.host == OfflineDocsAssetPathHandler.DOMAIN) {
                    nextAssetLoader.shouldInterceptRequest(url)
                } else {
                    null
                }
            }
        }
        showWebView()
        loadIntoWebView(DocsUtils.getOfflineUrl(desiredSuffix), forceReload)
    }

    private fun showPendingOfflineDocumentation() {
        if (assetLoader != null) return
        releaseOfflineContent()
        webView.stopLoading()
        clearHistoryAfterNextDocumentationPage = true
        if (webView.url != "about:blank") webView.loadUrl("about:blank")
        unavailableView.isVisible = false
        eWebView.isVisible = true
    }

    private fun showWebView() {
        unavailableView.isVisible = false
        eWebView.isVisible = true
    }

    private fun showUnavailable() {
        releaseOfflineContent()
        webView.stopLoading()
        clearHistoryAfterNextDocumentationPage = true
        if (webView.url != "about:blank") webView.loadUrl("about:blank")
        eWebView.isVisible = false
        unavailableView.isVisible = true
    }

    private fun loadIntoWebView(targetUrl: String, forceReload: Boolean) {
        if (!forceReload && webView.url == targetUrl) return
        webView.stopLoading()
        if (forceReload) clearHistoryAfterNextDocumentationPage = true
        webView.loadUrl(targetUrl)
    }

    private fun releaseOfflineContent() {
        eWebView.setRequestInterceptor(null)
        assetLoader = null
        loadedPluginIdentity = null
    }

    private fun currentSuffix(): String = DocsUtils.extractUrlSuffix(webView.url)
        ?: desiredSuffix

    private fun routeSuffix(url: String?): String? {
        DocsUtils.extractUrlSuffix(url)?.let { return it }
        val route = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return route.takeIf { !it.contains("://") && !it.startsWith("about:") }
            ?.trimStart('/')
            ?.ifBlank { DocsUtils.INDEX_URL_SUFFIX }
    }

    private fun confirmSwitchToOnlineDocumentation() {
        MaterialDialog.Builder(context)
            .title(R.string.text_documentation_source)
            .content(R.string.text_switch_to_online_documentation_confirmation)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_switch_to_online_documentation)
            .positiveColorRes(R.color.dialog_button_attraction)
            .widgetThemeColor()
            .onPositive { _, _ ->
                GeneralKeys.DOCUMENTATION_SOURCE.set(key(R.string.key_documentation_source_online))
            }
            .show()
    }
}
