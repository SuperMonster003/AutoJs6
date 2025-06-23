package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.os.Bundle
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.widget.CommonMarkdownView
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WebViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityColorSearchHelpBinding

class ColorSearchHelpActivity : BaseActivity() {

    override val handleStatusBarThemeColorAutomatically = false

    private lateinit var mWebView: CommonMarkdownView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityColorSearchHelpBinding.inflate(layoutInflater).also { binding ->
            setContentView(binding.root)
            val webView = binding.webView.also {
                mWebView = it
                it.settings.apply {
                    setSupportMultipleWindows(true)
                    @SuppressLint("SetJavaScriptEnabled")
                    javaScriptEnabled = true
                }
            }
            if (ViewUtils.isNightModeYes(this)) {
                WebViewUtils.adaptDarkMode(webView)
            }
            webView.loadMarkdown(getContent(R.raw.color_search_help_document))
            WebViewUtils.excludeWebViewFromStatusBarAndNavigationBar(binding.webViewParent, webView)
        }
    }

    override fun onStart() {
        super.onStart()
        setUpStatusBarIconLightByNightMode()
    }

    private fun getContent(contentResourceId: Int) = runCatching {
        resources.openRawResource(contentResourceId).bufferedReader(Charsets.UTF_8).use {
            it.lineSequence().joinToString(System.lineSeparator())
        }
    }.getOrElse { getString(R.string.error_failed_to_render_content) }

}
