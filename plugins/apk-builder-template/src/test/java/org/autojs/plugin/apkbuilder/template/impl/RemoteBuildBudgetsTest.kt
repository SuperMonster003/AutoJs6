package org.autojs.plugin.apkbuilder.template.impl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException

class RemoteBuildBudgetsTest {

    @Test
    fun boundedCopy_acceptsExactLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        val copied = RemoteBoundedStreamCopier.copy(
            input = ByteArrayInputStream(bytes),
            output = output,
            budget = RemoteTransferBudget("test input", bytes.size.toLong()),
        )

        assertEquals(bytes.size.toLong(), copied)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun boundedCopy_rejectsFirstBytePastLimit() {
        val output = ByteArrayOutputStream()

        assertThrows(IOException::class.java) {
            RemoteBoundedStreamCopier.copy(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                output = output,
                budget = RemoteTransferBudget("test input", 4L),
            )
        }
        assertEquals(0, output.size())
    }

    @Test
    fun declaredSize_rejectsNegativeAndPastLimitButAcceptsUnknownAndBoundary() {
        val budget = RemoteTransferBudget("test input", 4L)

        budget.validateDeclaredSize(0L)
        budget.validateDeclaredSize(4L)
        assertThrows(IOException::class.java) { budget.validateDeclaredSize(-1L) }
        assertThrows(IOException::class.java) { budget.validateDeclaredSize(5L) }
    }

    @Test
    fun boundedCopy_honorsCancellationBeforeReading() {
        val output = ByteArrayOutputStream()

        assertThrows(CancellationException::class.java) {
            RemoteBoundedStreamCopier.copy(
                input = ByteArrayInputStream(byteArrayOf(1)),
                output = output,
                budget = RemoteTransferBudget("test input", 1L),
                isCancelled = { true },
            )
        }
        assertEquals(0, output.size())
    }
}
