package org.autojs.autojs.core.plugin.bun

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
import org.autojs.plugin.bun.runtime.api.BunPluginActions
import org.autojs.plugin.bun.runtime.api.BunPluginCapabilityKeys
import org.autojs.plugin.bun.runtime.api.BunPluginIds
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.autojs.plugin.bun.runtime.api.IBunRuntimeCallback
import org.autojs.plugin.bun.runtime.api.IBunRuntimePlugin

internal object BunRuntimePluginClient {
    private val host = AidlPluginHost(
        action = if (org.autojs.autojs6.BuildConfig.isInrt) BunPluginActions.PACKAGED_RUNTIME else BunPluginActions.RUNTIME,
        category = BunPluginActions.CATEGORY,
        tag = "BunRuntimePluginClient",
        moduleLabel = "Bun Runtime",
        proxyFactory = IBunRuntimePlugin.Stub::asInterface,
        infoReader = { runtime ->
            val descriptor = runtime.asBinder().interfaceDescriptor
            check(descriptor == BunRuntimeContract.BINDER_DESCRIPTOR) {
                "Unexpected Bun runtime Binder descriptor: $descriptor"
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
            engineId = BunPluginIds.ID,
            engine = BunPluginIds.ENGINE,
            variant = BunPluginIds.VARIANT_BUN_1_4_0_ANDROID,
            label = "Bun Runtime",
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
        callback: IBunRuntimeCallback,
    ): Bundle {
        val appContext = context.applicationContext
        ensureEligibleForNewCall(appContext, selection)
        val executionId = request.getString(BunRuntimeContract.KEY_EXECUTION_ID).orEmpty()
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
        check(serviceInfo.exported) { "Bun runtime service is not exported" }
        check(serviceInfo.enabled && serviceInfo.applicationInfo.enabled) { "Bun runtime service is disabled" }
        val permission = if (org.autojs.autojs6.BuildConfig.isInrt) BunPluginActions.PACKAGED_RUNTIME_PERMISSION else BunRuntimeContract.PLUGIN_PERMISSION
        check(serviceInfo.permission == permission) {
            "Bun runtime service must require $permission"
        }
        check(PluginTrustManager.isAuthorized(context, serviceInfo.packageName)) {
            "Bun Runtime plugin is not authorized: ${serviceInfo.packageName}"
        }
        check(PluginEnableStore.isEnabled(context, serviceInfo.packageName)) {
            "Bun Runtime plugin is disabled: ${serviceInfo.packageName}"
        }
    }

    private fun validatePluginInfo(discovered: AidlPluginHost.Discovered) {
        val info = requireNotNull(discovered.pluginInfo) { "Bun Runtime PluginInfo is missing" }
        check(info.id == BunPluginIds.ID)
        check(info.engine == BunPluginIds.ENGINE)
        check(info.variant == BunPluginIds.VARIANT_BUN_1_4_0_ANDROID)
        val supportedAbis = info.supportedAbis.orEmpty().toSet()
        check(supportedAbis.isNotEmpty() && Build.SUPPORTED_ABIS.any(supportedAbis::contains)) {
            "Bun Runtime does not provide a compatible ABI for ${Build.SUPPORTED_ABIS.joinToString()}"
        }
        val capabilities = requireNotNull(info.capabilities) { "Bun Runtime capabilities are missing" }
        check(capabilities.getInt(BunPluginCapabilityKeys.CONTRACT_VERSION, -1) == BunRuntimeContract.PROTOCOL_VERSION)
        check(capabilities.getString(BunPluginCapabilityKeys.RUNTIME_VERSION) == BunRuntimeContract.RUNTIME_VERSION)
        check(capabilities.getString(BunPluginCapabilityKeys.RUNTIME_REVISION) == BunRuntimeContract.RUNTIME_REVISION)
        check(capabilities.getString(BunPluginCapabilityKeys.SOURCE_TRANSPORT) == "parcel-file-descriptor")
        check(capabilities.getString(BunPluginCapabilityKeys.EXECUTION_MODEL) == "isolated-process-single-source")
        check(capabilities.getBoolean(BunPluginCapabilityKeys.SUPPORTS_TYPESCRIPT))
        check(capabilities.getBoolean(BunPluginCapabilityKeys.SUPPORTS_CANCELLATION))
        check(capabilities.getBoolean(BunPluginCapabilityKeys.SUPPORTS_STREAMING_OUTPUT))
    }

    private fun validateRuntimeInfo(selection: Selection, runtimeInfo: Bundle) {
        check(runtimeInfo.getInt(BunRuntimeContract.KEY_PROTOCOL_VERSION, -1) == BunRuntimeContract.PROTOCOL_VERSION) {
            "Unsupported Bun runtime protocol"
        }
        check(runtimeInfo.getBoolean(BunRuntimeContract.KEY_RUNTIME_READY)) {
            runtimeInfo.getString(BunRuntimeContract.KEY_ERROR_MESSAGE) ?: "Bun Runtime is not ready"
        }
        check(runtimeInfo.getString(BunRuntimeContract.KEY_RUNTIME_VERSION) == BunRuntimeContract.RUNTIME_VERSION)
        check(runtimeInfo.getString(BunRuntimeContract.KEY_RUNTIME_REVISION) == BunRuntimeContract.RUNTIME_REVISION)
        val runtimeAbis = runtimeInfo.getStringArray(BunRuntimeContract.KEY_SUPPORTED_ABIS).orEmpty().toSet()
        val infoAbis = selection.discovered.pluginInfo?.supportedAbis.orEmpty().toSet()
        check(runtimeAbis.isNotEmpty() && runtimeAbis == infoAbis) { "Bun Runtime ABI inventory changed after selection" }
        val processAbi = runtimeInfo.getString(BunRuntimeContract.KEY_PROCESS_ABI)
        check(processAbi != null && processAbi in runtimeAbis && processAbi in Build.SUPPORTED_ABIS) {
            "Bun Runtime process ABI is incompatible: $processAbi"
        }
        check(runtimeInfo.getString(BunRuntimeContract.KEY_PROCESS_NAME) == selection.discovered.serviceInfo.processName) {
            "Bun Runtime is not running in its declared isolated process"
        }
        check(runtimeInfo.getLong(BunRuntimeContract.KEY_MAX_SOURCE_BYTES) >= BunRuntimeContract.MAX_SOURCE_BYTES)
        check(runtimeInfo.getLong(BunRuntimeContract.KEY_MAX_OUTPUT_BYTES) >= BunRuntimeContract.MAX_OUTPUT_BYTES)
    }

    private fun validateResult(result: Bundle, executionId: String) {
        check(result.getInt(BunRuntimeContract.KEY_PROTOCOL_VERSION, -1) == BunRuntimeContract.PROTOCOL_VERSION) {
            "Bun Runtime returned an unsupported terminal protocol"
        }
        check(result.getString(BunRuntimeContract.KEY_EXECUTION_ID) == executionId) {
            "Bun Runtime returned a mismatched execution ID"
        }
        check(result.getString(BunRuntimeContract.KEY_STDOUT).isNullOrEmpty())
        check(result.getString(BunRuntimeContract.KEY_STDERR).isNullOrEmpty())
        val succeeded = result.getBoolean(BunRuntimeContract.KEY_SUCCEEDED)
        val errorCode = result.getString(BunRuntimeContract.KEY_ERROR_CODE)
        check(succeeded == errorCode.isNullOrEmpty()) { "Bun Runtime returned an inconsistent terminal state" }
        check(result.getLong(BunRuntimeContract.KEY_DURATION_MILLIS, -1L) >= 0L)
        check(!result.getBoolean(BunRuntimeContract.KEY_CANCELLED) || errorCode == BunRuntimeContract.ERROR_CANCELLED)
        check(!result.getBoolean(BunRuntimeContract.KEY_TIMED_OUT) || errorCode == BunRuntimeContract.ERROR_TIMEOUT)
        check(
            result.getString(BunRuntimeContract.KEY_ERROR_MESSAGE).orEmpty().toByteArray().size <=
                BunRuntimeContract.MAX_TERMINAL_MESSAGE_BYTES,
        )
    }

    private fun ensureEligibleForNewCall(context: Context, selection: Selection) {
        check(matchesPinnedIdentity(context, selection)) { "Bun Runtime package changed after selection" }
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
