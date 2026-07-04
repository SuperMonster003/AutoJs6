package org.autojs.autojs.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin
import org.autojs.plugin.nodejs.api.NodeJsPluginActions
import org.autojs.plugin.nodejs.api.NodeJsPluginIds
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NodeJsRuntimePluginHost {

    private const val DEFAULT_BIND_TIMEOUT_MS = 10_000L
    private val handler = Handler(Looper.getMainLooper())

    data class Candidate(
        val serviceInfo: ServiceInfo,
        val packageVersionCode: Long?,
    )

    suspend fun discover(context: Context): List<Candidate> {
        val appContext = context.applicationContext
        return queryRuntimeServices(appContext)
            .asSequence()
            .filter { PluginEnableStore.isEnabled(appContext, it.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(appContext, it.packageName) }
            .filter(::isHostVersionCompatible)
            .map { serviceInfo ->
                Candidate(
                    serviceInfo = serviceInfo,
                    packageVersionCode = packageVersionCode(appContext, serviceInfo.packageName),
                )
            }
            .sortedWith(compareByDescending<Candidate> { it.packageVersionCode ?: 0L })
            .toList()
    }

    suspend fun probe(context: Context): Bundle {
        val candidate = discover(context).firstOrNull()
            ?: throw NodeRuntimePluginUnavailableException("No enabled trusted Node.js runtime plugin service found.")
        return withRuntimeService(context, candidate.serviceInfo) { runtime ->
            runtime.runtimeInfo
        }
    }

    suspend fun runScript(
        context: Context,
        request: Bundle,
        callback: INodeJsRuntimeCallback? = null,
    ): Bundle {
        val candidate = discover(context).firstOrNull()
            ?: throw NodeRuntimePluginUnavailableException("No enabled trusted Node.js runtime plugin service found.")
        return withRuntimeService(context, candidate.serviceInfo) { runtime ->
            runtime.runScript(request, callback)
        }
    }

    suspend fun cancelScript(context: Context, executionId: String): Boolean {
        val candidate = discover(context).firstOrNull()
            ?: return false
        return withRuntimeService(context, candidate.serviceInfo) { runtime ->
            runtime.cancelScript(executionId)
        }
    }

    private fun queryRuntimeServices(context: Context): List<ServiceInfo> {
        val intent = Intent(NodeJsPluginActions.RUNTIME).apply {
            addCategory(NodeJsPluginIds.ENGINE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val flags = PackageManager.GET_META_DATA
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            context.packageManager.queryIntentServices(intent, flags)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun isHostVersionCompatible(serviceInfo: ServiceInfo): Boolean {
        @Suppress("DEPRECATION")
        val requiresHostVersion = serviceInfo.metaData
            ?.get(PluginCapabilityKeys.REQUIRES_HOST_VERSION)
            ?.let(PluginCapabilityResolver::coerceLong)
        return PluginCapabilityResolver.isHostVersionCompatible(
            BuildConfig.VERSION_CODE.toLong(),
            requiresHostVersion,
        )
    }

    private fun packageVersionCode(context: Context, packageName: String): Long? {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
        return packageInfo?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) }
    }

    private suspend fun <T> withRuntimeService(
        context: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long = DEFAULT_BIND_TIMEOUT_MS,
        block: (INodeJsRuntimePlugin) -> T,
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
                cont.resumeWithException(NodeRuntimePluginUnavailableException("bindService timed out: $component"))
            }
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (resumed) return
                handler.removeCallbacks(timeout)
                val proxy = INodeJsRuntimePlugin.Stub.asInterface(service)
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
                    cont.resumeWithException(NodeRuntimePluginUnavailableException("service disconnected: $name"))
                }
            }

            override fun onBindingDied(name: ComponentName) {
                if (!resumed) {
                    handler.removeCallbacks(timeout)
                    resumed = true
                    cont.resumeWithException(NodeRuntimePluginUnavailableException("service binding died: $name"))
                }
            }

            override fun onNullBinding(name: ComponentName) {
                if (!resumed) {
                    handler.removeCallbacks(timeout)
                    resumed = true
                    cont.resumeWithException(NodeRuntimePluginUnavailableException("service returned null binding: $name"))
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
            cont.resumeWithException(NodeRuntimePluginUnavailableException("bindService failed: $component"))
        }
    }
}

class NodeRuntimePluginUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
