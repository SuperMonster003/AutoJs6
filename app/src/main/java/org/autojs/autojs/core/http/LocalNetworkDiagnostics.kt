package org.autojs.autojs.core.http

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs6.R
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Adds recovery advice after transport failures, without changing routing or blocking requests. */
class LocalNetworkDiagnostics(
    private val context: () -> Context = { GlobalAppContext.get() },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request())
        } catch (error: IOException) {
            if (chain.call().isCanceled()) throw error
            throw explain(context(), chain.request().url.host, error)
        }
    }

    companion object {

        fun withDiagnostics(client: OkHttpClient): OkHttpClient =
            if (client.interceptors.any { it is LocalNetworkDiagnostics }) client
            else client.newBuilder().addInterceptor(LocalNetworkDiagnostics()).build()

        fun explain(context: Context, host: String?, error: IOException): IOException {
            if (error is NetworkPermissionHintException || LocalNetworkPermission.isGranted(context) ||
                !couldBeLocalHost(host) || !isConnectionFailure(error)
            ) return error
            return NetworkPermissionHintException(
                "${error.message ?: error.javaClass.simpleName}\n${context.getString(R.string.error_local_network_permission_hint)}",
                error,
            )
        }

        internal fun isConnectionFailure(error: IOException) =
            error is SocketException || error is SocketTimeoutException || error is UnknownHostException || isCallTimeout(error)

        // OkHttp's whole-call timeout cancels the Call internally, then wraps the transport
        // exception in InterruptedIOException("timeout"). isCanceled() alone is not an abort.
        internal fun isCallTimeout(error: IOException) =
            error is InterruptedIOException && error !is SocketTimeoutException && error.message == "timeout"

        /**
         * A hint, not the platform's authorization decision. Names may resolve locally and IPv6
         * reachability depends on routes. Cellular, VPN and picker-granted access can still work
         * without the broad permission, so this predicate must never gate script traffic.
         */
        internal fun couldBeLocalHost(host: String?): Boolean {
            if (host.isNullOrBlank()) return false
            val name = host.trim().removePrefix("[").removeSuffix("]").trimEnd('.')
            if (name.equals("localhost", ignoreCase = true) || name.endsWith(".localhost", ignoreCase = true)) return false
            val numeric = name.contains(':') || name.all { it.isDigit() || it == '.' }
            if (!numeric) return true
            val address = runCatching { InetAddress.getByName(name) }.getOrNull() ?: return false
            if (address.isLoopbackAddress || address.isAnyLocalAddress) return false
            val bytes = address.address
            if (bytes.size == 16) return true // IPv6 directly connected routes can use global addresses.
            val first = bytes[0].toInt() and 255
            val second = bytes[1].toInt() and 255
            return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress ||
                    (first == 100 && second in 64..127) || bytes.all { (it.toInt() and 255) == 255 }
        }

        private class NetworkPermissionHintException(message: String, cause: IOException) : IOException(message, cause)
    }
}
