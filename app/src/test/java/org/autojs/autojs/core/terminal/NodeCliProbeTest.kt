package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NodeCliProbeTest {

    @Test
    fun permissionFailuresAreClassifiedAsDenied() {
        assertTrue(NodeCliProbe.classifyDenied(null, "Cannot run program \"/x/libnodexe.so\": error=13, Permission denied", ""))
        assertTrue(NodeCliProbe.classifyDenied(126, null, "sh: /x/libnodexe.so: Permission denied"))
        assertTrue(NodeCliProbe.classifyDenied(1, null, "spawn EACCES"))
        assertTrue(NodeCliProbe.classifyDenied(126, null, ""))
    }

    @Test
    fun otherFailuresAreNotDenied() {
        assertFalse(NodeCliProbe.classifyDenied(0, null, ""))
        assertFalse(NodeCliProbe.classifyDenied(1, null, "Error: Cannot find module"))
        assertFalse(NodeCliProbe.classifyDenied(126, null, "Exec format error"))
        assertFalse(NodeCliProbe.classifyDenied(null, "error=2, No such file or directory", ""))
        assertFalse(NodeCliProbe.classifyDenied(139, null, ""))
    }

    @Test
    fun resultSummarizesEachOutcome() {
        val ok = NodeCliProbe.Result(0, "v24.21.0\n", "", 27, null, false)
        assertTrue(ok.succeeded)
        assertFalse(ok.denied)
        assertEquals("v24.21.0", ok.versionOutput)
        assertEquals("ok v24.21.0 (27 ms)", ok.summary())

        val denied = NodeCliProbe.Result(null, "", "", 3, "error=13, Permission denied", false)
        assertFalse(denied.succeeded)
        assertTrue(denied.denied)
        assertTrue(denied.summary().startsWith("failed to start"))

        val timedOut = NodeCliProbe.Result(null, "", "", 15000, null, true)
        assertFalse(timedOut.succeeded)
        assertEquals("timed out after 15000 ms", timedOut.summary())

        val crashed = NodeCliProbe.Result(134, "", "abort\n", 10, null, false)
        assertFalse(crashed.succeeded)
        assertEquals("exit 134: abort", crashed.summary())
    }

    @Test
    fun runReportsAMissingExecutableWithoutThrowing() {
        val result = NodeCliProbe.run(File("/definitely/missing/libnodexe.so"), timeoutMs = 2_000)
        assertNull(result.exitCode)
        assertNotNull(result.error)
        assertFalse(result.timedOut)
        assertFalse(result.succeeded)
    }

}
