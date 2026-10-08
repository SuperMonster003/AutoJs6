package org.autojs.plugin.compose.api.interop

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.model.UiNode
import org.autojs.plugin.compose.api.model.UiValue
import org.autojs.plugin.compose.api.model.ValueKind
import org.junit.Assert.*
import org.junit.Test
import java.lang.ref.WeakReference

class AndroidViewInteropV1Test {
    @Test fun independentCatalogAddsOnlyOneProjectedComponentAndNeverMutatesV1() {
        assertTrue(ComposeUiContract.supports(1))
        assertEquals(30, ComponentCatalog.V1.components.size)
        assertNull(ComponentCatalog.V1.component(AndroidViewInteropV1.COMPONENT))
        assertEquals(31, AndroidViewInteropV1.catalog.components.size)
        ComponentCatalog.V1.components.forEach { assertSame(it, AndroidViewInteropV1.catalog.component(it.name)) }
        assertEquals(setOf(ValueKind.NUMBER), AndroidViewInteropV1.component.props.single().type.kinds)
        for (value in listOf(1.0, Int.MAX_VALUE.toDouble())) AndroidViewInteropV1.catalog.validateNode(
            UiNode(1, AndroidViewInteropV1.COMPONENT, props = mapOf(AndroidViewInteropV1.PROP_VIEW to UiValue.Num(value))))
        for (value in listOf(UiValue.Num(0.0), UiValue.Num(-1.0), UiValue.Num(1.5), UiValue.Num(Int.MAX_VALUE.toDouble() + 1), UiValue.Null, UiValue.Str("view"))) {
            failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { AndroidViewInteropV1.catalog.validateNode(
                UiNode(1, AndroidViewInteropV1.COMPONENT, props = mapOf(AndroidViewInteropV1.PROP_VIEW to value))) }
        }
        failure(ComposeUiErrorCodes.UNKNOWN_COMPONENT) { ComponentCatalog.V1.validateNode(
            UiNode(1, AndroidViewInteropV1.COMPONENT, props = mapOf(AndroidViewInteropV1.PROP_VIEW to UiValue.Num(1.0)))) }
    }

    @Test fun failedReplacementNeverReleasesOldOwnershipOrReservesEarlierCandidateViews() {
        val table = AndroidViewClaimTable()
        val first = Any(); val second = Any(); val a = Any(); val b = Any(); val c = Any()
        table.replace(first, listOf(AndroidViewClaimTable.Entry(1, 10, a)))
        table.replace(second, listOf(AndroidViewClaimTable.Entry(2, 20, b)))
        failure(ComposeUiErrorCodes.NODE_DETACHED) { table.replace(first, listOf(
            AndroidViewClaimTable.Entry(3, 30, c), AndroidViewClaimTable.Entry(2, 20, b))) }
        assertTrue(table.isOwnedBy(first, a, 1, 10)); assertTrue(table.isOwnedBy(second, b, 2, 20))
        assertFalse(table.isClaimed(c)); assertEquals(2, table.size)
        table.release(first); table.release(first)
        assertFalse(table.isClaimed(a)); assertTrue(table.isClaimed(b)); assertEquals(1, table.size)
    }

    @Test fun identityAndNodeTokenUniquenessRemainStrictAcrossOwners() {
        class EqualView { override fun equals(other: Any?) = other is EqualView; override fun hashCode() = 0 }
        val table = AndroidViewClaimTable()
        val first = Any(); val second = Any(); val a = EqualView(); val b = EqualView()
        table.replace(first, listOf(AndroidViewClaimTable.Entry(1, 10, a)))
        table.replace(second, listOf(AndroidViewClaimTable.Entry(2, 20, b)))
        assertEquals(2, table.size)
        failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { table.replace(first, listOf(AndroidViewClaimTable.Entry(2, 10, a))) }
        failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { table.replace(first, listOf(AndroidViewClaimTable.Entry(1, 11, a))) }
        failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { table.replace(Any(), listOf(
            AndroidViewClaimTable.Entry(5, 50, Any()), AndroidViewClaimTable.Entry(6, 50, Any()))) }
        failure(ComposeUiErrorCodes.INVALID_ARGUMENT) { table.replace(Any(), listOf(AndroidViewClaimTable.Entry(0, 1, Any()))) }
        assertTrue(table.isOwnedBy(first, a, 1, 10)); assertTrue(table.isOwnedBy(second, b, 2, 20))
    }

    @Test fun claimsDoNotKeepAbandonedViewsOrRendererOwnersAlive() {
        val table = AndroidViewClaimTable()
        val view = Any()
        fun owner(): WeakReference<Any> {
            val owner = Any()
            table.replace(owner, listOf(AndroidViewClaimTable.Entry(1, 1, view)))
            return WeakReference(owner)
        }
        val owner = owner()
        val liveOwner = Any()
        fun abandonedView(): WeakReference<Any> {
            val value = Any()
            table.replace(liveOwner, listOf(AndroidViewClaimTable.Entry(2, 2, value)))
            return WeakReference(value)
        }
        val abandoned = abandonedView()
        repeat(30) {
            if (owner.get() == null && abandoned.get() == null) return@repeat
            System.gc(); Thread.sleep(10)
        }
        assertNull("Claims retain only a weak renderer owner", owner.get())
        assertNull("Neither identity keys nor claim values retain a View", abandoned.get())
        assertFalse(table.isClaimed(view)); assertEquals(0, table.size)
    }

    private fun failure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (failure: ComposeUiContractException) { assertEquals(code, failure.code) }
    }
}
