package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginDiscoveryEndpointPolicyTest {

    @Test
    fun commonInfoEndpointWinsRegardlessOfDeclarationOrder() {
        assertNull(
            PluginDiscoveryEndpointPolicy.selectAction(
                listOf("org.autojs.plugin.OCR", null, "org.autojs.plugin.BARCODE"),
            ),
        )
    }

    @Test
    fun specializedEndpointOrderIsStableWhenCommonInfoIsAbsent() {
        assertEquals(
            "org.autojs.plugin.OCR",
            PluginDiscoveryEndpointPolicy.selectAction(
                listOf("org.autojs.plugin.OCR", "org.autojs.plugin.BARCODE"),
            ),
        )
    }
}
