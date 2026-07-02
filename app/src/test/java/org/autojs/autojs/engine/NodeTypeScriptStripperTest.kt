package org.autojs.autojs.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTypeScriptStripperTest {

    @Test
    fun typeScriptDeclarationsAreNotTypeScriptSources() {
        val result = NodeTypeScriptStripper.stripIfTypeScript(
            "globals.d.ts",
            "declare interface User { name: string }\n",
        )

        assertTrue(NodeTypeScriptStripper.isTypeScriptDeclarationSourceName("globals.d.ts"))
        assertFalse(NodeTypeScriptStripper.isTypeScriptSourceName("globals.d.ts"))
        assertFalse(result.stripped)
        assertTrue(NodeTypeScriptStripper.isTypeScriptSourceName("main.ts"))
    }
}
