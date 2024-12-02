package org.autojs.autojs.util

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING
import androidx.webkit.WebViewFeature.FORCE_DARK
import androidx.webkit.WebViewFeature.isFeatureSupported

class WebViewUtils {

    companion object {

        // @Hint by SuperMonster003 on Aug 26, 2022.
        //  ! Requirements for this feature are as follows:
        //  ! - For Android API Level >= 29 (10) [Q]:
        //  !   - Android System WebView (or browsers like Google Chrome) version >= 76
        //  ! - For Android API Level <= 28 (9) [P]:
        //  !   - Android System WebView (or browsers like Google Chrome) version >= 105
        //  ! Reference: https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
        //  ! zh-CN:
        //  ! 当前特性需满足如下要求：
        //  ! - 安卓 API 级别 >= 29 (10) [Q]:
        //  !   - 安卓系统 WebView（或类似 Google Chrome 浏览器）版本 >= 76
        //  ! - 安卓 API 级别 <= 28 (9) [P]:
        //  !   - 安卓系统 WebView（或类似 Google Chrome 浏览器）版本 >= 105
        //  ! 参阅: https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
        @JvmStatic
        fun adaptDarkMode(context: Context, webView: WebView) {
            if (isFeatureSupported(FORCE_DARK) && SDK_INT < TIRAMISU) {
                webView.settings.let { webSettings ->
                    webSettings.setSupportMultipleWindows(true)
                    // @Comment by SuperMonster003 on Aug 14, 2023.
                    //  # @Suppress("DEPRECATION")
                    //  # WebSettingsCompat.setForceDark(
                    //  #     webSettings, when (ViewUtils.isNightModeYes(context)) {
                    //  #         true -> FORCE_DARK_ON
                    //  #         else -> FORCE_DARK_OFF
                    //  #     }
                    //  # )
                    if (isFeatureSupported(ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, ViewUtils.isNightModeYes(context))
                    }
                }
            }
        }

    }

}