package org.autojs.autojs.memory

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Loopback HTTP and WebSocket endpoint for scenario S10, so that the gate needs no network.
 * GET /http answers a short text body; /ws upgrades, echoes text frames and answers a close frame.
 * zh-CN: 场景 S10 用的回环 HTTP 与 WebSocket 端点, 门禁因此不需要网络.
 * GET /http 返回一段短文本; /ws 升级后回显文本帧并应答关闭帧.
 */
internal class GateLoopbackServer : AutoCloseable {

    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "memory-gate-loopback") }
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    @Volatile
    private var running = true

    val httpUrl = "http://127.0.0.1:${serverSocket.localPort}/http"
    val webSocketUrl = "ws://127.0.0.1:${serverSocket.localPort}/ws"

    init {
        executor.execute {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    return@execute
                }
                executor.execute { socket.use { runCatching { handle(it) } } }
            }
        }
    }

    override fun close() {
        running = false
        runCatching { serverSocket.close() }
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 70_000
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val requestLine = readLine(input) ?: return
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }
        val path = requestLine.split(' ').getOrNull(1).orEmpty()
        when {
            path.startsWith("/ws") -> webSocket(input, output, headers["sec-websocket-key"].orEmpty())
            else -> {
                val body = "memory-gate".toByteArray(StandardCharsets.UTF_8)
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(StandardCharsets.ISO_8859_1))
                output.write(body)
                output.flush()
            }
        }
    }

    private fun webSocket(input: InputStream, output: OutputStream, key: String) {
        val accept = Base64.encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + ACCEPT_GUID).toByteArray(StandardCharsets.ISO_8859_1)),
            Base64.NO_WRAP,
        )
        output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
        while (running) {
            val first = input.read()
            val second = input.read()
            if (first < 0 || second < 0) return
            val opcode = first and 0x0f
            var length = (second and 0x7f).toLong()
            if (length == 126L) length = readExact(input, 2).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
            else if (length == 127L) length = readExact(input, 8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
            if (length > 1_048_576L) return
            val mask = if (second and 0x80 != 0) readExact(input, 4) else null
            val payload = readExact(input, length.toInt())
            if (mask != null) payload.indices.forEach { payload[it] = (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
            when (opcode) {
                0x1 -> writeFrame(output, 0x1, payload)
                0x8 -> {
                    writeFrame(output, 0x8, payload)
                    return
                }
                0x9 -> writeFrame(output, 0xA, payload)
            }
        }
    }

    private fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
        output.write(0x80 or opcode)
        if (payload.size <= 125) output.write(payload.size) else {
            output.write(126)
            output.write((payload.size ushr 8) and 0xff)
            output.write(payload.size and 0xff)
        }
        output.write(payload)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return if (buffer.size() == 0) null else buffer.toString("ISO-8859-1").trimEnd('\r')
            if (value == '\n'.code) return buffer.toString("ISO-8859-1").trimEnd('\r')
            buffer.write(value)
        }
    }

    private fun readExact(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw EOFException("Unexpected end of WebSocket frame")
            offset += count
        }
        return bytes
    }

    private companion object {
        const val ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
