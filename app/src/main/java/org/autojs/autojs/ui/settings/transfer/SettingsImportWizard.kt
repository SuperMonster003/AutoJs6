package org.autojs.autojs.ui.settings.transfer

import android.content.ActivityNotFoundException
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File

/**
 * The import wizard (docs/settings/ROADMAP.md T10, D8 / D9 / D19, P7.5): choose the settings file (the system page
 * "open document", T11) and see its summary (exported by, device, time, the warnings of a newer format and of another
 * host package, appendix C Q26, credentials sealed for another device, D23), tick the categories it holds (the same
 * step as the export, [SettingsCategoryPickerStep]), apply them ([SettingsImporter.importArchive]: internal backup,
 * write, effects) and read the result page. The chosen file is copied to the cache first and deleted when the wizard
 * ends; a failure offers to choose another file. The deployment step of T18 comes with P7.6.
 *
 * zh-CN: 导入向导 (ROADMAP T10, D8 / D9 / D19, P7.5): 选择设置文件 (系统 "打开文档" 页面, T11) 并查看摘要 (导出来源, 设备, 时间, 更高
 * 格式与其他宿主包名的警告 (附录 C Q26), 以其他设备密钥加密的凭据 (D23)), 勾选文件中有的分类 (与导出相同的步骤
 * [SettingsCategoryPickerStep]), 应用 ([SettingsImporter.importArchive]: 内部备份, 写入, 生效), 最后是结果页. 所选文件先复制到
 * 缓存, 向导结束时删除; 失败时可重新选择文件. T18 的部署项步骤随 P7.6 加入.
 */
class SettingsImportWizard(host: SettingsWizardHost) : SettingsWizard(host) {

    override val titleRes: Int = R.string.settings_transfer_import

    override val steps: List<SettingsWizardStep> = listOf(
        FileStep(),
        SettingsCategoryPickerStep(),
        SettingsApplyStep(R.string.settings_transfer_import_applying, SettingsImporter::importArchive),
        SettingsResultStep(R.string.settings_transfer_import_done_title),
        SettingsFailedStep(R.string.settings_transfer_import_failed_title) { anotherFile(it) },
    )

    override fun onFinished() {
        SettingsImporter.clearCache(host.hostActivity)
    }

    /**
     * Step 1: the system page that opens a document (requested once, so that a recreated page waits for its result),
     * the copy of the file, then its summary; "Other file" chooses again.
     * zh-CN: 第 1 步: 系统的打开文档页面 (只请求一次, 重建后的页面等待其结果), 复制文件, 然后显示摘要; "其他文件" 重新选择.
     */
    private class FileStep : SettingsWizardStep(Step.CHOOSE_FILE) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            val activity = wizard.host.hostActivity
            var current = state
            if (current.archive == null || !File(current.archive).isFile) {
                if (!current.locationRequested) {
                    current = current.copy(locationRequested = true, archive = null)
                    wizard.host.wizardState = current
                    try {
                        wizard.host.requestOpenDocument()
                    } catch (e: ActivityNotFoundException) {
                        return failed(current, e)
                    }
                }
                val uri = wizard.host.awaitDocument()
                if (uri == null) {
                    ViewUtils.showToast(activity, R.string.settings_transfer_import_canceled, true)
                    return null
                }
                val progress = progress(wizard)
                current = try {
                    val (file, name) = withContext(Dispatchers.IO) { SettingsImporter.copyToCache(activity, uri) }
                    current.copy(locationRequested = false, archive = file.path, fileName = name, selected = emptyList())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return failed(current, e)
                } finally {
                    progress.dismiss()
                }
                wizard.host.wizardState = current
            }
            val inspection = try {
                withContext(Dispatchers.IO) { SettingsImporter.inspect(activity, current) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return failed(current, e)
            }
            val action = dialog(wizard, current)
                .content(SettingsTransferMessages.inspectionText(activity, inspection))
                .neutralText(R.string.settings_transfer_import_other_file)
                .neutralColorRes(R.color.dialog_button_default)
                .showAndAwait()
            return when (action) {
                DialogAction.POSITIVE -> wizard.next(current.copy(selected = current.selected.ifEmpty { SettingsCategoryChoices.importSelection(inspection.choices) }))
                DialogAction.NEUTRAL -> anotherFile(current)
                else -> null
            }
        }

        private fun progress(wizard: SettingsWizard): MaterialDialog = MaterialDialog.Builder(wizard.host.hostActivity)
            .title(wizard.titleRes)
            .content(R.string.settings_transfer_import_reading)
            .progress(true, 0)
            .widgetThemeColor()
            .cancelable(false)
            .show()

        private fun failed(state: SettingsWizardState, e: Exception) =
            state.copy(step = Step.FAILED, locationRequested = false, failure = Failure.of(e), failureDetail = e.message ?: e.javaClass.simpleName)
    }

    companion object {

        /** The state the wizard starts with: step 1, nothing chosen. zh-CN: 向导的初始状态: 第 1 步, 尚未选择. */
        @JvmStatic
        fun initialState(): SettingsWizardState = SettingsWizardState(Kind.IMPORT, Step.CHOOSE_FILE)

        /** [state] back at step 1 without a file, which requests a file again. zh-CN: 回到第 1 步且不带文件, 即重新选择文件. */
        @JvmStatic
        fun anotherFile(state: SettingsWizardState): SettingsWizardState = state.copy(
            step = Step.CHOOSE_FILE, locationRequested = false, archive = null, fileName = null, selected = emptyList(),
            operation = null, outcome = null, failure = null, failureDetail = null,
        )
    }
}
