package org.autojs.autojs.ui.edit

import android.content.Context
import android.content.res.ColorStateList
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.annotation.ColorInt
import androidx.drawerlayout.widget.DrawerLayout
import org.autojs.autojs.ui.doc.DocumentationPageController
import org.autojs.autojs.ui.doc.OfflineDocsUnavailableView
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DocsUtils
import org.autojs.autojs.util.WebViewUtils
import org.autojs.autojs6.R

/**
 * Keeps the editor's pinned documentation independent from the host app day/night theme.
 *
 * WebView derives `prefers-color-scheme` from the theme of the Context used at construction time.
 * Merely allowing algorithmic darkening cannot turn a light-context WebView dark on recent
 * Android versions, so the inner WebView is recreated when the editor crosses light/dark modes.
 */
internal class EditorDocumentationPaneController(
    private val context: Context,
    private val root: View,
    private val eWebView: EWebView,
) {

    private val unavailableView: OfflineDocsUnavailableView = root.findViewById(R.id.offline_docs_unavailable)
    private val paneContainer: View? = eWebView.parent as? View
    private val drawerLayout: DrawerLayout? = root.findViewById(R.id.drawer_layout)
    private var pageController: DocumentationPageController? = null
    private var currentDarkMode: Boolean? = null
    private var pendingUrl: String? = null
    private var started = false
    private var disposed = false
    private var paletteBackground: Int? = null
    private var paletteForeground: Int? = null
    private var pendingScrollX: Int? = null
    private var pendingScrollY: Int? = null
    private var contentCheckGeneration = 0L
    private var recoveryUrl: String? = null
    private var recoveryAttempts = 0

    private val pageFinishedListener = EWebView.OnPageFinishedListener { webView, url ->
        applyWebContentPalette(webView)
        val scrollX = pendingScrollX
        val scrollY = pendingScrollY
        if (scrollX != null && scrollY != null) {
            pendingScrollX = null
            pendingScrollY = null
            webView.post { webView.scrollTo(scrollX, scrollY) }
        }
        requestContentCheck(
            webView,
            url,
            delayMillis = PAGE_FINISHED_CONTENT_CHECK_DELAY_MILLIS,
        )
    }

    private val drawerListener = object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerOpened(drawerView: View) {
            if (drawerView === paneContainer) {
                requestContentCheck(
                    eWebView.webView,
                    pageController?.currentUrl ?: pendingUrl,
                    delayMillis = DRAWER_OPEN_CONTENT_CHECK_DELAY_MILLIS,
                )
            }
        }
    }

    init {
        eWebView.addOnPageFinishedListener(pageFinishedListener)
        drawerLayout?.addDrawerListener(drawerListener)
    }

    fun start(initialUrl: String?) {
        check(!disposed) { "Editor documentation pane has already been disposed" }
        initialUrl?.let { pendingUrl = it }
        started = true
        pageController?.start(initialUrl ?: pendingUrl)
    }

    fun stop() {
        if (!started || disposed) return
        started = false
        pendingUrl = pageController?.currentUrl ?: pendingUrl
        pageController?.stop()
    }

    fun dispose() {
        if (disposed) return
        started = false
        disposed = true
        pageController?.dispose()
        pageController = null
        contentCheckGeneration++
        eWebView.removeOnPageFinishedListener(pageFinishedListener)
        drawerLayout?.removeDrawerListener(drawerListener)
    }

    fun applyPalette(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
    ) {
        if (disposed) return
        val darkMode = ColorUtils.isLuminanceDark(background)
        if (pageController == null || currentDarkMode != darkMode) {
            recreatePageController(darkMode)
        }
        applyViewPalette(background, foreground)
    }

    fun loadUrl(url: String?) {
        url?.let { pendingUrl = it }
        if (started) {
            pageController?.loadUrl(url)
        }
    }

    fun canGoBack(): Boolean = pageController?.canGoBack() == true

    fun goBack() {
        pageController?.goBack()
    }

    private fun recreatePageController(darkMode: Boolean) {
        val restoreUrl = pageController?.currentUrl ?: pendingUrl
        val previousWebView = eWebView.webView
        pendingScrollX = previousWebView.scrollX
        pendingScrollY = previousWebView.scrollY
        pendingUrl = restoreUrl
        pageController?.dispose()
        pageController = null

        val themedContext = ContextThemeWrapper(
            context,
            if (darkMode) R.style.Material3DarkTheme else R.style.Material3LightTheme,
        )
        val webView = eWebView.recreateWebView(themedContext)
        webView.settings.displayZoomControls = true
        WebViewUtils.adaptDarkMode(webView, darkMode)

        currentDarkMode = darkMode
        pageController = DocumentationPageController(context, root, eWebView).also { controller ->
            if (started) {
                controller.start(restoreUrl)
            }
        }

        /*
         * Do not use WebView.saveState()/restoreState() here. Several System WebView versions
         * restore only the navigation entry while leaving the replacement WebView without a
         * rendered document. Stopping the fresh controller load before restoreState() then turns
         * the pinned pane into a permanent, theme-coloured blank surface. Reloading restoreUrl is
         * deterministic, while the explicit scroll restoration above retains the useful state.
         */
        requestContentCheck(
            webView,
            restoreUrl,
            delayMillis = RECREATION_CONTENT_CHECK_DELAY_MILLIS,
        )
    }

    private fun applyViewPalette(
        @ColorInt background: Int,
        @ColorInt foreground: Int,
    ) {
        paletteBackground = background
        paletteForeground = foreground
        paneContainer?.setBackgroundColor(background)
        eWebView.setBackgroundColor(background)
        eWebView.swipeRefreshLayout.apply {
            setBackgroundColor(background)
            setColorSchemeColors(foreground)
            setProgressBackgroundColorSchemeColor(background)
        }
        eWebView.webView.setBackgroundColor(background)
        applyWebContentPalette(eWebView.webView)

        eWebView.findViewById<ProgressBar>(R.id.progress_bar)?.apply {
            progressTintList = ColorStateList.valueOf(foreground)
            indeterminateTintList = ColorStateList.valueOf(foreground)
            progressBackgroundTintList = ColorStateList.valueOf(
                ColorUtils.applyAlpha(foreground, PROGRESS_TRACK_ALPHA),
            )
        }

        unavailableView.setPalette(background, foreground)
    }

    /**
     * Also supplies a deterministic fallback for old System WebView builds that support neither
     * `prefers-color-scheme` nor AndroidX force/algorithmic darkening.
     */
    private fun applyWebContentPalette(webView: WebView) {
        val background = paletteBackground ?: return
        val foreground = paletteForeground ?: return
        val backgroundCss = String.format("#%06X", background and 0x00FFFFFF)
        val foregroundCss = String.format("#%06X", foreground and 0x00FFFFFF)
        val colorScheme = if (ColorUtils.isLuminanceDark(background)) "dark" else "light"
        webView.evaluateJavascript(
            """
            (function() {
                var id = 'autojs6-editor-doc-palette';
                var style = document.getElementById(id);
                if (!style) {
                    style = document.createElement('style');
                    style.id = id;
                    (document.head || document.documentElement).appendChild(style);
                }
                style.textContent =
                    ':root { color-scheme: $colorScheme !important; } ' +
                    'html, body { background-color: $backgroundCss !important; ' +
                    'color: $foregroundCss !important; }';
                document.documentElement.style.backgroundColor = '$backgroundCss';
                if (document.body) {
                    document.body.style.backgroundColor = '$backgroundCss';
                    document.body.style.color = '$foregroundCss';
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    /**
     * A restored WebView can report the expected URL and 100% progress while its DOM is empty.
     * Verify the document when loading finishes and whenever the drawer first becomes visible.
     * Normal pages are left untouched; only an actually empty document gets a bounded reload.
     */
    private fun requestContentCheck(
        webView: WebView,
        expectedUrl: String?,
        delayMillis: Long,
    ) {
        if (!started || disposed) return
        val generation = ++contentCheckGeneration
        webView.postDelayed(
            {
                if (
                    generation != contentCheckGeneration ||
                    disposed ||
                    !started ||
                    webView !== eWebView.webView
                ) {
                    return@postDelayed
                }
                val controller = pageController ?: return@postDelayed
                if (controller.isUnavailable || webView.progress < 100) {
                    return@postDelayed
                }
                val checkedUrl = webView.url
                if (
                    checkedUrl.isNullOrBlank() ||
                    checkedUrl == "about:blank" ||
                    DocsUtils.extractUrlSuffix(checkedUrl) == null
                ) {
                    return@postDelayed
                }
                webView.evaluateJavascript(DOCUMENT_CONTENT_SCORE_SCRIPT) { rawScore ->
                    if (
                        generation != contentCheckGeneration ||
                        disposed ||
                        !started ||
                        webView !== eWebView.webView ||
                        webView.progress < 100 ||
                        webView.url != checkedUrl
                    ) {
                        return@evaluateJavascript
                    }
                    val score = rawScore?.trim()?.trim('"')?.toIntOrNull() ?: -1
                    val actualUrl = webView.url
                    Log.d(
                        TAG,
                        "Pinned documentation content check: expected=$expectedUrl, " +
                            "actual=$actualUrl, progress=${webView.progress}, score=$score",
                    )
                    when {
                        score > 0 -> {
                            recoveryUrl = null
                            recoveryAttempts = 0
                        }
                        score == 0 -> {
                            recoverEmptyDocument(controller, actualUrl ?: expectedUrl)
                        }
                        else -> {
                            Log.w(TAG, "Unable to inspect pinned documentation content")
                        }
                    }
                }
            },
            delayMillis,
        )
    }

    private fun recoverEmptyDocument(
        controller: DocumentationPageController,
        url: String?,
    ) {
        val requestedUrl = controller.currentUrl.takeIf(String::isNotBlank) ?: url
        if (recoveryUrl != requestedUrl) {
            recoveryUrl = requestedUrl
            recoveryAttempts = 0
        }
        if (recoveryAttempts >= MAX_EMPTY_DOCUMENT_RECOVERY_ATTEMPTS) {
            Log.e(
                TAG,
                "Pinned documentation remained empty after $recoveryAttempts reload attempts: " +
                    requestedUrl,
            )
            return
        }
        recoveryAttempts++
        Log.w(
            TAG,
            "Reloading empty pinned documentation ($recoveryAttempts/" +
                "$MAX_EMPTY_DOCUMENT_RECOVERY_ATTEMPTS): $requestedUrl",
        )
        controller.reloadByCurrentSource()
    }

    private companion object {
        const val TAG = "EditorDocsPane"
        const val PROGRESS_TRACK_ALPHA = 0.20
        const val PAGE_FINISHED_CONTENT_CHECK_DELAY_MILLIS = 500L
        const val DRAWER_OPEN_CONTENT_CHECK_DELAY_MILLIS = 500L
        const val RECREATION_CONTENT_CHECK_DELAY_MILLIS = 1_000L
        const val MAX_EMPTY_DOCUMENT_RECOVERY_ATTEMPTS = 2

        const val DOCUMENT_CONTENT_SCORE_SCRIPT = """
            (function() {
                var body = document.body;
                if (!body) return 0;
                var text = (body.innerText || body.textContent || '').trim();
                return text.length + body.childElementCount;
            })();
        """
    }
}
