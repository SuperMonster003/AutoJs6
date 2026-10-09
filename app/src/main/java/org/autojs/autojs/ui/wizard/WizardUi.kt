package org.autojs.autojs.ui.wizard

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
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
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.SnackbarUtils.coverNavigationBar
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import kotlin.coroutines.resume

/**
 * Helpers shared by the setup wizards: dialogs and external pages awaited from coroutines,
 * snackbars and theme-colored widgets.
 *
 * zh-CN: 各设置向导共用的辅助方法: 在协程中等待对话框及外部页面, 提示条及主题色控件.
 */
object WizardUi {

    /** How long a launched page has to cover the activity before the step counts as handled in place. */
    private const val LEAVE_TIMEOUT_MILLIS = 2500L

    /** Alpha of a check box the user cannot change, such as the one of an installed plugin in the current profile. */
    const val LOCKED_CHECK_BOX_ALPHA = 0.38f

    /** Tints the loading spinner of a wizard dialog with the theme color, kept readable on the dialog background. */
    fun tintLoadingProgress(progressBar: ProgressBar) {
        val color = ColorUtils.adjustColorForContrast(
            progressBar.context.getColor(R.color.window_background),
            ThemeColorManager.colorPrimary,
            2.3,
        )
        progressBar.indeterminateTintList = ColorStateList.valueOf(color)
    }

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
    ): ReturnResult = launchAndAwaitReturn(activity.lifecycle, leaveTimeoutMillis, launch)

    internal suspend fun launchAndAwaitReturn(
        lifecycle: Lifecycle,
        leaveTimeoutMillis: Long = LEAVE_TIMEOUT_MILLIS,
        launch: () -> Boolean,
    ): ReturnResult {
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
        // A preceding installer may still cover the host when its package broadcast arrives.
        // Such an activity will resume without delivering another onPause to this observer.
        // zh-CN: 上一安装器的包广播到来时可能仍覆盖宿主, 此时只会收到恢复事件, 不会再收到暂停事件.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED) &&
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            left.complete(Unit)
        }
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
        Snackbar.make(anchor, text, Snackbar.LENGTH_LONG).coverNavigationBar().show()
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
