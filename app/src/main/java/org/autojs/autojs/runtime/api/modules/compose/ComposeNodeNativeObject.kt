package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

/** Catalogue properties are real Rhino accessors; unrelated property writes cannot bypass validation. */
class ComposeNodeNativeObject internal constructor(val node: ComposeNode) : NativeObject() {
    private var initializing = true
    init {
        RhinoUtils.initNativeObjectPrototype(node.graph.scope, this)
        defineProperty("type", node.type, READONLY or PERMANENT)
        defineProperty("key", node.key, READONLY or PERMANENT)
        defineProperty("nodeId", { node.nodeId }, null, READONLY or PERMANENT)
        defineProperty("parent", { node.parent?.js }, null, READONLY or PERMANENT)
        defineProperty("children", { RhinoUtils.newNativeArray(node.graph.scope, node.children.map { it.js as Any? }.toTypedArray()) }, null, READONLY or PERMANENT)
        val catalog = org.autojs.plugin.compose.api.v2.ComposeUiV2.interopCatalog
        val spec = catalog.requireComponent(node.type)
        val properties = (spec.props.flatMap { listOf(it.name) + it.aliases } + catalog.shortcuts.map { it.name } + "modifier").distinct()
        properties.forEach { name ->
            defineProperty(name, { guarded { node.get(name) } }, { raw -> guarded { node.set(mapOf(name to raw)); Unit } }, PERMANENT)
        }
        operation("set", 1..1) { node.set(it[0]).js }
        operation("get", 1..1) { node.get(string(it[0])) }
        operation("append", 1..1) { node.append(it[0]).js }
        operation("insert", 2..2) { node.insert(index(it[0]), it[1]).js }
        operation("remove", 1..1) { node.remove(it[0]).js }
        operation("replace", 1..1) { node.replace(it[0]).js }
        operation("clear", 0..0) { node.clear().js }
        operation("slot", 2..2) { node.slot(string(it[0]), it[1]).js }
        operation("on", 2..2) { node.on(string(it[0]), function(it[1])).js }
        operation("off", 1..2) { node.off(string(it[0]), it.getOrNull(1)?.takeUnless(ComposeScriptValues::isNullish)?.let(::function)).js }
        operation("focus", 0..0) { node.focus().js }
        operation("blur", 0..0) { node.blur().js }
        operation("scrollTo", 1..1) { node.scrollTo(it[0]).js }
        operation("edit", 1..1) { node.edit(it[0]).js }
        operation("toString", 0..0) { node.toString() }
        preventExtensions()
        initializing = false
    }

    override fun getClassName() = "ComposeNode"

    override fun put(name: String, start: Scriptable, value: Any?) {
        if (!initializing && !has(name, this)) throw node.graph.translateError(ComposeUiContractException(
            ComposeUiErrorCodes.UNKNOWN_PROP, message = "${node.type}.$name: Unknown property", nodeId = node.graph.errorNodeId(node), prop = name))
        super.put(name, start, value)
    }

    private fun operation(name: String, arity: IntRange, body: (Array<out Any?>) -> Any?) {
        defineProperty(name, RhinoUtils.newBaseFunction(node.graph.scope, name, { args ->
            guarded {
                if (args.size !in arity) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = name)
                body(args)
            }
        }, RhinoUtils.NOT_CONSTRUCTABLE), READONLY or DONTENUM or PERMANENT)
    }

    private fun <T> guarded(action: () -> T): T = try { action() } catch (failure: RuntimeException) { throw node.graph.translateError(failure) }
    private fun string(value: Any?) = (value as? CharSequence)?.toString()
        ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
    private fun function(value: Any?) = ComposeScriptValues.unwrap(value) as? Function
        ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
    private fun index(value: Any?): Int {
        val number = (value as? Number)?.toDouble() ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
        if (!number.isFinite() || number < 0 || number > Int.MAX_VALUE || number % 1.0 != 0.0) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
        return number.toInt()
    }
}
