package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDefaultEnabledPolicyTest {

    @Test
    fun yoloIsDisabledByDefault() {
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault("yolo"))
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(" YOLO "))
    }

    @Test
    fun existingAndUnknownEnginesKeepLegacyDefault() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault("paddle-ocr"))
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(null))
    }
}
