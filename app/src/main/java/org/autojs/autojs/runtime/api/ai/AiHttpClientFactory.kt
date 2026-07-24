package org.autojs.autojs.runtime.api.ai

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Builds the credential-bearing AI client from the runtime HTTP client while severing every
 * inherited object that can expose, replay, persist, or interfere with an AI request.
 *
 * OkHttp's [OkHttpClient.newBuilder] intentionally shares its dispatcher and connection pool and
 * also inherits interceptors, event listeners, authenticators, cookie jars, and caches. The
 * runtime HTTP client is reachable from Rhino, so sharing any request-observing component would
 * make AI authentication headers observable through that public client.
 */
internal object AiHttpClientFactory {

    fun create(sourceClient: OkHttpClient): OkHttpClient {
        val proxySelector = AiProxySelector(
            explicitProxy = sourceClient.proxy,
            delegate = sourceClient.proxySelector,
        )
        return sourceClient.newBuilder().apply {
            dispatcher(Dispatcher())
            connectionPool(ConnectionPool())
            proxy(null)
            proxySelector(proxySelector)
            interceptors().clear()
            networkInterceptors().clear()
            eventListener(EventListener.NONE)
            authenticator(Authenticator.NONE)
            proxyAuthenticator(Authenticator.NONE)
            cookieJar(CookieJar.NO_COOKIES)
            cache(null)
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(false)
        }.build()
    }

    /**
     * Releases the execution resources owned exclusively by a client returned from [create].
     *
     * Calls derived with [OkHttpClient.newBuilder] share these objects with the base client, so
     * closing the base also terminates every per-request timeout variant used by [AiService].
     */
    fun close(client: OkHttpClient) {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

/**
 * Preserves the runtime's proxy policy for remote providers but never sends a loopback request
 * through a proxy. Loopback endpoints may intentionally use plain HTTP and may carry a local
 * provider credential, neither of which may be disclosed to a system or explicitly set proxy.
 */
private class AiProxySelector(
    private val explicitProxy: Proxy?,
    private val delegate: ProxySelector,
) : ProxySelector() {

    override fun select(uri: URI): List<Proxy> {
        if (uri.host?.let(AiCredentialPolicy::isLoopbackHost) == true) {
            return listOf(Proxy.NO_PROXY)
        }
        explicitProxy?.let { return listOf(it) }
        return delegate.select(uri).takeIf(List<Proxy>::isNotEmpty)
            ?: listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI, socketAddress: SocketAddress, error: IOException) {
        if (
            explicitProxy == null &&
            uri.host?.let(AiCredentialPolicy::isLoopbackHost) != true
        ) {
            delegate.connectFailed(uri, socketAddress, error)
        }
    }
}
