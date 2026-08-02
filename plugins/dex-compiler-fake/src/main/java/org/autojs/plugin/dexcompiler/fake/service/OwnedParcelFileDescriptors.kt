package org.autojs.plugin.dexcompiler.fake.service

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class OwnedParcelFileDescriptors private constructor(
    val program: ParcelFileDescriptor,
    val output: ParcelFileDescriptor,
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        closeQuietly(program)
        closeQuietly(output)
    }

    companion object {
        private const val TAG = "DexFakePfdOwner"

        fun duplicateBeforeAsync(
            incomingProgram: ParcelFileDescriptor,
            incomingOutput: ParcelFileDescriptor,
        ): OwnedParcelFileDescriptors {
            var programCopy: ParcelFileDescriptor? = null
            var outputCopy: ParcelFileDescriptor? = null
            try {
                programCopy = ParcelFileDescriptor.dup(incomingProgram.fileDescriptor)
                outputCopy = ParcelFileDescriptor.dup(incomingOutput.fileDescriptor)
                return OwnedParcelFileDescriptors(programCopy, outputCopy)
            } catch (error: Throwable) {
                closeQuietly(programCopy)
                closeQuietly(outputCopy)
                throw error
            } finally {
                closeQuietly(incomingProgram)
                closeQuietly(incomingOutput)
            }
        }

        fun closeIncoming(
            incomingProgram: ParcelFileDescriptor?,
            incomingOutput: ParcelFileDescriptor?,
        ) {
            closeQuietly(incomingProgram)
            closeQuietly(incomingOutput)
        }

        private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
            runCatching { descriptor?.close() }.onFailure { error ->
                Log.w(TAG, "Failed to close a fixture file descriptor", error)
            }
        }
    }
}
