package org.autojs.autojs.ui.doc

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WebViewUtils
import org.autojs.autojs6.databinding.ActivityDocumentationBinding
import org.intellij.lang.annotations.Language

/**
 * Created by Stardust on Oct 24, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 26, 2023.
 */
class DocumentationActivity : BaseActivity() {

    override val handleStatusBarThemeColorAutomatically = false

    private lateinit var mWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityDocumentationBinding.inflate(layoutInflater).also { binding ->
            setContentView(binding.root)
            binding.ewebView.also { ewebView ->
                ewebView.webView.also { webView ->
                    mWebView = webView
                    webView.settings.setSupportMultipleWindows(true)
                    if (ViewUtils.isNightModeYes(this)) {
                        WebViewUtils.adaptDarkMode(webView)
                    }
                    WebViewUtils.excludeWebViewFromStatusBarAndNavigationBar(ewebView, webView) {
                        generateInjectCodeForBottomInsets(it)
                    }
                    webView.loadUrl(intent.getStringExtra(EXTRA_URL) ?: getUrl("index.html"))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setUpStatusBarAppearanceLightByNightMode()
    }

    @SuppressLint("MissingSuperCall")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack()
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    companion object {

        const val EXTRA_URL = "url"

        fun generateInjectCodeForBottomInsets(systemBarInsetsBottom: Int): String {
            @Language("JavaScript")
            val injectCode = """
                (function() {
                    let mainElements = document.querySelectorAll('main > *');
                    if (mainElements.length > 0) {
                        // Online docs (zh-CN: 在线文档)
                        mainElements.forEach(ele => {
                            let basePaddingBottom = ele.className === 'sidebar-toggle' ? 10 : 0;
                            ele.style.paddingBottom = (basePaddingBottom + ${systemBarInsetsBottom}) / window.devicePixelRatio + 'px';
                        });
                    } else {
                        // Local docs (zh-CN: 本地文档)
                        document.body.style.paddingBottom = '${systemBarInsetsBottom}px';
                    }
                })();
            """.trimIndent()
            return injectCode
        }

    }

}