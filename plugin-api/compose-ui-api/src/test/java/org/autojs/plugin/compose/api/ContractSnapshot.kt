package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.catalog.*
import org.autojs.plugin.compose.api.loading.*
import org.autojs.plugin.compose.api.model.*
import java.lang.reflect.Modifier

internal object ContractSnapshot {
    fun render(): String = buildString {
        val literals = listOf(ComposeUiIds::class.java, ComposeUiContract::class.java, ComposeUiCapabilityKeys::class.java,
            ComposeUiErrorCodes::class.java, ComposeUiLimits::class.java, ComposeUiComponents::class.java, ComposeUiProps::class.java,
            ComposeUiPropAliases::class.java, ComposeUiEvents::class.java, ComposeUiEventFields::class.java, ComposeUiSlots::class.java,
            ComposeUiModifiers::class.java, ComposeUiShortcuts::class.java, ComposeUiCommands::class.java, ComposeUiThemeColors::class.java)
        literals.forEach { owner -> owner.declaredFields.filter { Modifier.isStatic(it.modifiers) && (it.type.isPrimitive || it.type == String::class.java) }
            .sortedBy { it.name }.forEach { appendLine("${owner.simpleName}.${it.name}=${it.get(null)}") } }
        listOf(ValueKind.entries, ShapeKind.entries, ScopeKind.entries, SnackbarDuration.entries, ChildrenPolicy.entries,
            ComponentInvocation.entries, PropertyUpdate.entries, PayloadType.entries).forEach { values ->
            appendLine("enum ${values.first().javaClass.simpleName}=${values.joinToString { it.name }}")
        }
        listOf(ComposeUiRendererFactory::class.java, ComposeUiHostEnvironment::class.java, ComposeUiRenderer::class.java,
            ComposeUiEventSink::class.java).forEach { type ->
            type.declaredMethods.sortedBy { it.name }.forEach { method ->
                appendLine("interface ${type.simpleName}.${method.name}(${method.parameterTypes.joinToString { it.name }}):${method.returnType.name}")
            }
        }
        val models = listOf(ComposeParcelable::class.java, UiValue::class.java, UiValue.Str::class.java, UiValue.Num::class.java,
            UiValue.Bool::class.java, UiValue.Color::class.java, UiValue.Dp::class.java, UiValue.Sp::class.java, UiValue.Enum::class.java,
            UiValue.TextStyle::class.java, UiValue.BitmapRef::class.java, UiValue.IconName::class.java, UiValue.Shape::class.java,
            UiValue.ListOf::class.java, UiValue.Null::class.java, ModifierOp::class.java, UiNode::class.java, UiTree::class.java,
            ThemeSpec::class.java, UiPatch::class.java, UiPatch.SetProps::class.java, UiPatch.Insert::class.java,
            UiPatch.Remove::class.java, UiPatch.Move::class.java, UiPatch.ReplaceSlot::class.java, UiPatch.SetRoot::class.java,
            UiPatchBatch::class.java, UiEvent::class.java, TextSelection::class.java, UiCommand::class.java,
            UiCommand.Focus::class.java, UiCommand.Blur::class.java, UiCommand.ScrollTo::class.java, UiCommand.Edit::class.java,
            UiCommand.ShowSnackbar::class.java)
        models.forEach { type ->
            type.declaredConstructors.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }.map {
                "model ${type.name}(${it.genericParameterTypes.joinToString { p -> p.typeName }})"
            }.sorted().forEach(::appendLine)
            type.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && '$' !in it.name }.map {
                "model ${type.name}.${it.name}(${it.genericParameterTypes.joinToString { p -> p.typeName }}):${it.genericReturnType.typeName}"
            }.sorted().forEach(::appendLine)
        }
        val catalog = ComponentCatalog.V1
        catalog.components.forEach { component ->
            appendLine("component ${component.name} ${component.invocation} children=${component.childrenPolicy} scope=${component.scope} commands=${component.commands.joinToString()}")
            component.props.forEach { prop -> appendLine("  prop ${prop.name} ${describe(prop.type)} required=${prop.required} default=${value(prop.defaultValue)} aliases=${prop.aliases.joinToString()} update=${prop.update}") }
            component.slots.forEach { appendLine("  slot ${it.name} required=${it.required}") }
            component.events.forEach { appendLine("  ${event(it)}") }
        }
        catalog.modifiers.forEach { modifier ->
            appendLine("modifier ${modifier.name} scope=${modifier.scope} parents=${modifier.allowedParents.joinToString()} commands=${modifier.commands.joinToString()}")
            modifier.signatures.forEach { signature -> appendLine("  (${signature.args.joinToString { "${it.name}:${describe(it.type)}=${value(it.defaultValue)} callback=${it.callback}" }})") }
        }
        catalog.shortcuts.forEach { appendLine("shortcut ${it.name}->${it.modifierName} expandList=${it.expandList}") }
        catalog.systemEvents.forEach { appendLine("system ${event(it)}") }
    }
    private fun event(event: EventSpec) = "event ${event.name}(${event.payloadFields.joinToString { "${it.name}:${it.type}:${it.required}" }})"
    private fun describe(type: ValueType) = "kinds=${type.kinds.joinToString()} enum=${type.enumValues.joinToString()} min=${type.minimum} max=${type.maximum} exclusive=${type.minimumExclusive} integer=${type.integral} list=${type.listKinds.joinToString()}:${type.listMinimum}..${type.listMaximum}"
    private fun value(v: UiValue?): String = when (v) {
        null -> "<unspecified>"
        is UiValue.ListOf -> "LIST[${v.values.joinToString { value(it) }}]"
        else -> "${v.kind}[${v.fields().joinToString()}]"
    }
}
