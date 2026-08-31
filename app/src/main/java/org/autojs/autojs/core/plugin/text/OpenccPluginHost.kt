package org.autojs.autojs.core.plugin.text

import android.content.Context
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.opencc.api.IOpenccPlugin
import org.autojs.plugin.opencc.api.OpenccConversionTypes
import org.autojs.plugin.opencc.api.OpenccPluginCapabilityKeys
import org.autojs.plugin.opencc.api.OpenccPluginContract
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

    suspend fun getSupportedConversionTypes(context: Context): List<String> {
        val target = host.selectOrThrow(context, engine = OpenccPluginIds.ENGINE)
        if (!supportsExtendedConversions(target.pluginInfo)) {
            return OpenccConversionTypes.ALL
        }
        return host.call(context, target) { proxy ->
            OpenccPluginCallRouter.normalizeSupportedConversionTypes(
                reported = proxy.supportedConversionTypes,
                fallback = OpenccConversionTypes.ALL,
            )
        }
    }

    suspend fun convertBatch(
        context: Context,
        texts: List<String>,
        conversionType: String,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()
        val target = host.selectOrThrow(context, engine = OpenccPluginIds.ENGINE)
        val supportsExtendedConversions = supportsExtendedConversions(target.pluginInfo)
        return host.call(context, target) { proxy ->
            OpenccPluginCallRouter.convertBatch(
                supportsExtendedConversions = supportsExtendedConversions,
                texts = texts,
                conversionType = conversionType,
                extendedCall = { batch, type -> proxy.convertBatch(batch.toMutableList(), type) },
                legacyCall = proxy::convert,
            )
        }
    }

    suspend fun convertChain(
        context: Context,
        text: String,
        conversionTypes: List<String>,
    ): String {
        if (conversionTypes.isEmpty()) return text
        val target = host.selectOrThrow(context, engine = OpenccPluginIds.ENGINE)
        val supportsExtendedConversions = supportsExtendedConversions(target.pluginInfo)
        return host.call(context, target) { proxy ->
            OpenccPluginCallRouter.convertChain(
                supportsExtendedConversions = supportsExtendedConversions,
                text = text,
                conversionTypes = conversionTypes,
                extendedCall = { source, types -> proxy.convertChain(source, types.toMutableList()) },
                legacyCall = proxy::convert,
            )
        }
    }

    private fun supportsExtendedConversions(pluginInfo: PluginInfo?): Boolean {
        val contractVersion = pluginInfo?.capabilities?.getInt(
            OpenccPluginCapabilityKeys.CONTRACT_VERSION,
            OpenccPluginContract.VERSION_LEGACY,
        ) ?: OpenccPluginContract.VERSION_LEGACY
        return OpenccPluginCallRouter.supportsExtendedConversions(contractVersion)
    }
}
