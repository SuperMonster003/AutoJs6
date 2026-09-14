package org.autojs.autojs.core.http

import android.security.NetworkSecurityPolicy
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Runs against the target application's installed NSC, including release APKs. No external server or permissions needed. */
class CleartextTrafficDeviceTest {
    @Test fun installedPolicyAllowsScriptEndpointsAndKeepsGithubHttpsOnly() {
        val policy = NetworkSecurityPolicy.getInstance()
        for (host in listOf("script.sdk37.test", "192.0.2.1", "localhost", "127.0.0.1", "::1")) {
            assertTrue("HTTP policy for $host", policy.isCleartextTrafficPermitted(host))
        }
        for (host in listOf("githubusercontent.com", "raw.githubusercontent.com", "a.b.githubusercontent.com")) {
            assertFalse("HTTPS-only policy for $host", policy.isCleartextTrafficPermitted(host))
        }
        // A domain exception means the no-host query cannot report that ALL traffic is allowed.
        assertFalse(policy.isCleartextTrafficPermitted)
    }

    @Test fun httpToAnOrdinaryHostnameReceivesTheFixtureResponse() {
        HttpFixture().use { fixture ->
            fixture.client.newCall(Request.Builder().url(fixture.url("script.sdk37.test")).build()).execute().use {
                assertEquals(200, it.code)
                assertEquals(BODY, it.body!!.string())
            }
            assertEquals(1, fixture.requests.get())
        }
    }

    @Test fun githubHttpIsRejectedBeforeAnyRequestReachesTheServer() {
        HttpFixture().use { fixture ->
            for (host in listOf("githubusercontent.com", "raw.githubusercontent.com")) {
                val error = runCatching {
                    fixture.client.newCall(Request.Builder().url(fixture.url(host)).build()).execute().close()
                }.exceptionOrNull()
                assertNotNull("HTTP unexpectedly accepted $host", error)
                assertTrue("Expected cleartext policy failure, got $error",
                    error!!.message.orEmpty().contains("CLEARTEXT communication to $host not permitted"))
            }
            assertEquals(0, fixture.requests.get())
        }
    }

    @Test fun rhinoHttpUsesTheInstalledPolicy() {
        HttpFixture().use { fixture ->
            val done = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val listener = object : SimpleScriptExecutionListener() {
                override fun onSuccess(execution: ScriptExecution, result: Any?) { done.countDown() }
                override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error); done.countDown() }
            }
            val source = """
                var dns = new JavaAdapter(Packages.okhttp3.Dns, {
                    lookup: function (host) {
                        return java.util.Collections.singletonList(java.net.InetAddress.getByName("127.0.0.1"));
                    }
                });
                http.__okhttp__.muteClient(http.client().newBuilder().dns(dns).proxy(java.net.Proxy.NO_PROXY));
                var response = http.get("${fixture.url("script.sdk37.test")}", {timeout: 5000});
                if (response.statusCode !== 200 || response.body.string() !== "$BODY") throw new Error("HTTP fixture mismatch");
                var error = null;
                try { http.get("${fixture.url("raw.githubusercontent.com")}", {timeout: 5000}); } catch (e) { error = e; }
                if (!error || !/CLEARTEXT.*not permitted/.test(String(error))) throw new Error("Missing HTTPS-only rejection: " + error);
            """.trimIndent()
            val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("sdk37-cleartext", source), listener, ExecutionConfig())
            try {
                assertTrue("Rhino HTTP timed out", done.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("Rhino HTTP failed", it) }
                assertEquals(1, fixture.requests.get())
            } finally {
                (execution.engine as? RhinoJavaScriptEngine)?.let { if (it.thread.isAlive) it.forceStop() }
            }
        }
    }

    private class HttpFixture : Closeable {
        private val address = InetAddress.getByName("127.0.0.1")
        private val server = ServerSocket(0, 4, address)
        private val failure = AtomicReference<Throwable>()
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(address)
            })
            .proxy(Proxy.NO_PROXY)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        private val worker = thread(name = "sdk37-http-fixture", isDaemon = true) {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        var bytes = 0
                        while (true) {
                            val line = reader.readLine() ?: error("Incomplete HTTP request")
                            bytes += line.length
                            check(bytes <= 8192) { "HTTP headers exceeded fixture limit" }
                            if (line.isEmpty()) break
                        }
                        check(requests.incrementAndGet() <= 8) { "Too many fixture requests" }
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nConnection: close\r\n" +
                            "Content-Length: ${BODY.length}\r\n\r\n$BODY").toByteArray(Charsets.US_ASCII))
                    }
                }
            } catch (error: Exception) {
                if (!server.isClosed) failure.set(error)
            }
        }

        fun url(host: String) = "http://$host:${server.localPort}/cleartext"

        override fun close() {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            server.close()
            worker.join(6_000)
            check(!worker.isAlive) { "HTTP fixture did not stop" }
            failure.get()?.let { throw AssertionError("HTTP fixture failed", it) }
        }
    }

    companion object { private const val BODY = "sdk37-cleartext-ok" }
}
