package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.os.Bundle
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.*
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.interop.AndroidViewBindingV1
import org.autojs.plugin.compose.api.interop.AndroidViewRendererV1
import org.autojs.plugin.compose.api.interop.AndroidViewClaimsV1
import java.io.Closeable

/** Main-thread adapter. Script code owns the session; Activity/window owners own attachment. */
class ComposeAndroidRenderer(
    context: Context,
    private val loaded: ComposeUiPluginLoader.Loaded,
    private val sessionId: Int,
    mount: (View) -> Unit,
    lifecycleOwner: LifecycleOwner? = null,
    private val floatyOwners: ComposeFloatyOwners? = null,
    onDestroyed: () -> Unit,
    private var rendererFactory: ((ComposeUiHostEnvironment) -> ComposeUiRenderer)? = null,
    private val useActivityInsets: Boolean = true,
) : ComposeSessionRenderer, DefaultLifecycleObserver {
    private var context: Context? = context
    private var mount: ((View) -> Unit)? = mount
    private var lifecycleOwner: LifecycleOwner? = lifecycleOwner
    private var onDestroyed: (() -> Unit)? = onDestroyed
    private var renderer: ComposeUiRenderer? = null
    private var bridge: ComposeThemeBridge? = null
    private var overrideTheme: ThemeSpec? = null
    private var disposed = false
    private var generation = 0L
    private var insetsLease: Closeable? = null

    override fun open(sink: ComposeUiEventSink) {
        check(!disposed && renderer == null)
        lifecycleOwner?.lifecycle?.addObserver(this)
        val base = requireNotNull(context)
        val theme = ComposeThemeBridge(base) {
            try { renderer?.setTheme(resolveComposeTheme(it, overrideTheme)) }
            catch (failure: Exception) { themeError(sink, failure) }
            catch (failure: LinkageError) { themeError(sink, failure) }
        }.also { bridge = it }
        val environment = object : ComposeUiHostEnvironment {
            override val hostContext = loaded.context(base)
            override val mainExecutor = ScriptUiDispatcher.mainExecutor()
            override val eventSink = sink
            override val initialTheme = theme.snapshot()
            override val sessionId = this@ComposeAndroidRenderer.sessionId
        }
        try { renderer = rendererFactory?.invoke(environment) ?: loaded.androidViewFactory?.create(environment) ?: loaded.factory.create(environment) }
        catch (error: Throwable) { dispose(); throw error }
    }

    override fun attach() {
        val view = requireNotNull(renderer).view()
        if (view.parent != null) return
        var insetContext = context
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context, Boolean>())
        while (useActivityInsets && insetContext != null && seen.add(insetContext)) {
            if (insetContext is ComposeInsetsHost) { insetsLease = insetContext.acquireComposeInsets(); break }
            insetContext = (insetContext as? android.content.ContextWrapper)?.baseContext
        }
        try {
            floatyOwners?.attach(view)
            requireNotNull(mount).invoke(view)
        } catch (failure: Throwable) {
            insetsLease?.close(); insetsLease = null
            throw failure
        }
    }
    override fun detach() {
        try { renderer?.view()?.let { (it.parent as? ViewGroup)?.removeView(it) } }
        finally { insetsLease?.close(); insetsLease = null }
    }
    override fun apply(batch: UiPatchBatch) { requireNotNull(renderer).apply(batch); generation = batch.generation }
    override fun apply(batch: UiPatchBatch, views: List<AndroidViewBindingV1>) {
        val target = requireNotNull(renderer)
        if (target is AndroidViewRendererV1) target.applyWithViews(batch, views)
        else {
            if (views.isNotEmpty()) throw ComposeUiContractException(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE)
            target.apply(batch)
        }
        generation = batch.generation
    }
    override fun validateViews(views: List<AndroidViewBindingV1>) {
        val target = requireNotNull(renderer)
        if (views.isNotEmpty() && target !is AndroidViewRendererV1) throw ComposeUiContractException(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE)
        views.forEach { binding ->
            val claimed = AndroidViewClaimsV1.isClaimed(binding.view)
            if (claimed && !AndroidViewClaimsV1.isOwnedBy(target, binding.view, binding.nodeId, binding.leaseId) ||
                !claimed && binding.view.parent != null) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
                "An AndroidView already belongs to another parent or session", binding.nodeId, "view")
            var ancestor: View? = target.view()
            while (ancestor != null) {
                if (ancestor === binding.view) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
                    "An AndroidView cannot contain its renderer", binding.nodeId, "view")
                ancestor = ancestor.parent as? View
            }
        }
    }
    override fun execute(command: UiCommand) = requireNotNull(renderer).execute(command)
    override fun setTheme(theme: ThemeSpec) {
        overrideTheme = theme
        requireNotNull(renderer).setTheme(resolveComposeTheme(requireNotNull(bridge).snapshot(), theme))
    }
    override fun onDestroy(owner: LifecycleOwner) { onDestroyed?.invoke() }
    private fun themeError(sink: ComposeUiEventSink, failure: Throwable) {
        val error = ComposeUiError.from(failure)
        sink.enqueue(UiEvent(sessionId, generation, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.ERROR, ComposeUiContract.SYSTEM_CALLBACK_ID,
            Bundle().apply { putString(ComposeUiEventFields.CODE, error.code); putString(ComposeUiEventFields.MESSAGE, error.code) }))
    }
    override fun dispose() {
        if (disposed) return
        disposed = true
        bridge?.close(); bridge = null
        lifecycleOwner?.lifecycle?.removeObserver(this)
        lifecycleOwner = null; context = null; mount = null; onDestroyed = null; rendererFactory = null
        try { detach() } finally {
            try { renderer?.dispose() } finally { renderer = null; floatyOwners?.destroy() }
        }
    }
}

/** Partial script overrides retain the live host defaults for unspecified fields. */
internal fun resolveComposeTheme(host: ThemeSpec, override: ThemeSpec?): ThemeSpec = if (override == null) host else ThemeSpec(
    seedArgb = override.seedArgb ?: host.seedArgb,
    colorOverrides = host.colorOverrides + override.colorOverrides,
    dark = override.dark ?: host.dark,
    dynamicColor = override.dynamicColor,
    fontFamily = override.fontFamily ?: host.fontFamily,
    fontScale = override.fontScale ?: host.fontScale,
)
