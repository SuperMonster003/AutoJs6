package org.autojs.autojs.apkbuilder.template

import android.content.Context
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginAbiUtils
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.util.AndroidUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkKeyStoreRequest
import org.autojs.plugin.apkbuilder.template.ApkKeyStoreResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCompatibilityPolicy
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginActions
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateRequest
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult
import org.autojs.plugin.apkbuilder.template.IApkBuildCallback
import org.autojs.plugin.apkbuilder.template.IApkBuildSession
import org.autojs.plugin.apkbuilder.template.IApkBuilderTemplatePlugin
import org.autojs.plugin.common.api.PluginInfo
import java.io.Closeable

object ApkBuilderTemplatePluginHost {

    const val ACTION_APK_BUILDER = ApkBuilderTemplatePluginActions.APK_BUILDER

    private val host = AidlPluginHost(
        action = ACTION_APK_BUILDER,
        tag = "ApkBuilderTemplatePluginHost",
        moduleLabel = "APK Builder Template plugin",
        proxyFactory = { binder -> IApkBuilderTemplatePlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
        preBindValidator = { context, serviceInfo ->
            ApkBuilderPluginTrustPolicy.requireAccepted(context, serviceInfo.packageName)
        },
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
        val bindingLease: Closeable,
    )

    data class ResolvedRemoteBuilder(
        val target: AidlPluginHost.Discovered,
        val hostCompatibilityWarnings: List<String>,
    )

    suspend fun discover(context: Context) = host.discover(context)

    suspend fun openTemplate(context: Context, request: ApkBuilderTemplateRequest): OpenedTemplate? {
        val appContext = context.applicationContext
        val candidates = discover(appContext)
            .asSequence()
            .filter { isOfficialTemplatePlugin(appContext, it) }
            .filter { isDeclaredHostVersionCompatible(it.pluginInfo) }
            .filter { isDeviceAbiCompatible(it.pluginInfo) }
            .filter { supportsTemplateProtocol(it.pluginInfo, request.requiredProtocolVersion) }
            .sortedWith(
                compareByDescending<AidlPluginHost.Discovered> { isExactHostVersionMatch(it.pluginInfo) }
                    .thenByDescending { it.pluginInfo?.versionCode ?: 0L }
                    .thenBy { it.serviceInfo.packageName }
            )
            .toList()

        val target = candidates.firstOrNull() ?: return null
        val warnings = hostCompatibilityWarnings(appContext, target.pluginInfo)
        ensureStrictTarget(appContext, target)
        val result = host.call(
            context = appContext,
            target = target,
            enforceHostVersionCompatibility = true,
            enforceAuthorization = false,
        ) { proxy ->
            proxy.openTemplate(request)
        }
        return OpenedTemplate(target, result, warnings)
    }

    suspend fun hasRemoteBuildPlugin(
        context: Context,
        requiredProtocolVersion: Int = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
        preferredPackageName: String? = null,
    ): Boolean {
        return resolveRemoteBuilder(context, requiredProtocolVersion, preferredPackageName) != null
    }

    suspend fun resolveRemoteBuilder(
        context: Context,
        requiredProtocolVersion: Int = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
        preferredPackageName: String? = null,
    ): ResolvedRemoteBuilder? {
        val appContext = context.applicationContext
        val target = remoteBuildCandidates(appContext, requiredProtocolVersion)
            .firstOrNull { preferredPackageName == null || it.serviceInfo.packageName == preferredPackageName }
            ?: return null
        return ResolvedRemoteBuilder(
            target = target,
            hostCompatibilityWarnings = hostCompatibilityWarnings(appContext, target.pluginInfo),
        )
    }

    suspend fun openBuildSession(
        context: Context,
        request: ApkBuildRequest,
        callback: IApkBuildCallback?,
        preferredPackageName: String? = null,
    ): OpenedBuildSession? {
        val appContext = context.applicationContext
        val requiredProtocolVersion = request.requiredProtocolVersion.takeIf { it > 0 }
            ?: ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION
        val candidates = remoteBuildCandidates(appContext, requiredProtocolVersion)
            .asSequence()
            .filter { preferredPackageName == null || it.serviceInfo.packageName == preferredPackageName }
            .toList()

        val target = candidates.firstOrNull() ?: return null
        val warnings = hostCompatibilityWarnings(appContext, target.pluginInfo)
        ensureStrictTarget(appContext, target)
        val leasedSession = host.callWithDedicatedBindingLease(
            context = appContext,
            target = target,
            enforceHostVersionCompatibility = true,
            enforceAuthorization = false,
            retryOnRemoteException = false,
        ) { proxy ->
            proxy.openBuildSession(request, callback)
        }
        val session = leasedSession.value
        if (session == null) {
            leasedSession.bindingLease.close()
            return null
        }
        return OpenedBuildSession(target, session, warnings, leasedSession.bindingLease)
    }

    suspend fun manageKeyStore(
        context: Context,
        request: ApkKeyStoreRequest,
        preferredPackageName: String? = null,
    ): ApkKeyStoreResult? {
        val appContext = context.applicationContext
        val target = remoteBuildCandidates(appContext, ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION)
            .firstOrNull { candidate ->
                (preferredPackageName == null || candidate.serviceInfo.packageName == preferredPackageName) &&
                        supportsKeyStoreOperations(candidate.pluginInfo)
            }
            ?: return null
        ensureStrictTarget(appContext, target)
        return host.call(
            context = appContext,
            target = target,
            enforceHostVersionCompatibility = true,
            enforceAuthorization = false,
            retryOnRemoteException = false,
        ) { proxy ->
            proxy.manageKeyStore(request)
        }
    }

    private suspend fun remoteBuildCandidates(
        context: Context,
        requiredProtocolVersion: Int,
    ): List<AidlPluginHost.Discovered> {
        return discover(context)
            .asSequence()
            .filter { isOfficialTemplatePlugin(context, it) }
            .filter { isDeclaredHostVersionCompatible(it.pluginInfo) }
            .filter { isDeviceAbiCompatible(it.pluginInfo) }
            .filter { supportsRemoteBuild(it.pluginInfo, requiredProtocolVersion) }
            .sortedWith(
                compareByDescending<AidlPluginHost.Discovered> { isExactHostVersionMatch(it.pluginInfo) }
                    .thenByDescending { remoteBuildProtocolVersion(it.pluginInfo) }
                    .thenByDescending { it.pluginInfo?.versionCode ?: 0L }
                    .thenBy { it.serviceInfo.packageName }
            )
            .toList()
    }

    suspend fun unavailableWarnings(context: Context): List<String> {
        val appContext = context.applicationContext
        val discovered = discover(appContext)
            .filter { it.serviceInfo.packageName == ApkBuilderTemplatePluginIds.PACKAGE_NAME }
        if (discovered.isEmpty()) {
            return emptyList()
        }

        val untrustedSignatureItems = discovered.filterNot { item ->
            ApkBuilderPluginTrustPolicy.isAccepted(appContext, item.serviceInfo.packageName)
        }
        if (untrustedSignatureItems.isNotEmpty()) {
            val packageName = untrustedSignatureItems.first().serviceInfo.packageName
            return listOf(
                appContext.getString(
                    R.string.error_apk_builder_template_untrusted_signing_certificate,
                    packageName,
                    ApkBuilderPluginTrustPolicy.fingerprintSummary(appContext, packageName),
                )
            )
        }

        val infoErrors = discovered
            .mapNotNull { item ->
                item.error?.let { error ->
                    "${item.serviceInfo.packageName}/${item.serviceInfo.name}: ${error.message ?: error}"
                }
            }
        val infoItems = discovered.filter {
            it.pluginInfo != null && matchesTemplatePlugin(it.pluginInfo)
        }
        val invalidMetadataItems = discovered.filter {
            it.pluginInfo != null && !matchesTemplatePlugin(it.pluginInfo)
        }
        if (invalidMetadataItems.isNotEmpty()) {
            return (
                    infoErrors +
                            "Official APK Builder plugin metadata is invalid: both id=${ApkBuilderTemplatePluginIds.ID} " +
                            "and engine=${ApkBuilderTemplatePluginIds.ENGINE} are required."
                    ).filter { it.isNotBlank() }.distinct()
        }

        val unauthorizedItems = infoItems.filterNot {
            ApkBuilderPluginTrustPolicy.isAccepted(appContext, it.serviceInfo.packageName)
        }
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

        val abiIncompatibleItems = infoItems.filter {
            PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) &&
                    ApkBuilderPluginTrustPolicy.isAccepted(appContext, it.serviceInfo.packageName) &&
                    !isDeviceAbiCompatible(it.pluginInfo)
        }
        if (abiIncompatibleItems.isNotEmpty()) {
            val info = abiIncompatibleItems.first().pluginInfo
            val pluginAbis = PluginAbiUtils.formatAbiListOrFallback(
                info?.supportedAbis?.asList().orEmpty(),
                "unknown",
            )
            val deviceAbis = PluginAbiUtils.formatAbiListOrFallback(
                AndroidUtils.deviceFilteredAbiList,
                "unknown",
            )
            return (
                    infoErrors +
                            "Installed APK Builder plugin variant ${info?.variant ?: "unknown"} is incompatible " +
                            "with this device ABI: plugin=$pluginAbis, device=$deviceAbis"
                    ).filter { it.isNotBlank() }.distinct()
        }

        val hostIncompatibleItems = infoItems.filter {
            PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) &&
                    ApkBuilderPluginTrustPolicy.isAccepted(appContext, it.serviceInfo.packageName) &&
                    isDeviceAbiCompatible(it.pluginInfo) &&
                    !isDeclaredHostVersionCompatible(it.pluginInfo)
        }
        if (hostIncompatibleItems.isNotEmpty()) {
            val decision = hostCompatibilityDecision(hostIncompatibleItems.first().pluginInfo)
            return (
                    infoErrors +
                            appContext.getString(
                                R.string.error_apk_builder_template_host_version_outside_range,
                                decision.declaration.minHostVersionCode,
                                decision.declaration.maxHostVersionCode,
                                BuildConfig.VERSION_CODE,
                            )
                    ).filter { it.isNotBlank() }.distinct()
        }

        val remoteBuildUnavailableItems = infoItems.filter {
            PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) &&
                    ApkBuilderPluginTrustPolicy.isAccepted(appContext, it.serviceInfo.packageName) &&
                    isDeviceAbiCompatible(it.pluginInfo) &&
                    isDeclaredHostVersionCompatible(it.pluginInfo) &&
                    !supportsRemoteBuild(
                        pluginInfo = it.pluginInfo,
                        requiredProtocolVersion = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
                    )
        }
        if (remoteBuildUnavailableItems.isNotEmpty()) {
            return (
                    infoErrors +
                            appContext.getString(
                                R.string.error_apk_builder_remote_build_not_supported_by_plugin,
                            )
                    ).filter { it.isNotBlank() }.distinct()
        }

        return infoErrors.filter { it.isNotBlank() }.distinct()
    }

    private fun matchesTemplatePlugin(pluginInfo: PluginInfo?): Boolean {
        pluginInfo ?: return false
        return pluginInfo.id == ApkBuilderTemplatePluginIds.ID &&
                pluginInfo.engine == ApkBuilderTemplatePluginIds.ENGINE
    }

    private fun isOfficialTemplatePlugin(
        context: Context,
        candidate: AidlPluginHost.Discovered,
    ): Boolean {
        val packageName = candidate.serviceInfo.packageName
        return packageName == ApkBuilderTemplatePluginIds.PACKAGE_NAME &&
                matchesTemplatePlugin(candidate.pluginInfo) &&
                PluginEnableStore.isEnabled(context, packageName, true) &&
                ApkBuilderPluginTrustPolicy.isAccepted(context, packageName)
    }

    private fun ensureStrictTarget(
        context: Context,
        candidate: AidlPluginHost.Discovered,
    ) {
        check(isOfficialTemplatePlugin(context, candidate)) {
            "APK Builder plugin identity or signature changed before invocation."
        }
        check(isDeclaredHostVersionCompatible(candidate.pluginInfo)) {
            "APK Builder plugin is outside its declared AutoJs6 compatibility range."
        }
        check(isDeviceAbiCompatible(candidate.pluginInfo)) {
            "APK Builder plugin variant is incompatible with this device ABI."
        }
    }

    internal fun isDeviceAbiCompatible(
        pluginInfo: PluginInfo?,
        deviceAbis: Collection<String> = AndroidUtils.deviceFilteredAbiList,
    ): Boolean {
        return ApkBuilderTemplateAbiPolicy.isCompatible(pluginInfo?.supportedAbis?.asList(), deviceAbis)
    }

    private fun isDeclaredHostVersionCompatible(pluginInfo: PluginInfo?): Boolean {
        return hostCompatibilityDecision(pluginInfo).isCompatible
    }

    private fun hostCompatibilityDecision(
        pluginInfo: PluginInfo?,
    ): ApkBuilderTemplateCompatibilityPolicy.Decision {
        val declaration = ApkBuilderTemplateCompatibilityPolicy.declarationFromCapabilities(
            capabilities = pluginInfo?.capabilities,
            fallbackBuiltForHostVersionCode = 0L,
        )
        return ApkBuilderTemplateCompatibilityPolicy.evaluate(
            declaration = declaration,
            actualHostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        )
    }

    private fun supportsTemplateProtocol(pluginInfo: PluginInfo?, requiredProtocolVersion: Int): Boolean {
        if (requiredProtocolVersion <= 0) return true
        val capabilities = pluginInfo?.capabilities ?: return false
        @Suppress("DEPRECATION")
        val protocolVersion = PluginCapabilityResolver.coerceLong(
            capabilities.get(ApkBuilderTemplateCapabilityKeys.PROTOCOL_VERSION)
        )?.toInt() ?: 0
        return protocolVersion >= requiredProtocolVersion
    }

    private fun supportsRemoteBuild(
        pluginInfo: PluginInfo?,
        requiredProtocolVersion: Int,
    ): Boolean {
        val capabilities = pluginInfo?.capabilities
        @Suppress("DEPRECATION")
        val capabilityAdvertised = coerceBoolean(
            capabilities?.get(ApkBuilderTemplateCapabilityKeys.SUPPORTS_REMOTE_BUILD)
        )
        return RemoteApkBuildAdmissionPolicy.evaluate(
            capabilityAdvertised = capabilityAdvertised,
            advertisedProtocolVersion = remoteBuildProtocolVersion(pluginInfo),
            requiredProtocolVersion = requiredProtocolVersion,
        ) == RemoteApkBuildAdmissionPolicy.Decision.ALLOW
    }

    private fun supportsKeyStoreOperations(pluginInfo: PluginInfo?): Boolean {
        val capabilities = pluginInfo?.capabilities ?: return false
        @Suppress("DEPRECATION")
        if (!coerceBoolean(capabilities.get(ApkBuilderTemplateCapabilityKeys.SUPPORTS_KEYSTORE_OPERATIONS))) {
            return false
        }
        @Suppress("DEPRECATION")
        val apiVersion = PluginCapabilityResolver.coerceLong(
            capabilities.get(ApkBuilderTemplateCapabilityKeys.KEYSTORE_API_VERSION)
        )?.toInt() ?: return false
        return apiVersion == ApkBuilderTemplateProtocol.KEYSTORE_VERSION
    }

    private fun remoteBuildProtocolVersion(pluginInfo: PluginInfo?): Int {
        val capabilities = pluginInfo?.capabilities ?: return 0
        @Suppress("DEPRECATION")
        return PluginCapabilityResolver.coerceLong(
            capabilities.get(ApkBuilderTemplateCapabilityKeys.REMOTE_BUILD_PROTOCOL_VERSION)
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
        val decision = hostCompatibilityDecision(pluginInfo)
        if (decision.level == ApkBuilderTemplateCompatibilityPolicy.Level.PATCH_COMPATIBLE) {
            warnings += context.getString(
                R.string.hint_apk_builder_template_patch_compatible_host,
                decision.declaration.builtForHostVersionCode,
                decision.actualHostVersionCode,
                decision.declaration.minHostVersionCode,
                decision.declaration.maxHostVersionCode,
            )
        } else if (!isExactHostVersionMatch(pluginInfo)) {
            warnings += "Plugin was not built for this AutoJs6 host version: plugin=${pluginInfo?.versionName}/${pluginInfo?.versionCode}, host=${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}"
        }
        return warnings
    }
}
