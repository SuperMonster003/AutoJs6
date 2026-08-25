package org.autojs.autojs.core.plugin.r8

import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidR8CompilerDescriptor(
    internal val descriptor: ParcelFileDescriptor,
) : R8CompilerOwnedDescriptor {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) descriptor.close()
    }
}
