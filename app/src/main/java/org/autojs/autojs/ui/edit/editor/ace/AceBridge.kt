package org.autojs.autojs.ui.edit.editor.ace

import android.webkit.JavascriptInterface
import org.json.JSONObject

class AceBridge(
    private val editor: AceCodeEditor,
) {

    @JavascriptInterface
    fun notifyEvent(name: String?, payloadJson: String?) {
        editor.handleEvent(name.orEmpty(), payloadJson)
    }

    @JavascriptInterface
    fun notifyReady(stateJson: String?) {
        editor.handleReady(stateJson)
    }

    @JavascriptInterface
    fun notifyStateChanged(stateJson: String?) {
        editor.handleStateChanged(stateJson)
    }

    @JavascriptInterface
    fun notifyTextChanged(stateJson: String?) {
        editor.handleTextChanged(stateJson)
    }

    @JavascriptInterface
    fun notifyCursorChanged(lineText: String?, line: Int, column: Int, stateJson: String?) {
        editor.handleCursorChanged(lineText.orEmpty(), line, column, stateJson)
    }

    @JavascriptInterface
    fun notifyBreakpointChanged(line: Int, enabled: Boolean, stateJson: String?) {
        editor.handleBreakpointChanged(line, enabled, stateJson)
    }

    @JavascriptInterface
    fun notifyError(message: String?) {
        editor.handleError(message.orEmpty())
    }

    @JavascriptInterface
    fun getTheme(): String {
        return editor.bridgeTheme()
    }

    @JavascriptInterface
    fun getFontFamily(): String {
        return editor.bridgeFontFamily()
    }

    @JavascriptInterface
    fun getLspOptions(): String {
        return "{}"
    }

    @JavascriptInterface
    fun shouldForceJsInitThrow(): Boolean {
        return AceEditorTestHooks.shouldForceJsInitThrow()
    }

    @JavascriptInterface
    fun read(uri: String?): String {
        return unavailableFileAccess(uri)
    }

    @JavascriptInterface
    fun write(uri: String?, text: String?): String {
        return unavailableFileAccess(uri)
    }

    private fun unavailableFileAccess(uri: String?): String {
        return JSONObject()
            .put("ok", false)
            .put("uri", uri.orEmpty())
            .put("error", "File access is not available from the ACE editor bridge")
            .toString()
    }
}
