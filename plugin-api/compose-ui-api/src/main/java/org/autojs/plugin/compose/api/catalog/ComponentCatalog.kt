package org.autojs.plugin.compose.api.catalog

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.*

class ComponentCatalog internal constructor(
    components: List<ComponentSpec>,
    modifiers: List<ModifierSpec>,
    shortcuts: List<ShortcutSpec>,
    systemEvents: List<EventSpec>,
) {
    val components = frozenList(components, ComposeUiLimits.MAX_VALUE_ITEMS)
    val modifiers = frozenList(modifiers, ComposeUiLimits.MAX_VALUE_ITEMS)
    /** Fixed prefix order, independent of the script object's property insertion order. */
    val shortcuts = frozenList(shortcuts, ComposeUiLimits.MAX_MODIFIER_OPS)
    val systemEvents = frozenList(systemEvents, ComposeUiLimits.MAX_EVENT_FIELDS)
    private val byName = this.components.associateBy { it.name }
    private val modifiersByName = this.modifiers.associateBy { it.name }

    fun component(name: String): ComponentSpec? = byName[text(name)]
    fun modifier(name: String): ModifierSpec? = modifiersByName[text(name)]
    fun requireComponent(name: String): ComponentSpec = component(name)
        ?: throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_COMPONENT)

    /** Call after extracting key/ref/callbacks. Aliases are canonicalized; shortcuts precede explicit ops. */
    fun normalizeProperties(component: String, props: Map<String, UiValue>, modifier: List<ModifierOp> = emptyList()): NormalizedProps {
        limit(props.size <= ComposeUiLimits.MAX_PROPERTIES_PER_NODE && modifier.size <= ComposeUiLimits.MAX_MODIFIER_OPS, "Property or modifier count exceeds contract limit")
        val spec = requireComponent(component)
        val shortcutNames = shortcuts.map { it.name }.toSet()
        val canonical = LinkedHashMap<String, UiValue>()
        props.filterKeys { it !in shortcutNames }.forEach { (key, value) ->
            val prop = spec.props.firstOrNull { it.name == key || key in it.aliases }
                ?: throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP, prop = key)
            if (canonical.put(prop.name, value) != null) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Conflicting property aliases", prop = prop.name)
        }
        val prefix = shortcuts.mapNotNull { shortcut ->
            props[shortcut.name]?.let { value ->
                val args = if (shortcut.expandList && value is UiValue.ListOf) value.values else listOf(value)
                val operation = modifiersByName.getValue(shortcut.modifierName)
                ModifierOp(operation.name, args, operation.scope).also { validateModifier(it, null, checkParent = false) }
            }
        }
        return NormalizedProps(canonical, prefix + modifier)
    }

    /** Structural node validation. Cross-node patch semantics and the total live session budget belong to the host/store. */
    fun validateNode(node: UiNode, parentType: String? = null) {
        val spec = requireComponent(node.type)
        if (spec.invocation != ComponentInvocation.NODE) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Command-only catalog entry", node.nodeId)
        node.props.forEach { (key, value) ->
            val prop = spec.props.firstOrNull { it.name == key }
                ?: throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP, nodeId = node.nodeId, prop = key)
            if (!prop.type.accepts(value)) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, nodeId = node.nodeId, prop = key)
        }
        spec.props.filter { it.required && it.name !in node.props }.firstOrNull()?.let {
            throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Missing required property", node.nodeId, it.name)
        }
        if (spec.childrenPolicy == ChildrenPolicy.NONE && node.children.isNotEmpty() ||
            spec.childrenPolicy == ChildrenPolicy.SINGLE && node.children.size > 1) invalid("Invalid primary child count")
        if (node.children.isNotEmpty() && ComposeUiSlots.CONTENT in node.slots) invalid("Both primary children and content slot supplied")
        if (node.slots.keys.any { key -> spec.slots.none { it.name == key } } ||
            spec.slots.any { it.required && it.name !in node.slots }) invalid("Invalid or missing slot")
        if (node.callbacks.keys.any { key -> spec.events.none { it.name == key } }) invalid("Unknown component event")
        node.modifier.forEach { validateModifier(it, parentType) }
        if (node.type == ComposeUiComponents.SLIDER) {
            val range = node.props[ComposeUiProps.RANGE] as? UiValue.ListOf
            val start = (range?.values?.get(0) as? UiValue.Num)?.value ?: 0.0
            val end = (range?.values?.get(1) as? UiValue.Num)?.value ?: 1.0
            val value = (node.props[ComposeUiProps.VALUE] as? UiValue.Num)?.value ?: 0.0
            if (start >= end || value !in start..end || start < -Float.MAX_VALUE || end > Float.MAX_VALUE) invalid("Invalid slider range or value")
        }
        if (node.type in setOf(ComposeUiComponents.TEXT_FIELD, ComposeUiComponents.OUTLINED_TEXT_FIELD)) {
            val min = (node.props[ComposeUiProps.MIN_LINES] as? UiValue.Num)?.value ?: 1.0
            val max = (node.props[ComposeUiProps.MAX_LINES] as? UiValue.Num)?.value ?: Int.MAX_VALUE.toDouble()
            if (min > max) invalid("Minimum lines exceeds maximum lines")
        }
        (node.props[ComposeUiProps.CONTENT_PADDING] as? UiValue.ListOf)?.let { padding ->
            if (padding.values.size !in setOf(1, 2, 4) || padding.values.any { (it as UiValue.Dp).value < 0 }) invalid("Invalid content padding")
        }
    }

    fun validateModifier(op: ModifierOp, parentType: String?, checkParent: Boolean = true) {
        val spec = modifiersByName[op.name] ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_MODIFIER)
        if (op.scope != spec.scope || spec.signatures.none { signature ->
                op.args.size in signature.requiredCount..signature.args.size &&
                    op.args.indices.all { signature.args[it].type.accepts(op.args[it]) }
            }) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_MODIFIER)
        if (checkParent && spec.allowedParents.isNotEmpty() && parentType !in spec.allowedParents) {
            throw ComposeUiContractException(ComposeUiErrorCodes.SCOPE_MISMATCH)
        }
    }

    companion object { @JvmField val V1 = V1Catalog.create() }
}
