package org.autojs.autojs.runtime.api.augment.compose

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.OneShotPreDrawListener
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.plugin.compose.ComposeAndroidRenderer
import org.autojs.autojs.core.plugin.compose.ComposeFloatyOwners
import org.autojs.autojs.core.plugin.compose.ComposeFloatyBackView
import org.autojs.autojs.core.plugin.compose.ComposeSessionRenderer
import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.core.plugin.compose.ComposeUiPluginLoader
import org.autojs.autojs.core.plugin.compose.ScriptUiDispatcher
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Floaty
import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.ThemeSpec
import org.autojs.plugin.compose.api.model.UiCommand
import org.autojs.plugin.compose.api.model.UiEvent
import org.autojs.plugin.compose.api.model.UiPatchBatch
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.io.Closeable

/** Android-only overlay adapter. All renderer/window effects run on main without script waits. */
internal class AndroidComposeFloatyRenderer(
    runtime: ScriptRuntime,
    private val loaded: ComposeUiPluginLoader.Loaded,
    private val sessionId: Int,
    options: ComposeFloatyOptions,
    beforeMount: (Any) -> Unit,
    onDestroyed: () -> Unit,
    onFailure: (Throwable) -> Unit,
    private var rendererFactory: ((org.autojs.plugin.compose.api.loading.ComposeUiHostEnvironment) -> org.autojs.plugin.compose.api.loading.ComposeUiRenderer)? = null,
    private val compositionAnchor: Boolean = false,
) : ComposeSessionRenderer {
    private var context: Context? = ContextThemeWrapper(runtime.uiHandler.applicationContext, R.style.ScriptTheme)
    private var mountCallback: ((Any) -> Unit)? = beforeMount
    private var destroyCallback: (() -> Unit)? = onDestroyed
    private var failureCallback: ((Throwable) -> Unit)? = onFailure
    private var sink: ComposeUiEventSink? = null
    private var delegate: ComposeAndroidRenderer? = null
    private var owners: ComposeFloatyOwners? = null
    private var content: View? = null
    private var generation = 0L
    private var disposed = false
    private var drawReady = false
    private var preDraw: OneShotPreDrawListener? = null
    private val commands = ArrayDeque<UiCommand>()
    private val control = NativeControl(runtime.floaty, options, ScriptUiDispatcher.mainExecutor(), ::windowClosed, ::fail, compositionAnchor)
    val window: ComposeFloatyControl get() = control

    init { control.reserveCloseAll() }

    override fun open(sink: ComposeUiEventSink) {
        main(); check(!disposed && delegate == null)
        this.sink = sink
        val owners = ComposeFloatyOwners().also { this.owners = it }
        val renderer = ComposeAndroidRenderer(requireNotNull(context), loaded, sessionId, { view ->
            if (compositionAnchor) {
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                view.isFocusable = false; view.isClickable = false
            }
            content = view
            mountCallback?.invoke(view)
            control.attach(view, owners)
            afterFrameChange()
        }, floatyOwners = owners, onDestroyed = ::windowClosed, rendererFactory = rendererFactory,
            useActivityInsets = !compositionAnchor).also { delegate = it }
        try { renderer.open(sink) } catch (failure: Throwable) { dispose(); throw failure }
    }

    override fun attach() {
        usable()
        try { requireNotNull(delegate).attach() }
        catch (failure: Exception) { fail(failure) }
        catch (failure: LinkageError) { fail(failure) }
    }

    override fun detach() {
        main()
        preDraw?.removeListener(); preDraw = null
        commands.clear(); drawReady = false
        control.detach()
        delegate?.detach()
        content = null
    }

    override fun apply(batch: UiPatchBatch) {
        usable()
        requireNotNull(delegate).apply(batch)
        generation = batch.generation
        afterFrameChange()
    }
    override fun apply(batch: UiPatchBatch, views: List<org.autojs.plugin.compose.api.interop.AndroidViewBindingV1>) {
        usable()
        requireNotNull(delegate).apply(batch, views)
        generation = batch.generation
        afterFrameChange()
    }
    override fun validateViews(views: List<org.autojs.plugin.compose.api.interop.AndroidViewBindingV1>) = requireNotNull(delegate).validateViews(views)

    override fun execute(command: UiCommand) {
        usable()
        // The real dialog content owns readiness; its invisible anchor's pre-draw is irrelevant.
        if (compositionAnchor || command is UiCommand.Edit || command is UiCommand.ShowSnackbar) {
            requireNotNull(delegate).execute(command)
            return
        }
        if (commands.size == ComposeUiLimits.MAX_PATCHES_PER_BATCH) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        commands += command
        if (drawReady) content?.post(::drainCommands) else awaitDraw()
    }

    private fun afterFrameChange() { drawReady = false; awaitDraw() }
    private fun awaitDraw() {
        if (disposed || preDraw != null) return
        val view = content ?: return
        preDraw = OneShotPreDrawListener.add(view) {
            preDraw = null
            if (!disposed) {
                drawReady = true
                // Run after pre-draw. Composition has installed focus/scroll command handles.
                view.post(::drainCommands)
            }
        }
    }
    private fun drainCommands() {
        if (disposed || !drawReady || content?.isAttachedToWindow != true) return
        while (commands.isNotEmpty()) {
            val command = commands.removeFirst()
            try { requireNotNull(delegate).execute(command) }
            catch (failure: Exception) { commandError(failure) }
            catch (failure: LinkageError) { commandError(failure) }
        }
    }
    private fun commandError(failure: Throwable) {
        val error = ComposeUiError.from(failure)
        sink?.enqueue(UiEvent(sessionId, generation, error.nodeId ?: ComposeUiContract.NO_NODE_ID,
            ComposeUiEvents.ERROR, ComposeUiContract.SYSTEM_CALLBACK_ID, Bundle().apply {
                putString(ComposeUiEventFields.CODE, error.code)
                putString(ComposeUiEventFields.MESSAGE, error.code)
                error.prop?.let { putString(ComposeUiEventFields.PROP, it) }
            }))
    }

    override fun setTheme(theme: ThemeSpec) { usable(); requireNotNull(delegate).setTheme(theme) }

    private fun fail(failure: Throwable) {
        main()
        if (disposed) return
        val mapped = if (failure is SecurityException || failure is WindowManager.BadTokenException && !control.hasPermission()) {
            ComposeUiError(ComposeUiErrorCodes.PERMISSION_REQUIRED,
                context?.getString(R.string.error_no_display_over_other_apps_permission) ?: ComposeUiErrorCodes.PERMISSION_REQUIRED,
                cause = failure)
        } else ComposeUiError.from(failure)
        // The owner posts report+close as one script action. Native cleanup suppresses onDestroyed,
        // otherwise an immediate core.close would discard the still-queued typed error.
        try { failureCallback?.invoke(mapped) } finally { dispose() }
    }

    private fun windowClosed() {
        main()
        if (disposed) return
        try { destroyCallback?.invoke() } finally { dispose() }
    }

    override fun dispose() {
        main()
        if (disposed) return
        disposed = true
        preDraw?.removeListener(); preDraw = null
        commands.clear(); drawReady = false
        try { control.dispose() } finally {
            try { delegate?.dispose() } finally {
                owners?.destroy()
                delegate = null; owners = null; content = null; context = null; sink = null
                mountCallback = null; destroyCallback = null; failureCallback = null
                rendererFactory = null
            }
        }
    }
    private fun usable() { main(); if (disposed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED) }
    private fun main() { check(Looper.myLooper() == Looper.getMainLooper()) { "Floating renderer effects require main" } }

    /** Immutable desired settings coalesce before service readiness; geometry reads use snapshots. */
    private class NativeControl(
        floaty: Floaty,
        private val options: ComposeFloatyOptions,
        private val main: Executor,
        onClosed: () -> Unit,
        onFailure: (Throwable) -> Unit,
        private val compositionAnchor: Boolean = false,
    ) : ComposeFloatyControl {
        private data class Desired(val x: Int?, val y: Int?, val width: Int?, val height: Int?,
            val touchable: Boolean, val focusable: Boolean, val adjust: Boolean? = null)
        private enum class Change { POSITION, SIZE, TOUCHABLE, FOCUS, ADJUST }
        private val lock = Any()
        private var desired = Desired(options.x, options.y, options.width, options.height, options.touchable, options.focusable)
        private val dirty = java.util.EnumSet.allOf(Change::class.java)
        @Volatile private var native: Floaty.ManagedWindow? = null
        @Volatile private var disposed = false
        private var floaty: Floaty? = floaty
        private var closedCallback: (() -> Unit)? = onClosed
        private var failureCallback: ((Throwable) -> Unit)? = onFailure
        private var ready = false
        private var scheduled = false
        private var detaching = false
        @Volatile private var reservation: Closeable? = null
        private val handler = Handler(Looper.getMainLooper())
        private val startupTimeout = Runnable {
            if (!disposed && !ready) failureCallback?.invoke(ComposeUiError(ComposeUiErrorCodes.RENDERER_FAILED, "Floating window service did not become ready"))
        }
        override val x get() = native?.x ?: -1
        override val y get() = native?.y ?: -1
        override val width get() = native?.width ?: 0
        override val height get() = native?.height ?: 0

        fun hasPermission() = floaty?.hasPermission() == true

        fun reserveCloseAll() {
            val registered = requireNotNull(floaty).reserveManagedClose(::close)
            reservation = registered
            if (disposed) { reservation = null; registered.close() }
        }

        fun attach(view: View, owners: ComposeFloatyOwners) {
            if (disposed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
            if (native != null) return
            handler.postDelayed(startupTimeout, 10000)
            val created = requireNotNull(floaty).createManagedWindow(options.raw,
                object : BaseResizableFloatyWindow.ViewSupplier {
                    override fun inflate(context: Context, parent: ViewGroup?): View {
                        if (disposed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
                        val target = if (compositionAnchor) requireNotNull(parent) else ComposeFloatyBackView(context, owners).also {
                            requireNotNull(parent).addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                        target.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        return view
                    }
                }, { window, failure ->
                    handler.removeCallbacks(startupTimeout)
                    if (disposed) window.close(true)
                    else if (failure != null) failureCallback?.invoke(failure)
                    else {
                        native = window
                        ready = true
                        applyDesired()
                    }
                }, {
                    if (!detaching && !disposed) {
                        ready = false; native = null
                        handler.removeCallbacks(startupTimeout)
                        closedCallback?.invoke()
                    }
                }, rawWindowParameters = if (compositionAnchor) { parameters ->
                    parameters.width = 1; parameters.height = 1; parameters.alpha = 0f
                    parameters.flags = parameters.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                } else null)
            reservation?.close(); reservation = null
            if (disposed) created.close(true) else if (native == null) native = created
        }

        override fun setPosition(x: Int, y: Int) = change(Change.POSITION) { it.copy(x = x, y = y) }
        override fun setSize(width: Int, height: Int) = change(Change.SIZE) { it.copy(width = width, height = height) }
        override fun requestFocus() = change(Change.FOCUS) { it.copy(focusable = true) }
        override fun disableFocus() = change(Change.FOCUS) { it.copy(focusable = false) }
        override fun setTouchable(touchable: Boolean) = change(Change.TOUCHABLE) { it.copy(touchable = touchable) }
        override fun setAdjustEnabled(enabled: Boolean) {
            if (options.raw) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Raw windows have no adjustment controls")
            change(Change.ADJUST) { it.copy(adjust = enabled) }
        }
        private fun change(kind: Change, update: (Desired) -> Desired) {
            synchronized(lock) {
                if (disposed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
                desired = update(desired)
                dirty += kind
                if (scheduled) return
                scheduled = true
            }
            main.execute {
                synchronized(lock) { scheduled = false }
                if (!disposed && ready) applyDesired()
            }
        }
        private fun applyDesired() {
            val window = native ?: return
            val (state, changes) = synchronized(lock) { (desired to dirty.toSet()).also { dirty.clear() } }
            try {
                if (Change.POSITION in changes && (state.x != null || state.y != null)) window.setPosition(state.x ?: window.x, state.y ?: window.y)
                if (Change.SIZE in changes && (state.width != null || state.height != null)) window.setSize(state.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                    state.height ?: ViewGroup.LayoutParams.WRAP_CONTENT)
                if (Change.TOUCHABLE in changes) window.setTouchable(state.touchable)
                if (Change.FOCUS in changes) {
                    if (state.focusable) window.requestFocus() else window.disableFocus()
                }
                if (Change.ADJUST in changes) state.adjust?.let(window::setAdjustEnabled)
            } catch (failure: Exception) { failureCallback?.invoke(failure) }
            catch (failure: LinkageError) { failureCallback?.invoke(failure) }
        }

        fun detach() {
            handler.removeCallbacks(startupTimeout)
            ready = false
            synchronized(lock) { dirty.addAll(Change.entries) }
            val window = native.also { native = null }
            detaching = true
            try { window?.close(true) } finally { detaching = false }
            if (!disposed && reservation == null) reserveCloseAll()
        }
        fun dispose() {
            synchronized(lock) { if (disposed) return; disposed = true }
            reservation?.close(); reservation = null
            try { detach() } finally { floaty = null; closedCallback = null; failureCallback = null }
        }
        override fun close() {
            main.execute {
                if (!disposed) try { closedCallback?.invoke() } finally { dispose() }
            }
        }
    }
}
