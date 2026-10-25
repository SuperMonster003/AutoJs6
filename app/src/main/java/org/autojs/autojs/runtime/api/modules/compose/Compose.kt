package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ui.TypeScriptTsxFragment
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.catalog.ComponentInvocation
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.IdentityHashMap

/** Catalog-derived script entry. The host owns handles/functions; only V1 data reaches the APK. */
class Compose internal constructor(
    private val runtime: ScriptRuntime,
    internal val platform: ComposeScriptPlatform,
    internal val values: ComposeScriptValues = ComposeScriptValues(platform::resolveImage),
) : ArgumentGuards(), Closeable {
    constructor(runtime: ScriptRuntime) : this(runtime, AndroidComposeScriptPlatform(runtime))
    internal val scope = runtime.topLevelScope
    private val origin = Thread.currentThread()
    private val closed = AtomicBoolean()
    private val cleanupFinished = AtomicBoolean()
    private val cleanupChannel = platform.dispatcher.createControlChannel(::finishClose)
    internal val graph = ComposeNodeGraph(scope, values, ::invokeCallback, ::checkThread,
        translateError = { ComposeJsErrors.exception(scope, it) }, warn = platform::warn, prepareView = ::prepareNativeView)
    internal val reactive = ComposeReactiveRuntime(scope, ::checkThread, { platform.post(0, it) }, ::reportRenderFailure)
    private val live = java.util.concurrent.CopyOnWriteArraySet<ComposeScriptSession>()
    private var nextSessionId = 1L
    private var defaultTheme: ThemeSpec? = null
    private var defaultThemeInput: Map<String, Any?> = emptyMap()
    @Volatile private var preparingView = false
    private var mountInProgress = false
    private val tsxFactories = IdentityHashMap<Function, String>()
    /** The render currently allowed to call compose.memo; set only by a session's render reaction. */
    internal var memoFrame: ComposeMemoCache.Frame? = null

    init { ComposeJsErrors.installLocalizer(scope, platform::errorMessage) }

    /** The value of the `compose.version` getter: the version of the Compose UI plugin, null without it. */
    internal fun versionValue(): Any? = guarded { RhinoUtils.toJsValue(scope, platform.version()) }

    /** The value of the `compose.sessions` getter: the mounted sessions that are not closed. */
    internal fun sessionsValue(): Any? = guarded { RhinoUtils.toJsValue(scope, live.filterNot { it.isClosed }.map { it.js }) }

    /**
     * The data properties the `compose` script module defines on its module object, in their order (its install hook
     * defines them, roadmap P5.2).
     * zh-CN: `compose` 脚本模块在模块对象上定义的数据属性, 按顺序 (由其 install 钩子定义, P5.2).
     */
    val scriptProperties: List<Pair<Any, Any?>> by lazy {
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
            "memo" to { args -> memo(args) },
            "mount" to { args -> mount(args).js },
            "attach" to { args ->
                composeArguments(args, 2, 3, "attach")
                mount(args.drop(1).toTypedArray(), container = platform.container(args[0])).js
            },
            "floaty" to { args -> mount(args, floating = true).floatingWindow() },
            "dialog" to { args -> mount(args, dialog = true).js },
            "post" to { args -> post(args); Undefined.instance },
            "theme" to { args -> theme(args) },
        )
        org.autojs.plugin.compose.api.v2.ComposeUiV2.interopCatalog.components.filter { it.invocation == ComponentInvocation.NODE }.forEach { spec ->
            functions[spec.name] = { args -> create(spec.name, args).js }
        }
        functions.map { (name, action) ->
            name to RhinoUtils.newBaseFunction(scope, name, { args ->
                if (name == "post") guardError { action(args) } else guarded { action(args) }
            }, RhinoUtils.NOT_CONSTRUCTABLE).also { function ->
                if (org.autojs.plugin.compose.api.v2.ComposeUiV2.interopCatalog.component(name)?.invocation == ComponentInvocation.NODE) tsxFactories[function] = name
            }
        } + ("ComposeError" to ComposeJsErrors.constructor(scope))
    }

    fun invoke(vararg args: Any?): Any? = guarded { mount(args).js }

    internal fun isTsxType(type: Any?): Boolean = type is Function ||
        type is String && (type.startsWith("compose:") || org.autojs.plugin.compose.api.v2.ComposeUiV2.interopCatalog.component(type) != null)

    internal fun createTsx(type: Any?, props: Any?, children: List<Any?>): Any = guarded {
        val name = when (type) {
            is String -> type.removePrefix("compose:")
            is Function -> tsxFactories[type] ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
            else -> throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
        }
        graph.create(name, props, children.takeIf { it.isNotEmpty() }).js
    }

    private fun create(type: String, args: Array<out Any?>): ComposeNode {
        val first = args.firstOrNull()
        // NativeObject implements Map. Preserve genuine (including foreign) node identity before
        // deciding whether the first argument is a property bag or the first positional child.
        val rawFirst = ComposeScriptValues.unwrap(first)
        val isNode = rawFirst is ComposeNode || rawFirst is ComposeNodeNativeObject
        val isProps = !isNode && (composeNullish(first) || first is Map<*, *> ||
            first is Scriptable && first !is NativeArray && first !is Function && graph.node(first) == null &&
            rawFirst !is TypeScriptTsxFragment)
        val props = if (isProps) first else null
        val children = if (isProps) args.drop(1) else args.toList()
        return graph.create(type, props, children.takeIf { it.isNotEmpty() })
    }

    private fun mount(args: Array<out Any?>, floating: Boolean = false, container: Any? = null, dialog: Boolean = false): ComposeScriptSession {
        usable()
        if (reactive.isRendering || preparingView || mountInProgress) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "Cannot create a Compose mount during rendering, View preparation or another mount")
        mountInProgress = true
        return try { openMount(args, floating, container, dialog) } finally { mountInProgress = false }
    }

    private fun openMount(args: Array<out Any?>, floating: Boolean, container: Any?, dialog: Boolean): ComposeScriptSession {
        val method = if (dialog) "dialog" else if (floating) "floaty" else if (container != null) "attach" else "mount"
        composeArguments(args, 1, 2, method)
        val options = values.objectProperties(args.getOrNull(1), method)
        val floatingOptions = if (floating) ComposeFloatyOptions.parse(args.getOrNull(1)) else null
        val dialogOptions = if (dialog) ComposeDialogOptions.parse(args.getOrNull(1)) else null
        if (!floating && !dialog && options.keys.any { it != "theme" }) throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP,
            prop = options.keys.first { it != "theme" })
        val previousUi = if (floating || dialog) emptyList() else live.filter {
            !it.isClosed && if (container != null) it.container === container else it.isUiRoot
        }
        if (live.count { !it.isClosed } - previousUi.size >= ComposeUiLimits.MAX_SESSIONS_PER_ENGINE || nextSessionId > Int.MAX_VALUE) {
            throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        }
        val source = args[0].let { value -> if (value is Function) value else graph.root(value)
            ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "mount requires a node or render function") }
        val theme = options["theme"]?.let(values::theme) ?: defaultTheme
        val session = ComposeScriptSession(this, (nextSessionId++).toInt(), theme, floatingOptions, previousUi.firstOrNull(), container, dialogOptions)
        try {
            session.open(source)
            // The new content is valid and its renderer opened; replacing UI now closes prior mounts.
            previousUi.forEach { it.close() }
            live += session
            if (floating || container != null || dialog) platform.post(0) {
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

    private fun prepareNativeView(value: Any?): android.view.View {
        if (preparingView) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "view")
        preparingView = true
        return try {
            platform.prepareView(value) { function, context ->
                try { call(function, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(RhinoUtils.toJsValue(scope, context))) }
                catch (failure: Throwable) { throw renderFailure(failure) }
            }
        } finally { preparingView = false }
    }

    internal fun batch(function: Function): Any? = graph.transaction { reactive.batch { call(function) } }

    /** compose.memo(fn, deps, options?): reuse an unchanged render fragment instead of rebuilding it. */
    private fun memo(args: Array<out Any?>): Any? {
        composeArguments(args, 2, 3, "memo")
        val frame = memoFrame ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "memo can only be called while a render function runs")
        val function = composeFunction(args[0], "memo")
        val dependencies = ComposeMemoCache.dependencies(args[1])
        val options = values.objectProperties(args.getOrNull(2), "memo")
        options.keys.firstOrNull { it != "key" }?.let { throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP, prop = it) }
        val key = ComposeMemoCache.key(options["key"])
        if (++frame.calls > ComposeUiLimits.MAX_NODES_PER_SESSION) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED,
            "Too many memo fragments in one render")
        val level = frame.current
        val previous = if (key != null) {
            if (!level.keys.add(key)) throw ComposeUiContractException(ComposeUiErrorCodes.DUPLICATE_KEY, "Duplicate memo key: $key", prop = "key")
            level.previous?.keyed?.get(key)
        } else level.previous?.ordered?.getOrNull(level.ordinal++)
        val identity = ComposeMemoCache.identity(function)
        val candidate = previous?.takeIf { it.identity === identity }
        if (candidate != null && ComposeMemoCache.same(candidate.dependencies, dependencies) && reactive.isCurrent(candidate.reads) &&
            candidate.node !in frame.used && graph.retainedIn(candidate.node, frame.previousRoot)) {
            frame.used += candidate.node
            reactive.replay(candidate.reads)
            level.next.add(key, candidate)
            return candidate.node.js
        }
        val children = ComposeMemoCache.Scope()
        val (result, reads) = frame.nested(candidate?.children, children) { reactive.record { call(function) } }
        val node = graph.root(result) ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "memo must return one ComposeNode")
        frame.used += node
        ComposeMemoCache.Entry(identity, dependencies, reads, node, children).also { entry ->
            frame.created += entry
            level.next.add(key, entry)
        }
        return node.js
    }

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
        live.filter { it.isUiRoot && it.mountedView != null && it.mountedView !== view }.toList().forEach {
            platform.warn("3-Block Compose session closed because ui content was replaced")
            it.close()
        }
    }
    internal fun ended(session: ComposeScriptSession) { live.remove(session) }
    internal fun validateNativeViews(session: ComposeScriptSession, views: List<org.autojs.plugin.compose.api.interop.AndroidViewBindingV1>) {
        if (views.isEmpty()) return
        val elsewhere = live.filter { it !== session && !it.isClosed }.flatMap { it.nativeViews() }
        if (views.any { binding -> elsewhere.any { it === binding.view } }) throw ComposeUiContractException(
            ComposeUiErrorCodes.INVALID_ARGUMENT, "A View cannot belong to two Compose sessions", prop = "view")
    }
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
            else -> ComposeV2Callbacks.arguments(event.type, payload.keySet().associateWith { payload.get(it) }) ?: emptyArray()
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
