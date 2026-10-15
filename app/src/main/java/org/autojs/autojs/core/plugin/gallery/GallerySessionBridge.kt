package org.autojs.autojs.core.plugin.gallery

import org.autojs.plugin.gallery.api.GalleryContract
import java.util.concurrent.atomic.AtomicLong

/**
 * One session event as the host reads it off the callback Bundle: `type` is one of
 * `GalleryContract.EVENTS`; the other fields are present per type (`open`: kind; `page`: index;
 * `result`: the picker's items document or the editor's output description; `error`: code and
 * message; `close`: reason).
 */
data class GalleryEvent(
    val type: String,
    val reason: String? = null,
    val kind: String? = null,
    val index: Int? = null,
    val itemsJson: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/** Receives the events a [GallerySessionBridge] accepted; the script-side call or session object implements it. */
interface GallerySessionSink {
    fun onEvent(event: GalleryEvent)
    fun onDropped(reason: String, generation: Long, seq: Long) = Unit
}

/**
 * Validates the `onEvent(generation, seq, event)` deliveries of one session: the plugin chooses the
 * generation, so the bridge locks onto the first one it sees and drops any other as stale; `seq`
 * must increase strictly; unknown event types are malformed; a `close` event ends the session so
 * later deliveries are dropped as well. Pure Kotlin; the Binder adapter is [GalleryCallbackBinder].
 */
class GallerySessionBridge(private val sink: GallerySessionSink) {

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
    fun accept(generation: Long, seq: Long, event: GalleryEvent?): Boolean {
        if (event == null || event.type !in GalleryContract.EVENTS) return drop(REASON_MALFORMED, generation, seq, malformed)
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
            if (event.type == GalleryContract.EVENT_CLOSE) isClosed = true
        }
        delivered.incrementAndGet()
        sink.onEvent(event)
        return true
    }

    /**
     * Ends the session on the host side (the plugin process died, or the host closed it without
     * waiting for the plugin's own `close` event): delivers one synthetic `close` with [reason] unless
     * the plugin's close already arrived.
     */
    fun closeLocally(reason: String) {
        synchronized(this) {
            if (isClosed) return
            isClosed = true
        }
        delivered.incrementAndGet()
        sink.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = reason))
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
