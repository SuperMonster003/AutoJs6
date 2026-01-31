package org.autojs.autojs.runtime.api.augment.http

import okhttp3.ResponseBody
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.toRuntimePath
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.getRhinoStandardFunctionMethods
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.io.InputStream
import java.io.OutputStream
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

@Suppress("unused")
class ResponseBodyNativeObject(
    val scriptRuntime: ScriptRuntime,
    val resBody: ResponseBody,
    val responseWrapper: ResponseWrapper,
    private val cacheBody: Boolean,
    private val cacheThresholdBytes: Long,
) : NativeObject() {

    private val mResBodyObject: Scriptable by lazy {
        RhinoScriptRuntime.toObject(scriptRuntime.topLevelScope, resBody)
    }

    init {
        RhinoUtils.initNativeObjectPrototype(this)
    }

    // Record whether it has been explicitly closed.
    // zh-CN: 记录是否已显式关闭.
    @Volatile
    var closed = false

    override fun has(name: String, start: Scriptable): Boolean {
        return mResBodyObject.has(name, start) || super.has(name, start)
    }

    override fun get(name: String, start: Scriptable): Any? {
        val rhinoMethods = Companion::class.java.getRhinoStandardFunctionMethods()
        rhinoMethods.firstOrNull { it.name == name }?.let {
            return super.get(name, start)
        }
        return when (val o = mResBodyObject.prop(name)) {
            is BaseFunction -> withRhinoContext(scriptRuntime) { cx ->
                BoundFunction(cx, mResBodyObject, o, mResBodyObject, arrayOf())
            }
            else -> super.get(name, start)
        }
    }

    fun ensureOpen() {
        if (closed) throw IllegalStateException("Response body already closed")
    }

    fun autoCloseIfNeeded() {
        // Close immediately after reading the complete content to avoid resource leaks;
        // stream() close is handled by the caller.
        // zh-CN: 读取完整内容后立即关闭, 避免资源泄露; stream() 由调用者负责 close().
        if (!closed) {
            runCatching { resBody.close() }
            closed = true
        }
    }

    fun shouldCache(lengthHint: Long?): Boolean {
        if (!cacheBody) return false
        if (lengthHint == null || lengthHint < 0) return true
        return lengthHint <= cacheThresholdBytes
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun string(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
            val o = thisObj as ResponseBodyNativeObject
            o.responseWrapper.resBodyString?.let { return@ensureArgumentsIsEmpty it }
            o.ensureOpen()
            val contentLength = runCatching {
                o.resBody.contentLength()
            }.getOrDefault(-1L)
            val str = o.resBody.string()
            if (o.shouldCache(contentLength)) {
                o.responseWrapper.resBodyString = str
            }
            // string() can safely close after consuming the stream.
            // zh-CN: string() 消费流后可安全关闭.
            o.autoCloseIfNeeded()
            return@ensureArgumentsIsEmpty str
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun bytes(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ByteArray = ensureArgumentsIsEmpty(args) {
            val o = thisObj as ResponseBodyNativeObject
            o.responseWrapper.resBodyBytes?.let { return@ensureArgumentsIsEmpty it }
            o.ensureOpen()
            val contentLength = runCatching {
                o.resBody.contentLength()
            }.getOrDefault(-1L)
            val data = o.resBody.bytes()
            if (o.shouldCache(contentLength)) {
                o.responseWrapper.resBodyBytes = data
            }
            // bytes() 消费流后可安全关闭
            o.autoCloseIfNeeded()
            return@ensureArgumentsIsEmpty data
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun json(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsIsEmpty(args) {
            val str = string(cx, thisObj, args, funObj)
            runCatching {
                return@ensureArgumentsIsEmpty RhinoUtils.js_json_parse(str)
            }.onFailure {
                throw IllegalStateException("Failed to parse JSON. Body string may be not in JSON format")
            }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun stream(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): InputStream = ensureArgumentsIsEmpty(args) {
            val o = thisObj as ResponseBodyNativeObject
            o.ensureOpen()
            // Don't auto-close; let the caller handle it, supporting streaming copy.
            // zh-CN: 不自动关闭; 交给调用者处理, 支持流式拷贝.
            o.resBody.byteStream()
        }

        // Save directly to file (avoid loading large responses into memory).
        // zh-CN: 直接保存到文件 (避免将大型响应加载到内存中).
        @JvmStatic
        @RhinoStandardFunctionInterface
        fun saveToFile(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): HttpSaveResult = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val o = thisObj as ResponseBodyNativeObject
            o.ensureOpen()

            val (pathRaw, bufSizeRaw) = argList
            val path = RhinoUtils.coerceString(pathRaw, "").toRuntimePath(o.scriptRuntime)
            val bufSize = RhinoUtils.coerceIntNumber(bufSizeRaw, 0).let {
                if (it > 0) it else 8192
            }

            val file = PFile(path)
            val isDirectory = file.isDirectory || path.endsWith("/")
            if (isDirectory) {
                throw WrappedIllegalArgumentException("Path \"$path\" must be a file path instead of a directory path")
            }
            val buffer = ByteArray(bufSize)
            var copied = 0L
            var input: InputStream? = null
            var output: OutputStream? = null
            return@ensureArgumentsLengthInRange try {
                input = o.resBody.byteStream()
                output = file.outputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    copied += read
                }
                output.flush()
                HttpSaveResult.ok(path, copied)
            } catch (e: Throwable) {
                HttpSaveResult.fail(path, copied, e)
            } finally {
                runCatching { input?.close() }
                runCatching { output?.close() }
                o.autoCloseIfNeeded()
            }
        }

        // Explicit close.
        // zh-CN: 显式关闭.
        @JvmStatic
        @RhinoStandardFunctionInterface
        fun close(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsIsEmpty(args) {
            val o = thisObj as ResponseBodyNativeObject
            if (!o.closed) {
                runCatching { o.resBody.close() }
                o.closed = true
            }
            RhinoUtils.UNDEFINED
        }
    }

}