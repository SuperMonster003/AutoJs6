package org.autojs.autojs.engine

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.widget.Toast
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.autojs.plugin.bun.runtime.api.IBunHostCapabilityBroker
import org.autojs.plugin.bun.runtime.api.IBunHostCapabilityCallback
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host side of the Bun capability bridge (plugin ROADMAP M7, dynamic calls): one Binder object per execution,
 * pinned to the plugin UID and the execution ID and handed over in the `runScript` request together with the
 * granted capability IDs. Every accepted call is answered exactly once through the oneway callback; calls from
 * another UID, for another execution or after [close] are ignored rather than failed. Capability groups are
 * explicitly granted and dispatched here; this is not a generic route into the Node host API registry.
 * Payloads are JSON strings on both sides, so the plugin never interprets them.
 */
internal class BunHostCapabilityBroker(
    context: Context,
    private val expectedCallerUid: Int,
    private val executionId: String,
    capabilities: List<String>,
    consoleProvider: () -> ConsoleImpl,
    workingDirectory: String? = null,
) : IBunHostCapabilityBroker.Stub() {
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val calls = AtomicInteger(0)
    private val toasts = AtomicInteger(0)
    private val coordinates = RuntimeCoordinateBridge()
    private val console = RuntimeConsoleBridge(consoleProvider, workingDirectory)
    private val pinyin by lazy { AndroidNodePinyinBackend(appContext) }
    private val pinyin4j by lazy { AndroidNodePinyin4jBackend(appContext) }

    /** Granted capability IDs in contract order; a broker is only attached when this is non-empty. */
    val capabilities: List<String> = capabilities.toList()

    fun attachTo(request: Bundle) {
        request.putInt(BunRuntimeContract.KEY_HOST_CAPABILITY_BRIDGE_VERSION, BunRuntimeContract.HOST_CAPABILITY_BRIDGE_VERSION)
        request.putBinder(BunRuntimeContract.KEY_HOST_CAPABILITY_BROKER, this)
        request.putStringArrayList(BunRuntimeContract.KEY_HOST_CAPABILITIES, ArrayList(capabilities))
    }

    /** After this no call is answered; the plugin fails its pending calls itself when the run ends. */
    fun close() {
        closed.set(true)
    }

    override fun invoke(request: Bundle?, callback: IBunHostCapabilityCallback?) {
        if (closed.get() || Binder.getCallingUid() != expectedCallerUid) return
        if (request == null || callback == null) return
        if (request.getInt(BunRuntimeContract.KEY_HOST_CAPABILITY_BRIDGE_VERSION, -1) != BunRuntimeContract.HOST_CAPABILITY_BRIDGE_VERSION) return
        if (request.getString(BunRuntimeContract.KEY_EXECUTION_ID) != executionId) return
        val callId = request.getString(BunRuntimeContract.KEY_HOST_CALL_ID)
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_CALL_ID_CHARS }
            ?: return
        val outcome = if (calls.incrementAndGet() > BunRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION) {
            Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_TOO_MANY_REQUESTS, "Call limit per execution reached")
        } else {
            try {
                val identity = Binder.clearCallingIdentity()
                try {
                    dispatch(
                        request.getString(BunRuntimeContract.KEY_HOST_CAPABILITY).orEmpty(),
                        request.getString(BunRuntimeContract.KEY_HOST_CALL_REQUEST_JSON).orEmpty(),
                    )
                } finally {
                    Binder.restoreCallingIdentity(identity)
                }
            } catch (error: IllegalArgumentException) {
                Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, error.message ?: "Invalid capability arguments")
            } catch (error: SecurityException) {
                Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED, error.message ?: "Android permission is not granted")
            } catch (error: Throwable) {
                Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INTERNAL, "Host capability failed")
            }
        }
        if (closed.get()) return
        val result = Bundle().apply {
            putString(BunRuntimeContract.KEY_EXECUTION_ID, executionId)
            putString(BunRuntimeContract.KEY_HOST_CALL_ID, callId)
            when (outcome) {
                is Outcome.Success -> {
                    putBoolean(BunRuntimeContract.KEY_SUCCEEDED, true)
                    putString(BunRuntimeContract.KEY_HOST_CALL_RESULT_JSON, outcome.json)
                }
                is Outcome.Failure -> {
                    putBoolean(BunRuntimeContract.KEY_SUCCEEDED, false)
                    putString(BunRuntimeContract.KEY_ERROR_CODE, outcome.code)
                    putString(BunRuntimeContract.KEY_ERROR_MESSAGE, outcome.message)
                }
            }
        }
        try {
            callback.onResult(result)
        } catch (error: RemoteException) {
            // The plugin process is gone; the run is over anyway.
        }
    }

    private fun dispatch(capability: String, requestJson: String): Outcome {
        if (capability !in capabilities) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED, "Capability is not granted for this execution")
        }
        if (requestJson.toByteArray(Charsets.UTF_8).size > BunRuntimeContract.MAX_HOST_CALL_REQUEST_BYTES) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_PAYLOAD_TOO_LARGE, "Request exceeds ${BunRuntimeContract.MAX_HOST_CALL_REQUEST_BYTES} bytes")
        }
        val request = try {
            JSONObject(requestJson.ifBlank { "{}" })
        } catch (error: JSONException) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "Request is not a JSON object")
        }
        val outcome = when (capability) {
            BunRuntimeContract.HOST_CAPABILITY_DEVICE_INFO -> deviceInfo(request)
            BunRuntimeContract.HOST_CAPABILITY_UI_TOAST -> toast(request)
            BunHostCapabilityGrants.TEXT_PINYIN -> textCall(request, NodeBridgeModules.PINYIN, PINYIN_METHODS) {
                NodePinyinBridge.dispatch(it, pinyin)
            }
            BunHostCapabilityGrants.TEXT_PINYIN4J -> textCall(request, NodeBridgeModules.PINYIN4J, PINYIN4J_METHODS) {
                NodePinyin4jBridge.dispatch(it, pinyin4j)
            }
            BunHostCapabilityGrants.SCREEN_METRICS -> methodCall(request, coordinates::invoke)
            BunHostCapabilityGrants.CONSOLE_CONTROL -> methodCall(request, console::invoke)
            else -> Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_UNKNOWN_CAPABILITY, "Capability is not implemented by this host")
        }
        if (outcome is Outcome.Success && outcome.json.toByteArray(Charsets.UTF_8).size > BunRuntimeContract.MAX_HOST_CALL_RESULT_BYTES) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_PAYLOAD_TOO_LARGE, "Result exceeds ${BunRuntimeContract.MAX_HOST_CALL_RESULT_BYTES} bytes")
        }
        return outcome
    }

    private fun parseMethodCall(request: JSONObject): Pair<String, JSONArray> {
        require(request.keys().asSequence().all { it == "method" || it == "args" }) {
            "Capability call expects {method, args?}"
        }
        val method = request.opt("method") as? String ?: throw IllegalArgumentException("method must be a string")
        val args = if (!request.has("args")) JSONArray() else request.optJSONArray("args")
            ?: throw IllegalArgumentException("args must be an array")
        return method to args
    }

    private fun methodCall(request: JSONObject, invoke: (String, JSONArray) -> Any?): Outcome {
        val (method, args) = parseMethodCall(request)
        val result = invoke(method, args)
        return Outcome.Success(JSONObject().put("value", result ?: JSONObject.NULL).toString())
    }

    private fun textCall(
        request: JSONObject,
        module: String,
        methods: Set<String>,
        dispatch: (NodeBridgeRequest) -> NodeBridgeResponse,
    ): Outcome {
        val (method, args) = parseMethodCall(request)
        require(method in methods) { "Unsupported $module method: $method" }
        val response = dispatch(NodeBridgeRequest(executionId, module, method, args.toString(), BunRuntimeContract.HOST_CALL_TIMEOUT_MILLIS.toLong()))
        if (response.ok) {
            // The Bun wire contract requires an outer object, including for strings, matrices and null.
            return Outcome.Success("{\"value\":${response.resultJson ?: "null"}}")
        }
        val error = response.error
        val code = when (error?.category) {
            NodeBridgeErrorCategory.INVALID_REQUEST -> BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST
            NodeBridgeErrorCategory.PERMISSION_DENIED, NodeBridgeErrorCategory.CAPABILITY_DENIED -> BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED
            NodeBridgeErrorCategory.TIMEOUT -> BunRuntimeContract.HOST_CALL_ERROR_TIMEOUT
            NodeBridgeErrorCategory.UNAVAILABLE, NodeBridgeErrorCategory.PROCESS_DEAD -> BunRuntimeContract.HOST_CALL_ERROR_HOST_UNAVAILABLE
            else -> BunRuntimeContract.HOST_CALL_ERROR_INTERNAL
        }
        return Outcome.Failure(code, error?.message ?: "Text provider failed")
    }

    /** `{}` in, the `android.os.Build` facts out; never a serial, IMEI, Android ID, account or network identifier. */
    private fun deviceInfo(request: JSONObject): Outcome {
        if (request.length() != 0) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "device.info takes no fields")
        }
        val json = JSONObject()
            .put("brand", Build.BRAND.orEmpty())
            .put("manufacturer", Build.MANUFACTURER.orEmpty())
            .put("model", Build.MODEL.orEmpty())
            .put("device", Build.DEVICE.orEmpty())
            .put("product", Build.PRODUCT.orEmpty())
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE.orEmpty())
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.orEmpty().toList()))
        return Outcome.Success(json.toString())
    }

    /** `{"text": string, "duration"?: "short" | "long"}`; text is trimmed, 1..1024 UTF-8 bytes, newlines only. */
    private fun toast(request: JSONObject): Outcome {
        val keys = request.keys().asSequence().toSet()
        if (TOAST_TEXT_KEY !in keys || !TOAST_KEYS.containsAll(keys)) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast expects {\"text\", \"duration\"?}")
        }
        val text = (request.opt(TOAST_TEXT_KEY) as? String)?.trim()
            ?: return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast text must be a string")
        if (text.toByteArray(Charsets.UTF_8).size !in 1..MAX_TOAST_TEXT_BYTES) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast text must be 1..$MAX_TOAST_TEXT_BYTES UTF-8 bytes")
        }
        if (text.any { it != '\n' && it.isISOControl() }) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast text contains control characters")
        }
        val durationValue: Any? = request.opt(TOAST_DURATION_KEY)
        val duration = when (durationValue) {
            null, TOAST_DURATION_SHORT -> Toast.LENGTH_SHORT
            TOAST_DURATION_LONG -> Toast.LENGTH_LONG
            else -> return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast duration must be \"short\" or \"long\"")
        }
        if (toasts.incrementAndGet() > MAX_TOAST_CALLS_PER_EXECUTION) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_QUOTA_EXCEEDED, "ui.toast allows $MAX_TOAST_CALLS_PER_EXECUTION calls per run")
        }
        val posted = mainHandler.post {
            if (!closed.get()) Toast.makeText(appContext, text, duration).show()
        }
        if (!posted) return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_HOST_UNAVAILABLE, "Host UI lane is unavailable")
        return Outcome.Success("{\"shown\":true}")
    }

    private sealed interface Outcome {
        class Success(val json: String) : Outcome
        class Failure(val code: String, val message: String) : Outcome
    }

    private companion object {
        const val MAX_CALL_ID_CHARS = 256
        val PINYIN_METHODS = setOf("convert", "simple", "compare", "compact", "fromCodePoint", "fromPhrase")
        val PINYIN4J_METHODS = setOf("of", "as", "readings")
        const val TOAST_TEXT_KEY = "text"
        const val TOAST_DURATION_KEY = "duration"
        const val TOAST_DURATION_SHORT = "short"
        const val TOAST_DURATION_LONG = "long"
        val TOAST_KEYS = setOf(TOAST_TEXT_KEY, TOAST_DURATION_KEY)
        const val MAX_TOAST_TEXT_BYTES = 1024
        const val MAX_TOAST_CALLS_PER_EXECUTION = 4
    }
}
