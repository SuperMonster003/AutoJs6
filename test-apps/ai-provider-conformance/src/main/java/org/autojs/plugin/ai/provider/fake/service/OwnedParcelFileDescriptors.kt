package org.autojs.plugin.ai.provider.fake.service

import android.os.ParcelFileDescriptor
import org.autojs.plugin.ai.provider.fake.FakeDeclaredPayloadReader
import org.autojs.plugin.ai.provider.fake.FakeDescriptorOwnershipLedger
import java.io.Closeable

internal class OwnedParcelFileDescriptors private constructor(
    private val descriptors: Array<ParcelFileDescriptor?>,
) : Closeable {
    private val ledger = FakeDescriptorOwnershipLedger(descriptors.size)

    val count: Int
        get() = ledger.count

    fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray {
        ledger.take(index)
        var descriptor: ParcelFileDescriptor? = null
        return try {
            val activeDescriptor = synchronized(descriptors) {
                descriptors[index] ?: error("Descriptor owner was closed")
            }
            descriptor = activeDescriptor
            val input = ParcelFileDescriptor.AutoCloseInputStream(activeDescriptor)
            try {
                FakeDeclaredPayloadReader.readExactly(input, declaredLengthBytes) {
                    activeDescriptor.checkError()
                }
            } finally {
                input.close()
            }
        } finally {
            ledger.release(index)
            synchronized(descriptors) {
                if (descriptors[index] === descriptor) descriptors[index] = null
            }
            closeQuietly(descriptor)
        }
    }

    override fun close() {
        ledger.closeOwned().forEach { index ->
            val descriptor = synchronized(descriptors) {
                descriptors[index].also { descriptors[index] = null }
            }
            closeQuietly(descriptor)
        }
    }

    companion object {
        fun duplicateBeforeAsync(incoming: Array<out ParcelFileDescriptor>): OwnedParcelFileDescriptors {
            val copies = arrayOfNulls<ParcelFileDescriptor>(incoming.size)
            try {
                incoming.forEachIndexed { index, descriptor ->
                    copies[index] = ParcelFileDescriptor.dup(descriptor.fileDescriptor)
                }
                return OwnedParcelFileDescriptors(copies)
            } catch (error: Throwable) {
                copies.forEach(::closeQuietly)
                throw error
            } finally {
                incoming.forEach(::closeQuietly)
            }
        }

        fun closeIncoming(incoming: Array<out ParcelFileDescriptor>?) {
            incoming?.forEach(::closeQuietly)
        }

        private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
            runCatching { descriptor?.close() }
        }
    }
}
