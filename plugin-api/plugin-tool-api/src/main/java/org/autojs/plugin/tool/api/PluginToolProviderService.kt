package org.autojs.plugin.tool.api

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.TransactionTooLargeException
import org.autojs.plugin.common.api.PluginInfo
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optional base class of a plugin tool provider (docs/dev/plugin-tool-protocol-v1.md, MCP roadmap D59). A subclass declares
 * its identity ([createPluginInfo]) and catalog ([createCatalogJson]) and implements its tools in [onCall]; the base class
 * answers the Binder transactions. It checks the contract version, refuses tools missing from the catalog, runs every
 * accepted call on a worker thread, forwards `cancel`, hands out the input descriptors and directory outputs by name, keeps
 * the result within PluginToolLimits, closes every descriptor and reports each call exactly once, also when the host cannot
 * take the result (PluginToolErrorCodes.LIMIT_EXCEEDED instead of a lost report). The service is still exported for
 * PluginToolContract.SERVICE_ACTION in the manifest, guarded by the permission `org.autojs.permission.PLUGIN`.
 *
 * Errors of [onCall]: a PluginToolException reports its code, an IllegalArgumentException reports
 * PluginToolErrorCodes.INVALID_ARGUMENTS, anything else PluginToolErrorCodes.FAILED; once the call was cancelled every
 * failure reports PluginToolErrorCodes.CANCELLED.
 *
 * zh-CN: 插件工具提供者的可选基类. 子类声明身份 ([createPluginInfo]) 与目录 ([createCatalogJson]), 在 [onCall] 中实现工具; 基类负责
 * Binder 事务: 检查契约版本, 拒绝目录外的工具, 在工作线程上运行调用, 转发取消, 按名字提供输入描述符与目录输出, 检查结果限额, 关闭全部描述符,
 * 并保证每次调用只报告一次 (宿主收不下结果时报告 LIMIT_EXCEEDED, 不会丢失报告). 服务仍需在清单中为 PluginToolContract.SERVICE_ACTION
 * 导出, 并以 `org.autojs.permission.PLUGIN` 权限保护.
 */
abstract class PluginToolProviderService : Service() {

    private val running = ConcurrentHashMap<String, PluginToolCall>()
    private val threadNumber = AtomicInteger()
    private var executor: ThreadPoolExecutor? = null
    private var destroyed = false

    @Volatile
    private var catalogNames: Pair<String, Set<String>>? = null

    /** Calls that may run at the same time; the host also limits every tool to its `maxConcurrency`. */
    protected open val workerThreads: Int
        get() = PluginToolLimits.MAX_CONCURRENCY

    /** Whether `cancel` interrupts the worker thread of the call, besides [PluginToolCall.isCancelled] and its listeners. */
    protected open val interruptOnCancel: Boolean
        get() = true

    /** Identity of the plugin; the base class adds PluginToolCapabilityKeys.CONTRACT_VERSION when it is missing. */
    protected abstract fun createPluginInfo(): PluginInfo

    /**
     * The catalog document (PluginToolCatalogJson). It is asked for on every `getCatalog` and `invoke`, so it may adapt to
     * the device, for example while a model is missing; throw a PluginToolException to answer NOT_READY and the like.
     */
    protected abstract fun createCatalogJson(): String

    /**
     * Runs one accepted call of a tool of the catalog on a worker thread and returns its result. Long work checks
     * [PluginToolCall.isCancelled] or calls [PluginToolCall.throwIfCancelled] now and then. The input descriptors, the
     * streams of the directory outputs and the output descriptors of the result are closed by the base class.
     */
    @Throws(Exception::class)
    protected abstract fun onCall(call: PluginToolCall): PluginToolResult

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        running.values.forEach { it.cancel(PluginToolValues.CANCEL_REASON_HOST) }
        synchronized(this) {
            destroyed = true
            executor?.shutdown()
        }
        super.onDestroy()
    }

    private val binder = object : IPluginToolProvider.Stub() {

        override fun getInfo(): PluginInfo = pluginInfo()

        override fun getCatalog(request: Bundle?): Bundle = catalogAnswer(request)

        override fun invoke(request: Bundle?, inputs: Array<out ParcelFileDescriptor>?, callback: IPluginToolCallback?): Bundle =
            accept(request, inputs?.toList().orEmpty(), callback)

        override fun cancel(request: Bundle?) {
            val invocationId = request?.getString(PluginToolCancelKeys.INVOCATION_ID) ?: return
            running[invocationId]?.cancel(request.getString(PluginToolCancelKeys.REASON) ?: PluginToolValues.CANCEL_REASON_CALLER)
        }
    }

    private fun pluginInfo(): PluginInfo = createPluginInfo().apply {
        val bundle = capabilities ?: Bundle().also { capabilities = it }
        if (!bundle.containsKey(PluginToolCapabilityKeys.CONTRACT_VERSION)) {
            bundle.putInt(PluginToolCapabilityKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
        }
    }

    private fun catalogAnswer(request: Bundle?): Bundle = Bundle().apply {
        putInt(PluginToolCatalogKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
        fun fail(code: String, message: String) {
            putString(PluginToolCatalogKeys.ERROR_CODE, code)
            putString(PluginToolCatalogKeys.ERROR_MESSAGE, message.take(PluginToolLimits.MAX_ERROR_MESSAGE_CHARS))
        }
        if (request != null && request.containsKey(PluginToolCatalogRequestKeys.CONTRACT_VERSION)) {
            val version = request.getInt(PluginToolCatalogRequestKeys.CONTRACT_VERSION)
            if (!PluginToolContract.supportsContractVersion(version)) {
                fail(PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION, "the host speaks contract version $version")
                return@apply
            }
        }
        try {
            val json = createCatalogJson()
            val bytes = json.toByteArray(Charsets.UTF_8).size
            if (bytes > PluginToolLimits.MAX_CATALOG_BYTES) {
                fail(PluginToolErrorCodes.LIMIT_EXCEEDED, "the catalog has $bytes bytes, more than ${PluginToolLimits.MAX_CATALOG_BYTES}")
            } else {
                putString(PluginToolCatalogKeys.CATALOG_JSON, json)
            }
        } catch (e: PluginToolException) {
            fail(e.code, e.message)
        } catch (e: Exception) {
            fail(PluginToolErrorCodes.FAILED, describe(e))
        }
    }

    private fun accept(request: Bundle?, descriptors: List<ParcelFileDescriptor>, callback: IPluginToolCallback?): Bundle {
        fun refuse(code: String, message: String): Bundle {
            descriptors.forEach { runCatching { it.close() } }
            return Bundle().apply {
                putBoolean(PluginToolAcceptKeys.ACCEPTED, false)
                putString(PluginToolAcceptKeys.ERROR_CODE, code)
                putString(PluginToolAcceptKeys.ERROR_MESSAGE, message.take(PluginToolLimits.MAX_ERROR_MESSAGE_CHARS))
            }
        }
        try {
            val invocationId = request?.getString(PluginToolInvokeKeys.INVOCATION_ID)
            val tool = request?.getString(PluginToolInvokeKeys.TOOL)
            if (request == null || callback == null || invocationId == null || tool == null) {
                return refuse(PluginToolErrorCodes.FAILED, "the request is incomplete")
            }
            val version = request.getInt(PluginToolInvokeKeys.CONTRACT_VERSION)
            if (!PluginToolContract.supportsContractVersion(version)) {
                return refuse(PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION, "the host speaks contract version $version")
            }
            val names = try {
                toolNames()
            } catch (e: PluginToolException) {
                return refuse(e.code, e.message)
            }
            if (tool !in names) return refuse(PluginToolErrorCodes.UNKNOWN_TOOL, "the catalog has no tool $tool")
            val arguments = try {
                JSONObject(request.getString(PluginToolInvokeKeys.ARGUMENTS_JSON) ?: "{}")
            } catch (e: JSONException) {
                return refuse(PluginToolErrorCodes.INVALID_ARGUMENTS, "the arguments are not a JSON object")
            }
            val wanted = JSONArray(request.getString(PluginToolInvokeKeys.OUTPUTS_JSON) ?: "[]").let { list ->
                (0 until list.length()).mapTo(LinkedHashSet()) { list.getString(it) }
            }
            val call = PluginToolCall(
                tool = tool,
                invocationId = invocationId,
                consumer = request.getString(PluginToolInvokeKeys.CONSUMER),
                timeoutMs = request.getLong(PluginToolInvokeKeys.TIMEOUT_MS, PluginToolLimits.DEFAULT_TIMEOUT_MS),
                arguments = arguments,
                inputs = PluginToolInput.fromRequest(request.getString(PluginToolInvokeKeys.INPUTS_JSON), descriptors),
                wantedOutputs = wanted,
                trees = request.getBundle(PluginToolInvokeKeys.OUTPUT_TREES),
            )
            if (running.putIfAbsent(invocationId, call) != null) {
                return refuse(PluginToolErrorCodes.FAILED, "the invocation $invocationId is already running")
            }
            try {
                executor().execute { run(call, callback) }
            } catch (e: RejectedExecutionException) {
                running.remove(invocationId, call)
                return refuse(PluginToolErrorCodes.NOT_READY, "the provider is shutting down")
            }
            return Bundle().apply { putBoolean(PluginToolAcceptKeys.ACCEPTED, true) }
        } catch (e: Exception) {
            return refuse(PluginToolErrorCodes.FAILED, describe(e))
        }
    }

    private fun run(call: PluginToolCall, callback: IPluginToolCallback) {
        var result: PluginToolResult? = null
        try {
            call.begin(if (interruptOnCancel) Thread.currentThread() else null)
            call.throwIfCancelled()
            result = onCall(call)
            settle(call)
            call.throwIfCancelled()
            deliver(call, result, callback)
        } catch (e: Throwable) {
            settle(call)
            result?.closeOutputs()
            val (code, message) = when {
                call.isCancelled -> PluginToolErrorCodes.CANCELLED to "the call was cancelled (${call.cancelReason})"
                e is PluginToolException -> e.code to e.message
                e is IllegalArgumentException -> PluginToolErrorCodes.INVALID_ARGUMENTS to describe(e)
                else -> PluginToolErrorCodes.FAILED to describe(e)
            }
            runCatching {
                callback.onError(Bundle().apply {
                    putString(PluginToolErrorKeys.INVOCATION_ID, call.invocationId)
                    putString(PluginToolErrorKeys.CODE, code)
                    putString(PluginToolErrorKeys.MESSAGE, message.take(PluginToolLimits.MAX_ERROR_MESSAGE_CHARS))
                })
            }
        } finally {
            // A cancel may interrupt the thread just before the call detached it; the next call must not see it.
            Thread.interrupted()
        }
    }

    /** Ends the work before the report: no more cancels, every input and directory output stream closed. */
    private fun settle(call: PluginToolCall) {
        call.finish()
        call.closeInputs()
        running.remove(call.invocationId, call)
    }

    private fun deliver(call: PluginToolCall, result: PluginToolResult, callback: IPluginToolCallback) {
        result.limitProblem()?.let { throw PluginToolException(PluginToolErrorCodes.LIMIT_EXCEEDED, it) }
        val outputs = result.outputs.filter { it.name in call.wantedOutputs }
        outputs.forEach { output ->
            val size = output.descriptor.statSize
            if (size > PluginToolLimits.MAX_DESCRIPTOR_BYTES) {
                throw PluginToolException(
                    PluginToolErrorCodes.LIMIT_EXCEEDED,
                    "the output ${output.name} has $size bytes, more than ${PluginToolLimits.MAX_DESCRIPTOR_BYTES}",
                )
            }
        }
        val bundle = Bundle().apply {
            putString(PluginToolResultKeys.INVOCATION_ID, call.invocationId)
            putString(PluginToolResultKeys.RESULT_JSON, result.json)
            result.text?.let { putString(PluginToolResultKeys.TEXT, it) }
            putString(PluginToolResultKeys.OUTPUTS_JSON, JSONArray().apply {
                outputs.forEach { put(JSONObject().put(PluginToolOutputJson.NAME, it.name).put(PluginToolOutputJson.MIME_TYPE, it.mimeType)) }
            }.toString())
        }
        try {
            report(bundle, outputs.map { it.descriptor }.toTypedArray(), callback)
        } finally {
            result.closeOutputs()
        }
    }

    /**
     * Sends the result. A oneway transaction the host's Binder buffer cannot take fails here and never reaches the host, so
     * it is tried again while the buffer may still be busy with other reports, then becomes the error of the call
     * (MCP roadmap D65); the host would otherwise wait for the timeout.
     */
    private fun report(bundle: Bundle, descriptors: Array<ParcelFileDescriptor>, callback: IPluginToolCallback) {
        var failure: Exception? = null
        for (delayMs in REPORT_RETRY_DELAYS_MS) {
            if (delayMs > 0) Thread.sleep(delayMs)
            try {
                callback.onResult(bundle, descriptors)
                return
            } catch (e: RemoteException) {
                failure = e
            } catch (e: RuntimeException) {
                failure = e
                break
            }
        }
        val parcel = Parcel.obtain()
        val parcelBytes = try {
            parcel.writeBundle(bundle)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
        throw PluginToolException(
            if (failure is TransactionTooLargeException) PluginToolErrorCodes.LIMIT_EXCEEDED else PluginToolErrorCodes.FAILED,
            "the host could not take the result of $parcelBytes Parcel bytes (${failure?.let(::describe)}); return less at a time",
        )
    }

    private fun toolNames(): Set<String> {
        val json = createCatalogJson()
        catalogNames?.let { (source, names) -> if (source == json) return names }
        val tools = JSONObject(json).getJSONArray(PluginToolCatalogJson.TOOLS)
        val names = (0 until tools.length()).mapTo(HashSet()) { tools.getJSONObject(it).getString(PluginToolCatalogJson.NAME) }
        catalogNames = json to names
        return names
    }

    @Synchronized
    private fun executor(): ThreadPoolExecutor {
        if (destroyed) throw RejectedExecutionException("the service is destroyed")
        return executor ?: workerThreads.coerceAtLeast(1).let { size ->
            ThreadPoolExecutor(size, size, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
                Thread(task, "plugin-tool-${threadNumber.incrementAndGet()}").apply { isDaemon = true }
            }.apply { allowCoreThreadTimeOut(true) }
        }.also { executor = it }
    }

    private fun describe(e: Throwable): String = e.message?.takeIf { it.isNotBlank() }?.let { "${e.javaClass.simpleName}: $it" } ?: e.javaClass.name

    private companion object {

        /** Waits before each try of a result report (MCP roadmap D65). */
        val REPORT_RETRY_DELAYS_MS = longArrayOf(0L, 100L, 400L)
    }
}
