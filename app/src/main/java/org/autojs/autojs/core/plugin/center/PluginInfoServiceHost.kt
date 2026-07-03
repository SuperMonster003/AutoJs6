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
import android.os.RemoteException
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginInfo
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PluginInfoServiceHost {

    private const val DEFAULT_BIND_TIMEOUT_MS = 10_000L
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
        val serviceInfo = queryInfoServices(context, packageName).firstOrNull()
            ?: error("No plugin info service found for package: $packageName")
        return withInfoService(context, serviceInfo) { it.info }
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
    ): T = suspendCancellableCoroutine { cont ->
        val appContext = context.applicationContext
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val intent = Intent().setComponent(component)
        var resumed = false

        lateinit var connection: ServiceConnection
        val timeout = Runnable {
            if (!resumed) {
                resumed = true
                runCatching { appContext.unbindService(connection) }
                cont.resumeWithException(IllegalStateException("bindService timed out: $component"))
            }
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (resumed) return
                handler.removeCallbacks(timeout)
                val proxy = IPluginInfoProvider.Stub.asInterface(service)
                try {
                    val value = block(proxy)
                    resumed = true
                    cont.resume(value)
                } catch (e: RemoteException) {
                    resumed = true
                    cont.resumeWithException(e)
                } catch (e: RuntimeException) {
                    resumed = true
                    cont.resumeWithException(e)
                } finally {
                    runCatching { appContext.unbindService(this) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (!resumed) {
                    handler.removeCallbacks(timeout)
                    resumed = true
                    cont.resumeWithException(IllegalStateException("service disconnected before getInfo: $name"))
                }
            }

            override fun onBindingDied(name: ComponentName) {
                if (!resumed) {
                    handler.removeCallbacks(timeout)
                    resumed = true
                    cont.resumeWithException(IllegalStateException("service binding died before getInfo: $name"))
                }
            }

            override fun onNullBinding(name: ComponentName) {
                if (!resumed) {
                    handler.removeCallbacks(timeout)
                    resumed = true
                    cont.resumeWithException(IllegalStateException("service returned null binding: $name"))
                }
            }
        }

        cont.invokeOnCancellation {
            handler.removeCallbacks(timeout)
            runCatching { appContext.unbindService(connection) }
        }

        handler.postDelayed(timeout, bindTimeoutMs)
        val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound && !resumed) {
            handler.removeCallbacks(timeout)
            resumed = true
            cont.resumeWithException(
                IllegalStateException("bindService failed: $component at ${SystemClock.uptimeMillis()}")
            )
        }
    }
}
