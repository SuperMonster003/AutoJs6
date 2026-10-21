package org.autojs.autojs.engine

import android.util.Base64
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Loopback XOAUTH2 fixture: an expired synthetic token fails, its replacement can list INBOX. */
internal class RuntimeMailTokenServer : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val pool = Executors.newCachedThreadPool { Thread(it, "runtime-mail-token-fixture").apply { isDaemon = true } }
    val attempts = AtomicInteger()
    val accepted = AtomicInteger()
    val rejected = AtomicInteger()
    val port get() = server.localPort

    init {
        pool.execute {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets += socket
                pool.execute { runCatching { serve(socket) } }
            }
        }
    }

    private fun serve(socket: Socket) = socket.use {
        socket.soTimeout = 15_000
        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
        val writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
        fun send(line: String) { writer.write(line); writer.write("\r\n"); writer.flush() }
        send("* OK runtime token fixture")
        while (true) {
            val line = reader.readLine() ?: break
            val parts = line.split(' ', limit = 3)
            val tag = parts[0]
            when (parts.getOrElse(1) { "" }.uppercase()) {
                "CAPABILITY" -> {
                    send("* CAPABILITY IMAP4rev1 UIDPLUS AUTH=XOAUTH2 SASL-IR")
                    send(tag + " OK capability")
                }
                "AUTHENTICATE" -> {
                    val auth = parts.getOrElse(2) { "" }.split(' ', limit = 2)
                    val encoded = if (auth.size == 2) auth[1] else { send("+ "); reader.readLine().orEmpty() }
                    val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                    attempts.incrementAndGet()
                    if (auth[0].equals("XOAUTH2", true) && decoded.contains("auth=Bearer valid-runtime-token")) {
                        accepted.incrementAndGet()
                        send(tag + " OK authenticated")
                    } else {
                        rejected.incrementAndGet()
                        send(tag + " NO [AUTHENTICATIONFAILED] synthetic token expired")
                    }
                }
                "LIST", "LSUB" -> {
                    send("* LIST (\\HasNoChildren) \"/\" \"INBOX\"")
                    send(tag + " OK list")
                }
                "STATUS" -> {
                    send("* STATUS \"INBOX\" (MESSAGES 0 RECENT 0 UIDNEXT 1 UIDVALIDITY 1 UNSEEN 0)")
                    send(tag + " OK status")
                }
                "LOGOUT" -> { send("* BYE"); send(tag + " OK logout"); break }
                else -> send(tag + " OK completed")
            }
        }
    }

    override fun close() {
        server.close()
        sockets.forEach { runCatching { it.close() } }
        pool.shutdownNow()
    }
}
