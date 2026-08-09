package org.autojs.plugin.ai.text.fake

class FakeDescriptorOwnershipLedger(descriptorCount: Int) {
    private val taken = BooleanArray(descriptorCount)
    private val released = BooleanArray(descriptorCount)
    private var closed = false

    val count: Int = descriptorCount

    init {
        require(descriptorCount >= 0)
    }

    @Synchronized
    fun take(index: Int) {
        check(!closed) { "Descriptor owner is closed" }
        require(index in taken.indices) { "Descriptor index is out of range" }
        check(!taken[index]) { "Descriptor was already consumed" }
        taken[index] = true
    }

    @Synchronized
    fun release(index: Int) {
        require(index in taken.indices) { "Descriptor index is out of range" }
        check(taken[index]) { "Descriptor was not consumed" }
        released[index] = true
    }

    /** Returns every descriptor still owned, including descriptors in an in-flight read. */
    @Synchronized
    fun closeOwned(): IntArray {
        if (closed) return IntArray(0)
        closed = true
        return released.indices.filterNot { released[it] }.toIntArray()
    }
}
