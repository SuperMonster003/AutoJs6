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
    fun getFontDescriptor(): String {
        return editor.bridgeFontDescriptor()
    }

    @JavascriptInterface
    fun isFontLigaturesEnabled(): Boolean {
        return editor.bridgeFontLigaturesEnabled()
    }

    @JavascriptInterface
    fun isFontStylesEnabled(): Boolean {
        return editor.bridgeFontStylesEnabled()
    }

    @JavascriptInterface
    fun isWordWrapEnabled(): Boolean {
        return editor.bridgeWordWrapEnabled()
    }

    @JavascriptInterface
    fun getWordWrapIndentStyle(): String {
        return editor.bridgeWordWrapIndentStyle()
    }

    @JavascriptInterface
    fun isLineNumbersEnabled(): Boolean {
        return editor.bridgeLineNumbersEnabled()
    }

    @JavascriptInterface
    fun isPrintMarginEnabled(): Boolean {
        return editor.bridgePrintMarginEnabled()
    }

    @JavascriptInterface
    fun isIndentGuidesEnabled(): Boolean {
        return editor.bridgeIndentGuidesEnabled()
    }

    @JavascriptInterface
    fun isBreakpointMarkersEnabled(): Boolean {
        return editor.bridgeBreakpointMarkersEnabled()
    }

    @JavascriptInterface
    fun isFoldMarkersEnabled(): Boolean {
        return editor.bridgeFoldMarkersEnabled()
    }

    @JavascriptInterface
    fun getGutterWidthMode(): String {
        return editor.bridgeGutterWidthMode()
    }

    @JavascriptInterface
    fun getLspOptions(): String {
        return editor.bridgeLspOptions()
    }

    @JavascriptInterface
    fun getPinchToZoomStrategy(): String {
        return editor.bridgePinchToZoomStrategy()
    }

    @JavascriptInterface
    fun shouldForceJsInitThrow(): Boolean {
        return AceEditorTestHooks.shouldForceJsInitThrow()
    }

    @JavascriptInterface
    fun showSoftInput() {
        editor.showSoftInput()
    }

    @JavascriptInterface
    fun hideSoftInput() {
        editor.hideSoftInput()
    }

    @JavascriptInterface
    fun startActionMode(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        hasSelection: Boolean,
        selectAll: Boolean,
    ) {
        editor.startActionMode(left, top, right, bottom, hasSelection, selectAll)
    }

    @JavascriptInterface
    fun finishActionMode() {
        editor.finishActionMode()
    }

    @JavascriptInterface
    fun performLongPressFeedback() {
        editor.performLongPressFeedback()
    }

    @JavascriptInterface
    fun handlePinchZoom(scaleFactor: Double, focusX: Double, focusY: Double, phase: String?) {
        editor.handleBridgePinchZoom(scaleFactor, focusX, focusY, phase.orEmpty())
    }

    @JavascriptInterface
    fun read(uri: String?): String {
        val normalized = normalizeEditorAssetUri(uri)
        if (normalized == null || !isAllowedReadAsset(normalized)) {
            return JSONObject()
                .put("ok", false)
                .put("uri", uri.orEmpty())
                .put("error", "Asset read is not allowed")
                .toString()
        }
        return runCatching {
            editor.context.assets.open("$EDITOR_ASSET_ROOT/$normalized").bufferedReader(Charsets.UTF_8).use { reader ->
                JSONObject()
                    .put("ok", true)
                    .put("uri", uri.orEmpty())
                    .put("asset", normalized)
                    .put("text", reader.readText())
                    .toString()
            }
        }.getOrElse { error ->
            JSONObject()
                .put("ok", false)
                .put("uri", uri.orEmpty())
                .put("asset", normalized)
                .put("error", error.message ?: error.javaClass.simpleName)
                .toString()
        }
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

    private fun normalizeEditorAssetUri(uri: String?): String? {
        var value = uri.orEmpty().trim().replace('\\', '/')
        if (value.isEmpty()) {
            return null
        }
        if (value.startsWith("./")) {
            value = value.removePrefix("./")
        }
        val androidAssetPrefix = "file:///android_asset/$EDITOR_ASSET_ROOT/"
        if (value.startsWith(androidAssetPrefix)) {
            value = value.removePrefix(androidAssetPrefix)
        }
        if (value.startsWith("$EDITOR_ASSET_ROOT/")) {
            value = value.removePrefix("$EDITOR_ASSET_ROOT/")
        }
        if (value.startsWith("/") || value.contains('\u0000')) {
            return null
        }
        val parts = value.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            return null
        }
        return value
    }

    private fun isAllowedReadAsset(path: String): Boolean {
        if (path == "autojs6/types/lib.autojs6.d.ts" || path == "autojs6/types/lib.autojs6.extra.d.ts") {
            return true
        }
        if (!path.startsWith("autojs6/typescript/")) {
            return false
        }
        val name = path.removePrefix("autojs6/typescript/")
        return name.matches(Regex("[A-Za-z0-9_.-]+\\.d\\.ts"))
    }

    private companion object {
        const val EDITOR_ASSET_ROOT = "editor/ace-builds-1.4.12"
    }
}
