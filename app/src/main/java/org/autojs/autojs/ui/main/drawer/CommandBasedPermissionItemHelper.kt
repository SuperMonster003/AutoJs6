package org.autojs.autojs.ui.main.drawer

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.ui.main.drawer.IPermissionItem.Companion.ACTION
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

interface CommandBasedPermissionItemHelper : PermissionItemHelper, IPermissionRootItem, IPermissionAdbItem {

    override fun request() {
        if (!RootUtils.isRootAvailable() || !requestWithRoot()) {
            requestWithAdb()
        }
    }

    override fun revoke() {
        if (!RootUtils.isRootAvailable() || !revokeWithRoot()) {
            revokeWithAdb()
        }
    }

    override fun requestWithAdb() = withAdb(ACTION.REQUEST)

    override fun revokeWithAdb() = withAdb(ACTION.REVOKE)

    override fun requestWithRoot() = withRoot(
        ACTION.REQUEST,
        R.string.text_permission_granted_with_root,
        R.string.text_permission_granted_failed_with_root,
    )

    override fun revokeWithRoot() = withRoot(
        ACTION.REVOKE,
        R.string.text_permission_revoked_with_root,
        R.string.text_permission_revoked_failed_with_root,
    )

    fun isActionMatched(action: ACTION) = when (has()) {
        true -> action == ACTION.REQUEST
        else -> action == ACTION.REVOKE
    }

    fun withRoot(action: ACTION, onSuccessMessageRes: Int, onFailureMessageRes: Int): Boolean {
        try {
            ProcessShell.execCommand(getCommand(action), true)
            if (isActionMatched(action)) {
                return true.also { ViewUtils.showToast(context, onSuccessMessageRes) }
            }
        } catch (ignore: Exception) {
            // Ignored.
        }
        return false.also { ViewUtils.showToast(context, onFailureMessageRes, true) }
    }

    fun withAdb(action: ACTION): Boolean {
        val lock = Object()

        AdbDialogBuilder(context, getCommand(action))
            .setChecker(object : AdbDialogBuilder.Checker {
                override fun check() = has()
            })
            .build()
            .dismissListener { synchronized(lock) { lock.notify() } }
            .let { Handler(Looper.getMainLooper()).post { it.show() } }

        synchronized(lock) { lock.wait() }

        return has()
    }

    private class AdbDialogBuilder(private val context: Context, command: String) {

        interface Checker {
            fun check(): Boolean
        }

        private val mCommand = "adb shell $command"
        private var mSnackBarDuration = 1000
        private var mChecker: Checker? = null

        fun setSnackBarDuration(duration: Int) = also { mSnackBarDuration = duration }

        fun setChecker(checker: Checker?) = also { mChecker = checker }

        fun build() = mChecker?.let {
            MaterialDialog.Builder(context)
                .title(R.string.text_adb_tool_needed)
                .content(mCommand)
                .neutralText(R.string.text_permission_test)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { dialog, _ ->
                    val view = dialog.view
                    val resultRes = if (it.check()) R.string.text_granted else R.string.text_not_granted
                    if (view != null) {
                        ViewUtils.showSnack(view, resultRes, mSnackBarDuration)
                    } else {
                        ViewUtils.showToast(context, resultRes)
                    }
                }
                .negativeText(R.string.dialog_button_cancel)
                .onNegative { dialog, _ -> dialog.dismiss() }
                .positiveText(R.string.text_copy_command)
                .onPositive { dialog, _ ->
                    ClipboardUtils.setClip(context, mCommand)
                    val view = dialog.view
                    val textRes = R.string.text_command_already_copied_to_clip
                    if (view != null) {
                        ViewUtils.showSnack(view, textRes, mSnackBarDuration)
                    } else {
                        ViewUtils.showToast(context, textRes)
                    }
                }
                .autoDismiss(false)
        } ?: throw Exception("A checker is required for AdbDialogBuilder")

    }

}