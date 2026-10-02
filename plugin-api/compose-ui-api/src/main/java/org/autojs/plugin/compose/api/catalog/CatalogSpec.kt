package org.autojs.plugin.compose.api.catalog

import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.model.*

enum class ChildrenPolicy { NONE, SINGLE, MANY, LAZY_ITEMS }
enum class ComponentInvocation { NODE, COMMAND }
enum class PropertyUpdate { SET_PROPS, EDIT_COMMAND }
enum class PayloadType { STRING, BOOLEAN, INTEGER, LONG, NUMBER }

/** The script bridge normalizes strings/numbers to these kinds before crossing the renderer boundary. */
class ValueType(
    kinds: Set<ValueKind>,
    enumValues: List<String> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minimumExclusive: Boolean = false,
    val integral: Boolean = false,
    listKinds: Set<ValueKind> = emptySet(),
    val listMinimum: Int = 0,
    val listMaximum: Int = ComposeUiLimits.MAX_VALUE_ITEMS,
) {
    val kinds = frozenSet(kinds)
    val enumValues = frozenList(enumValues, ComposeUiLimits.MAX_VALUE_ITEMS)
    val listKinds = frozenSet(listKinds)
    init {
        require(kinds.isNotEmpty())
        require(minimum == null || minimum.isFinite())
        require(maximum == null || maximum.isFinite())
        require(minimum == null || maximum == null || minimum <= maximum)
        require(listMinimum >= 0 && listMinimum <= listMaximum && listMaximum <= ComposeUiLimits.MAX_VALUE_ITEMS)
        require(enumValues.distinct().size == enumValues.size)
    }
    fun accepts(value: UiValue): Boolean {
        if (value.kind !in kinds) return false
        if (value is UiValue.Enum && enumValues.isNotEmpty() && value.value !in enumValues) return false
        val number = when (value) { is UiValue.Num -> value.value; is UiValue.Dp -> value.value; is UiValue.Sp -> value.value; else -> null }
        if (number != null) {
            if (minimum != null && (number < minimum || minimumExclusive && number == minimum)) return false
            if (maximum != null && number > maximum) return false
            if (integral && number % 1.0 != 0.0) return false
        }
        if (value is UiValue.ListOf) {
            if (value.values.size !in listMinimum..listMaximum) return false
            if (listKinds.isNotEmpty() && value.values.any { it.kind !in listKinds }) return false
        }
        return true
    }
}

class PropSpec(
    val name: String,
    val type: ValueType,
    val required: Boolean = false,
    val defaultValue: UiValue? = null,
    aliases: List<String> = emptyList(),
    val update: PropertyUpdate = PropertyUpdate.SET_PROPS,
) {
    val aliases = frozenList(aliases, ComposeUiLimits.MAX_PROPERTIES_PER_NODE)
}

/** Named slots have a single node root; use a layout node to hold multiple children. */
class SlotSpec(val name: String, val required: Boolean = false)
class PayloadFieldSpec(val name: String, val type: PayloadType, val required: Boolean = true)
class EventSpec(val name: String, payloadFields: List<PayloadFieldSpec> = emptyList()) {
    val payloadFields = frozenList(payloadFields, ComposeUiLimits.MAX_EVENT_FIELDS)
}
class ComponentSpec(
    val name: String,
    props: List<PropSpec> = emptyList(),
    slots: List<SlotSpec> = emptyList(),
    events: List<EventSpec> = emptyList(),
    val childrenPolicy: ChildrenPolicy = ChildrenPolicy.NONE,
    val scope: ScopeKind = ScopeKind.ANY,
    commands: List<String> = emptyList(),
    val invocation: ComponentInvocation = ComponentInvocation.NODE,
) {
    val props = frozenList(props, ComposeUiLimits.MAX_PROPERTIES_PER_NODE)
    val slots = frozenList(slots, ComposeUiLimits.MAX_SLOTS_PER_NODE)
    val events = frozenList(events, ComposeUiLimits.MAX_EVENT_FIELDS)
    val commands = frozenList(commands, ComposeUiLimits.MAX_VALUE_ITEMS)
}

class ModifierArgSpec(val name: String, val type: ValueType, val defaultValue: UiValue? = null, val callback: Boolean = false)
class ModifierSignature(args: List<ModifierArgSpec>) {
    val args = frozenList(args, ComposeUiLimits.MAX_MODIFIER_OPS)
    val requiredCount get() = args.count { it.defaultValue == null }
}
class ModifierSpec(
    val name: String,
    signatures: List<ModifierSignature>,
    val scope: ScopeKind = ScopeKind.ANY,
    allowedParents: List<String> = emptyList(),
    commands: List<String> = emptyList(),
) {
    val signatures = frozenList(signatures, ComposeUiLimits.MAX_VALUE_ITEMS)
    val allowedParents = frozenList(allowedParents, ComposeUiLimits.MAX_VALUE_ITEMS)
    val commands = frozenList(commands, ComposeUiLimits.MAX_VALUE_ITEMS)
}

class ShortcutSpec(val name: String, val modifierName: String, val expandList: Boolean = false)
class NormalizedProps(props: Map<String, UiValue>, modifier: List<ModifierOp>) {
    val props = frozenMap(props, ComposeUiLimits.MAX_PROPERTIES_PER_NODE)
    val modifier = frozenList(modifier, ComposeUiLimits.MAX_MODIFIER_OPS)
}
