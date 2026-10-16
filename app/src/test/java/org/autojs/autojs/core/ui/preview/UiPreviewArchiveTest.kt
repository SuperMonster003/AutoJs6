package org.autojs.autojs.core.ui.preview

import org.autojs.plugin.ui.preview.UiPreviewContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` R3: the zips of preview contract v3 unpack only regular
 * files under safe relative names, within the entry and byte limits, and a rejected zip leaves nothing behind.
 *
 * zh-CN: 宿主路线图 R3: 预览契约 v3 的 zip 只以安全的相对名称解出普通文件, 受条目数与字节上限约束, 被拒绝的 zip 不留残余.
 */
class UiPreviewArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String?>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                text?.let { zip.write(it.toByteArray()) }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun rejected(bytes: ByteArray, maxEntries: Int = UiPreviewContract.MAX_ARCHIVE_ENTRIES, maxBytes: Long = UiPreviewContract.MAX_ARCHIVE_UNPACKED_BYTES): String {
        val target = File(temp.root, "out")
        try {
            UiPreviewArchive.unpack(ByteArrayInputStream(bytes), target, maxEntries, maxBytes)
            fail("the zip was accepted")
        } catch (e: UiPreviewRequestException) {
            assertEquals(UiPreviewContract.ERROR_ARCHIVE_INVALID, e.code)
            assertFalse("nothing is left behind", target.exists())
            assertFalse("nothing is left behind", File(temp.root, "out.partial").exists())
            return e.message.orEmpty()
        }
        throw AssertionError("unreachable")
    }

    @Test
    fun filesAndDirectoriesUnpackUnderTheTarget() {
        val target = File(temp.root, "project")
        val bytes = zip(
            "main.js" to "\"ui\";",
            "res/" to null,
            "res/layout/main.xml" to "<LinearLayout/>",
            "res/values/dimens.xml" to "<resources/>",
        )
        UiPreviewArchive.unpack(ByteArrayInputStream(bytes), target)
        assertEquals("\"ui\";", File(target, "main.js").readText())
        assertEquals("<LinearLayout/>", File(target, "res/layout/main.xml").readText())
        assertTrue(File(target, "res/values/dimens.xml").isFile)
        assertFalse(File(temp.root, "project.partial").exists())
    }

    @Test
    fun unsafeNamesRejectTheWholeZip() {
        for (name in listOf("../evil.js", "res/../../evil.js", "/abs.js", "res\\layout\\main.xml", "C:evil.js", "res//main.xml", "./main.js", "res/./main.xml")) {
            val message = rejected(zip("main.js" to "ok", name to "x"))
            assertTrue(message, message.contains("zip entry"))
        }
        assertTrue(rejected(zip(("a/".repeat(300) + "x.js") to "x")).contains("exceeds"))
    }

    @Test
    fun repeatedNamesAreRejected() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                // ZipOutputStream refuses an exact duplicate, so the second name differs only by its directory marker.
                zip.putNextEntry(ZipEntry("res/a")); zip.write("1".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("res/a/")); zip.closeEntry()
            }
        }.toByteArray()
        assertTrue(rejected(bytes).contains("twice"))
    }

    @Test
    fun entryAndByteLimitsAreCountedWhileUnpacking() {
        assertTrue(rejected(zip("a" to "1", "b" to "2", "c" to "3"), maxEntries = 2).contains("more than 2 entries"))
        // A highly compressible entry: its header size is not trusted, the written bytes are counted.
        assertTrue(rejected(zip("big.txt" to "x".repeat(10_000)), maxBytes = 4_096).contains("more than 4096 bytes"))
        val target = File(temp.root, "fits")
        UiPreviewArchive.unpack(ByteArrayInputStream(zip("big.txt" to "x".repeat(4_096))), target, maxUnpackedBytes = 4_096)
        assertEquals(4_096L, File(target, "big.txt").length())
    }

    @Test
    fun anExistingTargetIsNeverOverwritten() {
        val target = File(temp.root, "taken").apply { mkdirs() }
        try {
            UiPreviewArchive.unpack(ByteArrayInputStream(zip("a" to "1")), target)
            fail("an existing target was reused")
        } catch (e: java.io.IOException) {
            assertTrue(target.isDirectory)
        }
    }
}
