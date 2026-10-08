package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.v2.ComposeUiV2
import org.junit.Assert.*
import org.junit.Test

class ComposeUiV2NegotiationTest {
    @Test fun originalV1ProvidersRemainValidAndCannotAdvertiseV2Components() {
        val old = ComponentCatalog.V1.components.map { it.name }.toSet()
        assertTrue(ComposeUiPluginLoader.validComponents(1, old))
        assertTrue(ComposeUiPluginLoader.validComponents(2, old))
        assertFalse(ComposeUiPluginLoader.validComponents(1, old + "TimePicker"))
        assertTrue(ComposeUiPluginLoader.validComponents(2, ComposeUiV2.catalog.components.map { it.name }.toSet()))
        assertFalse(ComposeUiPluginLoader.validComponents(3, old))
        assertFalse(ComposeUiPluginLoader.validComponents(2, emptySet()))
        assertFalse(ComposeUiPluginLoader.validComponents(2, setOf("Unknown")))
        // Interop remains a separately negotiated capability, including under V2.
        assertFalse(ComposeUiPluginLoader.validComponents(2, setOf("AndroidView")))
    }
    @Test fun bothSupportedVersionsStillRequireTheSharedDependencyFingerprint() {
        for (version in listOf(1, 2)) {
            assertNull(ComposeUiPluginSelection.afterLoad(version, { "same" }, "same"))
            assertEquals(SelectionFailure.FINGERPRINT, ComposeUiPluginSelection.afterLoad(version, { "other" }, "same"))
        }
        assertEquals(SelectionFailure.CONTRACT, ComposeUiPluginSelection.afterLoad(3, { error("must not read") }, "same"))
    }
}
