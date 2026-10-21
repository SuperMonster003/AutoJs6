package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginCatalog
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRequest
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRunner
import org.autojs.autojs.core.plugin.ai.AiPluginTargetSummary
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiTargetAvailability
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Per-script owner for read-only plugin target catalogs surfaced as `ai.catalog()`. */
internal class AiPluginCatalogRuntime(
    runnerProvider: () -> AiPluginCatalogRunner,
    private val dispatcherFactory: () -> AiAsyncDispatcher,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap.newKeySet<ActiveListing>()
    private val runnerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, runnerProvider)
    private val runner by runnerDelegate

    fun list(request: AiPluginCatalogRequest): ScriptPromiseAdapter {
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
        active.toList().forEach { attempt -> runCatching(attempt::cancelSilently) }
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
            selectedRunner: AiPluginCatalogRunner,
            request: AiPluginCatalogRequest,
        ) {
            if (terminal.get()) return
            val returned = try {
                selectedRunner.listCatalog(request, ::accept)
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
            finish { onDelivered -> dispatcher.reject(promiseAdapter, error.toPublicValue(), onDelivered) }
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

        private fun accept(outcome: AiPluginCatalogOutcome) {
            when (outcome) {
                is AiPluginCatalogOutcome.Completed -> finish { onDelivered ->
                    dispatcher.resolve(promiseAdapter, outcome.catalog.toPublicValue(), onDelivered)
                }
                is AiPluginCatalogOutcome.Failed -> fail(failure(outcome.failure))
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
        fun failure(failure: AiPluginAskFailure): AiPluginRouteException =
            AiPluginRouteException(
                code = failure.name,
                message = "AI plugin catalog failed (${failure.name})" +
                    AiPluginFailureHints.forFailure(failure),
            )
    }
}

internal fun AiPluginCatalog.toPublicValue(): Map<String, Any?> = linkedMapOf(
    "generation" to generation,
    "plugin" to linkedMapOf(
        "provider" to providerId,
        "component" to linkedMapOf(
            "packageName" to component.packageName,
            "className" to component.className,
        ),
    ),
    "defaultTarget" to defaultTargetId,
    "targets" to targets.map(AiPluginTargetSummary::toPublicValue),
)

internal fun AiPluginTargetSummary.toPublicValue(): Map<String, Any?> = linkedMapOf(
    "id" to targetId,
    "displayName" to displayName,
    "provider" to providerId,
    "profile" to profileId,
    "model" to modelId,
    "locality" to when (locality) {
        AiDataLocality.ON_DEVICE -> "local"
        AiDataLocality.REMOTE -> "remote"
        AiDataLocality.HYBRID -> "hybrid"
        else -> error("Unsupported AI target locality")
    },
    "credentialMode" to when (credentialMode) {
        AiCredentialMode.NONE -> "none"
        AiCredentialMode.PLUGIN_MANAGED -> "plugin-managed"
        else -> error("Unsupported AI target credential mode")
    },
    "configured" to configured,
    "available" to (availability == AiTargetAvailability.AVAILABLE),
    "availability" to availability,
    "isDefault" to isDefault,
    "capabilities" to capabilityIds,
    "supportedControls" to supportedControls,
    "limits" to linkedMapOf(
        "maximumContextBytes" to maximumContextBytes,
        "maximumOutputBytes" to maximumOutputBytes,
    ),
    "origins" to declaredHttpsOrigins,
    "backendProfiles" to backendProfiles.map { profile ->
        linkedMapOf<String, Any?>(
            "id" to profile.profileId,
            "availability" to profile.availability,
        ).apply {
            profile.unavailableReason?.let { put("unavailableReason", it) }
        }
    },
)

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
