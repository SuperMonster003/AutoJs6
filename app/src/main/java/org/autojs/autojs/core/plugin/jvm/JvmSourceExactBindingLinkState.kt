package org.autojs.autojs.core.plugin.jvm

/**
 * Serializes publication of a Binder death link with binding closure. The actual linkToDeath
 * call and every external callback stay outside this monitor. Publication is the linearization
 * point: a close that wins first suppresses the callback and requires exact unlink; publication
 * that wins first establishes a logical connection without allowing a blocking callback to hold
 * up close.
 */
internal class JvmSourceExactBindingLinkState<B : Any, R : Any> {
    data class Link<B : Any, R : Any>(val binder: B, val recipient: R)

    data class CloseResult<B : Any, R : Any>(
        val newlyClosed: Boolean,
        val publishedLink: Link<B, R>?,
    )

    private var closed = false
    private var publishedLink: Link<B, R>? = null

    @Synchronized
    fun isClosed(): Boolean = closed

    /** A true result linearizes delivery before close; the external callback still runs lock-free. */
    @Synchronized
    fun reserveDeliveryIfOpen(): Boolean = !closed

    /** A true result owns the link before close; physical callback completion may happen later. */
    @Synchronized
    fun publish(binder: B, recipient: R): Boolean {
        if (closed) return false
        check(publishedLink == null) { "A Binder death link was already published" }
        publishedLink = Link(binder, recipient)
        return true
    }

    @Synchronized
    fun close(): CloseResult<B, R> {
        if (closed) return CloseResult(newlyClosed = false, publishedLink = null)
        closed = true
        return CloseResult(newlyClosed = true, publishedLink = publishedLink.also { publishedLink = null })
    }
}
