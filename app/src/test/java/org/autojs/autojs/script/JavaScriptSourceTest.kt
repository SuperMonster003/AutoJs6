package org.autojs.autojs.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaScriptSourceTest {

    @Test
    fun typeScriptDeclarationsAreNotExecutableScriptFiles() {
        assertTrue(JavaScriptSource.isTypeScriptDeclarationFileName("types.d.ts"))
        assertFalse(JavaScriptSource.isJavaScriptFileName("types.d.ts"))
        assertFalse(JavaScriptSource.isNodeJsFileName("types.d.ts"))

        assertTrue(JavaScriptSource.isJavaScriptFileName("main.ts"))
        assertTrue(JavaScriptSource.isNodeJsFileName("main.ts"))
    }
}
