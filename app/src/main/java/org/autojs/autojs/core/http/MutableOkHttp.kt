package org.autojs.autojs.core.http

import android.annotation.SuppressLint
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Created by Stardust on Apr 11, 2018.
 * Modified by SuperMonster003 as of Sep 8, 2022.
 */
class MutableOkHttp : OkHttpClient() {

    private var mTimeout = DEFAULT_TIMEOUT
    private var mIsInsecure = DEFAULT_IS_INSECURE
    private var mOkHttpClient: OkHttpClient
    private val mInterceptors: MutableList<Interceptor> = ArrayList()

    @JvmField
    var maxRetries = DEFAULT_MAX_RETRIES

    var timeout: Long
        get() = mTimeout
        set(timeout) {
            mTimeout = timeout
            muteClient()
        }

    var isInsecure: Boolean
        get() = mIsInsecure
        set(isInsecure) {
            mIsInsecure = isInsecure
            muteClient()
        }

    init {
        mOkHttpClient = newClient(Builder())
        mInterceptors.add(getRetryInterceptor())
    }

    fun client() = mOkHttpClient

    private fun newClient(builder: Builder): OkHttpClient {
        mInterceptors.forEach { if (it !in builder.interceptors()) builder.addInterceptor(it) }
        return builder
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .also { b ->
                if (!isInsecure) return@also
                @SuppressLint("CustomX509TrustManager")
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, trustAllCerts, SecureRandom())
                }
                val trustManager = trustAllCerts.first() as X509TrustManager
                b.sslSocketFactory(sslContext.socketFactory, trustManager)
                b.hostnameVerifier { _, _ -> true }
            }
            .build()
    }

    @Synchronized
    fun muteClient(builder: Builder) {
        mOkHttpClient = newClient(builder)
    }

    @Synchronized
    private fun muteClient() = muteClient(mOkHttpClient.newBuilder())

    private fun getRetryInterceptor() = Interceptor { chain ->

        // @Reference to stackoverflow.com by SuperMonster003 on Apr 9, 2024.
        //  ! https://stackoverflow.com/questions/24562716/how-to-retry-http-requests-with-okhttp-retrofit

        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0
        while (!response.isSuccessful && tryCount < maxRetries) {
            Log.w(TAG, "Request is not successful - $tryCount")
            tryCount++
            response.close()
            response = chain.proceed(request)
        }
        response
    }

    @Suppress("MayBeConstant")
    companion object {

        private val TAG = MutableOkHttp::class.java.simpleName

        @JvmField
        val DEFAULT_TIMEOUT = 30 * 1000L

        @JvmField
        val DEFAULT_IS_INSECURE = false

        @JvmField
        val DEFAULT_MAX_RETRIES = 3

    }

}