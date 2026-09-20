package org.autojs.autojs.core.plugin.epub

import org.autojs.plugin.epub.api.EpubErrorCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class EpubOutputSinkTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun codeOf(block: () -> Unit): String = try {
        block()
        fail("expected an EpubPluginException")
        ""
    } catch (e: EpubPluginException) {
        e.code
    }

    @Test
    fun aDirectoryTargetTakesTheFileNameAndAPlainPathIsTheFileItself() {
        val dir = folder.newFolder("out")
        assertEquals(File(dir, "cover.png"), EpubOutputSink.resolveTarget(dir.path, "nested/cover.png", overwrite = false))
        assertEquals(File(dir, "cover.png"), EpubOutputSink.resolveTarget(dir.path, "C:\\x\\cover.png", overwrite = false))
        val plain = File(folder.root, "deep/er/chapter.xhtml")
        assertEquals(plain, EpubOutputSink.resolveTarget(plain.path, null, overwrite = false))
        assertTrue(plain.parentFile!!.isDirectory)
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubOutputSink.resolveTarget(dir.path, null, overwrite = false) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubOutputSink.resolveTarget(dir.path, "..", overwrite = false) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubOutputSink.resolveTarget(dir.path, "a/", overwrite = false) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubOutputSink.resolveTarget(" ", "x", overwrite = false) })
    }

    @Test
    fun anExistingTargetIsRefusedUnlessOverwriteIsSet() {
        val existing = folder.newFile("taken.png")
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubOutputSink.resolveTarget(existing.path, null, overwrite = false) })
        assertEquals(existing, EpubOutputSink.resolveTarget(existing.path, null, overwrite = true))
        val dir = folder.newFolder("dir")
        assertEquals(File(dir, "dir"), EpubOutputSink.resolveTarget(File(folder.root, "dir").path, "dir", overwrite = true))
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubOutputSink.resolveTarget(dir.parentFile!!.path, "dir", overwrite = true) })
    }

    @Test
    fun spoolWritesThroughAPartFileAndLeavesNoPartBehind() {
        val target = File(folder.root, "chapter.xhtml")
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val result = EpubOutputSink.spool(ByteArrayInputStream(bytes), target)
        assertEquals(target, result)
        assertArrayEquals(bytes, target.readBytes())
        assertFalse(EpubOutputSink.partFile(target).exists())
        val replaced = EpubOutputSink.spool(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target)
        assertArrayEquals(byteArrayOf(1, 2, 3), replaced.readBytes())
    }

    @Test
    fun theByteCeilingFailsWithLimitExceededAndDeletesThePart() {
        val target = File(folder.root, "big.bin")
        val input = ByteArrayInputStream(ByteArray(1025))
        assertEquals(EpubErrorCodes.LIMIT_EXCEEDED, codeOf { EpubOutputSink.spool(input, target, maxBytes = 1024) })
        assertFalse(target.exists())
        assertFalse(EpubOutputSink.partFile(target).exists())
        val exact = EpubOutputSink.spool(ByteArrayInputStream(ByteArray(1024)), target, maxBytes = 1024)
        assertEquals(1024L, exact.length())
    }

    @Test
    fun aFailedVerificationOrReadKeepsTheOldTargetUntouched() {
        val target = folder.newFile("keep.txt").apply { writeText("old") }
        assertEquals(EpubErrorCodes.IO, codeOf {
            EpubOutputSink.spool(ByteArrayInputStream("new".toByteArray()), target) {
                throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.IO, "plugin closed with error"))
            }
        })
        assertEquals("old", target.readText())
        assertFalse(EpubOutputSink.partFile(target).exists())
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("pipe broke")
        }
        assertEquals(EpubErrorCodes.IO, codeOf { EpubOutputSink.spool(broken, target) })
        assertEquals("old", target.readText())
        assertFalse(EpubOutputSink.partFile(target).exists())
    }
}
