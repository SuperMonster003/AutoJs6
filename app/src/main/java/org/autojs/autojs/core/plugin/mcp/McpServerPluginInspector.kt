package org.autojs.autojs.core.plugin.mcp

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.mcp.server.api.McpServerActions
import org.autojs.plugin.mcp.server.api.McpServerIds

data class McpServerInspection(
    val state: McpServerUiState,
    val requiredHostVersion: Long = McpServerIds.REQUIRED_HOST_VERSION_CODE,
    val signer: String = "",
)

/** Package metadata is inspected before any Binder call or activation side effect. */
object McpServerPluginInspector {
    const val CONFIRMATION_STORE = "mcp_server_trust_confirmation"

    @Suppress("DEPRECATION")
    fun inspect(context: Context): McpServerInspection {
        val pm = context.packageManager
        val pkg = try {
            pm.getPackageInfo(McpServerIds.DEFAULT_PACKAGE_NAME, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (_: PackageManager.NameNotFoundException) {
            return McpServerInspection(McpServerUiState.NOT_INSTALLED)
        }
        val app = pkg.applicationInfo ?: return McpServerInspection(McpServerUiState.FAILED)
        val enabled = when (pm.getApplicationEnabledSetting(McpServerIds.DEFAULT_PACKAGE_NAME)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> app.enabled
        }
        val serviceNames = pm.queryIntentServices(
            Intent(McpServerActions.SERVICE_ACTION).addCategory(McpServerActions.SERVICE_CATEGORY).setPackage(McpServerIds.DEFAULT_PACKAGE_NAME),
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        ).mapNotNull { it.serviceInfo?.name }.toSet()
        val services = pkg.services.orEmpty().filter { it.name in serviceNames && it.permission == McpServerActions.PLUGIN_PERMISSION && it.exported }
        val required = services.mapNotNull { service ->
            service.metaData?.get("requiresHostVersion")?.let(PluginCapabilityResolver::coerceLong)
        }.maxOrNull() ?: McpServerIds.REQUIRED_HOST_VERSION_CODE
        val trust = PluginTrustManager.resolveTrustInfo(context, McpServerIds.DEFAULT_PACKAGE_NAME)
        val signer = trust.fingerprintsSha256.sorted().joinToString(",")
        val confirmed = signer.isNotEmpty() && context.getSharedPreferences(CONFIRMATION_STORE, Context.MODE_PRIVATE).getBoolean(signer, false)
        val facts = McpServerPluginFacts(
            installed = true,
            applicationEnabled = enabled,
            stopped = app.flags and ApplicationInfo.FLAG_STOPPED != 0,
            pluginEnabled = PluginEnableStore.isEnabled(
                context,
                McpServerIds.DEFAULT_PACKAGE_NAME,
                defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(McpServerIds.ENGINE, trust.authorizedState),
            ),
            authorized = PluginTrustManager.isAuthorized(context, McpServerIds.DEFAULT_PACKAGE_NAME),
            trustedSigner = trust.isOfficial || trust.isTrusted,
            signerConfirmed = confirmed,
            compatible = required <= BuildConfig.VERSION_CODE && services.isNotEmpty(),
        )
        return McpServerInspection(McpServerUiPolicy.availability(facts), required, signer)
    }
}
