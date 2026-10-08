package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.plugin.compose.api.*
import org.autojs.autojs.runtime.api.augment.ui.TypeScriptTsxFragment
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiComponents as C
import org.autojs.plugin.compose.api.ComposeUiEventFields as F
import org.autojs.plugin.compose.api.ComposeUiEvents as E
import org.autojs.plugin.compose.api.ComposeUiProps as P
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.catalog.ComponentInvocation
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.util.Collections
import java.util.IdentityHashMap

/** One engine's script-confined handle graph. Tentative mutations never publish a partial tree. */
class ComposeNodeGraph(
    val scope: VarScope,
    val values: ComposeScriptValues,
    private val invokeCallback: (Function, ComposeNode, UiEvent) -> Unit,
    private val checkThread: () -> Unit = {},
    val translateError: (Throwable) -> RuntimeException = { it as? RuntimeException ?: RuntimeException(it) },
    private val warn: (String) -> Unit = {},
    private val prepareView: (Any?) -> android.view.View = { throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "view") },
) : AutoCloseable {
    internal val tsxOwner = Any()
    private val catalog = org.autojs.plugin.compose.api.v2.ComposeUiV2.interopCatalog
    private val roots = IdentityHashMap<ComposeNodeBinding, ComposeNode>()
    private val accepted = IdentityHashMap<ComposeNodeBinding, ComposeNodeSnapshot>()
    private val published = IdentityHashMap<ComposeNodeBinding, ComposeNodeSnapshot>()
    private val known = Collections.newSetFromMap(java.util.WeakHashMap<ComposeNode, Boolean>())
    private var nextNode = 1L
    private var nextCallback = 1L
    private var nextViewLease = 1L
    private var closed = false
    private var mutation: Mutation? = null
    private val warnedBindings = Collections.newSetFromMap(java.util.WeakHashMap<ComposeNodeBinding, Boolean>())
    private val warnedRoots = Collections.newSetFromMap(java.util.WeakHashMap<ComposeNode, Boolean>())

    private data class Backup(val state: ComposeNodeState, val editor: ComposeNodeEditor?, val deferredEdit: UiCommand.Edit?, val affinity: ComposeNodeBinding?, val mountedOnce: Boolean)
    private class Mutation(val roots: Map<ComposeNodeBinding, ComposeNode>, val published: Map<ComposeNodeBinding, ComposeNodeSnapshot>) {
        val backups = LinkedHashMap<ComposeNode, Backup>()
        val created = LinkedHashSet<ComposeNode>()
        val discarded = LinkedHashSet<ComposeNode>()
        val dirty = LinkedHashSet<ComposeNodeBinding>()
        val commands = ArrayList<Pair<ComposeNodeBinding, UiCommand>>()
        var rendering = false
    }

    fun node(value: Any?): ComposeNode? = when (val raw = ComposeScriptValues.unwrap(value)) {
        is ComposeNode -> raw.takeIf { it.graph === this }
        is ComposeNodeNativeObject -> raw.node.takeIf { it.graph === this }
        else -> null
    }

    internal fun root(value: Any?): ComposeNode? {
        val raw = ComposeScriptValues.unwrap(value)
        if (raw is TypeScriptTsxFragment) return transaction {
            val expanded = children(raw)
            if (expanded.size != 1) fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
            expanded.single()
        }
        if ((raw is ComposeNode || raw is ComposeNodeNativeObject) && node(raw) == null) fail(ComposeUiErrorCodes.NODE_DETACHED)
        return node(raw)
    }

    fun create(type: String, props: Any? = null, children: Any? = null): ComposeNode = transaction {
        val spec = catalog.component(type) ?: throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_COMPONENT, "$type: Unknown component")
        if (spec.invocation != ComponentInvocation.NODE) fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
        val shorthand = props is CharSequence || props is ComposeNode || props is ComposeNodeNativeObject || ComposeScriptValues.isArray(props)
        if (shorthand && !ComposeScriptValues.isNullish(children)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
        val raw = values.objectProperties(if (shorthand) null else props, type)
        val childInput = if (shorthand) props else children
        if ("children" in raw && !ComposeScriptValues.isNullish(childInput)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "children")
        val key = when (val value = raw["key"]) {
            null, is Undefined -> null
            is CharSequence -> value.toString()
            is Number -> if (value.toDouble().isFinite()) Context.toString(value) else fail(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "key")
            else -> fail(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "key")
        }
        val result = ComposeNode(this, type, key, allocateNode())
        known += result
        mutation!!.created += result
        if (isField(result)) {
            result.editor = ComposeNodeEditor()
            result.state = result.state.copy(listeners = mapOf(E.VALUE_CHANGE to ComposeNodeListeners(allocateCallback(), emptyList())))
        }
        updateProperties(result, raw - "key" - "children", creating = true)
        val supplied = if ("children" in raw) raw["children"] else childInput
        if (!ComposeScriptValues.isNullish(supplied)) replaceChildren(result, children(supplied))
        result
    }

    fun <T> transaction(action: () -> T): T {
        usable()
        if (mutation != null) return action()
        val work = Mutation(IdentityHashMap(roots), IdentityHashMap(published)).also { mutation = it }
        try {
            val result = action()
            // Detached edits have no wire selection field. Deliver their original complete editor
            // command once the node has a bound owner, without ever refreshing its captured seq.
            work.dirty.toList().forEach { owner -> roots[owner]?.let { root ->
                collect(root).forEach { node -> node.deferredEdit?.let { edit ->
                    touch(node); node.deferredEdit = null; command(node, edit)
                } }
            } }
            val detachedRoots = (work.backups.keys + work.created).filterNot { it in work.discarded }.map(::top).filterNot { it in work.discarded }.distinct()
            detachedRoots.forEach { buildSnapshot(it, strictRoot = roots.values.any { root -> root === it }) }
            val targets = work.dirty.mapNotNull { binding -> roots[binding]?.let { binding to buildSnapshot(it, strictRoot = true) } }
            // Cross-session ownership checks make a single handle mutation affect at most one live
            // session. Reject multi-session user transactions before any publication can escape.
            if (targets.size > 1) fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
            targets.forEach { (binding, snapshot) -> snapshot.states.keys.forEach { node ->
                if (node.affinity != null && node.affinity !== binding) fail(ComposeUiErrorCodes.NODE_DETACHED, node)
            } }
            for ((binding, snapshot) in targets) {
                if (binding.isClosed) fail(ComposeUiErrorCodes.SESSION_CLOSED)
                published[binding] = snapshot
                try { binding.publish(snapshot) } catch (failure: Throwable) { published.remove(binding); throw failure }
                bindNodes(binding, snapshot)
            }
            work.commands.forEach { (binding, command) -> binding.execute(command) }
            return result
        } catch (failure: Throwable) {
            rollback(work)
            throw failure
        } finally { mutation = null }
    }

    /**
     * Render-created objects are drafts. Objects created before this call keep their identity.
     * [resolved] receives each reconciled draft and the retained node that now represents it.
     */
    fun render(previousRoot: ComposeNode?, resolved: MutableMap<ComposeNode, ComposeNode>? = null, build: () -> ComposeNode): ComposeNode = transaction {
        val work = mutation!!
        val binding = previousRoot?.let(::binding)
        val priorCreated = work.created.toSet()
        val wasRendering = work.rendering
        work.rendering = true
        try {
            val draft = build().also(::usable)
            val drafts = work.created - priorCreated
            val result = reconcileImpl(previousRoot, draft, drafts, resolved)
            if (binding != null) { roots[binding] = result; work.dirty += binding }
            result
        } finally { work.rendering = wasRendering }
    }

    fun reconcile(previousRoot: ComposeNode?, draftRoot: ComposeNode): ComposeNode = transaction {
        val binding = previousRoot?.let(::binding)
        val result = reconcileImpl(previousRoot, draftRoot, mutation!!.created)
        if (binding != null) { roots[binding] = result; mutation!!.dirty += binding }
        result
    }

    private fun reconcileImpl(previous: ComposeNode?, draft: ComposeNode, drafts: Set<ComposeNode>,
                              resolved: MutableMap<ComposeNode, ComposeNode>? = null): ComposeNode {
        val draftSnapshot = buildSnapshot(draft, strictRoot = true)
        val owner = previous?.let(::binding)
        val warned = if (owner != null) owner in warnedBindings else previous != null && previous in warnedRoots
        val missingKeys = draftSnapshot.states.values.any { state -> state.children.size > 1 && state.children.any { it.key == null } }
        val reserved = draftSnapshot.states.keys.filterNot { it in drafts }.toSet()
        val used = HashSet<ComposeNode>()
        fun old(node: ComposeNode) = mutation!!.backups[node]?.state ?: node.state
        fun visit(input: ComposeNode, candidate: ComposeNode?): ComposeNode {
            if (input !in drafts) { used += input; return input }
            val target = candidate?.takeIf { it.type == input.type && it.key == input.key && it !in reserved && it !in used } ?: input
            used += target
            resolved?.put(input, target)
            val source = input.state
            val prior = if (target === input) null else old(target)
            val nativeView = source.nativeView?.let { nativeLease(target, it.view) ?: it }
            val nativeSource = if (nativeView == null) source.source else source.source +
                (AndroidViewInteropV1.PROP_VIEW to UiValue.Num(nativeView.id.toDouble()))
            val keyed = prior?.children.orEmpty().filter { it.key != null }.associateBy { it.key }
            val unkeyed = prior?.children.orEmpty().filter { it.key == null }.groupBy { it.type }
            val ordinals = HashMap<String, Int>()
            val children = source.children.map { child ->
                val match = if (child.key != null) keyed[child.key] else {
                    val index = ordinals.getOrDefault(child.type, 0); ordinals[child.type] = index + 1
                    unkeyed[child.type]?.getOrNull(index)
                }
                visit(child, match)
            }
            val slots = source.slots.mapValues { (name, child) -> visit(child, prior?.slots?.get(name)) }
            touch(target)
            val initial = target.state.props[P.TEXT]
            val listeners = source.listeners.mapValues { (event, handlers) ->
                prior?.listeners?.get(event)?.takeIf { old ->
                    old.functions.size == handlers.functions.size && old.functions.indices.all { old.functions[it] === handlers.functions[it] }
                } ?: handlers
            }
            target.state = source.copy(parent = target.state.parent, children = target.state.children, slots = target.state.slots,
                listeners = listeners, modifierCallbacks = prior?.modifierCallbacks ?: source.modifierCallbacks, nativeView = nativeView)
            normalize(target, nativeSource, source.explicitModifier, source.images)
            if (isField(target) && target.mountedOnce) {
                // Declarative text remains a declaration. Native typing is reconciled through edit.
                val requested = source.props[P.TEXT] as? UiValue.Str
                if (requested != initial && requested != null) edit(target, mapOf("text" to requested.value))
                val preserved = target.state.source.toMutableMap().apply { if (initial == null) remove(P.TEXT) else put(P.TEXT, initial) }
                normalize(target, preserved, target.state.explicitModifier, target.state.images)
            }
            replaceChildren(target, children)
            (target.state.slots.keys + slots.keys).toSet().forEach { name -> setSlot(target, name, slots[name]) }
            return target
        }
        return visit(draft, previous).also { result ->
            // The reconciled root is a root even if constructing a draft temporarily reparented it.
            detach(result)
            mutation!!.discarded += drafts - collect(result)
            if (missingKeys && !warned) warn("Compose UI render: list children have no key")
            if (warned || missingKeys) {
                if (owner != null) warnedBindings += owner else warnedRoots += result
            }
            if (previous != null && previous !== result) warnedRoots.remove(previous)
        }
    }

    fun mount(root: ComposeNode, binding: ComposeNodeBinding, replacing: ComposeNodeBinding? = null) = transaction {
        usable(root)
        if (binding.isClosed) fail(ComposeUiErrorCodes.SESSION_CLOSED)
        if (roots.containsKey(binding) || root.parent != null || roots.values.any { it === root }) fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
        val replacement = replacing?.takeIf { roots.containsKey(it) && !it.isClosed }
        if (roots.keys.count { !it.isClosed } - (if (replacement == null) 0 else 1) >= ComposeUiLimits.MAX_SESSIONS_PER_ENGINE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        val snapshot = buildSnapshot(root, strictRoot = true)
        snapshot.states.keys.forEach { node ->
            if (node.affinity != null && node.affinity !== binding) fail(ComposeUiErrorCodes.NODE_DETACHED, node)
        }
        // The prior native UI remains alive until this complete replacement has been validated.
        // Transaction rollback restores its graph binding if publication rejects the new target.
        replacement?.let { roots.remove(it) }
        roots[binding] = root
        if (warnedRoots.remove(root)) warnedBindings += binding
        mutation!!.dirty += binding
    }

    internal fun hasBinding(binding: ComposeNodeBinding): Boolean {
        checkThread()
        return !closed && !binding.isClosed && roots.containsKey(binding)
    }

    fun snapshot(root: ComposeNode): ComposeNodeSnapshot { usable(root); return buildSnapshot(root, strictRoot = true) }

    /** Called by a core receipt on the script dispatcher; newer local changes remain pending. */
    fun accept(binding: ComposeNodeBinding, snapshot: ComposeNodeSnapshot) {
        usable()
        if (binding.isClosed || !roots.containsKey(binding)) return
        val old = accepted.put(binding, snapshot)
        old?.states?.forEach { (node, state) ->
            if (isField(node) && node !in snapshot.states) resetEditor(node, state)
        }
        snapshot.states.forEach { (node, state) ->
            if (isField(node) && old?.states?.containsKey(node) != true) resetEditor(node, state)
        }
        old?.refs?.filterKeys { it !in snapshot.refs }?.forEach { (ref, node) ->
            if (ref.current === node) ref.current = null
        }
        snapshot.refs.forEach { (ref, node) -> ref.current = node }
    }

    fun reject(binding: ComposeNodeBinding, snapshot: ComposeNodeSnapshot) {
        usable()
        if (published[binding] !== snapshot || !roots.containsKey(binding)) return
        val previous = accepted[binding]
        if (previous == null) {
            // There is no accepted UI to restore, but the session remains open. Keep the local
            // declaration connected so correcting it (or a later reactive render) can submit a
            // valid first frame instead of leaving a permanently inert mounted session.
            return
        }
        val current = roots.getValue(binding)
        val currentNodes = collect(current)
        val unaccepted = currentNodes.filter { it !in previous.states }.toSet()
        unaccepted.forEach { node -> node.state = node.state.copy(
            parent = node.parent?.takeIf { it in unaccepted },
            children = node.children.filter { it in unaccepted }, slots = node.state.slots.filterValues { it in unaccepted },
        ) }
        previous.states.forEach { (node, state) -> node.state = state }
        previous.editors.forEach { (node, editor) ->
            if (editor != null && node.editor?.sequence == editor.sequence) node.editor = editor
        }
        roots[binding] = previous.root
        published[binding] = previous
    }

    fun unmount(binding: ComposeNodeBinding) {
        usable()
        roots.remove(binding)
        accepted.remove(binding)?.refs?.forEach { (ref, node) -> if (ref.current === node) ref.current = null }
        published.remove(binding)
        if (binding.isClosed) known.toList().filter { it.affinity === binding }.forEach(::releaseResources)
        if (binding.isClosed) warnedBindings.remove(binding)
    }

    internal fun set(node: ComposeNode, props: Any?): ComposeNode = transaction { usable(node); updateProperties(node, values.objectProperties(props, node.type)); node }

    internal fun get(node: ComposeNode, name: String): Any? {
        usable(node)
        if (name == "modifier") return node.state.explicitModifier ?: Undefined.instance
        if (node.type == AndroidViewInteropV1.COMPONENT && name == AndroidViewInteropV1.PROP_VIEW) {
            return node.state.nativeView?.view?.let { RhinoUtils.toJsValue(scope, it) } ?: Undefined.instance
        }
        val spec = catalog.requireComponent(node.type)
        val prop = spec.props.firstOrNull { it.name == name || name in it.aliases }
        val canonical = prop?.name ?: name
        if (prop == null && catalog.shortcuts.none { it.name == name }) fail(ComposeUiErrorCodes.UNKNOWN_PROP, node, name)
        if (isField(node) && canonical == P.TEXT) return node.editor!!.text
        val value = node.state.source[canonical] ?: prop?.defaultValue ?: return Undefined.instance
        return values.toRaw(value, scope)
    }

    private fun updateProperties(node: ComposeNode, raw: Map<String, Any?>, creating: Boolean = false) {
        touch(node)
        val spec = catalog.requireComponent(node.type)
        val source = node.state.source.toMutableMap()
        val images = node.state.images.toMutableMap()
        var modifier = node.state.explicitModifier
        val canonicalNames = HashSet<String>()
        var liveText: String? = null
        raw.forEach { (name, rawValue) ->
            val value = ComposeScriptValues.unwrap(rawValue)
            when {
                name == "ref" -> {
                    val ref = if (ComposeScriptValues.isNullish(value)) null else value as? ComposeNodeRef ?: fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, name)
                    node.state = node.state.copy(ref = ref)
                }
                name == "modifier" -> modifier = if (ComposeScriptValues.isNullish(value)) null else value as? ComposeModifier ?: fail(ComposeUiErrorCodes.INVALID_MODIFIER, node, name)
                name == "slots" -> values.objectProperties(value, node.type).forEach { (slot, child) -> setSlot(node, slot, singleChild(child)) }
                spec.slots.any { it.name == name } -> setSlot(node, name, singleChild(value))
                spec.events.any { ComposeScriptValues.callbackProperty(it.name) == name } -> {
                    val event = values.eventName(node.type, name)
                    val functions = if (ComposeScriptValues.isNullish(value)) emptyList() else listOf(value as? Function ?: fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, name))
                    listeners(node, event, functions)
                }
                name in setOf("key", "type", "nodeId", "parent", "children") -> fail(ComposeUiErrorCodes.UNKNOWN_PROP, node, name)
                node.type == AndroidViewInteropV1.COMPONENT && name == AndroidViewInteropV1.PROP_VIEW -> {
                    val view = prepareView(value)
                    val lease = nativeLease(node, view) ?: run {
                        if (nextViewLease > Int.MAX_VALUE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, node, name)
                        ComposeNativeViewLease((nextViewLease++).toInt(), view)
                    }
                    node.state = node.state.copy(nativeView = lease)
                    source[name] = UiValue.Num(lease.id.toDouble())
                }
                else -> {
                    val converted = values.property(node.type, name, value)
                    if (!canonicalNames.add(converted.name)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, converted.name)
                    if (isField(node) && converted.name == P.TEXT && (binding(node) != null || hasAcceptedEditor(node)) && !creating) liveText = (converted.value as UiValue.Str).value
                    else {
                        source[converted.name] = converted.value
                        if (converted.retainedImages.isEmpty()) images.remove(converted.name) else images[converted.name] = converted.retainedImages
                    }
                }
            }
        }
        normalize(node, source, modifier, images)
        if (isField(node) && binding(node) == null && !hasAcceptedEditor(node) && (creating || P.TEXT in canonicalNames)) {
            resetEditor(node, node.state)
            node.deferredEdit = null
        }
        liveText?.let { edit(node, mapOf("text" to it)) }
    }

    private fun normalize(node: ComposeNode, source: Map<String, UiValue>, explicit: ComposeModifier?, images: Map<String, List<Any>>) {
        val callbacks = LinkedHashMap<Int, Function>()
        val old = node.state.modifierCallbacks
        val converted = values.normalize(node.type, source, explicit) { function ->
            val token = old.entries.firstOrNull { it.value === function }?.key
                ?: callbacks.entries.firstOrNull { it.value === function }?.key ?: allocateCallback()
            callbacks[token] = function
            token
        }
        node.state = node.state.copy(source = source.toMap(), props = converted.props, modifier = converted.modifier,
            explicitModifier = explicit, modifierCallbacks = callbacks, images = images.toMap())
    }

    internal fun insert(parent: ComposeNode, index: Int, raw: Any?): ComposeNode = transaction {
        usable(parent)
        if (index !in 0..parent.children.size) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, parent)
        val additions = children(raw)
        if (additions.distinct().size != additions.size) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, parent)
        val next = parent.children.toMutableList()
        var at = index
        additions.forEach { child ->
            val before = next.indexOf(child)
            if (before >= 0) { next.removeAt(before); if (before < at) at-- }
            next.add(at++, child)
        }
        replaceChildren(parent, next)
        parent
    }

    internal fun remove(parent: ComposeNode, raw: Any?): ComposeNode = transaction {
        usable(parent)
        val child = if (raw is Number) parent.children.getOrNull(integer(raw)) else node(raw)
        if (child == null || child !in parent.children) fail(ComposeUiErrorCodes.NODE_DETACHED, parent)
        replaceChildren(parent, parent.children - child)
        parent
    }

    internal fun replace(parent: ComposeNode, raw: Any?): ComposeNode = transaction { usable(parent); replaceChildren(parent, children(raw)); parent }
    internal fun slot(parent: ComposeNode, name: String, raw: Any?): ComposeNode = transaction { usable(parent); setSlot(parent, name, singleChild(raw)); parent }

    private fun replaceChildren(parent: ComposeNode, children: List<ComposeNode>) {
        touch(parent)
        if (children.distinct().size != children.size) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, parent)
        children.forEach { adopt(parent, it) }
        parent.state.children.filter { it !in children }.forEach { child -> touch(child); child.state = child.state.copy(parent = null) }
        parent.state = parent.state.copy(children = children.toList())
        children.forEach { child -> touch(child); child.state = child.state.copy(parent = parent) }
    }

    private fun setSlot(parent: ComposeNode, name: String, child: ComposeNode?) {
        if (catalog.requireComponent(parent.type).slots.none { it.name == name }) fail(ComposeUiErrorCodes.UNKNOWN_PROP, parent, name)
        touch(parent)
        child?.let { adopt(parent, it) }
        parent.state.slots[name]?.takeIf { it !== child }?.let { old -> touch(old); old.state = old.state.copy(parent = null) }
        parent.state = parent.state.copy(slots = parent.state.slots.toMutableMap().apply { if (child == null) remove(name) else put(name, child) })
        child?.let { touch(it); it.state = it.state.copy(parent = parent) }
    }

    private fun adopt(parent: ComposeNode, child: ComposeNode) {
        usable(parent); usable(child)
        var ancestor: ComposeNode? = parent
        while (ancestor != null) {
            if (ancestor === child) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, parent)
            ancestor = ancestor.parent
        }
        val owner = binding(parent) ?: parent.affinity
        val from = binding(child) ?: child.affinity
        if (owner != null && from != null && owner !== from) fail(ComposeUiErrorCodes.NODE_DETACHED, child)
        if (roots.values.any { it === child } && !mutation!!.rendering) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, child)
        detach(child)
    }

    private fun detach(child: ComposeNode) {
        val old = child.parent ?: return
        touch(old); touch(child)
        old.state = old.state.copy(children = old.state.children.filterNot { it === child }, slots = old.state.slots.filterValues { it !== child })
        child.state = child.state.copy(parent = null)
    }

    internal fun on(node: ComposeNode, name: String, callback: Function): ComposeNode = transaction {
        usable(node); val event = values.eventName(node.type, name)
        listeners(node, event, node.state.listeners[event]?.functions.orEmpty() + callback); node
    }

    internal fun off(node: ComposeNode, name: String, callback: Function?): ComposeNode = transaction {
        usable(node); val event = values.eventName(node.type, name)
        listeners(node, event, if (callback == null) emptyList() else node.state.listeners[event]?.functions.orEmpty().filterNot { it === callback }); node
    }

    private fun listeners(node: ComposeNode, event: String, functions: List<Function>) {
        touch(node)
        if (functions.size > ComposeUiLimits.MAX_CALLBACKS_PER_SESSION) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, node)
        val next = node.state.listeners.toMutableMap()
        if (functions.isEmpty() && !(isField(node) && event == E.VALUE_CHANGE)) next.remove(event)
        else next[event] = ComposeNodeListeners(allocateCallback(), functions.toList())
        node.state = node.state.copy(listeners = next)
    }

    internal fun command(node: ComposeNode, command: UiCommand): ComposeNode = transaction {
        usable(node)
        val owner = binding(node) ?: fail(ComposeUiErrorCodes.NODE_DETACHED, node)
        val name = when (command) {
            is UiCommand.Focus -> ComposeUiCommands.FOCUS
            is UiCommand.Blur -> ComposeUiCommands.BLUR
            is UiCommand.ScrollTo -> ComposeUiCommands.SCROLL_TO
            is UiCommand.Edit -> ComposeUiCommands.EDIT
            else -> fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
        }
        val supported = name in catalog.requireComponent(node.type).commands || node.state.modifier.any { name in catalog.modifier(it.name)!!.commands }
        if (!supported) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
        if (command is UiCommand.ScrollTo && command.index != null &&
            (node.type !in setOf(C.LAZY_COLUMN, C.LAZY_ROW, org.autojs.plugin.compose.api.v2.ComposeUiV2.Components.HORIZONTAL_PAGER,
                org.autojs.plugin.compose.api.v2.ComposeUiV2.Components.LAZY_VERTICAL_GRID) || command.index!! >= node.children.size)) {
            fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
        }
        if (command is UiCommand.Edit) {
            val existing = mutation!!.commands.indexOfLast { it.first === owner && (it.second as? UiCommand.Edit)?.nodeId == node.stableId }
            if (existing >= 0) {
                mutation!!.commands.removeAt(existing)
                mutation!!.commands += owner to command
            } else mutation!!.commands += owner to command
        } else mutation!!.commands += owner to command
        node
    }

    internal fun scrollTo(node: ComposeNode, raw: Any?): ComposeNode {
        val input = ComposeScriptValues.unwrap(raw)
        val request = if (input is Number) UiCommand.ScrollTo(node.stableId, index = integer(input)) else {
            val options = values.objectProperties(input, node.type)
            if (options.keys.any { it !in setOf("index", "offset") }) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
            UiCommand.ScrollTo(node.stableId, options["index"]?.let { integer(it) }, options["offset"]?.let { integer(it, signed = options["index"] != null) })
        }
        return command(node, request)
    }

    internal fun edit(node: ComposeNode, raw: Any?): ComposeNode = transaction {
        usable(node)
        if (!isField(node)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
        val options = values.objectProperties(raw, node.type)
        if (options.keys.any { it !in setOf("text", "selection") } || options.isEmpty()) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
        val text = if ("text" in options) (values.property(node.type, P.TEXT, options["text"]).value as UiValue.Str).value else null
        val current = node.editor!!
        val selection = if ("selection" in options) options["selection"].let {
            if (ComposeScriptValues.isArray(it)) {
                val array = array(it)
                if (array.size != 2) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, "selection")
                TextSelection(integer(array[0]), integer(array[1]))
            } else {
                val map = values.objectProperties(it, node.type)
                if (map.keys != setOf("start", "end")) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, "selection")
                TextSelection(integer(map["start"]), integer(map["end"]))
            }
        } else null
        val nextText = text ?: current.text
        val start = selection?.start ?: if (text != null && text != current.text) nextText.length else current.start
        val end = selection?.end ?: if (text != null && text != current.text) nextText.length else current.end
        if (maxOf(start, end) > nextText.length) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, "selection")
        // Each queued edit is a complete prospective editor value. Last-writer coalescing cannot
        // accidentally combine a prior selection with a later shorter replacement string.
        val request = UiCommand.Edit(node.stableId, nextText, TextSelection(start, end), current.sequence)
        touch(node)
        node.editor = current.copy(text = nextText, start = start, end = end)
        if (binding(node) == null) {
            if (!hasAcceptedEditor(node)) normalize(node, node.state.source + (P.TEXT to UiValue.Str(nextText)), node.state.explicitModifier, node.state.images)
            node.deferredEdit = request
        } else {
            node.deferredEdit = null
            command(node, request)
        }
        node
    }

    private fun buildSnapshot(root: ComposeNode, strictRoot: Boolean): ComposeNodeSnapshot {
        val states = LinkedHashMap<ComposeNode, ComposeNodeState>()
        val output = ArrayList<UiNode>()
        val callbacks = LinkedHashMap<Int, (UiEvent) -> Unit>()
        val references = IdentityHashMap<ComposeNodeRef, ComposeNode>()
        val retained = ArrayList<Any>()
        val nativeViews = Collections.newSetFromMap(IdentityHashMap<android.view.View, Boolean>())
        var functionCount = 0
        val pending = java.util.ArrayDeque<Pair<ComposeNode, Int>>().apply { add(root to 1) }
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            if (node.graph !== this || states.put(node, node.state) != null) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
            if (depth > ComposeUiLimits.MAX_DEPTH || states.size > ComposeUiLimits.MAX_NODES_PER_SESSION) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, node)
            val state = node.state
            state.nativeView?.let { if (!nativeViews.add(it.view)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, "view") }
            val wire = UiNode(node.stableId, node.type, node.key, state.props, state.modifier,
                state.children.map { it.stableId }, state.slots.mapValues { it.value.stableId }, state.listeners.mapValues { it.value.token })
            val parent = if (node === root) null else node.parent?.type
            val syntheticParent = if (!strictRoot && node === root) {
                val allowed = state.modifier.mapNotNull { catalog.modifier(it.name)?.allowedParents?.takeIf { parents -> parents.isNotEmpty() }?.toSet() }
                allowed.reduceOrNull { a, b -> a intersect b }?.firstOrNull()
            } else null
            catalog.validateNode(wire, parent ?: syntheticParent)
            output += wire
            state.listeners.forEach { (event, handlers) ->
                val captured = handlers.functions.toList()
                functionCount += maxOf(1, captured.size)
                callbacks[handlers.token] = callback@{ message ->
                    if (closed || node.affinity?.isClosed == true) return@callback
                    if (isField(node) && event == E.VALUE_CHANGE) observeEditor(node, message)
                    captured.forEach {
                        // An earlier listener may close this session or stop the engine.
                        if (!closed && node.affinity?.isClosed != true) invokeCallback(it, node, message)
                    }
                }
            }
            state.modifierCallbacks.forEach { (token, function) -> callbacks[token] = {
                if (!closed && node.affinity?.isClosed != true) invokeCallback(function, node, it)
            } }
            functionCount += state.modifierCallbacks.size
            if (functionCount > ComposeUiLimits.MAX_CALLBACKS_PER_SESSION) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, node)
            state.ref?.let { ref -> if (references.put(ref, node) != null) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node, "ref") }
            state.images.values.forEach(retained::addAll)
            val children = state.children + state.slots.values
            children.forEach { if (it.parent !== node) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, it) }
            children.asReversed().forEach { pending.add(it to depth + 1) }
        }
        val tree = UiTree(root.stableId, output)
        org.autojs.plugin.compose.api.v2.ComposeUiV2.validateTree(tree, allowDetachedRoot = !strictRoot)
        return ComposeNodeSnapshot(root, tree, callbacks.toMap(), retained.toList(), references.toMap(), functionCount,
            states.toMap(), states.keys.associateWith { it.editor })
    }

    private fun observeEditor(node: ComposeNode, event: UiEvent) {
        if (closed || node.affinity?.isClosed == true) return
        val payload = event.payload
        val text = payload.getString(F.TEXT) ?: return
        val sequence = payload.getLong(F.EDIT_SEQ)
        if (sequence < node.editor!!.sequence) return
        node.editor = ComposeNodeEditor(text, payload.getInt(F.SELECTION_START), payload.getInt(F.SELECTION_END), sequence)
    }

    private fun children(raw: Any?): List<ComposeNode> {
        val output = ArrayList<ComposeNode>()
        val arrays = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        var entries = 0
        fun visit(raw: Any?, depth: Int) {
            if (++entries > TypeScriptTsxFragment.MAX_INPUT_ENTRIES || depth > ComposeUiLimits.MAX_DEPTH) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED)
            val value = ComposeScriptValues.unwrap(raw)
            when {
                ComposeScriptValues.isNullish(value) || value == false -> Unit
                node(value) != null -> output += node(value)!!
                value is ComposeNode || value is ComposeNodeNativeObject -> fail(ComposeUiErrorCodes.NODE_DETACHED)
                value is TypeScriptTsxFragment -> {
                    if (value.owner !== tsxOwner) fail(ComposeUiErrorCodes.NODE_DETACHED)
                    entries += value.cost - value.values.size - 1
                    if (entries > TypeScriptTsxFragment.MAX_INPUT_ENTRIES || depth + value.depth > ComposeUiLimits.MAX_DEPTH) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED)
                    value.values.forEach { visit(it, depth + 1) }
                }
                value is CharSequence -> output += create(C.TEXT, mapOf(P.TEXT to value.toString()))
                ComposeScriptValues.isArray(value) -> {
                    if (!arrays.add(value!!)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
                    array(value).forEach { visit(it, depth + 1) }
                    arrays.remove(value)
                }
                else -> fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
            }
            if (output.size > ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        }
        visit(raw, 0)
        return output
    }

    private fun singleChild(raw: Any?): ComposeNode? = children(raw).let { if (it.size > 1) fail(ComposeUiErrorCodes.INVALID_ARGUMENT); it.firstOrNull() }
    private fun array(raw: Any?): List<Any?> = when (val value = ComposeScriptValues.unwrap(raw)) {
        is NativeArray -> {
            if (value.length > ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED)
            (0 until value.length.toInt()).map { value.get(it, value).takeUnless { entry -> entry === org.mozilla.javascript.Scriptable.NOT_FOUND } }
        }
        is List<*> -> value.also { if (it.size > ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED) }
        is Array<*> -> value.toList().also { if (it.size > ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED) }
        else -> fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
    }

    private fun touch(node: ComposeNode) {
        usable(node)
        val work = mutation ?: error("Graph mutation requires a transaction")
        work.backups.putIfAbsent(node, Backup(node.state, node.editor, node.deferredEdit, node.affinity, node.mountedOnce))
        binding(node)?.let { work.dirty += it }
    }

    private fun bindNodes(binding: ComposeNodeBinding, snapshot: ComposeNodeSnapshot) {
        snapshot.states.keys.forEach { node ->
            if (node.affinity != null && node.affinity !== binding) fail(ComposeUiErrorCodes.NODE_DETACHED, node)
            node.affinity = binding; node.mountedOnce = true
        }
    }

    private fun rollback(work: Mutation) {
        work.backups.forEach { (node, backup) -> node.state = backup.state; node.editor = backup.editor; node.deferredEdit = backup.deferredEdit; node.affinity = backup.affinity; node.mountedOnce = backup.mountedOnce }
        work.created.forEach { node -> node.state = node.state.copy(parent = null, children = emptyList(), slots = emptyMap(), nativeView = null); node.affinity = null; node.mountedOnce = false; node.deferredEdit = null }
        roots.clear(); roots.putAll(work.roots)
        published.clear(); published.putAll(work.published)
    }

    private fun binding(node: ComposeNode): ComposeNodeBinding? {
        var current: ComposeNode? = node
        val visited = HashSet<ComposeNode>()
        while (current != null) {
            if (!visited.add(current)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node)
            roots.entries.firstOrNull { it.value === current }?.let { return it.key }
            current = current.parent
        }
        return null
    }

    internal fun isMounted(node: ComposeNode) = !closed && binding(node)?.isClosed == false
    /** A memo fragment may be reused only while its node is still inside the session's previous tree. */
    internal fun retainedIn(node: ComposeNode, root: ComposeNode?): Boolean =
        !closed && root != null && node.graph === this && node.affinity?.isClosed != true && top(node) === root
    internal fun errorNodeId(node: ComposeNode): Int? = node.stableId.takeIf { node.mountedOnce }
    private fun hasAcceptedEditor(node: ComposeNode) = node.affinity?.let { accepted[it]?.states?.containsKey(node) } == true

    /** Actual removal ends the native editor lifetime. Reinsertion starts at the wire declaration. */
    private fun resetEditor(node: ComposeNode, declaration: ComposeNodeState) {
        val text = (declaration.props[P.TEXT] as? UiValue.Str)?.value.orEmpty()
        node.editor = ComposeNodeEditor(text, text.length, text.length, 0)
    }

    private fun top(node: ComposeNode): ComposeNode {
        var current = node
        val seen = HashSet<ComposeNode>()
        while (current.parent != null) { if (!seen.add(current)) fail(ComposeUiErrorCodes.INVALID_ARGUMENT, node); current = current.parent!! }
        return current
    }

    private fun collect(root: ComposeNode): Set<ComposeNode> {
        val result = LinkedHashSet<ComposeNode>()
        val pending = java.util.ArrayDeque<ComposeNode>().apply { add(root) }
        while (pending.isNotEmpty()) { val node = pending.removeLast(); if (result.add(node)) { pending.addAll(node.state.children); pending.addAll(node.state.slots.values) } }
        return result
    }

    private fun integer(value: Any?, signed: Boolean = false): Int {
        val number = (value as? Number)?.toDouble() ?: fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
        if (!number.isFinite() || number % 1.0 != 0.0 || number < (if (signed) Int.MIN_VALUE else 0) || number > Int.MAX_VALUE) fail(ComposeUiErrorCodes.INVALID_ARGUMENT)
        return number.toInt()
    }
    private fun allocateNode(): Int { if (nextNode > Int.MAX_VALUE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED); return (nextNode++).toInt() }
    private fun allocateCallback(): Int { if (nextCallback > Int.MAX_VALUE) fail(ComposeUiErrorCodes.LIMIT_EXCEEDED); return (nextCallback++).toInt() }
    private fun usable(node: ComposeNode? = null) {
        checkThread()
        if (closed || node?.affinity?.isClosed == true) fail(ComposeUiErrorCodes.SESSION_CLOSED, node)
        if (node != null && node.graph !== this) fail(ComposeUiErrorCodes.NODE_DETACHED, node)
    }
    private fun fail(code: String, node: ComposeNode? = null, prop: String? = null): Nothing = throw ComposeUiContractException(code,
        message = "${node?.type ?: "Compose"}${prop?.let { ".$it" }.orEmpty()}: $code", nodeId = node?.let(::errorNodeId), prop = prop)
    private fun isField(node: ComposeNode) = node.type == C.TEXT_FIELD || node.type == C.OUTLINED_TEXT_FIELD

    private fun releaseResources(node: ComposeNode) {
        node.state = node.state.copy(listeners = emptyMap(), modifierCallbacks = emptyMap(), images = emptyMap(), nativeView = null, explicitModifier = null, ref = null)
        node.editor = null
        node.deferredEdit = null
    }

    /** Coalesced A -> B -> A updates retain A's accepted/in-flight native ownership ticket. */
    private fun nativeLease(node: ComposeNode, view: android.view.View): ComposeNativeViewLease? =
        sequenceOf(node.state.nativeView, accepted[node.affinity]?.states?.get(node)?.nativeView,
            published[node.affinity]?.states?.get(node)?.nativeView, mutation?.backups?.get(node)?.state?.nativeView,
            (node.affinity as? ComposeScriptSession)?.nativeLease(node, view)).filterNotNull().firstOrNull { it.view === view }

    override fun close() {
        checkThread()
        if (closed) return
        closed = true
        accepted.values.flatMap { it.refs.keys }.forEach { it.current = null }
        roots.clear(); accepted.clear(); published.clear()
        warnedBindings.clear(); warnedRoots.clear()
        known.toList().forEach(::releaseResources)
        known.clear()
    }
}
