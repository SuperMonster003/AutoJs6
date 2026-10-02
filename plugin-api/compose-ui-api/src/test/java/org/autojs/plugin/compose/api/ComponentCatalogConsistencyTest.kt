package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.catalog.*
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test

class ComponentCatalogConsistencyTest {
    private val catalog = ComponentCatalog.V1
    private fun unique(values: List<String>) = assertEquals(values.size, values.toSet().size)
    private fun fails(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: ComposeUiContractException) { assertEquals(code, failure.code) }
    }
    @Test fun namesDefaultsAliasesSlotsAndPayloadTypesAreCoherent() {
        unique(catalog.components.map { it.name })
        catalog.components.forEach { component ->
            unique(component.props.flatMap { listOf(it.name) + it.aliases })
            unique(component.slots.map { it.name })
            unique(component.events.map { it.name })
            component.props.forEach { prop -> prop.defaultValue?.let { assertTrue("${component.name}.${prop.name}", prop.type.accepts(it)) } }
            component.events.forEach { event -> unique(event.payloadFields.map { it.name }); assertTrue(event.payloadFields.all { it.type in PayloadType.entries }) }
        }
        assertEquals(ComponentInvocation.COMMAND, catalog.requireComponent(ComposeUiComponents.SNACKBAR).invocation)
        assertNotNull(catalog.component(ComposeUiComponents.TOP_APP_BAR))
        val editing = catalog.requireComponent(ComposeUiComponents.TEXT_FIELD).props.single { it.name == ComposeUiProps.TEXT }
        assertEquals(PropertyUpdate.EDIT_COMMAND, editing.update)
    }
    @Test fun modifierParentsExistAndOptionalArgumentsFollowRequiredOnes() {
        unique(catalog.modifiers.map { it.name })
        catalog.modifiers.forEach { modifier ->
            modifier.allowedParents.forEach { assertEquals(modifier.scope, catalog.requireComponent(it).scope) }
            if (modifier.scope != ScopeKind.ANY) assertTrue(modifier.allowedParents.isNotEmpty())
            modifier.signatures.forEach { signature ->
                unique(signature.args.map { it.name })
                var optional = false
                signature.args.forEach { arg ->
                    if (arg.defaultValue != null) { optional = true; assertTrue(arg.type.accepts(arg.defaultValue)) }
                    else assertFalse("Required argument after default in ${modifier.name}", optional)
                    if (arg.callback) assertEquals(setOf(ValueKind.NUMBER), arg.type.kinds)
                }
            }
        }
        unique(catalog.shortcuts.map { it.name })
        catalog.shortcuts.forEach { assertNotNull(catalog.modifier(it.modifierName)) }
    }
    @Test fun shortcutsHaveStablePrefixOrderAndAliasesCannotConflict() {
        val explicit = ModifierOp(ComposeUiModifiers.ALPHA, listOf(UiValue.Num(0.5)))
        val normalized = catalog.normalizeProperties(ComposeUiComponents.COLUMN, linkedMapOf(
            ComposeUiShortcuts.BG to UiValue.Color(0xff000000.toInt()), ComposeUiShortcuts.PADDING to UiValue.Dp(8.0),
            ComposeUiPropAliases.VERTICAL_ARRANGEMENT to UiValue.Enum("top")), listOf(explicit))
        assertEquals(listOf("padding", "background", "alpha"), normalized.modifier.map { it.name })
        assertEquals(setOf(ComposeUiProps.ARRANGEMENT), normalized.props.keys)
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { catalog.normalizeProperties("Column", mapOf("arrangement" to UiValue.Enum("top"), "verticalArrangement" to UiValue.Enum("bottom"))) }
    }
    @Test fun invalidScopeArgumentsAndNodeShapeAreTypedFailures() {
        val weight = ModifierOp(ComposeUiModifiers.WEIGHT, listOf(UiValue.Num(1.0)), ScopeKind.COLUMN_ROW)
        catalog.validateModifier(weight, ComposeUiComponents.ROW)
        fails(ComposeUiErrorCodes.SCOPE_MISMATCH) { catalog.validateModifier(weight, ComposeUiComponents.BOX) }
        fails(ComposeUiErrorCodes.INVALID_MODIFIER) { catalog.validateModifier(ModifierOp("alpha", listOf(UiValue.Num(2.0))), null) }
        fails(ComposeUiErrorCodes.INVALID_MODIFIER) { catalog.validateModifier(ModifierOp("weight", listOf(UiValue.Num(1e-300)), ScopeKind.COLUMN_ROW), "Row") }
        fails(ComposeUiErrorCodes.UNKNOWN_COMPONENT) { catalog.validateNode(UiNode(1, "Unknown")) }
        fails(ComposeUiErrorCodes.UNKNOWN_PROP) { catalog.validateNode(UiNode(1, "Text", props = mapOf("unknown" to UiValue.Str("")))) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { catalog.validateNode(UiNode(1, "Image")) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { catalog.validateNode(UiNode(1, "Snackbar", props = mapOf("message" to UiValue.Str("hello")))) }
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { catalog.validateNode(UiNode(1, "Slider", props = mapOf("range" to UiValue.ListOf(listOf(UiValue.Num(2.0), UiValue.Num(1.0)))))) }
    }
}
