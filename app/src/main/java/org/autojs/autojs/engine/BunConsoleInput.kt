package org.autojs.autojs.engine

import org.autojs.autojs.core.console.ConsoleInputRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * console.rawInput of the Bun host client (api-coverage P4.3, D-7, Q165 (a)). A Bun host call lasts at most 10 s, so the line
 * read is three console.control routes: `rawInput []` queues a line request on the execution console (the queue of stdin and
 * Rhino's console.rawInput, first come first served) and answers its id, `rawInputPoll [id]` waits at most 1 s and answers
 * `{done: false}` or `{done: true, text}` (text null when the request was cancelled: the execution ended or the console was
 * reset), and `rawInputCancel [id]` withdraws it. At most [MAX_PENDING] requests wait at once; [close] cancels the rest.
 * zh-CN: Bun 宿主客户端的 console.rawInput (api-coverage P4.3, D-7, Q165 (a)). Bun 宿主调用最长 10 s, 因此读行拆成三条 console.control 路由:
 * `rawInput []` 在执行控制台上排队一个读行请求 (与 stdin 及 Rhino console.rawInput 共用队列, 先到先得) 并返回其 id, `rawInputPoll [id]` 最多等
 * 1 s, 返回 `{done: false}` 或 `{done: true, text}` (请求被取消时 text 为 null: 执行结束或控制台重置), `rawInputCancel [id]` 撤回请求.
 * 同时最多 [MAX_PENDING] 个请求等待; [close] 取消其余请求.
 *
 * @param requestInput queues one request; its callback receives the line, or null when the request is cancelled.
 */
internal class BunConsoleInput(private val requestInput: (callback: (String?) -> Unit) -> ConsoleInputRequest) {

    private class Pending(val input: ConsoleInputRequest, val line: CompletableFuture<String?>)

    private val pending = LinkedHashMap<String, Pending>()
    private var closed = false

    fun invoke(method: String, args: JSONArray): Any? = when (method) {
        START -> {
            require(args.length() == 0) { "console.rawInput takes no arguments; print the prompt first" }
            start()
        }
        POLL -> poll(id(args))
        CANCEL -> cancel(id(args))
        else -> throw IllegalArgumentException("Unsupported console input method: $method")
    }

    private fun start(): String {
        synchronized(pending) {
            check(!closed) { "The execution has ended" }
            require(pending.size < MAX_PENDING) { "At most $MAX_PENDING console.rawInput requests may wait at once" }
        }
        val id = UUID.randomUUID().toString()
        val line = CompletableFuture<String?>()
        val input = requestInput { text -> line.complete(text) }
        val cancelNow = synchronized(pending) {
            if (!closed) pending[id] = Pending(input, line)
            closed
        }
        if (cancelNow) input.cancel()
        return id
    }

    private fun poll(id: String): JSONObject {
        val request = synchronized(pending) { pending[id] } ?: throw IllegalArgumentException("Unknown or finished console.rawInput request")
        val text = try {
            request.line.get(POLL_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            return JSONObject().put("done", false)
        }
        synchronized(pending) { pending.remove(id) }
        return JSONObject().put("done", true).put("text", text ?: JSONObject.NULL)
    }

    private fun cancel(id: String): Boolean {
        val request = synchronized(pending) { pending.remove(id) } ?: return false
        request.input.cancel()
        return true
    }

    /** Cancels the requests still waiting; later rawInput calls fail. zh-CN: 取消仍在等待的请求; 之后的 rawInput 调用失败. */
    fun close() {
        val waiting = synchronized(pending) {
            closed = true
            pending.values.toList().also { pending.clear() }
        }
        waiting.forEach { it.input.cancel() }
    }

    private fun id(args: JSONArray): String {
        require(args.length() == 1) { "Expected the request id" }
        return args.opt(0) as? String ?: throw IllegalArgumentException("The request id must be a string")
    }

    companion object {
        const val START = "rawInput"
        const val POLL = "rawInputPoll"
        const val CANCEL = "rawInputCancel"
        val METHODS = setOf(START, POLL, CANCEL)
        const val MAX_PENDING = 4
        const val POLL_MILLIS = 1_000L
    }
}
