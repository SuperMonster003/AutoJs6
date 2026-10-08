package org.autojs.plugin.compose.api.model

import android.graphics.Bitmap
import org.autojs.plugin.compose.api.ComposeUiLimits
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.WeakHashMap

/** Weak, process-local handles make Parcel round trips preserve D27 ownership without copying pixels. */
internal object BitmapHandles {
    val processToken: String = UUID.randomUUID().toString()
    private var next = 1L
    private val queue = ReferenceQueue<Bitmap>()
    private class Entry(bitmap: Bitmap, val id: Long, queue: ReferenceQueue<Bitmap>) : WeakReference<Bitmap>(bitmap, queue)
    private val identities = WeakHashMap<Bitmap, Long>()
    private val entries = HashMap<Long, Entry>()

    @Synchronized fun register(bitmap: Bitmap): Long {
        drain()
        if (bitmap.isRecycled) invalid("Bitmap is recycled")
        validateBitmapPixels(bitmap.width, bitmap.height)
        identities[bitmap]?.let { return it }
        limit(next > 0, "Bitmap handle space exhausted")
        return next++.also { id -> identities[bitmap] = id; entries[id] = Entry(bitmap, id, queue) }
    }

    @Synchronized fun get(id: Long): Bitmap? {
        drain()
        return entries[id]?.get()?.takeUnless { it.isRecycled }
    }

    @Synchronized fun restore(token: String, id: Long): UiValue.BitmapRef {
        if (token != processToken) invalid("BitmapRef cannot cross a process boundary")
        if (get(id) == null) invalid("BitmapRef owner is no longer available")
        return UiValue.BitmapRef.restored(id)
    }

    private fun drain() {
        while (true) {
            val entry = queue.poll() as? Entry ?: break
            entries.remove(entry.id)
        }
    }
}

internal fun validateBitmapPixels(width: Int, height: Int) {
    if (width <= 0 || height <= 0) invalid("Invalid bitmap dimensions")
    limit(width.toLong() * height <= ComposeUiLimits.MAX_BITMAP_PIXELS, "Bitmap exceeds pixel limit")
}
