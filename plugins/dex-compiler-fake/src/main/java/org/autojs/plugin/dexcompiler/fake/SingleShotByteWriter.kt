package org.autojs.plugin.dexcompiler.fake

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SingleShotByteWriter {
    private val claimed = AtomicBoolean(false)

    val hasClaimedWrite: Boolean
        get() = claimed.get()

    fun write(bytes: ByteArray, output: OutputStream) {
        check(claimed.compareAndSet(false, true)) { "Output writer may only be used once" }
        output.write(bytes)
        output.flush()
    }
}
