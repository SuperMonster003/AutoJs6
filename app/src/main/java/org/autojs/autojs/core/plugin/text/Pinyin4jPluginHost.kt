package org.autojs.autojs.core.plugin.text

import android.content.Context
import android.os.Bundle
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.pinyin4j.api.IPinyin4jPlugin
import org.autojs.plugin.pinyin4j.api.Pinyin4jPluginActions
import org.autojs.plugin.pinyin4j.api.Pinyin4jPluginIds

object Pinyin4jPluginHost {

    const val ACTION_PINYIN4J = Pinyin4jPluginActions.PINYIN4J

    private val host = AidlPluginHost(
        action = ACTION_PINYIN4J,
        tag = "Pinyin4jPluginHost",
        moduleLabel = "Pinyin4j plugin",
        proxyFactory = { binder -> IPinyin4jPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context) = host.discover(context)

    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    suspend fun of(context: Context, text: String, options: Bundle): String {
        val target = host.selectOrThrow(context, engine = Pinyin4jPluginIds.ENGINE)
        return host.call(context, target) { proxy -> proxy.of(text, options) }
    }

    suspend fun asTone(context: Context, text: String): String {
        val target = host.selectOrThrow(context, engine = Pinyin4jPluginIds.ENGINE)
        return host.call(context, target) { proxy -> proxy.asTone(text) }
    }
}
