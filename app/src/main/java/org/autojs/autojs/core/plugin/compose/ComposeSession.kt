package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Android effects live behind this seam; transactions and scheduling are tested without a Looper. */
interface ComposeSessionRenderer {
    fun open(sink: ComposeUiEventSink)
    fun attach()
    fun detach()
    fun apply(batch: UiPatchBatch)
    fun execute(command: UiCommand)
    fun setTheme(theme: ThemeSpec)
    fun dispose()
}

class ComposeSession(
    val sessionId: Int,
    private val script: ScriptUiDispatcher,
    private val main: Executor,
    private val renderer: ComposeSessionRenderer,
    private val components: Set<String>,
    private val onError: (ComposeUiError) -> Unit,
    private val warn: (String) -> Unit = {},
    private val onClosed: () -> Unit = {},
) : ComposeUiEventSink, AutoCloseable {
    enum class State { NEW, OPEN, ATTACHED, CLOSED }
    sealed interface CommitResult {
        data class Accepted(val tree: UiTree, val generation: Long) : CommitResult
        data class Rejected(val error: ComposeUiError) : CommitResult
        data object Superseded : CommitResult
    }
    private class Submission(val tree: UiTree, val completion: ((CommitResult) -> Unit)?) {
        var completed = false
    }
    @Volatile var state = State.NEW
        private set
    private val closing = AtomicBoolean()
    private val callbacks = CallbackRegistry()
    private val reconciler = TreeReconciler { warn("Compose UI session $sessionId: list children have no key") }
    private val eventLock = Any()
    private val events = ArrayDeque<UiEvent>()
    private val droppedCommands = CommandResultEvictions()
    private var eventScheduled = false
    private var overflowWarning = false
    private var pending: Submission? = null
    private var flight: Submission? = null
    private var flushScheduled = false
    // Main-thread value: a command may run after apply succeeds but before its script acknowledgement.
    private var appliedGeneration = 0L
    var tree: UiTree? = null
        private set
    var generation = 0L
        private set
    val callbackCount get() = callbacks.size

    init { require(sessionId > 0) }

    fun open() {
        confined()
        if (state != State.NEW) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
        state = State.OPEN
        effect { renderer.open(this) }
    }

    fun registerCallback(callback: (UiEvent) -> Unit): Int { usable(); return callbacks.register(callback) }
    fun containsCallback(callbackId: Int): Boolean { usable(); return callbacks.contains(callbackId) }

    /** A failed script snapshot may have registered callbacks before validation rejected the tree. */
    fun discardUnusedCallbacks() {
        usable()
        callbacks.retain(tree, pending?.tree, flight?.tree)
    }

    /** Repeated renders in the same scheduler turn replace the pending target and yield one batch. */
    fun render(draft: UiTree) {
        usable()
        validateCallbacksAndComponents(draft)
        // Validate and bound the prospective diff synchronously, before publishing pending state.
        val result = reconciler.reconcile(pending?.tree ?: flight?.tree ?: tree, draft)
        submit(Submission(result.tree, null))
    }

    /**
     * Submit a handle graph whose node IDs are already stable. Validation failures throw before
     * staging. Once staged, exactly one completion runs on the script dispatcher, including no-op,
     * replacement by a newer target, renderer rejection or close. Superseded is not a rollback.
     */
    fun submitStableTree(next: UiTree, completion: (CommitResult) -> Unit = {}) {
        usable()
        validateCallbacksAndComponents(next)
        TreeReconciler.validate(next)
        submit(Submission(next, completion))
    }

    private fun validateCallbacksAndComponents(next: UiTree) {
        next.nodes.firstOrNull { it.type !in components }?.let { throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_COMPONENT, nodeId = it.nodeId) }
        callbacks.validate(next)
    }

    private fun submit(submission: Submission) {
        TreeReconciler.diff(flight?.tree ?: tree, submission.tree)
        val superseded = pending
        pending = submission
        callbacks.stage(flight?.tree, pending?.tree)
        scheduleFlush()
        superseded?.let { complete(it, CommitResult.Superseded) }
    }

    fun attach() { usable(); state = State.ATTACHED; effect { renderer.attach() } }
    fun detach() { usable(); state = State.OPEN; effect { renderer.detach() } }
    fun setTheme(theme: ThemeSpec) { usable(); effect { renderer.setTheme(theme) } }

    fun execute(command: UiCommand) {
        usable()
        val id = when (command) {
            is UiCommand.Focus -> command.nodeId
            is UiCommand.Blur -> command.nodeId
            is UiCommand.ScrollTo -> command.nodeId
            is UiCommand.Edit -> command.nodeId
            is UiCommand.ShowSnackbar -> null
        }
        if (id != null && tree?.nodes?.none { it.nodeId == id } != false) throw ComposeUiContractException(ComposeUiErrorCodes.NODE_DETACHED, nodeId = id)
        val binding = (command as? UiCommand.ShowSnackbar)?.callbackId?.let(callbacks::reserveCommand)
        if (binding == null) {
            effect { renderer.execute(command) }
        } else {
            main.execute commandEffect@{
                if (closing.get()) return@commandEffect
                // A node event may have already queued the drain. Publish before execute so that
                // drain cannot observe a terminal command event before its generation is known.
                binding.publishGeneration(appliedGeneration)
                try { renderer.execute(command) }
                catch (failure: Exception) { script.post { callbacks.releaseCommand(binding); report(failure) } }
                catch (failure: LinkageError) { script.post { callbacks.releaseCommand(binding); report(failure) } }
            }
        }
    }

    private fun scheduleFlush() {
        if (flushScheduled || flight != null) return
        flushScheduled = true
        script.post {
            flushScheduled = false
            if (closing.get()) return@post
            val submission = pending ?: return@post
            val target = submission.tree
            pending = null
            val patches = TreeReconciler.diff(tree, target)
            if (patches.isEmpty()) {
                callbacks.retain(tree)
                complete(submission, CommitResult.Accepted(target, generation))
                return@post
            }
            val batch = UiPatchBatch(sessionId, generation + 1, patches)
            flight = submission
            main.execute {
                if (closing.get()) return@execute
                val failure = try { renderer.apply(batch); appliedGeneration = batch.generation; null } catch (error: Exception) { error } catch (error: LinkageError) { error }
                // Main completion is enqueued before any subsequently emitted view event.
                script.dispatch {
                    if (closing.get()) return@dispatch
                    flight = null
                    if (failure == null) {
                        tree = target
                        generation = batch.generation
                        callbacks.commit(target, generation, pending?.tree)
                        complete(submission, CommitResult.Accepted(target, generation))
                        if (pending != null) scheduleFlush()
                    } else {
                        val dependent = pending
                        pending = null
                        callbacks.retain(tree)
                        val rejection = CommitResult.Rejected(ComposeUiError.from(failure))
                        complete(submission, rejection)
                        dependent?.let { complete(it, rejection) }
                        report(failure)
                    }
                }
            }
        }
    }

    override fun enqueue(event: UiEvent) {
        if (closing.get() || event.sessionId != sessionId) return
        synchronized(eventLock) {
            if (closing.get()) return
            if (events.size == ComposeUiLimits.EVENT_QUEUE_CAPACITY) {
                val dropped = events.removeFirst()
                droppedCommands.add(dropped.generation, dropped.nodeId, dropped.type, dropped.callbackId)
                overflowWarning = true
            }
            events.addLast(event)
            if (eventScheduled) return
            eventScheduled = true
        }
        script.post(::drainEvents)
    }

    private fun drainEvents() {
        val (batch, overflow) = synchronized(eventLock) {
            eventScheduled = false
            droppedCommands.drain(callbacks)
            (events.toList() to overflowWarning).also { events.clear(); overflowWarning = false }
        }
        if (overflow) warn("Compose UI session $sessionId: event queue overflow")
        for (event in batch) {
            if (closing.get()) return
            if (event.callbackId == ComposeUiContract.SYSTEM_CALLBACK_ID && event.type == ComposeUiEvents.ERROR) {
                if (event.generation != generation) continue
                report(ComposeUiError(event.payload.getString(ComposeUiEventFields.CODE) ?: ComposeUiErrorCodes.RENDER_FAILED,
                    nodeId = event.nodeId.takeUnless { it == ComposeUiContract.NO_NODE_ID },
                    prop = event.payload.getString(ComposeUiEventFields.PROP)))
            } else {
                callbacks.resolve(event.generation, event.nodeId, event.type, event.callbackId)?.let { callback ->
                    try { callback(event) } catch (failure: Exception) { report(failure) } catch (failure: LinkageError) { report(failure) }
                }
            }
        }
    }

    private fun effect(action: () -> Unit) {
        main.execute {
            if (!closing.get()) try { action() } catch (failure: Exception) { script.post { report(failure) } }
            catch (failure: LinkageError) { script.post { report(failure) } }
        }
    }

    private fun report(failure: Throwable) {
        if (!closing.get()) onError(ComposeUiError.from(failure))
    }

    private fun complete(submission: Submission, result: CommitResult) {
        if (submission.completed) return
        submission.completed = true
        try { submission.completion?.invoke(result) }
        catch (failure: Exception) { report(failure) }
        catch (failure: LinkageError) { report(failure) }
    }

    /** Callable from engine/Activity/window teardown. No thread waits for another dispatcher. */
    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        state = State.CLOSED
        synchronized(eventLock) { events.clear(); droppedCommands.clear(); eventScheduled = false }
        script.dispatch {
            val outstanding = listOfNotNull(flight, pending)
            callbacks.close(); pending = null; flight = null; tree = null
            val rejection = CommitResult.Rejected(ComposeUiError(ComposeUiErrorCodes.SESSION_CLOSED))
            outstanding.forEach { complete(it, rejection) }
            try { onClosed() } finally { script.close() }
        }
        main.execute { try { renderer.dispose() } catch (_: Exception) { } catch (_: LinkageError) { } }
    }

    private fun confined() { check(script.isDispatchThread()) { "Compose UI mutations require the script dispatcher" } }
    private fun usable() {
        confined()
        if (closing.get()) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        if (state == State.NEW) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Session is not open")
    }
}

/** Owned by a script engine; P3 wires its close to the engine's existing exit hook. */
class ComposeSessionScope : AutoCloseable {
    private val sessions = LinkedHashSet<ComposeSession>()
    private var closed = false
    @Synchronized fun add(session: ComposeSession) {
        if (closed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        sessions.removeAll { it.state == ComposeSession.State.CLOSED }
        if (sessions.size >= ComposeUiLimits.MAX_SESSIONS_PER_ENGINE) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        sessions += session
    }
    override fun close() {
        val current = synchronized(this) { closed = true; sessions.toList().also { sessions.clear() } }
        current.forEach(ComposeSession::close)
    }
}
