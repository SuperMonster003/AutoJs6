package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.core.plugin.compose.ComposeSession
import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.interop.AndroidViewBindingV1
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.ScriptableObject.PERMANENT
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One script-visible mount, binding stable handles to the core's acknowledged transaction stream. */
internal class ComposeScriptSession(private val owner: Compose, val id: Int, private val initialTheme: ThemeSpec?,
    private val floatyOptions: ComposeFloatyOptions? = null, private var replacingUi: ComposeNodeBinding? = null,
    container: Any? = null, private val dialogOptions: ComposeDialogOptions? = null) : ComposeNodeBinding, Closeable {
    var container: Any? = container
        private set
    private val closed = AtomicBoolean()
    override val isClosed get() = closed.get()
    val isFloaty get() = floatyOptions != null
    val isDialog get() = dialogOptions != null
    val isUiRoot get() = !isFloaty && !isDialog && container == null
    private var window: ComposeFloatyControl? = null
    private var windowFacade: ComposeFloatyWindow? = null
    private val lifetime = AtomicReference<Closeable?>()
    private val packageWatch = AtomicReference<Closeable?>()
    private val finished = AtomicBoolean()
    @Volatile private lateinit var core: ComposeSession
    private val startupFailure = AtomicReference<Throwable?>()
    private var reaction: ComposeReactiveRuntime.Reaction? = null
    private var root: ComposeNode? = null
    private var accepted: ComposeNodeSnapshot? = null
    private var pending: ComposeNodeSnapshot? = null
    private var renderer: org.autojs.autojs.core.plugin.compose.ComposeSessionRenderer? = null
    private val submitted = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ComposeNodeSnapshot, Boolean>())
    internal fun nativeViews(): Set<android.view.View> = (submitted.toList() + listOfNotNull(accepted))
        .flatMap { it.states.values.mapNotNull { state -> state.nativeView?.view } }.toSet()
    internal fun nativeLease(node: ComposeNode, view: android.view.View): ComposeNativeViewLease? =
        (listOfNotNull(accepted) + submitted).asSequence().mapNotNull { it.states[node]?.nativeView }.firstOrNull { it.view === view }
    private val callbacks = HashMap<Int, Int>()
    private data class PendingCommand(val command: UiCommand, val action: Function? = null, val dismiss: Function? = null) {
        val callbackCount get() = (if (action == null) 0 else 1) + (if (dismiss == null || dismiss === action) 0 else 1)
    }
    private val commands = ArrayList<PendingCommand>()
    private val notificationCallbacks = HashMap<Int, Int>()
    private var commandsScheduled = false
    private val closeListeners = ArrayList<Function>()
    var mountedView: Any? = null
        private set
    val js = NativeObject().apply {
        RhinoUtils.initNativeObjectPrototype(owner.scope, this)
        defineProperty("root", { if (isClosed) null else root?.js }, null, READONLY or PERMANENT)
        defineProperty("isClosed", RhinoUtils.newBaseFunction(owner.scope, "isClosed", { args ->
            composeArguments(args, 0, name = "isClosed"); isClosed
        }, RhinoUtils.NOT_CONSTRUCTABLE), READONLY or PERMANENT)
        fun method(name: String, action: (Array<out Any?>) -> Any?) {
            defineProperty(name, RhinoUtils.newBaseFunction(owner.scope, name, { args ->
                ComposeJsErrors.guard(owner.scope) { action(args) }
            }, RhinoUtils.NOT_CONSTRUCTABLE), READONLY or PERMANENT)
        }
        method("update") { args -> usable(); composeArguments(args, 1, name = "update"); owner.batch(composeFunction(args[0], "update")) }
        method("post") { args -> owner.post(args, this@ComposeScriptSession); Undefined.instance }
        method("close") { args -> composeArguments(args, 0, name = "close"); close(); Undefined.instance }
        method("on") { args ->
            usable(); composeArguments(args, 2, name = "on")
            if ((args[0] as? CharSequence)?.toString() != "close") throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Session event must be close")
            checkCallbackBudget(additional = 1)
            closeListeners += composeFunction(args[1], "on"); this
        }
        method("showSnackbar") { args -> showSnackbar(args); this }
        preventExtensions()
    }

    fun open(source: Any?) {
        val targetContainer = container
        val failureHandler: (Throwable) -> Unit = { failure ->
            startupFailure.compareAndSet(null, failure)
            if (::core.isInitialized) startupFailure.getAndSet(null)?.let(core::failInitialization)
        }
        val opened = if (dialogOptions != null) owner.platform.createDialogRenderer(id, dialogOptions, { mountedView = it }, ::close, failureHandler)
        else if (targetContainer != null) owner.platform.createContainerRenderer(id, targetContainer, { mountedView = it }, ::close)
        else if (floatyOptions == null) owner.platform.createRenderer(id, { mountedView = it }, ::close)
        else owner.platform.createFloatyRenderer(id, floatyOptions, { mountedView = it }, ::close, failureHandler)
        window = opened.window
        renderer = opened.renderer
        lifetime.set(opened.lifetime)
        if (isClosed) {
            lifetime.getAndSet(null)?.close()
            opened.window?.close()
            throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        }
        // A core session may close independently; the shared engine dispatcher remains usable.
        val dispatcher = object : ScriptUiDispatcher by owner.platform.dispatcher { override fun close() = Unit }
        try {
            core = ComposeSession(id, dispatcher, owner.platform.mainExecutor, opened.renderer, opened.components,
                { failure -> owner.platform.report(failure) }, owner.platform::warn, ::finish,
                closeEventType = if (isDialog) org.autojs.plugin.compose.api.dialog.DialogInteropV1.DISMISS_REQUEST else null)
        } catch (failure: Throwable) {
            // Native ownership is already provisional when control-channel reservation can fail.
            // No core exists to dispose these resources during the outer session.close().
            owner.platform.mainExecutor.execute {
                try { try { opened.window?.close() } finally { opened.renderer.dispose() } }
                catch (_: Exception) { }
                catch (_: LinkageError) { }
            }
            throw failure
        }
        opened.watchInvalidation?.invoke(core::failInitialization)?.let { watch ->
            packageWatch.set(watch)
            if (isClosed) packageWatch.getAndSet(null)?.close()
        }
        if (isClosed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        core.open()
        startupFailure.getAndSet(null)?.let(core::failInitialization)
        initialTheme?.let(core::setTheme)
        if (source is Function) {
            val render = owner.reactive.reaction {
                try {
                    root = owner.graph.render(root) {
                        owner.graph.root(owner.call(source)) ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
                            "render must return one ComposeNode")
                    }
                } catch (failure: Throwable) {
                    throw owner.renderFailure(failure)
                }
            }
            // Publish ownership before the first run so failed mount cleanup releases subscriptions.
            reaction = render
            render.run()
        } else root = owner.graph.node(source)!!
        try { owner.graph.mount(requireNotNull(root), this, replacingUi) } finally { replacingUi = null }
    }
    fun floatingWindow(): ComposeFloatyWindow {
        usable()
        return windowFacade ?: ComposeFloatyWindow(owner, this, requireNotNull(window), requireNotNull(floatyOptions).raw).also { windowFacade = it }
    }
    fun attach() { usable(); core.attach() }
    fun setTheme(theme: ThemeSpec) { usable(); core.setTheme(theme) }

    override fun publish(snapshot: ComposeNodeSnapshot) {
        usable()
        val previous = pending
        val previousRoot = root
        val mapped = HashMap<Int, Int>()
        try {
            checkCallbackBudget(snapshot.scriptCallbackCount)
            snapshot.callbacks.forEach { (token, function) ->
                mapped[token] = callbacks[token]?.takeIf(core::containsCallback) ?: core.registerCallback(function).also { callbacks[token] = it }
            }
            val tree = UiTree(snapshot.tree.rootId, snapshot.tree.nodes.map { node ->
                UiNode(node.nodeId, node.type, node.key, node.props, node.modifier.map { operation ->
                    if (operation.name == ComposeUiModifiers.CLICKABLE) {
                        val token = (operation.args[0] as UiValue.Num).value.toInt()
                        ModifierOp(operation.name, listOf(UiValue.Num(mapped.getValue(token).toDouble())) + operation.args.drop(1), operation.scope)
                    } else operation
                }, node.children, node.slots, node.callbacks.mapValues { mapped.getValue(it.value) })
            })
            pending = snapshot
            root = snapshot.root
            val views = snapshot.states.mapNotNull { (node, state) -> state.nativeView?.let { AndroidViewBindingV1(node.stableId, it.id, it.view) } }
            owner.validateNativeViews(this, views)
            owner.platform.validateViews(views, container, requireNotNull(renderer))
            submitted += snapshot
            core.submitStableTree(tree, views) { result ->
                submitted.remove(snapshot)
                when (result) {
                    is ComposeSession.CommitResult.Accepted -> if (!isClosed) {
                        accepted = snapshot
                        if (pending === snapshot) pending = null
                        owner.graph.accept(this, snapshot)
                        pruneCallbacks()
                        scheduleCommands()
                    }
                    is ComposeSession.CommitResult.Rejected -> if (!isClosed) {
                        if (pending === snapshot) pending = null
                        owner.graph.reject(this, snapshot)
                        root = accepted?.root ?: root
                        commands.clear(); pruneCallbacks()
                    }
                    ComposeSession.CommitResult.Superseded -> Unit
                }
            }
            core.discardUnusedCallbacks()
            pruneCallbacks()
        } catch (failure: Throwable) {
            submitted.remove(snapshot)
            pending = previous
            root = previousRoot
            core.discardUnusedCallbacks(); pruneCallbacks()
            throw failure
        }
    }
    private fun pruneCallbacks() {
        callbacks.entries.removeAll { !core.containsCallback(it.value) }
        notificationCallbacks.keys.removeAll { !core.containsCallback(it) }
    }
    private fun checkCallbackBudget(nodeCallbacks: Int = maxOf(accepted?.scriptCallbackCount ?: 0, pending?.scriptCallbackCount ?: 0), additional: Int = 0) {
        pruneCallbacks()
        if (nodeCallbacks + closeListeners.size + notificationCallbacks.values.sum() + commands.sumOf { it.callbackCount } + additional > ComposeUiLimits.MAX_CALLBACKS_PER_SESSION) {
            throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        }
    }

    override fun execute(command: UiCommand) {
        usable()
        if (commands.size >= ComposeUiLimits.MAX_PATCHES_PER_BATCH) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        if (command is UiCommand.Edit) {
            val index = commands.indexOfLast { (it.command as? UiCommand.Edit)?.let { edit -> edit.nodeId == command.nodeId && edit.editSeq == command.editSeq } == true }
            if (index >= 0) {
                // Graph.edit supplies the complete final editor snapshot, including the caret.
                commands[index] = PendingCommand(command)
            } else commands += PendingCommand(command)
        } else commands += PendingCommand(command)
        scheduleCommands()
    }
    private fun scheduleCommands() {
        if (commandsScheduled || isClosed || commands.isEmpty()) return
        commandsScheduled = true
        try {
            owner.platform.post(0) {
                commandsScheduled = false
                if (!isClosed && pending == null) {
                    val ready = commands.toList().also { commands.clear() }
                    ready.forEach { request ->
                        try { executePending(request) } catch (failure: Exception) { owner.platform.report(failure) }
                    }
                }
            }
        } catch (failure: Throwable) { commandsScheduled = false; commands.clear(); throw failure }
    }
    private fun executePending(request: PendingCommand) {
        val command = request.command
        if (command !is UiCommand.ShowSnackbar || request.callbackCount == 0) { core.execute(command); return }
        var callback = 0
        callback = core.registerCallback { event ->
            notificationCallbacks.remove(callback)
            (if (event.type == ComposeUiEvents.ACTION) request.action else request.dismiss)?.let { owner.call(it, js) }
        }
        notificationCallbacks[callback] = request.callbackCount
        try { core.execute(UiCommand.ShowSnackbar(command.message, command.actionLabel, command.duration, callback)) }
        catch (failure: Throwable) { core.discardUnusedCallbacks(); pruneCallbacks(); throw failure }
    }

    private fun showSnackbar(args: Array<out Any?>) {
        usable(); composeArguments(args, 1, 2, "showSnackbar")
        val message = args[0] as? CharSequence ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "message")
        val options = owner.values.objectProperties(args.getOrNull(1), "Snackbar")
        val allowed = setOf("actionLabel", "duration", "onAction", "onDismiss")
        options.keys.firstOrNull { it !in allowed }?.let { throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP, prop = it) }
        val durationName = options["duration"]?.let { it as? CharSequence
            ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "duration") }?.toString() ?: "short"
        val duration = when (durationName) {
            "short" -> SnackbarDuration.SHORT; "long" -> SnackbarDuration.LONG; "indefinite" -> SnackbarDuration.INDEFINITE
            else -> throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "duration")
        }
        val action = options["onAction"]?.takeUnless(::composeNullish)?.let { composeFunction(it, "onAction") }
        val dismiss = options["onDismiss"]?.takeUnless(::composeNullish)?.let { composeFunction(it, "onDismiss") }
        val label = options["actionLabel"]?.takeUnless(::composeNullish)?.let {
            (owner.values.property(ComposeUiComponents.SNACKBAR, ComposeUiProps.ACTION_LABEL, it).value as UiValue.Str).value
        }
        val text = (owner.values.property(ComposeUiComponents.SNACKBAR, ComposeUiProps.MESSAGE, message).value as UiValue.Str).value
        val request = PendingCommand(UiCommand.ShowSnackbar(text, label, duration), action, dismiss)
        if (commands.size >= ComposeUiLimits.MAX_PATCHES_PER_BATCH) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        checkCallbackBudget(additional = request.callbackCount)
        commands += request
        // Initial mount and same-tick tree updates must commit before selecting a native host.
        scheduleCommands()
    }

    private fun usable() {
        owner.checkThread()
        if (isClosed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
    }
    fun stopRenderer() = close()
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            if (owner.platform.dispatcher.isDispatchThread()) {
                if (::core.isInitialized) core.close() else finish()
            }
            return
        }
        // Before core construction, open() still owns startup on the script thread. Its cancellation
        // check/catch finishes cleanup there; a native callback must not use the ordinary post queue.
        try { if (::core.isInitialized) core.close() else if (owner.platform.dispatcher.isDispatchThread()) finish() }
        finally { releaseOwnership() }
    }
    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        closed.set(true)
        startupFailure.set(null)
        replacingUi = null
        reaction?.close(); reaction = null
        owner.graph.unmount(this)
        root = null; pending = null; accepted = null; submitted.clear(); renderer = null; container = null; callbacks.clear(); commands.clear(); notificationCallbacks.clear(); mountedView = null
        owner.ended(this)
        val listeners = closeListeners.toList().also { closeListeners.clear() }
        try {
            listeners.forEach { function ->
                try { owner.call(function, js) } catch (failure: Exception) { owner.platform.report(failure) }
                catch (failure: LinkageError) { owner.platform.report(failure) }
            }
        } finally {
            window = null; windowFacade = null
            releaseOwnership()
        }
    }

    private fun releaseOwnership() {
        try { packageWatch.getAndSet(null)?.close() } finally { lifetime.getAndSet(null)?.close() }
    }
}
