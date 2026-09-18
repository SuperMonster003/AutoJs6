package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class MailAttachmentSourceTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File.createTempFile("mail-source", "").also {
            it.delete()
            assertTrue(it.mkdirs())
        }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun validReadableFilesAreReturnedInOrder() {
        val first = File(directory, "a.txt").apply { writeText("a") }
        val second = File(directory, "b.txt").apply { writeText("bb") }
        assertEquals(listOf(first, second), MailAttachmentSource.validate(listOf(first.path, second.path)))
        assertEquals(emptyList<File>(), MailAttachmentSource.validate(emptyList()))
    }

    @Test
    fun problemsMapToTheContractCodes() {
        val file = File(directory, "ok.txt").apply { writeText("data") }
        assertEquals(MailErrorCodes.ATTACHMENT_NOT_FOUND, codeOf { MailAttachmentSource.validate(listOf(File(directory, "missing.txt").path)) })
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailAttachmentSource.validate(listOf(directory.path)) })
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailAttachmentSource.validate(listOf(" ")) })
        assertEquals(MailErrorCodes.LIMIT_EXCEEDED, codeOf { MailAttachmentSource.validate(listOf(file.path, file.path), maxCount = 1) })
        assertEquals(MailErrorCodes.LIMIT_EXCEEDED, codeOf { MailAttachmentSource.validate(listOf(file.path), maxBytes = 3L) })
        assertEquals(listOf(file), MailAttachmentSource.validate(listOf(file.path), maxBytes = 4L))
    }

    @Test
    fun defaultsFollowTheContract() {
        assertEquals(64, MailContract.MAX_ATTACHMENTS_PER_MESSAGE)
        val paths = (0 until MailContract.MAX_ATTACHMENTS_PER_MESSAGE + 1).map { File(directory, "f$it").path }
        val error = errorOf { MailAttachmentSource.validate(paths) }
        assertEquals(MailErrorCodes.LIMIT_EXCEEDED, error.code)
        assertFalse(error.retryable)
        assertTrue(error.message.contains("65"))
        assertNull(error.details)
    }

    private fun codeOf(block: () -> Unit): String = errorOf(block).code

    private fun errorOf(block: () -> Unit): MailError {
        try {
            block()
        } catch (e: MailPluginException) {
            return e.error
        }
        fail("expected MailPluginException")
        throw AssertionError()
    }
}
