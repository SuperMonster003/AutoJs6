package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Per-script owner for local plugin text-message requests; the runner may be process-shared. */
internal class AiPluginAskRuntime(
    runnerProvider: () -> AiPluginAskRunner,
    private val dispatcherFactory: () -> AiAsyncDispatcher,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap.newKeySet<ActiveAsk>()
    private val runnerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, runnerProvider)
    private val runner by runnerDelegate

    fun ask(request: AiPluginAskRequest): ScriptPromiseAdapter =
        execute(request, Operation.ASK)

    fun chat(request: AiPluginAskRequest): ScriptPromiseAdapter =
        execute(request, Operation.CHAT)

    private fun execute(
        request: AiPluginAskRequest,
        operation: Operation,
    ): ScriptPromiseAdapter {
        val attempt = newAttempt(request, operation)
        if (closed.get()) {
            attempt.cancelSilently()
            return attempt.promiseAdapter
        }
        val selectedRunner = try {
            runner
        } catch (_: Exception) {
            attempt.fail(pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE, operation))
            return attempt.promiseAdapter
        }
        attempt.start(selectedRunner, request)
        if (closed.get()) attempt.cancelSilently()
        return attempt.promiseAdapter
    }

    fun reject(error: Exception): ScriptPromiseAdapter {
        val attempt = newAttempt(request = null, operation = Operation.ASK)
        if (closed.get()) {
            attempt.cancelSilently()
        } else {
            attempt.fail(error)
        }
        return attempt.promiseAdapter
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.toList().forEach { attempt ->
            runCatching(attempt::cancelSilently)
        }
        active.clear()
    }

    private fun newAttempt(
        request: AiPluginAskRequest?,
        operation: Operation,
    ): ActiveAsk {
        val attempt = ActiveAsk(
            dispatcher = dispatcherFactory(),
            promiseAdapter = ScriptPromiseAdapter(),
            request = request,
            operation = operation,
        )
        active.add(attempt)
        return attempt
    }

    private inner class ActiveAsk(
        private val dispatcher: AiAsyncDispatcher,
        val promiseAdapter: ScriptPromiseAdapter,
        private val request: AiPluginAskRequest?,
        private val operation: Operation,
    ) {
        private val handle = AtomicReference<AiPluginAskHandle?>()
        private val terminal = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val handleCleaned = AtomicBoolean(false)

        fun start(
            selectedRunner: AiPluginAskRunner,
            request: AiPluginAskRequest,
        ) {
            if (terminal.get()) return
            val returned = try {
                selectedRunner.start(request, ::accept)
            } catch (_: Exception) {
                fail(pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE, operation))
                return
            }
            if (!handle.compareAndSet(null, returned)) {
                runCatching(returned::close)
                fail(pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE, operation))
                return
            }
            cleanupHandleIfTerminal()
        }

        fun fail(error: Exception) {
            finish { onDelivered ->
                dispatcher.reject(promiseAdapter, error.toPublicValue(), onDelivered)
            }
        }

        fun cancelSilently() {
            cancelRequested.set(true)
            terminal.compareAndSet(false, true)
            try {
                runCatching { dispatcher.cancelSilently() }
            } finally {
                active.remove(this)
                cleanupHandleIfTerminal()
            }
        }

        private fun accept(outcome: AiPluginAskOutcome) {
            when (outcome) {
                is AiPluginAskOutcome.Completed -> {
                    val value = try {
                        completionValue(outcome)
                    } catch (_: Exception) {
                        fail(pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE, operation))
                        return
                    }
                    finish { onDelivered ->
                        dispatcher.resolve(promiseAdapter, value, onDelivered)
                    }
                }
                is AiPluginAskOutcome.Failed ->
                    fail(pluginFailure(outcome.failure, operation))
            }
        }

        private fun completionValue(outcome: AiPluginAskOutcome.Completed): Any =
            when (operation) {
                Operation.ASK -> outcome.text
                Operation.CHAT -> pluginChatResponse(
                    requireNotNull(request),
                    outcome.text,
                    outcome.usage,
                    outcome.modelId,
                )
            }

        private fun finish(
            dispatch: (onDelivered: () -> Unit) -> Boolean,
        ) {
            if (!terminal.compareAndSet(false, true)) return
            val onDelivered: () -> Unit = { active.remove(this) }
            try {
                if (!dispatch(onDelivered)) onDelivered()
            } catch (_: RuntimeException) {
                onDelivered()
            } finally {
                cleanupHandleIfTerminal()
            }
        }

        private fun cleanupHandleIfTerminal() {
            if (!terminal.get()) return
            val current = handle.get() ?: return
            if (!handleCleaned.compareAndSet(false, true)) return
            if (cancelRequested.get()) runCatching(current::cancel)
            runCatching(current::close)
        }
    }

    private enum class Operation(val publicName: String) {
        ASK("ask"),
        CHAT("chat"),
    }

    private companion object {
        const val PLUGIN_ROUTE = "plugin"

        fun pluginFailure(
            failure: AiPluginAskFailure,
            operation: Operation,
        ): AiPluginRouteException =
            AiPluginRouteException(
                code = failure.name,
                message = "AI plugin ${operation.publicName} failed (${failure.name})" +
                    AiPluginFailureHints.forFailure(failure),
            )

        fun pluginChatResponse(
            request: AiPluginAskRequest,
            text: String,
            usage: AiPluginUsage,
            resolvedModelId: String? = null,
        ): Map<String, Any?> = linkedMapOf(
            "text" to text,
            "reasoning" to "",
            "toolCalls" to emptyList<Any?>(),
            "usage" to usage.toPluginPublicValue(),
            "finishReason" to null,
            "message" to null,
            "error" to null,
            "raw" to null,
            "profile" to null,
            "route" to PLUGIN_ROUTE,
            "provider" to request.providerId,
            "model" to (resolvedModelId ?: request.modelId),
        )
    }
}

internal fun AiPluginUsage.toPluginPublicValue(): Map<String, Any?> = linkedMapOf(
    "inputTokens" to inputTokens,
    "outputTokens" to outputTokens,
    "totalTokens" to totalTokens,
    "reasoningTokens" to null,
    "cachedInputTokens" to null,
    "raw" to durationMillis?.let { linkedMapOf("durationMillis" to it) },
)

internal class AiPluginRouteException(
    val code: String,
    message: String,
) : IllegalStateException(message)

private fun Exception.toPublicValue(): Map<String, Any?> =
    linkedMapOf<String, Any?>(
        "name" to javaClass.simpleName,
        "message" to message.orEmpty(),
    ).apply {
        if (this@toPublicValue is AiPluginRouteException) {
            put("route", "plugin")
            put("code", this@toPublicValue.code)
        }
    }
