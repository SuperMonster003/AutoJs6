package org.autojs.plugin.packagearchive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkManifestDisplayDecoderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test fun binarySharedUidUsesVerifiedResourceIdentityAndTypedLiteral() {
        for (name in listOf("sharedUserId", "")) {
            val summary = ManifestSummaryParser.parse(ApkManifestDisplayDecoder.decodeManifest(sharedUidManifest(name = name)))
            assertEquals("com.example", summary.packageName)
            assertEquals("android.uid.system", summary.sharedUserId)
        }
    }

    @Test fun binarySharedUidAmbiguityCannotBecomeAnAbsentSummaryField() {
        for (manifest in listOf(sharedUidManifest(name = "pretendOtherField"), sharedUidManifest(resourceId = 0),
            sharedUidManifest(namespace = "urn:foreign"), sharedUidManifest(duplicate = true),
            sharedUidManifest(rawMismatch = true), sharedUidManifest(type = TYPE_REFERENCE))) {
            assertFailsWithIOException { ManifestSummaryParser.parse(ApkManifestDisplayDecoder.decodeManifest(manifest)) }
        }
    }

    private fun sharedUidManifest(name: String = "sharedUserId", namespace: String = ManifestRootAttributes.ANDROID_NAMESPACE,
        resourceId: Int = ManifestRootAttributes.SHARED_USER_ID_RESOURCE, duplicate: Boolean = false,
        rawMismatch: Boolean = false, type: Int = TYPE_STRING): ByteArray {
        val strings = listOf("android", namespace, "manifest", "package", "com.example", name, "android.uid.system", "other.uid")
        val resources = LittleEndianWriter().apply {
            u16(0x0180); u16(8); i32(8 + strings.size * 4)
            strings.indices.forEach { i32(if (it == 5) resourceId else 0) }
        }.toByteArray()
        val shared = Attribute(namespace = 1, name = 5, rawValue = if (rawMismatch) 7 else 6, type = type, data = 6)
        return wrapChunks(listOf(stringPool(strings), resources, namespaceChunk(true, 0, 1),
            startElement(name = 2, attributes = listOf(Attribute(NO_INDEX, 3, 4, TYPE_STRING, 4)) +
                List(if (duplicate) 2 else 1) { shared }), endElement(2), namespaceChunk(false, 0, 1)))
    }

    @Test
    fun binaryManifestIsFullyDecodedAndPrettyPrinted() {
        val decoded = ApkManifestDisplayDecoder.decodeManifest(binaryManifest())

        assertTrue(decoded.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"))
        assertTrue(decoded.contains("xmlns:android=\"http://schemas.android.com/apk/res/android\""))
        assertTrue(decoded.contains("package=\"com.example\""))
        assertTrue(decoded.contains("<uses-sdk android:minSdkVersion=\"24\" />"))
        assertTrue(decoded.contains("android:debuggable=\"true\""))
        assertTrue(decoded.contains("android:label=\"Demo &amp; Co\""))
        assertTrue(decoded.contains("android:icon=\"@0x7f080001\""))
        assertTrue(decoded.contains("\n  <uses-sdk"))
        assertTrue(decoded.endsWith("</manifest>"))
        assertFalse(decoded.contains("PackageManager-derived summary"))
    }

    @Test
    fun apkEntryIsDecodedWithoutExtractingOrWritingIt() {
        val apk = temporaryFolder.newFile("sample.apk")
        ZipOutputStream(FileOutputStream(apk)).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(binaryManifest())
            zip.closeEntry()
        }

        val decoded = ApkManifestDisplayDecoder.decode(apk)

        assertTrue(decoded.contains("<manifest"))
        assertTrue(decoded.contains("<application"))
    }

    @Test
    fun oneLineTextManifestIsFormattedWithoutSplittingQuotedMarkup() {
        val xml = """<?xml version="1.0"?><manifest package="a&gt;&lt;b"><application /></manifest>"""

        val decoded = ApkManifestDisplayDecoder.decodeManifest(xml.toByteArray())

        assertTrue(decoded.contains("\n<manifest package=\"a&gt;&lt;b\">\n"))
        assertTrue(decoded.contains("\n  <application />\n"))
        assertTrue(decoded.endsWith("</manifest>"))
    }

    @Test
    fun malformedRootLengthIsRejected() {
        val malformed = binaryManifest().clone()
        malformed.writeI32(4, Int.MAX_VALUE)

        assertFailsWithIOException {
            ApkManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    @Test
    fun outOfBoundsStringOffsetIsRejected() {
        val malformed = binaryManifest().clone()
        val stringPoolStart = 8
        val firstStringOffset = stringPoolStart + 28
        malformed.writeI32(firstStringOffset, Int.MAX_VALUE)

        assertFailsWithIOException {
            ApkManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    @Test
    fun maliciousAttributeCountIsRejectedBeforeAllocation() {
        val malformed = binaryManifest().clone()
        val firstStartElement = malformed.findChunk(0x0102)
        malformed.writeU16(firstStartElement + 28, 0xFFFF)

        assertFailsWithIOException {
            ApkManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    @Test
    fun attributeArrayCannotOverlapItsExtension() {
        val malformed = binaryManifest().clone()
        val firstStartElement = malformed.findChunk(0x0102)
        malformed.writeU16(firstStartElement + 24, 0)

        assertFailsWithIOException {
            ApkManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    @Test
    fun reusedOverlongAttributeNameIsRejectedBeforeRendering() {
        val malformed = binaryManifest(
            attributeName = "x".repeat(1_025),
            repeatedAttributeCount = 4_096,
        )

        assertFailsWithIOException {
            ApkManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    @Test
    fun overlongNamespaceUriIsRejectedByTheSingleStringBudget() {
        val malformed = binaryManifest(
            namespaceUri = "u".repeat(16 * 1_024 + 1),
        )

        assertFailsWithIOException {
            ApkManifestDisplayDecoder.decodeManifest(malformed)
        }
    }

    @Test
    fun shadowedPrefixIsNotReusedForTheOuterNamespace() {
        val decoded = ApkManifestDisplayDecoder.decodeManifest(shadowedNamespaceManifest())
        val child = decoded.lineSequence().first { it.contains("<child") }

        assertTrue(child.contains("xmlns:p=\"urn:new\""))
        assertTrue(child.contains("xmlns:ns=\"urn:old\""))
        assertTrue(child.contains("ns:old=\"true\""))
        assertTrue(child.contains("p:new=\"true\""))
        assertFalse(child.contains("p:old="))
    }

    @Test
    fun duplicatePendingPrefixIsRejected() {
        val chunks = listOf(
            stringPool(listOf("p", "urn:first", "urn:second", "manifest")),
            namespaceChunk(start = true, prefix = 0, uri = 1),
            namespaceChunk(start = true, prefix = 0, uri = 2),
            startElement(name = 3, attributes = emptyList()),
            endElement(name = 3),
        )

        assertFailsWithIOException {
            ApkManifestDisplayDecoder.decodeManifest(wrapChunks(chunks))
        }
    }

    @Test
    fun allDisplayBudgetsAreEnforced() {
        val manifest = binaryManifest()
        val generous = ApkManifestDisplayDecoder.Limits()

        listOf(
            generous.copy(maxInputBytes = manifest.size - 1),
            generous.copy(maxNodes = 2),
            generous.copy(maxStrings = 3),
            generous.copy(maxDecodedStringChars = 8),
            generous.copy(maxSingleStringChars = 4),
            generous.copy(maxXmlNameChars = 4),
            generous.copy(maxDepth = 1),
            generous.copy(maxNamespaces = 0),
            generous.copy(maxAttributesPerElement = 1),
            generous.copy(maxOutputChars = 64),
        ).forEach { limits ->
            assertFailsWithIOException {
                ApkManifestDisplayDecoder.decodeManifest(manifest, limits)
            }
        }
    }

    private fun binaryManifest(
        namespaceUri: String = "http://schemas.android.com/apk/res/android",
        attributeName: String = "debuggable",
        repeatedAttributeCount: Int = 0,
    ): ByteArray {
        val strings = listOf(
            "android",
            namespaceUri,
            "manifest",
            "package",
            "com.example",
            "uses-sdk",
            "minSdkVersion",
            "application",
            attributeName,
            "label",
            "Demo & Co",
            "icon",
        )
        val chunks = listOf(
            stringPool(strings),
            namespaceChunk(start = true, prefix = 0, uri = 1),
            startElement(
                name = 2,
                attributes = listOf(
                    Attribute(namespace = NO_INDEX, name = 3, rawValue = 4, type = TYPE_STRING, data = 4),
                ),
            ),
            startElement(
                name = 5,
                attributes = listOf(
                    Attribute(namespace = 1, name = 6, rawValue = NO_INDEX, type = TYPE_INT_DEC, data = 24),
                ),
            ),
            endElement(name = 5),
            startElement(
                name = 7,
                attributes = if (repeatedAttributeCount > 0) {
                    List(repeatedAttributeCount) {
                        Attribute(namespace = 1, name = 8, rawValue = NO_INDEX, type = TYPE_INT_BOOLEAN, data = 1)
                    }
                } else {
                    listOf(
                    Attribute(namespace = 1, name = 8, rawValue = NO_INDEX, type = TYPE_INT_BOOLEAN, data = 1),
                    Attribute(namespace = 1, name = 9, rawValue = 10, type = TYPE_STRING, data = 10),
                    Attribute(namespace = 1, name = 11, rawValue = NO_INDEX, type = TYPE_REFERENCE, data = 0x7F080001),
                    )
                },
            ),
            endElement(name = 7),
            endElement(name = 2),
            namespaceChunk(start = false, prefix = 0, uri = 1),
        )
        return wrapChunks(chunks)
    }

    private fun shadowedNamespaceManifest(): ByteArray {
        val chunks = listOf(
            stringPool(listOf("p", "urn:old", "urn:new", "manifest", "child", "old", "new")),
            namespaceChunk(start = true, prefix = 0, uri = 1),
            startElement(name = 3, attributes = emptyList()),
            namespaceChunk(start = true, prefix = 0, uri = 2),
            startElement(
                name = 4,
                attributes = listOf(
                    Attribute(namespace = 1, name = 5, rawValue = NO_INDEX, type = TYPE_INT_BOOLEAN, data = 1),
                    Attribute(namespace = 2, name = 6, rawValue = NO_INDEX, type = TYPE_INT_BOOLEAN, data = 1),
                ),
            ),
            endElement(name = 4),
            namespaceChunk(start = false, prefix = 0, uri = 2),
            endElement(name = 3),
            namespaceChunk(start = false, prefix = 0, uri = 1),
        )
        return wrapChunks(chunks)
    }

    private fun wrapChunks(chunks: List<ByteArray>): ByteArray {
        return LittleEndianWriter().apply {
            u16(0x0003)
            u16(8)
            i32(8 + chunks.sumOf { it.size })
            chunks.forEach(::bytes)
        }.toByteArray()
    }

    private fun stringPool(strings: List<String>): ByteArray {
        val encodedStrings = strings.map { value ->
            val utf8 = value.toByteArray(Charsets.UTF_8)
            LittleEndianWriter().apply {
                length8(value.length)
                length8(utf8.size)
                bytes(utf8)
                u8(0)
            }.toByteArray()
        }
        val offsets = encodedStrings.runningFold(0) { offset, value -> offset + value.size }.dropLast(1)
        val stringsStart = 28 + strings.size * 4
        val size = stringsStart + encodedStrings.sumOf { it.size }
        return LittleEndianWriter().apply {
            u16(0x0001)
            u16(28)
            i32(size)
            i32(strings.size)
            i32(0)
            i32(0x00000100)
            i32(stringsStart)
            i32(0)
            offsets.forEach(::i32)
            encodedStrings.forEach(::bytes)
        }.toByteArray()
    }

    private fun namespaceChunk(start: Boolean, prefix: Int, uri: Int): ByteArray {
        return LittleEndianWriter().apply {
            u16(if (start) 0x0100 else 0x0101)
            u16(16)
            i32(24)
            i32(1)
            i32(NO_INDEX)
            i32(prefix)
            i32(uri)
        }.toByteArray()
    }

    private fun startElement(
        name: Int,
        namespace: Int = NO_INDEX,
        attributes: List<Attribute>,
    ): ByteArray {
        val size = 36 + attributes.size * 20
        return LittleEndianWriter().apply {
            u16(0x0102)
            u16(16)
            i32(size)
            i32(1)
            i32(NO_INDEX)
            i32(namespace)
            i32(name)
            u16(20)
            u16(20)
            u16(attributes.size)
            u16(0)
            u16(0)
            u16(0)
            attributes.forEach { attribute ->
                i32(attribute.namespace)
                i32(attribute.name)
                i32(attribute.rawValue)
                u16(8)
                u8(0)
                u8(attribute.type)
                i32(attribute.data)
            }
        }.toByteArray()
    }

    private fun endElement(name: Int, namespace: Int = NO_INDEX): ByteArray {
        return LittleEndianWriter().apply {
            u16(0x0103)
            u16(16)
            i32(24)
            i32(1)
            i32(NO_INDEX)
            i32(namespace)
            i32(name)
        }.toByteArray()
    }

    private fun ByteArray.findChunk(type: Int): Int {
        var offset = 8
        while (offset <= size - 8) {
            val chunkType = readU16(offset)
            val chunkSize = readI32(offset + 4)
            if (chunkType == type) return offset
            if (chunkSize < 8) break
            offset += chunkSize
        }
        error("Chunk 0x${type.toString(16)} not found")
    }

    private fun ByteArray.readU16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readI32(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.writeU16(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.writeI32(offset: Int, value: Int) {
        repeat(4) { byteIndex ->
            this[offset + byteIndex] = (value ushr (byteIndex * 8)).toByte()
        }
    }

    private fun assertFailsWithIOException(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (_: IOException) {
            // Expected.
        }
    }

    private data class Attribute(
        val namespace: Int,
        val name: Int,
        val rawValue: Int,
        val type: Int,
        val data: Int,
    )

    private class LittleEndianWriter {

        private val output = ByteArrayOutputStream()

        fun u8(value: Int) {
            output.write(value and 0xFF)
        }

        fun u16(value: Int) {
            u8(value)
            u8(value ushr 8)
        }

        fun i32(value: Int) {
            repeat(4) { byteIndex -> u8(value ushr (byteIndex * 8)) }
        }

        fun length8(value: Int) {
            if (value <= 0x7F) {
                u8(value)
            } else {
                u8((value ushr 8) or 0x80)
                u8(value)
            }
        }

        fun bytes(value: ByteArray) {
            output.write(value)
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private companion object {
        const val NO_INDEX = -1
        const val TYPE_REFERENCE = 0x01
        const val TYPE_STRING = 0x03
        const val TYPE_INT_DEC = 0x10
        const val TYPE_INT_BOOLEAN = 0x12
    }
}
