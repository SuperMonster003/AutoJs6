package org.autojs.autojs.model.explorer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PrivilegedExplorerProtocolTest {
    @Test
    fun rootListingPreservesUnusualNamesAndHiddenDirectories() {
        val specialName = "line\nbreak ' \" ${'$'}(touch injected) 文件.js"
        val listing = PrivilegedExplorerProtocol.parseRootListing("/data/local/tmp/", encoded(
            "AJ6EXPLORER1", "100", "entry", specialName, "f", "1234", "99",
            "entry", ".hidden", "d", "4096", "98", "end", "2",
        ))
        assertEquals("/data/local/tmp", listing.path)
        assertEquals(100_000L, listing.modified)
        assertEquals(specialName, listing.entries[0].name)
        assertEquals(1234L, listing.entries[0].size)
        assertEquals(99_000L, listing.entries[0].modified)
        assertTrue(listing.entries[1].directory)
        assertEquals("/data/local/tmp/$specialName", PrivilegedExplorerProtocol.childPath(listing.path, specialName))
    }

    @Test
    fun anExplicitEmptyListingIsValid() {
        assertTrue(PrivilegedExplorerProtocol.parseRootListing("/", encoded("AJ6EXPLORER1", "0", "end", "0")).entries.isEmpty())
    }

    @Test
    fun truncatedDuplicateAndTraversalRecordsAreRejected() {
        assertInvalid(encoded("AJ6EXPLORER1", "0", "entry", "file", "f", "1", "1"))
        assertInvalid(encoded("AJ6EXPLORER1", "0", "end", "1"))
        assertInvalid(encoded("AJ6EXPLORER1", "0", "entry", "..", "d", "0", "0", "end", "1"))
        assertInvalid(encoded("AJ6EXPLORER1", "0", "entry", "a/b", "f", "0", "0", "end", "1"))
        assertInvalid(encoded("AJ6EXPLORER1", "0", "entry", "same", "f", "0", "0", "entry", "same", "d", "0", "0", "end", "2"))
        assertInvalid(encoded("AJ6EXPLORER1", "0", "entry", "file", "f", "-1", "0", "end", "1"))
        assertInvalid(encoded("AJ6EXPLORER1", Long.MAX_VALUE.toString(), "end", "0"))
        assertInvalid(byteArrayOf(0xc3.toByte(), 0x28))
    }

    @Test
    fun shellArgumentsRemainSingleQuotedEvenWithShellMetacharacters() {
        val path = "/data/a'b;${'$'}(touch nope)\n*"
        assertEquals("'/data/a'\"'\"'b;${'$'}(touch nope)\n*'", PrivilegedExplorerProtocol.quoteShellArgument(path))
        assertTrue(PrivilegedExplorerProtocol.rootCommand(path).contains("cd '/data/a'\"'\"'b;${'$'}(touch nope)\n*' || exit 11"))
        assertFalse(PrivilegedExplorerProtocol.rootCommand(path).contains("chmod"))
        try {
            PrivilegedExplorerProtocol.rootCommand("/data/invalid\u0000name")
            fail("A NUL path must be rejected before starting su")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun processDrainsStdoutAndStderrConcurrently() {
        val result = PrivilegedExplorerProcess.run(fixture("both"), 10_000L)
        assertEquals(0, result.exitCode)
        assertArrayEquals(ByteArray(128 * 1024) { 'o'.code.toByte() }, result.stdout)
        assertEquals("e".repeat(32 * 1024), result.stderr)
    }

    @Test
    fun processRejectsOutputOverflowInsteadOfReturningATruncatedListing() {
        try {
            PrivilegedExplorerProcess.run(fixture("overflow"), 10_000L)
            fail("Oversized output must fail")
        } catch (error: PrivilegedExplorerAccessException) {
            assertEquals(PrivilegedExplorerAccessException.Reason.TOO_LARGE, error.reason)
        }
    }

    @Test
    fun processStopsWhenItsDeadlineExpires() {
        val start = System.nanoTime()
        try {
            PrivilegedExplorerProcess.run(fixture("wait"), 250L)
            fail("An unresponsive command must time out")
        } catch (error: PrivilegedExplorerAccessException) {
            assertEquals(PrivilegedExplorerAccessException.Reason.TIMEOUT, error.reason)
        }
        assertTrue("The timeout must bound the wait", (System.nanoTime() - start) / 1_000_000L < 5000L)
    }

    @Test
    fun cancellingDoesNotWaitForInheritedPipesToClose() {
        val start = System.nanoTime()
        try {
            PrivilegedExplorerProcess.run(fixture("inherited-pipes"), 500L)
            fail("The parent command must time out")
        } catch (error: PrivilegedExplorerAccessException) {
            assertEquals(PrivilegedExplorerAccessException.Reason.TIMEOUT, error.reason)
        }
        assertTrue("Cleanup must not wait for the descendant's open pipes", (System.nanoTime() - start) / 1_000_000L < 2000L)
    }

    private fun fixture(mode: String): List<String> = listOf(
        File(System.getProperty("java.home"), "bin/java").absolutePath,
        "-cp", File(PrivilegedExplorerProcessFixture::class.java.protectionDomain.codeSource.location.toURI()).absolutePath,
        PrivilegedExplorerProcessFixture::class.java.name, mode,
    )

    private fun encoded(vararg fields: String) = (fields.joinToString("\u0000") + '\u0000').toByteArray(Charsets.UTF_8)

    private fun assertInvalid(output: ByteArray) {
        try {
            PrivilegedExplorerProtocol.parseRootListing("/", output)
            fail("Incomplete or invalid directory data must fail")
        } catch (error: PrivilegedExplorerAccessException) {
            assertEquals(PrivilegedExplorerAccessException.Reason.INVALID_DATA, error.reason)
        }
    }
}
