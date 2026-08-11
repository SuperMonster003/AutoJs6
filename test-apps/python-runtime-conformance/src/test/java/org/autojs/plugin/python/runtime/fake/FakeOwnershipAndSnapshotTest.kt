package org.autojs.plugin.python.runtime.fake

import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class FakeOwnershipAndSnapshotTest {
    @Test
    fun completeReceiverCopiesAreAdoptedWithoutChangingIdentity() {
        val first = Any()
        val second = Any()
        val closed = mutableListOf<Any>()
        val adopted = FakeAllOrNoneOwnership.adopt(
            incoming = listOf(first, second),
            closeReceiverCopy = closed::add,
        )
        assertSame(first, adopted[0])
        assertSame(second, adopted[1])
        assertTrue(closed.isEmpty())
    }

    @Test
    fun nullReceiverCopyPublishesNoOwnerAndClosesEveryNonNullCopy() {
        val receiverCopiesClosed = mutableListOf<Int>()
        assertTrue(
            runCatching {
                FakeAllOrNoneOwnership.adopt(
                    incoming = listOf(1, null, 3),
                    closeReceiverCopy = receiverCopiesClosed::add,
                )
            }.isFailure,
        )
        assertEquals(listOf(1, 3), receiverCopiesClosed)
    }

    @Test
    fun exactReaderRejectsTruncationOverflowAndSimulatedDeadline() {
        val exact = byteArrayOf(1, 2, 3)
        val accepted = FakeDeclaredPayloadReader.readExactly(
            ByteArrayInputStream(exact),
            declaredLengthBytes = 3L,
            maximumLengthBytes = 3L,
        )
        assertArrayEquals(exact, accepted.bytes)
        assertTrue(
            runCatching {
                FakeDeclaredPayloadReader.readExactly(ByteArrayInputStream(byteArrayOf(1)), 2L, 2L)
            }.isFailure,
        )
        assertTrue(
            runCatching {
                FakeDeclaredPayloadReader.readExactly(ByteArrayInputStream(byteArrayOf(1, 2)), 1L, 2L)
            }.isFailure,
        )

        var checks = 0
        val oneByteReads = object : InputStream() {
            private var value = 0
            override fun read(): Int = if (value++ < 4) value else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val next = read()
                if (next < 0) return -1
                bytes[offset] = next.toByte()
                return 1
            }
        }
        assertTrue(
            runCatching {
                FakeDeclaredPayloadReader.readExactly(
                    oneByteReads,
                    declaredLengthBytes = 4L,
                    maximumLengthBytes = 4L,
                    shouldStop = { ++checks > 1 },
                )
            }.isFailure,
        )
    }

    @Test
    fun snapshotPublicationIsDigestCheckedAtomicAndDetachedFromSenderMutation() {
        val source = "print('safe')".toByteArray()
        val stdin = "input".toByteArray()
        val request = FakePythonTestFixtures.request(source, stdin)
        val supplied = mutableMapOf(0 to source.copyOf(), 1 to stdin.copyOf())
        val snapshots = FakePythonSnapshotMaterializer.materialize(request) { reference ->
            val value = supplied.getValue(reference.descriptorIndex)
            FakeDeclaredPayloadReader.readExactly(
                ByteArrayInputStream(value),
                reference.declaredLengthBytes,
                reference.declaredLengthBytes,
            )
        }
        supplied.getValue(0).fill(0)
        assertArrayEquals(source, snapshots.bytes(PythonPayloadKind.SOURCE))
        assertArrayEquals(stdin, snapshots.bytes(PythonPayloadKind.STDIN))
        snapshots.close()
        assertTrue(runCatching { snapshots.bytes(PythonPayloadKind.SOURCE) }.isFailure)

        val badDigest = runCatching {
            FakePythonSnapshotMaterializer.materialize(request) { reference ->
                val bytes = supplied.getValue(reference.descriptorIndex)
                FakeReadPayload(bytes.copyOf(), ByteArray(32))
            }
        }
        assertTrue(badDigest.isFailure)
    }
}
