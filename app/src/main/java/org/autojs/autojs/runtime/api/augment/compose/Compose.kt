package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.catalog.ComponentInvocation
import org.autojs.plugin.compose.api.model.*
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/** Catalog-derived script entry. The host owns handles/functions; only V1 data reaches the APK. */
class Compose internal constructor(
    private val runtime: ScriptRuntime,
    internal val platform: ComposeScriptPlatform,
    internal val values: ComposeScriptValues = ComposeScriptValues(platform::resolveImage),
) : Augmentable(runtime), Invokable, Closeable {
    constructor(runtime: ScriptRuntime) : this(runtime, AndroidComposeScriptPlatform(runtime))
    internal val scope = runtime.topLevelScope
    private val origin = Thread.currentThread()
    private val closed = AtomicBoolean()
    private val cleanupFinished = AtomicBoolean()
    private val cleanupChannel = platform.dispatcher.createControlChannel(::finishClose)
    internal val graph = ComposeNodeGraph(scope, values, ::invokeCallback, ::checkThread,
        translateError = { ComposeJsErrors.exception(scope, it) }, warn = platform::warn)
    internal val reactive = ComposeReactiveRuntime(scope, ::checkThread, { platform.post(0, it) }, ::reportRenderFailure)
    private val live = java.util.concurrent.CopyOnWriteArraySet<ComposeScriptSession>()
    private var nextSessionId = 1L
    private var defaultTheme: ThemeSpec? = null
    private var defaultThemeInput: Map<String, Any?> = emptyMap()

    init { ComposeJsErrors.installLocalizer(scope, platform::errorMessage) }

    override val keys = listOf(AugmentableKey("compose"))
    override val scriptErrorPassThroughInvoke = true
    override val globalAssignmentProperties: List<Pair<Any, Any?>> by lazy {
        listOf("ComposeError" to ComposeJsErrors.constructor(scope))
    }
    override val selfAssignmentGetters: List<Any> by lazy {
        listOf(
            "version" to Supplier { guarded { RhinoUtils.toJsValue(scope, platform.version()) } },
            "sessions" to Supplier { guarded { RhinoUtils.toJsValue(scope, live.filterNot { it.isClosed }.map { it.js }) } },
        )
    }
    override val selfAssignmentProperties: List<Pair<Any, Any?>> by lazy {
        val functions = linkedMapOf<String, (Array<out Any?>) -> Any?>(
            "isAvailable" to { args -> composeArguments(args, 0, name = "isAvailable"); platform.version() != null },
            "createElement" to { args ->
                if (args.isEmpty() || args[0] !is CharSequence) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "createElement requires a component name")
                create(args[0].toString(), args.drop(1).toTypedArray()).js
            },
            "modifier" to { args -> composeArguments(args, 0, name = "modifier"); ComposeModifier(values, scope) },
            "state" to { args -> composeArguments(args, 1, name = "state"); reactive.state(args[0]) },
            "ref" to { args -> composeArguments(args, 0, name = "ref"); ComposeRef(scope) },
            "batch" to { args -> composeArguments(args, 1, name = "batch"); batch(composeFunction(args[0], "batch")) },
            "mount" to { args -> mount(args).js },
            "floaty" to { args -> mount(args, floating = true).floatingWindow() },
            "post" to { args -> post(args); Undefined.instance },
            "theme" to { args -> theme(args) },
        )
        ComponentCatalog.V1.components.filter { it.invocation == ComponentInvocation.NODE }.forEach { spec ->
            functions[spec.name] = { args -> create(spec.name, args).js }
        }
        functions.map { (name, action) ->
            name to RhinoUtils.newBaseFunction(scope, name, { args ->
                if (name == "post") guardError { action(args) } else guarded { action(args) }
            }, RhinoUtils.NOT_CONSTRUCTABLE)
        } + ("ComposeError" to ComposeJsErrors.constructor(scope))
    }

    override fun invoke(vararg args: Any?): Any? = guarded { mount(args).js }

    private fun create(type: String, args: Array<out Any?>): ComposeNode {
        val first = args.firstOrNull()
        val isProps = composeNullish(first) || first is Map<*, *> ||
            first is Scriptable && first !is NativeArray && first !is Function && graph.node(first) == null
        val props = if (isProps) first else null
        val children = if (isProps) args.drop(1) else args.toList()
        return graph.create(type, props, children.takeIf { it.isNotEmpty() })
    }

    private fun mount(args: Array<out Any?>, floating: Boolean = false): ComposeScriptSession {
        usable()
        if (reactive.isRendering) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "A render callback cannot create another Compose mount")
        val method = if (floating) "floaty" else "mount"
        composeArguments(args, 1, 2, method)
        val options = values.objectProperties(args.getOrNull(1), method)
        val floatingOptions = if (floating) ComposeFloatyOptions.parse(args.getOrNull(1)) else null
        if (!floating && options.keys.any { it != "theme" }) throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP,
            prop = options.keys.first { it != "theme" })
        val previousUi = if (floating) emptyList() else live.filter { !it.isFloaty && !it.isClosed }
        if (live.count { !it.isClosed } - previousUi.size >= ComposeUiLimits.MAX_SESSIONS_PER_ENGINE || nextSessionId > Int.MAX_VALUE) {
            throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        }
        val source = args[0]
        if (source !is Function && graph.node(source) == null) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "mount requires a node or render function")
        val theme = options["theme"]?.let(values::theme) ?: defaultTheme
        val session = ComposeScriptSession(this, (nextSessionId++).toInt(), theme, floatingOptions, previousUi.firstOrNull())
        try {
            session.open(source)
            // The new content is valid and its renderer opened; replacing UI now closes prior mounts.
            previousUi.forEach { it.close() }
            live += session
            if (floating) platform.post(0) {
                // A callback/batch may still be validating its graph when floaty returns. Never
                // attach a native window for a mount that the surrounding transaction rolled back.
                if (!session.isClosed) {
                    if (graph.hasBinding(session)) try { session.attach() }
                    catch (failure: Throwable) { try { platform.report(failure) } finally { session.close() } }
                    else session.close()
                }
            } else session.attach()
            return session
        } catch (failure: Throwable) { session.close(); throw failure }
    }

    internal fun call(function: Function, receiver: Scriptable = scope.globalThis, args: Array<Any?> = emptyArray()): Any? =
        RhinoUtils.withRhinoContext(runtime) { context ->
            // The generic helper exits UI scripts on failure. A failed Compose render must remain
            // recoverable, so preserve the exception for our transaction and dependency rollback.
            if (org.mozilla.javascript.ScriptRuntime.hasTopCall(context)) function.call(context, scope, receiver, args)
            else org.mozilla.javascript.ScriptRuntime.doTopCall(function, context, scope, receiver, args, false)
        }

    internal fun batch(function: Function): Any? = graph.transaction { reactive.batch { call(function) } }

    internal fun post(args: Array<out Any?>, session: ComposeScriptSession? = null) {
        if (closed.get() || session?.isClosed == true) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
        composeArguments(args, 1, 2, "post")
        val function = composeFunction(args[0], "post")
        val delay = if (args.size == 1) 0.0 else (args[1] as? Number)?.toDouble()
            ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "delay")
        if (!delay.isFinite() || delay < 0 || delay > Int.MAX_VALUE || delay % 1.0 != 0.0) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "delay")
        platform.post(delay.toLong()) {
            if (!closed.get() && session?.isClosed != true) try { batch(function) }
            catch (failure: Exception) { platform.report(failure) }
            catch (failure: LinkageError) { platform.report(failure) }
        }
    }

    private fun theme(args: Array<out Any?>): Any? {
        composeArguments(args, 0, 1, "theme")
        if (args.isEmpty()) return RhinoUtils.toJsValue(scope, defaultThemeInput)
        val parsed = values.theme(args[0])
        defaultTheme = parsed
        // Return a fresh ordinary script object on reads, independent of later caller mutations.
        defaultThemeInput = mapOf("seed" to parsed.seedArgb, "colors" to parsed.colorOverrides,
            "dark" to parsed.dark, "dynamicColor" to parsed.dynamicColor,
            "typography" to mapOf("fontFamily" to parsed.fontFamily, "scale" to parsed.fontScale))
        live.lastOrNull { !it.isClosed }?.setTheme(parsed)
        return RhinoUtils.toJsValue(scope, defaultThemeInput)
    }

    /** Called by the existing ui.setContentView path, including ui.layout and layoutFile. */
    fun beforeSetContentView(view: Any) {
        live.filter { !it.isFloaty && it.mountedView != null && it.mountedView !== view }.toList().forEach {
            platform.warn("Compose UI session closed because ui content was replaced")
            it.close()
        }
    }
    internal fun ended(session: ComposeScriptSession) { live.remove(session) }
    internal fun checkThread() {
        if (Thread.currentThread() !== origin) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "Compose mutations require the owning script dispatcher; use compose.post")
    }
    internal fun usable() { checkThread(); if (closed.get()) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED) }
    internal fun <T> guardError(action: () -> T): T = ComposeJsErrors.guard(scope, action)
    internal fun <T> guarded(action: () -> T): T = guardError { usable(); action() }
    internal fun renderFailure(failure: Throwable): Throwable = when {
        failure is ComposeUiContractException || failure is ComposeUiError -> failure
        failure is JavaScriptException && (failure.value as? Scriptable)?.let { ComposeJsErrors.constructor(scope).hasInstance(it) } == true -> failure
        else -> ComposeUiError(ComposeUiErrorCodes.RENDER_FAILED, "Compose render failed", cause = failure)
    }
    internal fun reportRenderFailure(failure: Throwable) = platform.report(renderFailure(failure))

    private fun invokeCallback(function: Function, node: ComposeNode, event: UiEvent) {
        val payload = event.payload
        val args: Array<Any?> = when (event.type) {
            ComposeUiEvents.CHECKED_CHANGE -> arrayOf(payload.getBoolean(ComposeUiEventFields.CHECKED))
            ComposeUiEvents.FOCUS_CHANGE -> arrayOf(payload.getBoolean(ComposeUiEventFields.FOCUSED))
            ComposeUiEvents.VALUE_CHANGE -> if (node.type == ComposeUiComponents.TEXT_FIELD || node.type == ComposeUiComponents.OUTLINED_TEXT_FIELD) {
                arrayOf(payload.getString(ComposeUiEventFields.TEXT), RhinoUtils.toJsValue(scope, mapOf(
                    "start" to payload.getInt(ComposeUiEventFields.SELECTION_START), "end" to payload.getInt(ComposeUiEventFields.SELECTION_END))),
                    payload.getLong(ComposeUiEventFields.EDIT_SEQ))
            } else arrayOf(payload.getDouble(ComposeUiEventFields.VALUE))
            ComposeUiEvents.SCROLL -> arrayOf(payload.takeIf { it.containsKey(ComposeUiEventFields.FIRST_VISIBLE_INDEX) }?.getInt(ComposeUiEventFields.FIRST_VISIBLE_INDEX),
                payload.getInt(ComposeUiEventFields.OFFSET))
            else -> emptyArray()
        }
        graph.transaction { reactive.batch { call(function, node.js, args) } }
    }

    override fun close() {
        val first = closed.compareAndSet(false, true)
        // Close core sessions immediately so teardown never waits for a script dispatcher.
        if (first) live.toList().forEach { it.stopRenderer() }
        // A force-stop can reject the earlier queued cleanup while its looper is exiting. The
        // runtime's owner-thread exit hook can finish it directly even after close was requested.
        if (platform.dispatcher.isDispatchThread()) finishClose()
        else if (first) cleanupChannel.request()
    }
    private fun finishClose() {
        checkThread()
        if (!cleanupFinished.compareAndSet(false, true)) return
        try {
            live.toList().forEach { it.close() }
            reactive.close(); graph.close(); live.clear()
        } finally { cleanupChannel.close(); platform.close() }
    }
}
