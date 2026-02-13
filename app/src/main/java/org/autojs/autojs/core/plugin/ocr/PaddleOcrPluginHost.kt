package org.autojs.autojs.core.plugin.ocr

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
import android.os.ParcelFileDescriptor
import android.os.SystemClock.uptimeMillis
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.paddle.ocr.api.IOcrPlugin
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.api.PluginInfo
import java.io.File
import java.io.FileOutputStream

/**
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 */
object PaddleOcrPluginHost {

    private const val TAG = "PaddleOcrPluginHost"

    const val ACTION_OCR = "org.autojs.plugin.PADDLE_OCR"

    private const val DEFAULT_BIND_TIMEOUT_MS = 60_000L
    private const val DEFAULT_CALL_TIMEOUT_MS = 60_000L

    private val externalServiceFlag = runCatching { ServiceInfo::class.java.getField("FLAG_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0
    private val bindExternalServiceFlag = runCatching { Context::class.java.getField("BIND_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0

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
        val start = uptimeMillis()
        return createTempPfd(context, bitmap).use { pfd ->
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
        val start = uptimeMillis()
        return createTempPfd(context, bitmap).use { pfd ->
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
    private fun createTempPfd(context: Context, bmp: Bitmap): ParcelFileDescriptor {
        val dir = File(context.cacheDir, "ocr_ipc").apply { if (!exists()) mkdirs() }
        val f = File.createTempFile("img_", ".bin", dir)
        FileOutputStream(f).use { fos ->
            val format = when {
                bmp.hasAlpha() -> CompressFormat.PNG
                else -> CompressFormat.JPEG
            }
            require(bmp.compress(format, 100, fos)) { "Failed to encode bitmap" }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
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
    ): T = suspendCancellableCoroutine { cont ->
        val cn = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val intent = Intent().setComponent(cn)
        val appCtx = context.applicationContext

        var resolved = false
        var jobRef: Job? = null

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "onServiceConnected: $name")
                val proxy = IOcrPlugin.Stub.asInterface(binder)
                val self = this

                jobRef = CoroutineScope(cont.context + Dispatchers.IO).launch {
                    val result = runCatching { block(proxy) }
                    try {
                        resolved = true
                        if (!cont.isCompleted) cont.resumeWith(result)
                        Log.i(TAG, "resume continuation: success=${result.isSuccess}")
                    } finally {
                        withContext(Dispatchers.Main) {
                            runCatching { appCtx.unbindService(self) }
                            Log.i(TAG, "unbindService: $name")
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "onServiceDisconnected: $name")
            }
        }

        val bindFlags = buildBindFlags(serviceInfo)
        var ok = try {
            Log.i(TAG, "bindService: $cn")
            appCtx.bindService(intent, conn, bindFlags)
        } catch (se: SecurityException) {
            Log.e(TAG, "bindService SecurityException: $cn | ${se.message}")
            cont.resumeWith(
                Result.failure(
                    IllegalStateException(
                        "bindService SecurityException: $cn. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.", se
                    )
                )
            )
            return@suspendCancellableCoroutine
        }
        if (!ok && bindFlags != Context.BIND_AUTO_CREATE) {
            ok = try {
                appCtx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            } catch (se: SecurityException) {
                Log.e(TAG, "bindService SecurityException: $cn | ${se.message}")
                cont.resumeWith(
                    Result.failure(
                        IllegalStateException(
                            "bindService SecurityException: $cn. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.", se
                        )
                    )
                )
                return@suspendCancellableCoroutine
            }
        }
        if (!ok) {
            val msg = buildBindFailureMessage(appCtx, serviceInfo, cn)
            Log.e(TAG, msg)
            cont.resumeWith(Result.failure(IllegalStateException(msg)))
            return@suspendCancellableCoroutine
        }

        val cancel = Runnable {
            if (!resolved && !cont.isCompleted) {
                Log.e(TAG, "bindService timeout: $cn")
                cont.resumeWith(Result.failure(TimeoutException("bindService timeout: $cn")))
                runCatching { appCtx.unbindService(conn) }
            }
        }
        val h = Handler(appCtx.mainLooper)
        h.postDelayed(cancel, bindTimeoutMs)

        cont.invokeOnCancellation {
            Log.w(TAG, "continuation cancelled: $cn")
            h.removeCallbacks(cancel)
            jobRef?.cancel()
            runCatching { appCtx.unbindService(conn) }
        }
    }

    private class TimeoutException(msg: String) : RuntimeException(msg)

}
