package org.autojs.autojs.core.plugin.mail

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enough IMAP for `session.test` (mail roadmap P1.3 and P3.1): greeting, CAPABILITY, LOGIN, ID
 * (RFC 2971), NOOP and LOGOUT on a loopback port owned by the test process, which the plugin
 * process reaches over `127.0.0.1`. With [holdConnections] set, new connections are kept open
 * without a greeting and commands on the connections already served go unanswered, so the
 * plugin blocks in a socket read until [releaseHeld] or the plugin process goes away.
 */
internal class FakeImapServer : Closeable {

    class Session {
        val commands = CopyOnWriteArrayList<String>()
    }

    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "fake-imap").apply { isDaemon = true } }
    private val held = CopyOnWriteArrayList<Socket>()
    private val blocked = AtomicInteger(0)
    val sessions = CopyOnWriteArrayList<Session>()

    @Volatile
    var holdConnections = false

    val port: Int
        get() = socket.localPort

    fun start() {
        executor.execute {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                executor.execute { serve(client) }
            }
        }
    }

    /** True once a connection or a command of the plugin is being held unanswered. */
    fun awaitBlocked(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (blocked.get() == 0) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(50L)
        }
        return true
    }

    fun releaseHeld() {
        held.forEach { runCatching { it.close() } }
        held.clear()
        blocked.set(0)
    }

    private fun serve(client: Socket) {
        if (holdConnections) {
            held += client
            blocked.incrementAndGet()
            return
        }
        val session = Session().also { sessions += it }
        client.use { connection ->
            val reader = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.ISO_8859_1))
            val writer = PrintWriter(OutputStreamWriter(connection.getOutputStream(), Charsets.ISO_8859_1))
            fun send(line: String) {
                writer.print(line)
                writer.print(CRLF)
                writer.flush()
            }
            send("* OK Fake IMAP ready")
            while (true) {
                val line = reader.readLine() ?: return
                val parts = line.split(' ', limit = 3)
                val tag = parts[0]
                val command = parts.getOrElse(1) { "" }.uppercase()
                session.commands += command
                if (holdConnections) {
                    blocked.incrementAndGet()
                    continue
                }
                when (command) {
                    "CAPABILITY" -> {
                        send("* CAPABILITY IMAP4rev1 UIDPLUS ID")
                        send("$tag OK CAPABILITY completed")
                    }
                    "ID" -> {
                        send("""* ID ("name" "FakeIMAP")""")
                        send("$tag OK ID completed")
                    }
                    "LOGOUT" -> {
                        send("* BYE Fake IMAP signing off")
                        send("$tag OK LOGOUT completed")
                        return
                    }
                    else -> send("$tag OK $command completed")
                }
            }
        }
    }

    override fun close() {
        releaseHeld()
        socket.close()
        executor.shutdownNow()
    }

    private companion object {
        val CRLF: String = String(charArrayOf(13.toChar(), 10.toChar()))
    }
}
