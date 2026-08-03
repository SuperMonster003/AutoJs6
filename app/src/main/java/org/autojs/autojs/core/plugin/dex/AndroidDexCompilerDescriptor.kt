package org.autojs.autojs.core.plugin.dex

import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidDexCompilerDescriptor(
    internal val descriptor: ParcelFileDescriptor,
) : DexCompilerOwnedDescriptor {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) descriptor.close()
    }
}
