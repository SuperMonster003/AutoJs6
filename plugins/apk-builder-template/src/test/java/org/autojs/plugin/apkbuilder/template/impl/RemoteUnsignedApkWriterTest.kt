package org.autojs.plugin.apkbuilder.template.impl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class RemoteUnsignedApkWriterTest {
    @Test fun obsoleteSignatureEntriesAreDroppedButServicesSurvive() {
        val temporary = kotlin.io.path.createTempDirectory("apk-meta-inf").toFile()
        try {
            val root = File(temporary, "template").apply { mkdirs() }
            File(root, "classes.dex").writeBytes(ByteArray(64) { it.toByte() })
            File(root, "META-INF/CERT.RSA").apply { requireNotNull(parentFile).mkdirs(); writeText("obsolete signature") }
            File(root, "META-INF/services/example.Service").apply { requireNotNull(parentFile).mkdirs(); writeText("example.Provider") }
            val output = File(temporary, "output.apk")
            RemoteUnsignedApkWriter.write(root, output) { }
            ZipFile(output).use { zip ->
                assertNull(zip.getEntry("META-INF/CERT.RSA"))
                assertEquals(ZipEntry.DEFLATED, zip.getEntry("classes.dex").method)
                assertEquals("example.Provider", zip.getInputStream(zip.getEntry("META-INF/services/example.Service")).use { String(it.readBytes()) })
            }
        } finally { temporary.deleteRecursively() }
    }

    /** Targeting API 30+ the package manager maps resources.arsc directly: stored, data on a 4-byte boundary. */
    @Test fun resourceTableIsStoredAndFourByteAligned() {
        // Filler names of different lengths move the local header of resources.arsc across every alignment phase.
        for (fillerLength in 1..4) {
            val temporary = kotlin.io.path.createTempDirectory("apk-arsc-alignment").toFile()
            try {
                val root = File(temporary, "template").apply { mkdirs() }
                File(root, "AndroidManifest.xml").writeBytes(ByteArray(77) { it.toByte() })
                File(root, "a".repeat(fillerLength) + ".bin").writeBytes(ByteArray(13) { 7 })
                val table = ByteArray(1001) { (it * 31).toByte() }
                File(root, "resources.arsc").writeBytes(table)
                val output = File(temporary, "output.apk")
                RemoteUnsignedApkWriter.write(root, output) { }
                ZipFile(output).use { zip ->
                    val entry = zip.getEntry("resources.arsc")
                    assertEquals(ZipEntry.STORED, entry.method)
                    assertEquals(table.size.toLong(), entry.size)
                    assertArrayEquals(table, zip.getInputStream(entry).use { it.readBytes() })
                    assertEquals(ZipEntry.DEFLATED, zip.getEntry("AndroidManifest.xml").method)
                }
                val dataOffset = localDataOffset(output.readBytes(), "resources.arsc")
                assertEquals("filler $fillerLength: data offset $dataOffset", 0L, dataOffset % 4)
            } finally { temporary.deleteRecursively() }
        }
    }

    /** Data offset of [name] from the zip's own headers: central directory -> local header -> name and extra lengths. */
    private fun localDataOffset(bytes: ByteArray, name: String): Long {
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var eocd = bytes.size - 22
        while (eocd >= 0 && buffer.getInt(eocd) != 0x06054b50) eocd--
        assertTrue("end of central directory", eocd >= 0)
        val entries = buffer.getShort(eocd + 10).toInt() and 0xffff
        var central = buffer.getInt(eocd + 16)
        repeat(entries) {
            assertEquals(0x02014b50, buffer.getInt(central))
            val nameLength = buffer.getShort(central + 28).toInt() and 0xffff
            val extraLength = buffer.getShort(central + 30).toInt() and 0xffff
            val commentLength = buffer.getShort(central + 32).toInt() and 0xffff
            val entryName = String(bytes, central + 46, nameLength, Charsets.UTF_8)
            if (entryName == name) {
                val local = buffer.getInt(central + 42)
                assertEquals(0x04034b50, buffer.getInt(local))
                val localNameLength = buffer.getShort(local + 26).toInt() and 0xffff
                val localExtraLength = buffer.getShort(local + 28).toInt() and 0xffff
                assertTrue("alignment extra field present", localExtraLength >= 6)
                assertEquals(0xD935.toShort(), buffer.getShort(local + 30 + localNameLength))
                return (local + 30 + localNameLength + localExtraLength).toLong()
            }
            central += 46 + nameLength + extraLength + commentLength
        }
        throw AssertionError("Missing entry " + name)
    }
}
