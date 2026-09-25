package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

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

    @Test
    fun boundedWaitHandlesAnAlreadyExitedProcessWithoutWaiting() {
        assertTrue(NodeCliProbe.waitForExit(ProbeProcess(0), 0))
    }

    @Test
    fun boundedWaitTimesOutWithoutCallingTheBlockingWaitApi() {
        val start = System.nanoTime()
        assertFalse(NodeCliProbe.waitForExit(ProbeProcess(Long.MAX_VALUE), 20))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
    }

    @Test
    fun boundedWaitObservesExitBeforeTheDeadline() {
        assertTrue(NodeCliProbe.waitForExit(ProbeProcess(TimeUnit.MILLISECONDS.toNanos(20)), 2_000))
    }

    @Test
    fun boundedWaitCanBeInterrupted() {
        Thread.currentThread().interrupt()
        try {
            org.junit.Assert.assertThrows(InterruptedException::class.java) {
                NodeCliProbe.waitForExit(ProbeProcess(Long.MAX_VALUE), 2_000)
            }
        } finally {
            Thread.interrupted()
        }
    }

    private class ProbeProcess(private val exitAfterNanos: Long) : Process() {
        private val startedAt = System.nanoTime()
        override fun exitValue(): Int {
            if (System.nanoTime() - startedAt < exitAfterNanos) throw IllegalThreadStateException()
            return 0
        }
        override fun waitFor(): Int = error("The probe must never wait without a deadline")
        override fun destroy() = Unit
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
    }

}
