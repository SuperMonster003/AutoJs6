package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.ServiceBindingLease

/**
 * One retained compiler binding: the dedicated bindService lease plus the Binder it produced.
 *
 * Liveness must not rely on [IBinder.isBinderAlive] alone. That flag only turns false after a
 * death notification, and the lease unlinks its recipient when the binding is reported dead
 * ([android.content.ServiceConnection.onBindingDied] can arrive before the kernel reports the
 * process death), after which a dead proxy keeps reporting itself alive for the rest of the
 * host process. A binding whose lease has already been released is dead by definition, and
 * [IBinder.pingBinder] settles the remaining cases with one round trip.
 */
internal class TypeScriptCompilerWarmBinding(
    private val bindingLease: ServiceBindingLease,
    private val binder: IBinder,
    val projectSessionRevision: Int?,
) : Closeable {

    private val closed = AtomicBoolean(false)

    val isAlive: Boolean
        get() = !closed.get() &&
            !bindingLease.isClosed &&
            binder.isBinderAlive &&
            runCatching { binder.pingBinder() }.getOrDefault(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) bindingLease.close()
    }
}

/** Moves compiler startup off the first user-triggered run while keeping failure cold-safe. */
internal object TypeScriptCompilerStartupPrewarm {

    private val retention = TypeScriptCompilerWarmBindingRetention<TypeScriptCompilerWarmBinding>(
        TypeScriptCompilerWarmBinding::isAlive,
    )
    private val pending = AtomicReference<CompletableDeferred<Unit>?>(null)

    fun requestAsync(context: Context, reason: String): Boolean {
        val generation = retention.generationToken()
        val completion = CompletableDeferred<Unit>()
        if (!pending.compareAndSet(null, completion)) return false
        val appContext = context.applicationContext
        return try {
            Thread(
                {
                    try {
                        runBlocking { prewarm(appContext, reason, generation) }
                    } catch (error: Exception) {
                        Log.w(
                            LOG_TAG,
                            "TypeScript compiler prewarm failed; cold fallback remains available.",
                            error,
                        )
                    } finally {
                        completion.complete(Unit)
                        pending.compareAndSet(completion, null)
                    }
                },
                "AutoJs6-TypeScript-Compiler-Prewarm",
            ).start()
            true
        } catch (error: Exception) {
            completion.complete(Unit)
            pending.compareAndSet(completion, null)
            Log.w(LOG_TAG, "Unable to start TypeScript compiler prewarm thread.", error)
            false
        }
    }

    suspend fun prewarm(context: Context, reason: String): Snapshot =
        prewarm(context, reason, retention.generationToken())

    private suspend fun prewarm(
        context: Context,
        reason: String,
        generation: Long,
    ): Snapshot {
        val startedAt = SystemClock.elapsedRealtime()
        val result = retention.retain(generation) {
            TypeScriptCompilerPluginClient(context).openWarmBinding()
        }
        return Snapshot(
            retained = result.value != null,
            acquired = result.acquired,
            projectSessionRevision = result.value?.projectSessionRevision,
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
        ).also { snapshot ->
            Log.i(
                LOG_TAG,
                "typescript-prewarm schema=${Snapshot.SCHEMA_REVISION} reason=$reason " +
                    "retained=${snapshot.retained} acquired=${snapshot.acquired} " +
                    "projectSessionRevision=${snapshot.projectSessionRevision ?: "legacy"} " +
                    "elapsedMs=${snapshot.elapsedMs}",
            )
        }
    }

    suspend fun awaitPendingPrewarm() {
        while (true) {
            val completion = pending.get() ?: return
            completion.await()
            if (pending.get() == null) return
        }
    }

    /** Takes ownership even when an existing live binding causes [binding] to be closed. */
    fun generationToken(): Long = retention.generationToken()

    fun retainCompilationBinding(
        binding: TypeScriptCompilerWarmBinding,
        expectedGeneration: Long,
    ) {
        val generationBefore = retention.generationToken()
        val candidateAlive = binding.isAlive
        val retained = retention.offer(binding, expectedGeneration)
        // Observational (startup-latency L2): why a compilation binding was or was not kept.
        Log.i(
            LOG_TAG,
            "typescript-warm-binding schema=1 event=offer retained=$retained " +
                "candidateAlive=$candidateAlive expectedGeneration=$expectedGeneration " +
                "generationBefore=$generationBefore " +
                "projectSessionRevision=${binding.projectSessionRevision ?: "legacy"}",
        )
    }

    fun release(reason: String) {
        retention.release()
        Log.i(LOG_TAG, "Released TypeScript compiler warm binding: reason=$reason")
    }

    internal data class Snapshot(
        val retained: Boolean,
        val acquired: Boolean,
        val projectSessionRevision: Int?,
        val elapsedMs: Long,
    ) {
        companion object {
            const val SCHEMA_REVISION = 1
        }
    }

    private const val LOG_TAG = "TypeScriptCompiler"
}
