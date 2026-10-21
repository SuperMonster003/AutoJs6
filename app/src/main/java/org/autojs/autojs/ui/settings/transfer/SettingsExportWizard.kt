package org.autojs.autojs.ui.settings.transfer

import android.content.ActivityNotFoundException
import android.net.Uri
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * The export wizard (docs/settings/ROADMAP.md T10, D4 / D5 / D6, P6.4): choose the categories, choose where to save
 * (the system page "create document", T11, with the default file name of D5), write the zip ([SettingsExporter], at
 * most [SettingsArchive.MAX_BYTES], T7), then the done page with the file name and the counts. Canceling the save
 * location ends the wizard with a notice; a failed write shows its reason and offers to go back to the categories.
 *
 * zh-CN: 导出向导 (ROADMAP T10, D4 / D5 / D6, P6.4): 选择分类, 选择保存位置 (系统 "创建文档" 页面, T11, 默认文件名见 D5), 写入 zip
 * ([SettingsExporter], 不超过 [SettingsArchive.MAX_BYTES], T7), 最后是显示文件名与计数的完成页. 取消选择保存位置时结束向导并提示;
 * 写入失败时显示原因, 可返回分类选择.
 */
class SettingsExportWizard(host: SettingsWizardHost) : SettingsWizard(host) {

    override val titleRes: Int = R.string.settings_transfer_export

    override val steps: List<SettingsWizardStep> = listOf(
        SettingsCategoryPickerStep(),
        LocationStep(),
        WriteStep(),
        DoneStep(),
        FailedStep(),
    )

    /**
     * Step 2: no dialog of its own. The request is recorded before the page opens, so a recreated page waits for the
     * result instead of opening another one.
     * zh-CN: 第 2 步: 没有自己的对话框. 打开系统页面之前先记录已请求, 因此重建后的页面等待结果, 而不是再打开一次.
     */
    private class LocationStep : SettingsWizardStep(Step.CHOOSE_LOCATION) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
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

    /** Step 3: writes the archive behind a progress dialog; a recreated page writes it again (truncating). */
    private class WriteStep : SettingsWizardStep(Step.WRITE) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState {
            val document = state.document ?: return state.copy(step = Step.CHOOSE_LOCATION, locationRequested = false)
            val activity = wizard.host.hostActivity
            val progress = MaterialDialog.Builder(activity)
                .title(wizard.titleRes)
                .content(R.string.settings_transfer_export_writing)
                .progress(true, 0)
                .widgetThemeColor()
                .cancelable(false)
                .show()
            return try {
                val result = withContext(Dispatchers.IO) { SettingsExporter.export(activity, Uri.parse(document), state.selected) }
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

    /** Step 4: the file name and the counts. zh-CN: 第 4 步: 文件名与计数. */
    private class DoneStep : SettingsWizardStep(Step.DONE) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            val activity = wizard.host.hostActivity
            val text = buildString {
                append(activity.getString(R.string.settings_transfer_export_done, state.fileName.orEmpty(), state.categories, state.entries))
                if (SettingCategory.CREDENTIALS.id in state.selected) append("\n\n").append(activity.getString(R.string.settings_transfer_export_sealed))
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
