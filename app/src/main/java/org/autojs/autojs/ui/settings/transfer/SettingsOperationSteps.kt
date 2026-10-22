package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.autojs.autojs.core.pref.deploy.DeploymentException
import org.autojs.autojs.permission.AllFilesAccessPermission
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.autojs.autojs.ui.shortcut.ShortcutBackupActivity
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.LauncherIconComponents
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs6.R

/**
 * The step that applies an import or an undo (docs/settings/ROADMAP.md T10 step 4, P7.5) behind a progress dialog:
 * [work] runs as an operation of the process ([SettingsOperations]), so that a page recreated by the effects it
 * applies (theme color, night mode, language) waits for the same operation. An operation the process no longer knows
 * ended with its process ([Failure.INTERRUPTED]).
 *
 * zh-CN: 在进度对话框后应用导入或撤销的步骤 (ROADMAP T10 第 4 步, P7.5): [work] 作为进程级操作运行 ([SettingsOperations]), 因此被其应用
 * 的设置 (主题色, 夜间模式, 语言) 重建的页面等待同一个操作. 进程已不认识的操作随其进程结束 ([Failure.INTERRUPTED]).
 */
class SettingsApplyStep(
    @param:StringRes private val progressRes: Int,
    private val work: suspend (Context, SettingsWizardState) -> SettingsTransferOutcome,
) : SettingsWizardStep(Step.APPLY) {

    override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState {
        val activity = wizard.host.hostActivity
        val known = state.operation?.let(SettingsOperations::find)
        if (state.operation != null && known == null) {
            return state.copy(step = Step.FAILED, operation = null, failure = Failure.INTERRUPTED, failureDetail = null)
        }
        val current = if (state.operation == null) state.copy(operation = SettingsOperations.newId()) else state
        wizard.host.wizardState = current
        val id = current.operation!!
        val app = activity.applicationContext
        val operation = known ?: SettingsOperations.start(id) { work(app, current) }
        val progress = MaterialDialog.Builder(activity)
            .title(wizard.titleRes)
            .content(progressRes)
            .progress(true, 0)
            .widgetThemeColor()
            .cancelable(false)
            .show()
        var deployments: DeploymentProgressDialog? = null
        // The deployment items of P7.6 replace the plain progress dialog once they start.
        val follower = CoroutineScope(currentCoroutineContext()).launch {
            val run = SettingsOperations.deploymentsOf(id).filterNotNull().first()
            progress.dismiss()
            // Kept before following, which only ends with the cancellation of this coroutine.
            val dialog = DeploymentProgressDialog(activity, wizard.titleRes, run)
            deployments = dialog
            dialog.follow()
        }
        return try {
            val outcome = operation.await()
            SettingsOperations.forget(id)
            current.copy(step = Step.RESULT, operation = null, outcome = outcome)
        } catch (e: CancellationException) {
            // The page is recreated; the operation goes on and the new page waits for it.
            throw e
        } catch (e: Exception) {
            SettingsOperations.forget(id)
            current.copy(step = Step.FAILED, operation = null, failure = Failure.of(e), failureDetail = e.message ?: e.javaClass.simpleName)
        } finally {
            follower.cancel()
            progress.dismiss()
            deployments?.dismiss()
        }
    }
}

/**
 * The result page of an import or an undo (T10): the counts, the skips by reason and what needs attention
 * ([SettingsTransferMessages.outcomeText]); "Restart" when a restart is needed or an effect failed (D10), "Post
 * notifications" when the foreground service lacks the permission (appendix C Q13), "Grant access" when a deployment
 * item lacked the storage access (T18), "Restore shortcuts" when imported shortcuts can be restored to the desktop
 * (P10.2: the page "Shortcut backup" offers them, then their missing icons; when both other buttons are taken, the
 * text tells where to restore them). A changed launcher icon is switched once the page is closed
 * ([SettingsTransferOutcome.launcherIconPending]).
 *
 * zh-CN: 导入或撤销的结果页 (T10): 计数, 按原因的跳过数与需要注意的事项 ([SettingsTransferMessages.outcomeText]); 需要重启或有生效入口
 * 失败时提供 "重启" (D10), 前台服务缺少通知权限时提供 "发送通知" 权限入口 (附录 C Q13), 部署项缺少存储权限时提供 "授予权限" (T18),
 * 导入的快捷方式可恢复到桌面时提供 "恢复快捷方式" (P10.2: "快捷方式备份" 页面列出它们, 然后补选缺失的图标; 另外两个按钮都被占用时由
 * 文字说明恢复位置). 启动图标有变化时在本页关闭后切换
 * ([SettingsTransferOutcome.launcherIconPending]).
 */
class SettingsResultStep(@param:StringRes private val titleRes: Int) : SettingsWizardStep(Step.RESULT) {

    private companion object {
        const val LANGUAGE_SETTLE_MILLIS = 600L
    }

    override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
        val activity = wizard.host.hostActivity
        val outcome = state.outcome ?: return null
        if (outcome.languageChanged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 applies the language while the night mode recreates the page, which may then keep the language
            // of before. Once settled, a page whose language differs from the app is recreated, once.
            // zh-CN: Android 13 在夜间模式重建页面的同时应用语言, 重建后的页面可能仍是旧语言. 稍候若本页语言与应用不同, 重建本页 (仅一次).
            delay(LANGUAGE_SETTLE_MILLIS)
            wizard.host.wizardState = state.copy(outcome = outcome.copy(languageChanged = false))
            if (activity.resources.configuration.locales[0] != activity.applicationContext.resources.configuration.locales[0]) {
                activity.recreate()
                awaitCancellation()
            }
        }
        val restart = outcome.restartNeeded || outcome.failedEffects.isNotEmpty()
        val builder = dialog(wizard, state, R.string.text_done, back = false, cancel = false)
            .title(titleRes)
            .content(SettingsTransferMessages.outcomeText(activity, outcome))
        if (restart) builder.neutralText(R.string.text_restart).neutralColorRes(R.color.dialog_button_default)
        val storageAccess = outcome.deployments.any { it.failure == DeploymentException.Reason.PERMISSION }
        when {
            storageAccess -> builder.negativeText(R.string.settings_transfer_grant_all_files_access).negativeColorRes(R.color.dialog_button_default)
            outcome.notificationPermission -> builder.negativeText(R.string.text_post_notifications_permission).negativeColorRes(R.color.dialog_button_default)
        }
        val restore = when {
            outcome.restorableShortcuts.isEmpty() -> null
            !storageAccess && !outcome.notificationPermission -> DialogAction.NEGATIVE
            !restart -> DialogAction.NEUTRAL
            else -> null
        }
        when (restore) {
            DialogAction.NEGATIVE -> builder.negativeText(R.string.settings_transfer_result_restore_shortcuts).negativeColorRes(R.color.dialog_button_default)
            DialogAction.NEUTRAL -> builder.neutralText(R.string.settings_transfer_result_restore_shortcuts).neutralColorRes(R.color.dialog_button_default)
            else -> Unit
        }
        val action = builder.showAndAwait()
        if (outcome.launcherIconPending) {
            // Last: the system may close the task of this page once the alias it was started from is disabled.
            runCatching { LauncherIconComponents.applyStored(activity) }
        }
        when {
            action == null -> Unit
            action == restore -> restoreShortcuts(activity, outcome)
            action == DialogAction.NEUTRAL -> if (restart) IntentUtils.App.restart(activity)
            action == DialogAction.NEGATIVE -> if (storageAccess) AllFilesAccessPermission(activity).config() else NotificationUtils.launchSettings()
        }
        return null
    }

    /** Opens the page "Shortcut backup" on the imported shortcuts (P10.2). zh-CN: 打开 "快捷方式备份" 页面并列出导入的快捷方式. */
    private fun restoreShortcuts(context: Context, outcome: SettingsTransferOutcome) {
        context.startActivity(
            Intent(context, ShortcutBackupActivity::class.java)
                .putStringArrayListExtra(ShortcutBackupActivity.EXTRA_RESTORE_IDS, ArrayList(outcome.restorableShortcuts))
                .putExtra(ShortcutBackupActivity.EXTRA_COMPLETE_ICONS, outcome.data.any { it.iconsMissing }),
        )
    }
}

/**
 * The reason an import or an undo failed (one message per [Failure]); [back], when given, offers "Back" to the state
 * it returns.
 * zh-CN: 导入或撤销失败的原因 (每种 [Failure] 一条提示); 给出 [back] 时提供 "上一步", 回到其返回的状态.
 */
class SettingsFailedStep(
    @param:StringRes private val titleRes: Int,
    private val back: ((SettingsWizardState) -> SettingsWizardState)? = null,
) : SettingsWizardStep(Step.FAILED) {

    override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
        val activity = wizard.host.hostActivity
        val builder = dialog(wizard, state, R.string.dialog_button_close, back = false, cancel = false)
            .title(titleRes)
            .content(SettingsTransferMessages.failureText(activity, state.failure ?: Failure.IO, state.failureDetail))
        if (back != null) builder.neutralText(R.string.dialog_button_back).neutralColorRes(R.color.dialog_button_default)
        val action = builder.showAndAwait()
        return if (action == DialogAction.NEUTRAL) back?.invoke(state.copy(failure = null, failureDetail = null)) else null
    }
}
