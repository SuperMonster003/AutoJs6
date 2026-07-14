package org.autojs.autojs.apkbuilder.template

import android.content.Context
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginActions
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult
import org.autojs.plugin.apkbuilder.template.IApkBuildCallback
import org.autojs.plugin.apkbuilder.template.IApkBuildSession
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

    data class OpenedBuildSession(
        val target: AidlPluginHost.Discovered,
        val session: IApkBuildSession,
        val hostCompatibilityWarnings: List<String>,
    )

    suspend fun discover(context: Context) = host.discover(context)

    suspend fun openTemplate(context: Context, request: ApkBuilderTemplateRequest): OpenedTemplate? {
        val appContext = context.applicationContext
        val candidates = discover(appContext)
            .asSequence()
            .filter { it.pluginInfo != null }
            .filter { matchesTemplatePlugin(it.pluginInfo) }
            .filter { PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(appContext, it.serviceInfo.packageName) }
            .sortedWith(
                compareByDescending<AidlPluginHost.Discovered> { isExactHostVersionMatch(it.pluginInfo) }
                    .thenByDescending { it.pluginInfo?.versionCode ?: 0L }
                    .thenBy { it.serviceInfo.packageName }
            )
            .toList()

        val target = candidates.firstOrNull() ?: return null
        val warnings = hostCompatibilityWarnings(appContext, target.pluginInfo)
        val result = host.call(appContext, target, enforceHostVersionCompatibility = false) { proxy ->
            proxy.openTemplate(request)
        }
        return OpenedTemplate(target, result, warnings)
    }

    suspend fun hasRemoteBuildPlugin(
        context: Context,
        requiredProtocolVersion: Int = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
    ): Boolean {
        val appContext = context.applicationContext
        return discover(appContext)
            .any { item ->
                item.pluginInfo != null &&
                        matchesTemplatePlugin(item.pluginInfo) &&
                        PluginEnableStore.isEnabled(appContext, item.serviceInfo.packageName, true) &&
                        PluginTrustManager.isAuthorized(appContext, item.serviceInfo.packageName) &&
                        supportsRemoteBuild(item.pluginInfo, requiredProtocolVersion)
            }
    }

    suspend fun openBuildSession(
        context: Context,
        request: ApkBuildRequest,
        callback: IApkBuildCallback?,
    ): OpenedBuildSession? {
        val appContext = context.applicationContext
        val requiredProtocolVersion = request.requiredProtocolVersion.takeIf { it > 0 }
            ?: ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION
        val candidates = discover(appContext)
            .asSequence()
            .filter { it.pluginInfo != null }
            .filter { matchesTemplatePlugin(it.pluginInfo) }
            .filter { PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(appContext, it.serviceInfo.packageName) }
            .filter { supportsRemoteBuild(it.pluginInfo, requiredProtocolVersion) }
            .sortedWith(
                compareByDescending<AidlPluginHost.Discovered> { isExactHostVersionMatch(it.pluginInfo) }
                    .thenByDescending { remoteBuildProtocolVersion(it.pluginInfo) }
                    .thenByDescending { it.pluginInfo?.versionCode ?: 0L }
                    .thenBy { it.serviceInfo.packageName }
            )
            .toList()

        val target = candidates.firstOrNull() ?: return null
        val warnings = hostCompatibilityWarnings(appContext, target.pluginInfo)
        val session = host.call(appContext, target, enforceHostVersionCompatibility = false) { proxy ->
            proxy.openBuildSession(request, callback)
        } ?: return null
        return OpenedBuildSession(target, session, warnings)
    }

    suspend fun unavailableWarnings(context: Context): List<String> {
        val appContext = context.applicationContext
        val discovered = discover(appContext)
            .filter { it.pluginInfo == null || matchesTemplatePlugin(it.pluginInfo) }
        if (discovered.isEmpty()) {
            return emptyList()
        }

        val infoErrors = discovered
            .mapNotNull { item ->
                item.error?.let { error ->
                    "${item.serviceInfo.packageName}/${item.serviceInfo.name}: ${error.message ?: error}"
                }
            }
        val infoItems = discovered.filter { it.pluginInfo != null }
        val unauthorizedItems = infoItems.filterNot { PluginTrustManager.isAuthorized(appContext, it.serviceInfo.packageName) }
        if (unauthorizedItems.isNotEmpty()) {
            return (
                    infoErrors +
                            appContext.getString(
                                R.string.error_plugin_is_not_authorized_in_plugin_center,
                                unauthorizedItems.first().serviceInfo.packageName,
                            )
                    ).filter { it.isNotBlank() }.distinct()
        }

        val disabledItems = infoItems.filterNot { PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) }
        if (disabledItems.isNotEmpty()) {
            return (
                    infoErrors +
                            appContext.getString(
                                R.string.error_plugin_is_not_enabled_in_plugin_center,
                                disabledItems.first().serviceInfo.packageName,
                            )
                    ).filter { it.isNotBlank() }.distinct()
        }

        return infoErrors.filter { it.isNotBlank() }.distinct()
    }

    private fun matchesTemplatePlugin(pluginInfo: PluginInfo?): Boolean {
        pluginInfo ?: return false
        return pluginInfo.id == ApkBuilderTemplatePluginIds.ID ||
                pluginInfo.engine == ApkBuilderTemplatePluginIds.ENGINE
    }

    private fun supportsRemoteBuild(pluginInfo: PluginInfo?, requiredProtocolVersion: Int): Boolean {
        val capabilities = pluginInfo?.capabilities ?: return false
        @Suppress("DEPRECATION")
        if (!coerceBoolean(capabilities.get(ApkBuilderTemplateCapabilityKeys.SUPPORTS_REMOTE_BUILD))) {
            return false
        }
        return remoteBuildProtocolVersion(pluginInfo) >= requiredProtocolVersion
    }

    private fun remoteBuildProtocolVersion(pluginInfo: PluginInfo?): Int {
        val capabilities = pluginInfo?.capabilities ?: return 0
        @Suppress("DEPRECATION")
        return PluginCapabilityResolver.coerceLong(
            capabilities.get(ApkBuilderTemplateCapabilityKeys.REMOTE_BUILD_PROTOCOL_VERSION)
                ?: capabilities.get(ApkBuilderTemplateCapabilityKeys.PROTOCOL_VERSION)
        )?.toInt() ?: 0
    }

    private fun coerceBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.trim().lowercase() in setOf("true", "1", "yes", "on")
            is Number -> value.toInt() != 0
            else -> false
        }
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
                R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
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
