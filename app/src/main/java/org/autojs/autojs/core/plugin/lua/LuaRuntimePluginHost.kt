package org.autojs.autojs.core.plugin.lua

import org.autojs.plugin.lua.runtime.api.ILuaExecutionSession
import org.autojs.plugin.lua.runtime.api.ILuaRuntimeProvider
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Injectable transport boundary. Android implementations query by action but bind only the exact
 * selected component; in-process JVM fakes implement the same contract without Binder.
 */
internal interface LuaRuntimeProviderTransport {
    fun discover(runtimeAction: String, timeoutMillis: Long): Collection<LuaRuntimeProviderDescriptor>

    /** Re-inspects one package/component identity without selecting or substituting another provider. */
    fun inspectExact(
        component: LuaRuntimeProviderComponent,
        timeoutMillis: Long,
    ): LuaRuntimeProviderDescriptor?

    fun bindExact(component: LuaRuntimeProviderComponent, timeoutMillis: Long): LuaRuntimeProviderBinding
}

internal interface LuaRuntimeProviderBinding : AutoCloseable {
    val component: LuaRuntimeProviderComponent
    val provider: ILuaRuntimeProvider

    /** Reads RuntimeInfo through the transport's bounded Binder-call policy. */
    fun readRuntimeInfo(timeoutMillis: Long): ByteArray

    override fun close()
}

/** Transfers an acquired closeable only when [block] succeeds; failures retain their original cause. */
internal fun <T : AutoCloseable, R> transferOrClose(resource: T, block: (T) -> R): R = try {
    block(resource)
} catch (failure: Throwable) {
    runCatching { resource.close() }
    failure.rethrowIfInterrupted()
    throw failure
}

/** Binder waits are interruptible; never translate a requested engine stop into provider absence. */
internal fun Throwable.rethrowIfInterrupted() {
    if (this is InterruptedException) {
        Thread.currentThread().interrupt()
        throw this
    }
}

internal enum class LuaRuntimeHostFailureCode {
    LUA_RUNTIME_UNAVAILABLE,
    LUA_RUNTIME_AMBIGUOUS,
    LUA_RUNTIME_IDENTITY_CHANGED,
    LUA_RUNTIME_BIND_FAILED,
}

internal class LuaRuntimeHostException(
    val code: LuaRuntimeHostFailureCode,
    message: String,
    evaluations: Collection<LuaRuntimeProviderEvaluation> = emptyList(),
    ambiguousCandidates: Collection<LuaRuntimeProviderPinnedIdentity> = emptyList(),
    cause: Throwable? = null,
) : IllegalStateException("$code: $message", cause) {
    val evaluations: List<LuaRuntimeProviderEvaluation> = immutableHostSnapshot(evaluations)
    val ambiguousCandidates: List<LuaRuntimeProviderPinnedIdentity> = immutableHostSnapshot(ambiguousCandidates)

    override fun toString(): String =
        "LuaRuntimeHostException(code=$code, evaluations=${evaluations.size}, " +
            "ambiguousCandidates=${ambiguousCandidates.size}, hasCause=${cause != null})"
}

internal class LuaRuntimePreparedProvider internal constructor(
    val pinnedIdentity: LuaRuntimeProviderPinnedIdentity,
    internal val preferredComponent: LuaRuntimeProviderComponent?,
) {
    val runtimeInfo: LuaRuntimeInfo
        get() = pinnedIdentity.runtimeInfo

    val expectedCallbackUid: Int
        get() = pinnedIdentity.expectedCallbackUid

    override fun toString(): String = "LuaRuntimePreparedProvider(pinnedIdentity=$pinnedIdentity)"
}

/**
 * Live explicit-component binding retained for one engine execution.
 *
 * The engine creates a session through [provider], calls [attachSession] immediately after a
 * successful `createExecution`, and then owns cancellation. Closing this handle closes an attached
 * session before releasing the service binding, and is idempotent.
 */
internal class LuaRuntimeAcquiredProvider internal constructor(
    val pinnedIdentity: LuaRuntimeProviderPinnedIdentity,
    private val binding: LuaRuntimeProviderBinding,
    private val beforeDispatch: () -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var session: ILuaExecutionSession? = null

    val runtimeInfo: LuaRuntimeInfo
        get() = pinnedIdentity.runtimeInfo

    val provider: ILuaRuntimeProvider
        get() = synchronized(lock) {
            check(!closed) { "Lua runtime provider binding is closed" }
            binding.provider
        }

    val expectedCallbackUid: Int
        get() = pinnedIdentity.expectedCallbackUid

    val component: LuaRuntimeProviderComponent
        get() = pinnedIdentity.component

    fun attachSession(value: ILuaExecutionSession): ILuaExecutionSession = synchronized(lock) {
        check(!closed) { "Lua runtime provider binding is closed" }
        check(session == null) { "A Lua execution session is already attached" }
        session = value
        value
    }

    fun releaseSession(value: ILuaExecutionSession): Boolean = synchronized(lock) {
        if (session !== value) return@synchronized false
        session = null
        true
    }

    /** Revalidates the bound provider immediately before the first source descriptor is dispatched. */
    fun revalidateBeforeDispatch() {
        synchronized(lock) { check(!closed) { "Lua runtime provider binding is closed" } }
        try {
            beforeDispatch()
            synchronized(lock) { check(!closed) { "Lua runtime provider binding is closed" } }
        } catch (failure: Throwable) {
            close()
            failure.rethrowIfInterrupted()
            throw failure
        }
    }

    fun cancelActiveSession(): Boolean {
        val current = synchronized(lock) {
            if (closed) return false
            session
        } ?: return false
        current.cancel()
        return true
    }

    fun isClosed(): Boolean = synchronized(lock) { closed }

    override fun close() {
        val current = synchronized(lock) {
            if (closed) return
            closed = true
            session.also { session = null }
        }
        runCatching { current?.close() }
        runCatching { binding.close() }
    }

    override fun toString(): String =
        "LuaRuntimeAcquiredProvider(component=$component, expectedCallbackUid=$expectedCallbackUid, " +
            "closed=${isClosed()})"
}

/** Synchronous preparation/acquisition seam used by the future Lua ScriptEngine. */
internal class LuaRuntimePluginHost(
    private val transport: LuaRuntimeProviderTransport,
    private val environment: LuaRuntimeProviderEnvironment,
    private val requirement: LuaRuntimeProviderRequirement = LuaRuntimeProviderRequirement(),
    private val handshakeTimeoutMillis: Long = DEFAULT_HANDSHAKE_TIMEOUT_MILLIS,
    private val nanoTime: () -> Long = System::nanoTime,
) {

    init {
        require(handshakeTimeoutMillis in 1L..MAX_HANDSHAKE_TIMEOUT_MILLIS) {
            "Lua runtime handshake timeout is outside the host limit"
        }
    }

    /** Discovers, probes through exact-component binds, and rejects zero or multiple providers. */
    fun prepare(preferredComponent: LuaRuntimeProviderComponent? = null): LuaRuntimePreparedProvider = try {
        prepareWithin(preferredComponent, newDeadline())
    } catch (failure: LuaBinderCallException) {
        throw LuaRuntimeHostException(
            LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE,
            failure.hostDiagnosticMessage(),
            cause = failure,
        )
    }

    private fun prepareWithin(
        preferredComponent: LuaRuntimeProviderComponent?,
        deadlineNanos: Long,
    ): LuaRuntimePreparedProvider {
        val descriptors = discoverOrThrow(deadlineNanos)
            .filter { preferredComponent == null || it.component == preferredComponent }
        val evaluations = ArrayList<LuaRuntimeProviderEvaluation>()
        val candidates = ArrayList<LuaRuntimeProviderCandidate>()

        descriptors.sortedWith(DESCRIPTOR_ORDER).forEach { descriptor ->
            val structuralRejection = LuaRuntimeProviderSelectionPolicy.preflight(descriptor, environment)
            if (structuralRejection != null) {
                evaluations += descriptor.evaluation(structuralRejection)
                return@forEach
            }
            when (val probe = probe(descriptor, deadlineNanos)) {
                is ProbeResult.Accepted -> candidates += LuaRuntimeProviderCandidate(descriptor, probe.runtimeInfo)
                is ProbeResult.Rejected -> evaluations += descriptor.evaluation(probe.rejection)
            }
        }

        return when (
            val selected = LuaRuntimeProviderSelectionPolicy.select(
                candidates = candidates,
                environment = environment,
                requirement = requirement,
                preferredComponent = preferredComponent,
            )
        ) {
            is LuaRuntimeProviderSelectionResult.Selected -> LuaRuntimePreparedProvider(
                pinnedIdentity = selected.pinned,
                preferredComponent = preferredComponent,
            )
            is LuaRuntimeProviderSelectionResult.NoMatch -> throw LuaRuntimeHostException(
                code = LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE,
                message = "No eligible Lua runtime provider is available",
                evaluations = evaluations + selected.evaluations,
            )
            is LuaRuntimeProviderSelectionResult.Ambiguous -> throw LuaRuntimeHostException(
                code = LuaRuntimeHostFailureCode.LUA_RUNTIME_AMBIGUOUS,
                message = "Multiple eligible Lua runtime providers are available",
                evaluations = evaluations,
                ambiguousCandidates = selected.candidates,
            )
        }
    }

    /**
     * Re-discovers and revalidates every pinned field immediately before returning a live binding.
     * The binding remains owned by the returned handle and is never silently switched or retried.
     */
    fun acquire(prepared: LuaRuntimePreparedProvider): LuaRuntimeAcquiredProvider {
        val deadlineNanos = newDeadline()
        val pinned = prepared.pinnedIdentity
        val refreshed = try {
            prepareWithin(prepared.preferredComponent, deadlineNanos)
        } catch (failure: LuaRuntimeHostException) {
            throw identityChanged("Lua runtime provider selection changed before dispatch", failure)
        } catch (failure: LuaBinderCallException) {
            throw identityChanged("Lua runtime provider handshake did not complete before dispatch", failure)
        }
        if (refreshed.pinnedIdentity != pinned) {
            throw identityChanged("Lua runtime provider selection changed before dispatch")
        }
        val matches = discoverOrIdentityChanged(deadlineNanos).filter { it.component == pinned.component }
        if (matches.size != 1) {
            throw identityChanged("Pinned Lua runtime component disappeared or became ambiguous")
        }
        val descriptor = matches.single()
        if (LuaRuntimeProviderSelectionPolicy.preflight(descriptor, environment) != null) {
            throw identityChanged("Pinned Lua runtime package metadata changed")
        }

        val (bindingTimeoutMillis, binding) = try {
            val timeoutMillis = remainingMillis(deadlineNanos)
            timeoutMillis to transport.bindExact(pinned.component, timeoutMillis)
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            throw LuaRuntimeHostException(
                LuaRuntimeHostFailureCode.LUA_RUNTIME_BIND_FAILED,
                "Could not bind the pinned Lua runtime provider",
                cause = failure,
            )
        }
        try {
            if (binding.component != pinned.component) {
                throw identityChanged("Lua runtime transport bound a different component")
            }
            val runtimeInfo = decodeRuntimeInfo(binding, remainingMillis(deadlineNanos))
            val current = LuaRuntimeProviderCandidate(descriptor, runtimeInfo)
            if (!LuaRuntimeProviderSelectionPolicy.revalidate(pinned, current, environment, requirement)) {
                throw identityChanged("Pinned Lua runtime identity changed before dispatch")
            }
            return LuaRuntimeAcquiredProvider(
                pinnedIdentity = pinned,
                binding = binding,
                beforeDispatch = {
                    revalidateBeforeDispatch(
                        pinned = pinned,
                        binding = binding,
                        bindingTimeoutMillis = bindingTimeoutMillis,
                    )
                },
            )
        } catch (failure: Throwable) {
            runCatching { binding.close() }
            failure.rethrowIfInterrupted()
            if (failure is LuaRuntimeHostException) throw failure
            throw identityChanged("Pinned Lua runtime information could not be revalidated", failure)
        }
    }

    private fun discoverOrThrow(deadlineNanos: Long): List<LuaRuntimeProviderDescriptor> = try {
        checkedProviderSnapshot(
            transport.discover(LuaRuntimeContract.SERVICE_ACTION, remainingMillis(deadlineNanos)),
        )
    } catch (failure: Throwable) {
        failure.rethrowIfInterrupted()
        if (failure is LuaBinderCallException) throw failure
        throw LuaRuntimeHostException(
            LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE,
            "Lua runtime discovery failed",
            cause = failure,
        )
    }

    private fun discoverOrIdentityChanged(deadlineNanos: Long): List<LuaRuntimeProviderDescriptor> = try {
        checkedProviderSnapshot(
            transport.discover(LuaRuntimeContract.SERVICE_ACTION, remainingMillis(deadlineNanos)),
        )
    } catch (failure: Throwable) {
        failure.rethrowIfInterrupted()
        throw identityChanged("Pinned Lua runtime could not be rediscovered", failure)
    }

    private fun revalidateBeforeDispatch(
        pinned: LuaRuntimeProviderPinnedIdentity,
        binding: LuaRuntimeProviderBinding,
        bindingTimeoutMillis: Long,
    ) {
        val deadlineNanos = newDeadline()
        val runtimeInfo = try {
            // The live binding retains the timeout admitted when it was acquired. A fresh
            // pre-dispatch deadline may be a few milliseconds larger, so never widen the
            // binding's original call budget while revalidating it.
            decodeRuntimeInfo(
                binding,
                minOf(remainingMillis(deadlineNanos), bindingTimeoutMillis),
            )
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            throw identityChanged("Pinned Lua runtime information changed before dispatch", failure)
        }
        val descriptor = try {
            transport.inspectExact(pinned.component, remainingMillis(deadlineNanos))
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            throw identityChanged("Pinned Lua runtime package could not be re-inspected before dispatch", failure)
        }
        val current = descriptor?.let { LuaRuntimeProviderCandidate(it, runtimeInfo) }
        if (!LuaRuntimeProviderSelectionPolicy.revalidate(pinned, current, environment, requirement)) {
            throw identityChanged("Pinned Lua runtime identity changed before dispatch")
        }
    }

    private fun probe(descriptor: LuaRuntimeProviderDescriptor, deadlineNanos: Long): ProbeResult {
        val binding = try {
            transport.bindExact(descriptor.component, remainingMillis(deadlineNanos))
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            if (failure is LuaBinderCallException) throw failure
            return ProbeResult.Rejected(LuaRuntimeProviderRejection.BIND_FAILED)
        }
        return try {
            if (binding.component != descriptor.component) {
                ProbeResult.Rejected(LuaRuntimeProviderRejection.BOUND_COMPONENT_MISMATCH)
            } else {
                ProbeResult.Accepted(decodeRuntimeInfo(binding, remainingMillis(deadlineNanos)))
            }
        } catch (_: IllegalArgumentException) {
            ProbeResult.Rejected(LuaRuntimeProviderRejection.INVALID_RUNTIME_INFO)
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            if (failure is LuaBinderCallException) throw failure
            ProbeResult.Rejected(LuaRuntimeProviderRejection.BIND_FAILED)
        } finally {
            runCatching { binding.close() }
        }
    }

    private fun decodeRuntimeInfo(binding: LuaRuntimeProviderBinding, timeoutMillis: Long): LuaRuntimeInfo =
        LuaRuntimeCodec.decodeRuntimeInfo(binding.readRuntimeInfo(timeoutMillis))

    private fun newDeadline(): Long =
        nanoTime() + TimeUnit.MILLISECONDS.toNanos(handshakeTimeoutMillis)

    private fun remainingMillis(deadlineNanos: Long): Long {
        val remainingNanos = deadlineNanos - nanoTime()
        if (remainingNanos <= 0L) {
            throw LuaBinderCallException(LuaBinderCallFailure.TIMEOUT, "host-handshake")
        }
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
    }

    private fun checkedProviderSnapshot(
        values: Collection<LuaRuntimeProviderDescriptor>,
    ): List<LuaRuntimeProviderDescriptor> {
        require(values.size <= MAX_DISCOVERED_PROVIDERS) { "Too many Lua runtime providers were discovered" }
        return immutableHostSnapshot(values)
    }

    private fun identityChanged(message: String, cause: Throwable? = null) = LuaRuntimeHostException(
        LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED,
        message,
        cause = cause,
    )

    private fun LuaBinderCallException.hostDiagnosticMessage(): String = when (failure) {
        LuaBinderCallFailure.POISONED ->
            "Lua runtime dispatch is disabled until the host process restarts after an uncertain execution creation"
        LuaBinderCallFailure.STALLED -> "A prior Lua runtime handshake is still stalled"
        LuaBinderCallFailure.BUSY -> "Another Lua runtime handshake is already active"
        LuaBinderCallFailure.TIMEOUT -> "Lua runtime handshake did not complete within the host budget"
        LuaBinderCallFailure.CLOSED -> "Lua runtime handshake infrastructure is closed"
    }

    private sealed class ProbeResult {
        data class Accepted(val runtimeInfo: LuaRuntimeInfo) : ProbeResult()
        data class Rejected(val rejection: LuaRuntimeProviderRejection) : ProbeResult()
    }

    private companion object {
        const val DEFAULT_HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val MAX_HANDSHAKE_TIMEOUT_MILLIS = 60_000L
        const val MAX_DISCOVERED_PROVIDERS = 16
        val DESCRIPTOR_ORDER = compareBy<LuaRuntimeProviderDescriptor>(
            { it.component.packageName },
            { it.component.className },
            { it.versionCode },
            { it.lastUpdateTime },
        )
    }
}

private fun LuaRuntimeProviderDescriptor.evaluation(rejection: LuaRuntimeProviderRejection) =
    LuaRuntimeProviderEvaluation(component, providerId = null, rejection)

private fun <T> immutableHostSnapshot(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
