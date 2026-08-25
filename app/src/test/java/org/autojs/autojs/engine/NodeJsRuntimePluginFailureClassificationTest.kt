package org.autojs.autojs.engine

import android.os.RemoteException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeJsRuntimePluginFailureClassificationTest {

    @Test
    fun dispatchedBinderLossIsReadableAndNeverFallbackSafe() {
        val error = NodeRuntimePluginOperationException(
            message = "Node.js runtime plugin execution response was lost.",
            cause = RemoteException("runtime process restarted"),
            candidate = null,
            diagnostics = null,
            dispatchStarted = true,
        )

        val classification = NodeJsRuntimePluginBridge.classifyPluginFailure(error)

        assertEquals("execution_lost", classification.failureKind)
        assertEquals(NodeRuntimeErrorCodes.NODE_PLUGIN_EXECUTION_LOST, classification.errorCode)
        assertFalse(classification.fallbackSafe)
    }

    @Test
    fun preDispatchSessionFailureRemainsFallbackSafe() {
        val error = NodeRuntimePluginOperationException(
            message = "Node.js runtime plugin session preparation failed.",
            candidate = null,
            diagnostics = null,
            dispatchStarted = false,
        )

        val classification = NodeJsRuntimePluginBridge.classifyPluginFailure(error)

        assertEquals("session", classification.failureKind)
        assertEquals(NodeRuntimeErrorCodes.NODE_PLUGIN_UNAVAILABLE, classification.errorCode)
        assertTrue(classification.fallbackSafe)
    }
}
