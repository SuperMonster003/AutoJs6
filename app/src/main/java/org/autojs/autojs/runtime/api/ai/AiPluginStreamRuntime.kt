package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.AiPluginStreamRunner
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.ai.AiStream
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Per-script owner for plugin-target text-message streams. */
internal class AiPluginStreamRuntime(
    runnerProvider: () -> AiPluginStreamRunner,
    private val dispatcherFactory: () -> AiAsyncDispatcher,
    private val bridges: ScriptBridges,
    private val cancellationActionWrapper: ((() -> Unit) -> () -> Unit) = { action -> action },
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap.newKeySet<ActiveStream>()
    private val runnerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, runnerProvider)
    private val runner by runnerDelegate

    fun stream(request: AiPluginAskRequest): AiStream {
        val attempt = newAttempt(request)
        if (closed.get()) {
            attempt.cancelSilently()
            return attempt.stream
        }
        val selectedRunner = try {
            runner
        } catch (_: Exception) {
            attempt.fail(pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE))
            return attempt.stream
        }
        attempt.start(selectedRunner, request)
        if (closed.get()) attempt.cancelSilently()
        return attempt.stream
    }

    fun reject(error: Exception): AiStream {
        val attempt = newAttempt(request = null)
        if (closed.get()) {
            attempt.cancelSilently()
        } else {
            attempt.fail(error)
        }
        return attempt.stream
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        active.toList().forEach { attempt ->
            runCatching(attempt::cancelSilently)
        }
        active.clear()
    }

    private fun newAttempt(request: AiPluginAskRequest?): ActiveStream {
        lateinit var attempt: ActiveStream
        val controller = AiStream.Controller(
            dispatcher = dispatcherFactory(),
            bridges = bridges,
            onTerminal = { active.remove(attempt) },
        )
        attempt = ActiveStream(request, controller)
        active.add(attempt)
        // Bind the attempt, rather than a returned handle, before runner dispatch. A synchronous
        // natural terminal may arrive before startStream returns its handle and must only close it.
        controller.bindCancellation(cancellationActionWrapper(attempt::cancelFromPublic))
        return attempt
    }

    private inner class ActiveStream(
        private val request: AiPluginAskRequest?,
        private val controller: AiStream.Controller,
    ) {
        private val handle = AtomicReference<AiPluginAskHandle?>()
        private val terminal = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val handleCleaned = AtomicBoolean(false)

        val stream: AiStream
            get() = controller.stream

        fun start(
            selectedRunner: AiPluginStreamRunner,
            request: AiPluginAskRequest,
        ) {
            if (terminal.get()) return
            val returned = try {
                selectedRunner.startStream(request, ::accept)
            } catch (_: Exception) {
                fail(pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            if (!handle.compareAndSet(null, returned)) {
                runCatching(returned::close)
                fail(pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            cleanupHandleIfTerminal()
        }

        fun fail(error: Exception) {
            finishNaturally {
                controller.fail(error.toPluginStreamPublicValue())
            }
        }

        fun cancelSilently() {
            runCatching(controller::cancelSilently)
        }

        fun cancelFromPublic() {
            cancelRequested.set(true)
            terminal.compareAndSet(false, true)
            cleanupHandleIfTerminal()
        }

        private fun accept(event: AiPluginStreamEvent) {
            when (event) {
                is AiPluginStreamEvent.Opened -> dispatchNonTerminal {
                    controller.open(openMetadata(event.target))
                }
                is AiPluginStreamEvent.Chunk -> dispatchNonTerminal {
                    controller.textChunk(
                        event.text,
                        normalizedChunk(event.text, event.reasoning),
                    )
                }
                is AiPluginStreamEvent.Usage -> dispatchNonTerminal {
                    controller.usage(event.usage.toPluginPublicValue())
                }
                is AiPluginStreamEvent.ToolCalls -> failLocally()
                is AiPluginStreamEvent.Completed -> finishNaturally {
                    controller.complete(completion(event))
                }
                is AiPluginStreamEvent.Failed -> finishNaturally {
                    controller.fail(pluginFailure(event.failure).toPluginStreamPublicValue())
                }
            }
        }

        private fun dispatchNonTerminal(action: () -> Unit) {
            if (terminal.get()) return
            try {
                action()
            } catch (_: Exception) {
                failLocally()
            }
        }

        private fun failLocally() {
            cancelRequested.set(true)
            if (!terminal.compareAndSet(false, true)) {
                cleanupHandleIfTerminal()
                return
            }
            try {
                controller.fail(
                    pluginFailure(AiPluginAskFailure.INTERNAL_FAILURE)
                        .toPluginStreamPublicValue(),
                )
            } finally {
                cleanupHandleIfTerminal()
            }
        }

        private fun finishNaturally(action: () -> Boolean) {
            if (!terminal.compareAndSet(false, true)) return
            try {
                // AiStream is the public terminal arbiter. If a user/runtime cancellation has
                // already won there, this natural callback must still cancel the remote handle.
                if (!action()) cancelRequested.set(true)
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

        private fun openMetadata(
            target: org.autojs.autojs.core.plugin.ai.AiPluginTargetSummary,
        ): Map<String, Any?> = linkedMapOf(
            "route" to PLUGIN_ROUTE,
            "provider" to target.providerId,
            "model" to target.modelId,
            "target" to target.toPublicValue(),
            "profile" to target.toProfilePublicValue(),
            "plugin" to request?.toPluginIdentityPublicValue(),
        )

        private fun normalizedChunk(text: String, reasoning: String): Map<String, Any?> = linkedMapOf(
            "text" to text,
            "reasoning" to reasoning,
            "toolCalls" to emptyList<Any?>(),
            "usage" to null,
            "finishReason" to null,
            "done" to false,
            "raw" to null,
        )

        private fun completion(event: AiPluginStreamEvent.Completed): Map<String, Any?> = linkedMapOf(
            "text" to event.text,
            "reasoning" to event.reasoning,
            "toolCalls" to emptyList<Any?>(),
            "usage" to event.usage.toPluginPublicValue(),
            "finishReason" to event.finishReason.toPublicFinishReason(),
            "message" to null,
            "error" to null,
            "raw" to null,
            "profile" to event.target.toProfilePublicValue(),
            "target" to event.target.toPublicValue(),
            "plugin" to request?.toPluginIdentityPublicValue(),
            "route" to PLUGIN_ROUTE,
            "provider" to event.target.providerId,
            "model" to event.target.modelId,
        )
    }

    private companion object {
        const val PLUGIN_ROUTE = "plugin"

        fun pluginFailure(failure: AiPluginAskFailure): AiPluginRouteException =
            AiPluginRouteException(
                code = failure.name,
                message = "AI plugin stream failed (${failure.name})" +
                    AiPluginFailureHints.forFailure(failure),
            )
    }
}

private fun Exception.toPluginStreamPublicValue(): Map<String, Any?> =
    linkedMapOf<String, Any?>(
        "name" to javaClass.simpleName,
        "message" to message.orEmpty(),
    ).apply {
        if (this@toPluginStreamPublicValue is AiPluginRouteException) {
            put("route", "plugin")
            put("code", this@toPluginStreamPublicValue.code)
        }
    }
