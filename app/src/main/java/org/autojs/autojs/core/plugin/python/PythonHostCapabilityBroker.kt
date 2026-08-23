package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.ScriptToast
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.plugin.python.runtime.api.IPythonHostCapabilityBroker
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal object PythonHostCapabilities {
    const val TOAST_SHOW = "toast.show"
    const val CLIP_GET = "clip.get"
    const val CLIP_SET = "clip.set"
    const val APP_LAUNCH = "app.launch"
    const val APP_LAUNCH_APP = "app.launch_app"
    const val APP_OPEN_URL = "app.open_url"

    val ENABLED = setOf(
        TOAST_SHOW,
        CLIP_GET,
        CLIP_SET,
        APP_LAUNCH,
        APP_LAUNCH_APP,
        APP_OPEN_URL,
    )
}

internal interface PythonHostCapabilityActions {
    fun showToast(text: String)

    fun getClipboardText(): String

    fun setClipboardText(text: String)

    fun launchPackage(packageName: String): Boolean

    fun launchApp(label: String): Boolean

    fun openUrl(url: String): Boolean
}

internal class AndroidPythonHostCapabilityActions(
    context: Context,
    executionId: String,
) : PythonHostCapabilityActions {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val app = AppUtils(appContext)
    private val toastOwnerId = "python@$executionId"

    override fun showToast(text: String) = onMain {
        ScriptToast.enqueueToast(appContext, toastOwnerId, text)
    }

    override fun getClipboardText(): String = onMain {
        ClipboardUtils.getClipOrEmpty(appContext).toString()
    }

    override fun setClipboardText(text: String) = onMain {
        ClipboardUtils.setClip(appContext, text)
    }

    override fun launchPackage(packageName: String): Boolean = onMain {
        app.launchPackage(packageName)
    }

    override fun launchApp(label: String): Boolean = onMain {
        app.launchApp(label)
    }

    override fun openUrl(url: String): Boolean = onMain {
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    private fun <T> onMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val task = FutureTask(Callable { action() })
        check(mainHandler.post(task)) { "AutoJs6 main dispatcher rejected a Python host capability" }
        return try {
            task.get(PythonRuntimeContract.HOST_CAPABILITY_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            task.cancel(true)
            throw PythonHostCapabilityTimeoutException(error)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }
}

/**
 * Execution-scoped pure-data dispatcher. Binder identity checks remain in [PythonHostCapabilityBroker].
 */
internal class PythonHostCapabilityDispatcher(
    private val executionId: String,
    grantedCapabilities: Collection<String>,
    private val actions: PythonHostCapabilityActions,
    private val maxCalls: Int = PythonRuntimeContract.MAX_HOST_CAPABILITY_CALLS,
) {
    private val grantedCapabilities = grantedCapabilities.toSet()
    private val admittedCallIds = linkedSetOf<Long>()
    private var closed = false

    init {
        require(executionId.isNotBlank()) { "Python host capability execution ID is blank" }
        require(this.grantedCapabilities.all { it in PythonHostCapabilities.ENABLED }) {
            "Python host capability grant contains an unsupported capability"
        }
        require(maxCalls in 1..PythonRuntimeContract.MAX_HOST_CAPABILITY_CALLS) {
            "Python host capability call quota is invalid"
        }
    }

    fun dispatch(encodedRequest: ByteArray): ByteArray {
        require(encodedRequest.size in 1..PythonRuntimeContract.MAX_HOST_CAPABILITY_REQUEST_BYTES) {
            "Python host capability request exceeds the byte limit"
        }
        val request = decodeRequest(encodedRequest)
        require(request.executionId == executionId) {
            "Python host capability request execution ID does not match"
        }
        admit(request)?.let { error -> return encodeFailure(request, error.first, error.second) }
        if (request.capability !in grantedCapabilities) {
            return encodeFailure(request, ERROR_CAPABILITY_UNAVAILABLE, "Host capability was not granted")
        }
        val response = try {
            when (request.capability) {
                PythonHostCapabilities.TOAST_SHOW -> {
                    actions.showToast(request.singleTextArgument(ARG_TEXT))
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.CLIP_GET -> {
                    request.requireArguments()
                    encodeSuccess(request, actions.getClipboardText())
                }
                PythonHostCapabilities.CLIP_SET -> {
                    actions.setClipboardText(request.singleTextArgument(ARG_TEXT))
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.APP_LAUNCH -> encodeSuccess(
                    request,
                    actions.launchPackage(request.singleTextArgument(ARG_PACKAGE, PACKAGE_NAME)),
                )
                PythonHostCapabilities.APP_LAUNCH_APP -> encodeSuccess(
                    request,
                    actions.launchApp(request.singleTextArgument(ARG_NAME)),
                )
                PythonHostCapabilities.APP_OPEN_URL -> encodeSuccess(
                    request,
                    actions.openUrl(request.singleTextArgument(ARG_URL, WEB_URL)),
                )
                else -> encodeFailure(
                    request,
                    ERROR_CAPABILITY_UNAVAILABLE,
                    "Host capability is unavailable",
                )
            }
        } catch (_: PythonHostCapabilityTimeoutException) {
            encodeFailure(request, ERROR_HOST_TIMEOUT, "Host capability timed out")
        } catch (_: IllegalArgumentException) {
            encodeFailure(request, ERROR_INVALID_ARGUMENT, "Host capability arguments are invalid")
        } catch (_: Throwable) {
            encodeFailure(request, ERROR_HOST_FAILURE, "Host capability failed")
        }
        if (response.size > PythonRuntimeContract.MAX_HOST_CAPABILITY_RESPONSE_BYTES) {
            return encodeFailure(
                request,
                ERROR_RESULT_LIMIT_EXCEEDED,
                "Host capability result exceeds the byte limit",
            )
        }
        return response
    }

    @Synchronized
    fun close(): Boolean {
        if (closed) return false
        closed = true
        admittedCallIds.clear()
        return true
    }

    @Synchronized
    private fun admit(request: Request): Pair<String, String>? {
        if (closed) return ERROR_BROKER_CLOSED to "Host capability broker is closed"
        if (request.callId in admittedCallIds) {
            return ERROR_REPLAYED_CALL to "Host capability call ID was already used"
        }
        if (admittedCallIds.size >= maxCalls) {
            return ERROR_CALL_LIMIT_EXCEEDED to "Host capability call quota is exhausted"
        }
        admittedCallIds += request.callId
        return null
    }

    private fun decodeRequest(encoded: ByteArray): Request {
        val text = strictUtf8(encoded)
        val document = runCatching { JsonParser.parseString(text) }
            .getOrElse { throw IllegalArgumentException("Python host capability request is malformed", it) }
            .takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: throw IllegalArgumentException("Python host capability request is not an object")
        require(document.keySet() == REQUEST_KEYS) {
            "Python host capability request has missing or unexpected fields"
        }
        require(document.requireLong(KEY_VERSION) == JSON_VERSION.toLong()) {
            "Python host capability JSON version is unsupported"
        }
        val requestExecutionId = document.requireString(KEY_EXECUTION_ID)
        val callId = document.requireLong(KEY_CALL_ID)
        require(callId > 0L) { "Python host capability call ID must be positive" }
        val capability = document.requireString(KEY_CAPABILITY)
        require(capability.isNotBlank()) { "Python host capability name is blank" }
        val arguments = document.get(KEY_ARGUMENTS)
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: throw IllegalArgumentException("Python host capability arguments must be an object")
        return Request(requestExecutionId, callId, capability, arguments)
    }

    private fun encodeSuccess(request: Request, value: Any?): ByteArray = encodeResponse(
        JsonObject().apply {
            addProperty(KEY_VERSION, JSON_VERSION)
            addProperty(KEY_EXECUTION_ID, request.executionId)
            addProperty(KEY_CALL_ID, request.callId)
            addProperty(KEY_OK, true)
            when (value) {
                null -> add(KEY_VALUE, JsonNull.INSTANCE)
                is String -> addProperty(KEY_VALUE, value)
                is Boolean -> addProperty(KEY_VALUE, value)
                else -> error("Unsupported Python host capability result type: ${value::class.java.name}")
            }
        },
    )

    private fun encodeFailure(request: Request, code: String, message: String): ByteArray = encodeResponse(
        JsonObject().apply {
            addProperty(KEY_VERSION, JSON_VERSION)
            addProperty(KEY_EXECUTION_ID, request.executionId)
            addProperty(KEY_CALL_ID, request.callId)
            addProperty(KEY_OK, false)
            add(
                KEY_ERROR,
                JsonObject().apply {
                    addProperty(KEY_ERROR_CODE, code)
                    addProperty(KEY_ERROR_MESSAGE, message)
                },
            )
        },
    )

    private fun encodeResponse(document: JsonObject): ByteArray =
        document.toString().toByteArray(StandardCharsets.UTF_8)

    private fun strictUtf8(value: ByteArray): String = runCatching {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(value))
            .toString()
    }.getOrElse { throw IllegalArgumentException("Python host capability request is not UTF-8", it) }

    private data class Request(
        val executionId: String,
        val callId: Long,
        val capability: String,
        val arguments: JsonObject,
    ) {
        fun requireArguments(vararg expected: String) {
            require(arguments.keySet() == expected.toSet()) {
                "Python host capability arguments have missing or unexpected fields"
            }
        }

        fun singleTextArgument(name: String, pattern: Regex? = null): String {
            requireArguments(name)
            val value = arguments.requireString(name)
            require(value.toByteArray(StandardCharsets.UTF_8).size <= PythonRuntimeContract.MAX_HOST_CAPABILITY_TEXT_BYTES) {
                "Python host capability text argument exceeds the byte limit"
            }
            require(pattern == null || pattern.matches(value)) {
                "Python host capability text argument has an invalid shape"
            }
            return value
        }
    }

    private companion object {
        const val JSON_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_EXECUTION_ID = "executionId"
        const val KEY_CALL_ID = "callId"
        const val KEY_CAPABILITY = "capability"
        const val KEY_ARGUMENTS = "arguments"
        const val KEY_OK = "ok"
        const val KEY_VALUE = "value"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_CODE = "code"
        const val KEY_ERROR_MESSAGE = "message"
        const val ARG_TEXT = "text"
        const val ARG_PACKAGE = "package"
        const val ARG_NAME = "name"
        const val ARG_URL = "url"
        const val ERROR_CAPABILITY_UNAVAILABLE = "CAPABILITY_UNAVAILABLE"
        const val ERROR_INVALID_ARGUMENT = "INVALID_ARGUMENT"
        const val ERROR_HOST_FAILURE = "HOST_FAILURE"
        const val ERROR_HOST_TIMEOUT = "HOST_TIMEOUT"
        const val ERROR_BROKER_CLOSED = "BROKER_CLOSED"
        const val ERROR_REPLAYED_CALL = "REPLAYED_CALL"
        const val ERROR_CALL_LIMIT_EXCEEDED = "CALL_LIMIT_EXCEEDED"
        const val ERROR_RESULT_LIMIT_EXCEEDED = "RESULT_LIMIT_EXCEEDED"
        val REQUEST_KEYS = setOf(KEY_VERSION, KEY_EXECUTION_ID, KEY_CALL_ID, KEY_CAPABILITY, KEY_ARGUMENTS)
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        val WEB_URL = Regex("https?://.+", RegexOption.IGNORE_CASE)
    }
}

internal class PythonHostCapabilityBroker(
    context: Context,
    executionId: String,
    private val expectedPluginUid: Int,
    private val onViolation: (Throwable) -> Unit,
    actions: PythonHostCapabilityActions = AndroidPythonHostCapabilityActions(context, executionId),
) : IPythonHostCapabilityBroker.Stub(), AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val dispatcher = PythonHostCapabilityDispatcher(
        executionId = executionId,
        grantedCapabilities = PythonHostCapabilities.ENABLED,
        actions = actions,
    )

    override fun dispatch(request: ByteArray?): ByteArray {
        val callingUid = Binder.getCallingUid()
        if (callingUid != expectedPluginUid) {
            val error = SecurityException(
                "Python host capability caller UID $callingUid differs from pinned UID $expectedPluginUid",
            )
            close()
            onViolation(error)
            throw error
        }
        val identity = Binder.clearCallingIdentity()
        return try {
            dispatcher.dispatch(requireNotNull(request) { "Python host capability request is null" })
        } catch (error: Throwable) {
            close()
            onViolation(error)
            throw error
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) dispatcher.close()
    }
}

private class PythonHostCapabilityTimeoutException(cause: Throwable) : RuntimeException(cause)

private fun JsonObject.requireString(name: String): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Python host capability field '$name' must be a string"
    }
    return value.asString
}

private fun JsonObject.requireLong(name: String): Long {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Python host capability field '$name' must be an integer"
    }
    val encoded = value.asJsonPrimitive.asString
    require(INTEGER.matches(encoded)) { "Python host capability field '$name' must be an integer" }
    return encoded.toLongOrNull()
        ?: throw IllegalArgumentException("Python host capability field '$name' is outside the integer range")
}

private val INTEGER = Regex("-?(?:0|[1-9][0-9]*)")
