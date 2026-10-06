package org.autojs.autojs.ui.wizard

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.ViewUtils
import kotlin.coroutines.resume

/**
 * Coroutine helpers shared by the setup wizards.
 *
 * zh-CN: 各设置向导共用的协程辅助方法.
 */
object WizardUi {

    /** How long a launched page has to cover the activity before the step counts as handled in place. */
    private const val LEAVE_TIMEOUT_MILLIS = 2500L

    /** Shows the dialog and suspends until it is dismissed; returns the pressed button, if any. */
    suspend fun MaterialDialog.Builder.showAndAwait(): DialogAction? = suspendCancellableCoroutine { continuation ->
        var action: DialogAction? = null
        onAny { _, which -> action = which }
        dismissListener { if (continuation.isActive) continuation.resume(action) }
        val dialog = show()
        continuation.invokeOnCancellation { runCatching { dialog.dismiss() } }
    }

    enum class ReturnResult {
        /** The activity was covered and has resumed again. */
        RETURNED,

        /** Nothing covered the activity in time; the step was handled in place or not at all. */
        NOT_LEFT,

        /** [launchAndAwaitReturn] could not start the step. */
        LAUNCH_FAILED,

        /** The activity was destroyed while waiting. */
        DESTROYED,
    }

    /**
     * Runs [launch] and suspends until the activity was covered (a settings page, a system dialog
     * or an installer) and resumed again. When nothing covers the activity within
     * [leaveTimeoutMillis], the step counts as handled in place.
     *
     * zh-CN: 执行 [launch] 并挂起, 直到 Activity 被覆盖 (设置页, 系统对话框或安装器) 后重新回到前台.
     * 若 [leaveTimeoutMillis] 内没有页面覆盖 Activity, 视为已就地处理.
     */
    suspend fun launchAndAwaitReturn(
        activity: ComponentActivity,
        leaveTimeoutMillis: Long = LEAVE_TIMEOUT_MILLIS,
        launch: () -> Boolean,
    ): ReturnResult {
        val lifecycle = activity.lifecycle
        val left = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<ReturnResult>()
        val observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                left.complete(Unit)
            }

            override fun onResume(owner: LifecycleOwner) {
                if (left.isCompleted) returned.complete(ReturnResult.RETURNED)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                left.complete(Unit)
                returned.complete(ReturnResult.DESTROYED)
            }
        }
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) return ReturnResult.DESTROYED
        lifecycle.addObserver(observer)
        try {
            if (!runCatching(launch).getOrDefault(false)) return ReturnResult.LAUNCH_FAILED
            withTimeoutOrNull(leaveTimeoutMillis) { left.await() } ?: return ReturnResult.NOT_LEFT
            return returned.await()
        } finally {
            lifecycle.removeObserver(observer)
        }
    }

    /** Shows [text] in a snackbar of the activity behind [context], or as a toast without one. */
    fun showSnack(context: Context, text: CharSequence) {
        val activity = context.findActivity()
        val anchor = activity?.takeUnless { it.isFinishing || it.isDestroyed }?.let(::findSnackbarAnchor)
        if (anchor == null) {
            ViewUtils.showToast(context, text.toString(), true)
            return
        }
        Snackbar.make(anchor, text, Snackbar.LENGTH_LONG).show()
    }

    /** The first coordinator layout lets the snackbar move floating buttons out of its way. */
    fun findSnackbarAnchor(activity: Activity): View? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findCoordinator(content) ?: content
    }

    private fun findCoordinator(view: View): CoordinatorLayout? {
        if (view is CoordinatorLayout) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) {
            findCoordinator(view.getChildAt(i))?.let { return it }
        }
        return null
    }

}
