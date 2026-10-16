package org.autojs.autojs.core.plugin

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.RemoteException
import android.os.SystemClock.uptimeMillis
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.center.PluginVariantPriorityStore
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginInfo
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AidlPluginHost<I : IInterface>(
    val action: String,
    private val category: String? = null,
    private val tag: String,
    private val moduleLabel: String,
    private val proxyFactory: (IBinder) -> I,
    private val infoReader: (I) -> PluginInfo,
    private val defaultEnabled: Boolean = true,
    // Decides the default per installed package (for example by its signer) instead of [defaultEnabled].
    // zh-CN: 按已安装的包 (例如按其签名) 决定默认值, 代替 [defaultEnabled].
    private val defaultEnabledFor: ((Context, String) -> Boolean)? = null,
    private val allowActivityStarts: Boolean = false,
    private val preBindValidator: ((Context, ServiceInfo) -> Unit)? = null,
    /**
     * Reuse the advertised [PluginInfo] of an unchanged installed package instead of binding the
     * service for `getInfo` on every discovery. Only for hosts whose selection relies on static
     * capabilities; [probe] always reads fresh.
     */
    private val cacheDiscovery: Boolean = false,
    /**
     * Bind with `BIND_INCLUDE_CAPABILITIES` (API 29+), so the plugin process inherits the host's while-in-use
     * capabilities such as the microphone (3-Macaw Speech roadmap D22). Off by default; only plugins that record or
     * capture for the host opt in.
     * zh-CN: 以 `BIND_INCLUDE_CAPABILITIES` (API 29+) 绑定, 使插件进程继承宿主的 while-in-use 能力 (如麦克风, 3-Macaw Speech 路线图 D22).
     * 默认关闭; 只有替宿主录音或采集的插件才开启.
     */
    private val includeCapabilities: Boolean = false,
    /**
     * Bind with `BIND_IMPORTANT`, raising the plugin process to the host's foreground importance while bound.
     * zh-CN: 以 `BIND_IMPORTANT` 绑定, 绑定期间把插件进程提升到宿主的前台重要性.
     */
    private val important: Boolean = false,
) {

    private fun isDefaultEnabled(context: Context, packageName: String): Boolean =
        defaultEnabledFor?.invoke(context, packageName) ?: defaultEnabled

    private val defaultBindTimeoutMs = 60_000L
    private val idleUnbindMs = 30_000L
    private val retryWakeDelayMs = 300L
    private val serviceQueryRetryDelaysMs = longArrayOf(150L, 300L, 600L, 1000L, 1500L)

    private val externalServiceFlag = runCatching { ServiceInfo::class.java.getField("FLAG_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0
    private val bindExternalServiceFlag = runCatching { Context::class.java.getField("BIND_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0
    private val poolHandler = Handler(Looper.getMainLooper())
    private val poolLock = Any()
    private val connectionPool = LinkedHashMap<ComponentName, PooledConnection>()

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val pluginInfo: PluginInfo?,
        val error: Throwable?,
    )

    data class LeasedCall<T>(
        val value: T,
        val bindingLease: ServiceBindingLease,
    )

    suspend fun discover(context: Context, packageName: String? = null): List<Discovered> {
        val services = queryServices(context, packageName)
        Log.i(tag, "discover: services=${services.size}")
        return services.map { service ->
            val identity = if (cacheDiscovery) {
                PluginDiscoveryCache.installedPackageIdentity(context, service.packageName)
            } else {
                null
            }
            if (cacheDiscovery) {
                PluginDiscoveryCache.shared.lookup(identity, action, category, service.packageName, service.name)?.let { cached ->
                    Log.i(tag, "discover: ${service.packageName}/${service.name} (cached info)")
                    return@map Discovered(service, cached, null)
                }
            }
            Log.i(tag, "discover: ${service.packageName}/${service.name}")
            val result = runCatching { withService(context, service, defaultBindTimeoutMs) { infoReader(it) } }
            result.exceptionOrNull()?.let { Log.w(tag, "getInfo failed: ${it.message}") }
            if (cacheDiscovery) {
                result.getOrNull()?.let { info ->
                    PluginDiscoveryCache.shared.store(identity, action, category, service.packageName, service.name, info)
                }
            }
            Discovered(service, result.getOrNull(), result.exceptionOrNull())
        }
    }

    /**
     * [discover], then wakes the packages whose service still failed and that [shouldWake] accepts (see
     * [PluginDiscoveryRecovery]) and discovers those packages once more; services that still fail keep their error.
     * Refused bindings are already woken inside [discover] (see [PluginAutoWake]), so plain [discover] is enough for
     * new callers.
     * zh-CN: 先 [discover], 再唤醒仍然失败且 [shouldWake] 接受的包 (见 [PluginDiscoveryRecovery]), 并对这些包重新发现一次; 仍然失败的
     * 服务保留其错误. 被拒的绑定已在 [discover] 内部唤醒 (见 [PluginAutoWake]), 新代码直接使用 [discover] 即可.
     */
    suspend fun discoverWakingFailures(
        context: Context,
        recovery: PluginDiscoveryRecovery,
        shouldWake: (String) -> Boolean,
    ): List<Discovered> {
        val first = discover(context)
        val wake = recovery.packagesToWake(first, { it.serviceInfo.packageName }, { it.error != null }, shouldWake)
        if (wake.isEmpty()) return first
        first.filter { it.error != null && it.serviceInfo.packageName in wake }.forEach { invalidateService(it.serviceInfo, it.error!!) }
        val woken = wake.filter { PluginWakeManager.tryAutoWakeIfNeeded(context, it) }
        if (woken.isEmpty()) return first
        Log.i(tag, "discover: woke ${woken.joinToString()} after failed bindings")
        delay(retryWakeDelayMs)
        val again = woken.flatMap { discover(context, it) }
        return PluginDiscoveryRecovery.merge(first, again, { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }, { it.error != null })
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo {
        if (!PluginTrustManager.isAuthorized(context, packageName)) {
            error("Plugin not authorized: $packageName")
        }
        val serviceInfo = queryFirstServiceWithRetry(context, packageName)
            ?: error("No plugin service found for action $action and package: $packageName")
        return withService(context, serviceInfo, defaultBindTimeoutMs) { infoReader(it) }
    }

    fun queryServiceCount(context: Context): Int = queryServices(context).size

    suspend fun select(
        context: Context,
        engineId: String? = null,
        engine: String? = null,
        variant: String? = null,
    ): Discovered? {
        return selectDetailed(context, engineId, engine, variant).selected
    }

    suspend fun selectOrThrow(
        context: Context,
        engineId: String? = null,
        engine: String? = null,
        variant: String? = null,
        label: String = moduleLabel,
    ): Discovered {
        val result = selectDetailed(context, engineId, engine, variant)
        return result.selected ?: throw buildSelectionFailure(context, result.discovered, engineId, engine, variant, label)
    }

    suspend fun <T> call(
        context: Context,
        target: Discovered,
        enforceHostVersionCompatibility: Boolean = true,
        enforceAuthorization: Boolean = true,
        enforcePreBindValidator: Boolean = true,
        retryOnRemoteException: Boolean = true,
        block: (I) -> T,
    ): T {
        if (enforceAuthorization && !PluginTrustManager.isAuthorized(context, target.serviceInfo.packageName)) {
            error("Plugin not authorized: ${target.serviceInfo.packageName}")
        }
        if (enforceHostVersionCompatibility) {
            ensureHostVersionCompatible(context, target)
        }
        return withService(
            context = context,
            serviceInfo = target.serviceInfo,
            bindTimeoutMs = defaultBindTimeoutMs,
            enforcePreBindValidator = enforcePreBindValidator,
            retryOnRemoteException = retryOnRemoteException,
            block = block,
        )
    }

    suspend fun <T> callWithDedicatedBindingLease(
        context: Context,
        target: Discovered,
        enforceHostVersionCompatibility: Boolean = true,
        enforceAuthorization: Boolean = true,
        retryOnRemoteException: Boolean = true,
        block: (I) -> T,
    ): LeasedCall<T> {
        if (enforceAuthorization && !PluginTrustManager.isAuthorized(context, target.serviceInfo.packageName)) {
            error("Plugin not authorized: ${target.serviceInfo.packageName}")
        }
        if (enforceHostVersionCompatibility) {
            ensureHostVersionCompatible(context, target)
        }
        return withDedicatedServiceBinding(
            context = context,
            serviceInfo = target.serviceInfo,
            bindTimeoutMs = defaultBindTimeoutMs,
            retryOnRemoteException = retryOnRemoteException,
            block = block,
        )
    }

    private suspend fun queryFirstServiceWithRetry(context: Context, packageName: String): ServiceInfo? {
        queryServices(context, packageName).firstOrNull()?.let { return it }
        serviceQueryRetryDelaysMs.forEach { delayMs ->
            delay(delayMs)
            queryServices(context, packageName).firstOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun selectDetailed(
        context: Context,
        engineId: String?,
        engine: String?,
        variant: String?,
    ): SelectionResult {
        var discovered = discover(context)
        selectFrom(discovered, context, engineId, engine, variant)?.let { return SelectionResult(it, discovered) }
        if (discovered.any { it.error != null }) {
            recoverFailedDiscoveries(context, discovered)
            discovered = discover(context)
            selectFrom(discovered, context, engineId, engine, variant)?.let { return SelectionResult(it, discovered) }
        }
        return SelectionResult(null, discovered)
    }

    private fun selectFrom(
        discovered: List<Discovered>,
        context: Context,
        engineId: String?,
        engine: String?,
        variant: String?,
    ): Discovered? {
        val list = discovered
            .filter { it.pluginInfo != null }
            .filter { PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, isDefaultEnabled(context, it.serviceInfo.packageName)) }
            .filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
            .filter { isHostVersionCompatible(it.pluginInfo) }
        if (list.isEmpty()) return null
        if (hasExplicitSelectionCriteria(engineId, engine, variant)) {
            val candidates = list.filter { item ->
                item.pluginInfo?.let { matchesSelectionCriteria(it, engineId, engine, variant) } == true
            }
            if (candidates.isEmpty()) return null
            return selectByPriorityWithKeys(
                candidates = candidates,
                keys = PluginVariantPriorityStore.buildPriorityKeys(
                    engine = engine ?: candidates.mapNotNull { it.pluginInfo?.engine }.distinct().singleOrNull(),
                    engineId = engineId ?: inferCommonEngineFamily(candidates),
                ),
            ) ?: candidates.first()
        }
        val singleEngine = list.mapNotNull { it.pluginInfo?.engine }.distinct().singleOrNull()
        if (!singleEngine.isNullOrBlank()) {
            selectByPriorityWithKeys(
                candidates = list,
                keys = PluginVariantPriorityStore.buildPriorityKeys(
                    engine = singleEngine,
                    engineId = inferCommonEngineFamily(list),
                ),
            )?.let { return it }
        }
        inferCommonEngineFamily(list)?.let { family ->
            selectByPriorityWithKeys(
                candidates = list,
                keys = PluginVariantPriorityStore.buildPriorityKeys(
                    engine = family,
                    engineId = family,
                ),
            )?.let { return it }
        }
        return list.first()
    }

    private fun selectByPriorityWithKeys(
        candidates: List<Discovered>,
        keys: List<String>,
    ): Discovered? {
        if (candidates.isEmpty()) return null
        val byPackage = candidates.associateBy { it.serviceInfo.packageName }
        val fallbackOrder = PluginVariantPriorityStore.getBestOrderForPackages(byPackage.keys)
        if (fallbackOrder.isNotEmpty()) {
            fallbackOrder.firstNotNullOfOrNull { byPackage[it] }?.let { return it }
        }
        keys.forEach { key ->
            PluginVariantPriorityStore.selectByPriority(
                engine = key,
                candidates = candidates,
                packageNameOf = { it.serviceInfo.packageName },
                variantOf = { it.pluginInfo?.variant },
            )?.let { selected ->
                if (PluginVariantPriorityStore.getOrder(key).isNotEmpty()) {
                    return selected
                }
            }
        }
        return PluginVariantPriorityStore.selectByPriority(
            engine = keys.firstOrNull(),
            candidates = candidates,
            packageNameOf = { it.serviceInfo.packageName },
            variantOf = { it.pluginInfo?.variant },
        )
    }

    private fun inferCommonEngineFamily(candidates: List<Discovered>): String? {
        if (candidates.isEmpty()) return null
        val families = candidates.mapNotNull { candidate ->
            PluginVariantPriorityStore.normalizeFamilyKey(candidate.pluginInfo?.id)
                ?: PluginVariantPriorityStore.normalizeFamilyKey(candidate.pluginInfo?.engine)
        }.distinct()
        return families.singleOrNull()
    }

    private fun buildSelectionFailure(
        context: Context,
        discovered: List<Discovered>,
        engineId: String?,
        engine: String?,
        variant: String?,
        label: String,
    ): IllegalStateException {
        if (discovered.isEmpty()) {
            return IllegalStateException(context.getString(R.string.error_missing_required_plugin_for_module_label, label))
        }
        val scopedInfoItems = discovered.filter { item ->
            item.pluginInfo?.let { matchesSelectionCriteria(it, engineId, engine, variant) } == true
        }
        if (scopedInfoItems.isEmpty() && hasExplicitSelectionCriteria(engineId, engine, variant)) {
            return IllegalStateException(context.getString(R.string.error_missing_required_plugin_for_module_label, label))
        }
        val variantList = scopedInfoItems
            .mapNotNull { item ->
                item.pluginInfo?.let { info ->
                    info.variant?.takeIf { it.isNotBlank() }
                        ?: info.id?.takeIf { it.isNotBlank() }
                        ?: info.engine?.takeIf { it.isNotBlank() }
                }
            }
            .distinct()
            .joinToString("/")
            .ifBlank { "*" }
        val fallback = context.getString(R.string.error_no_available_enabled_plugin_variants_found, label, variantList)

        if (scopedInfoItems.isEmpty()) {
            val failure = discovered.firstNotNullOfOrNull { item -> item.error?.let { item to it } }
            if (failure != null) {
                val (item, error) = failure
                return IllegalStateException("${fallback}: ${item.serviceInfo.packageName}/${item.serviceInfo.name}: ${error.message ?: error}", error)
            }
            return IllegalStateException(fallback)
        }

        val authorizedItems = scopedInfoItems.filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
        if (authorizedItems.isEmpty()) {
            return IllegalStateException(context.getString(R.string.error_plugin_is_not_authorized_in_plugin_center, scopedInfoItems.first().serviceInfo.packageName))
        }

        val enabledItems = authorizedItems.filter {
            PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, isDefaultEnabled(context, it.serviceInfo.packageName))
        }
        if (enabledItems.isEmpty()) {
            return IllegalStateException(context.getString(R.string.error_plugin_is_not_enabled_in_plugin_center, authorizedItems.first().serviceInfo.packageName))
        }

        val compatibleItems = enabledItems.filter { isHostVersionCompatible(it.pluginInfo) }
        if (compatibleItems.isEmpty()) {
            val requiredVersion = enabledItems
                .mapNotNull { PluginCapabilityResolver.resolveRequiresHostVersion(it.pluginInfo?.capabilities) }
                .maxOrNull()
                ?: 0L
            return IllegalStateException(
                context.getString(
                    R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                    requiredVersion,
                    BuildConfig.VERSION_CODE.toLong(),
                )
            )
        }

        return IllegalStateException(fallback)
    }

    private fun matchesSelectionCriteria(
        pluginInfo: PluginInfo,
        engineId: String?,
        engine: String?,
        variant: String?,
    ): Boolean {
        if (engineId != null && pluginInfo.id != engineId) return false
        if (engine != null && pluginInfo.engine != engine) return false
        if (variant != null && pluginInfo.variant != variant) return false
        return true
    }

    private fun hasExplicitSelectionCriteria(engineId: String?, engine: String?, variant: String?): Boolean {
        return engineId != null || engine != null || variant != null
    }

    private fun isHostVersionCompatible(pluginInfo: PluginInfo?): Boolean {
        val requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(pluginInfo?.capabilities)
        return PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), requiresHostVersion)
    }

    private fun ensureHostVersionCompatible(context: Context, target: Discovered) {
        val requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(target.pluginInfo?.capabilities)
        if (PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), requiresHostVersion)) {
            return
        }
        throw IllegalStateException(
            context.getString(
                R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                requiresHostVersion ?: 0L,
                BuildConfig.VERSION_CODE.toLong(),
            )
        )
    }

    private suspend fun recoverFailedDiscoveries(context: Context, discovered: List<Discovered>) {
        var didWake = false
        discovered
            .filter { it.error != null }
            .forEach { item ->
                val error = item.error!!
                invalidateService(item.serviceInfo, error)
                // A refused binding was already woken inside the bind (see PluginAutoWake); other failures (a plugin
                // process that died, for instance) are woken here, for enabled and authorized plugins only.
                // zh-CN: 被拒的绑定已在绑定内部唤醒过 (见 PluginAutoWake); 其他失败 (例如插件进程已退出) 在此唤醒, 且只针对已启用并已授权的插件.
                val packageName = item.serviceInfo.packageName
                if (!error.isPluginBindRefused() && PluginAutoWake.isEligible(context, packageName, isDefaultEnabled(context, packageName))) {
                    didWake = PluginWakeManager.tryAutoWakeIfNeeded(context, packageName) || didWake
                }
            }
        if (didWake) {
            delay(retryWakeDelayMs)
        }
    }

    private fun queryServices(context: Context, packageName: String? = null): List<ServiceInfo> {
        val intent = Intent(action).apply {
            category?.takeIf { it.isNotBlank() }?.let(::addCategory)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun buildBindFlags(serviceInfo: ServiceInfo): Int {
        val external = externalServiceFlag != 0 && bindExternalServiceFlag != 0 && (serviceInfo.flags and externalServiceFlag) != 0
        return bindFlags(
            sdkInt = Build.VERSION.SDK_INT,
            allowActivityStarts = allowActivityStarts,
            includeCapabilities = includeCapabilities,
            important = important,
            externalServiceFlag = if (external) bindExternalServiceFlag else 0,
        )
    }

    companion object {

        /**
         * The `bindService` flags of a plugin binding, pure for the JVM tests: `BIND_AUTO_CREATE`, plus
         * `BIND_ALLOW_ACTIVITY_STARTS` (API 34+) for a plugin that owns a requested user interface (Android still
         * requires the bound client to be visible; background calls use the plugin notification),
         * `BIND_INCLUDE_CAPABILITIES` (API 29+) for a plugin that records for the host (3-Macaw Speech roadmap D22),
         * `BIND_IMPORTANT`, and the external-service flag of an isolated service.
         * zh-CN: 插件绑定的 `bindService` flags, 为 JVM 测试保持纯函数: `BIND_AUTO_CREATE`, 加上拥有被请求界面的插件所需的
         * `BIND_ALLOW_ACTIVITY_STARTS` (API 34+; Android 仍要求绑定方可见, 后台调用使用插件通知), 替宿主录音的插件所需的
         * `BIND_INCLUDE_CAPABILITIES` (API 29+, 3-Macaw Speech 路线图 D22), `BIND_IMPORTANT`, 以及隔离服务的外部服务 flag.
         */
        @SuppressLint("InlinedApi")
        fun bindFlags(
            sdkInt: Int,
            allowActivityStarts: Boolean,
            includeCapabilities: Boolean,
            important: Boolean,
            externalServiceFlag: Int = 0,
        ): Int {
            var flags = Context.BIND_AUTO_CREATE
            if (allowActivityStarts && sdkInt >= 34) flags = flags or Context.BIND_ALLOW_ACTIVITY_STARTS
            if (includeCapabilities && sdkInt >= 29) flags = flags or Context.BIND_INCLUDE_CAPABILITIES
            if (important) flags = flags or Context.BIND_IMPORTANT
            return flags or externalServiceFlag
        }
    }

    private fun buildBindFailureMessage(context: Context, serviceInfo: ServiceInfo, component: ComponentName, reason: String? = null): String {
        val permission = serviceInfo.permission
        val hasPermission = permission.isNullOrBlank() || context.packageManager.checkPermission(permission, context.packageName) == PackageManager.PERMISSION_GRANTED
        val appInfo = serviceInfo.applicationInfo
        val appStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        val isExternal = externalServiceFlag != 0 && (serviceInfo.flags and externalServiceFlag) != 0
        val reasonText = reason?.let { " | reason=$it" } ?: ""
        return "bindService failed: $component. exported=${serviceInfo.exported} enabled=${serviceInfo.enabled} appEnabled=${appInfo.enabled} stopped=$appStopped perm=$permission hasPerm=$hasPermission external=$isExternal flags=0x${Integer.toHexString(serviceInfo.flags)}$reasonText"
    }

    private suspend fun <T> withService(
        context: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long = defaultBindTimeoutMs,
        enforcePreBindValidator: Boolean = true,
        retryOnRemoteException: Boolean = true,
        block: (I) -> T,
    ): T {
        if (enforcePreBindValidator) preBindValidator?.invoke(context, serviceInfo)
        val appContext = context.applicationContext
        val entry = getOrCreateEntry(appContext, serviceInfo)
        val proxy = PluginAutoWake.retryRefusedBind(appContext, serviceInfo.packageName, isDefaultEnabled(appContext, serviceInfo.packageName)) { awaitProxy(entry, bindTimeoutMs) }
        return try {
            block(proxy)
        } catch (e: RemoteException) {
            if (retryOnRemoteException) {
                retryAfterBinderFailure(entry, bindTimeoutMs, e, block)
            } else {
                throw e
            }
        } finally {
            touch(entry)
        }
    }

    private suspend fun <T> withDedicatedServiceBinding(
        context: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long,
        retryOnRemoteException: Boolean,
        block: (I) -> T,
    ): LeasedCall<T> {
        preBindValidator?.invoke(context, serviceInfo)
        var retriesRemaining = if (retryOnRemoteException) 1 else 0
        while (true) {
            val binding = PluginAutoWake.retryRefusedBind(context.applicationContext, serviceInfo.packageName, isDefaultEnabled(context, serviceInfo.packageName)) {
                awaitDedicatedServiceBinding(context.applicationContext, serviceInfo, bindTimeoutMs)
            }
            try {
                return LeasedCall(
                    value = block(binding.proxy),
                    bindingLease = binding.lease,
                )
            } catch (error: RemoteException) {
                binding.lease.close()
                if (retriesRemaining <= 0) {
                    throw error
                }
                retriesRemaining--
            } catch (error: Throwable) {
                binding.lease.close()
                throw error
            }
        }
    }

    private suspend fun awaitDedicatedServiceBinding(
        appContext: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long,
    ): DedicatedBinding<I> = suspendCancellableCoroutine { continuation ->
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val completionClaimed = AtomicBoolean(false)
        val timeoutRef = AtomicReference<Runnable?>()
        val binderRef = AtomicReference<IBinder?>()
        val deathRecipientRef = AtomicReference<IBinder.DeathRecipient?>()
        lateinit var connection: ServiceConnection

        val lease = ServiceBindingLease {
            timeoutRef.getAndSet(null)?.let(poolHandler::removeCallbacks)
            val binder = binderRef.getAndSet(null)
            val deathRecipient = deathRecipientRef.getAndSet(null)
            if (binder != null && deathRecipient != null) {
                runCatching { binder.unlinkToDeath(deathRecipient, 0) }
            }
            runCatching { appContext.unbindService(connection) }
                .onFailure { Log.w(tag, "Failed to release dedicated plugin binding: $component", it) }
        }

        fun fail(error: Throwable) {
            if (completionClaimed.compareAndSet(false, true)) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(error)
                }
            } else if (!lease.isClosed) {
                Log.w(tag, "Dedicated plugin binding lost after use: ${error.message}")
            }
            lease.close()
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val proxy = runCatching { proxyFactory(service) }.getOrElse { error ->
                    fail(IllegalStateException("Plugin Binder proxy creation failed: $name", error))
                    return
                }
                val deathRecipient = IBinder.DeathRecipient {
                    fail(IllegalStateException("Dedicated plugin binder died: $name"))
                }
                binderRef.set(service)
                deathRecipientRef.set(deathRecipient)
                try {
                    service.linkToDeath(deathRecipient, 0)
                } catch (error: RemoteException) {
                    fail(IllegalStateException("Dedicated plugin binder is already dead: $name", error))
                    return
                }
                if (lease.isClosed || !service.isBinderAlive) {
                    runCatching { service.unlinkToDeath(deathRecipient, 0) }
                    fail(IllegalStateException("Dedicated plugin binder disconnected before use: $name"))
                    return
                }
                if (completionClaimed.compareAndSet(false, true)) {
                    timeoutRef.getAndSet(null)?.let(poolHandler::removeCallbacks)
                    if (!continuation.isCompleted) {
                        continuation.resume(DedicatedBinding(proxy, lease))
                    } else {
                        lease.close()
                    }
                } else {
                    lease.close()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                fail(IllegalStateException("Dedicated plugin service disconnected: $name"))
            }

            override fun onBindingDied(name: ComponentName) {
                fail(IllegalStateException("Dedicated plugin binding died: $name"))
            }

            override fun onNullBinding(name: ComponentName) {
                fail(IllegalStateException("Dedicated plugin returned a null binding: $name"))
            }
        }

        continuation.invokeOnCancellation {
            completionClaimed.set(true)
            lease.close()
        }

        val timeout = Runnable {
            fail(TimeoutException("Dedicated bindService timeout: $component"))
        }
        timeoutRef.set(timeout)
        poolHandler.postDelayed(timeout, bindTimeoutMs)

        val intent = Intent().setComponent(component)
        val bindFlags = buildBindFlags(serviceInfo)
        var bound = try {
            Log.i(tag, "dedicated bindService: $component")
            appContext.bindService(intent, connection, bindFlags)
        } catch (error: SecurityException) {
            fail(
                IllegalStateException(
                    "Dedicated bindService SecurityException: $component. " +
                            "Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> " +
                            "and the Service uses this permission.",
                    error,
                )
            )
            return@suspendCancellableCoroutine
        }
        if (!bound && bindFlags != Context.BIND_AUTO_CREATE) {
            bound = try {
                appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (error: SecurityException) {
                fail(
                    IllegalStateException(
                        "Dedicated bindService SecurityException: $component. " +
                                "Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> " +
                                "and the Service uses this permission.",
                        error,
                    )
                )
                return@suspendCancellableCoroutine
            }
        }
        if (!bound) {
            fail(PluginBindRefusedException(serviceInfo.packageName, buildBindFailureMessage(appContext, serviceInfo, component)))
            return@suspendCancellableCoroutine
        }
        lease.markRegistered()
    }

    private fun invalidateService(serviceInfo: ServiceInfo, error: Throwable) {
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val entry = synchronized(poolLock) { connectionPool[component] }
        if (entry != null) {
            invalidateEntry(entry, error)
        }
    }

    private suspend fun <T> retryAfterBinderFailure(
        entry: PooledConnection,
        bindTimeoutMs: Long,
        error: RemoteException,
        block: (I) -> T,
    ): T {
        invalidateEntry(entry, error)
        val proxy = PluginAutoWake.retryRefusedBind(entry.appContext, entry.serviceInfo.packageName, isDefaultEnabled(entry.appContext, entry.serviceInfo.packageName)) { awaitProxy(entry, bindTimeoutMs) }
        return try {
            block(proxy)
        } catch (retryError: RemoteException) {
            invalidateEntry(entry, retryError)
            throw retryError
        }
    }

    private fun touch(entry: PooledConnection) {
        synchronized(entry.lock) {
            entry.lastUsed = uptimeMillis()
        }
        scheduleIdleUnbind(entry)
    }

    private suspend fun awaitProxy(entry: PooledConnection, bindTimeoutMs: Long): I =
        suspendCancellableCoroutine { cont ->
            var shouldBind = false
            synchronized(entry.lock) {
                entry.lastUsed = uptimeMillis()
                cancelIdleUnbindLocked(entry)
                val existing = entry.proxy
                if (existing != null && entry.bound && entry.isBinderAliveLocked()) {
                    cont.resume(existing)
                    return@suspendCancellableCoroutine
                }
                if (existing != null) {
                    entry.clearConnectionLocked()
                }
                entry.waiters.add(cont)
                if (!entry.connecting) {
                    entry.connecting = true
                    shouldBind = true
                }
            }
            if (shouldBind) {
                startBind(entry, bindTimeoutMs)
            }
            cont.invokeOnCancellation {
                synchronized(entry.lock) {
                    entry.waiters.remove(cont)
                }
            }
        }

    private fun startBind(entry: PooledConnection, bindTimeoutMs: Long) {
        val intent = Intent().setComponent(entry.component)
        val bindFlags = buildBindFlags(entry.serviceInfo)
        var bound = try {
            Log.i(tag, "bindService: ${entry.component}")
            entry.appContext.bindService(intent, entry.connection, bindFlags)
        } catch (e: SecurityException) {
            failWaiters(
                entry,
                IllegalStateException(
                    "bindService SecurityException: ${entry.component}. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.",
                    e,
                ),
            )
            return
        }
        if (!bound && bindFlags != Context.BIND_AUTO_CREATE) {
            bound = try {
                entry.appContext.bindService(intent, entry.connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                failWaiters(
                    entry,
                    IllegalStateException(
                        "bindService SecurityException: ${entry.component}. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.",
                        e,
                    ),
                )
                return
            }
        }
        if (!bound) {
            val message = buildBindFailureMessage(entry.appContext, entry.serviceInfo, entry.component)
            Log.e(tag, message)
            failWaiters(entry, PluginBindRefusedException(entry.serviceInfo.packageName, message))
            return
        }

        val timeout = Runnable {
            val pending = synchronized(entry.lock) {
                entry.connecting = false
                val list = entry.waiters.toList()
                entry.waiters.clear()
                entry.bindTimeoutRunnable = null
                list
            }
            if (pending.isNotEmpty()) {
                Log.e(tag, "bindService timeout: ${entry.component}")
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resumeWithException(TimeoutException("bindService timeout: ${entry.component}"))
                    }
                }
                runCatching { entry.appContext.unbindService(entry.connection) }
            }
        }
        synchronized(entry.lock) {
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = timeout
        }
        poolHandler.postDelayed(timeout, bindTimeoutMs)
    }

    private fun invalidateEntry(entry: PooledConnection, error: Throwable) {
        synchronized(entry.lock) {
            entry.clearConnectionLocked()
            entry.connecting = false
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = null
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = null
        }
        poolHandler.post {
            runCatching { entry.appContext.unbindService(entry.connection) }
            Log.w(tag, "invalidateEntry: ${entry.component} | ${error.message}")
        }
    }

    private fun failWaiters(entry: PooledConnection, error: Throwable) {
        val pending = synchronized(entry.lock) {
            entry.connecting = false
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = null
            val list = entry.waiters.toList()
            entry.waiters.clear()
            list
        }
        pending.forEach { waiter ->
            if (!waiter.isCompleted) {
                waiter.resumeWithException(error)
            }
        }
    }

    private fun scheduleIdleUnbind(entry: PooledConnection) {
        val runnable = Runnable {
            val shouldUnbind = synchronized(entry.lock) {
                val idleEnough = uptimeMillis() - entry.lastUsed >= idleUnbindMs
                idleEnough && entry.bound && entry.proxy != null && !entry.connecting
            }
            if (shouldUnbind) {
                runCatching { entry.appContext.unbindService(entry.connection) }
                synchronized(entry.lock) {
                    entry.clearConnectionLocked()
                }
            }
        }
        synchronized(entry.lock) {
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = runnable
        }
        poolHandler.postDelayed(runnable, idleUnbindMs)
    }

    private fun cancelIdleUnbindLocked(entry: PooledConnection) {
        entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
        entry.idleRunnable = null
    }

    private fun getOrCreateEntry(appContext: Context, serviceInfo: ServiceInfo): PooledConnection {
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        synchronized(poolLock) {
            return connectionPool.getOrPut(component) {
                PooledConnection(appContext, serviceInfo)
            }
        }
    }

    private fun evictEntry(entry: PooledConnection) {
        synchronized(poolLock) {
            if (connectionPool[entry.component] === entry) {
                connectionPool.remove(entry.component)
            }
        }
    }

    private inner class PooledConnection(
        val appContext: Context,
        val serviceInfo: ServiceInfo,
    ) {
        val component: ComponentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val lock = Any()
        val waiters = ArrayList<CancellableContinuation<I>>()
        var proxy: I? = null
        var binder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var bound = false
        var connecting = false
        var lastUsed = 0L
        var idleRunnable: Runnable? = null
        var bindTimeoutRunnable: Runnable? = null

        fun isBinderAliveLocked(): Boolean {
            val activeBinder = binder ?: proxy?.asBinder()
            return activeBinder?.isBinderAlive == true
        }

        fun clearConnectionLocked() {
            val currentBinder = binder
            val currentDeathRecipient = deathRecipient
            if (currentBinder != null && currentDeathRecipient != null) {
                runCatching { currentBinder.unlinkToDeath(currentDeathRecipient, 0) }
            }
            binder = null
            deathRecipient = null
            proxy = null
            bound = false
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.i(tag, "onServiceConnected: $name")
                val nextProxy = runCatching { proxyFactory(service) }.getOrElse { error ->
                    Log.w(tag, "Plugin Binder proxy creation failed: $name", error)
                    handleDisconnect(name, "proxyFactory", shouldUnbind = true)
                    return
                }
                val nextDeathRecipient = IBinder.DeathRecipient {
                    Log.w(tag, "binderDied: $name")
                    handleDisconnect(name, "binderDied", shouldUnbind = true)
                }
                try {
                    service.linkToDeath(nextDeathRecipient, 0)
                } catch (e: RemoteException) {
                    Log.w(tag, "linkToDeath failed: $name", e)
                    handleDisconnect(name, "linkToDeath", shouldUnbind = true)
                    return
                }
                val pending = synchronized(lock) {
                    clearConnectionLocked()
                    binder = service
                    deathRecipient = nextDeathRecipient
                    proxy = nextProxy
                    bound = true
                    connecting = false
                    lastUsed = uptimeMillis()
                    bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                    bindTimeoutRunnable = null
                    val list = waiters.toList()
                    waiters.clear()
                    list
                }
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resume(nextProxy)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(tag, "onServiceDisconnected: $name")
                handleDisconnect(name, "onServiceDisconnected", shouldUnbind = true)
            }

            override fun onBindingDied(name: ComponentName) {
                Log.w(tag, "onBindingDied: $name")
                handleDisconnect(name, "onBindingDied", shouldUnbind = true)
            }

            override fun onNullBinding(name: ComponentName) {
                Log.w(tag, "onNullBinding: $name")
                handleDisconnect(name, "onNullBinding", shouldUnbind = true)
            }
        }

        private fun handleDisconnect(name: ComponentName, reason: String, shouldUnbind: Boolean = false) {
            val pending = synchronized(lock) {
                clearConnectionLocked()
                connecting = false
                bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                bindTimeoutRunnable = null
                idleRunnable?.let { poolHandler.removeCallbacks(it) }
                idleRunnable = null
                val list = waiters.toList()
                waiters.clear()
                list
            }
            // Android may deliver several terminal callbacks for the same binding. Retire this
            // pooled entry by identity before posting any unbind, so a late callback from the old
            // ServiceConnection cannot cancel a replacement binding created for the same component.
            evictEntry(this)
            if (pending.isNotEmpty()) {
                val error = IllegalStateException("$reason: $name")
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resumeWithException(error)
                    }
                }
            }
            if (shouldUnbind) {
                poolHandler.post {
                    runCatching { appContext.unbindService(connection) }
                }
            }
        }
    }

    private data class SelectionResult(
        val selected: Discovered?,
        val discovered: List<Discovered>,
    )

    private data class DedicatedBinding<T : IInterface>(
        val proxy: T,
        val lease: ServiceBindingLease,
    )

    private class TimeoutException(message: String) : RuntimeException(message)
}
