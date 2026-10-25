package org.autojs.autojs.engine

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.runtime.api.modules.opencc.OpenccConversionPlan
import org.autojs.plugin.opencc.api.OpenccConversionTypes
import org.json.JSONArray
import java.util.Locale

interface NodeOpenccBackend {
    fun convert(text: String, conversionType: String): String
}

internal class AndroidNodeOpenccBackend(context: Context) : NodeOpenccBackend {
    private val appContext = context.applicationContext

    override fun convert(text: String, conversionType: String): String = runBlocking {
        val nativeType = conversionType.uppercase(Locale.ROOT)
        if (nativeType in OpenccConversionTypes.ALL) {
            OpenccPluginHost.convert(appContext, text, nativeType)
        } else {
            val chain = requireNotNull(OpenccConversionPlan.chainFor(conversionType)) { "Unknown OpenCC conversion: $conversionType" }
            OpenccPluginHost.convertChain(appContext, text, chain)
        }
    }
}

/** The same conversion names and composed conversion chains as the Rhino OpenCC module. */
internal object NodeOpenccBridge {
    val METHODS: Set<String> = setOf("convert") + OpenccConversionPlan.supportedNames

    fun dispatch(request: NodeBridgeRequest, backend: NodeOpenccBackend): NodeBridgeResponse = try {
        require(request.method in METHODS) { "Unknown OpenCC method: ${request.method}" }
        val args = JSONArray(request.argsJson)
        require(args.length() == if (request.method == "convert") 2 else 1) { "Invalid argument count for opencc.${request.method}" }
        val text = args.opt(0) as? String ?: throw IllegalArgumentException("OpenCC text must be a string")
        val type = (if (request.method == "convert") args.opt(1) as? String else request.method)
            ?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("OpenCC conversion type must be a string")
        require(type in OpenccConversionPlan.supportedNames) { "Unknown OpenCC conversion type: $type" }
        NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(backend.convert(text, type)))
    } catch (error: IllegalArgumentException) {
        NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest(error.message ?: "Invalid OpenCC argument", request.module, request.method))
    } catch (error: Throwable) {
        NodeBridgeResponse.failure(request.id, NodeBridgeError.providerFailed(error.message ?: "OpenCC provider failed", request.module, request.method))
    }
}
