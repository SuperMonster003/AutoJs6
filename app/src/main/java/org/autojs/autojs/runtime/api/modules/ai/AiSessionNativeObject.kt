package org.autojs.autojs.runtime.api.modules.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiPluginMessage
import org.autojs.autojs.core.plugin.ai.AiPluginSession
import org.autojs.autojs.core.plugin.ai.AiPluginStreamRunner
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ai.AiPluginAskRuntime
import org.autojs.autojs.runtime.api.ai.AiPluginRouteException
import org.autojs.autojs.runtime.api.ai.AiPluginSessionTarget
import org.autojs.autojs.runtime.api.ai.AiPluginStreamRuntime
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.NativeObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Rhino-visible owner of one persistent plugin-target Conversation. */
internal class AiSessionNativeObject(
    private val scriptRuntime: ScriptRuntime,
    private val baseRequest: AiPluginAskRequest,
    private val onClosed: (AiSessionNativeObject) -> Unit,
) : NativeObject(), Closeable, AiPluginSessionTarget {

    private val closed = AtomicBoolean(false)
    private val tracked = AtomicBoolean(true)
    private val session = AtomicReference<AiPluginSession?>()
    private val askRuntime = AiPluginAskRuntime(
        runnerProvider = {
            AiPluginAskRunner { request, onOutcome ->
                requireSession().startTurn(
                    message = request.messages.single(),
                    streaming = false,
                    onOutcome = { outcome ->
                        try {
                            onOutcome(outcome)
                        } finally {
                            retireIfCoreClosed()
                        }
                    },
                    onStreamEvent = null,
                )
            }
        },
        dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
    )
    private val streamRuntime = AiPluginStreamRuntime(
        runnerProvider = {
            AiPluginStreamRunner { request, onEvent ->
                requireSession().startTurn(
                    message = request.messages.single(),
                    streaming = true,
                    onOutcome = null,
                    onStreamEvent = { event ->
                        try {
                            onEvent(event)
                        } finally {
                            retireIfCoreClosed()
                        }
                    },
                )
            }
        },
        dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
        bridges = scriptRuntime.bridges,
    )

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineFunction("ask") { args ->
            ensureOpen()
            askRuntime.ask(turnRequest(Ai.parseSessionTurnArguments(args)))
                .toJsPromise(scriptRuntime)
        }
        defineFunction("chat") { args ->
            ensureOpen()
            askRuntime.chat(turnRequest(Ai.parseSessionTurnArguments(args)))
                .toJsPromise(scriptRuntime)
        }
        defineFunction("stream") { args ->
            ensureOpen()
            streamRuntime.stream(turnRequest(Ai.parseSessionTurnArguments(args)))
        }
        defineFunction("close") { args ->
            if (args.isNotEmpty()) {
                throw IllegalArgumentException("AI session close does not accept arguments")
            }
            close()
            RhinoUtils.UNDEFINED
        }
        defineProperty(
            "provider",
            { session.get()?.target?.providerId },
            null,
            READONLY or PERMANENT,
        )
        defineProperty(
            "model",
            { session.get()?.modelId },
            null,
            READONLY or PERMANENT,
        )
        defineProperty(
            "target",
            { session.get()?.targetId },
            null,
            READONLY or PERMANENT,
        )
        defineProperty(
            "profile",
            { session.get()?.target?.profileId },
            null,
            READONLY or PERMANENT,
        )
        defineProperty(
            "backend",
            { session.get()?.backendProfile },
            null,
            READONLY or PERMANENT,
        )
        defineProperty("isClosed", { isClosed }, null, READONLY or PERMANENT)
        defineProperty("state", { if (isClosed) "closed" else "ready" }, null, READONLY or PERMANENT)
        scriptRuntime.closeableManager.add(this)
    }

    override fun getClassName(): String = "AiSession"

    override val publicValue: Any
        get() = this

    override fun attach(value: AiPluginSession): Boolean {
        if (closed.get() || !session.compareAndSet(null, value)) return false
        if (closed.get() && session.compareAndSet(value, null)) {
            value.close()
            return false
        }
        return true
    }

    internal val isClosed: Boolean
        get() = closed.get() || session.get()?.isClosed == true

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            askRuntime.close()
            streamRuntime.close()
            session.getAndSet(null)?.close()
        } finally {
            untrack()
        }
    }

    private fun turnRequest(message: AiPluginMessage): AiPluginAskRequest =
        AiPluginAskRequest(
            component = baseRequest.component,
            providerId = baseRequest.providerId,
            targetId = requireSession().targetId,
            messages = listOf(message),
            timeoutMillis = baseRequest.timeoutMillis,
            maximumOutputBytes = baseRequest.maximumOutputBytes,
            maximumOutputTokens = baseRequest.maximumOutputTokens,
            temperature = baseRequest.temperature,
            topK = baseRequest.topK,
            topP = baseRequest.topP,
            responseSchemaJson = baseRequest.responseSchemaJson,
            backendProfile = baseRequest.backendProfile,
            includeReasoning = baseRequest.includeReasoning,
        )

    private fun requireSession(): AiPluginSession = session.get()
        ?: throw AiPluginRouteException(
            code = "SESSION_CLOSED",
            message = "AI plugin session is not available",
        )

    private fun ensureOpen() {
        if (isClosed) {
            throw AiPluginRouteException(
                code = "SESSION_CLOSED",
                message = "AI plugin session is closed; call ai.session again",
            )
        }
    }

    private fun retireIfCoreClosed() {
        if (session.get()?.isClosed == true) untrack()
    }

    private fun untrack() {
        if (!tracked.compareAndSet(true, false)) return
        scriptRuntime.closeableManager.remove(this)
        onClosed(this)
    }

    private fun defineFunction(name: String, body: (Array<out Any?>) -> Any?) {
        defineProperty(
            name,
            newBaseFunction(scriptRuntime.topLevelScope, name, body, NOT_CONSTRUCTABLE),
            READONLY or DONTENUM or PERMANENT,
        )
    }
}
