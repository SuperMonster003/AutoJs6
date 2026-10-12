package org.autojs.autojs.core.plugin.autojs4

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.autojs4.runtime.api.AutoJs4PluginActions
import org.autojs.plugin.autojs4.runtime.api.AutoJs4PluginCapabilityKeys
import org.autojs.plugin.autojs4.runtime.api.AutoJs4PluginIds
import org.autojs.plugin.autojs4.runtime.api.AutoJs4RuntimeContract
import org.autojs.plugin.autojs4.runtime.api.IAutoJs4RuntimeCallback
import org.autojs.plugin.autojs4.runtime.api.IAutoJs4RuntimePlugin

/**
 * Discovery, trust and Binder access for the AutoJs4 Runtime plugin (protocol V1). The plugin
 * identity is pinned at selection time and re-checked before every call, like the QuickJS client.
 *
 * zh-CN: AutoJs4 Runtime 插件 (协议 V1) 的发现, 信任与 Binder 访问. 选择时固定插件身份, 每次调用前
 * 重新校验, 与 QuickJS 客户端同构.
 */
internal object AutoJs4RuntimePluginClient {
    private val host = AidlPluginHost(
        action = AutoJs4PluginActions.RUNTIME,
        category = AutoJs4PluginActions.CATEGORY,
        tag = "AutoJs4RuntimePluginClient",
        moduleLabel = "AutoJs4 Runtime",
        proxyFactory = IAutoJs4RuntimePlugin.Stub::asInterface,
        infoReader = { runtime ->
            val descriptor = runtime.asBinder().interfaceDescriptor
            check(descriptor == AutoJs4RuntimeContract.BINDER_DESCRIPTOR) {
                "Unexpected AutoJs4 runtime Binder descriptor: $descriptor"
            }
            runtime.info
        },
        preBindValidator = ::validateServiceForNewCall,
    )

    internal class Selection internal constructor(
        internal val discovered: AidlPluginHost.Discovered,
        internal val identity: ProviderIdentity,
    ) {
        val providerUid: Int
            get() = identity.uid

        val autoJs4VersionName: String?
            get() = discovered.pluginInfo?.capabilities?.getString(AutoJs4PluginCapabilityKeys.AUTOJS4_VERSION_NAME)
    }

    internal data class ProviderIdentity(
        val component: ComponentName,
        val uid: Int,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val signerFingerprints: Set<String>,
    )

    /** Acceptance Bundle plus the runtime Binder so that callers can observe plugin death. */
    internal data class Admission(val acceptance: Bundle, val binder: IBinder)

    suspend fun select(context: Context): Selection {
        val appContext = context.applicationContext
        val discovered = host.selectOrThrow(
            context = appContext,
            engineId = AutoJs4PluginIds.ID,
            engine = AutoJs4PluginIds.ENGINE,
            variant = AutoJs4PluginIds.VARIANT_DEFAULT,
            label = "AutoJs4 Runtime",
        )
        validatePluginInfo(discovered)
        return Selection(discovered, captureIdentity(appContext, discovered.serviceInfo))
    }

    suspend fun runtimeInfo(context: Context, selection: Selection): Bundle {
        val appContext = context.applicationContext
        ensureEligibleForNewCall(appContext, selection)
        return host.call(
            context = appContext,
            target = selection.discovered,
            retryOnRemoteException = true,
        ) { runtime -> runtime.runtimeInfo }.also { runtimeInfo ->
            validateRuntimeInfo(selection, runtimeInfo)
        }
    }

    /**
     * Starts an execution and keeps a dedicated service binding alive until the returned lease
     * is closed, so the plugin process is not unbound while a long AutoJs4 script runs.
     */
    suspend fun runScript(
        context: Context,
        selection: Selection,
        request: Bundle,
        callback: IAutoJs4RuntimeCallback,
    ): AidlPluginHost.LeasedCall<Admission> {
        val appContext = context.applicationContext
        ensureEligibleForNewCall(appContext, selection)
        val executionId = request.getString(AutoJs4RuntimeContract.KEY_EXECUTION_ID).orEmpty()
        return host.callWithDedicatedBindingLease(
            context = appContext,
            target = selection.discovered,
            retryOnRemoteException = false,
        ) { runtime ->
            Admission(runtime.runScript(request, callback), runtime.asBinder())
        }.also { leased ->
            runCatching { validateAcceptance(leased.value.acceptance, executionId) }
                .onFailure { leased.bindingLease.close(); throw it }
        }
    }

    suspend fun cancel(
        context: Context,
        selection: Selection,
        executionId: String,
    ): Boolean {
        val appContext = context.applicationContext
        if (!matchesPinnedIdentity(appContext, selection)) return false
        return host.call(
            context = appContext,
            target = selection.discovered,
            enforceHostVersionCompatibility = false,
            enforceAuthorization = false,
            enforcePreBindValidator = false,
            retryOnRemoteException = false,
        ) { runtime -> runtime.cancelScript(executionId) }
    }

    private fun validateServiceForNewCall(context: Context, serviceInfo: ServiceInfo) {
        check(serviceInfo.exported) { "AutoJs4 runtime service is not exported" }
        check(serviceInfo.enabled && serviceInfo.applicationInfo.enabled) {
            "AutoJs4 runtime service is disabled"
        }
        check(serviceInfo.permission == AutoJs4RuntimeContract.PLUGIN_PERMISSION) {
            "AutoJs4 runtime service must require ${AutoJs4RuntimeContract.PLUGIN_PERMISSION}"
        }
        check(PluginTrustManager.isAuthorized(context, serviceInfo.packageName)) {
            "AutoJs4 Runtime plugin is not authorized: ${serviceInfo.packageName}"
        }
        check(PluginEnableStore.isEnabled(context, serviceInfo.packageName)) {
            "AutoJs4 Runtime plugin is disabled: ${serviceInfo.packageName}"
        }
    }

    private fun validatePluginInfo(discovered: AidlPluginHost.Discovered) {
        val info = requireNotNull(discovered.pluginInfo) { "AutoJs4 Runtime PluginInfo is missing" }
        check(info.id == AutoJs4PluginIds.ID) { "Unexpected AutoJs4 Runtime plugin id: ${info.id}" }
        check(info.engine == AutoJs4PluginIds.ENGINE) { "Unexpected AutoJs4 Runtime engine: ${info.engine}" }
        check(info.variant == AutoJs4PluginIds.VARIANT_DEFAULT) { "Unexpected AutoJs4 Runtime variant: ${info.variant}" }
        val supportedAbis = info.supportedAbis.orEmpty().toSet()
        check(supportedAbis.isNotEmpty() && Build.SUPPORTED_ABIS.any(supportedAbis::contains)) {
            "AutoJs4 Runtime does not provide a compatible ABI for ${Build.SUPPORTED_ABIS.joinToString()}"
        }
        val capabilities = requireNotNull(info.capabilities) { "AutoJs4 Runtime capabilities are missing" }
        check(
            capabilities.getInt(AutoJs4PluginCapabilityKeys.CONTRACT_VERSION, -1) ==
                AutoJs4RuntimeContract.PROTOCOL_VERSION,
        ) { "AutoJs4 Runtime protocol ${capabilities.getInt(AutoJs4PluginCapabilityKeys.CONTRACT_VERSION, -1)} is not supported" }
        check(
            capabilities.getString(AutoJs4PluginCapabilityKeys.SOURCE_TRANSPORT) ==
                AutoJs4RuntimeContract.SOURCE_TRANSPORT_FILE_PATH,
        ) { "AutoJs4 Runtime source transport is not file-path" }
        check(capabilities.getBoolean(AutoJs4PluginCapabilityKeys.SUPPORTS_CANCELLATION)) {
            "AutoJs4 Runtime does not support cancellation"
        }
        check(capabilities.getBoolean(AutoJs4PluginCapabilityKeys.SUPPORTS_STREAMING_LOG)) {
            "AutoJs4 Runtime does not stream console output"
        }
    }

    private fun validateRuntimeInfo(selection: Selection, runtimeInfo: Bundle) {
        check(
            runtimeInfo.getInt(AutoJs4RuntimeContract.KEY_PROTOCOL_VERSION, -1) ==
                AutoJs4RuntimeContract.PROTOCOL_VERSION,
        ) { "Unsupported AutoJs4 runtime protocol" }
        check(runtimeInfo.getBoolean(AutoJs4RuntimeContract.KEY_RUNTIME_READY)) {
            "AutoJs4 Runtime is not ready"
        }
        check(
            runtimeInfo.getString(AutoJs4RuntimeContract.KEY_PROCESS_NAME) ==
                selection.discovered.serviceInfo.processName,
        ) { "AutoJs4 Runtime is not running in its declared process" }
    }

    private fun validateAcceptance(acceptance: Bundle, executionId: String) {
        check(
            acceptance.getInt(AutoJs4RuntimeContract.KEY_PROTOCOL_VERSION, -1) ==
                AutoJs4RuntimeContract.PROTOCOL_VERSION,
        ) { "AutoJs4 Runtime returned an unsupported acceptance protocol" }
        check(acceptance.getString(AutoJs4RuntimeContract.KEY_EXECUTION_ID) == executionId) {
            "AutoJs4 Runtime returned a mismatched execution ID"
        }
        val accepted = acceptance.getBoolean(AutoJs4RuntimeContract.KEY_ACCEPTED)
        val errorCode = acceptance.getString(AutoJs4RuntimeContract.KEY_ERROR_CODE)
        check(accepted == errorCode.isNullOrEmpty()) {
            "AutoJs4 Runtime returned an inconsistent acceptance state"
        }
        check(
            acceptance.getString(AutoJs4RuntimeContract.KEY_ERROR_MESSAGE).orEmpty().toByteArray().size <=
                AutoJs4RuntimeContract.MAX_TERMINAL_MESSAGE_BYTES,
        ) { "AutoJs4 Runtime acceptance message exceeds its limit" }
    }

    private fun ensureEligibleForNewCall(context: Context, selection: Selection) {
        check(matchesPinnedIdentity(context, selection)) { "AutoJs4 Runtime package changed after selection" }
        validateServiceForNewCall(context, currentServiceInfo(context, selection.identity.component))
    }

    private fun matchesPinnedIdentity(context: Context, selection: Selection): Boolean = runCatching {
        val current = captureIdentity(context, currentServiceInfo(context, selection.identity.component))
        current == selection.identity
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun currentServiceInfo(context: Context, component: ComponentName): ServiceInfo =
        context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)

    @Suppress("DEPRECATION")
    private fun captureIdentity(context: Context, serviceInfo: ServiceInfo): ProviderIdentity {
        val packageInfo = context.packageManager.getPackageInfo(serviceInfo.packageName, 0)
        return ProviderIdentity(
            component = ComponentName(serviceInfo.packageName, serviceInfo.name),
            uid = serviceInfo.applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerFingerprints = PluginTrustManager.resolveTrustInfo(context, serviceInfo.packageName)
                .fingerprintsSha256.toSet(),
        )
    }
}
