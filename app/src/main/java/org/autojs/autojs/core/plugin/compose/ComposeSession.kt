package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.interop.AndroidViewBindingV1
import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Android effects live behind this seam; transactions and scheduling are tested without a Looper. */
interface ComposeSessionRenderer {
    fun open(sink: ComposeUiEventSink)
    fun attach()
    fun detach()
    fun apply(batch: UiPatchBatch)
    fun apply(batch: UiPatchBatch, views: List<AndroidViewBindingV1>) = apply(batch)
    fun validateViews(views: List<AndroidViewBindingV1>) = Unit
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
    private val closeEventType: String? = null,
) : ComposeUiEventSink, AutoCloseable {
    enum class State { NEW, OPEN, ATTACHED, CLOSED }
    sealed interface CommitResult {
        data class Accepted(val tree: UiTree, val generation: Long) : CommitResult
        data class Rejected(val error: ComposeUiError) : CommitResult
        data object Superseded : CommitResult
    }
    private class Submission(val tree: UiTree, val completion: ((CommitResult) -> Unit)?, val views: List<AndroidViewBindingV1> = emptyList()) {
        var completed = false
    }
    private class Receipt(val submission: Submission, val failure: Throwable?)
    @Volatile var state = State.NEW
        private set
    private val closing = AtomicBoolean()
    private val initializationFailed = AtomicBoolean()
    private val scriptCleanupFinished = AtomicBoolean()
    private val callbacks = CallbackRegistry()
    private val catalog = if (AndroidViewInteropV1.COMPONENT in components) AndroidViewInteropV1.catalog
        else org.autojs.plugin.compose.api.catalog.ComponentCatalog.V1
    private val reconciler = TreeReconciler(catalog) { warn("Compose UI session $sessionId: list children have no key") }
    private val eventLock = Any()
    private val events = ArrayDeque<UiEvent>()
    private val droppedCommands = CommandResultEvictions()
    private var overflowWarning = false
    // Native producers write only this bounded mailbox. A receipt has one slot because a second
    // frame cannot start until the script acknowledges the first. Command tickets are bounded by
    // the callback registry; ordinary diagnostic failures share the event queue's count bound.
    private var receipt: Receipt? = null
    private var initializationFailure: Throwable? = null
    private var requestedClose = false
    private var requestedCloseFailure: ComposeUiError? = null
    private val nativeFailures = ArrayDeque<Throwable>()
    private val failedCommands = LinkedHashSet<CallbackRegistry.CommandBinding>()
    private var discardFailedCommands = false
    private var failureOverflow = false
    private var pending: Submission? = null
    private var flight: Submission? = null
    private var flushScheduled = false
    // Main-thread value: a command may run after apply succeeds but before its script acknowledgement.
    private var appliedGeneration = 0L
    var tree: UiTree? = null
        private set
    private var treeViews: List<AndroidViewBindingV1> = emptyList()
    var generation = 0L
        private set
    val callbackCount get() = callbacks.size

    init { require(sessionId > 0) }
    private val control = script.createControlChannel(::drainControls)

    fun open() {
        confined()
        if (state != State.NEW) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
        state = State.OPEN
        main.execute {
            if (canRender()) try { renderer.open(this) }
            catch (failure: Exception) { failInitialization(failure) }
            catch (failure: LinkageError) { failInitialization(failure) }
        }
    }

    /** Initialization cannot recover by applying another frame, unlike an ordinary apply failure. */
    fun failInitialization(failure: Throwable) {
        synchronized(eventLock) {
            if (closing.get() || !initializationFailed.compareAndSet(false, true)) return
            initializationFailure = failure
        }
        control.request()
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
    fun submitStableTree(next: UiTree, completion: (CommitResult) -> Unit = {}) = submitStableTree(next, emptyList(), completion)

    fun submitStableTree(next: UiTree, views: List<AndroidViewBindingV1>, completion: (CommitResult) -> Unit = {}) {
        usable()
        validateCallbacksAndComponents(next)
        TreeReconciler.validate(next, catalog)
        submit(Submission(next, completion, views.toList()))
    }

    private fun validateCallbacksAndComponents(next: UiTree) {
        next.nodes.firstOrNull { it.type !in components }?.let {
            throw ComposeUiContractException(if (it.type == AndroidViewInteropV1.COMPONENT) ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE
                else ComposeUiErrorCodes.UNKNOWN_COMPONENT, nodeId = it.nodeId)
        }
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

    fun attach() {
        usable()
        state = State.ATTACHED
        main.execute {
            if (canRender()) try { renderer.attach() }
            catch (failure: Exception) { failInitialization(failure) }
            catch (failure: LinkageError) { failInitialization(failure) }
        }
    }
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
                if (!canRender()) return@commandEffect
                // A node event may have already queued the drain. Publish before execute so that
                // drain cannot observe a terminal command event before its generation is known.
                binding.publishGeneration(appliedGeneration)
                try { renderer.execute(command) }
                catch (failure: Exception) { nativeFailure(failure, binding) }
                catch (failure: LinkageError) { nativeFailure(failure, binding) }
            }
        }
    }

    private fun scheduleFlush() {
        if (flushScheduled || flight != null) return
        flushScheduled = true
        control.request()
    }

    private fun flush() {
        if (!flushScheduled || flight != null) return
        flushScheduled = false
        if (closing.get()) return
        val submission = pending ?: return
        val target = submission.tree
        pending = null
        val diff = TreeReconciler.diff(tree, target)
        val sameViews = submission.views.size == treeViews.size && submission.views.all { binding ->
            treeViews.any { it.nodeId == binding.nodeId && it.leaseId == binding.leaseId && it.view === binding.view }
        }
        val patches = if (diff.isEmpty() && !sameViews) listOf(UiPatch.SetRoot(target)) else diff
        if (patches.isEmpty()) {
            callbacks.retain(tree)
            complete(submission, CommitResult.Accepted(target, generation))
            return
        }
        val batch = UiPatchBatch(sessionId, generation + 1, patches)
        flight = submission
        main.execute {
            if (!canRender()) return@execute
            val failure = try { renderer.apply(batch, submission.views); appliedGeneration = batch.generation; null }
                catch (error: Exception) { error } catch (error: LinkageError) { error }
            synchronized(eventLock) {
                if (closing.get()) return@execute
                receipt = Receipt(submission, failure)
            }
            // The reserved control drain handles this receipt before events of its generation.
            control.request()
        }
    }

    private fun acknowledge(result: Receipt) {
        val submission = result.submission
        val target = submission.tree
        flight = null
        val failure = result.failure
        if (failure == null) {
            tree = target
            treeViews = submission.views
            generation = appliedGeneration
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

    override fun enqueue(event: UiEvent) {
        if (closing.get() || event.sessionId != sessionId) return
        if (closeEventType != null && event.type == closeEventType) {
            val failure = if (event.payload.containsKey(ComposeUiEventFields.CODE)) ComposeUiError.presentationFailure(
                event.payload.getString(ComposeUiEventFields.CODE), event.payload.getString(ComposeUiEventFields.PROP)) else null
            enqueueLifecycle(event, failure)
            return
        }
        synchronized(eventLock) {
            if (closing.get()) return
            if (events.size == ComposeUiLimits.EVENT_QUEUE_CAPACITY) {
                val dropped = events.removeFirst()
                droppedCommands.add(dropped.generation, dropped.nodeId, dropped.type, dropped.callbackId)
                overflowWarning = true
            }
            events.addLast(event)
        }
        control.request()
    }

    /** Payload decoding is Android-specific; the ownership/mailbox policy is independently testable. */
    internal fun enqueueLifecycle(event: UiEvent, failure: ComposeUiError?) {
        if (closing.get() || closeEventType == null || event.type != closeEventType || event.sessionId != sessionId ||
            event.nodeId != ComposeUiContract.NO_NODE_ID || event.callbackId != ComposeUiContract.SYSTEM_CALLBACK_ID) return
        synchronized(eventLock) {
            if (closing.get()) return
            // Window lifetime outlives tree generations and must survive ordinary event overflow.
            requestedClose = true
            if (requestedCloseFailure == null) requestedCloseFailure = failure
        }
        control.request()
    }

    private fun drainEvents() {
        val (batch, overflow) = synchronized(eventLock) {
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
            if (canRender()) try { action() } catch (failure: Exception) { nativeFailure(failure) }
            catch (failure: LinkageError) { nativeFailure(failure) }
        }
    }

    private fun nativeFailure(failure: Throwable, command: CallbackRegistry.CommandBinding? = null) {
        synchronized(eventLock) {
            if (closing.get()) return
            if (command != null && !discardFailedCommands) {
                if (failedCommands.size == ComposeUiLimits.MAX_CALLBACKS_PER_SESSION) {
                    failedCommands.clear(); discardFailedCommands = true
                } else failedCommands += command
            }
            if (nativeFailures.size == ComposeUiLimits.EVENT_QUEUE_CAPACITY) {
                nativeFailures.removeFirst(); failureOverflow = true
            }
            nativeFailures.addLast(failure)
        }
        control.request()
    }

    private fun drainControls() {
        confined()
        if (closing.get()) { finishOnScriptThread(); return }
        val fatal = synchronized(eventLock) { initializationFailure.also { initializationFailure = null } }
        if (fatal != null) {
            try { report(fatal) } finally { close() }
            return
        }
        val completed = synchronized(eventLock) { receipt.also { receipt = null } }
        completed?.let(::acknowledge)
        if (closing.get()) { finishOnScriptThread(); return }
        val dismiss = synchronized(eventLock) {
            (requestedClose to requestedCloseFailure).also { requestedClose = false; requestedCloseFailure = null }
        }
        if (dismiss.first) { try { dismiss.second?.let(::report) } finally { close() }; return }
        val failures = synchronized(eventLock) {
            if (discardFailedCommands) callbacks.discardAllCommands()
            else failedCommands.forEach(callbacks::releaseCommand)
            failedCommands.clear(); discardFailedCommands = false
            val overflow = failureOverflow.also { failureOverflow = false }
            nativeFailures.toList().also { nativeFailures.clear() } to overflow
        }
        if (failures.second) warn("Compose UI session $sessionId: native error queue overflow")
        failures.first.forEach(::report)
        drainEvents()
        flush()
    }

    private fun report(failure: Throwable) {
        if (!closing.get()) onError(ComposeUiError.from(failure))
    }

    private fun canRender() = !closing.get() && !initializationFailed.get()

    private fun complete(submission: Submission, result: CommitResult) {
        if (submission.completed) return
        submission.completed = true
        try { submission.completion?.invoke(result) }
        catch (failure: Exception) { report(failure) }
        catch (failure: LinkageError) { report(failure) }
    }

    /** Callable from engine/Activity/window teardown. No thread waits for another dispatcher. */
    override fun close() {
        if (!closing.compareAndSet(false, true)) {
            // A quitting Looper can reject the original cross-thread cleanup. Engine teardown
            // still has an opportunity to release script-owned state on the originating thread.
            if (script.isDispatchThread()) finishOnScriptThread()
            return
        }
        state = State.CLOSED
        synchronized(eventLock) { events.clear(); droppedCommands.clear() }
        try {
            if (script.isDispatchThread()) finishOnScriptThread() else control.request()
        } finally {
            main.execute { try { renderer.dispose() } catch (_: Exception) { } catch (_: LinkageError) { } }
        }
    }

    private fun finishOnScriptThread() {
        confined()
        if (!scriptCleanupFinished.compareAndSet(false, true)) return
        control.close()
        synchronized(eventLock) {
            events.clear(); droppedCommands.clear(); receipt = null; initializationFailure = null; requestedClose = false; requestedCloseFailure = null
            nativeFailures.clear(); failedCommands.clear(); discardFailedCommands = false; failureOverflow = false
        }
        val outstanding = listOfNotNull(flight, pending)
        callbacks.close(); pending = null; flight = null; tree = null; treeViews = emptyList(); flushScheduled = false
        val rejection = CommitResult.Rejected(ComposeUiError(ComposeUiErrorCodes.SESSION_CLOSED))
        outstanding.forEach { complete(it, rejection) }
        try { onClosed() } finally { script.close() }
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
