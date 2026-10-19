package org.autojs.autojs.runtime.api.augment.shell

import androidx.annotation.GuardedBy
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.shell.ProcessTree
import org.autojs.autojs.runtime.api.shell.ShellLauncher
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The script object returned by `shell.spawn(command, options?)` and `shizuku.spawn(command, options?)`: one command
 * line running as a process, with the properties `pid`, `command`, `mode` and `exitCode`, the methods `isAlive()`,
 * `readLine()`, `readErrorLine()`, `write(text)`, `writeLine(text)`, `closeInput()`, `waitFor(timeout?)`, `kill()`,
 * `getInputStream()`, `getErrorStream()`, `getOutputStream()`, the listener methods of an EventEmitter, and the events
 * `output(line)`, `errorOutput(line)` and `exit(code)`. Properties and results are plain JavaScript values.
 *
 * Each of stdout and stderr is read in one way only, fixed by the first use: line by line (`readLine` / `readErrorLine`,
 * blocking the caller), as a raw stream, or as events. Events reach the listeners on the thread that registered them,
 * through its event loop, and keep the script alive while listeners exist and the process runs; `exit` comes after the
 * last line. When listeners fall behind, reading pauses (the process then blocks on its full pipe) instead of dropping
 * lines. The process ends with the script that spawned it.
 * zh-CN: `shell.spawn(command, options?)` 与 `shizuku.spawn(command, options?)` 返回的脚本对象: 以进程运行的一条命令行, 带属性 `pid`, `command`, `mode` 与
 * `exitCode`, 方法 `isAlive()`, `readLine()`, `readErrorLine()`, `write(text)`, `writeLine(text)`, `closeInput()`, `waitFor(timeout?)`, `kill()`,
 * `getInputStream()`, `getErrorStream()`, `getOutputStream()`, EventEmitter 的监听器方法, 以及事件 `output(line)`, `errorOutput(line)` 与 `exit(code)`.
 * 属性与返回值均为普通 JavaScript 值.
 * stdout 与 stderr 各自只能以一种方式读取, 由首次使用决定: 逐行读取 (`readLine` / `readErrorLine`, 阻塞调用方), 原始流, 或事件. 事件经注册它的线程的
 * 事件循环交给监听器, 在有监听器且进程运行期间让脚本保持存活; `exit` 在最后一行之后到达. 监听器处理不过来时暂停读取 (进程随后在写满的管道上阻塞), 而不是
 * 丢弃行. 进程随启动它的脚本一同结束.
 */
class ShellProcess internal constructor(
    private val scriptRuntime: ScriptRuntime,
    private val command: String,
    private val process: Process,
    options: ShellLauncher.Options,
) : NativeObject(), Closeable {

    private enum class Claim(val description: String) {
        NONE(""),
        LINES("readLine() / readErrorLine()"),
        RAW("getInputStream() / getErrorStream()"),
        EVENTS("on('output') / on('errorOutput')"),
    }

    private inner class Dispatch {
        val dispatcher = ScriptAsyncDispatcher(scriptRuntime)
    }

    private val emitter = EventEmitter(scriptRuntime.bridges)
    private val mode = options.mode
    private val pid = ProcessTree.pidOf(process)
    private val lock = ReentrantLock()
    private val drained = lock.newCondition()
    private val writeLock = Any()
    private val closed = AtomicBoolean(false)

    @GuardedBy("lock")
    private var stdoutClaim = Claim.NONE

    @GuardedBy("lock")
    private var stderrClaim = Claim.NONE

    @GuardedBy("lock")
    private var stdoutReader: BufferedReader? = null

    @GuardedBy("lock")
    private var stderrReader: BufferedReader? = null

    @GuardedBy("lock")
    private var dispatch: Dispatch? = null

    @GuardedBy("lock")
    private val pendingLines = ArrayDeque<Pair<String, String>>()

    @GuardedBy("lock")
    private var pendingChars = 0L

    @GuardedBy("lock")
    private var drainScheduled = false

    @GuardedBy("lock")
    private var openPumps = 0

    @GuardedBy("lock")
    private var exitWatcherStarted = false

    @GuardedBy("lock")
    private var exitDelivered = false

    @Volatile
    private var knownExitCode: Int? = null

    private val exitCode: Int?
        get() = knownExitCode ?: try {
            process.exitValue().also { knownExitCode = it }
        } catch (_: IllegalThreadStateException) {
            null
        }

    init {
        RhinoUtils.initNativeObjectPrototype(scriptRuntime.topLevelScope, this)
        defineProperty("pid", { pid }, null, READONLY or PERMANENT)
        defineProperty("command", { command }, null, READONLY or PERMANENT)
        defineProperty("mode", { mode }, null, READONLY or PERMANENT)
        defineProperty("exitCode", { exitCode }, null, READONLY or PERMANENT)

        function("isAlive") { isAlive() }
        function("readLine") { readLineOf(isError = false) }
        function("readErrorLine") { readLineOf(isError = true) }
        function("write") { args -> write(args.getOrNull(0), lineEnd = false) }
        function("writeLine") { args -> write(args.getOrNull(0), lineEnd = true) }
        function("closeInput") {
            runCatching { process.outputStream.close() }
            this
        }
        function("waitFor") { args -> waitFor(args.getOrNull(0)) }
        function("kill") {
            val wasAlive = isAlive()
            terminate()
            wasAlive
        }
        function("getInputStream") {
            lock.withLock { claim(isError = false, wanted = Claim.RAW) }
            Context.javaToJS(process.inputStream, scriptRuntime.topLevelScope)
        }
        function("getErrorStream") {
            lock.withLock { claim(isError = true, wanted = Claim.RAW) }
            Context.javaToJS(process.errorStream, scriptRuntime.topLevelScope)
        }
        function("getOutputStream") { Context.javaToJS(process.outputStream, scriptRuntime.topLevelScope) }

        listenerFunction("on") { event, listener -> emitter.on(event, listener) }
        listenerFunction("addListener") { event, listener -> emitter.on(event, listener) }
        listenerFunction("once") { event, listener -> emitter.once(event, listener) }
        listenerFunction("prependListener") { event, listener -> emitter.prependListener(event, listener) }
        listenerFunction("prependOnceListener") { event, listener -> emitter.prependOnceListener(event, listener) }
        listenerFunction("off") { event, listener -> emitter.removeListener(event, listener) }
        listenerFunction("removeListener") { event, listener -> emitter.removeListener(event, listener) }
        function("removeAllListeners") { args ->
            when (val event = args.getOrNull(0)) {
                null, is org.mozilla.javascript.Undefined -> emitter.removeAllListeners()
                else -> emitter.removeAllListeners(coerceString(event))
            }
            listenersChanged()
            this
        }
        function("listenerCount") { args -> emitter.listenerCount(coerceString(args.getOrNull(0))) }
        function("toString") { describe() }

        scriptRuntime.closeableManager.add(this)
    }

    override fun getClassName() = "ShellProcess"

    override fun toString() = describe()

    /** Rhino only lets scripts catch Rhino exceptions thrown by a native lambda, so other failures are wrapped. zh-CN: Rhino 只允许脚本捕获原生 lambda 抛出的 Rhino 异常, 因此包装其他异常. */
    private fun function(name: String, body: (Array<out Any?>) -> Any?) = defineProperty(
        name,
        newBaseFunction(scriptRuntime.topLevelScope, name, { args ->
            try {
                body(args).let { if (it === Unit) UNDEFINED else it }
            } catch (e: RhinoException) {
                throw e
            } catch (t: Throwable) {
                if (ScriptInterruptedException.causedByInterrupt(t)) throw ScriptInterruptedException(t)
                throw WrappedRuntimeException("ShellProcess.$name: ${t.message ?: t}", t)
            }
        }, NOT_CONSTRUCTABLE),
        READONLY or PERMANENT,
    )

    /** Adding a listener may claim a stream; a refused claim takes the listener back. zh-CN: 添加监听器可能占用流; 占用被拒时撤回该监听器. */
    private fun listenerFunction(name: String, change: (String, Callable) -> Unit) = function(name) { args ->
        val event = args.getOrNull(0)
        val listener = args.getOrNull(1)
        require(event is CharSequence && listener is Callable) { "$name requires an event name and a listener instead of ${event.jsBrief()}, ${listener.jsBrief()}" }
        val eventName = event.toString()
        require(eventName in EVENTS) { "Unknown ShellProcess event \"$eventName\", expected one of ${EVENTS.joinToString(", ")}" }
        change(eventName, listener)
        try {
            listenersChanged()
        } catch (t: Throwable) {
            emitter.removeListener(eventName, listener)
            throw t
        }
        this
    }

    private fun isAlive(): Boolean = exitCode == null

    private fun write(text: Any?, lineEnd: Boolean): ShellProcess {
        val bytes = (coerceString(text, "") + if (lineEnd) "\n" else "").toByteArray(Charsets.UTF_8)
        synchronized(writeLock) {
            try {
                process.outputStream.apply {
                    write(bytes)
                    flush()
                }
            } catch (e: IOException) {
                throw IllegalStateException("The input of this process is closed", e)
            }
        }
        return this
    }

    /**
     * The exit code once the process exited. With a timeout (milliseconds), null if it still runs by then.
     * zh-CN: 进程退出后返回退出码. 指定超时 (毫秒) 时, 若届时仍在运行则返回 null.
     */
    private fun waitFor(timeout: Any?): Int? {
        if (timeout.isJsNullish()) {
            return try {
                process.waitFor().also { knownExitCode = it }
            } catch (_: InterruptedException) {
                throw ScriptInterruptedException()
            }
        }
        val millis = coerceLongNumber(timeout)
        require(millis >= 0) { "Argument timeout must not be negative" }
        val exited = try {
            ProcessTree.waitFor(process, millis)
        } catch (_: InterruptedException) {
            throw ScriptInterruptedException()
        }
        return if (exited) exitCode else null
    }

    private fun describe(): String = buildString {
        append("ShellProcess { pid: ").append(pid)
        append(", mode: ").append(mode)
        append(", command: ").append(command.take(MAX_COMMAND_PREVIEW).replace("\n", "; "))
        if (command.length > MAX_COMMAND_PREVIEW) append("...")
        append(", alive: ").append(isAlive())
        exitCode?.let { append(", exitCode: ").append(it) }
        append(" }")
    }

    // ------------------------------------------------------------------ events

    private fun emit(event: String, value: Any?) {
        emitter.emit(event, value)
        // EventEmitter drops once listeners before invoking them, without removeListener.
        // zh-CN: EventEmitter 在调用 once 监听器之前将其移除, 不经过 removeListener.
        listenersChanged()
    }

    /** Runs on the script thread, where listeners are added and removed. zh-CN: 在脚本线程上运行, 监听器在那里增删. */
    private fun listenersChanged() {
        val wantsOutput = emitter.listenerCount(EVENT_OUTPUT) > 0
        val wantsErrorOutput = emitter.listenerCount(EVENT_ERROR_OUTPUT) > 0
        if (!wantsOutput && !wantsErrorOutput && emitter.listenerCount(EVENT_EXIT) == 0) {
            teardownDispatch()
            return
        }
        val startWatcher = lock.withLock {
            if (exitDelivered || closed.get()) return
            if (wantsOutput) claim(isError = false, wanted = Claim.EVENTS)
            if (wantsErrorOutput) claim(isError = true, wanted = Claim.EVENTS)
            if (dispatch == null) dispatch = Dispatch()
            !exitWatcherStarted.also { exitWatcherStarted = true }
        }
        if (startWatcher) startThread("exit") { watchExit() }
        maybeDeliverExit()
    }

    // ------------------------------------------------------------------ reading

    @GuardedBy("lock")
    private fun claim(isError: Boolean, wanted: Claim) {
        val current = if (isError) stderrClaim else stdoutClaim
        if (current == wanted) return
        if (current != Claim.NONE) {
            val stream = if (isError) "stderr" else "stdout"
            throw IllegalStateException(
                "The $stream of this process is already read through ${current.description}; " +
                    "each stream is read through one of readLine(), getInputStream() and on() only"
            )
        }
        if (isError) stderrClaim = wanted else stdoutClaim = wanted
        if (wanted == Claim.EVENTS) {
            openPumps++
            startThread(if (isError) "stderr" else "stdout") { pump(isError) }
        }
    }

    private fun readLineOf(isError: Boolean): String? {
        val reader = lock.withLock {
            claim(isError, Claim.LINES)
            when {
                isError -> stderrReader ?: process.errorStream.bufferedReader(Charsets.UTF_8).also { stderrReader = it }
                else -> stdoutReader ?: process.inputStream.bufferedReader(Charsets.UTF_8).also { stdoutReader = it }
            }
        }
        return try {
            reader.readLine()
        } catch (e: IOException) {
            if (closed.get() || !isAlive()) null else throw e
        }
    }

    private fun pump(isError: Boolean) {
        val event = if (isError) EVENT_ERROR_OUTPUT else EVENT_OUTPUT
        try {
            val reader = (if (isError) process.errorStream else process.inputStream).bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                enqueue(event, line)
            }
        } catch (_: IOException) {
            // Closed by kill() or by the script's exit.
            // zh-CN: 已被 kill() 或脚本退出关闭.
        } catch (_: InterruptedException) {
            // Script exit.
            // zh-CN: 脚本退出.
        } finally {
            lock.withLock { openPumps-- }
            maybeDeliverExit()
        }
    }

    /** Pump thread: queues [line]; waits while the backlog is full, drops it when nobody listens. zh-CN: 泵线程: 将 [line] 入队; 积压已满时等待, 无人监听时丢弃. */
    private fun enqueue(event: String, line: String) {
        val current = lock.withLock {
            while (pendingChars >= MAX_PENDING_CHARS && dispatch != null && !closed.get()) drained.await(BACKPRESSURE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            val current = dispatch ?: return
            pendingLines.addLast(event to line)
            pendingChars += line.length + LINE_OVERHEAD_CHARS
            if (drainScheduled) return
            drainScheduled = true
            current
        }
        val scheduled = runCatching { current.dispatcher.dispatchValues(emptyArray()) { drainLines() } }.getOrDefault(false)
        if (!scheduled) lock.withLock {
            drainScheduled = false
            pendingLines.clear()
            pendingChars = 0L
            drained.signalAll()
        }
    }

    /** Script thread: emits every queued line. zh-CN: 脚本线程: 发出所有排队的行. */
    private fun drainLines() {
        var completed = false
        try {
            while (true) {
                val batch = lock.withLock {
                    if (pendingLines.isEmpty()) {
                        drainScheduled = false
                        drained.signalAll()
                        null
                    } else {
                        pendingLines.toList().also {
                            pendingLines.clear()
                            pendingChars = 0L
                            drained.signalAll()
                        }
                    }
                } ?: break
                batch.forEach { (event, line) -> emit(event, line) }
            }
            completed = true
        } finally {
            // A throwing listener loses the rest of its batch; the next line schedules a new drain.
            // zh-CN: 监听器抛出异常时丢失该批其余的行; 下一行会安排新的排空.
            if (!completed) lock.withLock {
                drainScheduled = false
                drained.signalAll()
            }
        }
    }

    private fun watchExit() {
        try {
            knownExitCode = process.waitFor()
        } catch (_: InterruptedException) {
            return
        }
        maybeDeliverExit()
    }

    /** `exit` goes out once the code is known and the event pumps drained; it ends the dispatcher. zh-CN: 退出码已知且事件泵排空后发出 `exit`; 它结束分发器. */
    private fun maybeDeliverExit() {
        val (current, code) = lock.withLock {
            val code = exitCode
            val current = dispatch
            if (exitDelivered || code == null || openPumps > 0 || current == null) return
            exitDelivered = true
            dispatch = null
            current to code
        }
        runCatching {
            current.dispatcher.terminalValues(arrayOf(code)) { mapped ->
                drainLines()
                emit(EVENT_EXIT, mapped[0])
            }
        }
    }

    /** No listener left, or the script exits: stop deliveries silently and let the script end. zh-CN: 再无监听器或脚本退出: 静默停止投递, 让脚本得以结束. */
    private fun teardownDispatch() {
        val current = lock.withLock {
            dispatch.also {
                dispatch = null
                pendingLines.clear()
                pendingChars = 0L
                drainScheduled = false
                drained.signalAll()
            }
        }
        current?.dispatcher?.cancelSilently()
    }

    // ------------------------------------------------------------------ ending

    /** Script exit: ends the process without a script-visible event. zh-CN: 脚本退出: 结束进程, 不产生脚本可见事件. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        teardownDispatch()
        terminate()
        scriptRuntime.closeableManager.remove(this)
    }

    /** Ends the process and the processes it started; listeners still get the remaining lines and `exit`. zh-CN: 结束进程及其启动的进程; 监听器仍会收到剩余的行与 `exit`. */
    private fun terminate() {
        ProcessTree.terminate(process)
        runCatching { process.outputStream.close() }
        // Readers get EOF once the process is gone. A root command behind su keeps the pipes open, so they are closed
        // after a short grace: the readers end, and the command gets SIGPIPE on its next write.
        // zh-CN: 进程结束后读取方会读到 EOF. su 背后的 root 命令会让管道保持打开, 因此稍候关闭管道: 读取方随之结束, 该命令在下一次写入时收到 SIGPIPE.
        startThread("close") {
            val deadline = System.nanoTime() + EXIT_GRACE_MILLIS * 1_000_000L
            while (lock.withLock { openPumps > 0 } && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }

    private fun startThread(name: String, block: () -> Unit) {
        Thread({
            try {
                block()
            } catch (_: InterruptedException) {
                // Script exit.
                // zh-CN: 脚本退出.
            }
        }, "ShellProcess-$name").apply { isDaemon = true }.start()
    }

    companion object {
        const val EVENT_OUTPUT = "output"
        const val EVENT_ERROR_OUTPUT = "errorOutput"
        const val EVENT_EXIT = "exit"
        private val EVENTS = setOf(EVENT_OUTPUT, EVENT_ERROR_OUTPUT, EVENT_EXIT)

        private const val POLL_MILLIS = 20L
        private const val EXIT_GRACE_MILLIS = 500L
        private const val BACKPRESSURE_WAIT_MILLIS = 200L
        private const val MAX_PENDING_CHARS = 1L shl 20
        private const val LINE_OVERHEAD_CHARS = 16L
        private const val MAX_COMMAND_PREVIEW = 80

        internal fun start(scriptRuntime: ScriptRuntime, command: String, options: ShellLauncher.Options): ShellProcess =
            ShellProcess(scriptRuntime, command, ShellLauncher.startCommand(command, options), options)
    }

}
