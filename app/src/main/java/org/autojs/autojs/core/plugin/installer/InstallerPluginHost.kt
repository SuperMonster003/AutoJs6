package org.autojs.autojs.core.plugin.installer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.installer.api.IInstallerCallback
import org.autojs.plugin.installer.api.IInstallerPlugin
import org.autojs.plugin.installer.api.InstallerActions
import org.autojs.plugin.installer.api.InstallerCapabilityKeys
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.autojs.plugin.installer.api.InstallerIds
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** How long a refused `openSession` waits for the plugin's `onFailed` on the oneway callback. */
private const val REFUSAL_TIMEOUT_MS = 3_000L

/** Ceiling of every one-shot asynchronous call that needs no user interaction. */
private const val ONE_SHOT_TIMEOUT_MS = 60_000L

/**
 * Host wrapper for the installer Binder contract (plugin roadmap P1.3, decisions D13 and D21):
 * the plugin is enabled by default, any package answering the `org.autojs.plugin.INSTALLER` query
 * with the `installer` category and engine may be selected, and the plugin must declare a
 * contract version the host understands plus a `requiresHostVersion` not below the build that
 * ships this module.
 */
object InstallerPluginHost {

    const val ACTION_INSTALLER = InstallerActions.SERVICE_ACTION

    private const val LABEL = "Installer plugin"

    private val host = AidlPluginHost(
        action = ACTION_INSTALLER,
        category = InstallerActions.SERVICE_CATEGORY,
        tag = "InstallerPluginHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> IInstallerPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(InstallerIds.ENGINE),
        allowActivityStarts = true,
        preBindValidator = ::validateService,
    )

    private val hostVersionCode: Long get() = BuildConfig.VERSION_CODE.toLong()

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        host.discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    /** True when an authorized, enabled and compatible installer plugin can be selected right now. */
    suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { host.select(context, engine = InstallerIds.ENGINE, variant = InstallerIds.VARIANT_DEFAULT) != null }.getOrDefault(false)
    }

    suspend fun getCapabilities(context: Context): Bundle = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        host.call(context, target) { proxy -> proxy.getCapabilities() ?: Bundle() }
    }

    /** Metadata of the same enabled/trusted/compatible selection used by installer calls. */
    suspend fun getSelectedInfo(context: Context): PluginInfo = withContext(Dispatchers.IO) {
        requireNotNull(selectOrThrow(context).pluginInfo)
    }

    /** Authorizers the selected plugin implements (never `auto`). */
    suspend fun authorizers(context: Context): List<String> =
        InstallerBundles.peer(getCapabilities(context)).authorizers.toList()

    suspend fun getAuthorizerState(context: Context, authorizer: String): InstallerJson.AuthorizerState = withContext(Dispatchers.IO) {
        require(authorizer in InstallerContract.AUTHORIZERS) { "unknown authorizer: $authorizer" }
        val target = selectOrThrow(context)
        host.call(context, target) { proxy ->
            peer(proxy).requireAuthorizer(authorizer)
            InstallerJson.decodeAuthorizerState(InstallerBundles.string(proxy.getAuthorizerState(authorizer), InstallerContract.KEY_STATUS_JSON))
        }
    }

    /** Asks the user for [authorizer]; `true` when granted, otherwise an [InstallerPluginException]. */
    suspend fun requestAuthorizer(context: Context, authorizer: String, timeoutMillis: Long = InstallerContract.DEFAULT_USER_ACTION_TIMEOUT_MILLIS): Boolean = withContext(Dispatchers.IO) {
        require(authorizer in InstallerContract.AUTHORIZERS) { "unknown authorizer: $authorizer" }
        val target = selectOrThrow(context)
        val json = oneShot(context, target, timeoutMillis) { proxy, peer, callback ->
            peer.requireAuthorizer(authorizer)
            proxy.requestAuthorizer(authorizer, callback)
        }
        InstallerJson.decodeGranted(json)
    }

    /** Reads one package without installing it; the normalized PackageArchiveInfo document is returned as JSON. */
    suspend fun inspect(context: Context, source: InstallerSource): String = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        val requestJson = InstallerJson.encodeInspectRequest(source.displayName, source.size)
        val json = oneShot(context, target, ONE_SHOT_TIMEOUT_MS) { proxy, peer, callback ->
            proxy.inspect(source.descriptor, InstallerBundles.request(requestJson, hostVersionCode, contractVersion = peer.contractVersion), callback)
        }
        InstallerJson.decodePackageArchiveInfo(json)
    }

    /**
     * Opens an installation session on a dedicated binding lease. The handle owns the lease, the
     * death recipient and [sources]; the caller awaits and closes it.
     */
    suspend fun openSession(
        context: Context,
        sources: List<InstallerSource>,
        interaction: String = InstallerContract.INTERACTION_AUTO,
        options: InstallerJson.InstallOptions = InstallerJson.InstallOptions(),
        listener: InstallerSessionListener = object : InstallerSessionListener {},
        hostSessionId: String = UUID.randomUUID().toString(),
        isBatch: Boolean = sources.map { it.item }.distinct().size > 1,
        origin: String = InstallerContract.SOURCE_HOST,
    ): InstallerSessionHandle {
        // withContext can discard a successfully constructed return value when cancellation wins
        // the dispatcher handoff. Retain it outside that boundary so its lease/PFDs still close.
        val handoff = AtomicReference<InstallerSessionHandle?>()
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val requestJson = InstallerJson.encodeInstallRequest(hostSessionId, sources.map { it.toEntry() }, interaction, options, isBatch, origin)
                val descriptors = sources.map { it.descriptor }.toTypedArray<ParcelFileDescriptor>()
                val target = selectOrThrow(context)
                val callback = InstallerSessionCallbackBinder(hostSessionId, listener)
                val leased = host.callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy ->
                    val peer = peer(proxy)
                    peer.requireInstall(options.authorizer, interaction, options)
                    val request = InstallerBundles.request(requestJson, hostVersionCode, hostSessionId, peer.contractVersion)
                    proxy.openSession(descriptors, request, callback) ?: throw refusal(callback)
                }
                try {
                    callback.pluginSessionId = runCatching { leased.value.getId() }.getOrNull()
                    InstallerSessionHandle(hostSessionId, leased.value, leased.bindingLease, callback, sources, options.timeoutMillis).also {
                        handoff.set(it)
                        currentCoroutineContext().ensureActive()
                    }
                } catch (failure: Throwable) {
                    if (handoff.get() == null) {
                        runCatching { leased.value.close() }
                        runCatching { leased.bindingLease.close() }
                    }
                    throw failure
                }
            }
        } catch (failure: Throwable) {
            runCatching { handoff.getAndSet(null)?.close() }
            sources.forEach { it.close() }
            throw failure
        }
    }

    suspend fun uninstall(context: Context, packageName: String, options: InstallerJson.UninstallOptions = InstallerJson.UninstallOptions()): InstallerJson.UninstallResult = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        val requestJson = InstallerJson.encodeUninstallRequest(packageName, options)
        val json = oneShot(context, target, options.timeoutMillis) { proxy, peer, callback ->
            peer.requireAuthorizer(options.authorizer)
            proxy.uninstall(InstallerBundles.request(requestJson, hostVersionCode, contractVersion = peer.contractVersion), callback)
        }
        InstallerJson.decodeUninstallResult(json)
    }

    suspend fun getDefaultInstallerState(context: Context): InstallerJson.DefaultInstallerState = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        host.call(context, target) { proxy ->
            InstallerJson.decodeDefaultInstallerState(InstallerBundles.string(proxy.getDefaultInstallerState(), InstallerContract.KEY_STATUS_JSON))
        }
    }

    suspend fun setDefaultInstaller(context: Context, enable: Boolean, authorizer: String = InstallerContract.AUTHORIZER_AUTO,
        mode: String = InstallerContract.DEFAULT_MODE_PREFERRED): InstallerJson.DefaultInstallerState = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        val requestJson = InstallerJson.encodeDefaultInstallerRequest(authorizer, mode)
        val json = oneShot(context, target, InstallerContract.DEFAULT_USER_ACTION_TIMEOUT_MILLIS) { proxy, peer, callback ->
            peer.requireDefault(authorizer, mode)
            val request = InstallerBundles.request(requestJson, hostVersionCode, contractVersion = peer.contractVersion)
            if (mode == InstallerContract.DEFAULT_MODE_PREFERRED) proxy.setDefaultInstaller(enable, request, callback)
            else proxy.setDefaultInstallerV2(enable, request, callback)
        }
        InstallerJson.decodeDefaultInstallerState(json)
    }

    suspend fun getUsers(context: Context, authorizer: String = InstallerContract.AUTHORIZER_AUTO): List<InstallerJson.DeviceUser> = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        val requestJson = InstallerJson.encodeAuthorizerRequest(authorizer)
        val json = oneShot(context, target, ONE_SHOT_TIMEOUT_MS) { proxy, peer, callback ->
            peer.requireAuthorizer(authorizer)
            proxy.getUsers(InstallerBundles.request(requestJson, hostVersionCode, contractVersion = peer.contractVersion), callback)
        }
        InstallerJson.decodeUsers(json)
    }

    /** Runs one asynchronous contract method and returns its result JSON, or throws the mapped error. */
    private suspend fun oneShot(
        context: Context,
        target: AidlPluginHost.Discovered,
        timeoutMillis: Long,
        invoke: (IInstallerPlugin, InstallerPeer, IInstallerCallback) -> Unit,
    ): String? {
        val leased = host.callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy -> proxy }
        try {
            return withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : IInstallerCallback.Stub() {
                        override fun onResult(result: Bundle?) {
                            if (continuation.isActive) continuation.resume(Result.success(InstallerBundles.string(result, InstallerContract.KEY_RESULT_JSON)))
                        }

                        override fun onError(error: Bundle?) {
                            val mapped = InstallerJson.decodeError(InstallerBundles.string(error, InstallerContract.KEY_ERROR_JSON))
                            if (continuation.isActive) continuation.resume(Result.failure(InstallerPluginException(mapped)))
                        }
                    }
                    runCatching { invoke(leased.value, peer(leased.value), callback) }.onFailure { failure ->
                        if (continuation.isActive) continuation.resume(Result.failure(InstallerPluginException(InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.CALL), failure)))
                    }
                }.getOrThrow()
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            throw InstallerPluginException(InstallerErrorMapper.error(InstallerErrorCodes.TIMEOUT, "installer plugin did not answer within $timeoutMillis ms", retryable = true), timeout)
        } finally {
            runCatching { leased.bindingLease.close() }
        }
    }

    /** Never infer support by attempting an unknown Binder transaction. */
    private fun peer(proxy: IInstallerPlugin): InstallerPeer =
        InstallerBundles.peer(requireNotNull(proxy.capabilities) { "Installer plugin capabilities are missing" })

    /**
     * The error of a refused `openSession`: the plugin reports it through the oneway session
     * callback, which may land after the null return, so the opener waits briefly for it.
     */
    private fun refusal(callback: InstallerSessionCallbackBinder): Exception {
        val outcome = runCatching {
            kotlinx.coroutines.runBlocking { withTimeout(REFUSAL_TIMEOUT_MS) { callback.outcome.await() } }
        }.getOrNull()
        val error = (outcome as? InstallerSessionOutcome.Failed)?.error
            ?: InstallerErrorMapper.error(InstallerErrorCodes.INVALID_ARGUMENT, "installer plugin refused the request", retryable = false)
        return InstallerPluginException(error)
    }

    private suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered {
        return host.selectOrThrow(
            context = context,
            engineId = null,
            engine = InstallerIds.ENGINE,
            variant = InstallerIds.VARIANT_DEFAULT,
            label = LABEL,
        )
    }

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.engine == InstallerIds.ENGINE) { "Unexpected installer plugin engine: ${it.engine}" }
        require(it.variant == InstallerIds.VARIANT_DEFAULT) { "Unexpected installer plugin variant: ${it.variant}" }
        val capabilities = requireNotNull(it.capabilities) { "Installer plugin capabilities are missing" }
        InstallerBundles.peer(capabilities)
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= InstallerIds.REQUIRED_HOST_VERSION_CODE) {
            "Installer plugin required host version is missing or invalid"
        }
    }

    private fun validateService(context: Context, serviceInfo: ServiceInfo) {
        require(serviceInfo.exported && serviceInfo.enabled) {
            "Installer plugin service is not exported and enabled"
        }
        require(serviceInfo.permission == InstallerActions.PLUGIN_PERMISSION) {
            "Installer plugin service must require ${InstallerActions.PLUGIN_PERMISSION}"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context, serviceInfo.packageName)) {
            "Installer plugin service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context, packageName: String): Set<ComponentName> {
        val intent = Intent(InstallerActions.SERVICE_ACTION).apply {
            addCategory(InstallerActions.SERVICE_CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(packageName)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { item ->
            item.serviceInfo?.let { service -> ComponentName(service.packageName, service.name) }
        }.toSet()
    }
}
