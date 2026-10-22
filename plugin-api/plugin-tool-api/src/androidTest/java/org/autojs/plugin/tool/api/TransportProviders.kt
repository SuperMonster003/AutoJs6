package org.autojs.plugin.tool.api

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import org.autojs.plugin.common.api.PluginInfo
import org.json.JSONArray
import org.json.JSONObject

/** Results of an exact size in UTF-16 units for PluginToolResultTransportTest. */
internal object TransportResults {

    const val TOOL = "sized"

    /** Answer keys of RawTransportProviderService: "ok" or the class of the exception the oneway report threw. */
    const val TRANSPORT = "transport"
    const val PARCEL_BYTES = "parcelBytes"

    /** A JSON object of [units] UTF-16 units, all of them ASCII. */
    fun json(units: Int): String = JSONObject().put("t", "x".repeat(units - 8)).toString()
}

/** The base class in the process `:provider`: `sized` returns `units` UTF-16 units, `textUnits` of them in the text. */
class TransportProviderService : PluginToolProviderService() {

    override fun createPluginInfo() = PluginInfo().apply {
        name = "Transport test"
        author = "SuperMonster003"
        versionName = "1.0.0"
        versionCode = 1L
    }

    override fun createCatalogJson(): String = JSONObject().put("shortName", "transport").put("tools", JSONArray().put(
        JSONObject().put("name", TransportResults.TOOL).put("description", "Returns a result of the requested size.").put("risk", PluginToolValues.RISK_READONLY)
            .put("inputSchema", JSONObject().put("type", "object")),
    )).toString()

    override fun onCall(call: PluginToolCall): PluginToolResult {
        val units = call.arguments.getInt("units")
        val textUnits = call.arguments.optInt("textUnits", 0)
        return PluginToolResult(TransportResults.json(units - textUnits)).withText(if (textUnits > 0) "y".repeat(textUnits) else null)
    }
}

/**
 * A provider without the base class in the process `:provider`: `invoke` reports a result of `units` UTF-16 units at once and
 * answers whether the oneway transaction went through and how many bytes the result Bundle takes in a Parcel.
 */
class RawTransportProviderService : Service() {

    override fun onBind(intent: Intent?): IBinder = object : IPluginToolProvider.Stub() {

        override fun getInfo(): PluginInfo = PluginInfo()

        override fun getCatalog(request: Bundle?): Bundle = Bundle()

        override fun invoke(request: Bundle, inputs: Array<out ParcelFileDescriptor>?, callback: IPluginToolCallback): Bundle {
            val units = JSONObject(request.getString(PluginToolInvokeKeys.ARGUMENTS_JSON)!!).getInt("units")
            val result = Bundle().apply {
                putString(PluginToolResultKeys.INVOCATION_ID, request.getString(PluginToolInvokeKeys.INVOCATION_ID))
                putString(PluginToolResultKeys.RESULT_JSON, TransportResults.json(units))
                putString(PluginToolResultKeys.OUTPUTS_JSON, "[]")
            }
            val parcel = Parcel.obtain()
            val parcelBytes = try {
                parcel.writeBundle(result)
                parcel.dataSize()
            } finally {
                parcel.recycle()
            }
            val transport = try {
                callback.onResult(result, emptyArray())
                "ok"
            } catch (e: Exception) {
                e.javaClass.simpleName
            }
            return Bundle().apply {
                putBoolean(PluginToolAcceptKeys.ACCEPTED, true)
                putString(TransportResults.TRANSPORT, transport)
                putInt(TransportResults.PARCEL_BYTES, parcelBytes)
            }
        }

        override fun cancel(request: Bundle?) = Unit
    }
}
