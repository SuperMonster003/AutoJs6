package org.autojs.plugin.packagearchive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkSignatureDetectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun signingBlockIdsAreReadWithoutApksig() {
        val apk = temporaryFolder.newFile("v2-v3.apk")
        val block = signingBlock(0x7109871A, 0xF05368C0.toInt())
        FileOutputStream(apk).use { output ->
            output.write(block)
            output.write(emptyZipEocd(block.size))
        }

        assertEquals(
            setOf(0x7109871A, 0xF05368C0.toInt()),
            ApkSignatureDetector.readSigningBlockIds(apk),
        )
        assertEquals("V2 + V3", ApkSignatureDetector.detectSchemes(apk))
    }

    @Test
    fun v1DetectorAcceptsEcSignatureBlocks() {
        val apk = temporaryFolder.newFile("v1-ec.apk")
        ZipOutputStream(FileOutputStream(apk)).use { zip ->
            listOf(
                "META-INF/MANIFEST.MF",
                "META-INF/CERT.SF",
                "META-INF/CERT.EC",
            ).forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }

        assertTrue(ApkSignatureDetector.hasV1Signature(apk))
        assertEquals("V1", ApkSignatureDetector.detectSchemes(apk))
    }

    @Test
    fun unsignedZipHasNoDetectedScheme() {
        val apk = temporaryFolder.newFile("unsigned.apk")
        ZipOutputStream(FileOutputStream(apk)).use { Unit }

        assertFalse(ApkSignatureDetector.hasV1Signature(apk))
        assertTrue(ApkSignatureDetector.readSigningBlockIds(apk).isEmpty())
        assertEquals(null, ApkSignatureDetector.detectSchemes(apk))
    }

    private fun signingBlock(vararg ids: Int): ByteArray {
        val pairs = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                ids.forEach { id ->
                    output.writeLittleEndianLong(4)
                    output.writeLittleEndianInt(id)
                }
            }
        }.toByteArray()
        val size = pairs.size.toLong() + 24
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeLittleEndianLong(size)
                output.write(pairs)
                output.writeLittleEndianLong(size)
                output.write("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
            }
        }.toByteArray()
    }

    private fun emptyZipEocd(centralDirectoryOffset: Int): ByteArray {
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(byteArrayOf(0x50, 0x4B, 0x05, 0x06))
                repeat(4) { output.writeLittleEndianShort(0) }
                output.writeLittleEndianInt(0)
                output.writeLittleEndianInt(centralDirectoryOffset)
                output.writeLittleEndianShort(0)
            }
        }.toByteArray()
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        repeat(4) { shift -> writeByte((value ushr (shift * 8)) and 0xFF) }
    }

    private fun DataOutputStream.writeLittleEndianLong(value: Long) {
        repeat(8) { shift -> writeByte(((value ushr (shift * 8)) and 0xFF).toInt()) }
    }
}
