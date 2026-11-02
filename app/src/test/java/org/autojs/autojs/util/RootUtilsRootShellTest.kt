package org.autojs.autojs.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class RootUtilsRootShellTest {

    // RootShell itself cannot run here, so the test holds the turn taking that its shared shell needs (api-coverage P6.2).
    @Test
    fun rootShellCheckTakesTurns() {
        val rootUtils = Class.forName("org.autojs.autojs.util.RootUtils", false, javaClass.classLoader)
        assertTrue(Modifier.isSynchronized(rootUtils.getDeclaredMethod("getRootStateWithRootShell").modifiers))
    }
}
