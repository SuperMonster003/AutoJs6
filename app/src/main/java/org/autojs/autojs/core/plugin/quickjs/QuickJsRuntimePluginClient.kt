package org.autojs.autojs.core.plugin.quickjs

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.quickjs.runtime.api.IQuickJsRuntimeCallback
import org.autojs.plugin.quickjs.runtime.api.IQuickJsRuntimePlugin
import org.autojs.plugin.quickjs.runtime.api.QuickJsPluginActions
import org.autojs.plugin.quickjs.runtime.api.QuickJsPluginCapabilityKeys
import org.autojs.plugin.quickjs.runtime.api.QuickJsPluginIds
import org.autojs.plugin.quickjs.runtime.api.QuickJsRuntimeContract

internal object QuickJsRuntimePluginClient {
    private val host = AidlPluginHost(
        action = QuickJsPluginActions.RUNTIME,
        category = QuickJsPluginActions.CATEGORY,
        tag = "QuickJsRuntimePluginClient",
        moduleLabel = "QuickJS Runtime",
        proxyFactory = IQuickJsRuntimePlugin.Stub::asInterface,
        infoReader = { runtime ->
            val descriptor = runtime.asBinder().interfaceDescriptor
            check(descriptor == QuickJsRuntimeContract.BINDER_DESCRIPTOR) {
                "Unexpected QuickJS runtime Binder descriptor: $descriptor"
            }
            runtime.info
        },
        preBindValidator = ::validateServiceForNewCall,
    )

    internal fun pooledConnectionCount(): Int = host.pooledConnectionCount()

    internal class Selection internal constructor(
        internal val discovered: AidlPluginHost.Discovered,
        internal val identity: ProviderIdentity,
    ) {
        val providerUid: Int
            get() = identity.uid
    }

    internal data class ProviderIdentity(
        val component: ComponentName,
        val uid: Int,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val signerFingerprints: Set<String>,
    )

    suspend fun select(context: Context): Selection {
        val appContext = context.applicationContext
        val discovered = host.selectOrThrow(
            context = appContext,
            engineId = QuickJsPluginIds.ID,
            engine = QuickJsPluginIds.ENGINE,
            variant = QuickJsPluginIds.VARIANT_QUICKJS_2026_06_04,
            label = "QuickJS Runtime",
        )
        validatePluginInfo(discovered)
        return Selection(discovered, captureIdentity(appContext, discovered.serviceInfo))
    }

    suspend fun prewarm(context: Context, selection: Selection): Bundle {
        val appContext = context.applicationContext
        ensureEligibleForNewCall(appContext, selection)
        return host.call(
            context = appContext,
            target = selection.discovered,
            retryOnRemoteException = true,
        ) { runtime -> runtime.prewarmRuntime() }.also { runtimeInfo ->
            validateRuntimeInfo(selection, runtimeInfo)
        }
    }

    suspend fun runScript(
        context: Context,
        selection: Selection,
        request: Bundle,
        source: ParcelFileDescriptor,
        callback: IQuickJsRuntimeCallback,
    ): Bundle {
        val appContext = context.applicationContext
        ensureEligibleForNewCall(appContext, selection)
        val executionId = request.getString(QuickJsRuntimeContract.KEY_EXECUTION_ID).orEmpty()
        return host.call(
            context = appContext,
            target = selection.discovered,
            retryOnRemoteException = false,
        ) { runtime -> runtime.runScript(request, source, callback) }.also { result ->
            validateResult(result, executionId)
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
        check(serviceInfo.exported) { "QuickJS runtime service is not exported" }
        check(serviceInfo.enabled && serviceInfo.applicationInfo.enabled) {
            "QuickJS runtime service is disabled"
        }
        check(serviceInfo.permission == QuickJsRuntimeContract.PLUGIN_PERMISSION) {
            "QuickJS runtime service must require ${QuickJsRuntimeContract.PLUGIN_PERMISSION}"
        }
        check(PluginTrustManager.isAuthorized(context, serviceInfo.packageName)) {
            "QuickJS Runtime plugin is not authorized: ${serviceInfo.packageName}"
        }
        check(PluginEnableStore.isEnabled(context, serviceInfo.packageName)) {
            "QuickJS Runtime plugin is disabled: ${serviceInfo.packageName}"
        }
    }

    private fun validatePluginInfo(discovered: AidlPluginHost.Discovered) {
        val info = requireNotNull(discovered.pluginInfo) { "QuickJS Runtime PluginInfo is missing" }
        check(info.id == QuickJsPluginIds.ID)
        check(info.engine == QuickJsPluginIds.ENGINE)
        check(info.variant == QuickJsPluginIds.VARIANT_QUICKJS_2026_06_04)
        val supportedAbis = info.supportedAbis.orEmpty().toSet()
        check(supportedAbis.isNotEmpty() && Build.SUPPORTED_ABIS.any(supportedAbis::contains)) {
            "QuickJS Runtime does not provide a compatible ABI for ${Build.SUPPORTED_ABIS.joinToString()}"
        }
        val capabilities = requireNotNull(info.capabilities) { "QuickJS Runtime capabilities are missing" }
        check(
            capabilities.getInt(QuickJsPluginCapabilityKeys.CONTRACT_VERSION, -1) ==
                QuickJsRuntimeContract.PROTOCOL_VERSION,
        )
        check(
            capabilities.getString(QuickJsPluginCapabilityKeys.RUNTIME_VERSION) ==
                QuickJsRuntimeContract.RUNTIME_VERSION,
        )
        check(
            capabilities.getString(QuickJsPluginCapabilityKeys.RUNTIME_REVISION) ==
                QuickJsRuntimeContract.RUNTIME_REVISION,
        )
        check(capabilities.getString(QuickJsPluginCapabilityKeys.SOURCE_TRANSPORT) == "parcel-file-descriptor")
        check(
            capabilities.getString(QuickJsPluginCapabilityKeys.EXECUTION_MODEL) ==
                "isolated-process-single-source",
        )
        check(capabilities.getBoolean(QuickJsPluginCapabilityKeys.SUPPORTS_MODULES))
        check(capabilities.getBoolean(QuickJsPluginCapabilityKeys.SUPPORTS_CANCELLATION))
        check(capabilities.getBoolean(QuickJsPluginCapabilityKeys.SUPPORTS_STREAMING_OUTPUT))
        check(capabilities.getBoolean(QuickJsPluginCapabilityKeys.SUPPORTS_MEMORY_LIMITS))
    }

    private fun validateRuntimeInfo(selection: Selection, runtimeInfo: Bundle) {
        check(
            runtimeInfo.getInt(QuickJsRuntimeContract.KEY_PROTOCOL_VERSION, -1) ==
                QuickJsRuntimeContract.PROTOCOL_VERSION,
        ) { "Unsupported QuickJS runtime protocol" }
        check(runtimeInfo.getBoolean(QuickJsRuntimeContract.KEY_RUNTIME_READY)) {
            runtimeInfo.getString(QuickJsRuntimeContract.KEY_ERROR_MESSAGE) ?: "QuickJS Runtime is not ready"
        }
        check(
            runtimeInfo.getString(QuickJsRuntimeContract.KEY_RUNTIME_VERSION) ==
                QuickJsRuntimeContract.RUNTIME_VERSION,
        )
        check(
            runtimeInfo.getString(QuickJsRuntimeContract.KEY_RUNTIME_REVISION) ==
                QuickJsRuntimeContract.RUNTIME_REVISION,
        )
        check(runtimeInfo.getString(QuickJsRuntimeContract.KEY_NATIVE_LIBRARY) == "libautojs_quickjs_runtime.so")
        val runtimeAbis = runtimeInfo.getStringArray(QuickJsRuntimeContract.KEY_SUPPORTED_ABIS).orEmpty().toSet()
        val infoAbis = selection.discovered.pluginInfo?.supportedAbis.orEmpty().toSet()
        check(runtimeAbis.isNotEmpty() && runtimeAbis == infoAbis) {
            "QuickJS Runtime ABI inventory changed after selection"
        }
        val processAbi = runtimeInfo.getString(QuickJsRuntimeContract.KEY_PROCESS_ABI)
        check(processAbi != null && processAbi in runtimeAbis && processAbi in Build.SUPPORTED_ABIS) {
            "QuickJS Runtime process ABI is incompatible: $processAbi"
        }
        check(
            runtimeInfo.getString(QuickJsRuntimeContract.KEY_PROCESS_NAME) ==
                selection.discovered.serviceInfo.processName,
        ) { "QuickJS Runtime is not running in its declared isolated process" }
        check(runtimeInfo.getLong(QuickJsRuntimeContract.KEY_MAX_SOURCE_BYTES) >= QuickJsRuntimeContract.MAX_SOURCE_BYTES)
        check(runtimeInfo.getLong(QuickJsRuntimeContract.KEY_MAX_OUTPUT_BYTES) >= QuickJsRuntimeContract.MAX_OUTPUT_BYTES)
        check(
            runtimeInfo.getLong(QuickJsRuntimeContract.KEY_MAX_MEMORY_BYTES) >=
                QuickJsRuntimeContract.MAX_MEMORY_LIMIT_BYTES,
        )
        check(
            runtimeInfo.getLong(QuickJsRuntimeContract.KEY_MAX_STACK_BYTES) >=
                QuickJsRuntimeContract.MAX_STACK_LIMIT_BYTES,
        )
    }

    private fun validateResult(result: Bundle, executionId: String) {
        check(
            result.getInt(QuickJsRuntimeContract.KEY_PROTOCOL_VERSION, -1) ==
                QuickJsRuntimeContract.PROTOCOL_VERSION,
        ) { "QuickJS Runtime returned an unsupported terminal protocol" }
        check(result.getString(QuickJsRuntimeContract.KEY_EXECUTION_ID) == executionId) {
            "QuickJS Runtime returned a mismatched execution ID"
        }
        check(result.getString(QuickJsRuntimeContract.KEY_STDOUT).isNullOrEmpty())
        check(result.getString(QuickJsRuntimeContract.KEY_STDERR).isNullOrEmpty())
        val succeeded = result.getBoolean(QuickJsRuntimeContract.KEY_SUCCEEDED)
        val errorCode = result.getString(QuickJsRuntimeContract.KEY_ERROR_CODE)
        check(succeeded == errorCode.isNullOrEmpty()) {
            "QuickJS Runtime returned an inconsistent terminal state"
        }
        check(result.getLong(QuickJsRuntimeContract.KEY_DURATION_MILLIS, -1L) >= 0L)
        check(
            !result.getBoolean(QuickJsRuntimeContract.KEY_CANCELLED) ||
                errorCode == QuickJsRuntimeContract.ERROR_CANCELLED,
        )
        check(
            !result.getBoolean(QuickJsRuntimeContract.KEY_TIMED_OUT) ||
                errorCode == QuickJsRuntimeContract.ERROR_TIMEOUT,
        )
        check(
            result.getString(QuickJsRuntimeContract.KEY_ERROR_MESSAGE).orEmpty().toByteArray().size <=
                QuickJsRuntimeContract.MAX_TERMINAL_MESSAGE_BYTES,
        )
    }

    private fun ensureEligibleForNewCall(context: Context, selection: Selection) {
        check(matchesPinnedIdentity(context, selection)) { "QuickJS Runtime package changed after selection" }
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
