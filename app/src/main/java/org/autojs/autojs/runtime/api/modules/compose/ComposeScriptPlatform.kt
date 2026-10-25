package org.autojs.autojs.runtime.api.modules.compose

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
import org.autojs.autojs.runtime.api.modules.ScriptDispatchQueueOverflowException
import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.UiValue
import java.io.Closeable
import java.util.concurrent.Executor

/** JVM-testable platform boundary. All script values and scheduling remain in the owning runtime. */
internal interface ComposeScriptPlatform : Closeable {
    data class Opened(val renderer: ComposeSessionRenderer, val components: Set<String>,
        val window: ComposeFloatyControl? = null, val lifetime: Closeable = Closeable {},
        val watchInvalidation: (((Throwable) -> Unit) -> Closeable)? = null)
    val dispatcher: ScriptUiDispatcher
    val mainExecutor: Executor
    fun version(): Map<String, Any>?
    fun createRenderer(sessionId: Int, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): Opened
    fun container(value: Any?): Any = throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
    fun createContainerRenderer(sessionId: Int, container: Any, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): Opened =
        throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
    fun createDialogRenderer(sessionId: Int, options: ComposeDialogOptions, beforeMount: (Any) -> Unit,
        onDestroyed: () -> Unit, onFailure: (Throwable) -> Unit): Opened =
        throw ComposeUiContractException(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE)
    fun prepareView(value: Any?, factory: (org.mozilla.javascript.Function, Any) -> Any?): View =
        throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "view")
    fun validateViews(views: List<org.autojs.plugin.compose.api.interop.AndroidViewBindingV1>, container: Any?, renderer: ComposeSessionRenderer) = Unit
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
    // Captured during engine initialization, before user code can reassign the global alias.
    private val ownerEngine = runtime.engines.myEngine()
    private val activityOwner = java.lang.ref.WeakReference(ownerEngine.getTag("activity") as? ScriptExecuteActivity)
    private val uiMode = activityOwner.get() != null ||
        (((ownerEngine.source as? org.autojs.autojs.script.JavaScriptSource)?.executionMode ?: 0) and
            org.autojs.autojs.script.JavaScriptSource.EXECUTION_MODE_UI != 0)
    private val host by lazy { ComposeUiPluginHost(GlobalAppContext.get()) }
    private var hostCreated = false
    private val delayed = Handler(Looper.getMainLooper())
    private val delayToken = Any()
    override val dispatcher: ScriptUiDispatcher = ScriptUiDispatcher.forRuntime(runtime,
        Looper.myLooper() == Looper.getMainLooper(), keepAlive = false)
    override val mainExecutor: Executor get() = ScriptUiDispatcher.mainExecutor()
    private fun plugin(): ComposeUiPluginLoader.Loaded { hostCreated = true; return host.load() }
    private fun activity() = runtime.topLevelScope.prop("activity").jsUnwrapped() as? ScriptExecuteActivity
    private fun ownedActivity(): ScriptExecuteActivity? {
        if (!uiMode) return null
        return activityOwner.get()?.takeIf {
            ownerEngine.getTag("activity") === it && !it.isFinishing && !it.isDestroyed
        } ?: throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED,
            "The owning UI Activity is no longer available")
    }

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
            // Already on the owning UI thread. Keep attach failures in the Compose session's
            // error boundary; the generic ui helper terminates UI scripts on callback exceptions.
            runtime.beforeSetUiContent(view)
            runtime.ui.view = view
            activity.setContentView(view)
        }, activity, onDestroyed = onDestroyed)
        return ComposeScriptPlatform.Opened(renderer, loaded.components,
            watchInvalidation = { invalidated -> host.watch(loaded.identity, invalidated) })
    }

    override fun container(value: Any?): Any {
        val target = ComposeScriptValues.unwrap(value) as? org.autojs.autojs.core.ui.widget.ComposeContainerView
        if (target == null || !target.belongsTo(runtime)) throw ComposeUiContractException(
            ComposeUiErrorCodes.INVALID_ARGUMENT, "attach requires this runtime's XML compose container")
        return target
    }

    override fun createContainerRenderer(sessionId: Int, container: Any, beforeMount: (Any) -> Unit, onDestroyed: () -> Unit): ComposeScriptPlatform.Opened {
        val loaded = plugin()
        val target = container as org.autojs.autojs.core.ui.widget.ComposeContainerView
        val token = runtime.loopers.waitWhenIdleToken()
        val lifetime = Closeable { token.release() }
        try {
            return ComposeScriptPlatform.Opened(AndroidComposeContainerRenderer(target, loaded, sessionId, beforeMount, onDestroyed),
                loaded.components, lifetime = lifetime, watchInvalidation = { invalidated -> host.watch(loaded.identity, invalidated) })
        } catch (failure: Throwable) { lifetime.close(); throw failure }
    }

    override fun createDialogRenderer(sessionId: Int, options: ComposeDialogOptions, beforeMount: (Any) -> Unit,
        onDestroyed: () -> Unit, onFailure: (Throwable) -> Unit): ComposeScriptPlatform.Opened {
        val loaded = plugin()
        val factory = loaded.dialogFactory ?: throw ComposeUiContractException(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE,
            "The installed Compose provider does not support dialog presentation")
        val activity = ownedActivity()
        if (activity != null) {
            if (Looper.myLooper() != Looper.getMainLooper()) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
            if (activity.isFinishing || activity.isDestroyed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
            val renderer = ComposeAndroidRenderer(activity, loaded, sessionId, { anchor ->
                if (activity.isFinishing || activity.isDestroyed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
                anchor.alpha = 0f; anchor.isFocusable = false; anchor.isClickable = false
                anchor.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                beforeMount(anchor)
                // ComponentActivity normally installs these from setContentView/addContentView.
                // A dialog may be the first UI in this Activity; direct decor attachment needs
                // the same public initialization without replacing the underlying content.
                activity.initializeViewTreeOwners()
                // A normal ui.layout replacement must leave this independent dialog alive.
                (activity.window.decorView as android.view.ViewGroup).addView(anchor, android.view.ViewGroup.LayoutParams(1, 1))
            }, activity, onDestroyed = onDestroyed, rendererFactory = { environment ->
                factory.create(environment, org.autojs.plugin.compose.api.dialog.DialogOptionsV1(options.type, options.cancelable,
                    // Let the Activity-backed WindowManager supply the application token. A
                    // decor's IWindow binder is not an application-window ownership token.
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION, null))
            }, useActivityInsets = false)
            return ComposeScriptPlatform.Opened(renderer, loaded.components,
                watchInvalidation = { invalidated -> host.watch(loaded.identity, invalidated) })
        }
        try { runtime.floaty.ensurePermission() }
        catch (failure: Exception) { throw ComposeUiError(ComposeUiErrorCodes.PERMISSION_REQUIRED, cause = failure) }
        val token = runtime.loopers.waitWhenIdleToken()
        val lease = Closeable { token.release() }
        try {
            val renderer = AndroidComposeFloatyRenderer(runtime, loaded, sessionId,
                ComposeFloatyOptions(raw = true, x = 0, y = 0, width = 1, height = 1, touchable = false, focusable = false),
                beforeMount, onDestroyed, onFailure, rendererFactory = { environment ->
                    factory.create(environment, org.autojs.plugin.compose.api.dialog.DialogOptionsV1(options.type, options.cancelable,
                        org.autojs.autojs.ui.enhancedfloaty.util.WindowTypeCompat.getPhoneWindowType(), null))
                }, compositionAnchor = true)
            return ComposeScriptPlatform.Opened(renderer, loaded.components, renderer.window, lease,
                watchInvalidation = { invalidated -> host.watch(loaded.identity, invalidated) })
        } catch (failure: Throwable) { lease.close(); throw failure }
    }

    override fun prepareView(value: Any?, factory: (org.mozilla.javascript.Function, Any) -> Any?): View = onMain {
        val raw = ComposeScriptValues.unwrap(value)
        val resolved = if (raw is org.mozilla.javascript.Function) factory(raw,
            ownedActivity() ?: android.view.ContextThemeWrapper(GlobalAppContext.get(), R.style.ScriptTheme)) else raw
        ComposeScriptValues.unwrap(resolved) as? View ?: throw ComposeUiContractException(
            ComposeUiErrorCodes.INVALID_ARGUMENT, "AndroidView requires a View or a factory returning a View", prop = "view")
    }

    override fun validateViews(views: List<org.autojs.plugin.compose.api.interop.AndroidViewBindingV1>, container: Any?, renderer: ComposeSessionRenderer) {
        if (views.isEmpty()) return
        onMain {
            renderer.validateViews(views)
            views.forEach { binding ->
                var ancestor = container as? View
                while (ancestor != null) {
                    if (ancestor === binding.view) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
                        "An AndroidView cannot contain its Compose container", binding.nodeId, "view")
                    ancestor = ancestor.parent as? View
                }
            }
        }
    }

    /** Only the calling script waits. Main never calls back into, or waits for, its dispatcher. */
    private fun <T> onMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val done = runtime.threads.disposable()
        var result: T? = null
        var failure: Throwable? = null
        runtime.uiHandler.post {
            try { if (!closed.get()) result = action() else throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED) }
            catch (error: Throwable) { failure = error }
            finally { done.setAndNotify(true) }
        }
        done.blockedGet()
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
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
            return ComposeScriptPlatform.Opened(renderer, loaded.components, renderer.window, lifetime,
                watchInvalidation = { invalidated -> host.watch(loaded.identity, invalidated) })
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
        runtime.console.error("3-Block Compose: $code" + (stack?.let { "\n$it" } ?: ""))
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        delayed.removeCallbacksAndMessages(delayToken)
        if (hostCreated) host.close()
        dispatcher.close()
    }
}
