package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.PluginBindRefusedException
import org.autojs.autojs.core.plugin.ServiceBindingLease
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginInfo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PluginInfoServiceHost {

    private const val DEFAULT_BIND_TIMEOUT_MS = 10_000L
    private val SERVICE_QUERY_RETRY_DELAYS_MS = longArrayOf(150L, 300L, 600L, 1000L, 1500L)
    private val handler = Handler(Looper.getMainLooper())

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val pluginInfo: PluginInfo?,
        val error: Throwable?,
    )

    suspend fun discover(context: Context, packageName: String? = null): List<Discovered> {
        return queryInfoServices(context, packageName).map { serviceInfo ->
            val result = runCatching {
                withInfoService(context, serviceInfo) { it.info }
            }
            Discovered(
                serviceInfo = serviceInfo,
                pluginInfo = result.getOrNull(),
                error = result.exceptionOrNull(),
            )
        }
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo {
        if (!PluginTrustManager.isAuthorized(context, packageName)) {
            error("Plugin not authorized: $packageName")
        }
        val serviceInfo = queryFirstInfoServiceWithRetry(context, packageName)
            ?: error("No plugin info service found for package: $packageName")
        return withInfoService(context, serviceInfo) { it.info }
    }

    private suspend fun queryFirstInfoServiceWithRetry(context: Context, packageName: String): ServiceInfo? {
        queryInfoServices(context, packageName).firstOrNull()?.let { return it }
        SERVICE_QUERY_RETRY_DELAYS_MS.forEach { delayMs ->
            delay(delayMs)
            queryInfoServices(context, packageName).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun queryInfoServices(context: Context, packageName: String?): List<ServiceInfo> {
        val pm = context.packageManager
        val intent = Intent(PluginActions.INFO).apply {
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

    private suspend fun <T> withInfoService(
        context: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long = DEFAULT_BIND_TIMEOUT_MS,
        block: (IPluginInfoProvider) -> T,
    ): T {
        val binding = PluginAutoWake.retryRefusedBind(context.applicationContext, serviceInfo.packageName) {
            awaitInfoServiceBinding(
                appContext = context.applicationContext,
                serviceInfo = serviceInfo,
                bindTimeoutMs = bindTimeoutMs,
            )
        }
        return try {
            // ServiceConnection callbacks use the application's main thread by default.
            // Never invoke a remote plugin from that callback: even a short Binder wait
            // freezes ViewPager and swipe-refresh animations.
            // zh-CN: ServiceConnection 回调默认运行在应用主线程. 禁止在回调内调用
            // 远程插件; 即使短暂的 Binder 等待也会冻结 ViewPager 与下拉刷新动画.
            withContext(Dispatchers.IO) {
                block(binding.proxy)
            }
        } finally {
            binding.lease.close()
        }
    }

    private suspend fun awaitInfoServiceBinding(
        appContext: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long,
    ): InfoServiceBinding = suspendCancellableCoroutine { continuation ->
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val intent = Intent().setComponent(component)
        val completionClaimed = AtomicBoolean(false)
        val timeoutRef = AtomicReference<Runnable?>()

        lateinit var connection: ServiceConnection
        val lease = ServiceBindingLease {
            timeoutRef.getAndSet(null)?.let(handler::removeCallbacks)
            runCatching { appContext.unbindService(connection) }
        }

        fun fail(error: Throwable) {
            if (completionClaimed.compareAndSet(false, true)) {
                timeoutRef.getAndSet(null)?.let(handler::removeCallbacks)
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(error)
                }
            }
            lease.close()
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (lease.isClosed || !service.isBinderAlive) {
                    fail(IllegalStateException("plugin info service disconnected before use: $name"))
                    return
                }
                val proxy = IPluginInfoProvider.Stub.asInterface(service)
                if (completionClaimed.compareAndSet(false, true)) {
                    timeoutRef.getAndSet(null)?.let(handler::removeCallbacks)
                    if (!continuation.isCompleted) {
                        continuation.resume(InfoServiceBinding(proxy, lease))
                    } else {
                        lease.close()
                    }
                } else {
                    lease.close()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                fail(IllegalStateException("plugin info service disconnected before use: $name"))
            }

            override fun onBindingDied(name: ComponentName) {
                fail(IllegalStateException("plugin info service binding died before use: $name"))
            }

            override fun onNullBinding(name: ComponentName) {
                fail(IllegalStateException("plugin info service returned null binding: $name"))
            }
        }

        continuation.invokeOnCancellation {
            completionClaimed.set(true)
            lease.close()
        }

        val timeout = Runnable {
            fail(IllegalStateException("bindService timed out: $component"))
        }
        timeoutRef.set(timeout)
        handler.postDelayed(timeout, bindTimeoutMs)

        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (error: RuntimeException) {
            fail(IllegalStateException("bindService failed with exception: $component", error))
            return@suspendCancellableCoroutine
        }
        if (!bound) {
            fail(
                PluginBindRefusedException(serviceInfo.packageName, "bindService failed: $component at ${SystemClock.uptimeMillis()}")
            )
            return@suspendCancellableCoroutine
        }
        lease.markRegistered()
    }

    private data class InfoServiceBinding(
        val proxy: IPluginInfoProvider,
        val lease: ServiceBindingLease,
    )
}
