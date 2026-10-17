package org.autojs.autojs.model.explorer

import android.system.Os
import android.util.Base64
import androidx.annotation.Keep
import org.autojs.autojs.model.explorer.PrivilegedReadProtocol as Protocol
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** One authorized app_process keeps one read-only descriptor open for an entire viewer session. */
@Keep
object PrivilegedReadCommand {
    @Keep
    @JvmStatic
    fun main(arguments: Array<String>) {
        val output = DataOutputStream(BufferedOutputStream(System.out, 8192))
        var handshaken = false
        val end = System.nanoTime() + Protocol.MAX_LIFETIME_MS * 1_000_000L
        val activeDeadline = AtomicLong(System.nanoTime() + Protocol.OPEN_TIMEOUT_MS * 1_000_000L)
        val finished = AtomicBoolean(false)
        // Start before stat/open: removable/FUSE storage may block even metadata lookup. The
        // authorized helper must enforce its own deadline independently of the su wrapper.
        val watchdog = thread(name = "PrivilegedReadDeadline", isDaemon = true) {
            while (!finished.get()) {
                val now = System.nanoTime()
                if (now >= end || now >= activeDeadline.get()) exitProcess(2)
                try { Thread.sleep(100) } catch (_: InterruptedException) { return@thread }
            }
        }
        try {
            require(arguments.size == 1 && arguments[0].length <= 65536)
            val request = JSONObject(String(Base64.decode(arguments[0], Base64.NO_WRAP), Charsets.UTF_8))
            require(request.toString().length <= 32 * 1024)
            val statRequest = JSONObject(request.toString()).put("operation", "stat")
            val stat = JSONObject(PrivilegedFileEngine.execute(statRequest.toString()))
            Protocol.checkSuccess(stat)
            val openRequest = JSONObject(request.toString()).put("source", stat.getString("path"))
                .put("expected", stat.getJSONObject("identity"))
            PrivilegedFileEngine.openReadDescriptor(openRequest.toString()).use { descriptor ->
                val identity = Protocol.Identity.from(Os.fstat(descriptor.fileDescriptor))
                Protocol.requireExpected(stat.getJSONObject("identity"), identity)
                Protocol.requireExpected(request.optJSONObject("expected"), identity)
                output.writeInt(Protocol.MAGIC)
                output.writeInt(Protocol.VERSION)
                Protocol.writeJson(output, JSONObject().put("ok", true).put("identity", identity.json())
                    .put("size", identity.size).put("pid", android.os.Process.myPid()))
                output.flush()
                handshaken = true
                activeDeadline.set(Long.MAX_VALUE)
                val input = DataInputStream(BufferedInputStream(System.`in`, 4096))
                val buffer = ByteArray(Protocol.MAX_CHUNK)
                while (true) {
                    // EOF also releases the descriptor while a player is paused. An outer native
                    // timeout bounds the process even if an abandoned client retains a pipe.
                    val operation = try { input.readInt() } catch (_: EOFException) { break }
                    activeDeadline.set(System.nanoTime() + Protocol.READ_TIMEOUT_MS * 1_000_000L)
                    val id = input.readInt()
                    val offset = input.readLong()
                    val count = input.readInt()
                    val requestedDeadline = input.readLong()
                    if (operation == Protocol.CLOSE) break
                    try {
                        if (System.nanoTime() >= end) throw Protocol.failure("ACCESS_UNAVAILABLE", "The viewing session has expired")
                        if (requestedDeadline <= System.currentTimeMillis()) throw Protocol.failure("TIMEOUT", "The read request expired")
                        val remaining = (requestedDeadline - System.currentTimeMillis()).coerceAtMost(Protocol.READ_TIMEOUT_MS)
                        val deadline = System.nanoTime() + remaining * 1_000_000L
                        activeDeadline.set(deadline)
                        if (operation !in setOf(Protocol.READ, Protocol.CHECK) || id <= 0 || offset < 0 || count !in 0..Protocol.MAX_CHUNK ||
                            (operation == Protocol.CHECK && (count != 0 || offset != 0L))) {
                            throw Protocol.failure("INVALID_REQUEST", "Invalid random-access request")
                        }
                        val read = if (operation == Protocol.READ) Protocol.read(descriptor, identity, offset, buffer, count)
                        else { identity.check(Os.fstat(descriptor.fileDescriptor)); 0 }
                        if (System.nanoTime() >= deadline) throw Protocol.failure("TIMEOUT", "The read request timed out")
                        output.writeInt(id)
                        output.writeInt(0)
                        output.writeInt(read)
                        if (read != 0) output.write(buffer, 0, read)
                        output.flush()
                        activeDeadline.set(Long.MAX_VALUE)
                    } catch (error: Exception) {
                        output.writeInt(id)
                        output.writeInt(1)
                        Protocol.writeJson(output, Protocol.error(error))
                        output.flush()
                        break // Fail closed; no rebinding, reopening, or hidden retries.
                    }
                }
            }
        } catch (error: Throwable) {
            if (!handshaken) runCatching {
                output.writeInt(Protocol.MAGIC)
                output.writeInt(Protocol.VERSION)
                Protocol.writeJson(output, Protocol.error(error))
                output.flush()
            }
            System.err.println(error.message.orEmpty().take(2048))
            exitProcess(1)
        } finally {
            finished.set(true)
            watchdog.interrupt()
        }
    }
}
