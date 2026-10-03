package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.model.ModifierOp
import org.autojs.plugin.compose.api.model.UiValue
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

/** Immutable ordered modifier chain; each click function receives a node-local token on attachment. */
class ComposeModifier private constructor(
    private val converter: ComposeScriptValues,
    private val scope: VarScope,
    private val operations: List<Entry>,
) : NativeObject() {
    constructor(values: ComposeScriptValues, scope: VarScope) : this(values, scope, emptyList())
    constructor(values: ComposeScriptValues, scope: Scriptable) : this(values, RhinoUtils.getDeclarationScope(scope))

    private data class Entry(val operation: ModifierOp, val callback: Function? = null)

    init {
        RhinoUtils.initNativeObjectPrototype(scope, this)
        ComponentCatalog.V1.modifiers.forEach { spec ->
            defineProperty(spec.name, RhinoUtils.newBaseFunction(scope, spec.name, { args ->
                ComposeJsErrors.guard(scope) { append(spec.name, args) }
            }, RhinoUtils.NOT_CONSTRUCTABLE), ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        }
        preventExtensions()
    }

    override fun getClassName(): String = "ComposeModifier"

    fun append(name: String, arguments: Array<out Any?>): ComposeModifier = converter.contextual("Modifier", name) {
        val spec = ComponentCatalog.V1.modifier(name)
            ?: ComposeScriptValues.fail(ComposeUiErrorCodes.INVALID_MODIFIER, "Modifier", name, "Unknown modifier")
        if (operations.size >= ComposeUiLimits.MAX_MODIFIER_OPS) {
            ComposeScriptValues.fail(ComposeUiErrorCodes.LIMIT_EXCEEDED, "Modifier", name, "Modifier chain exceeds contract limit")
        }
        val signature = spec.signatures.firstOrNull { arguments.size in it.requiredCount..it.args.size }
            ?: ComposeScriptValues.invalid("Modifier", name, "Unsupported argument count")
        var callback: Function? = null
        val args = arguments.mapIndexed { index, raw ->
            if (signature.args[index].callback) {
                callback = ComposeScriptValues.unwrap(raw) as? Function
                    ?: ComposeScriptValues.invalid("Modifier", name, "Expected a callback function")
                UiValue.Num(1.0)
            } else converter.modifierArgument("Modifier", name, raw, signature.args[index].type)
        }
        val operation = ModifierOp(name, args, spec.scope)
        ComponentCatalog.V1.validateModifier(operation, null, checkParent = false)
        ComposeModifier(converter, scope, operations + Entry(operation, callback))
    }

    fun resolve(allocateCallback: (Function) -> Int): List<ModifierOp> = operations.map { entry ->
        val callback = entry.callback
        if (callback == null) entry.operation else {
            val token = allocateCallback(callback)
            if (token <= 0) ComposeScriptValues.invalid("Modifier", entry.operation.name, "Callback token must be positive")
            ModifierOp(entry.operation.name, listOf(UiValue.Num(token.toDouble())) + entry.operation.args.drop(1), entry.operation.scope)
        }
    }
}
