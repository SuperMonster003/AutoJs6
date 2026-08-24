package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginModelListOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginModelListRequest
import org.autojs.autojs.core.plugin.ai.AiPluginModelListRunner
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Per-script owner for read-only plugin model listings surfaced as `ai.models()`. */
internal class AiPluginModelListRuntime(
    runnerProvider: () -> AiPluginModelListRunner,
    private val dispatcherFactory: () -> AiAsyncDispatcher,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap.newKeySet<ActiveListing>()
    private val runnerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, runnerProvider)
    private val runner by runnerDelegate

    fun list(request: AiPluginModelListRequest): ScriptPromiseAdapter {
        val attempt = newAttempt()
        if (closed.get()) {
            attempt.cancelSilently()
            return attempt.promiseAdapter
        }
        val selectedRunner = try {
            runner
        } catch (_: Exception) {
            attempt.fail(failure(AiPluginAskFailure.INTERNAL_FAILURE))
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

    private fun newAttempt(): ActiveListing {
        val attempt = ActiveListing(
            dispatcher = dispatcherFactory(),
            promiseAdapter = ScriptPromiseAdapter(),
        )
        active.add(attempt)
        return attempt
    }

    private inner class ActiveListing(
        private val dispatcher: AiAsyncDispatcher,
        val promiseAdapter: ScriptPromiseAdapter,
    ) {
        private val handle = AtomicReference<AiPluginAskHandle?>()
        private val terminal = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val handleCleaned = AtomicBoolean(false)

        fun start(
            selectedRunner: AiPluginModelListRunner,
            request: AiPluginModelListRequest,
        ) {
            if (terminal.get()) return
            val returned = try {
                selectedRunner.listModels(request, ::accept)
            } catch (_: Exception) {
                fail(failure(AiPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            if (!handle.compareAndSet(null, returned)) {
                runCatching(returned::close)
                fail(failure(AiPluginAskFailure.INTERNAL_FAILURE))
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

        private fun accept(outcome: AiPluginModelListOutcome) {
            when (outcome) {
                is AiPluginModelListOutcome.Completed -> {
                    val value = outcome.models.map { model ->
                        linkedMapOf<String, Any?>(
                            "modelId" to model.modelId,
                            "displayName" to model.displayName,
                            "capabilityIds" to model.capabilityIds,
                            "maximumContextBytes" to model.maximumContextBytes,
                            "maximumOutputBytes" to model.maximumOutputBytes,
                            "backendProfiles" to model.backendProfiles.map { profile ->
                                linkedMapOf<String, Any?>(
                                    "id" to profile.profileId,
                                    "availability" to profile.availability,
                                ).apply {
                                    profile.unavailableReason?.let { reason ->
                                        put("unavailableReason", reason)
                                    }
                                }
                            },
                        )
                    }
                    finish { onDelivered ->
                        dispatcher.resolve(promiseAdapter, value, onDelivered)
                    }
                }
                is AiPluginModelListOutcome.Failed ->
                    fail(failure(outcome.failure))
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
        fun failure(failure: AiPluginAskFailure): AiPluginRouteException =
            AiPluginRouteException(
                code = failure.name,
                message = "AI plugin models failed (${failure.name})" +
                    AiPluginFailureHints.forFailure(failure),
            )
    }
}

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
