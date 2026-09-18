package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailContract
import java.util.concurrent.atomic.AtomicLong

/** Receives the events a [MailWatchBridge] accepted; the P3 `MailWatch` object implements it. */
interface MailWatchSink {
    fun onEvent(event: MailJson.Event)
    fun onDropped(reason: String, generation: Long, seq: Long)
}

/**
 * Validates the `onEvent(generation, seq, event)` deliveries of one watch (roadmap P1.2, D17):
 * events of another generation are stale and dropped, `seq` must increase strictly, and a
 * `closed` event ends the watch so later deliveries are dropped as well. Pure Kotlin; the Binder
 * adapter lives in [MailWatchCallbackBinder].
 */
class MailWatchBridge(val generation: Long, private val sink: MailWatchSink) {

    private val lastSeqValue = AtomicLong(0L)
    private val delivered = AtomicLong(0L)
    private val stale = AtomicLong(0L)
    private val reordered = AtomicLong(0L)
    private val malformed = AtomicLong(0L)

    @Volatile
    var isClosed: Boolean = false
        private set

    val lastSeq: Long
        get() = lastSeqValue.get()

    val deliveredCount: Long
        get() = delivered.get()

    val staleCount: Long
        get() = stale.get()

    val reorderedCount: Long
        get() = reordered.get()

    val malformedCount: Long
        get() = malformed.get()

    /** Returns true when the event reached the sink. */
    fun accept(generation: Long, seq: Long, eventJson: String?): Boolean {
        if (isClosed) return drop(REASON_CLOSED, generation, seq, stale)
        if (generation != this.generation) return drop(REASON_STALE_GENERATION, generation, seq, stale)
        val event = runCatching { MailJson.decodeEvent(eventJson) }.getOrNull()
            ?: return drop(REASON_MALFORMED, generation, seq, malformed)
        if (event.seq != 0L && event.seq != seq) return drop(REASON_MALFORMED, generation, seq, malformed)
        if (event.generation != 0L && event.generation != generation) return drop(REASON_STALE_GENERATION, generation, seq, stale)
        synchronized(this) {
            if (seq <= lastSeqValue.get()) return drop(REASON_OUT_OF_ORDER, generation, seq, reordered)
            lastSeqValue.set(seq)
            if (event.type == MailContract.EVENT_CLOSED) isClosed = true
        }
        delivered.incrementAndGet()
        sink.onEvent(event)
        return true
    }

    /** Marks the watch closed locally (stop requested by the host); later events are dropped. */
    fun close() {
        isClosed = true
    }

    private fun drop(reason: String, generation: Long, seq: Long, counter: AtomicLong): Boolean {
        counter.incrementAndGet()
        sink.onDropped(reason, generation, seq)
        return false
    }

    companion object {
        const val REASON_CLOSED = "closed"
        const val REASON_STALE_GENERATION = "stale-generation"
        const val REASON_OUT_OF_ORDER = "out-of-order"
        const val REASON_MALFORMED = "malformed"
    }
}
