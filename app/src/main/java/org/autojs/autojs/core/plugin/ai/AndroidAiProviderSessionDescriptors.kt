package org.autojs.autojs.core.plugin.ai

import android.os.ParcelFileDescriptor
import org.autojs.plugin.ai.common.api.AiCommonLimits
import java.io.Closeable

/**
 * Host-owned request descriptors for one synchronous `openSession` transaction.
 *
 * The Binder proxy only borrows these descriptor objects while it marshals the synchronous call.
 * Ownership remains with the transcript policy until that call is committed as succeeded or failed.
 */
internal class AndroidAiProviderSessionRequestDescriptors(
    descriptors: Array<out ParcelFileDescriptor?>,
) : AiProviderSessionRequestDescriptorLease {
    private val lock = Any()
    private val owned = takeAndroidAiProviderSessionDescriptorsOrClose(
        descriptors = descriptors,
        missingMessage = "local AI request descriptor is null",
    )
    private val descriptorCount = owned.size
    private var borrowClaimed = false
    private var activeBorrow = false
    private var closeRequested = false

    override val count: Int
        get() = descriptorCount

    /**
     * Borrows, but never duplicates or transfers, the owned descriptors for exactly one synchronous
     * Binder call. A racing [close] is deferred until [block] unwinds.
     */
    internal fun <T> withBorrowedDescriptorsForOpen(
        block: (Array<ParcelFileDescriptor>) -> T,
    ): T {
        val borrowed = synchronized(lock) {
            check(!closeRequested) { "local AI request descriptors are closed" }
            check(!borrowClaimed) { "local AI request descriptors were already dispatched" }
            val available = Array(descriptorCount) { index ->
                checkNotNull(owned[index]) { "local AI request descriptor owner is unavailable" }
            }
            borrowClaimed = true
            activeBorrow = true
            available
        }
        try {
            return block(borrowed)
        } finally {
            val closing = synchronized(lock) {
                check(activeBorrow) { "local AI request descriptor borrow was not active" }
                activeBorrow = false
                if (closeRequested) takeOwnedLocked() else emptyList()
            }
            closing.closeAllQuietly()
        }
    }

    override fun close() {
        val closing = synchronized(lock) {
            if (closeRequested) return
            closeRequested = true
            if (activeBorrow) emptyList() else takeOwnedLocked()
        }
        closing.closeAllQuietly()
    }

    private fun takeOwnedLocked(): List<ParcelFileDescriptor> = buildList(descriptorCount) {
        owned.forEachIndexed { index, descriptor ->
            if (descriptor != null) {
                add(descriptor)
                owned[index] = null
            }
        }
    }
}

/** Receiver-owned Binder callback descriptors before the sole asynchronous ownership transfer. */
internal class AndroidAiProviderSessionIncomingDescriptors(
    descriptors: Array<out ParcelFileDescriptor?>,
    private val operationObserver: AiProviderSessionOperationObserver,
) : AiProviderSessionIncomingDescriptors {
    private val lock = Any()
    private val incoming = takeAndroidAiProviderSessionDescriptorsOrClose(
        descriptors = descriptors,
        missingMessage = "Incoming local AI descriptor is null",
    )
    private val descriptorCount = incoming.size
    private var copyClaimed = false
    private var closed = false

    override val count: Int
        get() = descriptorCount

    /**
     * Transfers every complete platform descriptor exactly once to an asynchronous lease.
     *
     * `ParcelFileDescriptor.dup()` only duplicates the data descriptor and loses the reliable-pipe
     * communication channel used by `closeWithError`/`checkError`. Moving the callback-owned PFD
     * objects preserves that channel. A later [close] therefore sees empty slots and cannot close
     * descriptors already owned by the returned lease.
     */
    override fun copyForAsync(): AiProviderSessionDescriptorLease {
        val transferred = synchronized(lock) {
            check(!closed) { "Incoming local AI descriptors are closed" }
            check(!copyClaimed) { "Incoming local AI descriptors were already transferred" }
            val available = Array<ParcelFileDescriptor?>(descriptorCount) { index ->
                checkNotNull(incoming[index]) { "Incoming local AI descriptor is unavailable" }
            }
            copyClaimed = true
            incoming.indices.forEach { index -> incoming[index] = null }
            available
        }
        try {
            return AndroidAiProviderSessionDescriptorLease(transferred, operationObserver)
        } catch (error: Throwable) {
            transferred.asList().filterNotNull().closeAllQuietly()
            throw error
        }
    }

    override fun close() {
        val closing = synchronized(lock) {
            if (closed) return
            closed = true
            buildList(descriptorCount) {
                incoming.forEachIndexed { index, descriptor ->
                    if (descriptor != null) {
                        add(descriptor)
                        incoming[index] = null
                    }
                }
            }
        }
        closing.closeAllQuietly()
    }
}

/** Independently owned callback descriptors materialized only on the control lane. */
private class AndroidAiProviderSessionDescriptorLease(
    descriptors: Array<ParcelFileDescriptor?>,
    private val operationObserver: AiProviderSessionOperationObserver,
) : AiProviderSessionDescriptorLease {
    private val lock = Any()
    private val owned = descriptors.copyOf()
    private val claimed = BooleanArray(owned.size)
    private val descriptorCount = owned.size
    private var closed = false

    override val count: Int
        get() = descriptorCount

    override fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray {
        require(declaredLengthBytes in 0L..AiCommonLimits.MAX_PAYLOAD_BYTES) {
            "local AI descriptor payload exceeds its protocol limit"
        }
        require(declaredLengthBytes <= Int.MAX_VALUE.toLong()) {
            "local AI descriptor payload cannot be materialized"
        }
        val descriptor = synchronized(lock) {
            check(!closed) { "local AI callback descriptors are closed" }
            require(index in owned.indices) { "local AI callback descriptor index is out of range" }
            check(!claimed[index]) { "local AI callback descriptor was already read" }
            claimed[index] = true
            checkNotNull(owned[index]) { "local AI callback descriptor owner is unavailable" }
        }

        var operation: Closeable? = null
        try {
            operation = operationObserver.begin()
            return readExactly(descriptor, declaredLengthBytes)
        } finally {
            synchronized(lock) {
                if (owned[index] === descriptor) owned[index] = null
            }
            descriptor.closeQuietly()
            runCatching { operation?.close() }
        }
    }

    override fun close() {
        val closing = synchronized(lock) {
            if (closed) return
            closed = true
            buildList(descriptorCount) {
                owned.forEachIndexed { index, descriptor ->
                    if (descriptor != null) {
                        add(descriptor)
                        owned[index] = null
                    }
                }
            }
        }
        closing.closeAllQuietly()
    }

    private fun readExactly(
        descriptor: ParcelFileDescriptor,
        declaredLengthBytes: Long,
    ): ByteArray {
        val output = ByteArray(declaredLengthBytes.toInt())
        val input = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        try {
            var offset = 0
            while (offset < output.size) {
                val count = input.read(output, offset, output.size - offset)
                require(count >= 0) { "local AI descriptor ended before its declared length" }
                if (count == 0) {
                    val value = input.read()
                    require(value >= 0) { "local AI descriptor ended before its declared length" }
                    output[offset] = value.toByte()
                    offset += 1
                } else {
                    offset += count
                }
            }
            require(input.read() == -1) { "local AI descriptor exceeds its declared length" }
            descriptor.checkError()
            return output
        } finally {
            runCatching(input::close)
        }
    }
}

internal fun closeAndroidAiProviderSessionIncomingDescriptors(
    descriptors: Array<out ParcelFileDescriptor?>?,
) {
    if (descriptors == null) return
    var index = 0
    while (index < descriptors.size) {
        descriptors[index].closeQuietly()
        index += 1
    }
}

/**
 * Takes ownership of a platform descriptor array or closes every valid element on any failure.
 * Android/Java arrays can contain null despite a non-null Kotlin element projection.
 */
private fun takeAndroidAiProviderSessionDescriptorsOrClose(
    descriptors: Array<out ParcelFileDescriptor?>,
    missingMessage: String,
): Array<ParcelFileDescriptor?> {
    try {
        return arrayOfNulls<ParcelFileDescriptor>(descriptors.size).also { copy ->
            var index = 0
            while (index < descriptors.size) {
                copy[index] = requireNotNull(descriptors[index]) { missingMessage }
                index += 1
            }
        }
    } catch (error: Throwable) {
        closeAndroidAiProviderSessionIncomingDescriptors(descriptors)
        throw error
    }
}

private fun Collection<ParcelFileDescriptor>.closeAllQuietly() {
    forEach(ParcelFileDescriptor::closeQuietly)
}

private fun ParcelFileDescriptor?.closeQuietly() {
    if (this == null) return
    try {
        close()
    } catch (_: Throwable) {
        // Continue closing later elements even if one hostile/broken descriptor rejects close.
    }
}
