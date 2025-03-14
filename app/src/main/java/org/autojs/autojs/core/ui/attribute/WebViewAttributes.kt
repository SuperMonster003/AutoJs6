package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.webkit.WebView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.runtime.ScriptRuntime

open class WebViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as WebView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("url") { view.loadUrl(it) }
        registerAttrs(arrayOf("scale", "initialScale")) { view.setInitialScale(it.toInt()) }
        registerAttrs(arrayOf("enableNetwork", "networkAvailable")) { view.setNetworkAvailable(it.toBoolean()) }
        registerAttr("blockNetworkImage") { view.settings.blockNetworkImage = it.toBoolean() }
        registerAttr("blockNetworkLoads") { view.settings.blockNetworkLoads = it.toBoolean() }
        registerAttrs(arrayOf("enableBuiltInZoomControls", "builtInZoomControls")) { view.settings.builtInZoomControls = it.toBoolean() }
        @Suppress("DEPRECATION")
        registerAttrs(arrayOf("enableDatabase", "databaseEnabled", "isDatabaseEnabled")) { view.settings.databaseEnabled = it.toBoolean() }
        registerAttrs(arrayOf("fontSize", "defaultFontSize")) { view.settings.defaultFontSize = it.toInt() }
        registerAttrs(arrayOf("fixedFontSize", "defaultFixedFontSize")) { view.settings.defaultFixedFontSize = it.toInt() }
        registerAttrs(arrayOf("textEncodingName", "defaultTextEncodingName")) { view.settings.defaultTextEncodingName = Strings.parse(view, it) }
        registerAttrs(arrayOf("jsAutoOpenWindows", "javaScriptAutoOpenWindows", "javaScriptCanOpenWindowsAutomatically")) { view.settings.javaScriptCanOpenWindowsAutomatically = it.toBoolean() }
        registerAttrs(arrayOf("enableJs", "enableJavaScript", "javaScriptEnabled", "isJavaScriptEnabled")) { view.settings.javaScriptEnabled = it.toBoolean() }
        registerAttrs(arrayOf("autoLoadImages", "autoLoadsImages", "loadsImagesAutomatically")) { view.settings.loadsImagesAutomatically = it.toBoolean() }
        registerAttrs(arrayOf("minFontSize", "minimumFontSize")) { view.settings.minimumFontSize = it.toInt() }
        registerAttr("sansSerifFontFamily") { view.settings.sansSerifFontFamily = Strings.parse(view, it) }
        registerAttr("serifFontFamily") { view.settings.serifFontFamily = Strings.parse(view, it) }
        registerAttrs(arrayOf("fontFamily", "standardFontFamily")) { view.settings.standardFontFamily = Strings.parse(view, it) }
        registerAttrs(arrayOf("multiWindows", "multipleWindows", "supportMultipleWindows")) { view.settings.setSupportMultipleWindows(it.toBoolean()) }
        registerAttrs(arrayOf("userAgent", "userAgentString")) { view.settings.userAgentString = Strings.parse(view, it) }
    }

}
