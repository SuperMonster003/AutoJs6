package org.autojs.autojs.runtime.api.augment.http

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import okhttp3.Request
import org.autojs.autojs.core.http.MutableOkHttp
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.http.Http.Companion.DEFAULT_TIMEOUT
import org.autojs.autojs.runtime.api.augment.http.Http.Companion.KEY_CLIENT
import org.autojs.autojs.runtime.api.augment.http.Http.Companion.KEY_TIMEOUT
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.NativeObject
import java.lang.reflect.Method
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class RequestBuilder(
    private val scriptRuntime: ScriptRuntime,
    private val url: Any?,
    options: NativeObject = RhinoUtils.newNativeObject(),
) {

    private val mRequest = Request.Builder()
    private val mRequestBuilderHelper = RequestBuilderHelper(options)

    fun build(): Request {
        mRequest.url(mRequestBuilderHelper.getUrl(coerceString(url)))
        mRequestBuilderHelper.setHeaders(mRequest)
        mRequestBuilderHelper.setMethod(scriptRuntime, mRequest)
        return mRequest.build()
    }

    companion object {

        fun MutableOkHttp.applyOkHttpClientBuilder(opt: NativeObject) {
            val clientProp = opt.prop(KEY_CLIENT).takeUnless { it.isJsNullish() }
            require(clientProp is NativeObject?) { "Argument \"client\" ${clientProp.jsBrief()} for http.request must be a JavaScript Object" }

            val timeout = coerceLongNumber(opt.prop(KEY_TIMEOUT), DEFAULT_TIMEOUT)
            val isInsecure = opt.inquire(listOf("isInsecure", "insecure"), ::coerceBoolean, false)

            val builder = this.client().newBuilder()
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)

            val builderClass = OkHttpClient.Builder::class.java

            fun coerceArg(paramType: Class<*>, value: Any?) = when {
                paramType == java.lang.Boolean.TYPE || paramType == Boolean::class.java -> coerceBoolean(value, false)
                paramType == java.lang.Long.TYPE || paramType == Long::class.java -> coerceLongNumber(value, 0L)
                paramType == Integer.TYPE || paramType == Int::class.java -> coerceIntNumber(value, 0)
                paramType == java.lang.Double.TYPE || paramType == Double::class.java -> coerceNumber(value, 0.0)
                paramType == String::class.java -> coerceString(value, "")
                paramType == TimeUnit::class.java -> when (value) {
                    is TimeUnit -> value
                    is String -> runCatching { TimeUnit.valueOf(value.trim().uppercase()) }.getOrElse {
                        throw WrappedIllegalArgumentException("Invalid TimeUnit string: $value")
                    }
                    else -> throw WrappedIllegalArgumentException("Invalid TimeUnit argument: ${value.jsBrief()}")
                }
                // Java object: Proxy/Dispatcher/ConnectionPool/Authenticator/SSLSocketFactory/...
                paramType.isInstance(value) -> value
                // Let reflection verify the type matching by itself.
                // zh-CN: 让反射自行校验类型是否匹配.
                else -> value
            }

            fun findCandidateMethods(name: String): List<Method> {
                return builderClass.methods.filter { it.name == name && it.declaringClass == builderClass }
            }

            clientProp?.forEach { entry ->
                val (rawKey, rawVal) = entry
                val methodName = coerceString(rawKey, "").trim()
                if (methodName.isEmpty()) return@forEach

                val candidates = findCandidateMethods(methodName)
                require(candidates.isNotEmpty()) {
                    "No such Builder method: $methodName on ${builderClass.name}"
                }

                val zeroArg = candidates.firstOrNull { it.parameterCount == 0 }
                require(zeroArg == null) {
                    "Builder method \"$methodName\" with 0 parameter is not allowed to be invoked via client options"
                }
                val twoArgs = candidates.firstOrNull { it.parameterCount == 2 }
                if (twoArgs != null && rawVal is List<*> && rawVal.size == 2) {
                    val args2 = arrayOf(coerceArg(twoArgs.parameterTypes[0], rawVal[0]), coerceArg(twoArgs.parameterTypes[1], rawVal[1]))
                    twoArgs.invoke(builder, args2[0], args2[1])
                    return@forEach
                }

                val oneArg = candidates.firstOrNull { it.parameterCount == 1 }
                if (oneArg != null) {
                    val paramTypes = oneArg.parameterTypes
                    val arg0 = coerceArg(paramTypes[0], rawVal)
                    oneArg.invoke(builder, arg0)
                    return@forEach
                }

                val supported = candidates.joinToString { "(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                throw WrappedIllegalArgumentException(
                    "Builder method \"$methodName\" is not invokable with 1 or 2 parameters via client options. Supported overloads: $supported"
                )
            }

            if (isInsecure) {
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
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
                builder.hostnameVerifier { _, _ -> true }
            }

            // Apply the new Builder to the internal client.
            // zh-CN: 应用新的 Builder 到内部客户端.
            muteClient(builder)
        }

    }

}