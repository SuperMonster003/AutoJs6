package org.autojs.autojs.core.plugin.epub

import org.autojs.plugin.epub.api.EpubContract
import java.util.concurrent.atomic.AtomicLong

/**
 * One reader-session event as the host reads it off the callback Bundle (plugin roadmap P5.3 /
 * P5.4): `type` is one of `EpubContract.EVENTS`; the other fields are present per type (`open`:
 * visible, title, locator, positions; `progress`: locator, progression, title, href; `bookmark`:
 * change and the bookmark document as a one-element `bookmarks` array; `close`: reason;
 * `error`: code and message).
 */
data class EpubReaderEvent(
    val type: String,
    val reason: String? = null,
    val change: String? = null,
    val locatorJson: String? = null,
    val progression: Double? = null,
    val title: String? = null,
    val href: String? = null,
    val visible: Boolean? = null,
    val positions: Int? = null,
    val bookmarksJson: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/** Receives the events an [EpubReaderBridge] accepted; the script-side session object (roadmap P6) implements it. */
interface EpubReaderSink {
    fun onEvent(event: EpubReaderEvent)
    fun onDropped(reason: String, generation: Long, seq: Long)
}

/**
 * Validates the `onEvent(generation, seq, event)` deliveries of one reader session: the plugin
 * chooses the generation, so the bridge locks onto the first one it sees and drops any other as
 * stale; `seq` must increase strictly; unknown event types are malformed; a `close` event ends
 * the session so later deliveries are dropped as well. Pure Kotlin; the Binder adapter lives in
 * [EpubReaderCallbackBinder].
 */
class EpubReaderBridge(private val sink: EpubReaderSink) {

    private val lastSeqValue = AtomicLong(0L)
    private val delivered = AtomicLong(0L)
    private val stale = AtomicLong(0L)
    private val reordered = AtomicLong(0L)
    private val malformed = AtomicLong(0L)

    @Volatile
    var generation: Long? = null
        private set

    @Volatile
    var isClosed: Boolean = false
        private set

    val lastSeq: Long get() = lastSeqValue.get()
    val deliveredCount: Long get() = delivered.get()
    val staleCount: Long get() = stale.get()
    val reorderedCount: Long get() = reordered.get()
    val malformedCount: Long get() = malformed.get()

    /** Returns true when the event reached the sink. */
    fun accept(generation: Long, seq: Long, event: EpubReaderEvent?): Boolean {
        if (event == null || event.type !in EpubContract.EVENTS) return drop(REASON_MALFORMED, generation, seq, malformed)
        synchronized(this) {
            if (isClosed) return drop(REASON_CLOSED, generation, seq, stale)
            val expected = this.generation
            if (expected == null) {
                this.generation = generation
            } else if (generation != expected) {
                return drop(REASON_STALE_GENERATION, generation, seq, stale)
            }
            if (seq <= lastSeqValue.get()) return drop(REASON_OUT_OF_ORDER, generation, seq, reordered)
            lastSeqValue.set(seq)
            if (event.type == EpubContract.EVENT_CLOSE) isClosed = true
        }
        delivered.incrementAndGet()
        sink.onEvent(event)
        return true
    }

    /**
     * Ends the session on the host side (the plugin process died, or the host closed it without
     * waiting for the plugin's own `close` event): delivers one synthetic `close` with [reason]
     * unless the plugin's close already arrived.
     */
    fun closeLocally(reason: String) {
        synchronized(this) {
            if (isClosed) return
            isClosed = true
        }
        delivered.incrementAndGet()
        sink.onEvent(EpubReaderEvent(EpubContract.EVENT_CLOSE, reason = reason))
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
