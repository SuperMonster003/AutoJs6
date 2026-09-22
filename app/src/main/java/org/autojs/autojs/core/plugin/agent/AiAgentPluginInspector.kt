package org.autojs.autojs.core.plugin.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.autojs.plugin.ai.agent.api.AiAgentIds

data class AiAgentInspection(
    val state: AiAgentUiState,
    val requiredHostVersion: Long = AiAgentIds.REQUIRED_HOST_VERSION_CODE,
    val signer: String = "",
)

/** Package metadata is inspected before any Binder call or activation side effect. */
object AiAgentPluginInspector {
    const val CONFIRMATION_STORE = "ai_agent_trust_confirmation"

    @Suppress("DEPRECATION")
    fun inspect(context: Context): AiAgentInspection {
        val pm = context.packageManager
        val pkg = try {
            pm.getPackageInfo(AiAgentIds.DEFAULT_PACKAGE_NAME, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (_: PackageManager.NameNotFoundException) {
            return AiAgentInspection(AiAgentUiState.NOT_INSTALLED)
        }
        val app = pkg.applicationInfo ?: return AiAgentInspection(AiAgentUiState.FAILED)
        val enabled = when (pm.getApplicationEnabledSetting(AiAgentIds.DEFAULT_PACKAGE_NAME)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> app.enabled
        }
        val serviceNames = pm.queryIntentServices(
            Intent(AiAgentActions.SERVICE_ACTION).addCategory(AiAgentActions.SERVICE_CATEGORY).setPackage(AiAgentIds.DEFAULT_PACKAGE_NAME)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        ).mapNotNull { it.serviceInfo?.name }.toSet()
        val services = pkg.services.orEmpty().filter { it.name in serviceNames && it.permission == AiAgentActions.PLUGIN_PERMISSION && it.exported }
        val required = services.mapNotNull { service ->
            service.metaData?.get("requiresHostVersion")?.let(PluginCapabilityResolver::coerceLong)
        }.maxOrNull() ?: AiAgentIds.REQUIRED_HOST_VERSION_CODE
        val trust = PluginTrustManager.resolveTrustInfo(context, AiAgentIds.DEFAULT_PACKAGE_NAME)
        val signer = trust.fingerprintsSha256.sorted().joinToString(",")
        val confirmed = signer.isNotEmpty() && context.getSharedPreferences(CONFIRMATION_STORE, Context.MODE_PRIVATE).getBoolean(signer, false)
        val facts = AiAgentPluginFacts(
            installed = true,
            applicationEnabled = enabled,
            stopped = app.flags and ApplicationInfo.FLAG_STOPPED != 0,
            pluginEnabled = PluginEnableStore.isEnabled(
                context,
                AiAgentIds.DEFAULT_PACKAGE_NAME,
                defaultEnabled = false,
            ),
            authorized = PluginTrustManager.isAuthorized(context, AiAgentIds.DEFAULT_PACKAGE_NAME),
            trustedSigner = trust.isOfficial || trust.isTrusted,
            signerConfirmed = confirmed,
            compatible = required in AiAgentIds.REQUIRED_HOST_VERSION_CODE..BuildConfig.VERSION_CODE.toLong()
                && services.size == 1 && services.single().enabled,
        )
        return AiAgentInspection(AiAgentUiPolicy.availability(facts), required, signer)
    }
}
