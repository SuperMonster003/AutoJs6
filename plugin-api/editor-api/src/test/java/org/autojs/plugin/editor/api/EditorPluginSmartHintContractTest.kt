package org.autojs.plugin.editor.api

import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPluginSmartHintContractTest {

    @Test
    fun smartHintTriggerRequiresContractSix() {
        assertTrue(EditorPluginContract.MAX_SUPPORTED_VERSION >= 6)
        assertTrue(EditorPluginContract.MIN_SUPPORTED_VERSION <= 5)
    }

    @Test
    fun sessionExposesSmartHintTrigger() {
        val method = EditorPluginSession::class.java.methods.firstOrNull { it.name == "triggerSmartHint" }
        assertTrue(method != null && method.parameterCount == 0)
    }
}
