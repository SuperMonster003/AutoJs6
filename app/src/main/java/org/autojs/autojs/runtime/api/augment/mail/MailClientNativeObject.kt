package org.autojs.autojs.runtime.api.augment.mail

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.autojs.core.plugin.mail.MailAttachmentSink
import org.autojs.autojs.core.plugin.mail.MailAttachmentSource
import org.autojs.autojs.core.plugin.mail.MailDownloads
import org.autojs.autojs.core.plugin.mail.MailJson
import org.autojs.autojs.core.plugin.mail.MailSessionClient
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsIsEmpty
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsLengthInRange
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsOnlyOne
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.mail.MailScriptArguments
import org.autojs.autojs.runtime.api.mail.MailScriptOptions
import org.autojs.autojs.runtime.api.mail.MailScriptValues
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.Closeable
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** The work of one client method: runs in a coroutine; the dispatcher is present for the `Async` form only. zh-CN: 客户端方法的工作: 在协程里运行; 只有 `Async` 形态带分发器. */
internal typealias MailWork = suspend (MailAsyncDispatcher?) -> Any?

/**
 * The script-visible `MailClient` (mail roadmap P3.1 and P3.2, appendix A.3): `account` (the
 * options without secrets), `isConnected`, `isClosed`, `test()`, `send()`, `folders()` /
 * `folder(path)` / `folderStatus()` / `createFolder()` / `deleteFolder()` / `renameFolder()`,
 * `fetch()`, `search()`, `get()` (alias `fetchBody()`), `download()`, `raw()`, `setFlags()` with
 * `markRead()` / `markUnread()` / `flag()` / `unflag()`, `move()`, `copy()`, `delete()`,
 * `expunge()`, `append()`, `watch(folder?, options?)` (P5, a [MailWatch] emitter) and `close()`.
 * Every network method has a synchronous form that blocks the script thread inside the runtime's
 * coroutine context (so stopping the script cancels the call on the plugin side) and an `Async`
 * form that returns a Promise settled on the script thread; argument shapes are normalized in
 * [MailScriptArguments] (pure Kotlin) and results are decorated by [MailJsResults]. With
 * `debug: true` (decision D28) the redacted protocol trace the plugin sends before each result
 * goes to `console.verbose`. Closing the client (or the script's exit) stops its watches.
 * zh-CN: 脚本可见的 `MailClient` (邮件路线图 P3.1 与 P3.2, 附录 A.3): `account` (不含秘密的选项), `isConnected`, `isClosed`, `test()`, `send()`,
 * `folders()` / `folder(path)` / `folderStatus()` / `createFolder()` / `deleteFolder()` / `renameFolder()`, `fetch()`, `search()`, `get()` (别名
 * `fetchBody()`), `download()`, `raw()`, `setFlags()` 及 `markRead()` / `markUnread()` / `flag()` / `unflag()`, `move()`, `copy()`, `delete()`,
 * `expunge()`, `append()`, `watch(folder?, options?)` (P5, [MailWatch] 发射器) 与 `close()`. 每个网络方法都有在运行时协程上下文里阻塞脚本线程的
 * 同步形态 (停止脚本即在插件侧取消调用) 与返回在脚本线程落定的 Promise 的 `Async` 形态; 参数形态在 [MailScriptArguments] (纯 Kotlin) 规范化,
 * 结果由 [MailJsResults] 装饰. `debug: true` (决策 D28) 时插件在每个结果前发来的脱敏协议摘要输出到 `console.verbose`. 关闭客户端 (或脚本退出)
 * 会停止它的监听.
 */
class MailClientNativeObject(
    private val scriptRuntime: ScriptRuntime,
    private val service: MailService,
    internal val session: MailSessionClient,
    internal val options: MailScriptOptions,
) : NativeObject(), Closeable, MailJsResults.Binding {

    private val closed = AtomicBoolean(false)
    private val watches = CopyOnWriteArraySet<MailWatchRunner>()
    private val generations = AtomicLong(0L)

    private val progressListener: ((MailJson.Progress) -> Unit)? = if (options.debug) {
        { progress -> progress.debug?.forEach { line -> scriptRuntime.console.verbose(line) } }
    } else null

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty("account", RhinoUtils.toJsValue(scriptRuntime.topLevelScope, MailScriptValues.toPlain(options.snapshot)), READONLY or PERMANENT)
        defineProperty("isConnected", { session.isOpen }, null, READONLY or PERMANENT)
        defineProperty("isClosed", { closed.get() }, null, READONLY or PERMANENT)
        defineFunction(this, "test") { args -> ensureArgumentsIsEmpty(args) { sync { _ -> test() } } }
        defineFunction(this, "testAsync") { args -> ensureArgumentsIsEmpty(args) { async { _ -> test() } } }
        defineFunction(this, "close") { args ->
            ensureArgumentsIsEmpty(args) {
                close()
                UNDEFINED
            }
        }
        defineFunction(this, "toString") { describe() }

        // Appendix A.3, in table order. zh-CN: 附录 A.3, 按表序.
        defineOp(this, "send", 1..2) { a ->
            outgoing(MailContract.OP_MAIL_SEND, MailScriptArguments.send(json(a[0]), json(a.getOrNull(1)), ::resolvePath, ::sizeOf))
        }
        defineOp(this, "folders", 0..1) { a ->
            val args = MailScriptArguments.folders(json(a.getOrNull(0)))
            plain { call(MailContract.OP_FOLDERS_LIST, args) }
        }
        defineFunction(this, "folder") { args -> ensureArgumentsOnlyOne(args) { path -> folderObject(MailScriptArguments.folderPath(json(path), "path")) } }
        defineOp(this, "folderStatus", 1..1) { a -> folderStatus(json(a[0])) }
        defineOp(this, "createFolder", 1..1) { a -> createFolder(json(a[0])) }
        defineOp(this, "deleteFolder", 1..1) { a -> deleteFolder(json(a[0])) }
        defineOp(this, "renameFolder", 2..2) { a -> renameFolder(json(a[0]), json(a[1])) }
        defineOp(this, "fetch", 0..1) { a ->
            val args = MailScriptArguments.fetch(json(a.getOrNull(0)))
            messages { call(MailContract.OP_MESSAGES_LIST, args) }
        }
        defineOp(this, "search", 1..2) { a ->
            val args = MailScriptArguments.search(json(a[0]), json(a.getOrNull(1)))
            searchResult { call(MailContract.OP_MESSAGES_SEARCH, args) }
        }
        defineOp(this, "get", 1..2) { a -> getWork(a[0], a.getOrNull(1)) }
        defineOp(this, "fetchBody", 1..2) { a -> getWork(a[0], a.getOrNull(1)) }
        defineOp(this, "download", 1..3) { a -> downloadWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
        defineOp(this, "raw", 1..3) { a -> rawWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
        defineOp(this, "setFlags", 2..4) { a ->
            val args = MailScriptArguments.flags(json(a[0]), json(a[1]), json(a.getOrNull(2)), json(a.getOrNull(3)))
            uidsResult { call(MailContract.OP_MESSAGES_SET_FLAGS, args) }
        }
        defineFlagShortcut("markRead", "seen", "add")
        defineFlagShortcut("markUnread", "seen", "remove")
        defineFlagShortcut("flag", "flagged", "add")
        defineFlagShortcut("unflag", "flagged", "remove")
        defineOp(this, "move", 2..3) { a ->
            val args = MailScriptArguments.transfer(json(a[0]), json(a[1]), json(a.getOrNull(2)))
            transferResult { call(MailContract.OP_MESSAGES_MOVE, args) }
        }
        defineOp(this, "copy", 2..3) { a ->
            val args = MailScriptArguments.transfer(json(a[0]), json(a[1]), json(a.getOrNull(2)))
            transferResult { call(MailContract.OP_MESSAGES_COPY, args) }
        }
        defineOp(this, "delete", 1..2) { a ->
            val args = MailScriptArguments.delete(json(a[0]), json(a.getOrNull(1)))
            uidsResult { call(MailContract.OP_MESSAGES_DELETE, args) }
        }
        defineOp(this, "expunge", 0..1) { a ->
            val args = MailScriptArguments.expunge(json(a.getOrNull(0)))
            field("count") { call(MailContract.OP_MESSAGES_EXPUNGE, args) }
        }
        defineOp(this, "append", 2..3) { a ->
            val plan = MailScriptArguments.append(json(a[0]), json(a[1]), json(a.getOrNull(2)), ::resolvePath, ::sizeOf)
            val work: MailWork = { _ -> (outgoingCall(MailContract.OP_MESSAGES_APPEND, plan) as? Map<*, *>)?.get("uid") }
            work
        }
        defineFunction(this, "watch") { args -> ensureArgumentsLengthInRange(args, 0..2) { a -> watch(a.getOrNull(0), a.getOrNull(1)) } }
    }

    override fun getClassName(): String = "MailClient"

    val isClosed: Boolean
        get() = closed.get()

    /** Watches of this client that are still open. zh-CN: 本客户端仍打开着的监听数. */
    val watchCount: Int
        get() = watches.size

    fun describe(): String = "MailClient { ${options.describe()} }"

    /** The `session.test` document as a plain value. zh-CN: `session.test` 文档的普通值. */
    internal suspend fun test(): Any? = MailScriptValues.toPlain(call(MailContract.OP_SESSION_TEST))

    /** One operation of the contract; the debug trace is forwarded when the account asked for it. zh-CN: 契约的一个操作; 账户要求时转发调试摘要. */
    internal suspend fun call(op: String, argsJson: String? = null): String =
        session.call(op, argsJson, onProgress = progressListener)

    private suspend fun call(op: String, args: JsonObject): String = call(op, args.toString())

    // ------------------------------------------------------------------ sync / async bridges

    /**
     * Runs [work] on the script thread until it completes and returns its outcome as a script
     * value; failures reach the script as `MailError`.
     * zh-CN: 在脚本线程上运行 [work] 直到完成并把结果作为脚本值返回; 失败以 `MailError` 到达脚本.
     */
    internal fun sync(work: MailWork): Any? {
        try {
            ensureOpen()
            val value = runBlocking(scriptRuntime.coroutineContext) { work(null) }
            val produced = if (value is MailPromises.OnScriptThread) value.produce() else value
            return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, produced)
        } catch (t: Throwable) {
            MailJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** Runs [work] as a coroutine and returns the Promise of its outcome. zh-CN: 把 [work] 作为协程运行并返回其结果的 Promise. */
    internal fun async(work: MailWork): Any {
        runCatching { ensureOpen() }.exceptionOrNull()?.let { return MailPromises.rejected(scriptRuntime, it) }
        return MailPromises.launchWith(scriptRuntime) { dispatcher -> work(dispatcher) }
    }

    private fun syncOf(build: () -> MailWork): Any? = try {
        sync(build())
    } catch (t: Throwable) {
        MailJsErrors.throwInto(scriptRuntime, t)
    }

    private fun asyncOf(build: () -> MailWork): Any = try {
        async(build())
    } catch (t: Throwable) {
        MailPromises.rejected(scriptRuntime, t)
    }

    // ------------------------------------------------------------------ MailJsResults.Binding

    override fun load(message: Scriptable, options: Any?): Any? = syncOf { getWork(message, options) }

    override fun loadAsync(message: Scriptable, options: Any?): Any = asyncOf { getWork(message, options) }

    override fun download(attachment: Scriptable, target: Any?, options: Any?): Any? = syncOf { downloadWork(attachment, target, options) }

    override fun downloadAsync(attachment: Scriptable, target: Any?, options: Any?): Any = asyncOf { downloadWork(attachment, target, options) }

    // ------------------------------------------------------------------ work builders

    private fun getWork(target: Any?, options: Any?): MailWork {
        val args = MailScriptArguments.get(json(target), json(options))
        return message { call(MailContract.OP_MESSAGES_GET, args) }
    }

    private fun downloadWork(attachment: Any?, target: Any?, options: Any?): MailWork {
        val plan = MailScriptArguments.download(json(attachment), json(options))
        val onProgress = progressCallback(options)
        val targetPath = targetPath(target)
        return { dispatcher -> transfer(MailContract.OP_ATTACHMENTS_DOWNLOAD, plan, targetPath, onProgress, dispatcher) }
    }

    private fun rawWork(target: Any?, filePath: Any?, options: Any?): MailWork {
        val plan = MailScriptArguments.raw(json(target), json(options))
        val onProgress = progressCallback(options)
        val targetPath = targetPath(filePath)
        return { dispatcher -> transfer(MailContract.OP_MESSAGES_RAW, plan, targetPath, onProgress, dispatcher) }
    }

    private fun folderStatus(path: JsonElement?): MailWork {
        val args = MailScriptArguments.folderStatus(path)
        return plain { call(MailContract.OP_FOLDERS_STATUS, args) }
    }

    private fun createFolder(path: JsonElement?): MailWork {
        val args = MailScriptArguments.createFolder(path)
        return plain { call(MailContract.OP_FOLDERS_CREATE, args) }
    }

    private fun deleteFolder(path: JsonElement?): MailWork {
        val args = MailScriptArguments.deleteFolder(path)
        return plain { call(MailContract.OP_FOLDERS_DELETE, args) }
    }

    private fun renameFolder(path: JsonElement?, newPath: JsonElement?): MailWork {
        val args = MailScriptArguments.renameFolder(path, newPath)
        return plain { call(MailContract.OP_FOLDERS_RENAME, args) }
    }

    private fun outgoing(op: String, plan: MailScriptArguments.Outgoing): MailWork = { _ -> outgoingCall(op, plan) }

    /** `mail.send` / `messages.append`: the attachment files travel as read-only descriptors. zh-CN: `mail.send` / `messages.append`: 附件文件以只读描述符随行. */
    private suspend fun outgoingCall(op: String, plan: MailScriptArguments.Outgoing): Any? {
        val source = MailAttachmentSource.open(plan.attachmentPaths)
        return source.use {
            val result = session.call(
                op,
                plan.args.toString(),
                descriptors = source.descriptorArray.takeIf { it.isNotEmpty() },
                transferBytes = source.totalBytes.takeIf { it > 0L },
                onProgress = progressListener,
            )
            MailScriptValues.toPlain(result)
        }
    }

    /**
     * `attachments.download` / `messages.raw` into a file; returns the final path. The attachment
     * `size` is the encoded part size the server reported, so it only budgets the call timeout and
     * the progress total; the sink checks the ceiling on the decoded bytes it receives.
     * zh-CN: `attachments.download` / `messages.raw` 落盘; 返回最终路径. 附件 `size` 是服务器报告的编码后分部大小, 因此只用于调用超时预算与进度
     * 总量; 接收端按收到的解码字节检查上限.
     */
    private suspend fun transfer(op: String, plan: MailScriptArguments.Download, targetPath: String, onProgress: Callable?, dispatcher: MailAsyncDispatcher?): String {
        val sink = MailAttachmentSink.create(targetPath, plan.fileName, expectedSize = null, overwrite = plan.overwrite)
        return MailDownloads.download(session, op, plan.args.toString(), sink, plan.size, progressRelay(plan.size, onProgress, dispatcher)).path
    }

    private fun progressRelay(total: Long?, onProgress: Callable?, dispatcher: MailAsyncDispatcher?): ((MailJson.Progress) -> Unit)? {
        val debug = progressListener
        val relay: ((MailJson.Progress) -> Unit)? = if (onProgress != null && dispatcher != null) {
            { progress ->
                dispatcher.dispatchValues(arrayOf(progress.transferred, progress.total ?: total)) { mapped ->
                    RhinoUtils.callFunction(scriptRuntime, onProgress, scriptRuntime.topLevelScope, null, mapped)
                }
            }
        } else null
        if (debug == null && relay == null) return null
        return { progress ->
            debug?.invoke(progress)
            if (progress.hasTransfer) relay?.invoke(progress)
        }
    }

    private fun plain(block: suspend () -> String): MailWork = { _ -> MailScriptValues.toPlain(block()) }

    private fun field(name: String, block: suspend () -> String): MailWork = { _ -> (MailScriptValues.toPlain(block()) as? Map<*, *>)?.get(name) }

    private fun uidsResult(block: suspend () -> String): MailWork = { _ -> (MailScriptValues.toPlain(block()) as? Map<*, *>)?.get("uids") ?: emptyList<Any?>() }

    /** `messages.move` / `messages.copy`: the target UIDs when the server reported them, else `true`. zh-CN: `messages.move` / `messages.copy`: 服务器报告时为目标 UID, 否则 `true`. */
    private fun transferResult(block: suspend () -> String): MailWork = { _ -> (MailScriptValues.toPlain(block()) as? Map<*, *>)?.get("uids") ?: true }

    private fun message(block: suspend () -> String): MailWork = { _ ->
        val plain = MailScriptValues.toPlain(block())
        MailPromises.OnScriptThread { RhinoUtils.withRhinoContext(scriptRuntime) { cx -> MailJsResults.message(cx, scriptRuntime.topLevelScope, plain, this) } }
    }

    private fun messages(block: suspend () -> String): MailWork = { _ ->
        val plain = MailScriptValues.toPlain(block())
        MailPromises.OnScriptThread { RhinoUtils.withRhinoContext(scriptRuntime) { cx -> MailJsResults.messages(cx, scriptRuntime.topLevelScope, plain, this) } }
    }

    private fun searchResult(block: suspend () -> String): MailWork = { _ ->
        val plain = MailScriptValues.toPlain(block())
        MailPromises.OnScriptThread { RhinoUtils.withRhinoContext(scriptRuntime) { cx -> MailJsResults.search(cx, scriptRuntime.topLevelScope, plain, this) } }
    }

    // ------------------------------------------------------------------ script value conversion

    /** A script argument as a Gson tree, like `JSON.stringify` would see it; `undefined` and null become null. zh-CN: 脚本参数的 Gson 树, 如 `JSON.stringify` 所见; `undefined` 与 null 成为 null. */
    private fun json(value: Any?): JsonElement? = when (value) {
        null, is Undefined -> null
        is CharSequence -> JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Scriptable -> {
            val serialized = js_json_stringify(value)
            if (serialized is Undefined) throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible, got ${value.jsBrief()}")
            try {
                JsonParser.parseString(Context.toString(serialized))
            } catch (e: RuntimeException) {
                throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible: ${e.message.orEmpty()}")
            }
        }
        else -> throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "unsupported argument ${value.jsBrief()}")
    }

    private fun progressCallback(options: Any?): Callable? {
        val holder = options as? Scriptable ?: return null
        val value = ScriptableObject.getProperty(holder, MailScriptArguments.ON_PROGRESS)
        if (value == Scriptable.NOT_FOUND || value.isJsNullish()) return null
        return value as? Callable ?: throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "'options.onProgress' must be a function")
    }

    /** The download target: the script's working directory unless a directory or file path is given. zh-CN: 下载目标: 未给目录或文件路径时为脚本工作目录. */
    private fun targetPath(value: Any?): String {
        if (value.isJsNullish()) {
            return scriptRuntime.files.cwd() ?: throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "no working directory: pass a target directory or file path")
        }
        val text = (value as? CharSequence)?.toString()
            ?: throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "target must be a directory or file path string, got ${value.jsBrief()}")
        if (text.isBlank()) throw MailJsErrors.failure(MailErrorCodes.INVALID_ARGUMENT, "target path must not be blank")
        return resolvePath(text)
    }

    private fun resolvePath(path: String): String = scriptRuntime.files.path(path) ?: path

    private fun sizeOf(path: String): Long? = File(path).takeIf { it.isFile }?.length()

    // ------------------------------------------------------------------ watches

    /**
     * `client.watch(folder?, options?)` (roadmap P5): opens the plugin watch synchronously (the
     * plugin's refusal throws `MailError`) and returns the [MailWatch] whose events arrive on
     * this script thread; the dispatcher behind it keeps the script alive until the watch closed.
     * zh-CN: `client.watch(folder?, options?)` (路线图 P5): 同步打开插件监听 (插件拒绝时抛 `MailError`), 返回事件在本脚本线程到达的
     * [MailWatch]; 其背后的分发器让脚本活到监听关闭.
     */
    private fun watch(folder: Any?, options: Any?): Any? {
        val plan = MailScriptArguments.watch(json(folder), json(options))
        ensureOpen()
        val controller = MailWatch.Controller(MailAsyncDispatcher(scriptRuntime), scriptRuntime.bridges, plan.options.folder, plan.options.mode)
        val runner = MailWatchRunner(
            scope = scriptRuntime.coroutineScope,
            session = session,
            options = plan.options,
            reconnect = plan.reconnect,
            generations = generations::incrementAndGet,
            controller = controller,
            decorate = { value -> RhinoUtils.withRhinoContext(scriptRuntime) { cx -> MailJsResults.message(cx, scriptRuntime.topLevelScope, value, this) } },
            convertError = { error -> RhinoUtils.withRhinoContext(scriptRuntime) { cx -> MailJsErrors.newMailError(cx, scriptRuntime.topLevelScope, error, null) } },
            onFinished = { finished -> watches.remove(finished) },
        )
        watches.add(runner)
        try {
            return sync { _ ->
                runner.open()
                controller.watch
            }
        } catch (t: Throwable) {
            watches.remove(runner)
            controller.cancelSilently()
            throw t
        }
    }

    // ------------------------------------------------------------------ folder objects

    /** `client.folder(path)`: `path`, `name`, `status()`, `create()`, `delete()`, `rename(newPath)` and their `Async` forms. zh-CN: `client.folder(path)`: `path`, `name`, `status()`, `create()`, `delete()`, `rename(newPath)` 及其 `Async` 形态. */
    private fun folderObject(path: String): NativeObject {
        val target = RhinoUtils.newNativeObject()
        val pathJson = JsonPrimitive(path)
        target.defineProperty("path", path, READONLY or PERMANENT)
        target.defineProperty("name", path.substringAfterLast('/'), READONLY or PERMANENT)
        defineOp(target, "status", 0..0) { folderStatus(pathJson) }
        defineOp(target, "create", 0..0) { createFolder(pathJson) }
        defineOp(target, "delete", 0..0) { deleteFolder(pathJson) }
        defineOp(target, "rename", 1..1) { a -> renameFolder(pathJson, json(a[0])) }
        defineFunction(target, "toString") { path }
        return target
    }

    // ------------------------------------------------------------------ lifecycle

    /** Closes the plugin session; the script sees a `MailError` when the plugin refused. zh-CN: 关闭插件会话; 插件拒绝时脚本看到 `MailError`. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        service.untrack(this)
        watches.toList().forEach { runner -> runCatching { runner.stop(MailWatch.REASON_CLIENT_CLOSED) } }
        try {
            runBlocking(scriptRuntime.coroutineContext) { session.close() }
        } catch (t: Throwable) {
            MailJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** Drops a client whose open failed: nothing to close on the plugin side. zh-CN: 丢弃打开失败的客户端: 插件侧没有可关闭的东西. */
    internal fun discard() {
        closed.set(true)
        service.untrack(this)
        watches.toList().forEach { runner -> runCatching { runner.cancelSilently() } }
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(CLOSE_ON_EXIT_TIMEOUT_MS) { session.close() } }
    }

    /** Script exit: close without the runtime context (already cancelled) and without throwing; the session's close stops the watches. zh-CN: 脚本退出: 不经运行时上下文 (已取消) 且不抛出地关闭; 会话关闭会停止监听. */
    internal fun closeOnExit() {
        if (!closed.compareAndSet(false, true)) return
        watches.toList().forEach { runner -> runCatching { runner.cancelSilently() } }
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(CLOSE_ON_EXIT_TIMEOUT_MS) { session.close() } }
    }

    private fun ensureOpen() {
        if (closed.get()) throw MailJsErrors.failure(MailErrorCodes.SESSION_CLOSED, "mail client closed by the caller")
    }

    // ------------------------------------------------------------------ definition helpers

    /** Defines `name` (sync) and `nameAsync` on [holder]; argument errors throw or reject as `MailError`. zh-CN: 在 [holder] 上定义 `name` (同步) 与 `nameAsync`; 参数错误以 `MailError` 抛出或拒绝. */
    private fun defineOp(holder: NativeObject, name: String, arity: IntRange, build: (Array<Any?>) -> MailWork) {
        defineFunction(holder, name) { args -> ensureArgumentsLengthInRange(args, arity) { a -> sync(build(a)) } }
        defineFunction(holder, "${name}Async") { args ->
            val work = try {
                ensureArgumentsLengthInRange(args, arity) { a -> build(a) }
            } catch (t: Throwable) {
                return@defineFunction MailPromises.rejected(scriptRuntime, t)
            }
            async(work)
        }
    }

    private fun defineFlagShortcut(name: String, flag: String, mode: String) {
        defineOp(this, name, 1..2) { a ->
            val args = MailScriptArguments.flags(json(a[0]), JsonPrimitive(flag), JsonPrimitive(mode), json(a.getOrNull(1)))
            uidsResult { call(MailContract.OP_MESSAGES_SET_FLAGS, args) }
        }
    }

    /** Argument guards and host failures leave every method as `MailError`. zh-CN: 参数守卫与宿主失败以 `MailError` 离开每个方法. */
    private fun defineFunction(holder: NativeObject, name: String, body: (Array<out Any?>) -> Any?) {
        val guarded: (Array<out Any?>) -> Any? = { args ->
            try {
                body(args)
            } catch (t: Throwable) {
                MailJsErrors.throwInto(scriptRuntime, t)
            }
        }
        holder.defineProperty(name, newBaseFunction(scriptRuntime.topLevelScope, name, guarded, NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
    }

    private companion object {
        const val CLOSE_ON_EXIT_TIMEOUT_MS = 3_000L
    }
}
