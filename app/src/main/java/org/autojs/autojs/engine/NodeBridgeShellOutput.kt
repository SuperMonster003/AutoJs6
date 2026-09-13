package org.autojs.autojs.engine

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bounds stdout and stderr together without splitting UTF-8 characters. */
internal object NodeBridgeShellOutput {
    const val MAX_BYTES = 256 * 1024

    /** Process.waitFor(timeout, unit) was added in Android 26. Use the same bounded polling on 24 / 25. */
    fun awaitExit(process: Process, timeoutMs: Long): Boolean {
        val start = System.nanoTime()
        while (true) {
            try { process.exitValue(); return true } catch (_: IllegalThreadStateException) { /* still running */ }
            val remaining = timeoutMs - (System.nanoTime() - start) / 1_000_000
            if (remaining <= 0) return false
            Thread.sleep(minOf(remaining, 10))
        }
    }

    fun limit(value: Any?): Int? {
        if (value == null) return null
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble() && value.toLong() in 1L..MAX_BYTES.toLong()) {
            "maxOutputBytes must be an integer from 1 to $MAX_BYTES"
        }
        return value.toInt()
    }

    fun text(bytes: ByteArray, maximum: Int): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.IGNORE).onUnmappableCharacter(CodingErrorAction.IGNORE)
        .decode(ByteBuffer.wrap(bytes, 0, bytes.size.coerceAtMost(maximum))).toString()

    fun bound(stdout: String, stderr: String, maximum: Int): Pair<String, String> {
        val out = text(stdout.toByteArray(Charsets.UTF_8), maximum)
        return out to text(stderr.toByteArray(Charsets.UTF_8), maximum - out.toByteArray(Charsets.UTF_8).size)
    }
}
