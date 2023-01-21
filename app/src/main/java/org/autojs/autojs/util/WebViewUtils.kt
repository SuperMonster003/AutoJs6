package org.autojs.autojs.util

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
import androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
import androidx.webkit.WebViewFeature.FORCE_DARK
import androidx.webkit.WebViewFeature.isFeatureSupported

class WebViewUtils {

    companion object {

        // @Hint by SuperMonster003 on Aug 26, 2022.
        //  ! Requirements for this feature are as follows:
        //  !   For Android API Level >= 29 (Android 10) [Q]:
        //  !     Android System WebView (or browsers like Google Chrome) version >= 76
        //  !   For Android API Level <= 28 (Android 9) [P]:
        //  !     Android System WebView (or browsers like Google Chrome) version >= 105
        // @See https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
        @JvmStatic
        fun adaptDarkMode(context: Context, webView: WebView) {
            if (isFeatureSupported(FORCE_DARK) && SDK_INT < TIRAMISU) {
                webView.settings.let { webSettings ->
                    webSettings.setSupportMultipleWindows(true)
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDark(
                        webSettings, when (ViewUtils.isNightModeYes(context)) {
                            true -> FORCE_DARK_ON
                            else -> FORCE_DARK_OFF
                        }
                    )
                }
            }
        }

    }

}