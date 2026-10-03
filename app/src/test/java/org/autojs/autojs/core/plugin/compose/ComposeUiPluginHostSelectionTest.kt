package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiIds
import org.junit.Assert.*
import org.junit.Test

class ComposeUiPluginHostSelectionTest {
    /** Fake PackageManager plus host enable/trust stores, without Android stub method calls. */
    private class Packages(private val failed: Int) : ComposePluginInventory {
        val calls = ArrayList<Int>()
        private fun gate(index: Int): Boolean { calls += index; return index != failed }
        override fun installed() = gate(0)
        override fun systemEnabled() = gate(1)
        override fun centerEnabled() = gate(2)
        override fun authorized() = gate(3)
        override fun requiredHostVersion() = if (gate(4)) ComposeUiIds.REQUIRED_HOST_VERSION_CODE else Long.MAX_VALUE
        override fun hasFactoryAndInfoService() = gate(5)
    }
    @Test fun preLoadGatesAreLazyOrderedAndHaveTypedFailures() {
        val reasons = listOf(SelectionFailure.MISSING, SelectionFailure.SYSTEM_DISABLED, SelectionFailure.CENTER_DISABLED,
            SelectionFailure.UNAUTHORIZED, SelectionFailure.HOST_TOO_OLD, SelectionFailure.DISCOVERY)
        reasons.forEachIndexed { i, reason ->
            val packages = Packages(i)
            assertEquals(reason, ComposeUiPluginSelection.beforeLoad(packages, ComposeUiIds.REQUIRED_HOST_VERSION_CODE))
            assertEquals((0..i).toList(), packages.calls)
        }
        assertNull(ComposeUiPluginSelection.beforeLoad(Packages(-1), ComposeUiIds.REQUIRED_HOST_VERSION_CODE))
        assertEquals(ComposeUiErrorCodes.PLUGIN_DISABLED, SelectionFailure.SYSTEM_DISABLED.code)
        assertEquals(ComposeUiErrorCodes.PLUGIN_UNAUTHORIZED, SelectionFailure.UNAUTHORIZED.code)
        assertEquals(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE, SelectionFailure.HOST_TOO_OLD.code)
    }
    @Test fun contractMismatchNeverReadsCapabilitiesAndFingerprintMustMatch() {
        assertEquals(SelectionFailure.CONTRACT, ComposeUiPluginSelection.afterLoad(-1, { error("must not run") }, "host"))
        assertEquals(SelectionFailure.FINGERPRINT, ComposeUiPluginSelection.afterLoad(1, { "other" }, "host"))
        assertEquals(SelectionFailure.FINGERPRINT, ComposeUiPluginSelection.afterLoad(1, { null }, "host"))
        assertNull(ComposeUiPluginSelection.afterLoad(1, { "host" }, "host"))
    }
}
