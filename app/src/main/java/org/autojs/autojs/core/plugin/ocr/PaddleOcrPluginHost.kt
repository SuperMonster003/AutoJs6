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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.paddle.ocr.api.IOcrPlugin
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.api.PluginInfo
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 */
object PaddleOcrPluginHost {

    private const val TAG = "PaddleOcrPluginHost"

    const val ACTION_OCR = "org.autojs.plugin.PADDLE_OCR"

    private const val DEFAULT_BIND_TIMEOUT_MS = 60_000L
    private const val DEFAULT_CALL_TIMEOUT_MS = 60_000L
    private const val EXTRA_IMAGE_FORMAT = "imageFormat"
    private const val EXTRA_IMAGE_QUALITY = "imageQuality"
    private const val EXTRA_RAW_IMAGE = "rawImage"
    private const val EXTRA_RAW_WIDTH = "rawWidth"
    private const val EXTRA_RAW_HEIGHT = "rawHeight"
    private const val EXTRA_RAW_STRIDE = "rawStride"
    private const val EXTRA_RAW_CONFIG = "rawConfig"
    private const val IDLE_UNBIND_MS = 30_000L

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
        ensureRawSupport(target, options)
        val start = uptimeMillis()
        return createTempPfd(bitmap, options).use { pfd ->
            withService(context, target.serviceInfo, DEFAULT_BIND_TIMEOUT_MS) { proxy ->
                val remain = callTimeoutMs - (uptimeMillis() - start)
                if (remain <= 0) error("AIDL call timeout in ${callTimeoutMs / 1000} seconds")
                val lines = proxy.recognizeText(pfd, options)
                Log.i(TAG, "recognizeTextAidl: got ${lines.size} lines")
                lines
            }
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
        ensureRawSupport(target, options)
        val start = uptimeMillis()
        return createTempPfd(bitmap, options).use { pfd ->
            withService(context, target.serviceInfo, DEFAULT_BIND_TIMEOUT_MS) { proxy ->
                val remain = callTimeoutMs - (uptimeMillis() - start)
                if (remain <= 0) error("AIDL call timeout")
                val results = proxy.detect(pfd, options)
                Log.i(TAG, "detectAidl: got ${results.size} items")
                results
            }
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
        val list = discover(context)
            .filter { it.pluginInfo != null }
            .filter { PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
        if (list.isEmpty()) return null
        if (engineId != null) {
            list.firstOrNull { d -> d.pluginInfo?.id == engineId }?.let { return it }
        }
        if (engine != null && variant != null) {
            list.firstOrNull { d -> d.pluginInfo?.engine == engine && d.pluginInfo.variant == variant }?.let { return it }
        }
        if (engine != null) {
            list.firstOrNull { d -> d.pluginInfo?.engine == engine }?.let { return it }
        }
        return list.maxBy {
            val variant = it.pluginInfo?.variant ?: return@maxBy 0
            variant.replace(Regex("\\D"), "").toIntOrNull() ?: 0
        }
    }

    // Convert temporary file to read-only FD.
    // zh-CN: 临时文件转换为只读 FD.
    private fun createTempPfd(bmp: Bitmap, options: OcrOptions): ParcelFileDescriptor {
        val useRaw = options.extras?.getBoolean(EXTRA_RAW_IMAGE, false) == true
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
        extras.putBoolean(EXTRA_RAW_IMAGE, true)
        extras.putInt(EXTRA_RAW_WIDTH, src.width)
        extras.putInt(EXTRA_RAW_HEIGHT, src.height)
        extras.putInt(EXTRA_RAW_STRIDE, rowBytes)
        extras.putString(EXTRA_RAW_CONFIG, Bitmap.Config.ARGB_8888.name)

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
        val formatName = extras?.getString(EXTRA_IMAGE_FORMAT)?.trim()?.lowercase().orEmpty()
        val quality = extras?.getInt(EXTRA_IMAGE_QUALITY, 100) ?: 100
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
        extras.remove(EXTRA_RAW_IMAGE)
        extras.remove(EXTRA_RAW_WIDTH)
        extras.remove(EXTRA_RAW_HEIGHT)
        extras.remove(EXTRA_RAW_STRIDE)
        extras.remove(EXTRA_RAW_CONFIG)
    }

    private fun ensureRawSupport(target: Discovered, options: OcrOptions) {
        val extras = options.extras ?: return
        if (!extras.getBoolean(EXTRA_RAW_IMAGE, false)) return
        val supportsRaw = target.pluginInfo?.capabilities?.getBoolean("supportsRawImage", false) == true
        if (!supportsRaw) {
            disableRawExtras(options)
        }
    }

    private fun queryOcrServices(context: Context, packageName: String? = null): List<ServiceInfo> {
        val pm = context.packageManager
        val intent = Intent(ACTION_OCR).apply {
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
        block: suspend (IOcrPlugin) -> T,
    ): T {
        val appCtx = context.applicationContext
        val entry = getOrCreateEntry(appCtx, serviceInfo)
        val proxy = awaitProxy(entry, bindTimeoutMs)
        return try {
            block(proxy)
        } catch (e: RemoteException) {
            retryAfterBinderFailure(entry, bindTimeoutMs, e, block)
        } finally {
            touch(entry)
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
        return block(proxy)
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
                if (existing != null && entry.bound) {
                    cont.resume(existing)
                    return@suspendCancellableCoroutine
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
            entry.proxy = null
            entry.bound = false
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
                    entry.proxy = null
                    entry.bound = false
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
        var bound: Boolean = false
        var connecting: Boolean = false
        var lastUsed: Long = 0L
        var idleRunnable: Runnable? = null
        var bindTimeoutRunnable: Runnable? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "onServiceConnected: $name")
                val proxy = IOcrPlugin.Stub.asInterface(binder)
                val pending = synchronized(lock) {
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
                handleDisconnect(name, "onServiceDisconnected")
            }

            override fun onBindingDied(name: ComponentName) {
                Log.w(TAG, "onBindingDied: $name")
                handleDisconnect(name, "onBindingDied")
            }

            override fun onNullBinding(name: ComponentName) {
                Log.w(TAG, "onNullBinding: $name")
                handleDisconnect(name, "onNullBinding")
            }
        }

        private fun handleDisconnect(name: ComponentName, reason: String) {
            val pending = synchronized(lock) {
                proxy = null
                bound = false
                connecting = false
                bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                bindTimeoutRunnable = null
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
        }
    }

    private class TimeoutException(msg: String) : RuntimeException(msg)

}
