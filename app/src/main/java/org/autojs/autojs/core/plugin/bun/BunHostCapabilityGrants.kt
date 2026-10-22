package org.autojs.autojs.core.plugin.bun

import android.content.Context
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract

/**
 * Per-capability grants of the Bun capability bridge (plugin ROADMAP M7).
 *
 * Every capability the host offers to Bun scripts is switched independently of the plugin enable
 * state and of every other capability. A revoked capability is simply not offered in the next
 * `runScript` request, so the plugin never sees it and the script never receives it; nothing has to
 * be negotiated with, or torn down inside, the plugin.
 *
 * The grants are the named store `bun_host_capability_grants`, one Boolean per capability id, declared as the key
 * family [CredentialKeys.BUN_HOST_CAPABILITY_GRANT] (docs/settings/ROADMAP.md P3.4, sensitive); each capability keeps
 * its own default here. The [Context] parameters are kept for the callers; the store is opened by the settings layer
 * with the application context.
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
    const val DATABASE_CONTROL = "database.control"
    const val SENSORS_CONTROL = "sensors.control"
    const val SHELL_CONTROL = "shell.control"
    const val SHELL_ROOT = "shell.root"
    const val SHELL_SHIZUKU = "shell.shizuku"

    const val EVENTS_OBSERVE = "events.observe"
    const val EVENTS_NOTIFICATION = "events.notification"
    const val EVENTS_TOAST = "events.toast"
    const val EVENTS_KEY = "events.key"
    const val NOTIFICATIONS_POST = "notifications.post"
    const val NOTIFICATIONS_SETTINGS = "notifications.settings"

    const val DEVICE_CONTROL = "device.control"
    const val DEVICE_POWER = "device.power"
    const val DEVICE_AUDIO = "device.audio"
    const val DIALOGS_CONTROL = "dialogs.control"

    const val FLOW_CONTROL = "flow.control"
    const val MAIL_CONTROL = "mail.control"
    const val AI_CONTROL = "ai.control"
    const val AGENT_CONTROL = "agent.control"
    const val EPUB_CONTROL = "epub.control"
    const val SPEECH_CONTROL = "speech.control"
    const val TTS_CONTROL = "tts.control"
    const val JAVA_INTEROP = "java.interop"
    const val ANDROID_OBJECTS = "android.objects"
    const val TASKS_CONTROL = "tasks.control"

    /** Read-only host info snapshot: host package, version and advisory locale/build date. */
    private const val KEY_HOST_INFO = "host_info"

    fun isHostInfoGranted(context: Context): Boolean = isGranted(context, KEY_HOST_INFO, true)

    fun setHostInfoGranted(context: Context, granted: Boolean) = setGranted(context, KEY_HOST_INFO, granted)

    /** Dynamic capability `ui.toast`: short text toasts, at most four per run. */
    private const val KEY_UI_TOAST = "ui_toast"

    /** Dynamic capability `device.info`: brand, model and Android version from `android.os.Build`, no identifiers. */
    private const val KEY_DEVICE_INFO = "device_info"

    fun isUiToastGranted(context: Context): Boolean = isGranted(context, KEY_UI_TOAST, true)

    fun setUiToastGranted(context: Context, granted: Boolean) = setGranted(context, KEY_UI_TOAST, granted)

    fun isDeviceInfoGranted(context: Context): Boolean = isGranted(context, KEY_DEVICE_INFO, true)

    fun setDeviceInfoGranted(context: Context, granted: Boolean) = setGranted(context, KEY_DEVICE_INFO, granted)

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

    fun isDatabaseControlGranted(context: Context): Boolean = isGranted(context, DATABASE_CONTROL, false)
    fun setDatabaseControlGranted(context: Context, granted: Boolean) = setGranted(context, DATABASE_CONTROL, granted)

    fun isSensorsControlGranted(context: Context): Boolean = isGranted(context, SENSORS_CONTROL, false)
    fun setSensorsControlGranted(context: Context, granted: Boolean) = setGranted(context, SENSORS_CONTROL, granted)

    fun isShellShizukuGranted(context: Context): Boolean = isGranted(context, SHELL_SHIZUKU, false)
    fun setShellShizukuGranted(context: Context, granted: Boolean) = setGranted(context, SHELL_SHIZUKU, granted)

    fun isShellRootGranted(context: Context): Boolean = isGranted(context, SHELL_ROOT, false)
    fun setShellRootGranted(context: Context, granted: Boolean) = setGranted(context, SHELL_ROOT, granted)

    fun isShellControlGranted(context: Context): Boolean = isGranted(context, SHELL_CONTROL, false)
    fun setShellControlGranted(context: Context, granted: Boolean) = setGranted(context, SHELL_CONTROL, granted)

    fun isEventsObserveGranted(context: Context): Boolean = isGranted(context, EVENTS_OBSERVE, false)
    fun setEventsObserveGranted(context: Context, granted: Boolean) = setGranted(context, EVENTS_OBSERVE, granted)

    fun isEventsNotificationGranted(context: Context): Boolean = isGranted(context, EVENTS_NOTIFICATION, false)
    fun setEventsNotificationGranted(context: Context, granted: Boolean) = setGranted(context, EVENTS_NOTIFICATION, granted)

    fun isEventsToastGranted(context: Context): Boolean = isGranted(context, EVENTS_TOAST, false)
    fun setEventsToastGranted(context: Context, granted: Boolean) = setGranted(context, EVENTS_TOAST, granted)

    fun isEventsKeyGranted(context: Context): Boolean = isGranted(context, EVENTS_KEY, false)
    fun setEventsKeyGranted(context: Context, granted: Boolean) = setGranted(context, EVENTS_KEY, granted)

    fun isNotificationsPostGranted(context: Context): Boolean = isGranted(context, NOTIFICATIONS_POST, false)
    fun setNotificationsPostGranted(context: Context, granted: Boolean) = setGranted(context, NOTIFICATIONS_POST, granted)

    fun isNotificationsSettingsGranted(context: Context): Boolean = isGranted(context, NOTIFICATIONS_SETTINGS, false)
    fun setNotificationsSettingsGranted(context: Context, granted: Boolean) = setGranted(context, NOTIFICATIONS_SETTINGS, granted)

    fun isDeviceControlGranted(context: Context): Boolean = isGranted(context, DEVICE_CONTROL, false)
    fun setDeviceControlGranted(context: Context, granted: Boolean) = setGranted(context, DEVICE_CONTROL, granted)

    fun isDevicePowerGranted(context: Context): Boolean = isGranted(context, DEVICE_POWER, false)
    fun setDevicePowerGranted(context: Context, granted: Boolean) = setGranted(context, DEVICE_POWER, granted)

    fun isDeviceAudioGranted(context: Context): Boolean = isGranted(context, DEVICE_AUDIO, false)
    fun setDeviceAudioGranted(context: Context, granted: Boolean) = setGranted(context, DEVICE_AUDIO, granted)

    fun isDialogsControlGranted(context: Context): Boolean = isGranted(context, DIALOGS_CONTROL, false)
    fun setDialogsControlGranted(context: Context, granted: Boolean) = setGranted(context, DIALOGS_CONTROL, granted)

    fun isFlowControlGranted(context: Context): Boolean = isGranted(context, FLOW_CONTROL, false)
    fun setFlowControlGranted(context: Context, granted: Boolean) = setGranted(context, FLOW_CONTROL, granted)

    fun isMailControlGranted(context: Context): Boolean = isGranted(context, MAIL_CONTROL, false)
    fun isAiControlGranted(context: Context): Boolean = isGranted(context, AI_CONTROL, false)
    fun isAgentControlGranted(context: Context): Boolean = isGranted(context, AGENT_CONTROL, false)
    fun isEpubControlGranted(context: Context): Boolean = isGranted(context, EPUB_CONTROL, false)
    fun setMailControlGranted(context: Context, granted: Boolean) = setGranted(context, MAIL_CONTROL, granted)
    fun setAiControlGranted(context: Context, granted: Boolean) = setGranted(context, AI_CONTROL, granted)
    fun setAgentControlGranted(context: Context, granted: Boolean) = setGranted(context, AGENT_CONTROL, granted)
    fun setEpubControlGranted(context: Context, granted: Boolean) = setGranted(context, EPUB_CONTROL, granted)

    fun isSpeechControlGranted(context: Context): Boolean = isGranted(context, SPEECH_CONTROL, false)
    fun setSpeechControlGranted(context: Context, granted: Boolean) = setGranted(context, SPEECH_CONTROL, granted)

    fun isTtsControlGranted(context: Context): Boolean = isGranted(context, TTS_CONTROL, false)
    fun setTtsControlGranted(context: Context, granted: Boolean) = setGranted(context, TTS_CONTROL, granted)

    fun isJavaInteropGranted(context: Context): Boolean = isGranted(context, JAVA_INTEROP, false)
    fun setJavaInteropGranted(context: Context, granted: Boolean) = setGranted(context, JAVA_INTEROP, granted)

    fun isAndroidObjectsGranted(context: Context): Boolean = isGranted(context, ANDROID_OBJECTS, false)
    fun setAndroidObjectsGranted(context: Context, granted: Boolean) = setGranted(context, ANDROID_OBJECTS, granted)

    fun isTasksControlGranted(context: Context): Boolean = isGranted(context, TASKS_CONTROL, false)
    fun setTasksControlGranted(context: Context, granted: Boolean) = setGranted(context, TASKS_CONTROL, granted)

    fun isConsoleControlGranted(context: Context): Boolean = isGranted(context, CONSOLE_CONTROL, false)
    fun setConsoleControlGranted(context: Context, granted: Boolean) = setGranted(context, CONSOLE_CONTROL, granted)

    fun isUiLayoutGranted(context: Context): Boolean = isGranted(context, UI_LAYOUT, false)
    fun setUiLayoutGranted(context: Context, granted: Boolean) = setGranted(context, UI_LAYOUT, granted)
    fun isUiComposeGranted(context: Context): Boolean = isGranted(context, UI_COMPOSE, false)
    fun setUiComposeGranted(context: Context, granted: Boolean) = setGranted(context, UI_COMPOSE, granted)

    /** The stored grant of [key], else [default] (`getBoolean(key, default)`; a value of another type throws, as before). */
    @Suppress("UNUSED_PARAMETER")
    private fun isGranted(context: Context, key: String, default: Boolean): Boolean =
        CredentialKeys.BUN_HOST_CAPABILITY_GRANT.key(key).storedOrNull() ?: default

    @Suppress("UNUSED_PARAMETER")
    private fun setGranted(context: Context, key: String, granted: Boolean) {
        CredentialKeys.BUN_HOST_CAPABILITY_GRANT.key(key).set(granted)
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
        if (isDatabaseControlGranted(context)) add(DATABASE_CONTROL)
        if (isSensorsControlGranted(context)) add(SENSORS_CONTROL)
        if (isShellControlGranted(context)) add(SHELL_CONTROL)
        if (isShellRootGranted(context)) add(SHELL_ROOT)
        if (isShellShizukuGranted(context)) add(SHELL_SHIZUKU)
        if (isEventsObserveGranted(context)) add(EVENTS_OBSERVE)
        if (isEventsNotificationGranted(context)) add(EVENTS_NOTIFICATION)
        if (isEventsToastGranted(context)) add(EVENTS_TOAST)
        if (isEventsKeyGranted(context)) add(EVENTS_KEY)
        if (isNotificationsPostGranted(context)) add(NOTIFICATIONS_POST)
        if (isNotificationsSettingsGranted(context)) add(NOTIFICATIONS_SETTINGS)
        if (isDeviceControlGranted(context)) add(DEVICE_CONTROL)
        if (isDevicePowerGranted(context)) add(DEVICE_POWER)
        if (isDeviceAudioGranted(context)) add(DEVICE_AUDIO)
        if (isDialogsControlGranted(context)) add(DIALOGS_CONTROL)
        if (isFlowControlGranted(context)) add(FLOW_CONTROL)
        if (isMailControlGranted(context)) add(MAIL_CONTROL)
        if (isAiControlGranted(context)) add(AI_CONTROL)
        if (isAgentControlGranted(context)) add(AGENT_CONTROL)
        if (isEpubControlGranted(context)) add(EPUB_CONTROL)
        if (isSpeechControlGranted(context)) add(SPEECH_CONTROL)
        if (isTtsControlGranted(context)) add(TTS_CONTROL)
        if (isJavaInteropGranted(context)) add(JAVA_INTEROP)
        if (isAndroidObjectsGranted(context)) add(ANDROID_OBJECTS)
        if (isTasksControlGranted(context)) add(TASKS_CONTROL)
        if (isConsoleControlGranted(context)) add(CONSOLE_CONTROL)
        }
    }

    internal fun validatePackagedCapabilities(capabilities: List<String>): List<String> {
        val supported = setOf(AGENT_CONTROL, AI_CONTROL, FLOW_CONTROL, MAIL_CONTROL, EPUB_CONTROL, SPEECH_CONTROL, TTS_CONTROL, UI_LAYOUT, UI_COMPOSE, "ui.toast", "device.info", TEXT_PINYIN, TEXT_PINYIN4J,
            TEXT_OPENCC, IMAGE_PROCESS, IMAGE_FILES, SCREEN_CAPTURE, SCREEN_RECORD, SCREEN_RECORD_MICROPHONE,
            SCREEN_RECORD_PLAYBACK, SCREEN_METRICS, CONSOLE_CONTROL, APP_CONTROL, ACCESSIBILITY_CONTROL, ACCESSIBILITY_GESTURE, CLIPBOARD_CONTROL, FILES_CONTROL, STORAGE_CONTROL, DATABASE_CONTROL, SENSORS_CONTROL, SHELL_CONTROL, SHELL_ROOT, SHELL_SHIZUKU, EVENTS_OBSERVE, EVENTS_NOTIFICATION, EVENTS_TOAST, EVENTS_KEY, NOTIFICATIONS_POST, NOTIFICATIONS_SETTINGS, DEVICE_CONTROL, DEVICE_POWER, DEVICE_AUDIO, DIALOGS_CONTROL, ANDROID_OBJECTS, TASKS_CONTROL, JAVA_INTEROP)
        require(capabilities.size <= supported.size && capabilities.all { it in supported }) { "Unknown Bun host capability in project.json" }
        require(capabilities.distinct().size == capabilities.size) { "Duplicate Bun host capability in project.json" }
        return capabilities.toList()
    }

}
