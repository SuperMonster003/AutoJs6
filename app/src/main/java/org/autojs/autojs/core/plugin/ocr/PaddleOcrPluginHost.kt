package org.autojs.autojs.core.plugin.ocr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import org.autojs.plugin.paddle.ocr.IOcrPlugin
import org.autojs.plugin.paddle.ocr.OcrOptions
import org.autojs.plugin.paddle.ocr.OcrResult
import org.autojs.plugin.paddle.ocr.PluginInfo
import java.io.File
import java.io.FileOutputStream

object PaddleOcrPluginHost {

    private const val TAG = "PaddleOcrPluginHost"

    const val ACTION_OCR = "org.autojs.plugin.PADDLE_OCR"

    private const val DEFAULT_BIND_TIMEOUT_MS = 60_000L
    private const val DEFAULT_CALL_TIMEOUT_MS = 60_000L

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val pluginInfo: PluginInfo?,
    )

    suspend fun discover(context: Context): List<Discovered> {
        val pm = context.packageManager
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(Intent(ACTION_OCR), PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentServices(Intent(ACTION_OCR), 0)
        }
        val services = resolveList.mapNotNull { it.serviceInfo }
        Log.i(TAG, "discover: services=${services.size}")
        return services.map { svc ->
            Log.i(TAG, "discover: ${svc.packageName}/${svc.name}")
            val info = runCatching { withService(context, svc, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() } }
                .onFailure { e -> Log.w(TAG, "getInfo failed: ${e.message}") }
                .getOrNull()
            Discovered(svc, info)
        }
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
        // e.g. "paddle-ocr-v5"
        engineId: String? = null,
        // e.g. "paddle-ocr"
        engine: String? = null,
        // e.g. "v5"
        variant: String? = null,
    ): Discovered? {
        val list = discover(context).filter { it.pluginInfo != null }
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
        return list.first()
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

        val ok = try {
            Log.i(TAG, "bindService: $cn")
            appCtx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        } catch (se: SecurityException) {
            Log.e(TAG, "bindService SecurityException: $cn | ${se.message}")
            cont.resumeWith(
                Result.failure(
                    IllegalStateException(
                        "bindService SecurityException: $cn. Please make sure the plugin declares <permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.", se
                    )
                )
            )
            return@suspendCancellableCoroutine
        }
        if (!ok) {
            Log.e(TAG, "bindService failed: $cn")
            cont.resumeWith(Result.failure(IllegalStateException("bindService failed: $cn")))
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
