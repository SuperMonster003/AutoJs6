package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginStreamRunner
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.ai.AiStream
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Per-script owner for prompt-only local plugin streams. */
internal class AiPluginStreamRuntime(
    runnerProvider: () -> OnDeviceAiPluginStreamRunner,
    private val dispatcherFactory: () -> AiAsyncDispatcher,
    private val bridges: ScriptBridges,
    private val cancellationActionWrapper: ((() -> Unit) -> () -> Unit) = { action -> action },
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap.newKeySet<ActiveStream>()
    private val runnerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, runnerProvider)
    private val runner by runnerDelegate

    fun stream(request: OnDeviceAiPluginAskRequest): AiStream {
        val attempt = newAttempt(request)
        if (closed.get()) {
            attempt.cancelSilently()
            return attempt.stream
        }
        val selectedRunner = try {
            runner
        } catch (_: Exception) {
            attempt.fail(pluginFailure(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE))
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

    private fun newAttempt(request: OnDeviceAiPluginAskRequest?): ActiveStream {
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
        private val request: OnDeviceAiPluginAskRequest?,
        private val controller: AiStream.Controller,
    ) {
        private val handle = AtomicReference<OnDeviceAiPluginAskHandle?>()
        private val terminal = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val handleCleaned = AtomicBoolean(false)

        val stream: AiStream
            get() = controller.stream

        fun start(
            selectedRunner: OnDeviceAiPluginStreamRunner,
            request: OnDeviceAiPluginAskRequest,
        ) {
            if (terminal.get()) return
            val returned = try {
                selectedRunner.startStream(request, ::accept)
            } catch (_: Exception) {
                fail(pluginFailure(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            if (!handle.compareAndSet(null, returned)) {
                runCatching(returned::close)
                fail(pluginFailure(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE))
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

        private fun accept(event: OnDeviceAiPluginStreamEvent) {
            when (event) {
                OnDeviceAiPluginStreamEvent.Opened -> dispatchNonTerminal {
                    controller.open(openMetadata())
                }
                is OnDeviceAiPluginStreamEvent.Chunk -> dispatchNonTerminal {
                    controller.textChunk(event.text, normalizedChunk(event.text))
                }
                is OnDeviceAiPluginStreamEvent.Completed -> finishNaturally {
                    controller.complete(completion(event.text))
                }
                is OnDeviceAiPluginStreamEvent.Failed -> finishNaturally {
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
                    pluginFailure(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
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

        private fun openMetadata(): Map<String, Any?> = linkedMapOf(
            "route" to PLUGIN_ROUTE,
            "provider" to request?.providerId,
            "model" to request?.modelId,
        )

        private fun normalizedChunk(text: String): Map<String, Any?> = linkedMapOf(
            "text" to text,
            "reasoning" to "",
            "toolCalls" to emptyList<Any?>(),
            "usage" to null,
            "finishReason" to null,
            "done" to false,
            "raw" to null,
        )

        private fun completion(text: String): Map<String, Any?> = linkedMapOf(
            "text" to text,
            "reasoning" to "",
            "toolCalls" to emptyList<Any?>(),
            "usage" to null,
            "finishReason" to null,
            "message" to null,
            "error" to null,
            "raw" to null,
            "profile" to null,
            "route" to PLUGIN_ROUTE,
            "provider" to request?.providerId,
            "model" to request?.modelId,
        )
    }

    private companion object {
        const val PLUGIN_ROUTE = "plugin"

        fun pluginFailure(failure: OnDeviceAiPluginAskFailure): AiPluginRouteException =
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
