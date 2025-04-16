package org.autojs.autojs.util

import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING
import androidx.webkit.WebViewFeature.FORCE_DARK
import androidx.webkit.WebViewFeature.isFeatureSupported
import org.intellij.lang.annotations.Language

/**
 * Created by SuperMonster003 on Aug 26, 2022.
 * Modified by SuperMonster003 as of Mar 12, 2025.
 */
class WebViewUtils {

    companion object {

        // @Hint by SuperMonster003 on Aug 26, 2022.
        //  ! Requirements for this feature are as follows:
        //  ! - For Android API Level >= 29 (10) [Q]:
        //  !   - Android System WebView (or browsers like Google Chrome) version >= 76
        //  ! - For Android API Level <= 28 (9) [P]:
        //  !   - Android System WebView (or similar Google Chrome browsers) version >= 105
        //  ! Reference: https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
        //  ! zh-CN:
        //  ! 当前特性需满足如下要求：
        //  ! - 安卓 API 级别 >= 29 (10) [Q]:
        //  !   - 安卓系统 WebView（或类似 Google Chrome 浏览器）版本 >= 76
        //  ! - 安卓 API 级别 <= 28 (9) [P]:
        //  !   - 安卓系统 WebView（或类似 Google Chrome 浏览器）版本 >= 105
        //  ! 参阅: https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
        @JvmStatic
        fun adaptDarkMode(webView: WebView) {
            val settings = webView.settings
            when {
                isFeatureSupported(ALGORITHMIC_DARKENING) -> {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                }
                isFeatureSupported(FORCE_DARK) -> {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                }
                // FIXME by SuperMonster003 on Mar 14, 2025.
                //  ! Not working.
                //  ! zh-CN: 不起作用.
                // else -> injectDarkModeCSS(webView)
            }
        }

        private fun injectDarkModeCSS(webView: WebView) {
            val darkModeCSS = """
             (function() {
                 let style = document.createElement('style');
                 style.type = 'text/css';
                 style.innerHTML = `
                     html { filter: invert(1) hue-rotate(180deg); }
                     img, video { filter: invert(1) hue-rotate(180deg); }
                 `;
                 document.head.appendChild(style);
             })();
         """.trimIndent()

            webView.evaluateJavascript(darkModeCSS, null)
        }

        fun excludeWebViewFromNavigationBar(webViewWrapper: View, webView: WebView) {
            excludeWebViewFromSystemBars(webViewWrapper, webView, excludeStatusBar = false, excludeNavigationBar = true)
        }

        fun excludeWebViewFromStatusBarAndNavigationBar(webViewWrapper: View, webView: WebView) {
            excludeWebViewFromSystemBars(webViewWrapper, webView, excludeStatusBar = true, excludeNavigationBar = true)
        }

        @Suppress("SameParameterValue")
        private fun excludeWebViewFromSystemBars(webViewWrapper: View, webView: WebView, excludeStatusBar: Boolean, excludeNavigationBar: Boolean) {
            var mSystemBarInsets: Insets? = null
            ViewCompat.setOnApplyWindowInsetsListener(webViewWrapper) { v, insets ->
                val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars()).also {
                    mSystemBarInsets = it
                }
                if (excludeStatusBar) {
                    v.setPadding(0, systemBarInsets.top, 0, 0)
                }
                if (excludeNavigationBar) {
                    webView.setPadding(0, 0, 0, systemBarInsets.bottom)
                }
                insets
            }
            if (excludeNavigationBar) {
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val systemBarInsets = mSystemBarInsets ?: return
                        val webView = view ?: return
                        adjustBodyPaddingToAvoidNavOverlay(webView, systemBarInsets)
                    }
                }
            }
        }

        private fun adjustBodyPaddingToAvoidNavOverlay(webView: WebView, systemBarInsets: Insets) {
            // @Hint by SuperMonster003 on Mar 12, 2025.
            //  ! Inject JavaScript code for document body with adding a bottom padding
            //  ! to ensure that content won't be covered by nav bar when scrolling to the end.
            //  ! zh-CN: 注入 JavaScript, 为 body 添加底部 padding, 确保滚动到底部时导航栏不遮挡内容.
            @Language("JavaScript")
            val jsCode = """
                (function() {
                    let mainElements = document.querySelectorAll('main > *');
                    if (mainElements.length > 0) {
                        const additionalPadding = ${systemBarInsets.bottom};
                        mainElements.forEach(ele => {
                            const computedStyle = window.getComputedStyle(ele);
                            const currentPadding = parseFloat(computedStyle.paddingBottom) || 0;
                            ele.style.paddingBottom = (currentPadding + additionalPadding) / window.devicePixelRatio + 'px';
                        });
                    } else {
                        document.body.style.paddingBottom = '${systemBarInsets.bottom}px';
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(jsCode, null)
        }

    }

}