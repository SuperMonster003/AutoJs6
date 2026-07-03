package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCapabilityResolverTest {

    @Test
    fun hostVersionCompatibilityAllowsMissingOrSatisfiedRequirement() {
        assertTrue(PluginCapabilityResolver.isHostVersionCompatible(3923L, null))
        assertTrue(PluginCapabilityResolver.isHostVersionCompatible(3923L, 0L))
        assertTrue(PluginCapabilityResolver.isHostVersionCompatible(3923L, 3835L))
        assertTrue(PluginCapabilityResolver.isHostVersionCompatible(3923L, 3923L))
    }

    @Test
    fun hostVersionCompatibilityRejectsHigherRequirement() {
        assertFalse(PluginCapabilityResolver.isHostVersionCompatible(3923L, 3924L))
    }
}
