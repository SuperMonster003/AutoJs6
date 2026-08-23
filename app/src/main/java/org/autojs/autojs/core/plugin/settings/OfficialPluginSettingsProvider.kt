package org.autojs.autojs.core.plugin.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.AutoJs6HostSettingsContract

/** Read-only IPC endpoint guarded by signature permission and official-plugin verification. */
class OfficialPluginSettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != AutoJs6HostSettingsContract.METHOD_GET_SETTINGS) {
            throw IllegalArgumentException("Unsupported official plugin settings method: $method")
        }
        val context = requireNotNull(context)
        val callingPackage = callingPackage
        val facts = callerFacts(callingPackage, Binder.getCallingUid())
        val denial = OfficialPluginSettingsAccessPolicy.denial(facts)
        if (denial != null) {
            throw SecurityException("Official plugin settings access denied: ${denial.name}")
        }
        return appearanceSnapshot()
    }

    private fun callerFacts(callingPackage: String?, callingUid: Int): OfficialPluginSettingsCallerFacts {
        val context = requireNotNull(context)
        val packageManager = context.packageManager
        val packageName = callingPackage.orEmpty()
        val applicationEnabled = packageName.isNotEmpty() && isApplicationEnabled(packageName)
        return OfficialPluginSettingsCallerFacts(
            callingPackage = callingPackage,
            packagesForUid = packageManager.getPackagesForUid(callingUid).orEmpty().toSet(),
            applicationEnabled = applicationEnabled,
            protectedPluginEntryEnabled = applicationEnabled && hasProtectedPluginEntry(packageName),
            officialSignature = applicationEnabled && runCatching {
                PluginTrustManager.resolveTrustInfo(context, packageName).isOfficial
            }.getOrDefault(false),
        )
    }

    private fun isApplicationEnabled(packageName: String): Boolean {
        val packageManager = requireNotNull(context).packageManager
        val applicationInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrNull() ?: return false
        return when (packageManager.getApplicationEnabledSetting(packageName)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> applicationInfo.enabled
        }
    }

    private fun hasProtectedPluginEntry(packageName: String): Boolean {
        val packageManager = requireNotNull(context).packageManager
        val intent = Intent(AutoJs6HostSettingsContract.PLUGIN_INFO_ACTION).setPackage(packageName)
        val services = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrDefault(emptyList())
        return services.any { resolved ->
            resolved.serviceInfo?.let { service ->
                service.packageName == packageName && service.exported && service.enabled &&
                    service.permission == AutoJs6HostSettingsContract.PLUGIN_PERMISSION &&
                    service.applicationInfo.enabled
            } == true
        }
    }

    private fun appearanceSnapshot(): Bundle {
        val theme = ThemeColor.fromPreferences() ?: ThemeColorManager.defaultThemeColor
        val language = Language.getPrefLanguage()
        return Bundle().apply {
            putInt(
                AutoJs6HostSettingsContract.KEY_PROTOCOL_VERSION,
                AutoJs6HostSettingsContract.PROTOCOL_VERSION,
            )
            putString(AutoJs6HostSettingsContract.KEY_HOST_PACKAGE_NAME, BuildConfig.APPLICATION_ID)
            putLong(
                AutoJs6HostSettingsContract.KEY_HOST_VERSION_CODE,
                BuildConfig.VERSION_CODE.toLong(),
            )
            putString(
                AutoJs6HostSettingsContract.KEY_HOST_VERSION_NAME,
                BuildConfig.VERSION_NAME,
            )
            putInt(
                AutoJs6HostSettingsContract.KEY_THEME_COLOR_PRIMARY,
                theme.colorPrimary,
            )
            putInt(
                AutoJs6HostSettingsContract.KEY_THEME_COLOR_PRIMARY_DARK,
                theme.colorPrimaryDark,
            )
            putInt(
                AutoJs6HostSettingsContract.KEY_THEME_COLOR_ACCENT,
                theme.colorAccent,
            )
            putString(
                AutoJs6HostSettingsContract.KEY_DARK_MODE_POLICY,
                when {
                    ViewUtils.isAutoNightModeEnabled ->
                        AutoJs6HostSettingsContract.DARK_MODE_FOLLOW_SYSTEM
                    ViewUtils.isNightModeEnabled -> AutoJs6HostSettingsContract.DARK_MODE_DARK
                    else -> AutoJs6HostSettingsContract.DARK_MODE_LIGHT
                },
            )
            putBoolean(
                AutoJs6HostSettingsContract.KEY_DARK_MODE_ACTIVE,
                ViewUtils.isNightModeEnabled,
            )
            putString(AutoJs6HostSettingsContract.KEY_LANGUAGE_TAG, language.languageTag)
            putString(
                AutoJs6HostSettingsContract.KEY_RESOLVED_LANGUAGE_TAG,
                language.getLocalCompatibleLanguageTag(),
            )
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
