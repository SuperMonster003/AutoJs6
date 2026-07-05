package org.autojs.autojs.apkbuilder.template

import android.content.Context
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginActions
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult
import org.autojs.plugin.apkbuilder.template.IApkBuilderTemplatePlugin
import org.autojs.plugin.common.api.PluginInfo

object ApkBuilderTemplatePluginHost {

    const val ACTION_APK_BUILDER = ApkBuilderTemplatePluginActions.APK_BUILDER

    private val host = AidlPluginHost(
        action = ACTION_APK_BUILDER,
        tag = "ApkBuilderTemplatePluginHost",
        moduleLabel = "APK Builder Template plugin",
        proxyFactory = { binder -> IApkBuilderTemplatePlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    data class OpenedTemplate(
        val target: AidlPluginHost.Discovered,
        val result: ApkBuilderTemplateResult,
        val hostCompatibilityWarnings: List<String>,
    )

    suspend fun discover(context: Context) = host.discover(context)

    suspend fun openTemplate(context: Context, request: ApkBuilderTemplateRequest): OpenedTemplate? {
        val appContext = context.applicationContext
        val candidates = discover(appContext)
            .filter { it.pluginInfo != null }
            .filter { matchesTemplatePlugin(it.pluginInfo) }
            .filter { PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(appContext, it.serviceInfo.packageName) }
            .sortedWith(
                compareByDescending<AidlPluginHost.Discovered> { isExactHostVersionMatch(it.pluginInfo) }
                    .thenByDescending { it.pluginInfo?.versionCode ?: 0L }
                    .thenBy { it.serviceInfo.packageName }
            )

        val target = candidates.firstOrNull() ?: return null
        val warnings = hostCompatibilityWarnings(appContext, target.pluginInfo)
        val result = host.call(appContext, target, enforceHostVersionCompatibility = false) { proxy ->
            proxy.openTemplate(request)
        }
        return OpenedTemplate(target, result, warnings)
    }

    private fun matchesTemplatePlugin(pluginInfo: PluginInfo?): Boolean {
        pluginInfo ?: return false
        return pluginInfo.id == ApkBuilderTemplatePluginIds.ID ||
                pluginInfo.engine == ApkBuilderTemplatePluginIds.ENGINE
    }

    private fun isExactHostVersionMatch(pluginInfo: PluginInfo?): Boolean {
        val capabilities = pluginInfo?.capabilities ?: return false
        @Suppress("DEPRECATION")
        val builtForHostVersionCode = PluginCapabilityResolver.coerceLong(
            capabilities.get(ApkBuilderTemplateCapabilityKeys.BUILT_FOR_HOST_VERSION_CODE)
        )
        val builtForHostVersionName = capabilities.getString(ApkBuilderTemplateCapabilityKeys.BUILT_FOR_HOST_VERSION_NAME)
        return builtForHostVersionCode == BuildConfig.VERSION_CODE.toLong() &&
                builtForHostVersionName == BuildConfig.VERSION_NAME
    }

    private fun hostCompatibilityWarnings(context: Context, pluginInfo: PluginInfo?): List<String> {
        val warnings = mutableListOf<String>()
        val requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(pluginInfo?.capabilities)
        if (!PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), requiresHostVersion)) {
            warnings += context.getString(
                org.autojs.autojs6.R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                requiresHostVersion ?: 0L,
                BuildConfig.VERSION_CODE.toLong(),
            )
        }
        if (!isExactHostVersionMatch(pluginInfo)) {
            warnings += "Plugin was not built for this AutoJs6 host version: plugin=${pluginInfo?.versionName}/${pluginInfo?.versionCode}, host=${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}"
        }
        return warnings
    }
}
