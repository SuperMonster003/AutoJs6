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
            if (overrideTheme == null) try { renderer?.setTheme(it) }
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
        try { renderer = loaded.factory.create(environment) } catch (error: Throwable) { dispose(); throw error }
    }

    override fun attach() {
        val view = requireNotNull(renderer).view()
        if (view.parent != null) return
        insetsLease = (context as? ComposeInsetsHost)?.acquireComposeInsets()
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
    override fun execute(command: UiCommand) = requireNotNull(renderer).execute(command)
    override fun setTheme(theme: ThemeSpec) { overrideTheme = theme; requireNotNull(renderer).setTheme(theme) }
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
        lifecycleOwner = null; context = null; mount = null; onDestroyed = null
        try { detach() } finally {
            try { renderer?.dispose() } finally { renderer = null; floatyOwners?.destroy() }
        }
    }
}
