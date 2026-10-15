package org.autojs.autojs.core.plugin.barcode

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock.uptimeMillis
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.PluginBindRefusedException
import org.autojs.autojs.core.plugin.isPluginBindRefused
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.center.PluginVariantPriorityStore
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.mlkit.barcode.api.BarcodeOptions
import org.autojs.plugin.mlkit.barcode.api.BarcodePluginActions
import org.autojs.plugin.mlkit.barcode.api.BarcodeResult
import org.autojs.plugin.mlkit.barcode.api.IBarcodePlugin
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object BarcodePluginHost {

    private const val TAG = "BarcodePluginHost"

    const val ACTION_BARCODE = BarcodePluginActions.BARCODE

    private const val DEFAULT_BIND_TIMEOUT_MS = 60_000L
    private const val DEFAULT_CALL_TIMEOUT_MS = 60_000L
    private const val IDLE_UNBIND_MS = 30_000L
    private const val RETRY_WAKE_DELAY_MS = 300L
    private const val MODULE_LABEL_BARCODE_PLUGIN = "ML Kit Barcode plugin"
    private val SERVICE_QUERY_RETRY_DELAYS_MS = longArrayOf(150L, 300L, 600L, 1000L, 1500L)

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

    suspend fun discover(context: Context, packageName: String? = null): List<Discovered> {
        val services = queryBarcodeServices(context, packageName)
        Log.i(TAG, "discover: services=${services.size}")
        return services.map { svc ->
            Log.i(TAG, "discover: ${svc.packageName}/${svc.name}")
            val result = runCatching { withService(context, svc, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() } }
            val error = result.exceptionOrNull()
            if (error != null) {
                Log.w(TAG, "getInfo failed: ${error.message}")
            }
            Discovered(svc, result.getOrNull(), error)
        }
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo {
        if (!PluginTrustManager.isAuthorized(context, packageName)) {
            error("Plugin not authorized: $packageName")
        }
        val serviceInfo = queryFirstBarcodeServiceWithRetry(context, packageName)
            ?: error("No Barcode service found for package: $packageName")
        return withService(context, serviceInfo, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() }
    }

    fun queryServiceCount(context: Context): Int = queryBarcodeServices(context).size

    suspend fun detect(
        context: Context,
        target: Discovered,
        bitmap: Bitmap,
        options: BarcodeOptions = BarcodeOptions(),
        callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    ): List<BarcodeResult> {
        if (!PluginTrustManager.isAuthorized(context, target.serviceInfo.packageName)) {
            error("Plugin not authorized: ${target.serviceInfo.packageName}")
        }
        ensureHostVersionCompatible(context, target)
        val start = uptimeMillis()
        return withImagePfdService(context, target.serviceInfo, bitmap, options, callTimeoutMs, start) { proxy, pfd ->
            val results = proxy.detect(pfd, options)
            Log.i(TAG, "detectAidl: got ${results.size} items")
            results
        }
    }

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
        moduleLabel: String = MODULE_LABEL_BARCODE_PLUGIN,
    ): Discovered {
        val result = selectDetailed(context, engineId, engine, variant)
        return result.selected ?: throw buildSelectionFailure(context, result.discovered, engineId, engine, variant, moduleLabel)
    }

    private suspend fun queryFirstBarcodeServiceWithRetry(context: Context, packageName: String): ServiceInfo? {
        queryBarcodeServices(context, packageName).firstOrNull()?.let { return it }
        SERVICE_QUERY_RETRY_DELAYS_MS.forEach { delayMs ->
            delay(delayMs)
            queryBarcodeServices(context, packageName).firstOrNull()?.let { return it }
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
            .filter { PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
            .filter { isHostVersionCompatible(it.pluginInfo) }
        if (list.isEmpty()) return null
        if (hasExplicitSelectionCriteria(engineId, engine, variant)) {
            val candidates = list.filter { d ->
                d.pluginInfo?.let { matchesSelectionCriteria(it, engineId, engine, variant) } == true
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
        val byPkg = candidates.associateBy { it.serviceInfo.packageName }
        val fallbackOrder = PluginVariantPriorityStore.getBestOrderForPackages(byPkg.keys)
        if (fallbackOrder.isNotEmpty()) {
            fallbackOrder.firstNotNullOfOrNull { byPkg[it] }?.let { return it }
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
        moduleLabel: String,
    ): IllegalStateException {
        if (discovered.isEmpty()) {
            return IllegalStateException(context.getString(R.string.error_missing_required_plugin_for_module_label, moduleLabel))
        }
        val scopedInfoItems = discovered.filter { item ->
            item.pluginInfo?.let { matchesSelectionCriteria(it, engineId, engine, variant) } == true
        }
        if (scopedInfoItems.isEmpty() && hasExplicitSelectionCriteria(engineId, engine, variant)) {
            return IllegalStateException(context.getString(R.string.error_missing_required_plugin_for_module_label, moduleLabel))
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
        val fallback = context.getString(R.string.error_no_available_enabled_plugin_variants_found, moduleLabel, variantList)
        val infoItems = scopedInfoItems
        if (infoItems.isEmpty()) {
            val failure = discovered.firstNotNullOfOrNull { item -> item.error?.let { item to it } }
            if (failure != null) {
                val (item, error) = failure
                return IllegalStateException("${fallback}: ${item.serviceInfo.packageName}/${item.serviceInfo.name}: ${error.message ?: error}", error)
            }
            return IllegalStateException(fallback)
        }
        val authorizedItems = infoItems.filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
        if (authorizedItems.isEmpty()) {
            return IllegalStateException(context.getString(R.string.error_plugin_is_not_authorized_in_plugin_center, infoItems.first().serviceInfo.packageName))
        }
        val enabledItems = authorizedItems.filter { PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, true) }
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
                // Refused bindings were already woken inside the bind (PluginAutoWake); wake only enabled and authorized plugins here.
                // zh-CN: 被拒的绑定已在绑定内部唤醒 (PluginAutoWake); 这里只唤醒已启用并已授权的插件.
                val packageName = item.serviceInfo.packageName
                if (!error.isPluginBindRefused() && PluginAutoWake.isEligible(context, packageName)) {
                    didWake = PluginWakeManager.tryAutoWakeIfNeeded(context, packageName) || didWake
                }
            }
        if (didWake) {
            delay(RETRY_WAKE_DELAY_MS)
        }
    }

    private suspend fun <T> withImagePfdService(
        context: Context,
        serviceInfo: ServiceInfo,
        bitmap: Bitmap,
        options: BarcodeOptions,
        callTimeoutMs: Long,
        start: Long,
        block: suspend (IBarcodePlugin, ParcelFileDescriptor) -> T,
    ): T {
        return try {
            withImagePfdServiceOnce(context, serviceInfo, bitmap, options, callTimeoutMs, start, block)
        } catch (e: RemoteException) {
            invalidateService(serviceInfo, e)
            try {
                withImagePfdServiceOnce(context, serviceInfo, bitmap, options, callTimeoutMs, start, block)
            } catch (retryError: RemoteException) {
                invalidateService(serviceInfo, retryError)
                throw retryError
            }
        }
    }

    private suspend fun <T> withImagePfdServiceOnce(
        context: Context,
        serviceInfo: ServiceInfo,
        bitmap: Bitmap,
        options: BarcodeOptions,
        callTimeoutMs: Long,
        start: Long,
        block: suspend (IBarcodePlugin, ParcelFileDescriptor) -> T,
    ): T {
        return createEncodedPfd(bitmap, options).use { pfd ->
            withService(context, serviceInfo, DEFAULT_BIND_TIMEOUT_MS, retryOnRemoteException = false) { proxy ->
                val remain = callTimeoutMs - (uptimeMillis() - start)
                if (remain <= 0) error("AIDL call timeout in ${callTimeoutMs / 1000} seconds")
                block(proxy, pfd)
            }
        }
    }

    private fun createEncodedPfd(bmp: Bitmap, options: BarcodeOptions): ParcelFileDescriptor {
        val extras = options.extras
        val quality = extras?.getInt("imageQuality", 100)?.coerceIn(1, 100) ?: 100
        // WEBP is required on Android versions before API 30.
        // zh-CN: API 30 之前的 Android 版本仍需使用 WEBP.
        @Suppress("DEPRECATION")
        val format = when (extras?.getString("imageFormat")?.trim()?.lowercase()) {
            "jpg", "jpeg" -> CompressFormat.JPEG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CompressFormat.WEBP_LOSSY else CompressFormat.WEBP
            else -> CompressFormat.PNG
        }
        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe[0]
        val writeFd = pipe[1]
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    require(bmp.compress(format, quality, out)) { "Failed to encode bitmap" }
                }
            }.onFailure { t ->
                Log.e(TAG, "encode bitmap failed: ${t.message}")
            }
        }
        return readFd
    }

    private fun queryBarcodeServices(context: Context, packageName: String? = null): List<ServiceInfo> {
        val pm = context.packageManager
        val intent = Intent(ACTION_BARCODE).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentServices(intent, 0)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun buildBindFlags(serviceInfo: ServiceInfo): Int {
        var flags = Context.BIND_AUTO_CREATE
        if (externalServiceFlag != 0 && bindExternalServiceFlag != 0 && (serviceInfo.flags and externalServiceFlag) != 0) {
            flags = flags or bindExternalServiceFlag
        }
        return flags
    }

    private fun buildBindFailureMessage(context: Context, serviceInfo: ServiceInfo, cn: ComponentName, reason: String? = null): String {
        val pm = context.packageManager
        val perm = serviceInfo.permission
        val hasPerm = perm.isNullOrBlank() || pm.checkPermission(perm, context.packageName) == PackageManager.PERMISSION_GRANTED
        val appInfo = serviceInfo.applicationInfo
        val appStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        val isExternal = externalServiceFlag != 0 && (serviceInfo.flags and externalServiceFlag) != 0
        val reasonText = reason?.let { " | reason=$it" } ?: ""
        return "bindService failed: $cn. exported=${serviceInfo.exported} enabled=${serviceInfo.enabled} appEnabled=${appInfo.enabled} stopped=$appStopped perm=$perm hasPerm=$hasPerm external=$isExternal flags=0x${Integer.toHexString(serviceInfo.flags)}$reasonText"
    }

    private suspend fun <T> withService(
        context: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long = DEFAULT_BIND_TIMEOUT_MS,
        retryOnRemoteException: Boolean = true,
        block: suspend (IBarcodePlugin) -> T,
    ): T {
        val appCtx = context.applicationContext
        val entry = getOrCreateEntry(appCtx, serviceInfo)
        val proxy = PluginAutoWake.retryRefusedBind(entry.appCtx, entry.serviceInfo.packageName) { awaitProxy(entry, bindTimeoutMs) }
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

    private fun invalidateService(serviceInfo: ServiceInfo, error: Throwable) {
        val cn = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val entry = synchronized(poolLock) { connectionPool[cn] }
        if (entry != null) {
            invalidateEntry(entry, error)
        }
    }

    private suspend fun <T> retryAfterBinderFailure(
        entry: PooledConnection,
        bindTimeoutMs: Long,
        error: RemoteException,
        block: suspend (IBarcodePlugin) -> T,
    ): T {
        invalidateEntry(entry, error)
        val proxy = PluginAutoWake.retryRefusedBind(entry.appCtx, entry.serviceInfo.packageName) { awaitProxy(entry, bindTimeoutMs) }
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

    private suspend fun awaitProxy(entry: PooledConnection, bindTimeoutMs: Long): IBarcodePlugin =
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
        val intent = Intent().setComponent(entry.cn)
        val bindFlags = buildBindFlags(entry.serviceInfo)
        var ok = try {
            Log.i(TAG, "bindService: ${entry.cn}")
            entry.appCtx.bindService(intent, entry.connection, bindFlags)
        } catch (se: SecurityException) {
            failWaiters(
                entry,
                IllegalStateException(
                    "bindService SecurityException: ${entry.cn}. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.",
                    se,
                ),
            )
            return
        }
        if (!ok && bindFlags != Context.BIND_AUTO_CREATE) {
            ok = try {
                entry.appCtx.bindService(intent, entry.connection, Context.BIND_AUTO_CREATE)
            } catch (se: SecurityException) {
                failWaiters(
                    entry,
                    IllegalStateException(
                        "bindService SecurityException: ${entry.cn}. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.",
                        se,
                    ),
                )
                return
            }
        }
        if (!ok) {
            val msg = buildBindFailureMessage(entry.appCtx, entry.serviceInfo, entry.cn)
            Log.e(TAG, msg)
            failWaiters(entry, PluginBindRefusedException(entry.serviceInfo.packageName, msg))
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
                Log.e(TAG, "bindService timeout: ${entry.cn}")
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resumeWithException(TimeoutException("bindService timeout: ${entry.cn}"))
                    }
                }
                runCatching { entry.appCtx.unbindService(entry.connection) }
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
            runCatching { entry.appCtx.unbindService(entry.connection) }
            Log.w(TAG, "invalidateEntry: ${entry.cn} | ${error.message}")
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
                val idleEnough = uptimeMillis() - entry.lastUsed >= IDLE_UNBIND_MS
                idleEnough && entry.bound && entry.proxy != null && !entry.connecting
            }
            if (shouldUnbind) {
                runCatching { entry.appCtx.unbindService(entry.connection) }
                synchronized(entry.lock) {
                    entry.clearConnectionLocked()
                }
            }
        }
        synchronized(entry.lock) {
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = runnable
        }
        poolHandler.postDelayed(runnable, IDLE_UNBIND_MS)
    }

    private fun cancelIdleUnbindLocked(entry: PooledConnection) {
        entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
        entry.idleRunnable = null
    }

    private fun getOrCreateEntry(appCtx: Context, serviceInfo: ServiceInfo): PooledConnection {
        val cn = ComponentName(serviceInfo.packageName, serviceInfo.name)
        synchronized(poolLock) {
            return connectionPool.getOrPut(cn) {
                PooledConnection(appCtx, serviceInfo)
            }
        }
    }

    private class PooledConnection(
        val appCtx: Context,
        val serviceInfo: ServiceInfo,
    ) {
        val cn: ComponentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val lock = Any()
        val waiters = ArrayList<CancellableContinuation<IBarcodePlugin>>()
        var proxy: IBarcodePlugin? = null
        var binder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var bound: Boolean = false
        var connecting: Boolean = false
        var lastUsed: Long = 0L
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
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "onServiceConnected: $name")
                val proxy = IBarcodePlugin.Stub.asInterface(binder)
                val deathRecipient = IBinder.DeathRecipient {
                    Log.w(TAG, "binderDied: $name")
                    handleDisconnect(name, "binderDied", shouldUnbind = true)
                }
                try {
                    binder.linkToDeath(deathRecipient, 0)
                } catch (e: RemoteException) {
                    Log.w(TAG, "linkToDeath failed: $name", e)
                    handleDisconnect(name, "linkToDeath", shouldUnbind = true)
                    return
                }
                val pending = synchronized(lock) {
                    clearConnectionLocked()
                    this@PooledConnection.binder = binder
                    this@PooledConnection.deathRecipient = deathRecipient
                    this@PooledConnection.proxy = proxy
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
                        waiter.resume(proxy)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "onServiceDisconnected: $name")
                handleDisconnect(name, "onServiceDisconnected", shouldUnbind = true)
            }

            override fun onBindingDied(name: ComponentName) {
                Log.w(TAG, "onBindingDied: $name")
                handleDisconnect(name, "onBindingDied", shouldUnbind = true)
            }

            override fun onNullBinding(name: ComponentName) {
                Log.w(TAG, "onNullBinding: $name")
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
                    runCatching { appCtx.unbindService(connection) }
                }
            }
        }
    }

    private data class SelectionResult(
        val selected: Discovered?,
        val discovered: List<Discovered>,
    )

    private class TimeoutException(msg: String) : RuntimeException(msg)
}
