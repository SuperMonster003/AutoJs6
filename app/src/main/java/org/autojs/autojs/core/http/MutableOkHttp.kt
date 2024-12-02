package org.autojs.autojs.core.http

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Created by Stardust on Apr 11, 2018.
 * Modified by SuperMonster003 as of Sep 8, 2022.
 */
class MutableOkHttp : OkHttpClient() {

    private var mTimeout = DEFAULT_TIMEOUT
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

    companion object {

        private val TAG = MutableOkHttp::class.java.simpleName

        @JvmField
        @Suppress("MayBeConstant")
        val DEFAULT_TIMEOUT = 30 * 1000L

        @JvmField
        @Suppress("MayBeConstant")
        val DEFAULT_MAX_RETRIES = 3

    }

}