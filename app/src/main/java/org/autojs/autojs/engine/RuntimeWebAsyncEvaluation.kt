package org.autojs.autojs.engine

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.json.JSONTokener
import java.io.Closeable
import java.util.UUID

/** Await page Promises without retaining a Binder call or exposing a JavaScript interface. */
internal object RuntimeWebAsyncEvaluation {
    fun start(web: RuntimeWebView, source: String, timeoutMs: Long, complete: (Result<Any?>) -> Unit): Closeable {
        check(!web.disposed) { "WebView is disposed" }
        require(web.settings.javaScriptEnabled) { "evaluateJavascriptAsync requires javaScriptEnabled" }
        val main = Handler(Looper.getMainLooper())
        val key = JSONObject.quote("__autojs6_result_" + UUID.randomUUID().toString().replace("-", ""))
        var done = false
        lateinit var poll: Runnable
        lateinit var timeout: Runnable
        fun finish(result: Result<Any?>) {
            if (done) return
            done = true
            main.removeCallbacks(poll); main.removeCallbacks(timeout)
            if (!web.disposed) runCatching { web.evaluateJavascript("delete window[$key]", null) }
            complete(result)
        }
        timeout = Runnable { finish(Result.failure(IllegalStateException("WebView asynchronous JavaScript result timed out"))) }
        poll = Runnable {
            if (done) return@Runnable
            try {
                web.evaluateJavascript("window[$key] || null") { raw ->
                    if (!done) {
                        try {
                            val value = JSONTokener(raw ?: "null").nextValue()
                            check(value is JSONObject) { "WebView asynchronous evaluation lost its page" }
                            if (!value.optBoolean("done")) main.postDelayed(poll, 25)
                            else if (value.has("error")) finish(Result.failure(IllegalStateException(value.getString("error"))))
                            else finish(Result.success(value.opt("value")))
                        } catch (error: Throwable) { finish(Result.failure(error)) }
                    }
                }
            } catch (error: Throwable) { finish(Result.failure(error)) }
        }
        val script = """
            (() => {
              const state = {done:false};
              Object.defineProperty(window, $key, {value:state, configurable:true});
              const fail = error => { if (window[$key] === state) { state.error = String(error && error.name || 'Error') + ': ' + String(error && error.message || error); state.done = true; } };
              Promise.resolve().then(() => (0, eval)(${JSONObject.quote(source)})).then(value => {
                if (window[$key] !== state) return;
                try { state.value = value === undefined ? null : JSON.parse(JSON.stringify(value)); state.done = true; } catch (error) { fail(error); }
              }, fail);
              return true;
            })()
        """.trimIndent()
        main.postDelayed(timeout, timeoutMs)
        try { web.evaluateJavascript(script) { if (!done) main.post(poll) } }
        catch (error: Throwable) { finish(Result.failure(error)) }
        return Closeable { finish(Result.failure(IllegalStateException("WebView asynchronous evaluation cancelled"))) }
    }
}
