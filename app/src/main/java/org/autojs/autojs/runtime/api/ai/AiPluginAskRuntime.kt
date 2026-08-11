package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiTextPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskRunner
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Per-script owner for local plugin asks; the injected runner may remain process-shared. */
internal class AiPluginAskRuntime(
    runnerProvider: () -> AiTextPluginAskRunner,
    private val dispatcherFactory: () -> AiAsyncDispatcher,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap.newKeySet<ActiveAsk>()
    private val runnerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, runnerProvider)
    private val runner by runnerDelegate

    fun ask(request: AiTextPluginAskRequest): ScriptPromiseAdapter {
        val attempt = newAttempt()
        if (closed.get()) {
            attempt.cancelSilently()
            return attempt.promiseAdapter
        }
        val selectedRunner = try {
            runner
        } catch (_: Exception) {
            attempt.fail(pluginFailure(AiTextPluginAskFailure.INTERNAL_FAILURE))
            return attempt.promiseAdapter
        }
        attempt.start(selectedRunner, request)
        if (closed.get()) attempt.cancelSilently()
        return attempt.promiseAdapter
    }

    fun reject(error: Exception): ScriptPromiseAdapter {
        val attempt = newAttempt()
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

    private fun newAttempt(): ActiveAsk {
        val attempt = ActiveAsk(
            dispatcher = dispatcherFactory(),
            promiseAdapter = ScriptPromiseAdapter(),
        )
        active.add(attempt)
        return attempt
    }

    private inner class ActiveAsk(
        private val dispatcher: AiAsyncDispatcher,
        val promiseAdapter: ScriptPromiseAdapter,
    ) {
        private val handle = AtomicReference<AiTextPluginAskHandle?>()
        private val terminal = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val handleCleaned = AtomicBoolean(false)

        fun start(
            selectedRunner: AiTextPluginAskRunner,
            request: AiTextPluginAskRequest,
        ) {
            if (terminal.get()) return
            val returned = try {
                selectedRunner.start(request, ::accept)
            } catch (_: Exception) {
                fail(pluginFailure(AiTextPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            if (!handle.compareAndSet(null, returned)) {
                runCatching(returned::close)
                fail(pluginFailure(AiTextPluginAskFailure.INTERNAL_FAILURE))
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

        private fun accept(outcome: AiTextPluginAskOutcome) {
            when (outcome) {
                is AiTextPluginAskOutcome.Completed -> finish { onDelivered ->
                    dispatcher.resolve(promiseAdapter, outcome.text, onDelivered)
                }
                is AiTextPluginAskOutcome.Failed -> fail(pluginFailure(outcome.failure))
            }
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

    private companion object {
        fun pluginFailure(failure: AiTextPluginAskFailure): AiPluginRouteException =
            AiPluginRouteException(
                code = failure.name,
                message = "AI plugin ask failed (${failure.name})",
            )
    }
}

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
