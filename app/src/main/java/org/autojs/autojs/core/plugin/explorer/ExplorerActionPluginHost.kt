package org.autojs.autojs.core.plugin.explorer

import android.content.Context
import android.os.Bundle
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.IExplorerActionPlugin

object ExplorerActionPluginHost {

    const val ACTION_EXPLORER_ACTION = ExplorerActionPluginActions.EXPLORER_ACTION

    private val host = AidlPluginHost(
        action = ACTION_EXPLORER_ACTION,
        tag = "ExplorerActionPluginHost",
        moduleLabel = "Explorer Action plugin",
        proxyFactory = { binder -> IExplorerActionPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context) = host.discover(context)

    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    suspend fun getActionCatalog(
        context: Context,
        target: AidlPluginHost.Discovered,
    ): Bundle = host.call(context, target) { proxy -> proxy.getActionCatalog() }
}
