package org.autojs.autojs.core.plugin.explorer.output

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExplorerActionBoundedOutputCopierTest {

    @Test
    fun copiesSequentiallyWithinTheDeclaredLimitAndReserve() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val output = ByteArrayOutputStream()
        val reserve = 10L
        val copier = ExplorerActionBoundedOutputCopier(
            policy = ExplorerActionBoundedOutputPolicy(
                declaredMaxBytes = bytes.size.toLong(),
                hostMaxBytes = 10L,
                minimumUsableBytes = reserve,
            ),
            usableBytes = { reserve + 100L - output.size() },
            bufferSize = 2,
        )

        assertEquals(bytes.size.toLong(), copier.copy(ByteArrayInputStream(bytes), output))
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun stopsBeforeAChunkWouldCrossTheDeclaredLimit() {
        val output = ByteArrayOutputStream()
        val copier = ExplorerActionBoundedOutputCopier(
            policy = ExplorerActionBoundedOutputPolicy(
                declaredMaxBytes = 3L,
                hostMaxBytes = 10L,
                minimumUsableBytes = 0L,
            ),
            usableBytes = { 100L },
            bufferSize = 2,
        )

        val error = runCatching {
            copier.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), output)
        }.exceptionOrNull()

        assertTrue(error is ExplorerActionOutputCopyException)
        assertEquals(
            ExplorerActionOutputCopyViolation.DECLARED_LIMIT_EXCEEDED,
            (error as ExplorerActionOutputCopyException).violation,
        )
        assertArrayEquals(byteArrayOf(1, 2), output.toByteArray())
    }

    @Test
    fun enforcesTheHostLimitEvenWhenTheDescriptorIsLarger() {
        val copier = ExplorerActionBoundedOutputCopier(
            policy = ExplorerActionBoundedOutputPolicy(
                declaredMaxBytes = 10L,
                hostMaxBytes = 3L,
                minimumUsableBytes = 0L,
            ),
            usableBytes = { 100L },
            bufferSize = 4,
        )

        val error = runCatching {
            copier.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), ByteArrayOutputStream())
        }.exceptionOrNull()

        assertEquals(
            ExplorerActionOutputCopyViolation.HOST_LIMIT_EXCEEDED,
            (error as ExplorerActionOutputCopyException).violation,
        )
    }

    @Test
    fun preservesTheConfiguredUsableStorageReserve() {
        val output = ByteArrayOutputStream()
        val copier = ExplorerActionBoundedOutputCopier(
            policy = ExplorerActionBoundedOutputPolicy(
                declaredMaxBytes = 10L,
                hostMaxBytes = 10L,
                minimumUsableBytes = 32L,
            ),
            usableBytes = { 33L },
            bufferSize = 2,
        )

        val error = runCatching {
            copier.copy(ByteArrayInputStream(byteArrayOf(1, 2)), output)
        }.exceptionOrNull()

        assertEquals(
            ExplorerActionOutputCopyViolation.STORAGE_RESERVE_EXHAUSTED,
            (error as ExplorerActionOutputCopyException).violation,
        )
        assertEquals(0, output.size())
    }
}
