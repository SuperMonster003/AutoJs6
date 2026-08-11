package org.autojs.plugin.dexcompiler.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DexCompilerInputBundleCodecTest {

    @Test
    fun streamsCanonicalBundleAndRecoversExactOrderedPayloads() {
        val fixture = fixture()
        val encoded = ByteArrayOutputStream()
        val written = DexCompilerInputBundleCodec.write(encoded, fixture.sources)

        assertEquals(DexCompilerInputBundleCodec.encodedSize(fixture.identities), written.sizeBytes)
        assertEquals(DexSha256.digest(encoded.toByteArray()), written.contentSha256)

        val payloads = mutableListOf<ByteArray>()
        val read = DexCompilerInputBundleCodec.read(ByteArrayInputStream(encoded.toByteArray())) { _, input ->
            val payload = ByteArrayOutputStream()
            input.copyTo(payload)
            payloads += payload.toByteArray()
        }
        assertEquals(fixture.identities, read.identities)
        assertEquals(fixture.payloads.map(ByteArray::toList), payloads.map(ByteArray::toList))
        assertEquals(written.sizeBytes, read.sizeBytes)
        assertEquals(written.contentSha256, read.contentSha256)
    }

    @Test
    fun inputSetFingerprintIsDomainSeparatedAndOrderSensitive() {
        val identities = fixture().identities
        val reorderedClasspathContent = listOf(
            identities[0],
            identities[1].copy(
                sizeBytes = identities[2].sizeBytes,
                contentSha256 = identities[2].contentSha256,
            ),
            identities[2].copy(
                sizeBytes = identities[1].sizeBytes,
                contentSha256 = identities[1].contentSha256,
            ),
        )

        assertNotEquals(
            DexCompilerInputSetFingerprint.compute(identities),
            DexCompilerInputSetFingerprint.compute(reorderedClasspathContent),
        )
        assertNotEquals(
            DexCompilerInputSetFingerprint.compute(identities),
            DexRuntimeLibraryFingerprint.compute(
                identities.map { DexRuntimeLibraryIdentity(it.sizeBytes, it.contentSha256) }
            ),
        )
    }

    @Test
    fun rejectsTruncatedTrailingAndCorruptedPayloads() {
        val encoded = encode(fixture())

        assertBundleError(DexCompilerInputBundleError.TRUNCATED, encoded.copyOf(encoded.size - 1))
        assertBundleError(DexCompilerInputBundleError.TRAILING_DATA, encoded + 0x7f.toByte())
        assertBundleError(
            DexCompilerInputBundleError.CONTENT_MISMATCH,
            encoded.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() },
        )
    }

    @Test
    fun rejectsNonCanonicalRoleOrdinalAndEntryCountLimit() {
        val encoded = encode(fixture())
        val nonCanonicalOrdinal = encoded.copyOf().also { bytes ->
            // Record 1 begins at byte 64; its ordinal occupies bytes 68..71.
            bytes[71] = 1
        }
        assertBundleError(DexCompilerInputBundleError.INVALID_LAYOUT, nonCanonicalOrdinal)

        val error = assertThrows(DexCompilerInputBundleException::class.java) {
            DexCompilerInputBundleCodec.read(
                ByteArrayInputStream(encoded),
                limits = DexCompilerInputBundleLimits(maxEntryCount = 2),
            ) { _, _ -> Unit }
        }
        assertEquals(DexCompilerInputBundleError.LIMIT_EXCEEDED, error.error)
    }

    @Test
    fun writerRejectsSourceSizeAndDigestMismatchWithoutClosingCallerOutput() {
        val fixture = fixture()
        val first = fixture.sources.first()
        val badSource = DexCompilerInputBundleSource(first.identity) {
            ByteArrayInputStream(fixture.payloads.first() + 0x01)
        }
        val output = TrackingOutputStream()

        val error = assertThrows(DexCompilerInputBundleException::class.java) {
            DexCompilerInputBundleCodec.write(output, listOf(badSource) + fixture.sources.drop(1))
        }
        assertEquals(DexCompilerInputBundleError.CONTENT_MISMATCH, error.error)
        assertTrue(!output.closed)
    }

    @Test
    fun zeroBulkReadsMakeBoundedProgressAndCodecPreservesCallerStreamOwnership() {
        val fixture = fixture()
        val openedSources = mutableListOf<ZeroBulkInputStream>()
        val sources = fixture.identities.mapIndexed { index, identity ->
            DexCompilerInputBundleSource(identity) {
                ZeroBulkInputStream(fixture.payloads[index]).also(openedSources::add)
            }
        }
        val output = TrackingOutputStream()
        DexCompilerInputBundleCodec.write(output, sources)
        assertTrue(!output.closed)
        assertTrue(openedSources.all { it.closed })

        val callerInput = ZeroBulkInputStream(output.toByteArray())
        val decoded = mutableListOf<ByteArray>()
        DexCompilerInputBundleCodec.read(callerInput) { _, entry ->
            entry.close() // Entry close is intentionally a no-op inside the synchronous callback.
            val payload = ByteArrayOutputStream()
            entry.copyTo(payload)
            decoded += payload.toByteArray()
        }
        assertTrue(!callerInput.closed)
        assertEquals(fixture.payloads.map(ByteArray::toList), decoded.map(ByteArray::toList))
    }

    private fun assertBundleError(expected: DexCompilerInputBundleError, encoded: ByteArray) {
        val error = assertThrows(DexCompilerInputBundleException::class.java) {
            DexCompilerInputBundleCodec.read(ByteArrayInputStream(encoded)) { _, input ->
                input.copyTo(ByteArrayOutputStream())
            }
        }
        assertEquals(expected, error.error)
    }

    private fun encode(fixture: Fixture): ByteArray = ByteArrayOutputStream().also { output ->
        DexCompilerInputBundleCodec.write(output, fixture.sources)
    }.toByteArray()

    private fun fixture(): Fixture {
        val payloads = listOf(
            "program-jar".toByteArray(),
            "classpath-zero".toByteArray(),
            "classpath-one".toByteArray(),
        )
        val identities = listOf(
            identity(DexCompilerInputRole.PROGRAM, 0, payloads[0]),
            identity(DexCompilerInputRole.CLASSPATH, 0, payloads[1]),
            identity(DexCompilerInputRole.CLASSPATH, 1, payloads[2]),
        )
        return Fixture(
            payloads = payloads,
            identities = identities,
            sources = identities.mapIndexed { index, identity ->
                DexCompilerInputBundleSource(identity) { ByteArrayInputStream(payloads[index]) }
            },
        )
    }

    private fun identity(
        role: DexCompilerInputRole,
        ordinal: Int,
        payload: ByteArray,
    ) = DexCompilerInputIdentity(role, ordinal, payload.size.toLong(), DexSha256.digest(payload))

    private data class Fixture(
        val payloads: List<ByteArray>,
        val identities: List<DexCompilerInputIdentity>,
        val sources: List<DexCompilerInputBundleSource>,
    )

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class ZeroBulkInputStream(
        private val bytes: ByteArray,
    ) : java.io.InputStream() {
        private var index = 0
        var closed = false
            private set

        override fun read(): Int = if (index >= bytes.size) -1 else bytes[index++].toInt() and 0xff

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = when {
            length == 0 -> 0
            index >= bytes.size -> -1
            else -> 0 // Deliberately violates the bulk-read progress contract.
        }

        override fun close() {
            closed = true
        }
    }
}
