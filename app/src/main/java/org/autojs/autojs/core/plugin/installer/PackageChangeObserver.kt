package org.autojs.autojs.core.plugin.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Observes `PACKAGE_ADDED` / `PACKAGE_REPLACED` for one package (plugin roadmap D22). It replaces
 * the status coordinator of the former host installer for callers that hand a package to the
 * system installer and still need to know when the package landed, such as the plugin install
 * wizard. It never blocks the main thread and unregisters itself on every exit path.
 */
object PackageChangeObserver {

    /**
     * Suspends until [packageName] is added or replaced, at most [timeoutMillis]; returns `false`
     * on timeout. A package that is already installed before the call does not count, so the
     * caller decides whether an in-place update is what it waits for.
     */
    suspend fun awaitInstalled(context: Context, packageName: String, timeoutMillis: Long): Boolean {
        val appContext = context.applicationContext
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val settled = AtomicBoolean(false)
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, intent: Intent) {
                        if (intent.action != Intent.ACTION_PACKAGE_ADDED && intent.action != Intent.ACTION_PACKAGE_REPLACED) return
                        if (intent.data?.schemeSpecificPart != packageName) return
                        if (!settled.compareAndSet(false, true)) return
                        runCatching { appContext.unregisterReceiver(this) }
                        if (continuation.isActive) continuation.resume(true)
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                continuation.invokeOnCancellation {
                    if (settled.compareAndSet(false, true)) runCatching { appContext.unregisterReceiver(receiver) }
                }
            }
        } ?: false
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)
}
