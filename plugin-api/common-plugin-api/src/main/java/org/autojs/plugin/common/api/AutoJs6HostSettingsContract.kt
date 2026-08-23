package org.autojs.plugin.common.api

/**
 * Versioned, read-only appearance settings contract exposed by AutoJs6 to official plugins.
 *
 * Call [METHOD_GET_SETTINGS] on [CONTENT_URI] with `ContentResolver.call()`. The AutoJs6 host
 * grants access only to enabled plugin packages signed as official AutoJs6 plugins and protected
 * by [PLUGIN_PERMISSION]. Consumers must reject protocol versions they do not understand.
 */
object AutoJs6HostSettingsContract {
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val AUTHORITY = "$HOST_PACKAGE_NAME.plugin.settings"
    const val CONTENT_URI = "content://$AUTHORITY"
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val PLUGIN_INFO_ACTION = "org.autojs.plugin.INFO"

    const val METHOD_GET_SETTINGS = "getSettings"
    const val PROTOCOL_VERSION = 1

    const val KEY_PROTOCOL_VERSION = "protocolVersion"
    const val KEY_HOST_PACKAGE_NAME = "hostPackageName"
    const val KEY_HOST_VERSION_CODE = "hostVersionCode"
    const val KEY_HOST_VERSION_NAME = "hostVersionName"
    const val KEY_THEME_COLOR_PRIMARY = "themeColorPrimary"
    const val KEY_THEME_COLOR_PRIMARY_DARK = "themeColorPrimaryDark"
    const val KEY_THEME_COLOR_ACCENT = "themeColorAccent"
    const val KEY_DARK_MODE_POLICY = "darkModePolicy"
    const val KEY_DARK_MODE_ACTIVE = "darkModeActive"
    const val KEY_LANGUAGE_TAG = "languageTag"
    const val KEY_RESOLVED_LANGUAGE_TAG = "resolvedLanguageTag"

    const val DARK_MODE_FOLLOW_SYSTEM = "FOLLOW_SYSTEM"
    const val DARK_MODE_LIGHT = "LIGHT"
    const val DARK_MODE_DARK = "DARK"
}
