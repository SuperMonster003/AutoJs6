package org.autojs.autojs.core.plugin.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds

data class ThreeStoveAgentInspection(
    val state: ThreeStoveAgentUiState,
    val requiredHostVersion: Long = ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE,
    val signer: String = "",
)

/** Package metadata is inspected before any Binder call or activation side effect. */
object ThreeStoveAgentPluginInspector {
    @Suppress("DEPRECATION")
    fun inspect(context: Context): ThreeStoveAgentInspection {
        val pm = context.packageManager
        val pkg = try {
            pm.getPackageInfo(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (_: PackageManager.NameNotFoundException) {
            return ThreeStoveAgentInspection(ThreeStoveAgentUiState.NOT_INSTALLED)
        }
        val app = pkg.applicationInfo ?: return ThreeStoveAgentInspection(ThreeStoveAgentUiState.FAILED)
        val enabled = when (pm.getApplicationEnabledSetting(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> app.enabled
        }
        val serviceNames = pm.queryIntentServices(
            Intent(ThreeStoveAgentActions.SERVICE_ACTION).addCategory(ThreeStoveAgentActions.SERVICE_CATEGORY).setPackage(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        ).mapNotNull { it.serviceInfo?.name }.toSet()
        val services = pkg.services.orEmpty().filter { it.name in serviceNames && it.permission == ThreeStoveAgentActions.PLUGIN_PERMISSION && it.exported }
        val required = services.mapNotNull { service ->
            service.metaData?.get("requiresHostVersion")?.let(PluginCapabilityResolver::coerceLong)
        }.maxOrNull() ?: ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE
        val trust = PluginTrustManager.resolveTrustInfo(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
        val signer = trust.fingerprintsSha256.sorted().joinToString(",")
        // The confirmed signers are the named store three_stove_agent_trust_confirmation (CredentialKeys, P3.4).
        val confirmed = signer.isNotEmpty() && CredentialKeys.THREE_STOVE_AGENT_TRUST_CONFIRMATION.key(signer).get()
        val facts = ThreeStoveAgentPluginFacts(
            installed = true,
            applicationEnabled = enabled,
            stopped = app.flags and ApplicationInfo.FLAG_STOPPED != 0,
            pluginEnabled = PluginEnableStore.isEnabled(
                context,
                ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME,
                defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(ThreeStoveAgentIds.ENGINE, trust.authorizedState),
            ),
            authorized = PluginTrustManager.isAuthorized(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME),
            trustedSigner = trust.isOfficial || trust.isTrusted,
            signerConfirmed = confirmed,
            compatible = required in ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE..BuildConfig.VERSION_CODE.toLong()
                && services.size == 1 && services.single().enabled,
        )
        return ThreeStoveAgentInspection(ThreeStoveAgentUiPolicy.availability(facts), required, signer)
    }
}
