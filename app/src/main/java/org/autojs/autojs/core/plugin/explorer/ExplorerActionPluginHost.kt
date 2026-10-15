package org.autojs.autojs.core.plugin.explorer

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.PluginDiscoveryRecovery
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRemoteFailure
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerArchiveOpenKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveOpenValues
import org.autojs.plugin.explorer.api.IExplorerActionPlugin
import org.autojs.plugin.explorer.api.IExplorerArchiveSession

object ExplorerActionPluginHost {

    const val ACTION_EXPLORER_ACTION = ExplorerActionPluginActions.EXPLORER_ACTION

    private val host = AidlPluginHost(
        action = ACTION_EXPLORER_ACTION,
        tag = "ExplorerActionPluginHost",
        moduleLabel = "Explorer Action plugin",
        proxyFactory = { binder -> IExplorerActionPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context, packageName: String? = null) = host.discover(context, packageName)

    private val recovery = PluginDiscoveryRecovery()

    /**
     * [discover] for the registry: plugins whose service fails to bind (ColorOS refuses plugins that never ran) are
     * woken and discovered again, as long as the Plugin Center enables and authorizes them.
     * zh-CN: 供注册表使用的 [discover]: 服务绑定失败的插件 (ColorOS 拒绝从未运行过的插件) 若在插件中心已启用且已授权, 会被唤醒并重新发现.
     */
    suspend fun discoverWakingFailures(context: Context) = host.discoverWakingFailures(context, recovery) { packageName ->
        PluginEnableStore.isEnabled(context, packageName, true) && PluginTrustManager.isAuthorized(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    suspend fun getActionCatalog(
        context: Context,
        target: AidlPluginHost.Discovered,
    ): Bundle = host.call(context, target) { proxy -> proxy.getActionCatalog() }

    suspend fun getHostFileInfo(
        context: Context,
        target: AidlPluginHost.Discovered,
        source: ParcelFileDescriptor,
        request: Bundle,
    ): Bundle = host.call(
        context = context,
        target = target,
        retryOnRemoteException = false,
    ) { proxy ->
        requireNotNull(proxy.getHostFileInfo(source, request)) {
            "Explorer Action plugin returned no host file information"
        }
    }

    suspend fun openArchive(
        context: Context,
        target: AidlPluginHost.Discovered,
        source: ParcelFileDescriptor,
        request: Bundle,
        protocolVersion: Int,
    ): AidlPluginHost.LeasedCall<IExplorerArchiveSession> =
        host.callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy ->
            if (protocolVersion < 11) {
                requireNotNull(proxy.openArchive(source, request)) {
                    "Explorer Action plugin returned no archive session"
                }
            } else {
                val result = requireNotNull(proxy.openArchiveV11(source, request)) {
                    "Explorer Action plugin returned no archive-open result"
                }
                val errorCode = result.getInt(
                    ExplorerArchiveOpenKeys.ERROR_CODE,
                    ExplorerArchiveOpenValues.ERROR_UNRECOVERABLE,
                )
                val sessionBinder = result.getBinder(ExplorerArchiveOpenKeys.SESSION_BINDER)
                if (errorCode == ExplorerArchiveOpenValues.ERROR_NONE && sessionBinder != null) {
                    requireNotNull(IExplorerArchiveSession.Stub.asInterface(sessionBinder)) {
                        "Explorer Action plugin returned an invalid archive session"
                    }
                } else {
                    check(sessionBinder == null) {
                        "Explorer Action plugin returned both a session and an archive-open error"
                    }
                    throw ExplorerArchiveOpenRemoteFailure(
                        errorCode = errorCode,
                        message = result.getString(ExplorerArchiveOpenKeys.ERROR_MESSAGE)
                            ?.takeIf(String::isNotBlank)
                            ?: "Explorer Action plugin could not open the archive",
                    )
                }
            }
        }
}
