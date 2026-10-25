package org.autojs.autojs.runtime.api.modules.installer

import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerErrorMapper
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.core.plugin.installer.InstallerSessionListener
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.installer.InstallerOperation
import org.autojs.autojs.runtime.api.installer.InstallerScriptArguments
import org.autojs.autojs.runtime.api.installer.InstallerScriptOptions
import org.autojs.autojs.runtime.api.installer.InstallerScriptValues
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.runtime.api.modules.ScriptDispatchQueueOverflowException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** A session is fully constructed before its controller starts; Binder never accesses a Rhino value. */
internal class InstallSessionNativeObject(
    private val runtime: ScriptRuntime,
    service: InstallerService,
    plan: InstallerScriptArguments.SourcePlan,
    options: InstallerScriptOptions,
) : NativeObject(), Closeable {
    private val originThread = Thread.currentThread()
    private val closed = AtomicBoolean()
    private val localFailure = AtomicReference<InstallerError?>()
    private val emitter = EventEmitter(runtime.bridges)
    private val operation: InstallerOperation
    private var delivery: InstallerDispatch? = null
    private var waiting = false
    private var scriptResult: Any? = UNDEFINED

    private val listener = object : InstallerSessionListener {
        override fun onStage(stage: String, detail: InstallerJson.StageDetail) {
            val value = linkedMapOf<String, Any?>().apply {
                detail.index?.let { put("index", it) }
                detail.packageName?.let { put("packageName", it) }
            }.toMap()
            enqueue { it.dispatcher.dispatchValues(arrayOf(stage, value)) { values ->
                if (it.active && !closed.get()) emit("stage", *values)
            } }
        }

        override fun onProgress(progress: Float, detail: InstallerJson.ProgressDetail) {
            val value = linkedMapOf<String, Any?>("bytesWritten" to (detail.bytesWritten ?: 0L), "totalBytes" to (detail.totalBytes ?: 0L)).apply {
                detail.index?.let { put("index", it) }
            }.toMap()
            enqueue { it.dispatcher.dispatchValues(arrayOf(progress.toDouble(), value)) { values ->
                if (it.active && !closed.get()) emit("progress", *values)
            } }
        }

        override fun onCompleted(completion: InstallerJson.Completion) {
            val plain = try { InstallerScriptValues.completion(completion, plan.isBatch) } catch (failure: Throwable) {
                failLocally(failure)
                return
            }
            enqueue { registration -> registration.dispatcher.terminalValues(arrayOf(plain)) { values ->
                try {
                    if (registration.active && !closed.get()) {
                        if (scriptResult === UNDEFINED) scriptResult = InstallerJsResults.decorate(runtime, values[0])
                        emit("complete", scriptResult)
                    }
                } finally { registration.finish() }
            } }
        }

        override fun onFailed(error: InstallerError) {
            enqueue { registration -> registration.dispatcher.terminalValues { _ ->
                try {
                    if (registration.active && !closed.get()) emitFailure(error)
                } finally { registration.finish() }
            } }
        }
    }

    init {
        RhinoUtils.initNativeObjectPrototype(runtime.topLevelScope, this)
        operation = service.newSession(plan, options, listener)
        defineProperty("id", { guarded { operation.id } }, null, READONLY or PERMANENT)
        defineProperty("state", { guarded { if (localFailure.get() != null) "failed" else operation.state } }, null, READONLY or PERMANENT)
        defineProperty("result", { guarded { resultValue() } }, null, READONLY or PERMANENT)

        for (name in listOf("on", "addListener", "once", "off", "removeListener", "prependListener", "prependOnceListener")) {
            function(name) { args ->
                InstallerJsArguments.count("InstallSession.$name", args, 2..2)
                val event = eventName(args[0])
                val callback = args[1] as? Callable ?: invalid("$name requires a listener function")
                when (name) {
                    "on", "addListener" -> emitter.on(event, callback)
                    "once" -> emitter.once(event, callback)
                    "prependListener" -> emitter.prependListener(event, callback)
                    "prependOnceListener" -> emitter.prependOnceListener(event, callback)
                    else -> emitter.removeListener(event, callback)
                }
                this
            }
        }
        function("removeAllListeners") { args ->
            InstallerJsArguments.count("InstallSession.removeAllListeners", args, 0..1)
            if (args.isEmpty()) emitter.removeAllListeners() else emitter.removeAllListeners(eventName(args[0]))
            this
        }
        function("listenerCount") { args ->
            InstallerJsArguments.count("InstallSession.listenerCount", args, 1..1)
            emitter.listenerCount(eventName(args[0]))
        }
        function("listeners") { args ->
            InstallerJsArguments.count("InstallSession.listeners", args, 1..1)
            RhinoUtils.toJsValue(runtime.topLevelScope, emitter.listeners(eventName(args[0])))
        }
        function("eventNames") { args ->
            InstallerJsArguments.count("InstallSession.eventNames", args, 0..0)
            RhinoUtils.toJsValue(runtime.topLevelScope, emitter.eventNames())
        }
        function("getMaxListeners") { args ->
            InstallerJsArguments.count("InstallSession.getMaxListeners", args, 0..0)
            emitter.maxListeners
        }
        function("setMaxListeners") { args ->
            InstallerJsArguments.count("InstallSession.setMaxListeners", args, 1..1)
            val number = args[0] as? Number ?: invalid("maximum listener count must be a non-negative integer")
            val value = number.toDouble()
            if (!value.isFinite() || value < 0 || value > Int.MAX_VALUE || value != number.toInt().toDouble()) invalid("maximum listener count must be a non-negative integer")
            emitter.setMaxListeners(number.toInt())
            this
        }
        function("emit") { args ->
            if (args.isEmpty()) invalid("emit requires an event name")
            emit(eventName(args[0]), *args.drop(1).toTypedArray())
        }
        function("cancel") { args ->
            InstallerJsArguments.count("InstallSession.cancel", args, 0..0)
            operation.cancel()
        }
        function("wait") { args ->
            InstallerJsArguments.count("InstallSession.wait", args, 0..1)
            val timeout = InstallerJsArguments.optional(args, 0)?.let { InstallerScriptOptions.timeout(it) }
            waitForResult(timeout)
        }
        delivery = InstallerPromises.dispatch(runtime, ::close)
        try {
            if (!closed.get()) operation.start()
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    override fun getClassName(): String = "InstallSession"

    private fun resultValue(): Any? {
        if (scriptResult === UNDEFINED) operation.result?.let { completion ->
            scriptResult = InstallerJsResults.toJs(runtime, InstallerScriptValues.completion(completion, operation.isBatch))
        }
        return scriptResult
    }

    private fun waitForResult(timeout: Long?): Any? {
        if (waiting) invalid("InstallSession.wait cannot be called recursively from its listeners")
        localFailure.get()?.let { throw InstallerPluginException(it) }
        waiting = true
        try {
            InstallerPromises.sync(runtime) { operation.await(timeout) }
            localFailure.get()?.let { throw InstallerPluginException(it) }
            return resultValue()
        } finally { waiting = false }
    }

    private fun enqueue(action: (InstallerDispatch) -> Unit) {
        val registration = delivery ?: return
        if (closed.get() || localFailure.get() != null || !registration.active) return
        try { action(registration) } catch (failure: ScriptDispatchQueueOverflowException) {
            failLocally(failure)
        } catch (_: Throwable) {
            close()
        }
    }

    private fun failLocally(failure: Throwable) {
        val error = InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.CALL)
        if (!localFailure.compareAndSet(null, error)) return
        val registration = delivery ?: return
        try {
            registration.dispatcher.replaceQueuedWithTerminalValues { _ ->
                try {
                    if (registration.active && !closed.get()) emitFailure(error)
                } finally { registration.finish() }
            }
            operation.cancel()
        } catch (_: Throwable) { close() }
    }

    private fun emitFailure(error: InstallerError) {
        if (error.code == InstallerErrorCodes.CANCELLED || error.code == InstallerErrorCodes.USER_CANCELLED) {
            emit("cancel")
        } else {
            val js = InstallerJsErrors.toJs(runtime, InstallerPluginException(error))
            if (!emit("error", js)) runtime.console.error("InstallerError [${error.code}]: ${error.message}")
        }
    }

    private fun emit(event: String, vararg args: Any?): Boolean = emitter.emit(event, *args)

    private fun function(name: String, body: (Array<out Any?>) -> Any?) {
        defineProperty(name, newBaseFunction(runtime.topLevelScope, name, { args -> guarded { body(args) } }, NOT_CONSTRUCTABLE), READONLY or PERMANENT)
    }

    private inline fun <T> guarded(body: () -> T): T {
        // Reject a foreign thread before doing even error-object conversion in this scope.
        check(Thread.currentThread() === originThread) { "InstallSession belongs to its creating script thread" }
        return try {
            if (closed.get()) throw InstallerJsErrors.failure(InstallerErrorCodes.CANCELLED, "installer script has stopped")
            body()
        } catch (failure: Throwable) { InstallerJsErrors.throwInto(runtime, failure) }
    }

    /** Never remove listeners here: EventEmitter.removeAllListeners itself can call JavaScript. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        delivery?.close()
        operation.cancelSilently()
    }

    private fun eventName(value: Any?): String = (value as? CharSequence)?.toString()?.takeIf { it.isNotEmpty() }
        ?: invalid("event name must be a non-empty string")

    private fun invalid(message: String): Nothing = throw InstallerJsErrors.failure(InstallerErrorCodes.INVALID_ARGUMENT, message)
}
