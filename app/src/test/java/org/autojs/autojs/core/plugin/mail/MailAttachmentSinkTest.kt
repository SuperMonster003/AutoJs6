package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class MailAttachmentSinkTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File.createTempFile("mail-sink", "").also {
            it.delete()
            assertTrue(it.mkdirs())
        }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun spoolWritesThroughAPartFileAndRenamesAtomically() {
        val payload = ByteArray(MailAttachmentSink.BUFFER_SIZE * 3 + 17) { (it % 251).toByte() }
        val target = File(directory, "report.pdf")
        val observed = ArrayList<Boolean>()
        val input = object : InputStream() {
            val delegate = ByteArrayInputStream(payload)
            override fun read(): Int = delegate.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                observed += target.exists()
                return delegate.read(b, off, len)
            }
        }
        val result = MailAttachmentSink.spool(input, target, payload.size.toLong())
        assertEquals(target, result)
        assertArrayEquals(payload, target.readBytes())
        assertTrue(observed.isNotEmpty())
        assertTrue("target must not exist while streaming", observed.none { it })
        assertFalse(MailAttachmentSink.partFile(target).exists())
        assertEquals(listOf("report.pdf"), directory.list()!!.toList())
    }

    @Test
    fun unknownSizeIsAcceptedWhenBelowTheCeiling() {
        val target = File(directory, "unknown.bin")
        MailAttachmentSink.spool(ByteArrayInputStream(ByteArray(10)), target, expectedSize = null, maxBytes = 10L)
        assertEquals(10L, target.length())
    }

    @Test
    fun shortStreamFailsWithoutLeavingFiles() {
        val target = File(directory, "short.bin")
        val error = failure { MailAttachmentSink.spool(ByteArrayInputStream(ByteArray(5)), target, expectedSize = 10L) }
        assertEquals(MailErrorCodes.IO_FAILED, error.code)
        assertTrue(error.message.contains("5 of 10"))
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun streamLongerThanDeclaredFails() {
        val target = File(directory, "long.bin")
        val error = failure { MailAttachmentSink.spool(ByteArrayInputStream(ByteArray(12)), target, expectedSize = 3L) }
        assertEquals(MailErrorCodes.IO_FAILED, error.code)
        assertTrue(error.message.contains("declared size"))
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun streamAboveTheCeilingFails() {
        val target = File(directory, "big.bin")
        val error = failure { MailAttachmentSink.spool(ByteArrayInputStream(ByteArray(12)), target, expectedSize = null, maxBytes = 4L) }
        assertEquals(MailErrorCodes.LIMIT_EXCEEDED, error.code)
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun declaredSizeAboveTheCeilingIsRejectedBeforeReading() {
        val target = File(directory, "big.bin")
        var read = false
        val input = object : InputStream() {
            override fun read(): Int {
                read = true
                return -1
            }
        }
        val error = failure { MailAttachmentSink.spool(input, target, expectedSize = 10L, maxBytes = 4L) }
        assertEquals(MailErrorCodes.LIMIT_EXCEEDED, error.code)
        assertFalse(read)
        assertEquals(MailErrorCodes.LIMIT_EXCEEDED, failure { MailAttachmentSink.precheck(target, 10L, 4L) }.code)
    }

    @Test
    fun cancellationAbortsAndDeletesThePartFile() {
        val target = File(directory, "cancel.bin")
        var reads = 0
        val error = failure {
            MailAttachmentSink.spool(ByteArrayInputStream(ByteArray(MailAttachmentSink.BUFFER_SIZE * 4)), target, expectedSize = null) { ++reads > 2 }
        }
        assertEquals(MailErrorCodes.CANCELLED, error.code)
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun ioFailureWhileReadingMapsToIoFailed() {
        val target = File(directory, "broken.bin")
        val input = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("pipe broken")
        }
        val error = failure { MailAttachmentSink.spool(input, target, expectedSize = null) }
        assertEquals(MailErrorCodes.IO_FAILED, error.code)
        assertTrue(error.message.contains("pipe broken"))
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun overwriteReplacesAnExistingTarget() {
        val target = File(directory, "same.txt")
        target.writeText("old")
        MailAttachmentSink.spool(ByteArrayInputStream("new".toByteArray()), target, expectedSize = 3L)
        assertEquals("new", target.readText())
    }

    @Test
    fun resolveTargetSanitizesServerNamesInsideDirectories() {
        val resolved = MailAttachmentSink.resolveTarget(directory.path, "../../evil.txt", overwrite = false)
        assertEquals(directory.absoluteFile, resolved.absoluteFile.parentFile)
        assertEquals("evil.txt", resolved.name)
        assertEquals("attachment", MailAttachmentSink.resolveTarget(directory.path, null, overwrite = false).name)
        val nested = MailAttachmentSink.resolveTarget(File(directory, "sub").path + File.separator, "a.txt", overwrite = false)
        assertTrue(nested.parentFile!!.isDirectory)
        assertEquals("sub", nested.parentFile!!.name)
        assertEquals("a.txt", nested.name)
        val slash = MailAttachmentSink.resolveTarget(File(directory, "other").path + "/", "b.txt", overwrite = false)
        assertEquals("other", slash.parentFile!!.name)
        assertEquals("b.txt", slash.name)
    }

    @Test
    fun resolveTargetAppendsASuffixUnlessOverwriting() {
        val existing = File(directory, "dup.txt").apply { writeText("x") }
        val next = MailAttachmentSink.resolveTarget(directory.path, "dup.txt", overwrite = false)
        assertEquals("dup (1).txt", next.name)
        assertEquals(existing, MailAttachmentSink.resolveTarget(directory.path, "dup.txt", overwrite = true))
        assertEquals(existing, MailAttachmentSink.resolveTarget(existing.path, null, overwrite = true))
        assertEquals("dup (1).txt", MailAttachmentSink.resolveTarget(existing.path, null, overwrite = false).name)
        val explicit = MailAttachmentSink.resolveTarget(File(directory, "deep/er/file.bin").path, "ignored.txt", overwrite = false)
        assertEquals("file.bin", explicit.name)
        assertTrue(explicit.parentFile!!.isDirectory)
        try {
            MailAttachmentSink.resolveTarget(" ", null, overwrite = false)
            fail("blank path accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun failure(block: () -> Unit): MailError {
        try {
            block()
        } catch (e: MailPluginException) {
            return e.error
        }
        fail("expected MailPluginException")
        throw AssertionError()
    }
}
