package org.autojs.autojs.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeJsPluginWorkspaceArchiveTransportPolicyTest {

    @Test
    fun suppressesRuntimeOnlyModuleSourceFilesAndTheirReplacementTrees() {
        val ephemeral = setOf("main.cjs", "src/generated.mjs")

        assertTrue(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "main.cjs",
                ephemeral,
            ),
        )
        assertTrue(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "main.cjs/replacement.txt",
                ephemeral,
            ),
        )
        assertTrue(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "src/generated.mjs",
                ephemeral,
            ),
        )
        assertFalse(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "main.cjs.map",
                ephemeral,
            ),
        )
        assertFalse(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "src/generated-other.mjs",
                ephemeral,
            ),
        )
    }
}
