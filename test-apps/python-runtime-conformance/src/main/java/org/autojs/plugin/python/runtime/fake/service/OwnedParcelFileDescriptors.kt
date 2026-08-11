package org.autojs.plugin.python.runtime.fake.service

import android.os.ParcelFileDescriptor
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.fake.FakeDeclaredPayloadReader
import org.autojs.plugin.python.runtime.fake.FakeReadPayload
import java.io.Closeable

internal class OwnedParcelFileDescriptors private constructor(
    descriptors: List<ParcelFileDescriptor>,
) : Closeable {
    private val descriptors = descriptors.map<ParcelFileDescriptor, ParcelFileDescriptor?> { it }.toTypedArray()

    val count: Int
        get() = descriptors.size

    fun read(
        reference: PythonPayloadReference,
        maximumLengthBytes: Long,
        shouldStop: () -> Boolean,
    ): FakeReadPayload {
        val index = reference.descriptorIndex
        val descriptor = synchronized(descriptors) {
            require(index in descriptors.indices) { "Descriptor index is out of range" }
            checkNotNull(descriptors[index]) { "Descriptor was already consumed or closed" }
        }
        return try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                FakeDeclaredPayloadReader.readExactly(
                    input = input,
                    declaredLengthBytes = reference.declaredLengthBytes,
                    maximumLengthBytes = maximumLengthBytes,
                    shouldStop = shouldStop,
                    verifyProducerCompletion = descriptor::checkError,
                )
            }
        } finally {
            synchronized(descriptors) {
                if (descriptors[index] === descriptor) descriptors[index] = null
            }
            closeQuietly(descriptor)
        }
    }

    override fun close() {
        val pending = synchronized(descriptors) {
            descriptors.mapIndexedNotNull { index, descriptor ->
                descriptor?.also { descriptors[index] = null }
            }
        }
        pending.forEach(::closeQuietly)
    }

    companion object {
        /**
         * Atomically adopts the complete receiver-side PFD copies unmarshalled by Binder.
         * Keeping the ParcelFileDescriptor objects intact preserves reliable-pipe error channels;
         * duplicating only descriptor.fileDescriptor would silently discard that channel.
         */
        fun takeReceiverCopies(
            incoming: Array<out ParcelFileDescriptor?>,
        ): OwnedParcelFileDescriptors = OwnedParcelFileDescriptors(
            incoming.mapIndexed { index, descriptor ->
                requireNotNull(descriptor) { "Descriptor $index is null" }
            },
        )

        fun closeIncoming(incoming: Array<out ParcelFileDescriptor?>?) {
            incoming?.forEach(::closeQuietly)
        }

        private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
            runCatching { descriptor?.close() }
        }
    }
}
