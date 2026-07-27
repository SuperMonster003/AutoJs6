package org.autojs.autojs.ui.edit

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.annotation.ColorInt
import org.autojs.autojs.ui.doc.DocumentationPageController
import org.autojs.autojs.ui.edit.theme.EditorDialogViewThemeApplier
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.util.ColorUtils
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

    private val unavailableView: View = root.findViewById(R.id.offline_docs_unavailable)
    private val paneContainer: View? = eWebView.parent as? View
    private var pageController: DocumentationPageController? = null
    private var currentDarkMode: Boolean? = null
    private var pendingUrl: String? = null
    private var started = false
    private var disposed = false
    private var paletteBackground: Int? = null
    private var paletteForeground: Int? = null
    private var pendingScrollX: Int? = null
    private var pendingScrollY: Int? = null

    private val pageFinishedListener = EWebView.OnPageFinishedListener { webView, _ ->
        applyWebContentPalette(webView)
        val scrollX = pendingScrollX
        val scrollY = pendingScrollY
        if (scrollX != null && scrollY != null) {
            pendingScrollX = null
            pendingScrollY = null
            webView.post { webView.scrollTo(scrollX, scrollY) }
        }
    }

    init {
        eWebView.addOnPageFinishedListener(pageFinishedListener)
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
        eWebView.removeOnPageFinishedListener(pageFinishedListener)
        EditorDialogViewThemeApplier.detach(unavailableView)
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
        val webViewState = Bundle()
        val hasWebViewState = previousWebView.saveState(webViewState) != null
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
        if (hasWebViewState) {
            webView.stopLoading()
            val restored = webView.restoreState(webViewState) != null
            if (!restored && started) {
                pageController?.loadUrl(restoreUrl)
            }
        }
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

        unavailableView.setBackgroundColor(background)
        EditorDialogViewThemeApplier.apply(
            root = unavailableView,
            background = background,
            foreground = foreground,
            control = foreground,
        )
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

    private companion object {
        const val PROGRESS_TRACK_ALPHA = 0.20
    }
}
