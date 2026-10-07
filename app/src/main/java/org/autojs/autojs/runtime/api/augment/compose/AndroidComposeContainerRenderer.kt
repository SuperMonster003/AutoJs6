package org.autojs.autojs.runtime.api.augment.compose

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.OneShotPreDrawListener
import androidx.lifecycle.LifecycleOwner
import org.autojs.autojs.core.plugin.compose.*
import org.autojs.autojs.core.ui.widget.ComposeContainerView
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*

/** Main-thread Android effects for a container in an Activity or an existing legacy window. */
internal class AndroidComposeContainerRenderer(
    private var container: ComposeContainerView?,
    private val loaded: ComposeUiPluginLoader.Loaded,
    private val sessionId: Int,
    private var beforeMount: ((Any) -> Unit)?,
    private var onDestroyed: (() -> Unit)?,
) : ComposeSessionRenderer, View.OnAttachStateChangeListener {
    private var delegate: ComposeAndroidRenderer? = null
    private var backView: ComposeFloatyBackView? = null
    private var attachedOnce = false
    private var disposed = false
    private var mounted = false
    private var sink: ComposeUiEventSink? = null
    private var generation = 0L
    private var ready = false
    private var preDraw: OneShotPreDrawListener? = null
    private val commands = java.util.ArrayDeque<UiCommand>()

    override fun open(sink: ComposeUiEventSink) {
        this.sink = sink
        val target = requireNotNull(container)
        val owner = contextOwner(target.context)
        val floatyOwners = if (owner == null) ComposeFloatyOwners() else null
        delegate = ComposeAndroidRenderer(target.context, loaded, sessionId, { view ->
            if (target.childCount != 0) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
                "Compose container already has content")
            beforeMount?.invoke(view)
            val child = if (floatyOwners == null) view else ComposeFloatyBackView(target.context, floatyOwners,
                preserveOutsideFocus = true).also { wrapper ->
                backView = wrapper
                wrapper.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            target.addView(child, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, owner, floatyOwners, { onDestroyed?.invoke() }).also { it.open(sink) }
    }

    override fun attach() {
        check(!disposed)
        val target = requireNotNull(container)
        attachedOnce = target.isAttachedToWindow
        target.addOnAttachStateChangeListener(this)
        requireNotNull(delegate).attach()
        mounted = true
        ready = false
        awaitDraw()
    }
    override fun onViewAttachedToWindow(view: View) { attachedOnce = true; awaitDraw() }
    override fun onViewDetachedFromWindow(view: View) { if (attachedOnce && !disposed) onDestroyed?.invoke() }
    override fun detach() {
        mounted = false; ready = false
        preDraw?.removeListener(); preDraw = null; commands.clear()
        try { delegate?.detach() } finally { removeBackView() }
    }
    override fun apply(batch: UiPatchBatch) { requireNotNull(delegate).apply(batch); changed(batch.generation) }
    override fun apply(batch: UiPatchBatch, views: List<org.autojs.plugin.compose.api.interop.AndroidViewBindingV1>) {
        requireNotNull(delegate).apply(batch, views); changed(batch.generation)
    }
    override fun validateViews(views: List<org.autojs.plugin.compose.api.interop.AndroidViewBindingV1>) = requireNotNull(delegate).validateViews(views)
    private fun changed(generation: Long) { this.generation = generation; ready = false; awaitDraw() }
    private fun awaitDraw() {
        val target = container ?: return
        if (disposed || !mounted || preDraw != null) return
        preDraw = OneShotPreDrawListener.add(target) {
            preDraw = null
            if (!disposed) { ready = true; target.post(::drainCommands) }
        }
    }
    override fun execute(command: UiCommand) {
        if (command is UiCommand.Edit || command is UiCommand.ShowSnackbar) { requireNotNull(delegate).execute(command); return }
        if (commands.size == ComposeUiLimits.MAX_PATCHES_PER_BATCH) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        commands += command
        if (ready) container?.post(::drainCommands) else awaitDraw()
    }
    private fun drainCommands() {
        if (disposed || !mounted || !ready || container?.isAttachedToWindow != true) return
        while (commands.isNotEmpty()) {
            try { requireNotNull(delegate).execute(commands.removeFirst()) }
            catch (failure: Exception) { commandError(failure) }
            catch (failure: LinkageError) { commandError(failure) }
        }
    }
    private fun commandError(failure: Throwable) {
        val error = ComposeUiError.from(failure)
        sink?.enqueue(UiEvent(sessionId, generation, error.nodeId ?: ComposeUiContract.NO_NODE_ID,
            ComposeUiEvents.ERROR, ComposeUiContract.SYSTEM_CALLBACK_ID, android.os.Bundle().apply {
                putString(ComposeUiEventFields.CODE, error.code)
                putString(ComposeUiEventFields.MESSAGE, error.code)
                error.prop?.let { putString(ComposeUiEventFields.PROP, it) }
            }))
    }
    override fun setTheme(theme: ThemeSpec) = requireNotNull(delegate).setTheme(theme)
    override fun dispose() {
        if (disposed) return
        disposed = true
        preDraw?.removeListener(); preDraw = null; commands.clear(); sink = null
        container?.removeOnAttachStateChangeListener(this)
        container = null; beforeMount = null; onDestroyed = null
        try { delegate?.dispose() } finally { removeBackView(); delegate = null }
    }

    private fun removeBackView() {
        val owned = backView.also { backView = null } ?: return
        (owned.parent as? ViewGroup)?.removeView(owned)
    }

    private fun contextOwner(context: Context): LifecycleOwner? {
        var current = context
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context, Boolean>())
        while (seen.add(current)) {
            if (current is LifecycleOwner) return current
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }
}
