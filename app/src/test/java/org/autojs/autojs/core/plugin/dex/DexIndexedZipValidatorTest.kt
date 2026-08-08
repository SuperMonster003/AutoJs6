package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DexIndexedZipValidatorTest {

    @Test
    fun derivesTheD8OutputVersionProfileFromMinimumApi() {
        assertEquals(setOf("035"), dexVersionsCompatibleWithMinApi(23))
        assertEquals(setOf("035", "037"), dexVersionsCompatibleWithMinApi(24))
        assertEquals(setOf("035", "037"), dexVersionsCompatibleWithMinApi(25))
        assertEquals(setOf("035", "037", "038"), dexVersionsCompatibleWithMinApi(26))
        assertEquals(setOf("035", "037", "038", "039"), dexVersionsCompatibleWithMinApi(28))
        assertEquals(setOf("035", "037", "038", "039"), dexVersionsCompatibleWithMinApi(29))
        assertEquals(setOf("035", "037", "038", "039", "040"), dexVersionsCompatibleWithMinApi(30))
        assertThrows(IllegalArgumentException::class.java) { dexVersionsCompatibleWithMinApi(0) }
    }

    @Test
    fun acceptsOneOrContiguousMultipleStructurallyValidDexEntries() {
        val one = DexIndexedZipValidator.validate(zipOf("classes.dex" to minimalDex("Lfixture/One;")))
        assertEquals(1, one.dexEntryCount)
        assertEquals(1, one.classDefinitionCount)
        assertEquals(setOf("035"), one.dexVersions)

        val two = DexIndexedZipValidator.validate(
            zipOf(
                "classes.dex" to minimalDex("Lfixture/One;"),
                "classes2.dex" to minimalDex("Lfixture/Two;"),
            ),
        )
        assertEquals(2, two.dexEntryCount)
        assertEquals(2, two.classDefinitionCount)
    }

    @Test
    fun rejectsUnexpectedAndNonContiguousEntryNames() {
        assertFailure(DexOutputValidationFailure.UNEXPECTED_ZIP_ENTRY) {
            DexIndexedZipValidator.validate(zipOf("resource.txt" to minimalDex("Lfixture/One;")))
        }
        assertFailure(DexOutputValidationFailure.NON_CONTIGUOUS_DEX_ENTRIES) {
            DexIndexedZipValidator.validate(
                zipOf(
                    "classes.dex" to minimalDex("Lfixture/One;"),
                    "classes3.dex" to minimalDex("Lfixture/Three;"),
                ),
            )
        }
    }

    @Test
    fun rejectsContainerEntryAndInflatedByteQuotaViolations() {
        val container = zipOf("classes.dex" to minimalDex("Lfixture/One;"))
        assertFailure(DexOutputValidationFailure.CONTAINER_QUOTA) {
            DexIndexedZipValidator.validate(container, DexIndexedZipLimits(maxContainerBytes = 10))
        }
        assertFailure(DexOutputValidationFailure.DEX_QUOTA) {
            DexIndexedZipValidator.validate(
                zipOf(
                    "classes.dex" to minimalDex("Lfixture/One;"),
                    "classes2.dex" to minimalDex("Lfixture/Two;"),
                ),
                DexIndexedZipLimits(maxTotalDexBytes = 240),
            )
        }
        assertFailure(DexOutputValidationFailure.DEX_QUOTA) {
            DexIndexedZipValidator.validate(
                zipOf(
                    "classes.dex" to minimalDex("Lfixture/One;"),
                    "classes2.dex" to minimalDex("Lfixture/Two;"),
                ),
                DexIndexedZipLimits(maxDexEntries = 1),
            )
        }
    }

    @Test
    fun acceptsDeflatedDataDescriptorsAndPreflightsCompressionRatio() {
        val container = deflatedZipOf("classes.dex" to minimalDex("Lfixture/Compressed;"))

        assertEquals(1, DexIndexedZipValidator.validate(container).dexEntryCount)
        assertFailure(DexOutputValidationFailure.DEX_QUOTA) {
            DexIndexedZipValidator.validate(container, DexIndexedZipLimits(maxCompressionRatio = 1))
        }
    }

    @Test
    fun rejectsInvalidMagicAndUnnegotiatedVersion() {
        val badMagic = minimalDex("Lfixture/One;").apply {
            this[0] = 'D'.code.toByte()
        }
        assertFailure(DexOutputValidationFailure.INVALID_DEX_MAGIC) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to badMagic))
        }

        val unsupportedVersion = minimalDex("Lfixture/One;").apply {
            this[4] = '0'.code.toByte()
            this[5] = '4'.code.toByte()
            this[6] = '2'.code.toByte()
            resealDex(this)
        }
        assertFailure(DexOutputValidationFailure.UNSUPPORTED_DEX_VERSION) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to unsupportedVersion))
        }
    }

    @Test
    fun rejectsFileSizeHeaderSizeAndEndianMismatches() {
        listOf(
            32 to 200L,
            36 to 0x74L,
            40 to 0x78563412L,
        ).forEach { (offset, value) ->
            val dex = minimalDex("Lfixture/One;").apply {
                putU32(offset, value)
                resealDex(this)
            }
            assertFailure(DexOutputValidationFailure.INVALID_DEX_HEADER) {
                DexIndexedZipValidator.validate(zipOf("classes.dex" to dex))
            }
        }
    }

    @Test
    fun independentlyChecksAdler32AndSha1Integrity() {
        val badChecksum = minimalDex("Lfixture/One;").apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        assertFailure(DexOutputValidationFailure.INVALID_DEX_CHECKSUM) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to badChecksum))
        }

        val badSignature = minimalDex("Lfixture/One;").apply {
            this[12] = (this[12].toInt() xor 1).toByte()
            writeAdler32(this)
        }
        assertFailure(DexOutputValidationFailure.INVALID_DEX_SIGNATURE) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to badSignature))
        }
    }

    @Test
    fun rejectsMisalignedSectionsAndIncompleteMap() {
        val misalignedStrings = minimalDex("Lfixture/One;").apply {
            putU32(60, 113)
            resealDex(this)
        }
        assertFailure(DexOutputValidationFailure.INVALID_DEX_SECTION) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to misalignedStrings))
        }

        val missingStringIdMap = minimalDex("Lfixture/One;").apply {
            putU16(MAP_OFFSET + 4 + MAP_ITEM_BYTES, 0x0003)
            resealDex(this)
        }
        assertFailure(DexOutputValidationFailure.INVALID_DEX_MAP) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to missingStringIdMap))
        }
    }

    @Test
    fun rejectsOutOfRangeMappedDescriptorAndMalformedDescriptor() {
        val outOfRangeString = minimalDex("Lfixture/One;").apply {
            putU32(STRING_IDS_OFFSET, size.toLong())
            resealDex(this)
        }
        assertFailure(DexOutputValidationFailure.INVALID_DEX_SECTION) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to outOfRangeString))
        }

        assertFailure(DexOutputValidationFailure.INVALID_CLASS_DESCRIPTOR) {
            DexIndexedZipValidator.validate(zipOf("classes.dex" to minimalDex("not/a/class")))
        }
    }

    @Test
    fun rejectsDuplicateClassDefinitionsAcrossDexEntries() {
        assertFailure(DexOutputValidationFailure.DUPLICATE_CLASS_DEFINITION) {
            DexIndexedZipValidator.validate(
                zipOf(
                    "classes.dex" to minimalDex("Lfixture/Duplicate;"),
                    "classes2.dex" to minimalDex("Lfixture/Duplicate;"),
                ),
            )
        }
    }

    @Test
    fun malformedZipFramingAndCrcFailClosed() {
        val valid = zipOf("classes.dex" to minimalDex("Lfixture/One;"))
        assertFailure(DexOutputValidationFailure.MALFORMED_ZIP) {
            DexIndexedZipValidator.validate(valid + byteArrayOf(0))
        }

        val damagedPayload = valid.copyOf().apply {
            val firstDexByte = 30 + "classes.dex".length
            this[firstDexByte] = (this[firstDexByte].toInt() xor 1).toByte()
        }
        assertFailure(DexOutputValidationFailure.MALFORMED_ZIP) {
            DexIndexedZipValidator.validate(damagedPayload)
        }
    }

    private fun assertFailure(expected: DexOutputValidationFailure, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected DexOutputValidationException but got $error", error is DexOutputValidationException)
        assertEquals(expected, (error as DexOutputValidationException).failure)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                val crc = CRC32().apply { update(bytes) }.value
                zip.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc
                })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun deflatedZipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun minimalDex(descriptor: String): ByteArray {
        val descriptorBytes = descriptor.toByteArray(Charsets.UTF_8)
        require(descriptor.length < 0x80 && descriptorBytes.size == descriptor.length)
        val stringDataOffset = MAP_OFFSET + 4 + MAP_ENTRY_COUNT * MAP_ITEM_BYTES
        val fileSize = stringDataOffset + 1 + descriptorBytes.size + 1
        val dex = ByteArray(fileSize)
        "dex\n035\u0000".toByteArray(Charsets.US_ASCII).copyInto(dex, 0)
        dex.putU32(32, fileSize.toLong())
        dex.putU32(36, 0x70)
        dex.putU32(40, 0x12345678)
        dex.putU32(52, MAP_OFFSET.toLong())
        dex.putU32(56, 1)
        dex.putU32(60, STRING_IDS_OFFSET.toLong())
        dex.putU32(64, 1)
        dex.putU32(68, TYPE_IDS_OFFSET.toLong())
        dex.putU32(96, 1)
        dex.putU32(100, CLASS_DEFS_OFFSET.toLong())
        dex.putU32(104, (fileSize - MAP_OFFSET).toLong())
        dex.putU32(108, MAP_OFFSET.toLong())

        dex.putU32(STRING_IDS_OFFSET, stringDataOffset.toLong())
        dex.putU32(TYPE_IDS_OFFSET, 0)
        dex.putU32(CLASS_DEFS_OFFSET, 0)
        dex.putU32(CLASS_DEFS_OFFSET + 4, 1)
        dex.putU32(CLASS_DEFS_OFFSET + 8, 0xffffffffL)
        dex.putU32(CLASS_DEFS_OFFSET + 16, 0xffffffffL)

        dex.putU32(MAP_OFFSET, MAP_ENTRY_COUNT.toLong())
        var mapCursor = MAP_OFFSET + 4
        listOf(
            Triple(0x0000, 1, 0),
            Triple(0x0001, 1, STRING_IDS_OFFSET),
            Triple(0x0002, 1, TYPE_IDS_OFFSET),
            Triple(0x0006, 1, CLASS_DEFS_OFFSET),
            Triple(0x1000, 1, MAP_OFFSET),
            Triple(0x2002, 1, stringDataOffset),
        ).forEach { (type, count, offset) ->
            dex.putU16(mapCursor, type)
            dex.putU16(mapCursor + 2, 0)
            dex.putU32(mapCursor + 4, count.toLong())
            dex.putU32(mapCursor + 8, offset.toLong())
            mapCursor += MAP_ITEM_BYTES
        }
        dex[stringDataOffset] = descriptor.length.toByte()
        descriptorBytes.copyInto(dex, stringDataOffset + 1)
        dex[fileSize - 1] = 0
        resealDex(dex)
        return dex
    }

    private fun resealDex(dex: ByteArray) {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(dex.copyOfRange(32, dex.size))
        sha1.copyInto(dex, 12)
        writeAdler32(dex)
    }

    private fun writeAdler32(dex: ByteArray) {
        val checksum = Adler32().apply { update(dex, 12, dex.size - 12) }.value
        dex.putU32(8, checksum)
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val STRING_IDS_OFFSET = 112
        const val TYPE_IDS_OFFSET = 116
        const val CLASS_DEFS_OFFSET = 120
        const val MAP_OFFSET = 152
        const val MAP_ENTRY_COUNT = 6
        const val MAP_ITEM_BYTES = 12
    }
}
