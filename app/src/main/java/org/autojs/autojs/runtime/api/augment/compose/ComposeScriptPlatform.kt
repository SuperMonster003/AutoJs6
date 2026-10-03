package org.autojs.autojs.runtime.api.augment.compose

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.compose.*
import org.autojs.autojs.execution.ScriptExecuteActivity
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ScriptDispatchQueueOverflowException
import org.autojs.autojs.runtime.api.augment.ui.UI
import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.UiValue
import java.io.Closeable
import java.util.concurrent.Executor

/** JVM-testable platform boundary. All script values and scheduling remain in the owning runtime. */
internal interface ComposeScriptPlatform : Closeable {
    data class Opened(val renderer: ComposeSessionRenderer, val components: Set<String>,
        val window: ComposeFloatyControl? = null, val lifetime: Closeable = Closeable {})
    val dispatcher: ScriptUiDispatcher
    val mainExecutor: Executor
    fun version(): Map<String, Any>?
    fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): Opened
    fun createFloatyRenderer(sessionId: Int, options: ComposeFloatyOptions, beforeMount: (Any) -> Unit,
        onDestroyed: () -> Unit, onFailure: (Throwable) -> Unit): Opened =
        throw ComposeUiContractException(ComposeUiErrorCodes.PLUGIN_UNAVAILABLE)
    fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage
    fun post(delayMillis: Long, action: () -> Unit)
    fun warn(message: String)
    fun report(error: Throwable)
    fun errorMessage(error: ComposeUiError): String = error.message ?: error.code
}

internal class AndroidComposeScriptPlatform(private val runtime: ScriptRuntime) : ComposeScriptPlatform {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    private val host by lazy { ComposeUiPluginHost(GlobalAppContext.get()) }
    private var hostCreated = false
    private val delayed = Handler(Looper.getMainLooper())
    private val delayToken = Any()
    override val dispatcher: ScriptUiDispatcher = ScriptUiDispatcher.forRuntime(runtime,
        Looper.myLooper() == Looper.getMainLooper(), keepAlive = false)
    override val mainExecutor: Executor get() = ScriptUiDispatcher.mainExecutor()
    private fun plugin(): ComposeUiPluginLoader.Loaded { hostCreated = true; return host.load() }
    private fun activity() = runtime.topLevelScope.prop("activity").jsUnwrapped() as? ScriptExecuteActivity

    @Suppress("DEPRECATION")
    override fun version(): Map<String, Any>? = try {
        val loaded = plugin()
        val version = loaded.pluginContext.packageManager.getPackageInfo(loaded.identity.name, 0).versionName
            ?: loaded.identity.version.toString()
        mapOf("plugin" to version, "contract" to loaded.factory.contractVersion(),
            "compose" to requireNotNull(loaded.factory.capabilities().getString(ComposeUiCapabilityKeys.COMPOSE_VERSION)))
    } catch (_: Exception) { null }
    catch (_: LinkageError) { null }

    override fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): ComposeScriptPlatform.Opened {
        val activity = activity() ?: throw ComposeUiError(ComposeUiErrorCodes.UI_MODE_REQUIRED,
            GlobalAppContext.get().getString(R.string.error_compose_ui_mode_required))
        if (Looper.myLooper() != Looper.getMainLooper()) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "compose.mount requires the UI script dispatcher")
        val loaded = plugin()
        val renderer = ComposeAndroidRenderer(activity, loaded, sessionId, { view ->
            beforeMount(view)
            UI.setContentViewRhinoRuntime(runtime, view)
        }, activity, onDestroyed = onDestroyed)
        return ComposeScriptPlatform.Opened(renderer, loaded.components)
    }

    override fun createFloatyRenderer(sessionId: Int, options: ComposeFloatyOptions, beforeMount: (Any) -> Unit,
        onDestroyed: () -> Unit, onFailure: (Throwable) -> Unit): ComposeScriptPlatform.Opened {
        try { runtime.floaty.ensurePermission() }
        catch (failure: Exception) { throw ComposeUiError(ComposeUiErrorCodes.PERMISSION_REQUIRED, cause = failure) }
        val loaded = plugin()
        // A non-UI script remains alive only while it owns a floating session. Releasing this token
        // is thread-safe and never waits for Android main or another script dispatcher.
        val token = runtime.loopers.waitWhenIdleToken()
        val lifetime = Closeable { token.release() }
        try {
            val renderer = AndroidComposeFloatyRenderer(runtime, loaded, sessionId, options, beforeMount, onDestroyed, onFailure)
            return ComposeScriptPlatform.Opened(renderer, loaded.components, renderer.window, lifetime)
        } catch (failure: Throwable) { lifetime.close(); throw failure }
    }

    override fun errorMessage(error: ComposeUiError): String = error.localizedMessage(GlobalAppContext.get())

    override fun resolveImage(value: Any): ComposeScriptValues.ResolvedImage {
        val owner: Any = when (value) {
            is ImageWrapper -> value
            is android.graphics.Bitmap -> value
            is String -> if (value.startsWith("@drawable/")) {
                val context = activity() ?: GlobalAppContext.get()
                val name = value.removePrefix("@drawable/")
                @Suppress("DiscouragedApi")
                val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                if (id == 0) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Unknown host drawable", prop = "src")
                (ContextCompat.getDrawable(context, id) ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "src")).toBitmap()
            } else {
                if (value.contains("://")) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Image paths must be local", prop = "src")
                runtime.images.read(runtime.files.path(value))
                    ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Cannot decode image", prop = "src")
            }
            else -> throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Image requires ImageWrapper, path or @drawable/name", prop = "src")
        }
        val bitmap = if (owner is ImageWrapper) owner.bitmap else owner as android.graphics.Bitmap
        return ComposeScriptValues.ResolvedImage(UiValue.BitmapRef(bitmap), owner)
    }

    override fun post(delayMillis: Long, action: () -> Unit) {
        // Capture/create the dispatcher on its script thread before callers use compose.post from workers.
        if (closed.get()) return
        val target = dispatcher
        fun enqueue() {
            try { target.post(action) }
            catch (failure: ScriptDispatchQueueOverflowException) {
                throw ComposeUiError(ComposeUiErrorCodes.LIMIT_EXCEEDED, prop = "post", cause = failure)
            }
        }
        if (delayMillis == 0L) enqueue()
        else delayed.postAtTime({
            if (!closed.get()) try { enqueue() }
            catch (failure: ComposeUiError) { report(failure) }
        }, delayToken, android.os.SystemClock.uptimeMillis() + delayMillis)
    }
    override fun warn(message: String) { runtime.console.warn(message) }
    override fun report(error: Throwable) {
        val scriptError = (error as? org.mozilla.javascript.JavaScriptException)?.value as? org.mozilla.javascript.Scriptable
        val scriptCode = scriptError?.takeIf { ComposeJsErrors.constructor(runtime.topLevelScope).hasInstance(it) }
            ?.let { org.mozilla.javascript.ScriptableObject.getProperty(it, "code") as? String }
        val code = (error as? ComposeUiError)?.code ?: (error as? ComposeUiContractException)?.code ?: scriptCode ?: ComposeUiErrorCodes.RENDER_FAILED
        val scriptFailure = generateSequence(error) { it.cause }.filterIsInstance<org.mozilla.javascript.RhinoException>().firstOrNull()
        val stack = scriptFailure?.scriptStackTrace?.takeIf { it.isNotBlank() }
        runtime.console.error("Compose UI: $code" + (stack?.let { "\n$it" } ?: ""))
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        delayed.removeCallbacksAndMessages(delayToken)
        if (hostCreated) host.close()
        dispatcher.close()
    }
}
