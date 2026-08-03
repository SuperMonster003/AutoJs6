package org.autojs.plugin.protocol.wire

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Small deterministic field-tagged envelope used by cross-process plugin APIs.
 *
 * The Binder surface transports the encoded document as a byte array. This
 * keeps optional field evolution explicit and avoids positional Parcelable
 * compatibility traps. Large content is always transported by a separately
 * declared ParcelFileDescriptor.
 */
object TaggedWireProtocol {
    const val MAGIC: Int = 0x414A3657 // AJ6W
    const val FORMAT_VERSION: Int = 1
    const val HEADER_SIZE_BYTES: Int = 28
    const val FIELD_HEADER_SIZE_BYTES: Int = 16

    const val TYPE_INT32: Int = 1
    const val TYPE_INT64: Int = 2
    const val TYPE_BOOLEAN: Int = 3
    const val TYPE_UTF8: Int = 4
    const val TYPE_BYTES: Int = 5
    const val TYPE_DOCUMENT: Int = 6
    const val TYPE_FD_REF: Int = 7
    const val TYPE_FLOAT64: Int = 8

    /** An older reader must reject an unknown field carrying this flag. */
    const val FLAG_REQUIRED_FOR_READER: Int = 1

    val DEFAULT_LIMITS = TaggedWireLimits()
}

data class TaggedWireLimits(
    val maxDocumentBytes: Int = 256 * 1024,
    val maxFieldBytes: Int = 256 * 1024,
    val maxFields: Int = 1_024,
) {
    init {
        require(maxDocumentBytes >= TaggedWireProtocol.HEADER_SIZE_BYTES)
        require(maxFieldBytes >= 0)
        require(maxFields >= 0)
    }
}

enum class TaggedWireError {
    MALFORMED_HEADER,
    UNSUPPORTED_FORMAT,
    SIZE_LIMIT,
    FIELD_LIMIT,
    TRUNCATED,
    INVALID_FIELD,
    TYPE_MISMATCH,
    DUPLICATE_FIELD,
    MISSING_FIELD,
    SCHEMA_MISMATCH,
    UNKNOWN_REQUIRED_FIELD,
    INVALID_UTF8,
}

class TaggedWireException(
    val error: TaggedWireError,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class TaggedWireWriter(
    private val schemaId: Int,
    private val schemaMajor: Int,
    private val schemaMinor: Int,
    private val limits: TaggedWireLimits = TaggedWireProtocol.DEFAULT_LIMITS,
) {
    private val fields = mutableListOf<TaggedWireField>()
    private var insertionIndex = 0

    init {
        require(schemaId > 0) { "schemaId must be positive" }
        require(schemaMajor > 0) { "schemaMajor must be positive" }
        require(schemaMinor >= 0) { "schemaMinor must not be negative" }
    }

    fun int32(tag: Int, value: Int, requiredForReader: Boolean = false) = apply {
        add(tag, TaggedWireProtocol.TYPE_INT32, requiredForReader, ByteBuffer.allocate(Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(value)
            .array())
    }

    fun int64(tag: Int, value: Long, requiredForReader: Boolean = false) = apply {
        add(tag, TaggedWireProtocol.TYPE_INT64, requiredForReader, ByteBuffer.allocate(Long.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(value)
            .array())
    }

    fun boolean(tag: Int, value: Boolean, requiredForReader: Boolean = false) = apply {
        add(
            tag,
            TaggedWireProtocol.TYPE_BOOLEAN,
            requiredForReader,
            byteArrayOf(if (value) 1 else 0),
        )
    }

    fun float64(tag: Int, value: Double, requiredForReader: Boolean = false) = apply {
        if (!value.isFinite()) {
            throw TaggedWireException(TaggedWireError.INVALID_FIELD, "Floating-point value must be finite")
        }
        add(tag, TaggedWireProtocol.TYPE_FLOAT64, requiredForReader, ByteBuffer.allocate(Long.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putDouble(value)
            .array())
    }

    fun string(tag: Int, value: String, requiredForReader: Boolean = false) = apply {
        add(tag, TaggedWireProtocol.TYPE_UTF8, requiredForReader, encodeUtf8(value, tag))
    }

    fun strings(tag: Int, values: Iterable<String>, requiredForReader: Boolean = false) = apply {
        values.forEach { string(tag, it, requiredForReader) }
    }

    fun bytes(tag: Int, value: ByteArray, requiredForReader: Boolean = false) = apply {
        add(tag, TaggedWireProtocol.TYPE_BYTES, requiredForReader, value.copyOf())
    }

    fun document(tag: Int, value: ByteArray, requiredForReader: Boolean = false) = apply {
        add(tag, TaggedWireProtocol.TYPE_DOCUMENT, requiredForReader, value.copyOf())
    }

    fun fileDescriptorRef(tag: Int, index: Int, requiredForReader: Boolean = false) = apply {
        if (index < 0) {
            throw TaggedWireException(TaggedWireError.INVALID_FIELD, "File descriptor index must not be negative")
        }
        add(tag, TaggedWireProtocol.TYPE_FD_REF, requiredForReader, ByteBuffer.allocate(Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(index)
            .array())
    }

    fun encode(): ByteArray {
        if (fields.size > limits.maxFields) {
            throw TaggedWireException(
                TaggedWireError.FIELD_LIMIT,
                "Field count ${fields.size} exceeds ${limits.maxFields}",
            )
        }
        val ordered = fields.sortedWith(compareBy<TaggedWireField> { it.tag }.thenBy { it.insertionIndex })
        var totalSize = TaggedWireProtocol.HEADER_SIZE_BYTES.toLong()
        ordered.forEach { field ->
            totalSize += TaggedWireProtocol.FIELD_HEADER_SIZE_BYTES.toLong() + field.payload.size.toLong()
            if (field.payload.size > limits.maxFieldBytes) {
                throw TaggedWireException(
                    TaggedWireError.SIZE_LIMIT,
                    "Field ${field.tag} exceeds ${limits.maxFieldBytes} bytes",
                )
            }
        }
        if (totalSize > limits.maxDocumentBytes || totalSize > Int.MAX_VALUE) {
            throw TaggedWireException(
                TaggedWireError.SIZE_LIMIT,
                "Document size $totalSize exceeds ${limits.maxDocumentBytes} bytes",
            )
        }

        return ByteBuffer.allocate(totalSize.toInt()).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(TaggedWireProtocol.MAGIC)
            putInt(TaggedWireProtocol.FORMAT_VERSION)
            putInt(totalSize.toInt())
            putInt(schemaId)
            putInt(schemaMajor)
            putInt(schemaMinor)
            putInt(ordered.size)
            ordered.forEach { field ->
                putInt(field.tag)
                putInt(field.type)
                putInt(field.flags)
                putInt(field.payload.size)
                put(field.payload)
            }
        }.array()
    }

    private fun add(tag: Int, type: Int, requiredForReader: Boolean, payload: ByteArray) {
        if (tag <= 0) {
            throw TaggedWireException(TaggedWireError.INVALID_FIELD, "Field tag must be positive")
        }
        if (payload.size > limits.maxFieldBytes) {
            throw TaggedWireException(
                TaggedWireError.SIZE_LIMIT,
                "Field $tag exceeds ${limits.maxFieldBytes} bytes",
            )
        }
        if (fields.size >= limits.maxFields) {
            throw TaggedWireException(
                TaggedWireError.FIELD_LIMIT,
                "Field count exceeds ${limits.maxFields}",
            )
        }
        fields += TaggedWireField(
            tag = tag,
            type = type,
            flags = if (requiredForReader) TaggedWireProtocol.FLAG_REQUIRED_FOR_READER else 0,
            payload = payload,
            insertionIndex = insertionIndex++,
        )
    }

    private fun encodeUtf8(value: String, tag: Int): ByteArray = try {
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value))
        ByteArray(encoded.remaining()).also(encoded::get)
    } catch (e: Exception) {
        throw TaggedWireException(TaggedWireError.INVALID_UTF8, "Field $tag cannot be encoded as UTF-8", e)
    }
}

class TaggedWireDocument private constructor(
    val schemaId: Int,
    val schemaMajor: Int,
    val schemaMinor: Int,
    private val fields: List<TaggedWireField>,
) {
    val fieldCount: Int
        get() = fields.size

    val tags: Set<Int>
        get() = fields.mapTo(linkedSetOf()) { it.tag }

    fun requireSchema(expectedId: Int, expectedMajor: Int): TaggedWireDocument = apply {
        if (schemaId != expectedId || schemaMajor != expectedMajor) {
            throw TaggedWireException(
                TaggedWireError.SCHEMA_MISMATCH,
                "Expected schema $expectedId/$expectedMajor but found $schemaId/$schemaMajor",
            )
        }
    }

    fun rejectUnknownRequiredFields(knownTags: Set<Int>): TaggedWireDocument = apply {
        val unknown = fields.firstOrNull { field ->
            field.tag !in knownTags &&
                field.flags and TaggedWireProtocol.FLAG_REQUIRED_FOR_READER != 0
        }
        if (unknown != null) {
            throw TaggedWireException(
                TaggedWireError.UNKNOWN_REQUIRED_FIELD,
                "Unknown required field ${unknown.tag}",
            )
        }
    }

    fun validateKnownCardinality(
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = apply {
        fields.asSequence()
            .filter { it.tag in knownTags }
            .groupingBy { it.tag }
            .eachCount()
            .forEach { (tag, count) ->
                if (count > 1 && tag !in repeatedTags) {
                    throw TaggedWireException(
                        TaggedWireError.DUPLICATE_FIELD,
                        "Field $tag occurs $count times",
                    )
                }
            }
    }

    fun requireInt32(tag: Int): Int = requireSingle(tag, TaggedWireProtocol.TYPE_INT32).asInt32()

    fun optionalInt32(tag: Int): Int? = optionalSingle(tag, TaggedWireProtocol.TYPE_INT32)?.asInt32()

    fun requireInt64(tag: Int): Long = requireSingle(tag, TaggedWireProtocol.TYPE_INT64).asInt64()

    fun optionalInt64(tag: Int): Long? = optionalSingle(tag, TaggedWireProtocol.TYPE_INT64)?.asInt64()

    fun requireBoolean(tag: Int): Boolean = requireSingle(tag, TaggedWireProtocol.TYPE_BOOLEAN).asBoolean()

    fun optionalBoolean(tag: Int): Boolean? = optionalSingle(tag, TaggedWireProtocol.TYPE_BOOLEAN)?.asBoolean()

    fun requireFloat64(tag: Int): Double = requireSingle(tag, TaggedWireProtocol.TYPE_FLOAT64).asFloat64()

    fun optionalFloat64(tag: Int): Double? = optionalSingle(tag, TaggedWireProtocol.TYPE_FLOAT64)?.asFloat64()

    fun requireString(tag: Int): String = requireSingle(tag, TaggedWireProtocol.TYPE_UTF8).asString()

    fun optionalString(tag: Int): String? = optionalSingle(tag, TaggedWireProtocol.TYPE_UTF8)?.asString()

    fun strings(tag: Int): List<String> = multiple(tag, TaggedWireProtocol.TYPE_UTF8).map { it.asString() }

    fun requireBytes(tag: Int): ByteArray = requireSingle(tag, TaggedWireProtocol.TYPE_BYTES).payload.copyOf()

    fun optionalBytes(tag: Int): ByteArray? = optionalSingle(tag, TaggedWireProtocol.TYPE_BYTES)?.payload?.copyOf()

    fun requireDocument(tag: Int): ByteArray = requireSingle(tag, TaggedWireProtocol.TYPE_DOCUMENT).payload.copyOf()

    fun optionalDocument(tag: Int): ByteArray? = optionalSingle(tag, TaggedWireProtocol.TYPE_DOCUMENT)?.payload?.copyOf()

    fun documents(tag: Int): List<ByteArray> = multiple(tag, TaggedWireProtocol.TYPE_DOCUMENT)
        .map { it.payload.copyOf() }

    fun requireFileDescriptorRef(tag: Int): Int = requireSingle(tag, TaggedWireProtocol.TYPE_FD_REF).asInt32()

    fun optionalFileDescriptorRef(tag: Int): Int? = optionalSingle(tag, TaggedWireProtocol.TYPE_FD_REF)?.asInt32()

    fun fileDescriptorRefs(tag: Int): List<Int> = multiple(tag, TaggedWireProtocol.TYPE_FD_REF)
        .map { it.asInt32() }

    private fun requireSingle(tag: Int, expectedType: Int): TaggedWireField = optionalSingle(tag, expectedType)
        ?: throw TaggedWireException(TaggedWireError.MISSING_FIELD, "Missing field $tag")

    private fun optionalSingle(tag: Int, expectedType: Int): TaggedWireField? {
        val matches = fields.filter { it.tag == tag }
        if (matches.size > 1) {
            throw TaggedWireException(
                TaggedWireError.DUPLICATE_FIELD,
                "Field $tag occurs ${matches.size} times",
            )
        }
        return matches.singleOrNull()?.also { requireType(it, expectedType) }
    }

    private fun multiple(tag: Int, expectedType: Int): List<TaggedWireField> = fields
        .filter { it.tag == tag }
        .onEach { requireType(it, expectedType) }

    private fun requireType(field: TaggedWireField, expectedType: Int) {
        if (field.type != expectedType) {
            throw TaggedWireException(
                TaggedWireError.TYPE_MISMATCH,
                "Field ${field.tag} has type ${field.type}, expected $expectedType",
            )
        }
    }

    companion object {
        fun decode(
            bytes: ByteArray,
            limits: TaggedWireLimits = TaggedWireProtocol.DEFAULT_LIMITS,
        ): TaggedWireDocument {
            if (bytes.size > limits.maxDocumentBytes) {
                throw TaggedWireException(
                    TaggedWireError.SIZE_LIMIT,
                    "Document size ${bytes.size} exceeds ${limits.maxDocumentBytes} bytes",
                )
            }
            if (bytes.size < TaggedWireProtocol.HEADER_SIZE_BYTES) {
                throw TaggedWireException(TaggedWireError.TRUNCATED, "Document header is truncated")
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.int
            if (magic != TaggedWireProtocol.MAGIC) {
                throw TaggedWireException(TaggedWireError.MALFORMED_HEADER, "Unexpected wire magic")
            }
            val formatVersion = buffer.int
            if (formatVersion != TaggedWireProtocol.FORMAT_VERSION) {
                throw TaggedWireException(
                    TaggedWireError.UNSUPPORTED_FORMAT,
                    "Unsupported wire format $formatVersion",
                )
            }
            val declaredSize = buffer.int
            if (declaredSize != bytes.size) {
                throw TaggedWireException(
                    TaggedWireError.MALFORMED_HEADER,
                    "Declared size $declaredSize does not match ${bytes.size}",
                )
            }
            val schemaId = buffer.int
            val schemaMajor = buffer.int
            val schemaMinor = buffer.int
            val fieldCount = buffer.int
            if (schemaId <= 0 || schemaMajor <= 0 || schemaMinor < 0) {
                throw TaggedWireException(TaggedWireError.MALFORMED_HEADER, "Invalid schema header")
            }
            if (fieldCount < 0 || fieldCount > limits.maxFields) {
                throw TaggedWireException(
                    TaggedWireError.FIELD_LIMIT,
                    "Field count $fieldCount exceeds ${limits.maxFields}",
                )
            }
            val maximumHeadersInDocument =
                (bytes.size - TaggedWireProtocol.HEADER_SIZE_BYTES) / TaggedWireProtocol.FIELD_HEADER_SIZE_BYTES
            if (fieldCount > maximumHeadersInDocument) {
                throw TaggedWireException(
                    TaggedWireError.TRUNCATED,
                    "Field count $fieldCount cannot fit in the declared document",
                )
            }

            val fields = ArrayList<TaggedWireField>(fieldCount)
            var previousTag = 0
            repeat(fieldCount) { index ->
                if (buffer.remaining() < TaggedWireProtocol.FIELD_HEADER_SIZE_BYTES) {
                    throw TaggedWireException(TaggedWireError.TRUNCATED, "Field $index header is truncated")
                }
                val tag = buffer.int
                val type = buffer.int
                val flags = buffer.int
                val length = buffer.int
                if (
                    tag <= 0 ||
                    type <= 0 ||
                    flags and TaggedWireProtocol.FLAG_REQUIRED_FOR_READER.inv() != 0 ||
                    length < 0
                ) {
                    throw TaggedWireException(TaggedWireError.INVALID_FIELD, "Field $index has invalid metadata")
                }
                if (tag < previousTag) {
                    throw TaggedWireException(TaggedWireError.INVALID_FIELD, "Field tags are not canonical")
                }
                previousTag = tag
                if (length > limits.maxFieldBytes) {
                    throw TaggedWireException(
                        TaggedWireError.SIZE_LIMIT,
                        "Field $tag exceeds ${limits.maxFieldBytes} bytes",
                    )
                }
                if (length > buffer.remaining()) {
                    throw TaggedWireException(TaggedWireError.TRUNCATED, "Field $tag payload is truncated")
                }
                val payload = ByteArray(length)
                buffer.get(payload)
                validatePrimitiveShape(tag, type, payload)
                fields += TaggedWireField(tag, type, flags, payload, index)
            }
            if (buffer.hasRemaining()) {
                throw TaggedWireException(TaggedWireError.INVALID_FIELD, "Document has trailing bytes")
            }
            return TaggedWireDocument(schemaId, schemaMajor, schemaMinor, fields)
        }

        private fun validatePrimitiveShape(tag: Int, type: Int, payload: ByteArray) {
            val valid = when (type) {
                TaggedWireProtocol.TYPE_INT32 -> payload.size == Int.SIZE_BYTES
                TaggedWireProtocol.TYPE_FD_REF -> payload.size == Int.SIZE_BYTES &&
                    ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).int >= 0
                TaggedWireProtocol.TYPE_INT64 -> payload.size == Long.SIZE_BYTES
                TaggedWireProtocol.TYPE_FLOAT64 -> payload.size == Long.SIZE_BYTES &&
                    ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).double.isFinite()
                TaggedWireProtocol.TYPE_BOOLEAN -> payload.size == 1 && (payload[0].toInt() == 0 || payload[0].toInt() == 1)
                TaggedWireProtocol.TYPE_UTF8 -> {
                    decodeUtf8(tag, payload)
                    true
                }
                TaggedWireProtocol.TYPE_BYTES,
                TaggedWireProtocol.TYPE_DOCUMENT,
                -> true
                else -> true // Unknown optional types remain skippable by length.
            }
            if (!valid) {
                throw TaggedWireException(TaggedWireError.INVALID_FIELD, "Field $tag has an invalid payload")
            }
        }
    }
}

private data class TaggedWireField(
    val tag: Int,
    val type: Int,
    val flags: Int,
    val payload: ByteArray,
    val insertionIndex: Int,
) {
    fun asInt32(): Int = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).int

    fun asInt64(): Long = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).long

    fun asFloat64(): Double = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).double

    fun asBoolean(): Boolean = payload[0].toInt() == 1

    fun asString(): String = decodeUtf8(tag, payload)
}

private fun decodeUtf8(tag: Int, payload: ByteArray): String = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(payload))
        .toString()
} catch (e: CharacterCodingException) {
    throw TaggedWireException(TaggedWireError.INVALID_UTF8, "Field $tag is not valid UTF-8", e)
}
