package org.autojs.autojs.core.plugin.text

import android.content.Context
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.opencc.api.IOpenccPlugin
import org.autojs.plugin.opencc.api.OpenccPluginActions
import org.autojs.plugin.opencc.api.OpenccPluginIds

object OpenccPluginHost {

    const val ACTION_OPENCC = OpenccPluginActions.OPENCC

    private val host = AidlPluginHost(
        action = ACTION_OPENCC,
        tag = "OpenccPluginHost",
        moduleLabel = "OpenCC plugin",
        proxyFactory = { binder -> IOpenccPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context, packageName: String? = null) = host.discover(context, packageName)

    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    suspend fun convert(context: Context, text: String, conversionType: String): String {
        val target = host.selectOrThrow(context, engine = OpenccPluginIds.ENGINE)
        return host.call(context, target) { proxy -> proxy.convert(text, conversionType) }
    }
}
