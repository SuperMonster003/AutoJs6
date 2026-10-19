package org.autojs.autojs.core.plugin.bun

import android.content.Context
import androidx.core.content.edit
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract

/**
 * Per-capability grants of the Bun capability bridge (plugin ROADMAP M7).
 *
 * Every capability the host offers to Bun scripts is switched independently of the plugin enable
 * state and of every other capability. A revoked capability is simply not offered in the next
 * `runScript` request, so the plugin never sees it and the script never receives it; nothing has to
 * be negotiated with, or torn down inside, the plugin.
 */
object BunHostCapabilityGrants {

    // Host-owned capabilities. The plugin negotiates these IDs through /v1/info, without a new AIDL version.
    const val UI_LAYOUT = "ui.layout"
    const val UI_COMPOSE = "ui.compose"
    const val TEXT_PINYIN = "text.pinyin"
    const val TEXT_PINYIN4J = "text.pinyin4j"
    const val TEXT_OPENCC = "text.opencc"
    const val IMAGE_PROCESS = "image.process"
    const val IMAGE_FILES = "image.files"
    const val SCREEN_CAPTURE = "screen.capture"
    const val SCREEN_RECORD = "screen.record"
    const val SCREEN_RECORD_MICROPHONE = "screen.record.microphone"
    const val SCREEN_RECORD_PLAYBACK = "screen.record.playback"
    const val SCREEN_METRICS = "screen.metrics"
    const val CONSOLE_CONTROL = "console.control"

    const val APP_CONTROL = "app.control"
    const val ACCESSIBILITY_CONTROL = "accessibility.control"
    const val ACCESSIBILITY_GESTURE = "accessibility.gesture"
    const val CLIPBOARD_CONTROL = "clipboard.control"
    const val FILES_CONTROL = "files.control"
    const val STORAGE_CONTROL = "storage.control"

    private const val SP_NAME = "bun_host_capability_grants"

    /** Read-only host info snapshot: host package, version and advisory locale/build date. */
    private const val KEY_HOST_INFO = "host_info"

    fun isHostInfoGranted(context: Context): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HOST_INFO, true)

    fun setHostInfoGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_HOST_INFO, granted) }
    }

    /** Dynamic capability `ui.toast`: short text toasts, at most four per run. */
    private const val KEY_UI_TOAST = "ui_toast"

    /** Dynamic capability `device.info`: brand, model and Android version from `android.os.Build`, no identifiers. */
    private const val KEY_DEVICE_INFO = "device_info"

    fun isUiToastGranted(context: Context): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_UI_TOAST, true)

    fun setUiToastGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_UI_TOAST, granted) }
    }

    fun isDeviceInfoGranted(context: Context): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DEVICE_INFO, true)

    fun setDeviceInfoGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_DEVICE_INFO, granted) }
    }

    fun isTextPinyinGranted(context: Context): Boolean = isGranted(context, TEXT_PINYIN, true)
    fun setTextPinyinGranted(context: Context, granted: Boolean) = setGranted(context, TEXT_PINYIN, granted)

    fun isTextPinyin4jGranted(context: Context): Boolean = isGranted(context, TEXT_PINYIN4J, true)
    fun setTextPinyin4jGranted(context: Context, granted: Boolean) = setGranted(context, TEXT_PINYIN4J, granted)

    fun isTextOpenccGranted(context: Context): Boolean = isGranted(context, TEXT_OPENCC, true)
    fun setTextOpenccGranted(context: Context, granted: Boolean) = setGranted(context, TEXT_OPENCC, granted)

    fun isImageProcessGranted(context: Context): Boolean = isGranted(context, IMAGE_PROCESS, true)
    fun setImageProcessGranted(context: Context, granted: Boolean) = setGranted(context, IMAGE_PROCESS, granted)

    /** Host file access is separate from processing images supplied by the script. */
    fun isImageFilesGranted(context: Context): Boolean = isGranted(context, IMAGE_FILES, false)
    fun setImageFilesGranted(context: Context, granted: Boolean) = setGranted(context, IMAGE_FILES, granted)

    /** Allows requesting Android consent, not silently authorizing MediaProjection. */
    fun isScreenCaptureGranted(context: Context): Boolean = isGranted(context, SCREEN_CAPTURE, false)
    fun setScreenCaptureGranted(context: Context, granted: Boolean) = setGranted(context, SCREEN_CAPTURE, granted)

    /** Silent screen video encoding and host MP4 file output, independently opt-in. */
    fun isScreenRecordGranted(context: Context): Boolean = isGranted(context, SCREEN_RECORD, false)
    fun setScreenRecordGranted(context: Context, granted: Boolean) = setGranted(context, SCREEN_RECORD, granted)

    fun isScreenRecordMicrophoneGranted(context: Context): Boolean = isGranted(context, SCREEN_RECORD_MICROPHONE, false)
    fun setScreenRecordMicrophoneGranted(context: Context, granted: Boolean) = setGranted(context, SCREEN_RECORD_MICROPHONE, granted)
    fun isScreenRecordPlaybackGranted(context: Context): Boolean = isGranted(context, SCREEN_RECORD_PLAYBACK, false)
    fun setScreenRecordPlaybackGranted(context: Context, granted: Boolean) = setGranted(context, SCREEN_RECORD_PLAYBACK, granted)

    fun isScreenMetricsGranted(context: Context): Boolean = isGranted(context, SCREEN_METRICS, true)
    fun setScreenMetricsGranted(context: Context, granted: Boolean) = setGranted(context, SCREEN_METRICS, granted)

    /** Window and global log configuration are opt-in, independently of normal stdout/stderr forwarding. */
    fun isAppControlGranted(context: Context): Boolean = isGranted(context, APP_CONTROL, false)
    fun setAppControlGranted(context: Context, granted: Boolean) = setGranted(context, APP_CONTROL, granted)

    fun isAccessibilityControlGranted(context: Context): Boolean = isGranted(context, ACCESSIBILITY_CONTROL, false)
    fun setAccessibilityControlGranted(context: Context, granted: Boolean) = setGranted(context, ACCESSIBILITY_CONTROL, granted)

    fun isAccessibilityGestureGranted(context: Context): Boolean = isGranted(context, ACCESSIBILITY_GESTURE, false)
    fun setAccessibilityGestureGranted(context: Context, granted: Boolean) = setGranted(context, ACCESSIBILITY_GESTURE, granted)

    fun isClipboardControlGranted(context: Context): Boolean = isGranted(context, CLIPBOARD_CONTROL, false)
    fun setClipboardControlGranted(context: Context, granted: Boolean) = setGranted(context, CLIPBOARD_CONTROL, granted)

    fun isFilesControlGranted(context: Context): Boolean = isGranted(context, FILES_CONTROL, false)
    fun setFilesControlGranted(context: Context, granted: Boolean) = setGranted(context, FILES_CONTROL, granted)

    fun isStorageControlGranted(context: Context): Boolean = isGranted(context, STORAGE_CONTROL, false)
    fun setStorageControlGranted(context: Context, granted: Boolean) = setGranted(context, STORAGE_CONTROL, granted)

    fun isConsoleControlGranted(context: Context): Boolean = isGranted(context, CONSOLE_CONTROL, false)
    fun setConsoleControlGranted(context: Context, granted: Boolean) = setGranted(context, CONSOLE_CONTROL, granted)

    fun isUiLayoutGranted(context: Context): Boolean = isGranted(context, UI_LAYOUT, false)
    fun setUiLayoutGranted(context: Context, granted: Boolean) = setGranted(context, UI_LAYOUT, granted)
    fun isUiComposeGranted(context: Context): Boolean = isGranted(context, UI_COMPOSE, false)
    fun setUiComposeGranted(context: Context, granted: Boolean) = setGranted(context, UI_COMPOSE, granted)

    private fun isGranted(context: Context, key: String, default: Boolean): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(key, default)

    private fun setGranted(context: Context, key: String, granted: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { putBoolean(key, granted) }
    }

    /** Capability IDs of the runtime bridge (plugin M7, dynamic calls) that are currently granted, in contract order. */
    fun grantedBridgeCapabilities(context: Context): List<String> {
        if (org.autojs.autojs6.BuildConfig.isInrt) {
            val config = org.autojs.autojs.project.ProjectConfig.fromAssets(context, "project/project.json")
            return validatePackagedCapabilities(config?.bunConfig?.hostCapabilities.orEmpty())
        }
        return buildList {
        if (isUiLayoutGranted(context)) add(UI_LAYOUT)
        if (isUiComposeGranted(context)) add(UI_COMPOSE)
        if (isUiToastGranted(context)) add(BunRuntimeContract.HOST_CAPABILITY_UI_TOAST)
        if (isDeviceInfoGranted(context)) add(BunRuntimeContract.HOST_CAPABILITY_DEVICE_INFO)
        if (isTextPinyinGranted(context)) add(TEXT_PINYIN)
        if (isTextPinyin4jGranted(context)) add(TEXT_PINYIN4J)
        if (isTextOpenccGranted(context)) add(TEXT_OPENCC)
        if (isImageProcessGranted(context)) add(IMAGE_PROCESS)
        if (isImageFilesGranted(context)) add(IMAGE_FILES)
        if (isScreenCaptureGranted(context)) add(SCREEN_CAPTURE)
        if (isScreenRecordGranted(context)) add(SCREEN_RECORD)
        if (isScreenRecordMicrophoneGranted(context)) add(SCREEN_RECORD_MICROPHONE)
        if (isScreenRecordPlaybackGranted(context)) add(SCREEN_RECORD_PLAYBACK)
        if (isScreenMetricsGranted(context)) add(SCREEN_METRICS)
        if (isAppControlGranted(context)) add(APP_CONTROL)
        if (isAccessibilityControlGranted(context)) add(ACCESSIBILITY_CONTROL)
        if (isAccessibilityGestureGranted(context)) add(ACCESSIBILITY_GESTURE)
        if (isClipboardControlGranted(context)) add(CLIPBOARD_CONTROL)
        if (isFilesControlGranted(context)) add(FILES_CONTROL)
        if (isStorageControlGranted(context)) add(STORAGE_CONTROL)
        if (isConsoleControlGranted(context)) add(CONSOLE_CONTROL)
        }
    }

    internal fun validatePackagedCapabilities(capabilities: List<String>): List<String> {
        val supported = setOf(UI_LAYOUT, UI_COMPOSE, "ui.toast", "device.info", TEXT_PINYIN, TEXT_PINYIN4J,
            TEXT_OPENCC, IMAGE_PROCESS, IMAGE_FILES, SCREEN_CAPTURE, SCREEN_RECORD, SCREEN_RECORD_MICROPHONE,
            SCREEN_RECORD_PLAYBACK, SCREEN_METRICS, CONSOLE_CONTROL, APP_CONTROL, ACCESSIBILITY_CONTROL, ACCESSIBILITY_GESTURE, CLIPBOARD_CONTROL, FILES_CONTROL, STORAGE_CONTROL)
        require(capabilities.size <= supported.size && capabilities.all { it in supported }) { "Unknown Bun host capability in project.json" }
        require(capabilities.distinct().size == capabilities.size) { "Duplicate Bun host capability in project.json" }
        return capabilities.toList()
    }

}
