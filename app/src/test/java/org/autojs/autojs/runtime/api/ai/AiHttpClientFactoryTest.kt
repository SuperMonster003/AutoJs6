package org.autojs.autojs.runtime.api.ai

import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AiHttpClientFactoryTest {

    @Test
    fun replacesInheritedRequestObserversAndSharedExecutionState() {
        val sourceDispatcher = Dispatcher()
        val sourceConnectionPool = ConnectionPool()
        val sourceEventListener = object : EventListener() {}
        val sourceCookieJar = object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }
        val source = OkHttpClient.Builder()
            .dispatcher(sourceDispatcher)
            .connectionPool(sourceConnectionPool)
            .addInterceptor(Interceptor { chain -> chain.proceed(chain.request()) })
            .addNetworkInterceptor(Interceptor { chain -> chain.proceed(chain.request()) })
            .eventListener(sourceEventListener)
            .authenticator(Authenticator { _, _ -> null })
            .proxyAuthenticator(Authenticator { _, _ -> null })
            .cookieJar(sourceCookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        val isolated = AiHttpClientFactory.create(source)
        val probeCall = isolated.newCall(
            Request.Builder().url("https://example.test/").build(),
        )

        assertNotSame(sourceDispatcher, isolated.dispatcher)
        assertNotSame(sourceConnectionPool, isolated.connectionPool)
        assertTrue(isolated.interceptors.isEmpty())
        assertTrue(isolated.networkInterceptors.isEmpty())
        assertSame(EventListener.NONE, isolated.eventListenerFactory.create(probeCall))
        assertSame(Authenticator.NONE, isolated.authenticator)
        assertSame(Authenticator.NONE, isolated.proxyAuthenticator)
        assertSame(CookieJar.NO_COOKIES, isolated.cookieJar)
        assertNull(isolated.cache)
        assertFalse(isolated.followRedirects)
        assertFalse(isolated.followSslRedirects)
        assertFalse(isolated.retryOnConnectionFailure)
    }

    @Test
    fun loopbackAlwaysBypassesExplicitProxyWhileRemoteRequestsKeepIt() {
        val explicitProxy = Proxy(
            Proxy.Type.HTTP,
            InetSocketAddress.createUnresolved("proxy.example", 8080),
        )
        val isolated = AiHttpClientFactory.create(
            OkHttpClient.Builder().proxy(explicitProxy).build(),
        )

        try {
            listOf(
                "http://localhost:11434/v1",
                "http://localhost.:11434/v1",
                "http://127.42.0.7:11434/v1",
                "http://[::1]:11434/v1",
                "http://[0:0:0:0:0:0:0:1]:11434/v1",
            ).forEach { url ->
                assertEquals(
                    "Loopback request must bypass proxies: $url",
                    listOf(Proxy.NO_PROXY),
                    isolated.proxySelector.select(URI(url)),
                )
            }
            assertEquals(
                listOf(explicitProxy),
                isolated.proxySelector.select(URI("https://api.example/v1")),
            )
        } finally {
            AiHttpClientFactory.close(isolated)
        }
    }

    @Test
    fun remoteRequestsDelegateToSourceProxySelector() {
        val delegatedProxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress.createUnresolved("proxy.example", 1080),
        )
        val selections = AtomicInteger()
        val delegate = object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                selections.incrementAndGet()
                return listOf(delegatedProxy)
            }

            override fun connectFailed(
                uri: URI,
                socketAddress: SocketAddress,
                error: IOException,
            ) = Unit
        }
        val isolated = AiHttpClientFactory.create(
            OkHttpClient.Builder().proxySelector(delegate).build(),
        )

        try {
            assertEquals(
                listOf(Proxy.NO_PROXY),
                isolated.proxySelector.select(URI("http://127.0.0.1:11434/v1")),
            )
            assertEquals(0, selections.get())
            assertEquals(
                listOf(delegatedProxy),
                isolated.proxySelector.select(URI("https://api.example/v1")),
            )
            assertEquals(1, selections.get())
        } finally {
            AiHttpClientFactory.close(isolated)
        }
    }

    @Test
    fun closeReleasesOwnedExecutionResourcesAndIsIdempotent() {
        val isolated = AiHttpClientFactory.create(OkHttpClient())
        val executor = isolated.dispatcher.executorService

        assertFalse(executor.isShutdown)
        AiHttpClientFactory.close(isolated)
        AiHttpClientFactory.close(isolated)

        assertTrue(executor.isShutdown)
        assertEquals(0, isolated.connectionPool.connectionCount())
    }

    @Test
    fun credentialBearingCallCannotBeEnumeratedFromSourceDispatcher() {
        val sourceEvents = AtomicInteger()
        val source = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    sourceEvents.incrementAndGet()
                }
            })
            .build()
        val isolatedBase = AiHttpClientFactory.create(source)
        val requestEntered = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val callFinished = CountDownLatch(1)
        val isolated = isolatedBase.newBuilder()
            .addInterceptor { chain ->
                requestEntered.countDown()
                check(releaseResponse.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to release the isolated request"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val secret = "sk-isolated-test-secret"
        val request = Request.Builder()
            .url("https://example.test/v1/chat/completions")
            .header("Authorization", "Bearer $secret")
            .header("x-api-key", secret)
            .header("x-goog-api-key", secret)
            .build()

        try {
            isolated.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callFinished.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    callFinished.countDown()
                }
            })
            assertTrue(
                "The isolated call did not enter its interceptor",
                requestEntered.await(5, TimeUnit.SECONDS),
            )

            val sourceCalls =
                source.dispatcher.queuedCalls() + source.dispatcher.runningCalls()
            val sourceVisibleHeaders = sourceCalls.flatMap { call ->
                listOf(
                    call.request().header("Authorization"),
                    call.request().header("x-api-key"),
                    call.request().header("x-goog-api-key"),
                )
            }
            val isolatedCalls =
                isolated.dispatcher.queuedCalls() + isolated.dispatcher.runningCalls()

            assertNotSame(source.dispatcher, isolated.dispatcher)
            assertTrue(sourceCalls.isEmpty())
            assertFalse(sourceVisibleHeaders.any { header -> header?.contains(secret) == true })
            assertTrue(
                isolatedCalls.any { call ->
                    call.request().header("Authorization") == "Bearer $secret"
                },
            )
            assertEquals(0, sourceEvents.get())
        } finally {
            releaseResponse.countDown()
            val finished = callFinished.await(5, TimeUnit.SECONDS)
            AiHttpClientFactory.close(isolatedBase)
            source.dispatcher.executorService.shutdownNow()
            source.connectionPool.evictAll()
            assertTrue(finished)
        }
    }
}
