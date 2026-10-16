package org.autojs.plugin.tool.api

import android.os.Bundle
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One accepted call that PluginToolProviderService hands to `onCall` (MCP roadmap D59): the tool name, the arguments the
 * host validated, the input descriptors of the image, file and directory parameters, the outputs the caller wants and the
 * cancel state. [arguments] holds neither image nor file nor directory parameters; read those through [input] and
 * [inputsOf].
 *
 * zh-CN: 交给 `onCall` 的一次已接受调用: 工具名, 经宿主校验的参数, 图片 / 文件 / 目录参数的输入描述符, 调用方需要的输出与取消状态.
 * [arguments] 不含图片, 文件与目录参数, 它们经 [input] 与 [inputsOf] 读取.
 */
class PluginToolCall internal constructor(
    /** The tool name as the catalog declares it, without the short name. */
    val tool: String,
    val invocationId: String,
    /** Who asked (PluginToolValues.CONSUMER_*), for logs and statistics only; null for hosts that do not say. */
    val consumer: String?,
    /** Milliseconds the host waits for the result before it cancels the call. */
    val timeoutMs: Long,
    val arguments: JSONObject,
    /** Every input descriptor in the order the host sent them; a directory parameter contributes one entry per file. */
    val inputs: List<PluginToolInput>,
    /** The declared outputs the caller wants written, file and directory outputs alike. */
    val wantedOutputs: Set<String>,
    private val trees: Bundle?,
) {

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<Runnable>()
    private val directoryOutputs = HashMap<String, PluginToolDirectoryOutput>()
    private var worker: Thread? = null
    private var finished = false

    @Volatile
    private var reason: String? = null

    /** True once the host cancelled the call (by the caller, on timeout or because the host stops it). */
    val isCancelled: Boolean
        get() = reason != null

    /** PluginToolValues.CANCEL_REASON_* once [isCancelled], null before. */
    val cancelReason: String?
        get() = reason

    /** The first input of the image or file parameter [name], null when the caller left it out. */
    fun input(name: String): PluginToolInput? = inputs.firstOrNull { it.name == name }

    /** The input of the image or file parameter [name]; a missing one reports PluginToolErrorCodes.INVALID_ARGUMENTS. */
    fun requireInput(name: String): PluginToolInput =
        input(name) ?: throw PluginToolException(PluginToolErrorCodes.INVALID_ARGUMENTS, "the input $name is missing")

    /** Every input of the parameter [name], such as the files of a directory parameter. */
    fun inputsOf(name: String): List<PluginToolInput> = inputs.filter { it.name == name }

    /** Whether the caller wants the declared output [name]; the base class drops file outputs nobody wants. */
    fun wantsOutput(name: String): Boolean = name in wantedOutputs

    /** The directory output [name] the host opened for this call, null when the caller does not want it. */
    fun directoryOutput(name: String): PluginToolDirectoryOutput? = synchronized(lock) {
        directoryOutputs[name] ?: trees?.getBinder(name)?.let { binder ->
            PluginToolDirectoryOutput(name, IPluginToolOutputTree.Stub.asInterface(binder), this).also { directoryOutputs[name] = it }
        }
    }

    /** Throws a PluginToolException with PluginToolErrorCodes.CANCELLED once the call was cancelled. */
    fun throwIfCancelled() {
        reason?.let { throw PluginToolException(PluginToolErrorCodes.CANCELLED, "the call was cancelled ($it)") }
    }

    /**
     * Runs [listener] when the call is cancelled, at once when it already is. Listeners run on a Binder thread and should
     * only signal the work, for example by closing a socket or stopping a recognizer.
     */
    fun onCancel(listener: Runnable) {
        synchronized(lock) {
            if (reason == null) {
                listeners += listener
                return
            }
        }
        runCatching { listener.run() }
    }

    internal fun cancel(reason: String) {
        synchronized(lock) {
            if (this.reason != null || finished) return
            this.reason = reason
            worker?.interrupt()
        }
        listeners.forEach { runCatching { it.run() } }
    }

    internal fun begin(thread: Thread?) {
        synchronized(lock) { worker = thread }
    }

    /** Ends the work: later cancels are ignored and the open streams of the directory outputs are closed. */
    internal fun finish() {
        val outputs = synchronized(lock) {
            worker = null
            finished = true
            directoryOutputs.values.toList()
        }
        outputs.forEach { it.closeStreams() }
    }

    internal fun closeInputs() {
        inputs.forEach { it.close() }
    }
}
