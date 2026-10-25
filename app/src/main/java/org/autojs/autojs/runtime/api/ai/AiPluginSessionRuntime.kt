package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginSessionOpenOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginSessionRunner
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Per-script owner for asynchronous persistent plugin-session planning. */
internal class AiPluginSessionRuntime(
    runnerProvider: () -> AiPluginSessionRunner,
    private val dispatcherFactory: () -> AiAsyncDispatcher,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap.newKeySet<ActiveOpen>()
    private val runnerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, runnerProvider)
    private val runner by runnerDelegate

    fun open(
        request: AiPluginAskRequest,
        target: AiPluginSessionTarget,
    ): ScriptPromiseAdapter {
        val attempt = ActiveOpen(dispatcherFactory(), ScriptPromiseAdapter(), target)
        active.add(attempt)
        if (closed.get()) {
            attempt.cancelSilently()
            return attempt.promiseAdapter
        }
        val selectedRunner = try {
            runner
        } catch (_: Exception) {
            attempt.fail(AiPluginAskFailure.INTERNAL_FAILURE)
            return attempt.promiseAdapter
        }
        attempt.start(selectedRunner, request)
        if (closed.get()) attempt.cancelSilently()
        return attempt.promiseAdapter
    }

    fun reject(
        target: AiPluginSessionTarget,
        error: Exception,
    ): ScriptPromiseAdapter {
        val attempt = ActiveOpen(dispatcherFactory(), ScriptPromiseAdapter(), target)
        active.add(attempt)
        if (closed.get()) attempt.cancelSilently() else attempt.fail(error)
        return attempt.promiseAdapter
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.toList().forEach { attempt -> runCatching(attempt::cancelSilently) }
        active.clear()
    }

    private inner class ActiveOpen(
        private val dispatcher: AiAsyncDispatcher,
        val promiseAdapter: ScriptPromiseAdapter,
        private val target: AiPluginSessionTarget,
    ) {
        private val handle = AtomicReference<AiPluginAskHandle?>()
        private val terminal = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val handleCleaned = AtomicBoolean(false)

        fun start(
            selectedRunner: AiPluginSessionRunner,
            request: AiPluginAskRequest,
        ) {
            if (terminal.get()) return
            val returned = try {
                selectedRunner.openSession(request, ::accept)
            } catch (_: Exception) {
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            if (!handle.compareAndSet(null, returned)) {
                runCatching(returned::close)
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            cleanupHandleIfTerminal()
        }

        fun fail(failure: AiPluginAskFailure) = fail(sessionFailure(failure))

        fun fail(error: Exception) {
            target.close()
            finish { onDelivered ->
                dispatcher.reject(promiseAdapter, error.toSessionPublicValue(), onDelivered)
            }
        }

        fun cancelSilently() {
            cancelRequested.set(true)
            terminal.compareAndSet(false, true)
            try {
                target.close()
                runCatching { dispatcher.cancelSilently() }
            } finally {
                active.remove(this)
                cleanupHandleIfTerminal()
            }
        }

        private fun accept(outcome: AiPluginSessionOpenOutcome) {
            if (terminal.get()) {
                if (outcome is AiPluginSessionOpenOutcome.Completed) outcome.session.close()
                return
            }
            when (outcome) {
                is AiPluginSessionOpenOutcome.Completed -> {
                    if (!target.attach(outcome.session)) {
                        outcome.session.close()
                        cancelSilently()
                        return
                    }
                    finish { onDelivered ->
                        dispatcher.resolve(promiseAdapter, target.publicValue, onDelivered)
                    }
                }
                is AiPluginSessionOpenOutcome.Failed -> fail(outcome.failure)
            }
        }

        private fun finish(dispatch: (onDelivered: () -> Unit) -> Boolean) {
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
        fun sessionFailure(failure: AiPluginAskFailure) = AiPluginRouteException(
            code = failure.name,
            message = "AI plugin session failed (${failure.name})" +
                AiPluginFailureHints.forFailure(failure),
        )
    }
}

internal interface AiPluginSessionTarget : Closeable {
    val publicValue: Any
    fun attach(value: org.autojs.autojs.core.plugin.ai.AiPluginSession): Boolean
}

private fun Exception.toSessionPublicValue(): Map<String, Any?> =
    linkedMapOf<String, Any?>(
        "name" to javaClass.simpleName,
        "message" to message.orEmpty(),
    ).apply {
        if (this@toSessionPublicValue is AiPluginRouteException) {
            put("route", "plugin")
            put("code", this@toSessionPublicValue.code)
        }
    }
