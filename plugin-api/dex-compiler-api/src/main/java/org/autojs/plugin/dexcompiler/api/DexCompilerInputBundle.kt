package org.autojs.plugin.dexcompiler.api

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Collections

enum class DexCompilerInputBundleError {
    MALFORMED_HEADER,
    UNSUPPORTED_VERSION,
    LIMIT_EXCEEDED,
    INVALID_LAYOUT,
    TRUNCATED,
    CONTENT_MISMATCH,
    TRAILING_DATA,
}

class DexCompilerInputBundleException(
    val error: DexCompilerInputBundleError,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

data class DexCompilerInputBundleLimits(
    val maxEntryCount: Int = DexCompilerContract.MAX_CLASSPATH_JARS + 1,
    val maxEntryBytes: Long = DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES,
    val maxBundleBytes: Long = DexCompilerContract.MAX_COMPRESSED_INPUT_BUNDLE_BYTES,
) {
    init {
        require(maxEntryCount in 1..DexCompilerContract.MAX_CLASSPATH_JARS + 1)
        require(maxEntryBytes in 1L..DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES)
        require(maxBundleBytes in 1L..DexCompilerContract.MAX_COMPRESSED_INPUT_BUNDLE_BYTES)
    }
}

class DexCompilerInputBundleSource(
    val identity: DexCompilerInputIdentity,
    val openInputStream: () -> InputStream,
)

class DexCompilerInputBundleSummary(
    identities: Collection<DexCompilerInputIdentity>,
    val sizeBytes: Long,
    val contentSha256: DexSha256,
) {
    val identities: List<DexCompilerInputIdentity> = Collections.unmodifiableList(ArrayList(identities))
}

/**
 * Canonical streaming framing for protocol 1.1 input bundles.
 *
 * The format is deliberately not ZIP: it has no paths, offsets, compression, or optional fields.
 * All integers are big-endian. The fixed header is `AJ6DXB01`, version 1, and record count. Each
 * 48-byte record is role, ordinal, byte length, and SHA-256. Payloads immediately follow the full
 * record table in the same order, with no trailing bytes.
 *
 * [write] closes every stream opened by [DexCompilerInputBundleSource.openInputStream], but never
 * closes the caller-owned output. [read] never closes the caller-owned input. The entry stream
 * supplied to [read]'s `onEntry` callback is valid only for that synchronous callback invocation;
 * retaining it is unsupported and its [InputStream.close] operation is deliberately a no-op.
 */
object DexCompilerInputBundleCodec {
    private val MAGIC = byteArrayOf(
        'A'.code.toByte(),
        'J'.code.toByte(),
        '6'.code.toByte(),
        'D'.code.toByte(),
        'X'.code.toByte(),
        'B'.code.toByte(),
        '0'.code.toByte(),
        '1'.code.toByte(),
    )
    private const val FORMAT_VERSION = 1
    private const val FIXED_HEADER_BYTES = 16L
    private const val RECORD_BYTES = 48L

    fun encodedSize(identities: Collection<DexCompilerInputIdentity>): Long {
        val ordered = identities.toList()
        DexCompilerInputIdentityValidation.requireCanonical(ordered)
        return encodedSizeUnchecked(ordered)
    }

    fun write(
        output: OutputStream,
        sources: Collection<DexCompilerInputBundleSource>,
        limits: DexCompilerInputBundleLimits = DexCompilerInputBundleLimits(),
    ): DexCompilerInputBundleSummary {
        val ordered = sources.toList()
        val identities = ordered.map(DexCompilerInputBundleSource::identity)
        validateForBundle(identities, limits)
        val expectedSize = encodedSizeUnchecked(identities)
        if (expectedSize > limits.maxBundleBytes) {
            fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle exceeds the byte limit")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val counted = DigestingOutputStream(output, digest, limits.maxBundleBytes)
        val data = DataOutputStream(counted)
        data.write(MAGIC)
        data.writeInt(FORMAT_VERSION)
        data.writeInt(identities.size)
        identities.forEach { identity ->
            data.writeInt(identity.role.wireCode)
            data.writeInt(identity.ordinal)
            data.writeLong(identity.sizeBytes)
            data.write(identity.contentSha256.toByteArray())
        }

        ordered.forEachIndexed { index, source ->
            val entryDigest = MessageDigest.getInstance("SHA-256")
            source.openInputStream().use { input ->
                var remaining = source.identity.sizeBytes
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (remaining > 0L) {
                    val requested = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, requested)
                    if (read < 0) {
                        fail(
                            DexCompilerInputBundleError.CONTENT_MISMATCH,
                            "Input bundle source $index ended before its declared size",
                        )
                    }
                    if (read == 0) {
                        val value = input.read()
                        if (value < 0) {
                            fail(
                                DexCompilerInputBundleError.CONTENT_MISMATCH,
                                "Input bundle source $index ended before its declared size",
                            )
                        }
                        data.write(value)
                        entryDigest.update(value.toByte())
                        remaining--
                        continue
                    }
                    data.write(buffer, 0, read)
                    entryDigest.update(buffer, 0, read)
                    remaining -= read.toLong()
                }
                if (input.read() >= 0) {
                    fail(
                        DexCompilerInputBundleError.CONTENT_MISMATCH,
                        "Input bundle source $index exceeds its declared size",
                    )
                }
            }
            if (!entryDigest.digest().contentEquals(source.identity.contentSha256.toByteArray())) {
                fail(
                    DexCompilerInputBundleError.CONTENT_MISMATCH,
                    "Input bundle source $index SHA-256 does not match its identity",
                )
            }
        }
        data.flush()
        if (counted.count != expectedSize) {
            fail(DexCompilerInputBundleError.CONTENT_MISMATCH, "Input bundle size is not canonical")
        }
        return DexCompilerInputBundleSummary(
            identities = identities,
            sizeBytes = counted.count,
            contentSha256 = DexSha256.fromBytes(digest.digest()),
        )
    }

    fun read(
        input: InputStream,
        limits: DexCompilerInputBundleLimits = DexCompilerInputBundleLimits(),
        onEntry: (DexCompilerInputIdentity, InputStream) -> Unit,
    ): DexCompilerInputBundleSummary {
        val digest = MessageDigest.getInstance("SHA-256")
        val counted = DigestingInputStream(input, digest, limits.maxBundleBytes)
        val data = DataInputStream(counted)
        val magic = readExact(data, MAGIC.size, "Input bundle magic")
        if (!magic.contentEquals(MAGIC)) {
            fail(DexCompilerInputBundleError.MALFORMED_HEADER, "Input bundle magic is invalid")
        }
        val version = readInt(data, "Input bundle version")
        if (version != FORMAT_VERSION) {
            fail(DexCompilerInputBundleError.UNSUPPORTED_VERSION, "Input bundle version $version is unsupported")
        }
        val entryCount = readInt(data, "Input bundle entry count")
        if (entryCount !in 1..limits.maxEntryCount) {
            fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle entry count is invalid")
        }

        val identities = ArrayList<DexCompilerInputIdentity>(entryCount)
        repeat(entryCount) { index ->
            val roleCode = readInt(data, "Input bundle record $index role")
            val role = DexCompilerInputRole.values().firstOrNull { it.wireCode == roleCode }
                ?: fail(DexCompilerInputBundleError.INVALID_LAYOUT, "Input bundle record $index role is unknown")
            val ordinal = readInt(data, "Input bundle record $index ordinal")
            val sizeBytes = readLong(data, "Input bundle record $index size")
            if (sizeBytes !in 1L..limits.maxEntryBytes) {
                fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle record $index size is invalid")
            }
            identities += DexCompilerInputIdentity(
                role = role,
                ordinal = ordinal,
                sizeBytes = sizeBytes,
                contentSha256 = DexSha256.fromBytes(readExact(data, DexSha256.BYTE_COUNT, "record $index SHA-256")),
            )
        }
        validateForBundle(identities, limits)
        val expectedSize = encodedSizeUnchecked(identities)
        if (expectedSize > limits.maxBundleBytes) {
            fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle exceeds the byte limit")
        }

        identities.forEachIndexed { index, identity ->
            val entryDigest = MessageDigest.getInstance("SHA-256")
            val entry = EntryInputStream(counted, identity.sizeBytes, entryDigest, index)
            onEntry(identity, entry)
            entry.drain()
            if (!entryDigest.digest().contentEquals(identity.contentSha256.toByteArray())) {
                fail(
                    DexCompilerInputBundleError.CONTENT_MISMATCH,
                    "Input bundle record $index SHA-256 does not match its payload",
                )
            }
        }
        if (counted.read() >= 0) {
            fail(DexCompilerInputBundleError.TRAILING_DATA, "Input bundle contains trailing data")
        }
        if (counted.count != expectedSize) {
            fail(DexCompilerInputBundleError.TRUNCATED, "Input bundle size differs from its canonical framing")
        }
        return DexCompilerInputBundleSummary(
            identities = identities,
            sizeBytes = counted.count,
            contentSha256 = DexSha256.fromBytes(digest.digest()),
        )
    }

    private fun validateForBundle(
        identities: List<DexCompilerInputIdentity>,
        limits: DexCompilerInputBundleLimits,
    ) {
        try {
            DexCompilerInputIdentityValidation.requireCanonical(identities)
        } catch (error: DexCompilerContractException) {
            fail(DexCompilerInputBundleError.INVALID_LAYOUT, error.message ?: "Input bundle layout is invalid", error)
        }
        if (identities.size > limits.maxEntryCount || identities.any { it.sizeBytes > limits.maxEntryBytes }) {
            fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle exceeds an entry limit")
        }
    }

    private fun encodedSizeUnchecked(identities: List<DexCompilerInputIdentity>): Long = try {
        var size = Math.addExact(FIXED_HEADER_BYTES, Math.multiplyExact(RECORD_BYTES, identities.size.toLong()))
        identities.forEach { identity -> size = Math.addExact(size, identity.sizeBytes) }
        size
    } catch (error: ArithmeticException) {
        fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle size overflows", error)
    }

    private fun readExact(input: DataInputStream, size: Int, label: String): ByteArray = ByteArray(size).also {
        try {
            input.readFully(it)
        } catch (error: EOFException) {
            fail(DexCompilerInputBundleError.TRUNCATED, "$label is truncated", error)
        }
    }

    private fun readInt(input: DataInputStream, label: String): Int = try {
        input.readInt()
    } catch (error: EOFException) {
        fail(DexCompilerInputBundleError.TRUNCATED, "$label is truncated", error)
    }

    private fun readLong(input: DataInputStream, label: String): Long = try {
        input.readLong()
    } catch (error: EOFException) {
        fail(DexCompilerInputBundleError.TRUNCATED, "$label is truncated", error)
    }

    private fun fail(
        error: DexCompilerInputBundleError,
        message: String,
        cause: Throwable? = null,
    ): Nothing = throw DexCompilerInputBundleException(error, message, cause)

    private class DigestingOutputStream(
        private val delegate: OutputStream,
        private val digest: MessageDigest,
        private val maximumBytes: Long,
    ) : OutputStream() {
        var count: Long = 0L
            private set

        override fun write(value: Int) {
            reserve(1)
            delegate.write(value)
            digest.update(value.toByte())
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (length == 0) return
            reserve(length)
            delegate.write(buffer, offset, length)
            digest.update(buffer, offset, length)
        }

        override fun flush() = delegate.flush()

        private fun reserve(length: Int) {
            val next = try {
                Math.addExact(count, length.toLong())
            } catch (error: ArithmeticException) {
                fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle size overflows", error)
            }
            if (next > maximumBytes) {
                fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle exceeds the byte limit")
            }
            count = next
        }
    }

    private class DigestingInputStream(
        private val delegate: InputStream,
        private val digest: MessageDigest,
        private val maximumBytes: Long,
    ) : InputStream() {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                reserve(1)
                digest.update(value.toByte())
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val read = delegate.read(buffer, offset, length)
            if (read == 0) {
                val value = delegate.read()
                if (value < 0) return -1
                reserve(1)
                buffer[offset] = value.toByte()
                digest.update(value.toByte())
                return 1
            }
            if (read > 0) {
                reserve(read)
                digest.update(buffer, offset, read)
            }
            return read
        }

        private fun reserve(length: Int) {
            val next = try {
                Math.addExact(count, length.toLong())
            } catch (error: ArithmeticException) {
                fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle size overflows", error)
            }
            if (next > maximumBytes) {
                fail(DexCompilerInputBundleError.LIMIT_EXCEEDED, "Input bundle exceeds the byte limit")
            }
            count = next
        }
    }

    private class EntryInputStream(
        private val input: InputStream,
        sizeBytes: Long,
        private val digest: MessageDigest,
        private val index: Int,
    ) : InputStream() {
        private var remaining = sizeBytes

        override fun read(): Int {
            if (remaining == 0L) return -1
            val value = input.read()
            if (value < 0) truncated()
            digest.update(value.toByte())
            remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (remaining == 0L) return -1
            val requested = minOf(length.toLong(), remaining).toInt()
            val read = input.read(buffer, offset, requested)
            if (read < 0) truncated()
            if (read == 0) {
                fail(
                    DexCompilerInputBundleError.TRUNCATED,
                    "Input bundle record $index payload made no progress",
                )
            }
            digest.update(buffer, offset, read)
            remaining -= read.toLong()
            return read
        }

        override fun close() = Unit

        fun drain() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (remaining > 0L) {
                val read = read(buffer)
                if (read < 0) truncated()
                if (read == 0) {
                    fail(
                        DexCompilerInputBundleError.TRUNCATED,
                        "Input bundle record $index payload made no progress",
                    )
                }
            }
        }

        private fun truncated(): Nothing = fail(
            DexCompilerInputBundleError.TRUNCATED,
            "Input bundle record $index payload is truncated",
        )
    }
}

internal object DexCompilerInputIdentityValidation {
    fun requireCanonical(identities: List<DexCompilerInputIdentity>) {
        if (identities.isEmpty() || identities.size > DexCompilerContract.MAX_CLASSPATH_JARS + 1) {
            invalid("Input identity count is invalid")
        }
        identities.forEachIndexed { index, identity ->
            val expectedRole = if (index == 0) DexCompilerInputRole.PROGRAM else DexCompilerInputRole.CLASSPATH
            val expectedOrdinal = if (index == 0) 0 else index - 1
            if (identity.role != expectedRole || identity.ordinal != expectedOrdinal) {
                invalid("Input identities are not in canonical program/classpath order")
            }
            val maximumBytes = when (identity.role) {
                DexCompilerInputRole.PROGRAM -> DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES
                DexCompilerInputRole.CLASSPATH -> DexCompilerContract.MAX_COMPRESSED_CLASSPATH_JAR_BYTES
            }
            if (identity.sizeBytes !in 1L..maximumBytes) {
                invalid("Input identity $index size is invalid")
            }
        }
        val classpathBytes = try {
            identities.drop(1).fold(0L) { total, identity -> Math.addExact(total, identity.sizeBytes) }
        } catch (error: ArithmeticException) {
            throw DexCompilerContractException(
                DexCompilerContractViolation.INVALID_VALUE,
                "Classpath input size overflows",
                error,
            )
        }
        if (classpathBytes > DexCompilerContract.MAX_TOTAL_COMPRESSED_CLASSPATH_BYTES) {
            invalid("Classpath inputs exceed the protocol byte limit")
        }
    }

    private fun invalid(message: String): Nothing = throw DexCompilerContractException(
        DexCompilerContractViolation.INVALID_VALUE,
        message,
    )
}
