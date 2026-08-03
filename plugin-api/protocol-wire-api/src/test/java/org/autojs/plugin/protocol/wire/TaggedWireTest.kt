package org.autojs.plugin.protocol.wire

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TaggedWireTest {

    @Test
    fun roundTripsTypedAndRepeatedFields() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 2)
            .string(8, "second")
            .int32(1, 24, requiredForReader = true)
            .int64(2, 9_000_000_000L)
            .boolean(3, true)
            .float64(4, 0.75)
            .strings(8, listOf("first", "third"))
            .bytes(9, byteArrayOf(1, 2, 3))
            .fileDescriptorRef(10, 2)
            .encode()

        val document = TaggedWireDocument.decode(encoded)
            .requireSchema(SCHEMA, 1)
            .rejectUnknownRequiredFields(setOf(1, 2, 3, 4, 8, 9, 10))
            .validateKnownCardinality(
                knownTags = setOf(1, 2, 3, 4, 8, 9, 10),
                repeatedTags = setOf(8),
            )

        assertEquals(2, document.schemaMinor)
        assertEquals(24, document.requireInt32(1))
        assertEquals(9_000_000_000L, document.requireInt64(2))
        assertTrue(document.requireBoolean(3))
        assertEquals(0.75, document.requireFloat64(4), 0.0)
        assertEquals(listOf("second", "first", "third"), document.strings(8))
        assertArrayEquals(byteArrayOf(1, 2, 3), document.requireBytes(9))
        assertEquals(2, document.requireFileDescriptorRef(10))
    }

    @Test
    fun encodingIsDeterministicByTagAndInsertionOrder() {
        val first = TaggedWireWriter(SCHEMA, 1, 0)
            .string(4, "a")
            .int32(1, 1)
            .string(4, "b")
            .encode()
        val second = TaggedWireWriter(SCHEMA, 1, 0)
            .int32(1, 1)
            .string(4, "a")
            .string(4, "b")
            .encode()

        assertArrayEquals(first, second)
    }

    @Test
    fun ignoresUnknownOptionalFieldButRejectsUnknownRequiredField() {
        val optional = TaggedWireWriter(SCHEMA, 1, 1)
            .int32(1, 7)
            .string(99, "future")
            .encode()
        TaggedWireDocument.decode(optional).rejectUnknownRequiredFields(setOf(1))

        val required = TaggedWireWriter(SCHEMA, 1, 1)
            .int32(1, 7)
            .string(99, "future", requiredForReader = true)
            .encode()
        val error = assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(required).rejectUnknownRequiredFields(setOf(1))
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, error.error)
    }

    @Test
    fun rejectsDuplicateSingularField() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 0)
            .int32(1, 1)
            .int32(1, 2)
            .encode()

        val error = assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(encoded).requireInt32(1)
        }
        assertEquals(TaggedWireError.DUPLICATE_FIELD, error.error)
    }

    @Test
    fun rejectsTruncatedAndTrailingDocuments() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 0).int32(1, 1).encode()
        val truncated = encoded.copyOf(encoded.size - 1).also { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(8, bytes.size)
        }
        assertEquals(
            TaggedWireError.TRUNCATED,
            assertThrows(TaggedWireException::class.java) {
                TaggedWireDocument.decode(truncated)
            }.error,
        )

        val trailing = encoded.copyOf(encoded.size + 1).also { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(8, bytes.size)
        }
        assertEquals(
            TaggedWireError.INVALID_FIELD,
            assertThrows(TaggedWireException::class.java) {
                TaggedWireDocument.decode(trailing)
            }.error,
        )
    }

    @Test
    fun rejectsInvalidUtf8DuringStructuralDecode() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 0)
            .bytes(1, byteArrayOf(0xC3.toByte(), 0x28))
            .encode()
            .also { bytes ->
                ByteBuffer.wrap(bytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(TaggedWireProtocol.HEADER_SIZE_BYTES + Int.SIZE_BYTES, TaggedWireProtocol.TYPE_UTF8)
            }

        val error = assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(encoded)
        }
        assertEquals(TaggedWireError.INVALID_UTF8, error.error)
    }

    @Test
    fun rejectsInvalidUtf8InUnknownOptionalFieldDuringStructuralDecode() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 1)
            .bytes(99, byteArrayOf(0xC3.toByte(), 0x28))
            .encode()
            .also { bytes ->
                ByteBuffer.wrap(bytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(TaggedWireProtocol.HEADER_SIZE_BYTES + Int.SIZE_BYTES, TaggedWireProtocol.TYPE_UTF8)
            }

        val error = assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(encoded)
        }
        assertEquals(TaggedWireError.INVALID_UTF8, error.error)
    }

    @Test
    fun enforcesDocumentAndFieldLimitsBeforeAllocation() {
        val limits = TaggedWireLimits(maxDocumentBytes = 64, maxFieldBytes = 8, maxFields = 1)
        assertEquals(
            TaggedWireError.SIZE_LIMIT,
            assertThrows(TaggedWireException::class.java) {
                TaggedWireWriter(SCHEMA, 1, 0, limits).string(1, "123456789")
            }.error,
        )
        assertEquals(
            TaggedWireError.FIELD_LIMIT,
            assertThrows(TaggedWireException::class.java) {
                TaggedWireWriter(SCHEMA, 1, 0, limits)
                    .int32(1, 1)
                    .int32(2, 2)
            }.error,
        )
    }

    @Test
    fun rejectsNonFiniteFloatingPointValues() {
        assertEquals(
            TaggedWireError.INVALID_FIELD,
            assertThrows(TaggedWireException::class.java) {
                TaggedWireWriter(SCHEMA, 1, 0).float64(1, Double.NaN)
            }.error,
        )
    }

    @Test
    fun rejectsUnpairedUtf16SurrogateAtEncodingBoundary() {
        assertEquals(
            TaggedWireError.INVALID_UTF8,
            assertThrows(TaggedWireException::class.java) {
                TaggedWireWriter(SCHEMA, 1, 0).string(1, "\uD800")
            }.error,
        )
    }

    @Test
    fun rejectsNonCanonicalFieldOrder() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 0)
            .int32(1, 1)
            .int32(2, 2)
            .encode()
        val firstFieldOffset = TaggedWireProtocol.HEADER_SIZE_BYTES
        val fieldSize = TaggedWireProtocol.FIELD_HEADER_SIZE_BYTES + Int.SIZE_BYTES
        val reordered = encoded.copyOf()
        encoded.copyInto(
            destination = reordered,
            destinationOffset = firstFieldOffset,
            startIndex = firstFieldOffset + fieldSize,
            endIndex = firstFieldOffset + fieldSize * 2,
        )
        encoded.copyInto(
            destination = reordered,
            destinationOffset = firstFieldOffset + fieldSize,
            startIndex = firstFieldOffset,
            endIndex = firstFieldOffset + fieldSize,
        )

        assertEquals(
            TaggedWireError.INVALID_FIELD,
            assertThrows(TaggedWireException::class.java) {
                TaggedWireDocument.decode(reordered)
            }.error,
        )
    }

    @Test
    fun rejectsNonPositiveWireTypeEvenWhenTheFieldIsOptionalAndUnknown() {
        listOf(0, -1, Int.MIN_VALUE).forEach { invalidType ->
            val encoded = TaggedWireWriter(SCHEMA, 1, 1)
                .bytes(99, byteArrayOf(1))
                .encode()
                .also { bytes ->
                    ByteBuffer.wrap(bytes)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(TaggedWireProtocol.HEADER_SIZE_BYTES + Int.SIZE_BYTES, invalidType)
                }

            val error = assertThrows(TaggedWireException::class.java) {
                TaggedWireDocument.decode(encoded)
            }
            assertEquals(TaggedWireError.INVALID_FIELD, error.error)
        }
    }

    @Test
    fun skipsPositiveUnknownWireTypeOnlyWhenItsFieldIsOptionalAndUnknown() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 1)
            .int32(1, 7)
            .bytes(99, byteArrayOf(1, 2, 3))
            .encode()
            .also { bytes ->
                val secondFieldTypeOffset = TaggedWireProtocol.HEADER_SIZE_BYTES +
                    TaggedWireProtocol.FIELD_HEADER_SIZE_BYTES + Int.SIZE_BYTES +
                    Int.SIZE_BYTES
                ByteBuffer.wrap(bytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(secondFieldTypeOffset, 99)
            }

        val document = TaggedWireDocument.decode(encoded)
            .rejectUnknownRequiredFields(setOf(1))
        assertEquals(7, document.requireInt32(1))
    }

    @Test
    fun impossibleFieldCountIsRejectedBeforeAllocatingTheFieldList() {
        val encoded = TaggedWireWriter(SCHEMA, 1, 0).encode().also { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(24, Int.MAX_VALUE)
        }

        val error = assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(
                encoded,
                TaggedWireLimits(maxFields = Int.MAX_VALUE),
            )
        }
        assertEquals(TaggedWireError.TRUNCATED, error.error)
    }

    private companion object {
        const val SCHEMA = 100
    }
}
