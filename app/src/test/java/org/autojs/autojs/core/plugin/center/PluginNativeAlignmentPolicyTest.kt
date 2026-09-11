package org.autojs.autojs.core.plugin.center

import org.junit.Assert.*
import org.junit.Test

class PluginNativeAlignmentPolicyTest {
    @Test fun unknownAndPureJvmAreDistinctFromIncompatibleNativeLibraries() {
        listOf(null, -1, 16385, 16384.0, "-1", "16 KB", "null", "9223372036854775808").forEach {
            assertNull(PluginNativeAlignmentPolicy.parse(it))
        }
        assertEquals(0L, PluginNativeAlignmentPolicy.parse(0))
        assertEquals(16384L, PluginNativeAlignmentPolicy.parse("16384"))
        assertFalse(PluginNativeAlignmentPolicy.needsWarning(null, 16384))
        assertFalse(PluginNativeAlignmentPolicy.needsWarning(0, 16384))
        assertFalse(PluginNativeAlignmentPolicy.needsWarning(4096, 4096))
        assertTrue(PluginNativeAlignmentPolicy.needsWarning(4096, 16384))
        assertFalse(PluginNativeAlignmentPolicy.needsWarning(16384, 16384))
    }

    @Test fun anAlignedUpdateCannotMakeAnOlderInstalledPluginAppearAligned() {
        val releases = listOf(PluginIndexRelease("2.0", 2, nativePageAlignment = 16384))
        assertNull(PluginNativeAlignmentPolicy.installedAlignment(null, 1, releases))
        assertEquals(4096L, PluginNativeAlignmentPolicy.installedAlignment(4096, 1, releases))
        assertEquals(16384L, PluginNativeAlignmentPolicy.installedAlignment(null, 2, releases))
    }
}
