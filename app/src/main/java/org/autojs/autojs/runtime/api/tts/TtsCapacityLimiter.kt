package org.autojs.autojs.runtime.api.tts

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounds work before it is posted to the TTS command lane.
 *
 * Limits checked only inside a Handler callback do not bound the Handler mailbox itself. A
 * reservation is therefore acquired by the producer and released exactly once when the command
 * or payload leaves the relevant stage.
 */
internal class TtsCapacityLimiter(
    private val maxItems: Int,
    private val maxWeight: Long,
) {

    init {
        require(maxItems > 0) { "TTS capacity item limit must be positive" }
        require(maxWeight > 0L) { "TTS capacity weight limit must be positive" }
    }

    private val lock = Any()

    @GuardedBy("lock")
    private var items = 0

    @GuardedBy("lock")
    private var weight = 0L

    fun tryAcquire(itemWeight: Long = 1L): Reservation? {
        require(itemWeight >= 0L) { "TTS capacity weight must not be negative" }
        synchronized(lock) {
            if (
                items >= maxItems ||
                itemWeight > maxWeight ||
                itemWeight > maxWeight - weight
            ) {
                return null
            }
            items += 1
            weight += itemWeight
        }
        return Reservation(this, itemWeight)
    }

    internal fun snapshot(): Pair<Int, Long> = synchronized(lock) {
        items to weight
    }

    private fun release(itemWeight: Long) {
        synchronized(lock) {
            check(items > 0 && weight >= itemWeight) {
                "TTS capacity reservation accounting is inconsistent"
            }
            items -= 1
            weight -= itemWeight
        }
    }

    class Reservation internal constructor(
        private val owner: TtsCapacityLimiter,
        private val weight: Long,
    ) {

        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                owner.release(weight)
            }
        }
    }

    private annotation class GuardedBy(val value: String)
}
