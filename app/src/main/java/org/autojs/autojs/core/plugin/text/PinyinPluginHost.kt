package org.autojs.autojs.core.plugin.text

import android.content.Context
import android.os.Bundle
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.pinyin.api.IPinyinPlugin
import org.autojs.plugin.pinyin.api.PinyinPluginActions
import org.autojs.plugin.pinyin.api.PinyinPluginIds

object PinyinPluginHost {

    const val ACTION_PINYIN = PinyinPluginActions.PINYIN

    private val host = AidlPluginHost(
        action = ACTION_PINYIN,
        tag = "PinyinPluginHost",
        moduleLabel = "Pinyin plugin",
        proxyFactory = { binder -> IPinyinPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    suspend fun discover(context: Context, packageName: String? = null) = host.discover(context, packageName)

    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    suspend fun convert(context: Context, text: String, options: Bundle): String {
        val target = host.selectOrThrow(context, engine = PinyinPluginIds.ENGINE)
        return host.call(context, target) { proxy -> proxy.convert(text, options) }
    }

    suspend fun simple(context: Context, text: String, enableNumericTone: Boolean, enableSegment: Boolean): String {
        val target = host.selectOrThrow(context, engine = PinyinPluginIds.ENGINE)
        return host.call(context, target) { proxy -> proxy.simple(text, enableNumericTone, enableSegment) }
    }

    suspend fun fromCodePoint(context: Context, codePoint: Int): String? {
        val target = host.selectOrThrow(context, engine = PinyinPluginIds.ENGINE)
        return host.call(context, target) { proxy -> proxy.fromCodePoint(codePoint) }.takeIf { it.isNotEmpty() }
    }

    suspend fun fromPhrase(context: Context, phrase: String): String {
        val target = host.selectOrThrow(context, engine = PinyinPluginIds.ENGINE)
        return host.call(context, target) { proxy -> proxy.fromPhrase(phrase) }
    }
}
