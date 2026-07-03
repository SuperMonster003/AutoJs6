package org.autojs.autojs.core.plugin.ocr

import android.annotation.SuppressLint
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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SharedMemory
import android.os.SystemClock.uptimeMillis
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.center.PluginVariantPriorityStore
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.paddle.ocr.api.IOcrPlugin
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.autojs.plugin.paddle.ocr.api.PaddleOcrPluginCapabilityKeys
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 26, 2026.
 */
object PaddleOcrPluginHost {

    private const val TAG = "PaddleOcrPluginHost"

    const val ACTION_OCR = "org.autojs.plugin.PADDLE_OCR"

    private const val DEFAULT_BIND_TIMEOUT_MS = 60_000L
    private const val DEFAULT_CALL_TIMEOUT_MS = 60_000L
    private const val IDLE_UNBIND_MS = 30_000L
    private const val RETRY_WAKE_DELAY_MS = 300L
    private const val MODULE_LABEL_PADDLE_OCR = "Paddle OCR"

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

    suspend fun discover(context: Context): List<Discovered> {
        val services = queryOcrServices(context)
        Log.i(TAG, "discover: services=${services.size}")
        return services.map { svc ->
            Log.i(TAG, "discover: ${svc.packageName}/${svc.name}")
            val result = runCatching { withService(context, svc, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() } }
            val info = result.getOrNull()
            val error = result.exceptionOrNull()
            if (error != null) {
                Log.w(TAG, "getInfo failed: ${error.message}")
            }
            Discovered(svc, info, error)
        }
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo {
        if (!PluginTrustManager.isAuthorized(context, packageName)) {
            error("Plugin not authorized: $packageName")
        }
        val serviceInfo = queryOcrServices(context, packageName).firstOrNull()
            ?: error("No OCR service found for package: $packageName")
        return withService(context, serviceInfo, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() }
    }

    suspend fun recognizeText(
        context: Context,
        target: Discovered,
        bitmap: Bitmap,
        options: OcrOptions = OcrOptions(),
        callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    ): List<String> {
        if (!PluginTrustManager.isAuthorized(context, target.serviceInfo.packageName)) {
            error("Plugin not authorized: ${target.serviceInfo.packageName}")
        }
        ensureHostVersionCompatible(context, target)
        ensureRawSupport(target, options)
        val start = uptimeMillis()
        return withImagePfdService(context, target.serviceInfo, bitmap, options, callTimeoutMs, start) { proxy, pfd ->
            val lines = proxy.recognizeText(pfd, options)
            Log.i(TAG, "recognizeTextAidl: got ${lines.size} lines")
            lines
        }
    }

    suspend fun detect(
        context: Context,
        target: Discovered,
        bitmap: Bitmap,
        options: OcrOptions = OcrOptions(),
        callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    ): List<OcrResult> {
        if (!PluginTrustManager.isAuthorized(context, target.serviceInfo.packageName)) {
            error("Plugin not authorized: ${target.serviceInfo.packageName}")
        }
        ensureHostVersionCompatible(context, target)
        ensureRawSupport(target, options)
        val start = uptimeMillis()
        return withImagePfdService(context, target.serviceInfo, bitmap, options, callTimeoutMs, start) { proxy, pfd ->
            val results = proxy.detect(pfd, options)
            Log.i(TAG, "detectAidl: got ${results.size} items")
            results
        }
    }

    suspend fun select(
        context: Context,
        // e.g. "paddle-ocr-pp-ocrv5"
        engineId: String? = null,
        // e.g. "paddle-ocr"
        engine: String? = null,
        // e.g. "v5"
        variant: String? = null,
    ): Discovered? {
        return selectDetailed(context, engineId, engine, variant).selected
    }

    suspend fun selectOrThrow(
        context: Context,
        // e.g. "paddle-ocr-pp-ocrv5"
        engineId: String? = null,
        // e.g. "paddle-ocr"
        engine: String? = null,
        // e.g. "v5"
        variant: String? = null,
        moduleLabel: String = MODULE_LABEL_PADDLE_OCR,
    ): Discovered {
        val result = selectDetailed(context, engineId, engine, variant)
        return result.selected ?: throw buildSelectionFailure(context, result.discovered, engineId, engine, variant, moduleLabel)
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
        if (engineId != null) {
            list.firstOrNull { d -> d.pluginInfo?.id == engineId }?.let { return it }
        }
        if (engine != null && variant != null) {
            list.firstOrNull { d -> d.pluginInfo?.engine == engine && d.pluginInfo.variant == variant }?.let { return it }
        }
        if (engine != null) {
            val candidates = list.filter { d -> d.pluginInfo?.engine == engine }
            selectByPriorityWithKeys(
                candidates = candidates,
                keys = PluginVariantPriorityStore.buildPriorityKeys(
                    engine = engine,
                    engineId = engineId ?: inferCommonEngineFamily(candidates),
                ),
            )?.let { return it }
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
        return list.maxBy {
            val variant = it.pluginInfo?.variant ?: return@maxBy 0
            variant.replace(Regex("\\D"), "").toIntOrNull() ?: 0
        }
    }

    private fun selectByPriorityWithKeys(
        candidates: List<Discovered>,
        keys: List<String>,
    ): Discovered? {
        if (candidates.isEmpty()) return null
        val byPkg = candidates.associateBy { it.serviceInfo.packageName }
        val fallbackOrder = PluginVariantPriorityStore.getBestOrderForPackages(
            byPkg.keys,
        )
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
                val order = PluginVariantPriorityStore.getOrder(key)
                if (order.isNotEmpty()) {
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
            return IllegalStateException(
                context.getString(R.string.error_missing_required_plugin_for_module_label, moduleLabel)
            )
        }
        val scopedInfoItems = discovered.filter { item ->
            item.pluginInfo?.let { matchesSelectionCriteria(it, engineId, engine, variant) } == true
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
        val fallback = context.getString(
            R.string.error_no_available_enabled_plugin_variants_found,
            moduleLabel,
            variantList,
        )

        val infoItems = scopedInfoItems
        if (infoItems.isEmpty()) {
            val failure = discovered.firstNotNullOfOrNull { item ->
                item.error?.let { item to it }
            }
            if (failure != null) {
                val (item, error) = failure
                val message = "${fallback}: ${item.serviceInfo.packageName}/${item.serviceInfo.name}: ${error.message ?: error}"
                return IllegalStateException(message, error)
            }
            return IllegalStateException(fallback)
        }

        val authorizedItems = infoItems.filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
        if (authorizedItems.isEmpty()) {
            return IllegalStateException(
                context.getString(R.string.error_plugin_is_not_authorized_in_plugin_center, infoItems.first().serviceInfo.packageName)
            )
        }

        val enabledItems = authorizedItems.filter {
            PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, true)
        }
        if (enabledItems.isEmpty()) {
            return IllegalStateException(
                context.getString(R.string.error_plugin_is_not_enabled_in_plugin_center, authorizedItems.first().serviceInfo.packageName)
            )
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
                invalidateService(item.serviceInfo, item.error!!)
                didWake = PluginWakeManager.tryAutoWakeIfNeeded(context, item.serviceInfo.packageName) || didWake
            }
        if (didWake) {
            delay(RETRY_WAKE_DELAY_MS)
        }
    }

    private suspend fun <T> withImagePfdService(
        context: Context,
        serviceInfo: ServiceInfo,
        bitmap: Bitmap,
        options: OcrOptions,
        callTimeoutMs: Long,
        start: Long,
        block: suspend (IOcrPlugin, ParcelFileDescriptor) -> T,
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
        options: OcrOptions,
        callTimeoutMs: Long,
        start: Long,
        block: suspend (IOcrPlugin, ParcelFileDescriptor) -> T,
    ): T {
        return createTempPfd(bitmap, options).use { pfd ->
            withService(context, serviceInfo, DEFAULT_BIND_TIMEOUT_MS, retryOnRemoteException = false) { proxy ->
                val remain = callTimeoutMs - (uptimeMillis() - start)
                if (remain <= 0) error("AIDL call timeout in ${callTimeoutMs / 1000} seconds")
                block(proxy, pfd)
            }
        }
    }

    // Convert temporary file to read-only FD.
    // zh-CN: 临时文件转换为只读 FD.
    private fun createTempPfd(bmp: Bitmap, options: OcrOptions): ParcelFileDescriptor {
        val useRaw = options.extras?.getBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, false) == true
        if (useRaw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return runCatching { createRawPfd(bmp, options) }
                .getOrElse {
                    Log.w(TAG, "raw image transfer failed: ${it.message}")
                    disableRawExtras(options)
                    createEncodedPfd(bmp, options)
                }
        }
        if (useRaw) {
            disableRawExtras(options)
        }
        return createEncodedPfd(bmp, options)
    }

    private fun createEncodedPfd(bmp: Bitmap, options: OcrOptions): ParcelFileDescriptor {
        val (format, quality) = resolveEncodeOptions(bmp, options)
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

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun createRawPfd(bmp: Bitmap, options: OcrOptions): ParcelFileDescriptor {
        val src = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
        val shouldRecycle = src !== bmp
        val rowBytes = src.rowBytes
        val size = rowBytes * src.height

        val extras = options.extras ?: Bundle().also { options.extras = it }
        extras.putBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, true)
        extras.putInt(PaddleOcrOptionExtraKeys.RAW_WIDTH, src.width)
        extras.putInt(PaddleOcrOptionExtraKeys.RAW_HEIGHT, src.height)
        extras.putInt(PaddleOcrOptionExtraKeys.RAW_STRIDE, rowBytes)
        extras.putString(PaddleOcrOptionExtraKeys.RAW_CONFIG, Bitmap.Config.ARGB_8888.name)

        runCatching {
            val shm = SharedMemory.create("ocr_ipc_raw", size)
            val buffer = shm.mapReadWrite()
            buffer.order(ByteOrder.nativeOrder())
            src.copyPixelsToBuffer(buffer)
            SharedMemory.unmap(buffer)

            val pfd = dupSharedMemoryFd(shm)
            shm.close()
            if (pfd != null) {
                if (shouldRecycle) {
                    src.recycle()
                }
                return pfd
            }
        }.onFailure { t ->
            Log.w(TAG, "shared memory unavailable, fallback to raw pipe: ${t.message}")
        }

        return createRawPipePfd(src, size, shouldRecycle)
    }

    private fun createRawPipePfd(
        bmp: Bitmap,
        size: Int,
        shouldRecycle: Boolean,
    ): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe[0]
        val writeFd = pipe[1]
        val raw = ByteArray(size)
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buffer)

        if (shouldRecycle) {
            bmp.recycle()
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    out.write(raw, 0, size)
                }
            }.onFailure { t ->
                Log.e(TAG, "write raw pipe failed: ${t.message}")
            }
        }
        return readFd
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun dupSharedMemoryFd(shm: SharedMemory): ParcelFileDescriptor? {
        val method = shm.javaClass.methods.firstOrNull { it.name == "getFdDup" && it.parameterCount == 0 }
            ?: shm.javaClass.methods.firstOrNull { it.name == "getFileDescriptor" && it.parameterCount == 0 }
        val result = method?.let { runCatching { it.invoke(shm) }.getOrNull() }
        return when (result) {
            is ParcelFileDescriptor -> result
            is FileDescriptor -> ParcelFileDescriptor.dup(result)
            else -> null
        }
    }

    private fun resolveEncodeOptions(bmp: Bitmap, options: OcrOptions): Pair<CompressFormat, Int> {
        val extras = options.extras
        val formatName = extras?.getString(PaddleOcrOptionExtraKeys.IMAGE_FORMAT)?.trim()?.lowercase().orEmpty()
        val quality = extras?.getInt(PaddleOcrOptionExtraKeys.IMAGE_QUALITY, 100) ?: 100
        val resolvedQuality = quality.coerceIn(1, 100)
        val resolvedFormat = when (formatName) {
            "png" -> CompressFormat.PNG
            "jpg", "jpeg" -> CompressFormat.JPEG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CompressFormat.WEBP_LOSSY else CompressFormat.WEBP
            "webp_lossless" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CompressFormat.WEBP_LOSSLESS else CompressFormat.WEBP
            else -> if (bmp.hasAlpha()) CompressFormat.PNG else CompressFormat.JPEG
        }
        return resolvedFormat to resolvedQuality
    }

    private fun disableRawExtras(options: OcrOptions) {
        val extras = options.extras ?: return
        extras.remove(PaddleOcrOptionExtraKeys.RAW_IMAGE)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_WIDTH)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_HEIGHT)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_STRIDE)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_CONFIG)
    }

    private fun ensureRawSupport(target: Discovered, options: OcrOptions) {
        val extras = options.extras ?: return
        if (!extras.getBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, false)) return
        val supportsRaw = target.pluginInfo?.capabilities?.getBoolean(PaddleOcrPluginCapabilityKeys.SUPPORTS_RAW_IMAGE, false) == true
        if (!supportsRaw) {
            disableRawExtras(options)
        }
    }

    private fun queryOcrServices(context: Context, packageName: String? = null): List<ServiceInfo> {
        val pm = context.packageManager
        val intent = Intent(ACTION_OCR).apply {
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
        block: suspend (IOcrPlugin) -> T,
    ): T {
        val appCtx = context.applicationContext
        val entry = getOrCreateEntry(appCtx, serviceInfo)
        val proxy = awaitProxy(entry, bindTimeoutMs)
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
        block: suspend (IOcrPlugin) -> T,
    ): T {
        invalidateEntry(entry, error)
        val proxy = awaitProxy(entry, bindTimeoutMs)
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

    private suspend fun awaitProxy(entry: PooledConnection, bindTimeoutMs: Long): IOcrPlugin =
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
                    se
                )
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
                        se
                    )
                )
                return
            }
        }
        if (!ok) {
            val msg = buildBindFailureMessage(entry.appCtx, entry.serviceInfo, entry.cn)
            Log.e(TAG, msg)
            failWaiters(entry, IllegalStateException(msg))
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
        val shouldUnbind = synchronized(entry.lock) {
            entry.clearConnectionLocked()
            entry.connecting = false
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = null
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = null
            true
        }
        if (shouldUnbind) {
            poolHandler.post {
                runCatching { entry.appCtx.unbindService(entry.connection) }
                Log.w(TAG, "invalidateEntry: ${entry.cn} | ${error.message}")
            }
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
        val waiters = ArrayList<CancellableContinuation<IOcrPlugin>>()
        var proxy: IOcrPlugin? = null
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
                val proxy = IOcrPlugin.Stub.asInterface(binder)
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
