package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test

class CallbackRegistryTest {
    private fun tree(id: Int) = UiTree(10, listOf(UiNode(10, ComposeUiComponents.BUTTON, callbacks = mapOf(ComposeUiEvents.CLICK to id))))
    @Test fun callbacksBecomeVisibleOnlyAtCommitAndOldGenerationsAreDropped() {
        val registry = CallbackRegistry()
        val first = registry.register { }
        assertNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, first))
        registry.commit(tree(first), 1)
        assertNotNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, first))
        assertNull(registry.resolve(1, 11, ComposeUiEvents.CLICK, first))
        assertNull(registry.resolve(1, 10, ComposeUiEvents.LONG_CLICK, first))
        val second = registry.register { }
        registry.commit(tree(second), 2)
        assertNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, first))
        assertNotNull(registry.resolve(2, 10, ComposeUiEvents.CLICK, second))
        assertEquals(1, registry.size)
    }
    @Test fun pendingCallbacksSurviveAckButCloseAndUnmountReleaseAllReferences() {
        val registry = CallbackRegistry()
        val first = registry.register { }; val next = registry.register { }
        registry.commit(tree(first), 1, tree(next))
        assertEquals(2, registry.size)
        registry.commit(tree(next), 2)
        assertEquals(1, registry.size)
        registry.commit(UiTree(10, listOf(UiNode(10, ComposeUiComponents.BUTTON))), 3)
        assertEquals(0, registry.size)
        registry.close()
        assertNull(registry.resolve(2, 10, ComposeUiEvents.CLICK, next))
        try { registry.register { }; fail() } catch (e: ComposeUiContractException) { assertEquals(ComposeUiErrorCodes.SESSION_CLOSED, e.code) }
    }
}
