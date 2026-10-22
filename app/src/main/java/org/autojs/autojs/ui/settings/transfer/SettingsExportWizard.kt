package org.autojs.autojs.ui.settings.transfer

import android.content.ActivityNotFoundException
import android.net.Uri
import android.view.LayoutInflater
import androidx.core.widget.doAfterTextChanged
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsPassword
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogSettingsPasswordBinding

/**
 * The export wizard (docs/settings/ROADMAP.md T10, D4 / D5 / D6, P6.4): choose the categories, set an optional password
 * for the credentials (P7.7, only when they are ticked), choose where to save (the system page "create document", T11,
 * with the default file name of D5), write the zip ([SettingsExporter], at most [SettingsArchive.MAX_BYTES], T7), then
 * the done page with the file name and the counts. Canceling the save location ends the wizard with a notice; a failed
 * write shows its reason and offers to go back to the categories.
 *
 * zh-CN: 导出向导 (ROADMAP T10, D4 / D5 / D6, P6.4): 选择分类, 为凭据设置可选口令 (P7.7, 仅在勾选凭据时), 选择保存位置 (系统 "创建
 * 文档" 页面, T11, 默认文件名见 D5), 写入 zip ([SettingsExporter], 不超过 [SettingsArchive.MAX_BYTES], T7), 最后是显示文件名与计数的
 * 完成页. 取消选择保存位置时结束向导并提示; 写入失败时显示原因, 可返回分类选择.
 */
class SettingsExportWizard(host: SettingsWizardHost) : SettingsWizard(host) {

    override val titleRes: Int = R.string.settings_transfer_export

    override val steps: List<SettingsWizardStep> = listOf(
        SettingsCategoryPickerStep(),
        PasswordStep(),
        LocationStep(),
        WriteStep(),
        DoneStep(),
        FailedStep(),
    )

    /**
     * Step 2 when `credentials` is ticked (P7.7, D23 / T21): an optional password, typed twice, that adds a password slot
     * to the sealed credentials, so that another device imports them once it is entered there; both fields left empty
     * write the device slot only. "Next" stays disabled while the fields differ. The password stays in this process
     * ([SettingsPasswords]); without `credentials` the step forgets it and goes on.
     * zh-CN: 勾选 `credentials` 时的第 2 步 (P7.7, D23 / T21): 可选口令, 输入两次, 为加密的凭据另加口令槽, 在其他设备上输入该口令即可
     * 导入; 两栏都留空则只写设备槽. 两栏不一致时 "下一步" 不可用. 口令只保存在本进程中 ([SettingsPasswords]); 未勾选 `credentials` 时
     * 本步骤忘记口令并直接继续.
     */
    private class PasswordStep : SettingsWizardStep(Step.SET_PASSWORD) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            if (SettingCategory.CREDENTIALS.id !in state.selected) {
                SettingsPasswords.forget(state.password)
                return wizard.next(state.copy(password = null))
            }
            val activity = wizard.host.hostActivity
            val binding = DialogSettingsPasswordBinding.inflate(LayoutInflater.from(activity))
            binding.settingsPasswordHint.setText(R.string.settings_transfer_password_export_hint)
            var dialog: MaterialDialog? = null
            fun update() {
                val password = binding.settingsPassword.text?.toString().orEmpty()
                val confirm = binding.settingsPasswordConfirm.text?.toString().orEmpty()
                val differ = password != confirm
                binding.settingsPasswordConfirmLayout.error = if (differ && confirm.isNotEmpty()) activity.getString(R.string.settings_transfer_password_mismatch) else null
                dialog?.getActionButton(DialogAction.POSITIVE)?.isEnabled = !differ
            }
            binding.settingsPassword.doAfterTextChanged { update() }
            binding.settingsPasswordConfirm.doAfterTextChanged { update() }
            val builder = dialog(wizard, state)
                .title(R.string.settings_transfer_password_set_title)
                .customView(binding.root, true)
                .showListener {
                    dialog = it as MaterialDialog
                    update()
                }
            return navigate(wizard, state, builder) {
                SettingsPasswords.forget(state.password)
                val token = SettingsPassword.of(binding.settingsPassword.text ?: "")?.let(SettingsPasswords::remember)
                binding.settingsPassword.text?.clear()
                binding.settingsPasswordConfirm.text?.clear()
                wizard.next(state.copy(password = token))
            }
        }
    }

    /**
     * Step 3: no dialog of its own. The request is recorded before the page opens, so a recreated page waits for the
     * result instead of opening another one; a location chosen already (the password step asked again) is kept.
     * zh-CN: 第 3 步: 没有自己的对话框. 打开系统页面之前先记录已请求, 因此重建后的页面等待结果, 而不是再打开一次; 已选定的位置 (口令步骤
     * 重新询问过口令) 保持不变.
     */
    private class LocationStep : SettingsWizardStep(Step.CHOOSE_LOCATION) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            if (state.document != null) return wizard.next(state)
            var current = state
            if (!current.locationRequested) {
                current = current.copy(locationRequested = true)
                wizard.host.wizardState = current
                try {
                    wizard.host.requestDocument(SettingsArchive.defaultFileName(System.currentTimeMillis()))
                } catch (e: ActivityNotFoundException) {
                    return current.copy(step = Step.FAILED, failure = Failure.IO, failureDetail = e.message ?: e.javaClass.simpleName)
                }
            }
            val uri = wizard.host.awaitDocument()
            if (uri == null) {
                ViewUtils.showToast(wizard.host.hostActivity, R.string.settings_transfer_export_canceled, true)
                return null
            }
            return wizard.next(current.copy(document = uri.toString()))
        }
    }

    /**
     * Step 4: writes the archive behind a progress dialog; a recreated page writes it again (truncating). A password the
     * process does not hold any more (it ended meanwhile) is asked for again.
     * zh-CN: 第 4 步: 在进度对话框后写入归档; 重建后的页面再写一次 (先截断). 进程已不持有口令 (期间进程结束过) 时重新询问口令.
     */
    private class WriteStep : SettingsWizardStep(Step.WRITE) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState {
            val document = state.document ?: return state.copy(step = Step.CHOOSE_LOCATION, locationRequested = false)
            val password = state.password?.let { SettingsPasswords.find(it) ?: return state.copy(step = Step.SET_PASSWORD, password = null) }
            val activity = wizard.host.hostActivity
            val progress = MaterialDialog.Builder(activity)
                .title(wizard.titleRes)
                .content(R.string.settings_transfer_export_writing)
                .progress(true, 0)
                .widgetThemeColor()
                .cancelable(false)
                .show()
            return try {
                val result = withContext(Dispatchers.IO) { SettingsExporter.export(activity, Uri.parse(document), state.selected, password = password, refine = state.refine) }
                state.copy(
                    step = Step.DONE,
                    fileName = result.fileName,
                    categories = result.categories,
                    entries = result.entries,
                    unexportable = result.unexportable,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.copy(step = Step.FAILED, failure = Failure.of(e), failureDetail = e.message ?: e.javaClass.simpleName)
            } finally {
                progress.dismiss()
            }
        }
    }

    /** Step 5: the file name and the counts. zh-CN: 第 5 步: 文件名与计数. */
    private class DoneStep : SettingsWizardStep(Step.DONE) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            val activity = wizard.host.hostActivity
            val text = buildString {
                append(activity.getString(R.string.settings_transfer_export_done, state.fileName.orEmpty(), state.categories, state.entries))
                if (SettingCategory.CREDENTIALS.id in state.selected) {
                    val sealed = if (state.password != null) R.string.settings_transfer_export_sealed_password else R.string.settings_transfer_export_sealed
                    append("\n\n").append(activity.getString(sealed))
                }
                if (state.unexportable > 0) append("\n\n").append(activity.getString(R.string.settings_transfer_export_unexportable, state.unexportable))
            }
            dialog(wizard, state, R.string.text_done, back = false, cancel = false)
                .title(R.string.settings_transfer_export_done_title)
                .content(text)
                .showAndAwait()
            return null
        }
    }

    /** The reason of a failed write (one message per [Failure]); "Back" returns to the categories. */
    private class FailedStep : SettingsWizardStep(Step.FAILED) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            val activity = wizard.host.hostActivity
            val action = dialog(wizard, state, R.string.dialog_button_close, back = false, cancel = false)
                .title(R.string.settings_transfer_export_failed_title)
                .content(SettingsTransferMessages.failureText(activity, state.failure ?: Failure.IO, state.failureDetail))
                .neutralText(R.string.dialog_button_back)
                .neutralColorRes(R.color.dialog_button_default)
                .showAndAwait()
            return when (action) {
                DialogAction.NEUTRAL -> state.copy(
                    step = Step.PICK_CATEGORIES, locationRequested = false, document = null, failure = null, failureDetail = null,
                )
                else -> null
            }
        }
    }

    companion object {

        /**
         * The state the wizard starts with: the [remembered] selection (`MiscStateKeys.SETTINGS_EXPORT_CATEGORIES`) or the
         * default one of appendix A.1 ([SettingsCategorySelection.initial]).
         * zh-CN: 向导的初始状态: 记住的勾选 (`MiscStateKeys.SETTINGS_EXPORT_CATEGORIES`) 或附录 A.1 的默认勾选.
         */
        @JvmStatic
        fun initialState(remembered: List<String>?): SettingsWizardState = SettingsWizardState(
            kind = SettingsWizardState.Kind.EXPORT,
            step = Step.PICK_CATEGORIES,
            selected = SettingsCategorySelection.initial(remembered),
        )
    }
}
