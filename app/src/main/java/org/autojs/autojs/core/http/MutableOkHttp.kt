package org.autojs.autojs.core.http

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Created by Stardust on Apr 11, 2018.
 * Modified by SuperMonster003 as of Sep 8, 2022.
 */
class MutableOkHttp : OkHttpClient() {

    private var mTimeout = DEFAULT_TIMEOUT
    private var mIsInsecure = DEFAULT_IS_INSECURE
    private lateinit var mOkHttpClient: OkHttpClient
    private val mInterceptors: MutableList<Interceptor> = ArrayList()

    @JvmField
    var maxRetries = DEFAULT_MAX_RETRIES

    init {
        mInterceptors.add(getRetryInterceptor())
        muteClient(Builder())
    }

    fun client() = mOkHttpClient

    @Synchronized
    fun muteClient(builder: Builder) {
        mInterceptors.forEach { if (it !in builder.interceptors()) builder.addInterceptor(it) }
        mOkHttpClient = builder.build()
    }

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